package app.myspoti.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

internal const val DEFAULT_BG_COLOR = 0xFF181A1E.toInt()
internal const val DEFAULT_ACCENT_COLOR = 0xFF1ED760.toInt()

/** What the phone plays, as WatchRemote last published it over the Wearable Data Layer. */
internal data class RemoteState(
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val contextTitle: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val episode: Boolean = false,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    /** [positionMs]'s timestamp on this watch's [SystemClock.elapsedRealtime]. */
    val atElapsedMs: Long = 0L,
    val volume: Int = 50,
    val maxVolume: Int = 100,
    val shuffle: Boolean = false,
    /** "off", "all" or "one". */
    val repeat: String = "off",
    val canRepeat: Boolean = true,
    val deviceName: String = "",
    val deviceType: String = "",
    val color: Int = DEFAULT_BG_COLOR,
    val accentColor: Int = DEFAULT_ACCENT_COLOR,
    val artKey: String = "",
    val vizSeed: Int = 0,
    val vizStepMs: Int = 100,
    val vizSamples: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteState) return false
        return hasSession == other.hasSession &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            contextTitle == other.contextTitle &&
            playing == other.playing &&
            buffering == other.buffering &&
            episode == other.episode &&
            explicit == other.explicit &&
            liked == other.liked &&
            positionMs == other.positionMs &&
            durationMs == other.durationMs &&
            speed == other.speed &&
            atElapsedMs == other.atElapsedMs &&
            volume == other.volume &&
            maxVolume == other.maxVolume &&
            shuffle == other.shuffle &&
            repeat == other.repeat &&
            canRepeat == other.canRepeat &&
            deviceName == other.deviceName &&
            deviceType == other.deviceType &&
            color == other.color &&
            accentColor == other.accentColor &&
            artKey == other.artKey &&
            vizSeed == other.vizSeed &&
            vizStepMs == other.vizStepMs &&
            vizSamples.contentEquals(other.vizSamples)
    }

    override fun hashCode(): Int {
        var result = hasSession.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + contextTitle.hashCode()
        result = 31 * result + playing.hashCode()
        result = 31 * result + buffering.hashCode()
        result = 31 * result + episode.hashCode()
        result = 31 * result + explicit.hashCode()
        result = 31 * result + liked.hashCode()
        result = 31 * result + positionMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + atElapsedMs.hashCode()
        result = 31 * result + volume
        result = 31 * result + maxVolume
        result = 31 * result + shuffle.hashCode()
        result = 31 * result + repeat.hashCode()
        result = 31 * result + canRepeat.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + deviceType.hashCode()
        result = 31 * result + color
        result = 31 * result + accentColor
        result = 31 * result + artKey.hashCode()
        result = 31 * result + vizSeed
        result = 31 * result + vizStepMs
        result = 31 * result + vizSamples.contentHashCode()
        return result
    }
}

/** A row of the phone's library (browsable folder or playable item). */
internal data class RemoteItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val browsable: Boolean = false,
    val playable: Boolean = false,
)

/** A Spotify Connect device of the account: [self] the phone, [active] the one playing. */
internal data class RemoteDevice(
    val id: String,
    val name: String,
    val type: String,
    val self: Boolean,
    val active: Boolean,
)

/** The device list, or why the phone could not read it. */
internal data class RemoteDevices(
    val items: List<RemoteDevice>,
    val error: String? = null,
)

/** What plays next on the phone: its rows, [active] the one playing (-1: none marked). */
internal data class RemoteQueue(
    val items: List<RemoteItem> = emptyList(),
    val active: Long = -1L,
)

/** The children of a library node, or why the phone could not list them. */
internal data class RemotePage(
    val items: List<RemoteItem> = emptyList(),
    val error: String? = null,
)

/**
 * The watch's Wearable Data Layer link to `com.music.spotui.wear.WatchRemote` (`base/app`):
 * receives playback state, queue, devices, library pages, and compact visualizer waveform samples,
 * and sends back commands with immediate optimistic state holds so taps & bezel turns feel instant.
 */
internal object RemoteLink {
    private const val PREFIX = "/orpheon/remote"
    private const val STATE = "$PREFIX/state"
    private const val COMMAND = "$PREFIX/cmd/"
    private const val PAGE = "$PREFIX/page"
    private const val QUEUE = "$PREFIX/queue"
    private const val DEVICES = "$PREFIX/devices"
    private const val NODE_TTL_MS = 60_000L
    private const val HOLD_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var pagesLoaded = false

    private var volumeHoldUntil = 0L
    private var heldVolume = 50
    private var likedHoldUntil = 0L
    private var heldLiked = false
    private var shuffleHoldUntil = 0L
    private var heldShuffle = false
    private var repeatHoldUntil = 0L
    private var heldRepeat = "off"
    private var playingHoldUntil = 0L
    private var heldPlaying = false

    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = _state

    private val _cover = MutableStateFlow<Bitmap?>(null)
    val cover: StateFlow<Bitmap?> = _cover

    @Volatile
    private var decodedArtKey = ""

    /** null until the phone answered once; false: no phone connected. */
    private val _connected = MutableStateFlow<Boolean?>(null)
    val connected: StateFlow<Boolean?> = _connected

    private val _queue = MutableStateFlow(RemoteQueue())
    val queue: StateFlow<RemoteQueue> = _queue

    private val _devices = MutableStateFlow<RemoteDevices?>(null)
    val devices: StateFlow<RemoteDevices?> = _devices

    private val _pages = MutableStateFlow<Map<String, RemotePage>>(emptyMap())
    val pages: StateFlow<Map<String, RemotePage>> = _pages

    private val dataListener = DataClient.OnDataChangedListener { events ->
        events.forEach { e ->
            if (e.type != DataEvent.TYPE_CHANGED) return@forEach
            val path = e.dataItem.uri.path
            val map = DataMapItem.fromDataItem(e.dataItem).dataMap
            scope.launch(Dispatchers.Main.immediate) {
                when (path) {
                    STATE -> onState(map)
                    QUEUE -> onQueue(map)
                }
            }
        }
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { e ->
        val path = e.path
        val payload = e.data.decodeToString()
        scope.launch(Dispatchers.Main.immediate) {
            when (path) {
                PAGE -> runCatching { onPage(payload) }
                DEVICES -> runCatching { onDevices(payload) }
            }
        }
    }

    /** Starts listening while the watch UI is active and requests fresh state from the phone. */
    fun start(context: Context) {
        app = context.applicationContext
        loadPages()
        Wearable.getDataClient(app).addListener(dataListener)
        Wearable.getMessageClient(app).addListener(messageListener)
        scope.launch {
            lastState()
            send("hello")
        }
    }

    fun stop() {
        if (!::app.isInitialized) return
        Wearable.getDataClient(app).removeListener(dataListener)
        Wearable.getMessageClient(app).removeListener(messageListener)
        clearHolds()
        phoneNode = null
        phoneNodeAt = 0L
        decodedArtKey = ""
        _cover.value = null
    }

    /** Asks the phone for its Connect devices. */
    fun askDevices() = send("devices")

    /** Asks for [parentId]'s children; cached items stay visible meanwhile. */
    fun browse(parentId: String) = send("browse", parentId)

    /** Cached phone node id; refreshed every minute or after a failed send. */
    private var phoneNode: String? = null
    private var phoneNodeAt = 0L

    private suspend fun phone(): String? {
        val now = SystemClock.elapsedRealtime()
        phoneNode?.let { if (now - phoneNodeAt < NODE_TTL_MS) return it }
        val nodes = runCatching { Wearable.getNodeClient(app).connectedNodes.await() }.getOrDefault(emptyList())
        val node = (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
        phoneNode = node
        phoneNodeAt = now
        _connected.value = node != null
        return node
    }

    fun send(command: String, arg: String = "") {
        applyOptimistic(command, arg)
        if (!::app.isInitialized) return
        val known = phoneNode?.takeIf { SystemClock.elapsedRealtime() - phoneNodeAt < NODE_TTL_MS }
        if (known != null) {
            val task = Wearable.getMessageClient(app).sendMessage(known, COMMAND + command, arg.encodeToByteArray())
            task.addOnFailureListener {
                phoneNode = null
                _connected.value = false
            }
            return
        }
        scope.launch {
            val node = phone() ?: return@launch
            runCatching {
                Wearable.getMessageClient(app).sendMessage(node, COMMAND + command, arg.encodeToByteArray()).await()
            }.onFailure {
                phoneNode = null
                _connected.value = false
            }
        }
    }

    private fun applyOptimistic(command: String, arg: String) {
        when (command) {
            "hello" -> clearHolds()
            "volume" -> arg.toIntOrNull()?.coerceIn(0, 100)?.let(::holdVolume)
            "volume_up" -> holdVolume((((_state.value.volume + 5) / 10) * 10 + 10).coerceIn(0, 100))
            "volume_down" -> holdVolume((((_state.value.volume + 5) / 10) * 10 - 10).coerceIn(0, 100))
            "like" -> holdLiked(!_state.value.liked)
            "shuffle" -> holdShuffle(!_state.value.shuffle)
            "repeat" -> holdRepeat(when (_state.value.repeat) { "off" -> "all"; "all" -> "one"; else -> "off" })
            "toggle" -> holdPlaying(!_state.value.playing)
            "pause" -> holdPlaying(false)
            "play" -> if (arg.isBlank()) holdPlaying(true)
        }
    }

    private fun holdVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        heldVolume = clamped
        volumeHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        _state.value = _state.value.copy(volume = clamped)
    }

    private fun holdLiked(liked: Boolean) {
        heldLiked = liked
        likedHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        _state.value = _state.value.copy(liked = liked)
    }

    private fun holdShuffle(shuffle: Boolean) {
        heldShuffle = shuffle
        shuffleHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        _state.value = _state.value.copy(shuffle = shuffle)
    }

    private fun holdRepeat(repeat: String) {
        heldRepeat = repeat
        repeatHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        _state.value = _state.value.copy(repeat = repeat)
    }

    private fun holdPlaying(playing: Boolean) {
        heldPlaying = playing
        playingHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        val cur = _state.value
        val now = SystemClock.elapsedRealtime()
        val updatedPos = if (!playing && cur.playing && !cur.buffering) {
            (cur.positionMs + ((now - cur.atElapsedMs).coerceAtLeast(0L) * cur.speed).toLong()).coerceAtLeast(0L)
        } else {
            cur.positionMs
        }
        _state.value = cur.copy(playing = playing, positionMs = updatedPos, atElapsedMs = now)
    }

    private fun clearHolds() {
        volumeHoldUntil = 0L
        likedHoldUntil = 0L
        shuffleHoldUntil = 0L
        repeatHoldUntil = 0L
        playingHoldUntil = 0L
    }

    private val pageTexts = mutableMapOf<String, String>()
    private val pagesFile get() = File(app.filesDir, "remote_pages.json")

    private fun loadPages() {
        if (pagesLoaded || pageTexts.isNotEmpty()) return
        pagesLoaded = true
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching {
                if (!pagesFile.exists()) return@runCatching emptyMap<String, Pair<String, RemotePage>>()
                val saved = Json.parseToJsonElement(pagesFile.readText()).jsonObject
                buildMap {
                    saved.forEach { (parent, el) ->
                        val raw = (el as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        val parsed = parsePage(raw) ?: return@forEach
                        if (parsed.second.error == null) put(parent, raw to parsed.second)
                    }
                }
            }.getOrDefault(emptyMap())
            if (loaded.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                val merged = _pages.value.toMutableMap()
                var changed = false
                loaded.forEach { (parent, entry) ->
                    if (!merged.containsKey(parent)) {
                        merged[parent] = entry.second
                        pageTexts.putIfAbsent(parent, entry.first)
                        changed = true
                    }
                }
                if (changed) _pages.value = merged
            }
        }
    }

    private fun savePages() {
        val snapshot = JsonObject(pageTexts.mapValues { JsonPrimitive(it.value) }).toString()
        scope.launch(Dispatchers.IO) { runCatching { pagesFile.writeText(snapshot) } }
    }

    /** Loads the last Data Layer state & queue so the screen populates before the phone answers. */
    private suspend fun lastState() {
        for (path in listOf(STATE, QUEUE)) {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()
            runCatching {
                val items = Wearable.getDataClient(app).getDataItems(uri).await()
                try {
                    items.firstOrNull()?.let { item ->
                        val map = DataMapItem.fromDataItem(item).dataMap
                        if (path == STATE) onState(map) else onQueue(map)
                    }
                } finally {
                    items.release()
                }
            }
        }
    }

    private fun onQueue(map: DataMap) {
        val j = runCatching { Json.parseToJsonElement(map.getString("json").orEmpty()).jsonObject }.getOrNull() ?: return
        val items = j["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let {
                RemoteItem(it.text("id"), it.text("title"), it.text("subtitle"), browsable = false, playable = true)
            }
        }
        val active = j["active"]?.let { (it as? JsonPrimitive)?.longOrNull } ?: -1L
        _queue.value = RemoteQueue(items, active)
    }

    private fun onState(map: DataMap) {
        val j = runCatching { Json.parseToJsonElement(map.getString("json").orEmpty()).jsonObject }.getOrNull() ?: return
        val epoch = j.long("atEpochMs")
        val now = SystemClock.elapsedRealtime()
        val at = if (epoch > 0L) now - (System.currentTimeMillis() - epoch) else now
        val parsedSpeed = (j.float("speed") ?: 1f).let { if (it > 0f) it else 1f }
        val artKey = j.text("artKey")
        val artBytes = map.getByteArray("art")
        if (artKey.isEmpty() || artBytes == null || artBytes.isEmpty()) {
            decodedArtKey = ""
            _cover.value = null
        } else if (artKey != decodedArtKey || _cover.value == null) {
            val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            if (bmp != null) {
                decodedArtKey = artKey
                _cover.value = bmp
            }
        }
        val vizStepMs = (j.nullableInt("vizStepMs") ?: 100).coerceAtLeast(20)
        val vizBytes = map.getByteArray("viz") ?: ByteArray(0)
        val prev = _state.value
        val stableSamples = if (vizBytes.contentEquals(prev.vizSamples)) prev.vizSamples else vizBytes
        val incoming = RemoteState(
            hasSession = j.bool("hasSession"),
            title = j.text("title"),
            artist = j.text("artist"),
            album = j.text("album"),
            contextTitle = j.text("contextTitle"),
            playing = j.bool("playing"),
            buffering = j.bool("buffering"),
            episode = j.bool("episode"),
            explicit = j.bool("explicit"),
            liked = j.bool("liked"),
            positionMs = j.long("positionMs"),
            durationMs = j.long("durationMs"),
            speed = parsedSpeed,
            atElapsedMs = at,
            volume = j.nullableInt("volume") ?: 50,
            maxVolume = j.int("maxVolume").takeIf { it > 0 } ?: 100,
            shuffle = j.bool("shuffle"),
            repeat = j.text("repeat").ifEmpty { "off" },
            canRepeat = j.nullableBool("canRepeat") ?: true,
            deviceName = j.text("deviceName").ifEmpty { j.text("device") },
            deviceType = j.text("deviceType"),
            color = j.nullableInt("color") ?: DEFAULT_BG_COLOR,
            accentColor = j.nullableInt("accentColor") ?: j.nullableInt("accent") ?: DEFAULT_ACCENT_COLOR,
            artKey = artKey,
            vizSeed = j.int("vizSeed"),
            vizStepMs = vizStepMs,
            vizSamples = stableSamples,
        )

        val trackChanged = incoming.title != prev.title && prev.title.isNotEmpty()
        if (trackChanged) likedHoldUntil = 0L
        _state.value = incoming.copy(
            volume = if (now < volumeHoldUntil) heldVolume else incoming.volume,
            liked = if (!trackChanged && now < likedHoldUntil) heldLiked else incoming.liked,
            shuffle = if (now < shuffleHoldUntil) heldShuffle else incoming.shuffle,
            repeat = if (now < repeatHoldUntil) heldRepeat else incoming.repeat,
            playing = if (now < playingHoldUntil) heldPlaying else incoming.playing,
        )
        _connected.value = true
    }

    private fun onDevices(text: String) {
        val j = Json.parseToJsonElement(text).jsonObject
        val items = j["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let {
                RemoteDevice(it.text("id"), it.text("name"), it.text("type"), it.bool("self"), it.bool("active"))
            }
        }
        val error = j.text("error").takeIf { it.isNotEmpty() }
        _devices.value = RemoteDevices(items, error)
    }

    private fun parsePage(text: String): Pair<String, RemotePage>? = runCatching {
        val j = Json.parseToJsonElement(text).jsonObject
        val parent = j.text("parent")
        val items = j["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let {
                RemoteItem(it.text("id"), it.text("title"), it.text("subtitle"), it.bool("browsable"), it.bool("playable"))
            }
        }
        val error = j.text("error").takeIf { it.isNotEmpty() }
        parent to RemotePage(items, error)
    }.getOrNull()

    private fun onPage(text: String, save: Boolean = true) {
        val (parent, page) = parsePage(text) ?: return
        // Keep previously cached items visible if a live refresh fails.
        if (page.error != null && _pages.value[parent]?.items?.isNotEmpty() == true) return
        _pages.value = _pages.value + (parent to page)
        if (save && page.error == null) {
            pageTexts[parent] = text
            savePages()
        }
    }

    private fun JsonObject.text(key: String) = this[key]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    private fun JsonObject.nullableBool(key: String) = this[key]?.let { (it as? JsonPrimitive)?.booleanOrNull }
    private fun JsonObject.bool(key: String) = nullableBool(key) ?: false
    private fun JsonObject.long(key: String) = this[key]?.let { (it as? JsonPrimitive)?.longOrNull } ?: 0L
    private fun JsonObject.nullableInt(key: String) = this[key]?.let { (it as? JsonPrimitive)?.intOrNull }
    private fun JsonObject.int(key: String) = nullableInt(key) ?: 0
    private fun JsonObject.float(key: String) = this[key]?.let { (it as? JsonPrimitive)?.floatOrNull }
}
