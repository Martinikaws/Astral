package com.music.spotui.wear

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import app.myspoti.automix.TrackAnalysis
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.host.android.PlaybackService
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

internal const val WATCH_ACTIVE_TTL_MS = 10 * 60_000L
internal const val POSITION_DEDUP_TOLERANCE_MS = 1_500L

internal enum class PublishMode { NONE, MINIMAL_LAZY, FULL_URGENT }

internal fun publishModeFor(hasWatch: Boolean, watchActive: Boolean): PublishMode = when {
    !hasWatch -> PublishMode.NONE
    watchActive -> PublishMode.FULL_URGENT
    else -> PublishMode.MINIMAL_LAZY
}

internal fun isWatchActiveAt(
    watchActive: Boolean,
    hasWatch: Boolean,
    nowElapsedMs: Long,
    untilElapsedMs: Long,
): Boolean = hasWatch && watchActive && nowElapsedMs < untilElapsedMs

internal fun buildArtKey(mediaId: String, coverUrl: String, artVersion: Int): String =
    "$mediaId|$coverUrl|$artVersion"

internal data class StateSignature(
    val hasSession: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val contextTitle: String,
    val playing: Boolean,
    val buffering: Boolean,
    val episode: Boolean,
    val explicit: Boolean,
    val liked: Boolean,
    val durationMs: Long,
    val speed: Float,
    val shuffle: Boolean,
    val repeat: String,
    val volume: Int,
    val deviceName: String,
    val deviceType: String,
    val bgColor: Int,
    val accentColor: Int,
    val artKey: String,
    val vizKey: String,
    val positionMs: Long,
    val atElapsedMs: Long,
)

internal fun isSemanticallyDuplicateState(
    prev: StateSignature?,
    next: StateSignature,
    toleranceMs: Long = POSITION_DEDUP_TOLERANCE_MS,
): Boolean {
    if (prev == null) return false
    if (prev.hasSession != next.hasSession ||
        prev.title != next.title ||
        prev.artist != next.artist ||
        prev.album != next.album ||
        prev.contextTitle != next.contextTitle ||
        prev.playing != next.playing ||
        prev.buffering != next.buffering ||
        prev.episode != next.episode ||
        prev.explicit != next.explicit ||
        prev.liked != next.liked ||
        prev.durationMs != next.durationMs ||
        prev.speed != next.speed ||
        prev.shuffle != next.shuffle ||
        prev.repeat != next.repeat ||
        prev.volume != next.volume ||
        prev.deviceName != next.deviceName ||
        prev.deviceType != next.deviceType ||
        prev.bgColor != next.bgColor ||
        prev.accentColor != next.accentColor ||
        prev.artKey != next.artKey ||
        prev.vizKey != next.vizKey
    ) {
        return false
    }
    val running = prev.playing && !prev.buffering
    val elapsedMs = if (running) (next.atElapsedMs - prev.atElapsedMs).coerceAtLeast(0L) else 0L
    val rawExpected = prev.positionMs + (elapsedMs * prev.speed).toLong()
    val expectedPos = if (prev.durationMs > 0L && prev.positionMs <= prev.durationMs) {
        rawExpected.coerceAtMost(prev.durationMs)
    } else {
        rawExpected
    }
    return abs(next.positionMs - expectedPos) <= toleranceMs
}

/**
 * The watch as a remote for what this phone plays: the watch's "Phone" mode (base/wear,
 * RemoteLink) shows the song, its place, the volume and shuffle, and sends back play / pause,
 * next, previous, seek, volume, shuffle and a song of the library to play.
 *
 * Driven through this app's own media session ([PlaybackService.sessionToken]), in this process:
 * everything the notification and Android Auto can do, nothing more; the library is the one
 * Android Auto browses ([PlaybackService.library]).
 *
 * The state goes out as a DataItem ([STATE], JSON and the visualizer amplitude curve): the Data Layer keeps
 * the last one, so a watch opened later shows it at once. Commands come as messages under
 * [COMMAND] (see [WearLinkService.onMessageReceived]). The watch's RemoteLink keeps the same paths.
 */
object WatchRemote {
    const val PREFIX = "/orpheon/remote"
    const val STATE = "$PREFIX/state"
    const val ART = "$PREFIX/art"
    const val VIZ = "$PREFIX/viz"
    const val COMMAND = "$PREFIX/cmd/"
    const val PAGE = "$PREFIX/page"
    /** What plays next (the session's queue), a DataItem of its own: sent only when it changes. */
    const val QUEUE = "$PREFIX/queue"
    /** Phone -> watch message: the account's Spotify Connect devices, answering "devices". */
    const val DEVICES = "$PREFIX/devices"
    private const val CAPABILITY_WATCH_REMOTE = "astral_watch_remote"
    private const val MAX_QUEUE = 50

    private const val ART_SIZE_PX = 64
    private const val ART_BLUR_RADIUS = 4
    private const val ART_JPEG_QUALITY = 68
    private const val PRESENCE_RECHECK_MS = 60_000L
    private const val MAX_ITEMS = 150
    private const val BROWSE_MS = 25_000L
    private const val DEFAULT_BG = 0xFF181A1E.toInt()
    private const val DEFAULT_ACCENT = 0xFF1ED760.toInt()
    private const val EPISODE_SKIP_MS = 15_000L
    private const val VIZ_WINDOW_SEC = 0.15
    private const val VIZ_MAX_SAMPLES = 3600L
    private const val VIZ_RETRY_TRANSIENT_MS = 20_000L
    private const val VIZ_RETRY_UNAVAILABLE_MS = 300_000L

    private val SPOTIFY_TRACK_ID_REGEX = Regex("(?:spotify:track:|open\\.spotify\\.com/track/)([A-Za-z0-9]{22})")
    private val BASE62_ID_REGEX = Regex("^[A-Za-z0-9]{22}$")

    private class VizData(
        val seed: Int,
        val stepMs: Int,
        val samples: ByteArray,
        val isFallback: Boolean = false,
    )

    private data class CurvePoint(val x: Double, val y: Double)

    private data class AnalysisSegment(
        val start: Double,
        val duration: Double,
        val loudnessStart: Double?,
        val loudnessMax: Double?,
        val loudnessMaxTime: Double?,
        val loudnessEnd: Double?,
    )

    private data class ObservedState(
        val liked: Boolean,
        val repeatMode: Int,
        val deviceName: String,
        val deviceType: String,
        val shuffle: Boolean,
        val buffering: Boolean,
        val contextName: String,
        val trackUri: String,
        val explicit: Boolean,
    )

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    private lateinit var app: Context
    private var controller: MediaControllerCompat? = null
    private var publishJob: Job? = null
    private var immediatePublishJob: Job? = null
    private var queuePublishJob: Job? = null
    private var stateObserverJob: Job? = null
    private var pendingDebouncedPublish: Boolean = false

    @Volatile private var hasConnectedWatch: Boolean = false
    @Volatile private var presenceChecked: Boolean = false
    @Volatile private var lastPresenceCheckAt: Long = 0L
    @Volatile private var watchActive: Boolean = false
    @Volatile private var watchActiveUntilElapsed: Long = 0L

    @Volatile private var lastMetadata: MediaMetadataCompat? = null
    private var lastArtMetaId: String = ""
    private var lastArtMetaUrl: String = ""
    private var lastArtMetaBmp: Bitmap? = null
    @Volatile private var artVersion: Int = 0

    private var lastStateSignature: StateSignature? = null
    private var lastPublishedArtKey: String = ""
    private var lastPublishedVizKey: String = ""
    private var lastPublishedVizSeed: Int = 0
    private var lastPublishedVizStepMs: Int = 100

    @Volatile private var currentVizKey: String = ""
    private val fetchingVizKeys = Collections.synchronizedSet(HashSet<String>())
    private val vizCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, VizData>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VizData>?) = size > 64
        }
    )
    private val vizRetryAt = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 64
        }
    )
    private val colorCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Int, Int>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>?) = size > 128
        }
    )
    private val artCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ByteArray>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 32
        }
    )

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info: CapabilityInfo ->
        main.post {
            if (info.nodes.isNotEmpty()) {
                lastPresenceCheckAt = SystemClock.elapsedRealtime()
                applyWatchPresence(present = true, triggerPublishOnGain = true)
            } else {
                refreshWatchPresence(triggerPublishOnGain = true)
            }
        }
    }

    private val callback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = schedulePublish()
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updateCachedMetadata(metadata)
            schedulePublish()
        }
        override fun onShuffleModeChanged(shuffleMode: Int) = schedulePublish()
        override fun onRepeatModeChanged(repeatMode: Int) = schedulePublish()
        override fun onAudioInfoChanged(info: MediaControllerCompat.PlaybackInfo?) = schedulePublish()
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) = scheduleQueuePublish()
        override fun onSessionDestroyed() = attach()
    }

    /** Called by [WearLinkService] when the watch UI opens or closes. Safe from any thread. */
    internal fun setWatchActive(active: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyWatchActive(active)
        } else {
            main.post { applyWatchActive(active) }
        }
    }

    private fun applyWatchActive(active: Boolean) {
        if (!::app.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (active) {
            hasConnectedWatch = true
            presenceChecked = true
            lastPresenceCheckAt = now
            val wasActive = isWatchActiveNow(now)
            watchActive = true
            watchActiveUntilElapsed = now + WATCH_ACTIVE_TTL_MS
            if (!wasActive) {
                lastStateSignature = null
            }
            schedulePublish(immediate = true)
        } else {
            watchActive = false
            watchActiveUntilElapsed = 0L
        }
    }

    private fun touchWatchActive() {
        val now = SystemClock.elapsedRealtime()
        hasConnectedWatch = true
        presenceChecked = true
        lastPresenceCheckAt = now
        watchActive = true
        watchActiveUntilElapsed = now + WATCH_ACTIVE_TTL_MS
    }

    private fun isWatchActiveNow(nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean {
        val active = isWatchActiveAt(watchActive, hasConnectedWatch, nowElapsed, watchActiveUntilElapsed)
        if (!active && watchActive) {
            watchActive = false
            watchActiveUntilElapsed = 0L
        }
        return active
    }

    private fun updateCachedMetadata(metadata: MediaMetadataCompat?) {
        val newMediaId = metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).orEmpty()
        val newCoverUrl = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI).orEmpty()
        val newBmp = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        val prevBmp = lastArtMetaBmp
        val bmpChanged = when {
            prevBmp == null && newBmp == null -> false
            prevBmp == null || newBmp == null -> true
            prevBmp === newBmp -> false
            prevBmp.isRecycled || newBmp.isRecycled -> true
            prevBmp.width != newBmp.width || prevBmp.height != newBmp.height -> true
            else -> !runCatching { prevBmp.sameAs(newBmp) }.getOrDefault(false)
        }
        if (newMediaId != lastArtMetaId || newCoverUrl != lastArtMetaUrl || bmpChanged) {
            artVersion++
            lastArtMetaId = newMediaId
            lastArtMetaUrl = newCoverUrl
            lastArtMetaBmp = newBmp
        }
        lastMetadata = metadata
    }

    private fun refreshWatchPresence(triggerPublishOnGain: Boolean = false) {
        if (!::app.isInitialized) return
        lastPresenceCheckAt = SystemClock.elapsedRealtime()
        scope.launch {
            val present = queryWatchPresence()
            applyWatchPresence(present, triggerPublishOnGain)
        }
    }

    private suspend fun queryWatchPresence(): Boolean {
        val capNodes = runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(CAPABILITY_WATCH_REMOTE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        if (!capNodes.isNullOrEmpty()) return true
        // Transitional fallback for paired watches that have not updated to advertise astral_watch_remote.
        val connected = runCatching {
            Wearable.getNodeClient(app).connectedNodes.await()
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        return !connected.isNullOrEmpty()
    }

    private fun applyWatchPresence(present: Boolean, triggerPublishOnGain: Boolean) {
        val wasPresent = hasConnectedWatch
        presenceChecked = true
        hasConnectedWatch = present
        if (!present) {
            watchActive = false
            watchActiveUntilElapsed = 0L
            pendingDebouncedPublish = false
            publishJob?.cancel()
            publishJob = null
            immediatePublishJob?.cancel()
            immediatePublishJob = null
            queuePublishJob?.cancel()
            queuePublishJob = null
        } else if (!wasPresent && triggerPublishOnGain) {
            schedulePublish()
        }
    }

    /** A message from the watch (any thread). */
    fun onMessage(context: Context, nodeId: String, path: String, data: ByteArray) {
        if (!path.startsWith(COMMAND)) return
        main.post {
            init(context)
            val command = path.removePrefix(COMMAND)
            val arg = data.decodeToString()
            if (command == "watch_open") {
                applyWatchActive(true)
                return@post
            }
            if (command == "watch_close") {
                applyWatchActive(false)
                return@post
            }
            val c = controller
            val controls = c?.transportControls
            when (command) {
                "hello" -> {
                    lastQueue = null
                    lastStateSignature = null
                    touchWatchActive()
                }
                "toggle" -> if (c?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                    controls?.pause() ?: com.music.spotui.di.SongPlayer.pause(by = "watch")
                } else {
                    if (controls != null) controls.play()
                    else {
                        PlaybackService.ensureStarted(app)
                        com.music.spotui.di.SongPlayer.play(by = "watch")
                    }
                }
                "play" -> {
                    PlaybackService.ensureStarted(app)
                    if (arg.isNotBlank()) {
                        PlaybackService.controls?.playFromMediaId(arg) ?: com.music.spotui.di.AutoBrowser.play(arg)
                    } else {
                        if (controls != null) controls.play() else com.music.spotui.di.SongPlayer.play(by = "watch")
                    }
                }
                "play_item" -> if (arg.isNotBlank()) {
                    PlaybackService.ensureStarted(app)
                    PlaybackService.controls?.playFromMediaId(arg) ?: com.music.spotui.di.AutoBrowser.play(arg)
                }
                "pause" -> controls?.pause() ?: com.music.spotui.di.SongPlayer.pause(by = "watch")
                "next" -> controls?.skipToNext() ?: com.music.spotui.di.SongPlayer.next(by = "watch")
                "previous" -> controls?.skipToPrevious() ?: com.music.spotui.di.SongPlayer.previous(by = "watch")
                "seek_forward" -> {
                    lastStateSignature = null
                    com.music.spotui.di.SongPlayer.skipBy(EPISODE_SKIP_MS)
                }
                "seek_back" -> {
                    lastStateSignature = null
                    com.music.spotui.di.SongPlayer.skipBy(-EPISODE_SKIP_MS)
                }
                "seek" -> arg.toLongOrNull()?.let { ms ->
                    lastStateSignature = null
                    if (controls != null) controls.seekTo(ms) else com.music.spotui.di.SongPlayer.seekTo(ms)
                }
                "like" -> com.music.spotui.di.SongPlayer.setLiked(!com.music.spotui.di.Graph.currentSongState.likeState.value)
                "shuffle" -> com.music.spotui.di.SongPlayer.setShuffle(!com.music.spotui.di.SongPlayer.isShuffleOn())
                "repeat" -> {
                    val next = when (com.music.spotui.di.SongPlayer.repeatMode.value) {
                        0 -> 1
                        1 -> 2
                        else -> 0
                    }
                    com.music.spotui.di.SongPlayer.setRepeatMode(next)
                }
                "volume" -> arg.toIntOrNull()?.let { setVolumePercent(c, it) }
                // A bezel click: 10 % up or down from where the volume is now, on the tens.
                "volume_up" -> stepVolumePercent(c, +10)
                "volume_down" -> stepVolumePercent(c, -10)
                "queue" -> arg.toLongOrNull()?.let { id ->
                    if (controls != null) controls.skipToQueueItem(id) else PlaybackService.controls?.skipToQueueItem(id)
                }
                "devices" -> {
                    touchWatchActive()
                    devices(nodeId)
                    return@post
                }
                "device" -> lastDevices.firstOrNull { it.id == arg }?.let { com.music.spotui.di.SongPlayer.transferTo(it) }
                "browse" -> {
                    touchWatchActive()
                    browse(nodeId, arg)
                    return@post
                }
                else -> return@post
            }
            touchWatchActive()
            // The phone's own volume moves no session callback: publish anyway.
            schedulePublish()
        }
    }

    /** From the app's start: the state is published before the watch asks, so it shows at once. Main thread. */
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        runCatching {
            Wearable.getCapabilityClient(app).addListener(capabilityListener, CAPABILITY_WATCH_REMOTE)
        }
        refreshWatchPresence(triggerPublishOnGain = true)
        PlaybackService.sessionListener = { main.post { attach() } }
        attach()
        if (stateObserverJob?.isActive != true) {
            stateObserverJob = scope.launch {
                snapshotFlow {
                    val dev = com.music.spotui.di.SongPlayer.activeDevice.value?.takeIf { !it.isSelf }
                    val np = com.music.spotui.di.SongPlayer.nowPlaying.value
                    ObservedState(
                        liked = com.music.spotui.di.Graph.currentSongState.likeState.value,
                        repeatMode = com.music.spotui.di.SongPlayer.repeatMode.value,
                        deviceName = dev?.name.orEmpty(),
                        deviceType = dev?.type?.lowercase().orEmpty(),
                        shuffle = com.music.spotui.di.Graph.currentSongState.shuffle.value,
                        buffering = com.music.spotui.di.SongPlayer.isBuffering,
                        contextName = np.contextName,
                        trackUri = np.uri,
                        explicit = np.explicit,
                    )
                }.distinctUntilChanged().drop(1).collect {
                    schedulePublish()
                }
            }
        }
    }

    /** Follows the session: it comes with the first song and goes when the app signs out. */
    private fun attach() {
        val token = PlaybackService.sessionToken
        if (token == controller?.sessionToken) {
            refreshWatchPresence(triggerPublishOnGain = true)
            return schedulePublish()
        }
        controller?.unregisterCallback(callback)
        controller = token?.let { runCatching { MediaControllerCompat(app, it) }.getOrNull() }
        updateCachedMetadata(controller?.metadata)
        controller?.registerCallback(callback, main)
        refreshWatchPresence(triggerPublishOnGain = true)
        schedulePublish()
    }

    /** Bursts of callbacks (a new song fires several): one publish after they settle. */
    private fun schedulePublish(immediate: Boolean = false) {
        if (!::app.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (!hasConnectedWatch) {
            if (!presenceChecked || now - lastPresenceCheckAt >= PRESENCE_RECHECK_MS) {
                refreshWatchPresence(triggerPublishOnGain = true)
            }
            return
        }
        if (now - lastPresenceCheckAt >= PRESENCE_RECHECK_MS) {
            refreshWatchPresence(triggerPublishOnGain = false)
        }
        if (immediate) {
            publishJob?.cancel()
            publishJob = null
            immediatePublishJob?.cancel()
            pendingDebouncedPublish = false
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    publish(forceState = true)
                } finally {
                    if (immediatePublishJob === job) {
                        immediatePublishJob = null
                        if (pendingDebouncedPublish) {
                            pendingDebouncedPublish = false
                            schedulePublish(immediate = false)
                        }
                    }
                }
            }
            immediatePublishJob = job
            job.start()
            return
        }
        if (immediatePublishJob?.isActive == true) {
            pendingDebouncedPublish = true
            return
        }
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(150)
            publish(forceState = false)
        }
    }

    private fun scheduleQueuePublish() {
        if (!::app.isInitialized) return
        if (publishModeFor(hasConnectedWatch, isWatchActiveNow()) != PublishMode.FULL_URGENT) return
        queuePublishJob?.cancel()
        queuePublishJob = scope.launch {
            delay(150)
            if (publishModeFor(hasConnectedWatch, isWatchActiveNow()) != PublishMode.FULL_URGENT) return@launch
            val c = controller
            publishQueue(c, c?.playbackState)
        }
    }

    private suspend fun publish(forceState: Boolean = false) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val mode = publishModeFor(hasConnectedWatch, isWatchActiveNow(nowElapsed))
        if (mode == PublishMode.NONE) return

        val c = controller
        val meta = lastMetadata
        val state = c?.playbackState
        val np = com.music.spotui.di.SongPlayer.nowPlaying.value
        val songState = com.music.spotui.di.Graph.currentSongState

        val title = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: songState.title.value
        val artist = meta?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: songState.singer.value
        val album = meta?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            ?: songState.album.value
        val contextTitle = np.contextName.ifBlank { c?.queueTitle?.toString().orEmpty() }
        val hasSession = (c != null && meta != null) || title.isNotBlank()
        val playing = state?.state == PlaybackStateCompat.STATE_PLAYING
        val buffering = state?.state == PlaybackStateCompat.STATE_BUFFERING || com.music.spotui.di.SongPlayer.isBuffering
        val episode = com.music.spotui.di.SongPlayer.isEpisode
        val explicit = np.explicit
        val liked = songState.likeState.value
        val positionMs = state?.positionNow() ?: com.music.spotui.di.SongPlayer.getCurrentPosition()
        val durationMs = (meta?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.takeIf { it > 0L }
            ?: com.music.spotui.di.SongPlayer.getDuration()).coerceAtLeast(0L)
        val speed = (state?.playbackSpeed ?: 1f).let { if (it <= 0f) 1f else it }
        val shuffle = com.music.spotui.di.SongPlayer.isShuffleOn()
        val repeat = when (com.music.spotui.di.SongPlayer.repeatMode.value) {
            0 -> "off"
            1 -> "all"
            else -> "one"
        }
        val volume = volumePercentOf(c)
        val activeDev = com.music.spotui.di.SongPlayer.activeDevice.value?.takeIf { !it.isSelf }
        val deviceName = activeDev?.name.orEmpty()
        val deviceType = activeDev?.type?.lowercase().orEmpty()

        val mediaId = meta?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).orEmpty()
            .ifBlank { songState.songId.value.toString() }
        val coverUrl = meta?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            ?: songState.coverUri.value
        val bmp = meta?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        val rawArtKey = buildArtKey(mediaId, coverUrl.orEmpty(), artVersion)

        val localTrack = if (com.music.spotui.di.LocalPlayer.active.value) com.music.spotui.di.LocalPlayer.current() else null
        val localSpotifyId = localTrack?.let { com.music.spotui.di.LocalPlayer.spotifyIdOf(it) }.orEmpty()
        val trackUri = np.uri.ifBlank {
            if (localSpotifyId.isNotBlank()) "spotify:track:$localSpotifyId"
            else localTrack?.uri?.toString().orEmpty()
        }
        val trackKey = trackUri.ifBlank { "$title|$artist|$durationMs" }
        currentVizKey = if (hasSession) trackKey else ""

        val bgColor: Int
        val accentColor: Int
        var artAvailable: Boolean
        var artBytesToUpload: ByteArray? = null
        val vizPublishKey: String
        var vizAvailable: Boolean
        var vizToUpload: VizData? = null

        if (mode == PublishMode.FULL_URGENT) {
            val spotifyTrackId = extractSpotifyTrackId(trackUri, localSpotifyId)
            val localAnalysis = localTrack?.uri?.let { com.music.spotui.automix.AutoMix.analysis(it) }
            val needArtBytes = hasSession && rawArtKey.isNotBlank() && rawArtKey != lastPublishedArtKey
            val (colors, resolvedArtBytes, vizData) = withContext(Dispatchers.Default) {
                val (cols, artBytes) = resolveColorsAndArt(rawArtKey, coverUrl, bmp, needArt = needArtBytes)
                val viz = if (!hasSession) {
                    VizData(seed = 0, stepMs = 100, samples = ByteArray(0))
                } else {
                    resolveViz(trackKey, spotifyTrackId, localAnalysis, durationMs)
                }
                Triple(cols, artBytes, viz)
            }
            bgColor = colors.first
            accentColor = colors.second
            artAvailable = hasSession && rawArtKey.isNotBlank() && rawArtKey == lastPublishedArtKey
            if (needArtBytes && resolvedArtBytes != null) {
                artBytesToUpload = resolvedArtBytes
            }
            vizPublishKey = if (!hasSession) "" else if (vizData.isFallback) "$trackKey#fb" else trackKey
            vizAvailable = hasSession && vizPublishKey.isNotBlank() && vizPublishKey == lastPublishedVizKey
            if (hasSession && vizPublishKey.isNotBlank() && vizPublishKey != lastPublishedVizKey) {
                vizToUpload = vizData
            }
        } else {
            val cachedColors = colorCache[rawArtKey] ?: cachedColorsFromPalette(coverUrl)
            bgColor = cachedColors?.first ?: DEFAULT_BG
            accentColor = cachedColors?.second
                ?: runCatching { com.music.spotui.ui.theme.AppAccent.toArgb() }.getOrNull()
                ?: DEFAULT_ACCENT
            artAvailable = hasSession && rawArtKey.isNotBlank() && rawArtKey == lastPublishedArtKey
            vizPublishKey = if (hasSession && (trackKey == lastPublishedVizKey || "$trackKey#fb" == lastPublishedVizKey)) {
                lastPublishedVizKey
            } else {
                ""
            }
            vizAvailable = vizPublishKey.isNotEmpty()
        }

        if (artBytesToUpload != null) {
            val artReq = PutDataMapRequest.create(ART).apply {
                dataMap.putString("key", rawArtKey)
                dataMap.putByteArray("art", artBytesToUpload)
            }.asPutDataRequest().setUrgent()
            runCatching { Wearable.getDataClient(app).putDataItem(artReq).await() }
                .onSuccess {
                    lastPublishedArtKey = rawArtKey
                    artAvailable = true
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logbook.log("watch remote: art not sent (${it.message})")
                }
        }

        if (vizToUpload != null) {
            val vizReq = PutDataMapRequest.create(VIZ).apply {
                dataMap.putString("key", vizPublishKey)
                dataMap.putInt("seed", vizToUpload.seed)
                dataMap.putInt("stepMs", vizToUpload.stepMs)
                dataMap.putByteArray("viz", vizToUpload.samples)
            }.asPutDataRequest().setUrgent()
            runCatching { Wearable.getDataClient(app).putDataItem(vizReq).await() }
                .onSuccess {
                    lastPublishedVizKey = vizPublishKey
                    lastPublishedVizSeed = vizToUpload.seed
                    lastPublishedVizStepMs = vizToUpload.stepMs
                    vizAvailable = true
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logbook.log("watch remote: viz not sent (${it.message})")
                }
        }

        val effectiveArtKey = if (artAvailable) rawArtKey else ""
        val effectiveVizKey = if (vizAvailable) vizPublishKey else ""
        val signature = StateSignature(
            hasSession = hasSession,
            title = title,
            artist = artist,
            album = album,
            contextTitle = contextTitle,
            playing = playing,
            buffering = buffering,
            episode = episode,
            explicit = explicit,
            liked = liked,
            durationMs = durationMs,
            speed = speed,
            shuffle = shuffle,
            repeat = repeat,
            volume = volume,
            deviceName = deviceName,
            deviceType = deviceType,
            bgColor = bgColor,
            accentColor = accentColor,
            artKey = effectiveArtKey,
            vizKey = effectiveVizKey,
            positionMs = positionMs,
            atElapsedMs = nowElapsed,
        )

        val duplicate = !forceState && isSemanticallyDuplicateState(lastStateSignature, signature)
        if (!duplicate) {
            val atEpochMs = System.currentTimeMillis()
            val vizSeedVal = if (effectiveVizKey.isNotEmpty()) lastPublishedVizSeed else 0
            val vizStepMsVal = if (effectiveVizKey.isNotEmpty()) lastPublishedVizStepMs else 100
            val json = buildJsonObject {
                put("hasSession", hasSession)
                put("title", title)
                put("artist", artist)
                put("album", album)
                put("contextTitle", contextTitle)
                put("playing", playing)
                put("buffering", buffering)
                put("episode", episode)
                put("explicit", explicit)
                put("liked", liked)
                put("positionMs", positionMs)
                put("durationMs", durationMs)
                put("speed", speed)
                put("atEpochMs", atEpochMs)
                put("volume", volume)
                put("maxVolume", 100)
                put("shuffle", shuffle)
                put("repeat", repeat)
                put("canRepeat", true)
                // Where it plays: a Connect device's name, or nothing for this phone (kept for backwards compatibility).
                activeDev?.let { put("device", it.name) }
                put("deviceName", deviceName)
                put("deviceType", deviceType)
                put("color", bgColor)
                put("accentColor", accentColor)
                put("accent", accentColor)
                put("artKey", effectiveArtKey)
                put("vizKey", effectiveVizKey)
                put("vizSeed", vizSeedVal)
                put("vizStepMs", vizStepMsVal)
            }.toString()

            val request = PutDataMapRequest.create(STATE).apply {
                dataMap.putString("json", json)
            }.asPutDataRequest().apply {
                if (mode == PublishMode.FULL_URGENT) setUrgent()
            }
            runCatching { Wearable.getDataClient(app).putDataItem(request).await() }
                .onSuccess { lastStateSignature = signature }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logbook.log("watch remote: state not sent (${it.message})")
                }
        }

        if (mode == PublishMode.FULL_URGENT) {
            publishQueue(c, state)
        }
    }

    private var lastQueue: String? = null

    /** The rows after (and the one of) the song playing (without CDN icons so the payload stays small). */
    private suspend fun publishQueue(c: MediaControllerCompat?, state: PlaybackStateCompat?) {
        val rows = c?.queue.orEmpty()
        val json = buildJsonObject {
            put("active", state?.activeQueueItemId ?: -1L)
            put("items", buildJsonArray {
                rows.take(MAX_QUEUE).forEach { q ->
                    add(buildJsonObject {
                        put("id", q.queueId)
                        put("title", q.description.title?.toString().orEmpty())
                        put("subtitle", q.description.subtitle?.toString().orEmpty())
                    })
                }
            })
        }.toString()
        if (json == lastQueue) return
        lastQueue = json
        val request = PutDataMapRequest.create(QUEUE).apply { dataMap.putString("json", json) }.asPutDataRequest()
        runCatching { Wearable.getDataClient(app).putDataItem(request).await() }
            .onFailure {
                if (it is CancellationException) throw it
                lastQueue = null
                Logbook.log("watch remote: queue not sent (${it.message})")
            }
    }

    /** The session's position is a snapshot: brought to now before it travels. */
    private fun PlaybackStateCompat.positionNow(): Long {
        if (state != PlaybackStateCompat.STATE_PLAYING) return position.coerceAtLeast(0L)
        return (position + ((SystemClock.elapsedRealtime() - lastPositionUpdateTime) * playbackSpeed).toLong()).coerceAtLeast(0L)
    }

    private fun cachedColorsFromPalette(coverUrl: String?): Pair<Int, Int>? {
        val url = coverUrl?.takeIf { it.isNotBlank() } ?: return null
        val cachedFirst = com.music.spotui.di.Palette.cachedFirst(url)
        val cachedSecond = com.music.spotui.di.Palette.cachedSecond(url)
        if (cachedFirst == null && cachedSecond == null) return null
        val bg = cachedFirst?.let {
            com.music.spotui.di.Palette.readableUnderWhite(it).toArgb()
        } ?: DEFAULT_BG
        val accent = cachedSecond?.toArgb()
            ?: runCatching { com.music.spotui.ui.theme.AppAccent.toArgb() }.getOrNull()
            ?: DEFAULT_ACCENT
        return bg to accent
    }

    /**
     * Single-scale bitmap path: extracts `soft` once (only if [artKey] isn't already cached),
     * scales once to [ART_SIZE_PX]x[ART_SIZE_PX], and runs both Palette extraction and 3-pass box
     * blur + JPEG compression on that single scaled bitmap without recycling [bmp].
     */
    private fun resolveColorsAndArt(
        artKey: String,
        coverUrl: String?,
        bmp: Bitmap?,
        needArt: Boolean,
    ): Pair<Pair<Int, Int>, ByteArray?> {
        val cachedColors = colorCache[artKey]
        val cachedArt = if (needArt && artKey.isNotBlank()) synchronized(artCache) { artCache[artKey] } else null
        if (cachedColors != null && (!needArt || cachedArt != null || artKey.isBlank())) {
            return cachedColors to cachedArt
        }

        var resolvedColors = cachedColors
        var resolvedArt = cachedArt

        if (bmp != null && !bmp.isRecycled && (resolvedColors == null || (needArt && resolvedArt == null && artKey.isNotBlank()))) {
            var soft: Bitmap? = null
            var scaled: Bitmap? = null
            try {
                soft = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
                if (soft != null && !soft.isRecycled) {
                    scaled = Bitmap.createScaledBitmap(soft, ART_SIZE_PX, ART_SIZE_PX, true)
                    if (resolvedColors == null) {
                        val palette = runCatching { Palette.from(scaled).generate() }.getOrNull()
                        if (palette != null) {
                            val url = coverUrl?.takeIf { it.isNotBlank() }
                            val fallbackFirst = url?.let { com.music.spotui.di.Palette.cachedFirst(it) }
                            val fallbackSecond = url?.let { com.music.spotui.di.Palette.cachedSecond(it) }
                            val bgRgb = palette.darkVibrantSwatch?.rgb
                                ?: palette.vibrantSwatch?.rgb
                                ?: palette.dominantSwatch?.rgb
                                ?: palette.darkMutedSwatch?.rgb
                                ?: fallbackFirst?.toArgb()
                            val bg = bgRgb?.let {
                                com.music.spotui.di.Palette.readableUnderWhite(Color(it or (0xFF shl 24))).toArgb()
                            } ?: DEFAULT_BG
                            val accentRgb = palette.vibrantSwatch?.rgb
                                ?: palette.lightVibrantSwatch?.rgb
                                ?: palette.dominantSwatch?.rgb
                                ?: fallbackSecond?.toArgb()
                            val accent = accentRgb?.let { it or (0xFF shl 24) } ?: DEFAULT_ACCENT
                            resolvedColors = (bg to accent).also { colorCache[artKey] = it }
                        }
                    }
                    if (needArt && resolvedArt == null && artKey.isNotBlank()) {
                        val w = scaled.width
                        val h = scaled.height
                        if (w > 0 && h > 0) {
                            val pixels = IntArray(w * h)
                            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                            boxBlur3Pass(pixels, w, h, ART_BLUR_RADIUS)
                            var blurred: Bitmap? = null
                            try {
                                blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                blurred.setPixels(pixels, 0, w, 0, 0, w, h)
                                val out = ByteArrayOutputStream(2048)
                                if (blurred.compress(Bitmap.CompressFormat.JPEG, ART_JPEG_QUALITY, out)) {
                                    resolvedArt = out.toByteArray().also { artCache[artKey] = it }
                                }
                            } finally {
                                blurred?.recycle()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fall back to URL palette / defaults below.
            } finally {
                if (scaled != null && scaled !== soft && scaled !== bmp) scaled.recycle()
                if (soft != null && soft !== bmp) soft.recycle()
            }
        }

        if (resolvedColors == null) {
            val fallback = cachedColorsFromPalette(coverUrl)
            if (fallback != null) {
                colorCache[artKey] = fallback
                resolvedColors = fallback
            } else {
                val defaultAccent = runCatching { com.music.spotui.ui.theme.AppAccent.toArgb() }.getOrNull() ?: DEFAULT_ACCENT
                resolvedColors = DEFAULT_BG to defaultAccent
            }
        }
        return resolvedColors to resolvedArt
    }

    private fun boxBlur3Pass(pixels: IntArray, w: Int, h: Int, radius: Int) {
        if (w <= 0 || h <= 0 || radius <= 0 || pixels.size < w * h) return
        val tmp = IntArray(w * h)
        repeat(3) {
            boxBlur1D(src = pixels, dst = tmp, lines = h, len = w, lineStride = w, pixelStride = 1, radius = radius)
            boxBlur1D(src = tmp, dst = pixels, lines = w, len = h, lineStride = 1, pixelStride = w, radius = radius)
        }
    }

    private fun boxBlur1D(
        src: IntArray,
        dst: IntArray,
        lines: Int,
        len: Int,
        lineStride: Int,
        pixelStride: Int,
        radius: Int
    ) {
        val div = radius * 2 + 1
        val half = div / 2
        val maxIdx = len - 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -radius..radius) {
                val c = src[base + k.coerceIn(0, maxIdx) * pixelStride]
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (i in 0 until len) {
                val r = ((rSum + half) / div).coerceIn(0, 255)
                val g = ((gSum + half) / div).coerceIn(0, 255)
                val b = ((bSum + half) / div).coerceIn(0, 255)
                dst[base + i * pixelStride] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                if (i + 1 < len) {
                    val outC = src[base + (i - radius).coerceIn(0, maxIdx) * pixelStride]
                    val inC = src[base + (i + radius + 1).coerceIn(0, maxIdx) * pixelStride]
                    rSum += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
                    gSum += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
                    bSum += (inC and 0xFF) - (outC and 0xFF)
                }
            }
        }
    }

    /** Extracts a 22-char base62 Spotify track ID from a track URI or local track metadata. */
    private fun extractSpotifyTrackId(uri: String, localSpotifyId: String): String? {
        SPOTIFY_TRACK_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.let { return it }
        return localSpotifyId.takeIf { BASE62_ID_REGEX.matches(it) }
    }

    /**
     * Resolves visualizer amplitude curve data: cache -> AutoMix local analysis -> async Spotify
     * audio-analysis fetch (returning a deterministic rhythmic fallback immediately meanwhile).
     */
    private fun resolveViz(
        trackKey: String,
        spotifyTrackId: String?,
        localAnalysis: TrackAnalysis?,
        durationMs: Long,
    ): VizData {
        synchronized(vizCache) { vizCache[trackKey] }?.let { return it }
        if (localAnalysis != null) {
            return vizFromAutoMix(trackKey, localAnalysis, durationMs).also { vizCache[trackKey] = it }
        }
        if (spotifyTrackId != null) {
            val nowMs = SystemClock.elapsedRealtime()
            val retryAt = vizRetryAt[trackKey] ?: 0L
            if (nowMs >= retryAt) {
                launchSpotifyAnalysisFetch(trackKey, spotifyTrackId, durationMs)
            }
        }
        return fallbackViz(trackKey, durationMs)
    }

    private fun launchSpotifyAnalysisFetch(trackKey: String, trackId: String, durationMs: Long) {
        if (!MyspotiRuntime.isStarted || !MyspotiRuntime.spotify.ready.value) return
        if (!fetchingVizKeys.add(trackKey)) return
        scope.launch(Dispatchers.IO) {
            try {
                val root = try {
                    MyspotiRuntime.spotify.raw.get(
                        "https://spclient.wg.spotify.com/audio-attributes/v1/audio-analysis/$trackId?format=json"
                    ) as? JsonObject
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    vizRetryAt[trackKey] = SystemClock.elapsedRealtime() + VIZ_RETRY_TRANSIENT_MS
                    return@launch
                }

                val parsed = root?.let { parseAudioAnalysis(trackKey, it, durationMs) }
                if (parsed != null) {
                    vizCache[trackKey] = parsed
                    vizRetryAt.remove(trackKey)
                    main.post {
                        if (trackKey == currentVizKey && isWatchActiveNow()) schedulePublish()
                    }
                } else {
                    vizRetryAt[trackKey] = SystemClock.elapsedRealtime() + VIZ_RETRY_UNAVAILABLE_MS
                }
            } finally {
                fetchingVizKeys.remove(trackKey)
            }
        }
    }

    private fun decibelsToAmplitude(db: Double): Double = 10.0.pow(db / 20.0).coerceIn(0.0, 1.0)

    private fun integrateLinearSegment(x1: Double, y1: Double, x2: Double, y2: Double): Double =
        0.5 * (x2 - x1) * (y1 + y2)

    /** Moving-average trapezoidal integral over [positionSec ± windowSize/2], ported from Konsl/spicetify-visualizer. */
    private fun sampleAmplitudeMovingAverage(
        curve: List<CurvePoint>,
        positionSec: Double,
        windowSize: Double = VIZ_WINDOW_SEC,
    ): Double {
        if (curve.isEmpty()) return 0.0
        if (curve.size == 1) return curve[0].y.coerceIn(0.0, 1.0)
        if (windowSize <= 0.0) return curve[0].y.coerceIn(0.0, 1.0)

        val halfWindow = windowSize / 2.0
        val windowStart = positionSec - halfWindow
        val windowEnd = positionSec + halfWindow

        var lo = 0
        var hi = curve.size - 2
        var startIdx = curve.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (curve[mid + 1].x > windowStart) {
                startIdx = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }

        var area = 0.0
        var i = startIdx
        while (i < curve.size - 1 && curve[i].x < windowEnd) {
            val p1 = curve[i]
            val p2 = curve[i + 1]
            val dx = p2.x - p1.x
            if (dx > 0.0) {
                val segStart = maxOf(p1.x, windowStart)
                val segEnd = minOf(p2.x, windowEnd)
                if (segEnd > segStart) {
                    val y1 = p1.y + ((segStart - p1.x) / dx) * (p2.y - p1.y)
                    val y2 = p1.y + ((segEnd - p1.x) / dx) * (p2.y - p1.y)
                    area += integrateLinearSegment(segStart, y1, segEnd, y2)
                }
            }
            i++
        }
        return (area / windowSize).coerceIn(0.0, 1.0)
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun parseAudioAnalysis(trackKey: String, root: JsonObject, durationMs: Long): VizData? {
        val rawSegments = root.arr("segments") ?: return null
        val parsedSegments = ArrayList<AnalysisSegment>(rawSegments.size)
        for (el in rawSegments) {
            val seg = el as? JsonObject ?: continue
            val start = seg.num("start")?.takeIf { it.isFinite() && it >= 0.0 } ?: continue
            val duration = seg.num("duration")?.takeIf { it.isFinite() } ?: 0.0
            parsedSegments.add(
                AnalysisSegment(
                    start = start,
                    duration = duration,
                    loudnessStart = seg.num("loudness_start")?.takeIf { it.isFinite() },
                    loudnessMax = seg.num("loudness_max")?.takeIf { it.isFinite() },
                    loudnessMaxTime = seg.num("loudness_max_time")?.takeIf { it.isFinite() },
                    loudnessEnd = seg.num("loudness_end")?.takeIf { it.isFinite() },
                )
            )
        }
        if (parsedSegments.isEmpty()) return null
        parsedSegments.sortBy { it.start }

        val points = ArrayList<CurvePoint>(parsedSegments.size * 2 + 1)
        for (seg in parsedSegments) {
            if (seg.loudnessStart != null) {
                points.add(CurvePoint(seg.start, decibelsToAmplitude(seg.loudnessStart)))
            }
            if (seg.loudnessMax != null && seg.loudnessMaxTime != null && (seg.loudnessMaxTime > 0.0 || seg.loudnessStart == null)) {
                points.add(CurvePoint(seg.start + seg.loudnessMaxTime.coerceAtLeast(0.0), decibelsToAmplitude(seg.loudnessMax)))
            }
        }
        val last = parsedSegments.last()
        val endDb = last.loudnessEnd ?: last.loudnessStart ?: -60.0
        points.add(CurvePoint(last.start + last.duration.coerceAtLeast(0.0), decibelsToAmplitude(endDb)))

        val curve = points.sortedBy { it.x }
        if (curve.size < 2) return null

        val trackDurMs = root.obj("track")?.num("duration")
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (it * 1000.0).roundToLong() }
            ?: 0L
        val curveDurMs = (curve.last().x * 1000.0).roundToLong().coerceAtLeast(1_000L)
        val effectiveDurMs = maxOf(1_000L, if (durationMs > 0L) durationMs else if (trackDurMs > 0L) trackDurMs else curveDurMs)
        val stepMs = vizStepMs(effectiveDurMs)
        val count = vizSampleCount(effectiveDurMs, stepMs)

        val samples = ByteArray(count) { i ->
            val posSec = (i.toLong() * stepMs) / 1000.0
            val amp = sampleAmplitudeMovingAverage(curve, posSec)
            (amp * 255.0).roundToInt().coerceIn(0, 255).toByte()
        }
        val seed = root.obj("meta")?.num("timestamp")?.takeIf { it.isFinite() }?.toLong()?.toInt()
            ?: trackKey.hashCode()
        return VizData(seed, stepMs, samples, isFallback = false)
    }

    private fun percentileBounds(values: FloatArray): Pair<Double, Double> {
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return -40.0 to -6.0
        val p10 = finite[((finite.size - 1) * 0.10).roundToInt().coerceIn(0, finite.lastIndex)].toDouble()
        val p95 = finite[((finite.size - 1) * 0.95).roundToInt().coerceIn(0, finite.lastIndex)].toDouble()
        return p10 to (p95 - p10).coerceAtLeast(3.0)
    }

    private fun vizStepMs(durationMs: Long, maxSamples: Long = VIZ_MAX_SAMPLES): Int =
        (durationMs / maxSamples).coerceIn(100L, Int.MAX_VALUE.toLong()).toInt()

    private fun vizSampleCount(durationMs: Long, stepMs: Int, maxSamples: Long = 4096L): Int =
        ((durationMs / stepMs) + 1L).coerceIn(1L, maxSamples).toInt()

    private fun vizFromAutoMix(trackKey: String, an: TrackAnalysis, durationMs: Long): VizData {
        val anDurMs = if (an.duration.isFinite() && an.duration > 0.0) (an.duration * 1000.0).roundToLong() else 0L
        val effectiveDurMs = maxOf(1_000L, if (durationMs > 0L) durationMs else anDurMs)
        val stepMs = vizStepMs(effectiveDurMs)
        val count = vizSampleCount(effectiveDurMs, stepMs)

        val (dbLow, dbSpan) = percentileBounds(an.barDb)
        val (lowDbLow, lowDbSpan) = percentileBounds(an.barLowDb)
        val bars = an.bars
        val barEnergy = DoubleArray(bars) { b ->
            val db = an.barDb.getOrNull(b)?.takeIf { it.isFinite() }?.toDouble() ?: dbLow
            val low = an.barLowDb.getOrNull(b)?.takeIf { it.isFinite() }?.toDouble() ?: lowDbLow
            val normDb = ((db - dbLow) / dbSpan).coerceIn(0.0, 1.0)
            val normLow = ((low - lowDbLow) / lowDbSpan).coerceIn(0.0, 1.0)
            (0.65 * normDb + 0.35 * normLow).coerceIn(0.05, 1.0)
        }

        val beats = an.beats.filter { it.isFinite() }.sorted()
        val beatPeriodSec = if (an.bpm.isFinite() && an.bpm > 10.0) (60.0 / an.bpm).coerceIn(0.2, 2.0) else 0.5
        val decaySec = (beatPeriodSec * 0.55).coerceIn(0.12, 0.38)
        var barIdx = 0
        var beatIdx = 0

        val samples = ByteArray(count) { i ->
            val tSec = (i.toLong() * stepMs) / 1000.0
            val barLevel = if (bars > 0) {
                while (barIdx + 1 < bars && an.barStarts[barIdx + 1] <= tSec) barIdx++
                if (tSec < an.barStarts[0]) {
                    barEnergy[0] * 0.5
                } else {
                    val cur = barEnergy[barIdx]
                    val next = barEnergy[(barIdx + 1).coerceAtMost(bars - 1)]
                    val start = an.barStarts[barIdx]
                    val end = an.barEnd(barIdx).takeIf { it.isFinite() }?.coerceAtLeast(start + 0.05) ?: (start + 2.0)
                    val frac = ((tSec - start) / (end - start)).coerceIn(0.0, 1.0)
                    cur + (next - cur) * frac
                }
            } else 0.45

            val pulse = if (beats.isNotEmpty()) {
                while (beatIdx + 1 < beats.size && beats[beatIdx + 1] <= tSec) beatIdx++
                if (tSec >= beats[0]) {
                    val dt = (tSec - beats[beatIdx]).coerceAtLeast(0.0)
                    exp(-dt / decaySec)
                } else 0.0
            } else if (an.bpm.isFinite() && an.bpm > 10.0) {
                val phase = (tSec % beatPeriodSec) / beatPeriodSec
                exp(-phase * 3.0)
            } else 0.0

            val amp = (0.18 + 0.55 * barLevel + 0.30 * barLevel * pulse).coerceIn(0.0, 1.0)
            (amp * 255.0).roundToInt().coerceIn(0, 255).toByte()
        }

        val seed = an.audioKey.takeIf { it.isNotBlank() }?.hashCode() ?: trackKey.hashCode()
        return VizData(seed, stepMs, samples, isFallback = false)
    }

    /** Deterministic rhythmic amplitude curve used immediately while async analysis loads or when offline. */
    private fun fallbackViz(trackKey: String, durationMs: Long): VizData {
        val seed = trackKey.hashCode()
        val durMs = (if (durationMs > 0L) durationMs else 180_000L).coerceIn(30_000L, 600_000L)
        val stepMs = vizStepMs(durMs, 1800L)
        val count = vizSampleCount(durMs, stepMs, 2048L)

        val bpm = 100.0 + (Math.floorMod(seed, 39))
        val beatHz = bpm / 60.0
        val phraseHz = beatHz / 7.0
        val subHz = beatHz * 0.565
        val p1 = (Math.floorMod(seed * 31, 628)) / 100.0
        val p2 = (Math.floorMod(seed * 17 + 19, 628)) / 100.0
        val p3 = (Math.floorMod(seed * 13 + 7, 628)) / 100.0

        val samples = ByteArray(count) { i ->
            val tSec = (i.toLong() * stepMs) / 1000.0
            val beat = 0.5 + 0.5 * sin(2.0 * PI * beatHz * tSec + p1)
            val phrase = 0.5 + 0.5 * sin(2.0 * PI * phraseHz * tSec + p2)
            val sub = 0.5 + 0.5 * sin(2.0 * PI * subHz * tSec + p3)
            val amp = (0.22 + 0.34 * beat + 0.24 * phrase + 0.16 * sub).coerceIn(0.08, 0.96)
            (amp * 255.0).roundToInt().coerceIn(0, 255).toByte()
        }
        return VizData(seed, stepMs, samples, isFallback = true)
    }

    /** Normalized 0..100 volume: Connect device volume if active, else remote media controller, else phone media stream. */
    private fun volumePercentOf(c: MediaControllerCompat?): Int {
        if (com.music.spotui.di.SongPlayer.activeDevice.value != null) {
            return com.music.spotui.di.SongPlayer.remoteVolume.coerceIn(0, 100)
        }
        val info = c?.playbackInfo
        if (info?.playbackType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            val max = info.maxVolume.coerceAtLeast(1)
            return ((info.currentVolume * 100 + max / 2) / max).coerceIn(0, 100)
        }
        val audio = app.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((cur * 100 + max / 2) / max).coerceIn(0, 100)
    }

    /**
     * One click of the watch's bezel: the next ten percent, applied here at once so clicks never
     * act on a stale level (the watch shows its own count meanwhile, then this one).
     */
    private fun stepVolumePercent(c: MediaControllerCompat?, delta: Int) {
        val currentPercent = volumePercentOf(c)
        val target = if (delta % 10 == 0) {
            (((currentPercent + 5) / 10) * 10 + delta).coerceIn(0, 100)
        } else {
            (currentPercent + delta).coerceIn(0, 100)
        }
        setVolumePercent(c, target)
    }

    private fun setVolumePercent(c: MediaControllerCompat?, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        if (com.music.spotui.di.SongPlayer.activeDevice.value != null) {
            com.music.spotui.di.SongPlayer.setRemoteVolume(clamped)
            return
        }
        val info = c?.playbackInfo
        if (info?.playbackType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            c.setVolumeTo(targetVolumeStep(info.currentVolume, info.maxVolume, clamped), 0)
            return
        }
        val audio = app.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        // No flag: no volume panel popping up on the phone.
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolumeStep(cur, max, clamped), 0)
    }

    private fun targetVolumeStep(currentStep: Int, maxStep: Int, targetPercent: Int): Int {
        val safeMax = maxStep.coerceAtLeast(1)
        val clampedPercent = targetPercent.coerceIn(0, 100)
        val currentPercent = ((currentStep * 100 + safeMax / 2) / safeMax).coerceIn(0, 100)
        var steps = ((clampedPercent * safeMax) + 50) / 100
        // A coarse scale (15 steps) can round back to where it was: always move at least one step.
        if (steps == currentStep && clampedPercent != currentPercent) {
            steps = currentStep + if (clampedPercent > currentPercent) 1 else -1
        }
        return steps.coerceIn(0, safeMax)
    }

    private var lastDevices: List<com.music.spotui.di.SongPlayer.ConnectDevice> = emptyList()

    /** The devices the phone's own picker lists (this phone first), answered to the watch [nodeId]. */
    private fun devices(nodeId: String) {
        scope.launch {
            val json = runCatching {
                val list = withTimeout(BROWSE_MS) { com.music.spotui.di.SongPlayer.devices() }
                lastDevices = list
                buildJsonObject {
                    put("items", buildJsonArray {
                        list.forEach { d ->
                            add(buildJsonObject {
                                put("id", d.id); put("name", d.name); put("type", d.type)
                                put("self", d.isSelf); put("active", d.isActive)
                            })
                        }
                    })
                }
            }.getOrElse { e -> buildJsonObject { put("error", e.message ?: e.javaClass.simpleName) } }.toString()
            runCatching { Wearable.getMessageClient(app).sendMessage(nodeId, DEVICES, json.encodeToByteArray()).await() }
                .onFailure { Logbook.log("watch remote: devices not sent (${it.message})") }
        }
    }

    /** The children of [parentId] in the Android Auto library, answered to the watch [nodeId] as a [PAGE] message. */
    private fun browse(nodeId: String, parentId: String) {
        scope.launch {
            val json = runCatching {
                val items = withTimeout(BROWSE_MS) {
                    PlaybackService.library?.children(app, parentId)
                        ?: com.music.spotui.di.AutoBrowser.children(app, parentId)
                }
                buildJsonObject {
                    put("parent", parentId)
                    put("items", buildJsonArray {
                        items.take(MAX_ITEMS).forEach { add(itemJson(it)) }
                    })
                }
            }.getOrElse { e ->
                Logbook.log("watch remote: $parentId not listed (${e.message})")
                buildJsonObject { put("parent", parentId); put("error", e.message ?: e.javaClass.simpleName) }
            }.toString()
            runCatching { Wearable.getMessageClient(app).sendMessage(nodeId, PAGE, json.encodeToByteArray()).await() }
                .onFailure { Logbook.log("watch remote: list not sent (${it.message})") }
        }
    }

    private fun itemJson(item: MediaBrowserCompat.MediaItem) = buildJsonObject {
        put("id", item.mediaId.orEmpty())
        put("title", item.description.title?.toString().orEmpty())
        put("subtitle", item.description.subtitle?.toString().orEmpty())
        put("browsable", item.isBrowsable)
        put("playable", item.isPlayable)
    }
}
