/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, by Lelonio,
 * app/src/main/java/dev/lelonio/square/playback/ReverbTuning.kt and PlayerReverb.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: what one number on the 0..1 slider means as a room (decay from 0.5 s to 4.5 s, a
 * damped tail, the curved send level that leaves the dry path untouched) is Square's
 * ReverbTuning. Square puts Android's EnvironmentalReverb on the output as an auxiliary effect;
 * Astral's reverb is a media3 audio processor in the local player's chain instead (a small
 * Freeverb-style network of damped comb and all-pass filters, written here), so it is the same
 * room on every phone, works with the float output and shares the chain with the equalizer, and
 * is an exact copy of its input while the amount is 0.
 */
package com.music.spotui.di

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/** What one number on the reverb slider means as a room (Square's ReverbTuning, in seconds and gains). */
object ReverbTuning {
    /** The decay time (to -60 dB) of the room at [amount] 0..1: a live room up to a large hall. */
    fun decaySeconds(amount: Float): Float {
        val level = amount.coerceIn(0f, 1f)
        return (MIN_DECAY_MS + level * (MAX_DECAY_MS - MIN_DECAY_MS)) / 1000f
    }

    /**
     * How much of the signal goes to the tail, for [amount]. The dry path is untouched, so this is
     * purely the send. Curved rather than linear: the first part of the range is where the audible
     * change happens, and a straight mapping leaves most of the slider doing almost nothing.
     */
    fun sendLevel(amount: Float): Float {
        val level = amount.coerceIn(0f, 1f)
        return level * level * 0.85f + level * 0.15f
    }

    private const val MIN_DECAY_MS = 500f
    private const val MAX_DECAY_MS = 4500f
}

/**
 * The reverb of the local player (extensions' streams, downloads, the phone's files): a media3
 * audio processor in ExoPlayer's chain, each player with its own (the fading-out copy of a
 * crossfade too), all following [source] (0..1).
 *
 * The dry sound goes through as it came and a wet tail is added to it. At 0, once the last wet
 * tail has faded (about a third of a second after the dial reaches 0), the input is copied bit for
 * bit and nothing is computed. A change of the amount glides. Mono and stereo, 16-bit and float
 * PCM; anything else passes untouched (the processor is inactive). The sum is soft-limited near
 * full scale, only while the reverb is on.
 */
@UnstableApi
class ReverbProcessor(private val source: () -> Float = { AudioFx.reverb }) : BaseAudioProcessor() {
    private var channels = 0
    private var sampleRate = 0
    private var float = false

    /** The amount the room is tuned to now: follows the dial in small steps (no click, no zipper). */
    private var amount = 0f
    private var tunedFor = -1f
    private var wetGain = 0f
    private var inGain = 0f
    private var feedback = FloatArray(0)
    private var running = false

    // Per channel (index 0, 1): the combs and all-passes of a Freeverb, delays scaled to the rate.
    private var combs = Array(0) { FloatArray(0) }
    private var combPos = IntArray(0)
    private var combLow = FloatArray(0)
    private var allpasses = Array(0) { FloatArray(0) }
    private var allpassPos = IntArray(0)
    private var combLength = IntArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount !in 1..2 || inputAudioFormat.sampleRate < 8_000)
            return AudioProcessor.AudioFormat.NOT_SET
        // The format is applied in onFlush: the old stream may still be drained after this call.
        return inputAudioFormat
    }

    private fun allocate() {
        val scale = sampleRate / 44_100.0
        val lines = channels * COMBS
        combLength = IntArray(lines) { i ->
            val spread = if (i / COMBS == 1) STEREO_SPREAD else 0
            maxOf(((COMB_DELAYS[i % COMBS] + spread) * scale).roundToInt(), 8)
        }
        combs = Array(lines) { FloatArray(combLength[it]) }
        combPos = IntArray(lines)
        combLow = FloatArray(lines)
        feedback = FloatArray(lines)
        val apLines = channels * ALLPASSES
        allpasses = Array(apLines) { i ->
            val spread = if (i / ALLPASSES == 1) STEREO_SPREAD else 0
            FloatArray(maxOf(((ALLPASS_DELAYS[i % ALLPASSES] + spread) * scale).roundToInt(), 4))
        }
        allpassPos = IntArray(apLines)
        tunedFor = -1f
    }

    private fun clearState() {
        for (b in combs) b.fill(0f)
        for (b in allpasses) b.fill(0f)
        combPos.fill(0); allpassPos.fill(0); combLow.fill(0f)
        running = false
    }

    override fun onFlush() {
        val format = inputAudioFormat
        if (format != AudioProcessor.AudioFormat.NOT_SET) {
            float = format.encoding == C.ENCODING_PCM_FLOAT
            if (format.channelCount != channels || format.sampleRate != sampleRate || combs.isEmpty()) {
                channels = format.channelCount
                sampleRate = format.sampleRate
                allocate()
            }
        }
        clearState()
        // A new stream (or a seek) starts on the dial, not gliding to it.
        amount = source().coerceIn(0f, 1f)
        tunedFor = -1f
    }

    override fun onReset() {
        channels = 0; sampleRate = 0
        combs = Array(0) { FloatArray(0) }; allpasses = Array(0) { FloatArray(0) }
        combPos = IntArray(0); allpassPos = IntArray(0); combLow = FloatArray(0); feedback = FloatArray(0); combLength = IntArray(0)
        running = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val target = source().coerceIn(0f, 1f)
        if (target <= 0f && amount <= SETTLED) {
            // The exact copy: nothing heard is changed, nothing computed.
            if (running) clearState()
            amount = 0f
            val out = replaceOutputBuffer(inputBuffer.remaining())
            out.put(inputBuffer)
            out.flip()
            return
        }
        val bytes = if (float) 4 else 2
        val frames = inputBuffer.remaining() / (bytes * channels)
        if (frames == 0) return
        val out = replaceOutputBuffer(frames * bytes * channels)
        inputBuffer.order(ByteOrder.nativeOrder())
        running = true
        var left = frames
        while (left > 0) {
            val block = min(left, BLOCK)
            amount += (target - amount) * GLIDE_RATE
            if (target <= 0f && amount < SETTLED) amount = 0f
            if (abs(amount - tunedFor) > 0.002f || (amount == 0f && tunedFor != 0f)) tune()
            repeat(block) {
                val l = if (float) inputBuffer.float else inputBuffer.short * (1f / 32768f)
                val r = if (channels == 2) (if (float) inputBuffer.float else inputBuffer.short * (1f / 32768f)) else l
                val send = (l + r) * 0.5f * inGain
                val wetL = tail(0, send)
                val outL = soften(l + wetGain * wetL)
                put(out, outL)
                if (channels == 2) {
                    val wetR = tail(1, send)
                    put(out, soften(r + wetGain * wetR))
                }
            }
            left -= block
        }
        out.flip()
    }

    private fun put(out: ByteBuffer, s: Float) {
        if (float) out.putFloat(s) else out.putShort((s * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
    }

    /** Retunes the comb feedback (the decay) and the gains to the amount now. */
    private fun tune() {
        tunedFor = amount
        val rt60 = ReverbTuning.decaySeconds(amount)
        for (i in combLength.indices) {
            // Feedback that makes the comb's echo fall 60 dB in rt60 seconds.
            feedback[i] = 10.0.pow(-3.0 * combLength[i] / (rt60 * sampleRate)).toFloat().coerceAtMost(0.985f)
        }
        // Each comb's output is as loud as its input; eight of them add up to about one.
        val g = feedback[0]
        inGain = sqrt(1f - g * g) * COMB_LEVEL
        wetGain = ReverbTuning.sendLevel(amount) * WET_LEVEL
    }

    /** One channel's tail: the input into the eight combs (summed), then through the all-passes. */
    private fun tail(channel: Int, x: Float): Float {
        var sum = 0f
        val base = channel * COMBS
        for (k in 0 until COMBS) {
            val i = base + k
            val buf = combs[i]
            val p = combPos[i]
            val y = buf[p]
            // The feedback is lowpassed (the tail loses its highs as it ages).
            combLow[i] = y * (1f - DAMP) + combLow[i] * DAMP
            // Denormals flushed (they slow a float loop to a crawl on some CPUs); a NaN or infinity must not circulate.
            if (abs(combLow[i]) < DENORMAL) combLow[i] = 0f
            var v = x + combLow[i] * feedback[i]
            if (!v.isFinite()) { clearTail(); return 0f }
            if (abs(v) < DENORMAL) v = 0f
            buf[p] = v
            combPos[i] = if (p + 1 == buf.size) 0 else p + 1
            sum += y
        }
        var s = sum
        val apBase = channel * ALLPASSES
        for (k in 0 until ALLPASSES) {
            val i = apBase + k
            val buf = allpasses[i]
            val p = allpassPos[i]
            val held = buf[p]
            val y = held - s
            var stored = s + held * ALLPASS_GAIN
            if (abs(stored) < DENORMAL) stored = 0f
            buf[p] = stored
            allpassPos[i] = if (p + 1 == buf.size) 0 else p + 1
            s = y
        }
        if (!s.isFinite()) { clearTail(); return 0f }
        return s
    }

    /** The tail's state back to silence (a non-finite value got in): the dry path and the glide are untouched. */
    private fun clearTail() {
        for (b in combs) b.fill(0f)
        for (b in allpasses) b.fill(0f)
        combLow.fill(0f)
    }

    /** Untouched below the knee, then bent toward full scale: the dry plus the tail never clips. */
    private fun soften(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE))
        return if (x < 0) -y else y
    }

    private companion object {
        const val COMBS = 8
        const val ALLPASSES = 4
        const val STEREO_SPREAD = 23
        /** Freeverb's delays, in samples at 44.1 kHz. */
        val COMB_DELAYS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_DELAYS = intArrayOf(556, 441, 341, 225)
        const val ALLPASS_GAIN = 0.5f
        /** Highs lost in the feedback (Freeverb's 0.2 is for a brighter room; Square holds the highs damped). */
        const val DAMP = 0.3f
        /** Level of one comb's output against its input, and the wet level at the loudest send. */
        const val COMB_LEVEL = 0.35f
        const val WET_LEVEL = 0.7f
        const val KNEE = 0.9f
        /** Under this a filter state is zeroed. */
        const val DENORMAL = 1e-20f
        /** Frames between two steps of a glide (about 1.5 ms at 44.1 kHz) and the part of the way made at each step. */
        const val BLOCK = 64
        const val GLIDE_RATE = 0.03f
        /** Under this the amount counts as 0 once the dial is at 0. */
        const val SETTLED = 0.0005f
    }
}
