package com.soloist.android.runtime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.PlaybackParams
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of audio bridge playback and format state for UI and service diagnostics.
 */
data class AudioStreamStats(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val encodingName: String = "",
    val bytesPlayed: Long = 0L,
    val bytesWritten: Long = 0L,
    val volumeGain: Float = 1.0f,
    val lastError: String? = null,
    val effectsAvailable: Boolean = false,
)

interface SoloistPcmEffects {
    fun process(input: ByteBuffer): ByteBuffer
    fun reset()
}

data class SoloistAudioEffects(
    val rate: () -> Pair<Float, Float>,
    val create: (sampleRate: Int, channels: Int, encoding: Int) -> SoloistPcmEffects?,
)

/**
 * Abstract AF_UNIX audio bridge server matching `native-shims/bridge_proto.h`.
 *
 * Receives PulseAudio commands from `libgpulse0.so` (`pulse_shim.c`), manages an
 * Android [AudioTrack] in `MODE_STREAM`, converts unsupported 24/32-bit PCM formats
 * to IEEE float when needed, and emits synchronized `AUDIO_EVT_OPEN_ACK` and
 * `AUDIO_EVT_TIMING` frames back to the native shim.
 */
class AudioBridgeServer(
    val socketName: String,
    @field:Volatile
    var onPlaybackHeadAdvance: ((bytesPlayed: Long, bytesWritten: Long, playing: Boolean) -> Unit)? = null,
) : Closeable {

    private val lifecycleLock = Any()
    private val clientLifecycleLock = Any()
    private val running = AtomicBoolean(false)

    private val _stats = MutableStateFlow(AudioStreamStats())
    val stats: StateFlow<AudioStreamStats> = _stats.asStateFlow()
    @Volatile var effects: SoloistAudioEffects? = null

    @Volatile
    private var serverSocket: LocalServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    private var activeClientSocket: LocalSocket? = null

    @Volatile
    private var activeClientThread: Thread? = null

    @Volatile
    private var volumeGain: Float = 1.0f

    /**
     * Binds [socketName] synchronously and starts accepting audio shim clients.
     * Calling [start] while already running is a no-op.
     */
    fun start() {
        synchronized(lifecycleLock) {
            if (running.get()) {
                return
            }
            val bound = LocalServerSocket(socketName)
            serverSocket = bound
            running.set(true)
            _stats.update { it.copy(lastError = null) }

            val thread = Thread(
                { acceptLoop(bound) },
                "SoloistAudioAccept",
            ).apply {
                isDaemon = true
                start()
            }
            acceptThread = thread
            Log.i(TAG, "Audio bridge listening on abstract socket @$socketName")
        }
    }

    private fun acceptLoop(bound: LocalServerSocket) {
        try {
            while (running.get()) {
                val client = try {
                    bound.accept()
                } catch (e: IOException) {
                    if (running.get()) {
                        Log.w(TAG, "accept() failed on @$socketName", e)
                        _stats.update { it.copy(lastError = e.message ?: "accept failed") }
                    }
                    break
                }
                if (runCatching { client.peerCredentials.uid }.getOrNull() != android.os.Process.myUid()) {
                    runCatching { client.close() }
                    continue
                }
                if (!running.get()) {
                    runCatching { client.close() }
                    break
                }

                val accepted = synchronized(clientLifecycleLock) {
                    if (!running.get()) {
                        false
                    } else {
                        stopActiveClientLocked()
                        if (!running.get()) {
                            false
                        } else {
                            val clientThread = Thread(
                                { handleClient(client) },
                                "SoloistAudioClient",
                            ).apply {
                                isDaemon = true
                            }
                            activeClientSocket = client
                            activeClientThread = clientThread
                            clientThread.start()
                            true
                        }
                    }
                }
                if (!accepted) {
                    runCatching { client.close() }
                    break
                }
            }
        } finally {
            runCatching { bound.close() }
        }
    }

    private fun stopActiveClient() {
        synchronized(clientLifecycleLock) {
            stopActiveClientLocked()
        }
    }

    private fun stopActiveClientLocked() {
        val prevSocket = activeClientSocket
        val prevThread = activeClientThread
        activeClientSocket = null
        activeClientThread = null

        if (prevSocket != null) {
            runCatching { prevSocket.shutdownInput() }
            runCatching { prevSocket.shutdownOutput() }
            runCatching { prevSocket.close() }
        }
        if (prevThread != null && prevThread !== Thread.currentThread()) {
            runCatching { prevThread.join(1500L) }
        }
    }

    private fun handleClient(client: LocalSocket) {
        val sendLock = Any()
        val socketClosed = AtomicBoolean(false)
        var session: StreamSession? = null

        _stats.update {
            it.copy(
                connected = true,
                playing = false,
                lastError = null,
            )
        }

        try {
            client.use { socket ->
                val dis = DataInputStream(BufferedInputStream(socket.inputStream, 65536))
                val out = BufferedOutputStream(socket.outputStream, 1024)

                val sender = PacketSender(out, sendLock, socketClosed)
                val hdr = ByteArray(5)

                while (running.get() && !socketClosed.get()) {
                    try {
                        dis.readFully(hdr)
                    } catch (_: EOFException) {
                        break
                    }

                    val msgType = hdr[0].toInt() and 0xFF
                    val payloadLenU = readUInt32Le(hdr, 1)
                    if (payloadLenU < 0L || payloadLenU > MAX_PAYLOAD_BYTES) {
                        val err = "Invalid audio frame length: $payloadLenU (msgType=$msgType)"
                        Log.e(TAG, err)
                        _stats.update { it.copy(lastError = err) }
                        break
                    }

                    val payloadLen = payloadLenU.toInt()
                    val payload = if (payloadLen > 0) {
                        ByteArray(payloadLen).also { dis.readFully(it) }
                    } else {
                        EMPTY_BYTES
                    }

                    when (msgType) {
                        AUDIO_CMD_OPEN -> {
                            session?.close()
                            session = openStream(payload, sender)
                        }

                        AUDIO_CMD_WRITE -> {
                            val active = session
                            if (active != null && payload.isNotEmpty()) {
                                active.enqueuePcm(payload)
                            }
                        }

                        AUDIO_CMD_CORK -> {
                            if (payload.isNotEmpty()) {
                                val corked = payload[0].toInt() != 0
                                session?.setCorked(corked)
                            }
                        }

                        AUDIO_CMD_FLUSH -> {
                            session?.flush(sender)
                        }

                        AUDIO_CMD_VOLUME -> {
                            if (payload.size >= 4) {
                                val rawGain = ByteBuffer.wrap(payload, 0, 4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .float
                                val clamped = if (rawGain.isNaN()) 0f else rawGain.coerceIn(0f, 1f)
                                volumeGain = clamped
                                session?.setVolume(clamped)
                                _stats.update { it.copy(volumeGain = clamped) }
                            }
                        }

                        AUDIO_CMD_CLOSE -> {
                            session?.close()
                            session = null
                            _stats.update {
                                it.copy(
                                    playing = false,
                                    sampleRate = 0,
                                    effectsAvailable = false,
                                    channels = 0,
                                    encodingName = "",
                                    bytesPlayed = 0L,
                                    bytesWritten = 0L,
                                )
                            }
                        }

                        AUDIO_CMD_PING_TIMING -> {
                            session?.pingTiming(sender)
                        }

                        else -> {
                            Log.w(TAG, "Ignoring unknown audio bridge command msgType=$msgType")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (running.get()) {
                Log.w(TAG, "Audio bridge client disconnected with I/O error: ${e.message}")
                _stats.update { it.copy(lastError = e.message ?: "I/O error") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in audio bridge client loop", e)
            _stats.update { it.copy(lastError = e.message ?: e.javaClass.simpleName) }
        } finally {
            socketClosed.set(true)
            session?.close()
            _stats.update {
                it.copy(
                    connected = false,
                    playing = false,
                    sampleRate = 0,
                    effectsAvailable = false,
                    channels = 0,
                    encodingName = "",
                )
            }
        }
    }

    private fun openStream(payload: ByteArray, sender: PacketSender): StreamSession? {
        if (payload.size < 20) {
            val err = "AUDIO_CMD_OPEN payload too short (${payload.size} bytes)"
            Log.e(TAG, err)
            _stats.update { it.copy(lastError = err) }
            runCatching { sender.sendOpenAck(-1, 0L) }
            return null
        }

        val paFormat = readUInt32Le(payload, 0).toInt()
        val sampleRate = readUInt32Le(payload, 4).toInt()
        val channels = readUInt32Le(payload, 8).toInt()
        val tlengthBytes = readUInt32Le(payload, 12)
        // Bytes 16..19 hold minreq_bytes per bridge_proto.h (20-byte AUDIO_CMD_OPEN payload).

        if (sampleRate !in 4000..384000 || channels !in 1..8) {
            val err = "Unsupported audio spec: rate=$sampleRate channels=$channels paFormat=$paFormat"
            Log.e(TAG, err)
            _stats.update { it.copy(lastError = err) }
            runCatching { sender.sendOpenAck(-1, 0L) }
            return null
        }

        val formatSpec = resolveSampleFormat(paFormat)
        if (formatSpec == null) {
            val err = "Unsupported pa_sample_format=$paFormat"
            Log.e(TAG, err)
            _stats.update { it.copy(lastError = err) }
            runCatching { sender.sendOpenAck(-1, 0L) }
            return null
        }

        val created = buildAudioTrackWithFallback(
            formatSpec = formatSpec,
            sampleRate = sampleRate,
            channels = channels,
            tlengthBytes = tlengthBytes,
        )
        if (created == null) {
            val err = "Failed to initialize AudioTrack (rate=$sampleRate ch=$channels fmt=${formatSpec.encodingName})"
            Log.e(TAG, err)
            _stats.update { it.copy(lastError = err) }
            runCatching { sender.sendOpenAck(-1, 0L) }
            return null
        }

        val (track, resolvedSpec, reportedBufferBytes) = created
        val settings = effects
        val pcmEffects = runCatching {
            settings?.create?.invoke(sampleRate, channels, resolvedSpec.androidEncoding)
        }.onFailure { Log.w(TAG, "Couldn't initialize Soloist effects", it) }.getOrNull()
        val gain = volumeGain.coerceIn(0f, 1f)
        runCatching { track.setVolume(gain) }
        runCatching { track.play() }

        // Send OPEN_ACK before starting the timing reporter thread so OPEN_ACK is always first.
        try {
            sender.sendOpenAck(0, reportedBufferBytes)
        } catch (e: IOException) {
            runCatching {
                track.pause()
                track.flush()
                track.stop()
                track.release()
            }
            throw e
        }

        _stats.update {
            it.copy(
                connected = true,
                playing = true,
                sampleRate = sampleRate,
                channels = channels,
                encodingName = resolvedSpec.encodingName,
                effectsAvailable = pcmEffects != null,
                bytesPlayed = 0L,
                bytesWritten = 0L,
                volumeGain = gain,
                lastError = null,
            )
        }

        return StreamSession(
            track = track,
            paFormat = paFormat,
            sampleRate = sampleRate,
            channels = channels,
            inBps = resolvedSpec.inBps,
            outBps = resolvedSpec.outBps,
            needsFloatConversion = resolvedSpec.needsFloatConversion,
            pcmEffects = pcmEffects,
            effectRate = settings?.rate?.takeIf { pcmEffects != null },
            sender = sender,
        ).apply { start() }
    }

    private fun buildAudioTrackWithFallback(
        formatSpec: ResolvedFormat,
        sampleRate: Int,
        channels: Int,
        tlengthBytes: Long,
    ): BuiltAudioTrack? {
        val preferFloat = effects != null && channels in 1..2 && sampleRate >= 8_000
        if (!preferFloat) tryBuildTrack(formatSpec, sampleRate, channels, tlengthBytes)?.let { return it }

        if (!formatSpec.needsFloatConversion &&
            (formatSpec.paFormat == PA_SAMPLE_S32LE || formatSpec.paFormat == PA_SAMPLE_S24LE)
        ) {
            val fallbackSpec = ResolvedFormat(
                paFormat = formatSpec.paFormat,
                androidEncoding = AudioFormat.ENCODING_PCM_FLOAT,
                inBps = formatSpec.inBps,
                outBps = 4,
                needsFloatConversion = true,
                encodingName = if (formatSpec.paFormat == PA_SAMPLE_S32LE) "PCM_S32_TO_FLOAT" else "PCM_S24_TO_FLOAT",
            )
            tryBuildTrack(fallbackSpec, sampleRate, channels, tlengthBytes)?.let { return it }
        }
        return if (preferFloat) tryBuildTrack(formatSpec, sampleRate, channels, tlengthBytes) else null
    }

    private fun tryBuildTrack(
        spec: ResolvedFormat,
        sampleRate: Int,
        channels: Int,
        tlengthBytes: Long,
    ): BuiltAudioTrack? {
        val inFrameBytes = spec.inBps * channels
        val outFrameBytes = spec.outBps * channels
        val minBufRaw = computeMinBufferBytes(sampleRate, channels, spec.androidEncoding, outFrameBytes)
        if (minBufRaw <= 0) {
            return null
        }

        val clampedInTarget = tlengthBytes.coerceIn(MIN_BUFFER_BYTES, MAX_BUFFER_BYTES)
        val targetOutBytes = ((clampedInTarget / inFrameBytes) * outFrameBytes)
            .coerceIn(MIN_BUFFER_BYTES, MAX_BUFFER_BYTES)
        val rawOutBuf = max(minBufRaw.toLong() * 2L, targetOutBytes)
        val alignedOutBuf = (((rawOutBuf + outFrameBytes - 1) / outFrameBytes) * outFrameBytes)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val reportedInputBufBytes = ((alignedOutBuf.toLong() / outFrameBytes) * inFrameBytes)
            .coerceAtLeast(inFrameBytes.toLong())

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val formatBuilder = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(spec.androidEncoding)
        when (channels) {
            1 -> formatBuilder.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            2 -> formatBuilder.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            else -> formatBuilder.setChannelIndexMask((1 shl channels) - 1)
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(formatBuilder.build())
                .setBufferSizeInBytes(alignedOutBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack.Builder failed for ${spec.encodingName}: ${e.message}")
            return null
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return null
        }
        return BuiltAudioTrack(track, spec, reportedInputBufBytes)
    }

    private fun computeMinBufferBytes(
        sampleRate: Int,
        channels: Int,
        encoding: Int,
        outFrameBytes: Int,
    ): Int {
        val channelMask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            3 -> AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            5 -> AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            7 -> AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
        val direct = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        if (direct > 0) {
            return direct
        }
        if (channels > 2) {
            val stereo = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, encoding)
            if (stereo > 0) {
                return (stereo * channels) / 2
            }
        }
        // Fallback ~40ms frame buffer when HAL does not report multi-channel min size.
        val fallbackFrames = (sampleRate / 25).coerceAtLeast(256)
        return fallbackFrames * outFrameBytes
    }

    private fun resolveSampleFormat(paFormat: Int): ResolvedFormat? {
        return when (paFormat) {
            PA_SAMPLE_S16LE -> ResolvedFormat(
                paFormat = paFormat,
                androidEncoding = AudioFormat.ENCODING_PCM_16BIT,
                inBps = 2,
                outBps = 2,
                needsFloatConversion = false,
                encodingName = "PCM_16BIT",
            )
            PA_SAMPLE_FLOAT32LE -> ResolvedFormat(
                paFormat = paFormat,
                androidEncoding = AudioFormat.ENCODING_PCM_FLOAT,
                inBps = 4,
                outBps = 4,
                needsFloatConversion = false,
                encodingName = "PCM_FLOAT32",
            )
            PA_SAMPLE_S32LE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ResolvedFormat(
                        paFormat = paFormat,
                        androidEncoding = AudioFormat.ENCODING_PCM_32BIT,
                        inBps = 4,
                        outBps = 4,
                        needsFloatConversion = false,
                        encodingName = "PCM_32BIT",
                    )
                } else {
                    ResolvedFormat(
                        paFormat = paFormat,
                        androidEncoding = AudioFormat.ENCODING_PCM_FLOAT,
                        inBps = 4,
                        outBps = 4,
                        needsFloatConversion = true,
                        encodingName = "PCM_S32_TO_FLOAT",
                    )
                }
            }
            PA_SAMPLE_S24LE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ResolvedFormat(
                        paFormat = paFormat,
                        androidEncoding = AudioFormat.ENCODING_PCM_24BIT_PACKED,
                        inBps = 3,
                        outBps = 3,
                        needsFloatConversion = false,
                        encodingName = "PCM_24BIT_PACKED",
                    )
                } else {
                    ResolvedFormat(
                        paFormat = paFormat,
                        androidEncoding = AudioFormat.ENCODING_PCM_FLOAT,
                        inBps = 3,
                        outBps = 4,
                        needsFloatConversion = true,
                        encodingName = "PCM_S24_TO_FLOAT",
                    )
                }
            }
            PA_SAMPLE_S24_32LE -> ResolvedFormat(
                paFormat = paFormat,
                androidEncoding = AudioFormat.ENCODING_PCM_FLOAT,
                inBps = 4,
                outBps = 4,
                needsFloatConversion = true,
                encodingName = "PCM_S24_32_TO_FLOAT",
            )
            else -> null
        }
    }

    /**
     * Stops accepting new connections, closes active client sockets/streams, and releases
     * all associated [AudioTrack] resources.
     */
    override fun close() {
        synchronized(lifecycleLock) {
            if (!running.getAndSet(false) && serverSocket == null) {
                return
            }
            val sock = serverSocket
            serverSocket = null
            runCatching {
                LocalSocket().use { wake ->
                    wake.connect(LocalSocketAddress(socketName))
                }
            }
            runCatching { sock?.close() }

            val acc = acceptThread
            acceptThread = null
            if (acc != null && acc !== Thread.currentThread()) {
                runCatching { acc.join(2000L) }
            }

            stopActiveClient()
            _stats.update { it.copy(connected = false, playing = false, effectsAvailable = false) }
            Log.i(TAG, "Audio bridge stopped (@$socketName)")
        }
    }

    private inner class StreamSession(
        private val track: AudioTrack,
        private val paFormat: Int,
        private val sampleRate: Int,
        private val channels: Int,
        private val inBps: Int,
        private val outBps: Int,
        private val needsFloatConversion: Boolean,
        private val pcmEffects: SoloistPcmEffects?,
        private val effectRate: (() -> Pair<Float, Float>)?,
        private val sender: PacketSender,
    ) : Closeable {

        private val inFrameBytes = inBps * channels
        private val outFrameBytes = outBps * channels

        private val trackLock = Any()
        private val timingSendLock = Any()
        private val writeSignalLock = ReentrantLock()
        private val writeSignalCond = writeSignalLock.newCondition()

        private val chunkQueue = LinkedBlockingQueue<PcmChunk>()
        private val flushEpoch = AtomicLong(0L)
        private val writtenAckedBytes = AtomicLong(0L)
        private val flushing = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        @Volatile
        private var corked = false
        private var totalFramesPlayed = 0L
        private var lastHeadPos = 0L
        private var writtenOutUnits = 0L
        private var appliedRate = 1f to 1f
        private var requestedRate = appliedRate

        private var writerThread: Thread? = null
        private var timingThread: Thread? = null

        fun start() {
            writerThread = Thread({ writerLoop() }, "SoloistAudioWriter").apply {
                isDaemon = true
                start()
            }
            timingThread = Thread({ timingLoop() }, "SoloistAudioTiming").apply {
                isDaemon = true
                start()
            }
        }

        fun enqueuePcm(payload: ByteArray) {
            if (payload.isEmpty() || closed.get() || flushing.get()) {
                return
            }
            val chunk = PcmChunk(payload, flushEpoch.get())
            chunkQueue.offer(chunk)
            val acked = writtenAckedBytes.addAndGet(payload.size.toLong())
            _stats.update { it.copy(bytesWritten = acked) }
            signalWriter()
        }

        fun setCorked(newCorked: Boolean) {
            synchronized(trackLock) {
                if (closed.get()) return
                corked = newCorked
                runCatching {
                    if (newCorked) {
                        track.pause()
                    } else {
                        track.play()
                    }
                }
            }
            signalWriter()
            _stats.update { it.copy(playing = !newCorked) }
        }

        fun flush(packetSender: PacketSender) {
            flushing.set(true)
            synchronized(timingSendLock) {
                flushEpoch.incrementAndGet()
                chunkQueue.clear()
                signalWriter()

                synchronized(trackLock) {
                    if (closed.get()) return
                    chunkQueue.clear()
                    runCatching {
                        track.pause()
                        track.flush()
                    }
                    totalFramesPlayed = 0L
                    lastHeadPos = runCatching {
                        track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
                    }.getOrDefault(0L)
                    writtenOutUnits = 0L
                    writtenAckedBytes.set(0L)
                    if (!corked) {
                        runCatching { track.play() }
                    }
                }

                _stats.update { it.copy(bytesPlayed = 0L, bytesWritten = 0L) }
                onPlaybackHeadAdvance?.invoke(0L, 0L, !corked)
                runCatching { packetSender.sendTiming(0L, 0L, 0L) }

                if (!closed.get()) {
                    flushing.set(false)
                    signalWriter()
                }
            }
        }

        fun setVolume(gain: Float) {
            synchronized(trackLock) {
                if (!closed.get()) {
                    runCatching { track.setVolume(gain) }
                }
            }
        }

        fun pingTiming(packetSender: PacketSender) {
            val snap = snapshotTiming() ?: return
            synchronized(timingSendLock) {
                if (!closed.get() && !flushing.get() && snap.epoch == flushEpoch.get()) {
                    runCatching {
                        packetSender.sendTiming(snap.bytesPlayed, snap.sinkLatencyUs, snap.writtenAcked)
                    }
                }
            }
        }

        private fun writerLoop() {
            var effectsEpoch = 0L
            var floatBuffer = ByteBuffer.allocate(0)
            while (!closed.get()) {
                val chunk = try {
                    chunkQueue.poll(100L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue

                if (closed.get() || flushing.get() || chunk.epoch != flushEpoch.get()) {
                    continue
                }

                if (pcmEffects != null) {
                    if (effectsEpoch != chunk.epoch) {
                        pcmEffects.reset()
                        effectsEpoch = chunk.epoch
                    }
                    val input = if (needsFloatConversion) {
                        val floats = convertToFloat32(paFormat, chunk.data)
                        val bytes = floats.size * Float.SIZE_BYTES
                        if (floatBuffer.capacity() < bytes) {
                            floatBuffer = ByteBuffer.allocate(bytes).order(ByteOrder.nativeOrder())
                        }
                        floatBuffer.clear()
                        floatBuffer.asFloatBuffer().put(floats)
                        floatBuffer.limit(bytes)
                        floatBuffer
                    } else ByteBuffer.wrap(chunk.data)
                    val output = pcmEffects.process(input)
                    writeChunk(output.remaining(), chunk.epoch, "effects") { _, remaining ->
                        track.write(output, remaining, AudioTrack.WRITE_NON_BLOCKING)
                    }
                } else if (needsFloatConversion) {
                    val floats = convertToFloat32(paFormat, chunk.data)
                    writeFloatChunk(floats, chunk.epoch)
                } else {
                    writeByteChunk(chunk.data, chunk.epoch)
                }
            }
        }

        private fun writeByteChunk(data: ByteArray, epoch: Long) {
            writeChunk(data.size, epoch, "byte[]") { offset, remaining ->
                track.write(data, offset, remaining, AudioTrack.WRITE_NON_BLOCKING)
            }
        }

        private fun writeFloatChunk(floats: FloatArray, epoch: Long) {
            writeChunk(floats.size, epoch, "float[]") { offset, remaining ->
                track.write(floats, offset, remaining, AudioTrack.WRITE_NON_BLOCKING)
            }
        }

        private inline fun writeChunk(
            totalUnits: Int,
            epoch: Long,
            kind: String,
            writeOp: (offset: Int, remaining: Int) -> Int,
        ) {
            var offset = 0
            while (offset < totalUnits && !closed.get() && !flushing.get() && epoch == flushEpoch.get()) {
                val written = synchronized(trackLock) {
                    if (closed.get() || flushing.get() || epoch != flushEpoch.get()) {
                        return
                    }
                    if (!corked) applyEffectRate()
                    writeOp(offset, totalUnits - offset)
                }
                if (written > 0) {
                    offset += written
                    synchronized(trackLock) {
                        if (epoch == flushEpoch.get()) {
                            writtenOutUnits += written.toLong()
                        }
                    }
                } else if (written == 0) {
                    awaitWriteSpace(epoch)
                } else {
                    Log.w(TAG, "AudioTrack.write($kind) error=$written")
                    _stats.update { it.copy(lastError = "AudioTrack write error $written") }
                    break
                }
            }
        }

        private fun applyEffectRate() {
            val requested = effectRate?.invoke() ?: return
            if (requested == requestedRate) return
            requestedRate = requested
            runCatching {
                track.playbackParams = PlaybackParams().allowDefaults()
                    .setSpeed(requested.first).setPitch(requested.second)
                appliedRate = requested
            }.onFailure { Log.w(TAG, "AudioTrack refused Soloist speed or pitch", it) }
        }

        private fun awaitWriteSpace(epoch: Long) {
            writeSignalLock.withLock {
                if (closed.get() || flushing.get() || epoch != flushEpoch.get()) {
                    return
                }
                if (corked) {
                    while (corked && !closed.get() && !flushing.get() && epoch == flushEpoch.get()) {
                        try {
                            writeSignalCond.await()
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return
                        }
                    }
                } else {
                    try {
                        writeSignalCond.await(WRITER_BACKOFF_MS, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        }

        private fun signalWriter() {
            writeSignalLock.withLock {
                writeSignalCond.signalAll()
            }
        }

        private fun timingLoop() {
            while (!closed.get()) {
                try {
                    Thread.sleep(TIMING_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (closed.get()) {
                    break
                }
                val snap = snapshotTiming() ?: continue
                var ioFailed = false
                synchronized(timingSendLock) {
                    if (!closed.get() && !flushing.get() && snap.epoch == flushEpoch.get()) {
                        try {
                            sender.sendTiming(snap.bytesPlayed, snap.sinkLatencyUs, snap.writtenAcked)
                            _stats.update {
                                it.copy(
                                    bytesPlayed = snap.bytesPlayed,
                                    bytesWritten = snap.writtenAcked,
                                    playing = snap.playing,
                                )
                            }
                            onPlaybackHeadAdvance?.invoke(snap.bytesPlayed, snap.writtenAcked, snap.playing)
                        } catch (_: IOException) {
                            ioFailed = true
                        }
                    }
                }
                if (ioFailed) {
                    break
                }
            }
        }

        private fun snapshotTiming(): TimingSnapshot? {
            return synchronized(trackLock) {
                if (closed.get() || flushing.get()) {
                    return null
                }
                val epoch = flushEpoch.get()
                val rawPos = runCatching {
                    track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
                }.getOrDefault(lastHeadPos)
                val delta = (rawPos - lastHeadPos) and 0xFFFF_FFFFL
                if (delta in 1L until 0x8000_0000L) {
                    totalFramesPlayed += delta
                    lastHeadPos = rawPos
                }

                val framesWritten = if (needsFloatConversion && pcmEffects == null) {
                    writtenOutUnits / channels
                } else {
                    writtenOutUnits / outFrameBytes
                }
                val sinkLatencyUs = estimateSinkLatencyUs(
                    track = track,
                    framesWritten = framesWritten,
                    framesPlayed = totalFramesPlayed,
                    sampleRate = (sampleRate * appliedRate.first).toInt(),
                )
                val bytesPlayed = totalFramesPlayed * inFrameBytes
                val acked = writtenAckedBytes.get()
                TimingSnapshot(
                    bytesPlayed = bytesPlayed,
                    sinkLatencyUs = sinkLatencyUs,
                    writtenAcked = acked,
                    playing = !corked,
                    epoch = epoch,
                )
            }
        }

        private fun estimateSinkLatencyUs(
            track: AudioTrack,
            framesWritten: Long,
            framesPlayed: Long,
            sampleRate: Int,
        ): Long {
            val queuedFrames = (framesWritten - framesPlayed).coerceAtLeast(0L)
            val fallbackUs = (queuedFrames * 1_000_000L) / sampleRate
            val ts = AudioTimestamp()
            if (runCatching { track.getTimestamp(ts) }.getOrDefault(false)) {
                val ageNs = System.nanoTime() - ts.nanoTime
                if (ageNs in 0L..1_000_000_000L && ts.framePosition >= 0L) {
                    val presentedFrames = ts.framePosition + (ageNs * sampleRate) / 1_000_000_000L
                    if (presentedFrames <= framesWritten) {
                        val latUs = ((framesWritten - presentedFrames) * 1_000_000L) / sampleRate
                        if (latUs in 0L..5_000_000L) {
                            return latUs
                        }
                    }
                }
            }
            return fallbackUs
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) {
                return
            }
            flushing.set(true)
            chunkQueue.clear()
            signalWriter()

            val wt = writerThread
            val tt = timingThread
            writerThread = null
            timingThread = null

            wt?.interrupt()
            tt?.interrupt()
            if (wt != null && wt !== Thread.currentThread()) {
                runCatching { wt.join(1000L) }
            }
            if (tt != null && tt !== Thread.currentThread()) {
                runCatching { tt.join(1000L) }
            }

            synchronized(trackLock) {
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    private class PacketSender(
        private val out: BufferedOutputStream,
        private val sendLock: Any,
        private val socketClosed: AtomicBoolean,
    ) {
        fun sendOpenAck(status: Int, bufferBytes: Long) {
            val frame = ByteArray(5 + 8)
            frame[0] = AUDIO_EVT_OPEN_ACK.toByte()
            writeUInt32Le(frame, 1, 8L)
            writeInt32Le(frame, 5, status)
            writeUInt32Le(frame, 9, bufferBytes)
            writeFrame(frame)
        }

        fun sendTiming(bytesPlayed: Long, sinkLatencyUs: Long, writtenAckedBytes: Long) {
            val frame = ByteArray(5 + 24)
            frame[0] = AUDIO_EVT_TIMING.toByte()
            writeUInt32Le(frame, 1, 24L)
            writeUInt64Le(frame, 5, bytesPlayed)
            writeUInt64Le(frame, 13, sinkLatencyUs)
            writeUInt64Le(frame, 21, writtenAckedBytes)
            writeFrame(frame)
        }

        private fun writeFrame(frame: ByteArray) {
            synchronized(sendLock) {
                if (!socketClosed.get()) {
                    out.write(frame)
                    out.flush()
                }
            }
        }
    }

    private data class ResolvedFormat(
        val paFormat: Int,
        val androidEncoding: Int,
        val inBps: Int,
        val outBps: Int,
        val needsFloatConversion: Boolean,
        val encodingName: String,
    )

    private data class BuiltAudioTrack(
        val track: AudioTrack,
        val spec: ResolvedFormat,
        val reportedInputBufBytes: Long,
    )

    private class PcmChunk(
        val data: ByteArray,
        val epoch: Long,
    )

    private data class TimingSnapshot(
        val bytesPlayed: Long,
        val sinkLatencyUs: Long,
        val writtenAcked: Long,
        val playing: Boolean,
        val epoch: Long,
    )

    companion object {
        private const val TAG = "AudioBridgeServer"

        private const val MAX_PAYLOAD_BYTES = 4L * 1024L * 1024L
        private const val MIN_BUFFER_BYTES = 16384L
        private const val MAX_BUFFER_BYTES = 1048576L
        private const val WRITER_BACKOFF_MS = 5L
        private const val TIMING_INTERVAL_MS = 25L

        private const val AUDIO_CMD_OPEN = 1
        private const val AUDIO_CMD_WRITE = 2
        private const val AUDIO_CMD_CORK = 3
        private const val AUDIO_CMD_FLUSH = 4
        private const val AUDIO_CMD_VOLUME = 5
        private const val AUDIO_CMD_CLOSE = 6
        private const val AUDIO_CMD_PING_TIMING = 7

        private const val AUDIO_EVT_TIMING = 10
        private const val AUDIO_EVT_OPEN_ACK = 11

        private const val PA_SAMPLE_S16LE = 3
        private const val PA_SAMPLE_FLOAT32LE = 5
        private const val PA_SAMPLE_S32LE = 7
        private const val PA_SAMPLE_S24LE = 9
        private const val PA_SAMPLE_S24_32LE = 11

        private val EMPTY_BYTES = ByteArray(0)

        private fun readUInt32Le(buf: ByteArray, offset: Int): Long {
            return (buf[offset].toLong() and 0xFFL) or
                ((buf[offset + 1].toLong() and 0xFFL) shl 8) or
                ((buf[offset + 2].toLong() and 0xFFL) shl 16) or
                ((buf[offset + 3].toLong() and 0xFFL) shl 24)
        }

        private fun writeInt32Le(buf: ByteArray, offset: Int, value: Int) {
            writeUInt32Le(buf, offset, value.toLong() and 0xFFFF_FFFFL)
        }

        private fun writeUInt32Le(buf: ByteArray, offset: Int, value: Long) {
            buf[offset] = (value and 0xFFL).toByte()
            buf[offset + 1] = ((value ushr 8) and 0xFFL).toByte()
            buf[offset + 2] = ((value ushr 16) and 0xFFL).toByte()
            buf[offset + 3] = ((value ushr 24) and 0xFFL).toByte()
        }

        private fun writeUInt64Le(buf: ByteArray, offset: Int, value: Long) {
            buf[offset] = (value and 0xFFL).toByte()
            buf[offset + 1] = ((value ushr 8) and 0xFFL).toByte()
            buf[offset + 2] = ((value ushr 16) and 0xFFL).toByte()
            buf[offset + 3] = ((value ushr 24) and 0xFFL).toByte()
            buf[offset + 4] = ((value ushr 32) and 0xFFL).toByte()
            buf[offset + 5] = ((value ushr 40) and 0xFFL).toByte()
            buf[offset + 6] = ((value ushr 48) and 0xFFL).toByte()
            buf[offset + 7] = ((value ushr 56) and 0xFFL).toByte()
        }

        private fun convertToFloat32(paFormat: Int, input: ByteArray): FloatArray {
            return when (paFormat) {
                PA_SAMPLE_S32LE -> {
                    val count = input.size / 4
                    FloatArray(count) { i ->
                        val off = i * 4
                        val s = (input[off].toInt() and 0xFF) or
                            ((input[off + 1].toInt() and 0xFF) shl 8) or
                            ((input[off + 2].toInt() and 0xFF) shl 16) or
                            (input[off + 3].toInt() shl 24)
                        (s.toDouble() / 2147483648.0).toFloat()
                    }
                }
                PA_SAMPLE_S24LE -> {
                    val count = input.size / 3
                    FloatArray(count) { i ->
                        val off = i * 3
                        val s24 = (input[off].toInt() and 0xFF) or
                            ((input[off + 1].toInt() and 0xFF) shl 8) or
                            (input[off + 2].toInt() shl 16)
                        (s24.toDouble() / 8388608.0).toFloat()
                    }
                }
                PA_SAMPLE_S24_32LE -> {
                    val count = input.size / 4
                    FloatArray(count) { i ->
                        val off = i * 4
                        // Low 24 bits contain signed PCM; upper 8 bits are padding.
                        val s24 = (input[off].toInt() and 0xFF) or
                            ((input[off + 1].toInt() and 0xFF) shl 8) or
                            (input[off + 2].toInt() shl 16)
                        (s24.toDouble() / 8388608.0).toFloat()
                    }
                }
                else -> FloatArray(0)
            }
        }
    }
}
