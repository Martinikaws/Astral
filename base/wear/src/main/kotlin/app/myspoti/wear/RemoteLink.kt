package app.myspoti.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
internal const val MAX_CACHED_PAGES = 16
internal const val BROWSE_FRESH_MS = 45_000L

/** What the phone plays, as WatchRemote last published it over the Wearable Data Layer. */
@Immutable
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
)

/** Visualizer waveform curve separated from [RemoteState] so ByteArray updates stay isolated. */
@Immutable
internal data class VizSnapshot(
    val key: String = "",
    val seed: Int = 0,
    val stepMs: Int = 100,
    val samples: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VizSnapshot) return false
        return key == other.key &&
            seed == other.seed &&
            stepMs == other.stepMs &&
            samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + seed
        result = 31 * result + stepMs
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

/** A row of the phone's library (browsable folder or playable item). */
@Immutable
internal data class RemoteItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val browsable: Boolean = false,
    val playable: Boolean = false,
)

/** A Spotify Connect device of the account: [self] the phone, [active] the one playing. */
@Immutable
internal data class RemoteDevice(
    val id: String,
    val name: String,
    val type: String,
    val self: Boolean,
    val active: Boolean,
)

/** The device list, or why the phone could not read it. */
@Immutable
internal data class RemoteDevices(
    val items: List<RemoteDevice>,
    val error: String? = null,
)

/** What plays next on the phone: its rows, [active] the one playing (-1: none marked). */
@Immutable
internal data class RemoteQueue(
    val items: List<RemoteItem> = emptyList(),
    val active: Long = -1L,
)

/** The children of a library node, or why the phone could not list them. */
@Immutable
internal data class RemotePage(
    val items: List<RemoteItem> = emptyList(),
    val error: String? = null,
)

/** Bounded LRU cache for browsed library pages with per-entry freshness tracking. */
internal class PageLruCache(
    private val maxEntries: Int = MAX_CACHED_PAGES,
    private val freshMs: Long = BROWSE_FRESH_MS,
) {
    private val entries = object : LinkedHashMap<String, String>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }
    private val fetchedAt = HashMap<String, Long>()

    @Synchronized
    fun put(parent: String, rawJson: String, nowMs: Long? = null) {
        entries[parent] = rawJson
        if (nowMs != null) fetchedAt[parent] = nowMs
        if (fetchedAt.size > entries.size) {
            fetchedAt.keys.retainAll(entries.keys)
        }
    }

    @Synchronized
    fun get(parent: String): String? = entries[parent]

    @Synchronized
    fun isFresh(parent: String, nowMs: Long): Boolean {
        val ts = fetchedAt[parent] ?: return false
        return entries.containsKey(parent) && (nowMs - ts) in 0 until freshMs
    }

    @Synchronized
    fun snapshot(): Map<String, String> = LinkedHashMap(entries)

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun contains(parent: String): Boolean = entries.containsKey(parent)
}

/**
 * The watch's Wearable Data Layer link to `com.music.spotui.wear.WatchRemote` (`base/app`):
 * receives playback state, artwork, queue, devices, library pages, and compact visualizer waveform
 * samples, and sends back commands with immediate optimistic state holds so taps & bezel turns
 * feel instant.
 */
internal object RemoteLink {
    private const val PREFIX = "/orpheon/remote"
    private const val STATE = "$PREFIX/state"
    private const val ART = "$PREFIX/art"
    private const val VIZ = "$PREFIX/viz"
    private const val COMMAND = "$PREFIX/cmd/"
    private const val PAGE = "$PREFIX/page"
    private const val QUEUE = "$PREFIX/queue"
    private const val DEVICES = "$PREFIX/devices"
    private const val NODE_TTL_MS = 60_000L
    private const val HOLD_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dataMutex = Mutex()
    private val savePagesMutex = Mutex()
    private val holdLock = Any()
    private lateinit var app: Context
    @Volatile private var started = false
    @Volatile private var pagesLoaded = false
    @Volatile private var sessionGen = 0L
    private var startJob: Job? = null
    private var closeJob: Job? = null

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

    private val _viz = MutableStateFlow(VizSnapshot())
    val viz: StateFlow<VizSnapshot> = _viz

    private val _cover = MutableStateFlow<Bitmap?>(null)
    val cover: StateFlow<Bitmap?> = _cover

    @Volatile private var decodedArtKey = ""
    @Volatile private var pendingArtKey = ""
    @Volatile private var pendingArtBytes: ByteArray? = null
    @Volatile private var expectedVizKey = ""
    @Volatile private var pendingViz: VizSnapshot? = null
    @Volatile private var lastStateHasSeen = false

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
        val gen = sessionGen
        if (!started) return@OnDataChangedListener
        events.forEach { e ->
            if (e.type != DataEvent.TYPE_CHANGED) return@forEach
            val path = e.dataItem.uri.path
            val map = DataMapItem.fromDataItem(e.dataItem).dataMap
            scope.launch(Dispatchers.Default) {
                if (!started || sessionGen != gen) return@launch
                dataMutex.withLock {
                    if (!started || sessionGen != gen) return@withLock
                    when (path) {
                        STATE -> onState(map, gen)
                        ART -> onArt(map, gen)
                        VIZ -> onViz(map, gen)
                        QUEUE -> onQueue(map, gen)
                    }
                }
            }
        }
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { e ->
        val gen = sessionGen
        if (!started) return@OnMessageReceivedListener
        val path = e.path
        val payload = e.data.decodeToString()
        scope.launch(Dispatchers.Default) {
            if (!started || sessionGen != gen) return@launch
            dataMutex.withLock {
                if (!started || sessionGen != gen) return@withLock
                when (path) {
                    PAGE -> runCatching { onPage(payload, gen) }
                    DEVICES -> runCatching { onDevices(payload, gen) }
                }
            }
        }
    }

    /** Starts listening while the watch UI is active and requests fresh state from the phone. */
    fun start(context: Context) {
        app = context.applicationContext
        if (started) return
        closeJob?.cancel()
        closeJob = null
        val gen = ++sessionGen
        started = true
        startJob?.cancel()
        loadPages()
        Wearable.getDataClient(app).addListener(dataListener)
        Wearable.getMessageClient(app).addListener(messageListener)
        startJob = scope.launch {
            lastState(gen)
            if (!started || sessionGen != gen) return@launch
            send("watch_open")
        }
    }

    fun stop() {
        if (!::app.isInitialized || !started) return
        started = false
        val stopGen = ++sessionGen
        startJob?.cancel()
        startJob = null
        val known = phoneNode
        closeJob?.cancel()
        closeJob = scope.launch {
            val node = known ?: run {
                val nodes = runCatching { Wearable.getNodeClient(app).connectedNodes.await() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
                (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
            } ?: return@launch
            if (started || sessionGen != stopGen) return@launch
            runCatching {
                Wearable.getMessageClient(app).sendMessage(node, COMMAND + "watch_close", ByteArray(0)).await()
            }.onFailure { if (it is CancellationException) throw it }
        }
        Wearable.getDataClient(app).removeListener(dataListener)
        Wearable.getMessageClient(app).removeListener(messageListener)
        clearHolds()
        phoneNode = null
        phoneNodeAt = 0L
        _cover.value = null
        scope.launch(Dispatchers.Default) {
            dataMutex.withLock {
                if (sessionGen == stopGen) {
                    decodedArtKey = ""
                    pendingArtKey = ""
                    pendingArtBytes = null
                    pendingViz = null
                    expectedVizKey = ""
                    lastStateHasSeen = false
                }
            }
        }
    }

    /** Asks the phone for its Connect devices. */
    fun askDevices() = send("devices")

    /** Asks for [parentId]'s children when stale or forced; cached items stay visible meanwhile. */
    fun browse(parentId: String, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && pageCache.isFresh(parentId, now) && _pages.value[parentId]?.error == null) {
            return
        }
        send("browse", parentId)
    }

    /** Cached phone node id; refreshed every minute or after a failed send. */
    @Volatile private var phoneNode: String? = null
    @Volatile private var phoneNodeAt = 0L

    private suspend fun phone(): String? {
        val now = SystemClock.elapsedRealtime()
        phoneNode?.let { if (now - phoneNodeAt < NODE_TTL_MS) return it }
        val nodes = runCatching { Wearable.getNodeClient(app).connectedNodes.await() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(emptyList())
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
                if (it is CancellationException) throw it
                phoneNode = null
                _connected.value = false
            }
        }
    }

    private fun applyOptimistic(command: String, arg: String) {
        when (command) {
            "hello", "watch_open" -> clearHolds()
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
        synchronized(holdLock) {
            heldVolume = clamped
            volumeHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        }
        _state.value = _state.value.copy(volume = clamped)
    }

    private fun holdLiked(liked: Boolean) {
        synchronized(holdLock) {
            heldLiked = liked
            likedHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        }
        _state.value = _state.value.copy(liked = liked)
    }

    private fun holdShuffle(shuffle: Boolean) {
        synchronized(holdLock) {
            heldShuffle = shuffle
            shuffleHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        }
        _state.value = _state.value.copy(shuffle = shuffle)
    }

    private fun holdRepeat(repeat: String) {
        synchronized(holdLock) {
            heldRepeat = repeat
            repeatHoldUntil = SystemClock.elapsedRealtime() + HOLD_MS
        }
        _state.value = _state.value.copy(repeat = repeat)
    }

    private fun holdPlaying(playing: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(holdLock) {
            heldPlaying = playing
            playingHoldUntil = now + HOLD_MS
        }
        val cur = _state.value
        val updatedPos = if (!playing && cur.playing && !cur.buffering) {
            (cur.positionMs + ((now - cur.atElapsedMs).coerceAtLeast(0L) * cur.speed).toLong()).coerceAtLeast(0L)
        } else {
            cur.positionMs
        }
        _state.value = cur.copy(playing = playing, positionMs = updatedPos, atElapsedMs = now)
    }

    private fun clearHolds() {
        synchronized(holdLock) {
            volumeHoldUntil = 0L
            likedHoldUntil = 0L
            shuffleHoldUntil = 0L
            repeatHoldUntil = 0L
            playingHoldUntil = 0L
        }
    }

    private val pageCache = PageLruCache(MAX_CACHED_PAGES, BROWSE_FRESH_MS)
    private val pagesFile get() = File(app.filesDir, "remote_pages.json")

    private fun loadPages() {
        if (pagesLoaded || pageCache.size() > 0) return
        pagesLoaded = true
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching {
                if (!pagesFile.exists()) return@runCatching emptyMap<String, Pair<String, RemotePage>>()
                val saved = Json.parseToJsonElement(pagesFile.readText()).jsonObject
                buildMap {
                    saved.entries.take(MAX_CACHED_PAGES).forEach { (parent, el) ->
                        val raw = (el as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        val parsed = parsePage(raw) ?: return@forEach
                        if (parsed.second.error == null) put(parent, raw to parsed.second)
                    }
                }
            }.getOrDefault(emptyMap())
            if (loaded.isEmpty()) return@launch
            dataMutex.withLock {
                val merged = _pages.value.toMutableMap()
                var changed = false
                loaded.forEach { (parent, entry) ->
                    if (!pageCache.contains(parent)) {
                        pageCache.put(parent, entry.first, nowMs = null)
                    }
                    if (!merged.containsKey(parent)) {
                        merged[parent] = entry.second
                        changed = true
                    }
                }
                if (changed) _pages.value = merged
            }
        }
    }

    private fun savePages() {
        scope.launch(Dispatchers.IO) {
            savePagesMutex.withLock {
                runCatching {
                    val snapshot = pageCache.snapshot()
                    val json = JsonObject(snapshot.mapValues { JsonPrimitive(it.value) }).toString()
                    val target = pagesFile
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, "${target.name}.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(target)) {
                        target.writeText(json)
                        tmp.delete()
                    }
                }
            }
        }
    }

    /** Loads the last Data Layer state, art, viz & queue so the screen populates before the phone answers. */
    private suspend fun lastState(gen: Long) {
        for (path in listOf(STATE, ART, VIZ, QUEUE)) {
            if (!started || sessionGen != gen) return
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()
            runCatching {
                val items = Wearable.getDataClient(app).getDataItems(uri).await()
                try {
                    items.firstOrNull()?.let { item ->
                        val map = DataMapItem.fromDataItem(item).dataMap
                        withContext(Dispatchers.Default) {
                            if (!started || sessionGen != gen) return@withContext
                            dataMutex.withLock {
                                if (!started || sessionGen != gen) return@withLock
                                when (path) {
                                    STATE -> onState(map, gen)
                                    ART -> onArt(map, gen)
                                    VIZ -> onViz(map, gen)
                                    QUEUE -> onQueue(map, gen)
                                }
                            }
                        }
                    }
                } finally {
                    items.release()
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun decodeArtBitmap(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun onQueue(map: DataMap, gen: Long) {
        val j = runCatching { Json.parseToJsonElement(map.getString("json").orEmpty()).jsonObject }.getOrNull() ?: return
        val items = j["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let {
                RemoteItem(it.text("id"), it.text("title"), it.text("subtitle"), browsable = false, playable = true)
            }
        }
        val active = j["active"]?.let { (it as? JsonPrimitive)?.longOrNull } ?: -1L
        if (!started || sessionGen != gen) return
        _queue.value = RemoteQueue(items, active)
    }

    private fun onArt(map: DataMap, gen: Long) {
        val key = map.getString("key").orEmpty()
        val artBytes = map.getByteArray("art")
        if (key.isEmpty() || artBytes == null || artBytes.isEmpty()) {
            if (!started || sessionGen != gen) return
            pendingArtKey = ""
            pendingArtBytes = null
            if (!lastStateHasSeen || _state.value.artKey.isEmpty()) {
                decodedArtKey = ""
                _cover.value = null
            }
            return
        }
        if (!started || sessionGen != gen) return
        pendingArtKey = key
        pendingArtBytes = artBytes
        val stateMatches = !lastStateHasSeen || (_state.value.hasSession && _state.value.artKey == key)
        if (stateMatches && (key != decodedArtKey || _cover.value == null)) {
            decodeArtBitmap(artBytes)?.let { bmp ->
                if (!started || sessionGen != gen) return
                decodedArtKey = key
                _cover.value = bmp
            }
        }
    }

    private fun onViz(map: DataMap, gen: Long) {
        val key = map.getString("key").orEmpty()
        val seed = map.getInt("seed")
        val stepMs = map.getInt("stepMs", 100).coerceAtLeast(20)
        val samples = map.getByteArray("viz") ?: ByteArray(0)
        val prev = _viz.value
        val stableSamples = if (samples.contentEquals(prev.samples)) prev.samples else samples
        val snapshot = VizSnapshot(key = key, seed = seed, stepMs = stepMs, samples = stableSamples)
        if (!started || sessionGen != gen) return
        pendingViz = snapshot
        val stateMatches = !lastStateHasSeen || (_state.value.hasSession && (expectedVizKey.isEmpty() || expectedVizKey == key))
        if (stateMatches && snapshot != prev) {
            _viz.value = snapshot
        }
    }

    private fun onState(map: DataMap, gen: Long) {
        val j = runCatching { Json.parseToJsonElement(map.getString("json").orEmpty()).jsonObject }.getOrNull() ?: return
        val epoch = j.long("atEpochMs")
        val now = SystemClock.elapsedRealtime()
        val at = if (epoch > 0L) now - (System.currentTimeMillis() - epoch) else now
        val parsedSpeed = (j.float("speed") ?: 1f).let { if (it > 0f) it else 1f }
        val hasSession = j.bool("hasSession")
        val artKey = j.text("artKey")
        val inlineArtBytes = map.getByteArray("art")

        var decodedBmp: Bitmap? = null
        var clearCover = false
        if (!hasSession || artKey.isEmpty()) {
            clearCover = true
        } else if (inlineArtBytes != null && inlineArtBytes.isNotEmpty()) {
            if (artKey != decodedArtKey || _cover.value == null) {
                decodedBmp = decodeArtBitmap(inlineArtBytes)
            }
        } else if (artKey != decodedArtKey || _cover.value == null) {
            val pendingBytes = pendingArtBytes
            if (pendingArtKey == artKey && pendingBytes != null && pendingBytes.isNotEmpty()) {
                decodedBmp = decodeArtBitmap(pendingBytes)
            } else if (decodedArtKey != artKey) {
                clearCover = true
            }
        }

        if (!started || sessionGen != gen) return
        lastStateHasSeen = true
        if (!hasSession || artKey.isEmpty()) {
            decodedArtKey = ""
            _cover.value = null
        } else if (decodedBmp != null) {
            decodedArtKey = artKey
            _cover.value = decodedBmp
        } else if (clearCover) {
            _cover.value = null
        }

        val vizKey = j.text("vizKey")
        expectedVizKey = if (hasSession) vizKey else ""
        if (!hasSession) {
            if (_viz.value.samples.isNotEmpty() || _viz.value.key.isNotEmpty()) {
                _viz.value = VizSnapshot()
            }
        } else if (map.containsKey("viz")) {
            // Backwards-compatible inline viz bytes from older phone APKs.
            val vizStepMs = (j.nullableInt("vizStepMs") ?: 100).coerceAtLeast(20)
            val vizBytes = map.getByteArray("viz") ?: ByteArray(0)
            val prevViz = _viz.value
            val stableSamples = if (vizBytes.contentEquals(prevViz.samples)) prevViz.samples else vizBytes
            val nextViz = VizSnapshot(
                key = vizKey.ifEmpty { j.text("title") },
                seed = j.int("vizSeed"),
                stepMs = vizStepMs,
                samples = stableSamples,
            )
            if (nextViz != prevViz) _viz.value = nextViz
        } else if (vizKey.isNotEmpty()) {
            val cachedViz = pendingViz
            if (cachedViz != null && cachedViz.key == vizKey && cachedViz != _viz.value) {
                _viz.value = cachedViz
            }
        }

        val prev = _state.value
        val incoming = RemoteState(
            hasSession = hasSession,
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
        )

        if (!started || sessionGen != gen) return
        val trackChanged = incoming.title != prev.title && prev.title.isNotEmpty()
        val resolved = synchronized(holdLock) {
            if (trackChanged) likedHoldUntil = 0L
            incoming.copy(
                volume = if (now < volumeHoldUntil) heldVolume else incoming.volume,
                liked = if (!trackChanged && now < likedHoldUntil) heldLiked else incoming.liked,
                shuffle = if (now < shuffleHoldUntil) heldShuffle else incoming.shuffle,
                repeat = if (now < repeatHoldUntil) heldRepeat else incoming.repeat,
                playing = if (now < playingHoldUntil) heldPlaying else incoming.playing,
            )
        }
        if (!started || sessionGen != gen) return
        _state.value = resolved
        _connected.value = true
    }

    private fun onDevices(text: String, gen: Long) {
        val j = Json.parseToJsonElement(text).jsonObject
        val items = j["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            (el as? JsonObject)?.let {
                RemoteDevice(it.text("id"), it.text("name"), it.text("type"), it.bool("self"), it.bool("active"))
            }
        }
        val error = j.text("error").takeIf { it.isNotEmpty() }
        if (!started || sessionGen != gen) return
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

    private fun onPage(text: String, gen: Long, save: Boolean = true) {
        val (parent, page) = parsePage(text) ?: return
        if (!started || sessionGen != gen) return
        // Keep previously cached items visible if a live refresh fails.
        if (page.error != null && _pages.value[parent]?.items?.isNotEmpty() == true) return
        _pages.value = _pages.value + (parent to page)
        if (save && page.error == null) {
            pageCache.put(parent, text, SystemClock.elapsedRealtime())
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
