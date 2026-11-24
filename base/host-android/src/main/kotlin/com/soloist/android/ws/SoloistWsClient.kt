package com.soloist.android.ws

import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single upcoming track entry returned by Soloist's `get_queue` or `queue_changed`.
 */
data class SoloistQueueItem(
    val uri: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
)

/**
 * Observable playback, queue, and connection snapshot from the Soloist WebSocket API.
 */
data class SoloistPlaybackState(
    val connected: Boolean = false,
    val loggedIn: Boolean = false,
    val active: Boolean = false,
    val paused: Boolean = false,
    val trackUri: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 0,
    val shuffle: Boolean = false,
    val repeatContext: Boolean = false,
    val repeatTrack: Boolean = false,
    val queue: List<SoloistQueueItem> = emptyList(),
    val lastError: String? = null,
    val lastEventRaw: String? = null,
    val deviceId: String = "",
)

/**
 * OkHttp WebSocket client for Soloist's local loopback control API (`ws://127.0.0.1:<port>/`).
 */
class SoloistWsClient(
    private val client: OkHttpClient = defaultOkHttpClient(),
    @field:Volatile
    var onRawEvent: ((String) -> Unit)? = null,
) {

    private val _state = MutableStateFlow(SoloistPlaybackState())
    val state: StateFlow<SoloistPlaybackState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connLock = Any()
    private val connectionGen = AtomicLong(0L)
    private val manuallyDisconnected = AtomicBoolean(true)

    @Volatile
    private var activePort: Int = -1

    @Volatile
    private var webSocket: WebSocket? = null

    private var retryJob: Job? = null

    /**
     * Connects to the Soloist daemon WebSocket server at `ws://127.0.0.1:[port]/`.
     */
    fun connect(port: Int) {
        require(port in 1..65535) { "Invalid WebSocket port: $port" }
        synchronized(connLock) {
            manuallyDisconnected.set(false)
            activePort = port
            retryJob?.cancel()
            retryJob = null
            openSocketLocked(port, attempt = 0)
        }
    }

    /**
     * Closes the active WebSocket connection and cancels any reconnect jobs.
     */
    fun disconnect() {
        synchronized(connLock) {
            manuallyDisconnected.set(true)
            connectionGen.incrementAndGet()
            activePort = -1
            retryJob?.cancel()
            retryJob = null
            val ws = webSocket
            webSocket = null
            runCatching { ws?.close(WS_NORMAL_CLOSURE, "Client disconnecting") }
            _state.update { it.copy(connected = false) }
        }
    }

    fun play(uri: String) {
        sendCommand("play") { put("uri", uri) }
    }

    fun pause() {
        sendCommand("pause")
    }

    fun resume() {
        sendCommand("resume")
    }

    fun skipNext() {
        sendCommand("skip_next")
    }

    fun skipPrev() {
        sendCommand("skip_prev")
    }

    fun seek(positionMs: Long) {
        sendCommand("seek") { put("position_ms", positionMs.coerceAtLeast(0L)) }
    }

    fun setVolume(volume: Int) {
        sendCommand("set_volume") { put("volume", volume.coerceIn(0, 100)) }
    }

    fun setShuffle(enabled: Boolean) {
        sendCommand("set_shuffle") { put("enabled", enabled) }
    }

    fun setRepeatContext(enabled: Boolean) {
        sendCommand("set_repeat_context") { put("enabled", enabled) }
    }

    fun setRepeatTrack(enabled: Boolean) {
        sendCommand("set_repeat_track") { put("enabled", enabled) }
    }

    fun activate() {
        sendCommand("activate")
    }

    fun deactivate() {
        sendCommand("deactivate")
    }

    fun addToQueue(uri: String) {
        sendCommand("add_to_queue") { put("uri", uri) }
    }

    fun getQueue(limit: Int = 20) {
        sendCommand("get_queue") { put("limit", limit.coerceAtLeast(0)) }
    }

    fun getState() {
        sendCommand("get_state")
    }

    private fun sendCommand(command: String, configure: JSONObject.() -> Unit = {}) {
        val payload = JSONObject().apply {
            put("type", "command")
            put("command", command)
            configure()
        }.toString()

        synchronized(connLock) {
            val ws = webSocket
            if (ws == null || !ws.send(payload)) {
                Log.w(TAG, "Unable to send WebSocket command '$command' (not connected)")
                _state.update { it.copy(lastError = "WebSocket not connected for command: $command") }
            }
        }
    }

    private fun openSocketLocked(port: Int, attempt: Int) {
        val gen = connectionGen.incrementAndGet()
        val prev = webSocket
        webSocket = null
        runCatching { prev?.close(WS_NORMAL_CLOSURE, "Reconnecting") }

        val request = Request.Builder()
            .url("ws://127.0.0.1:$port/")
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(connLock) {
                        if (gen != connectionGen.get() || manuallyDisconnected.get()) {
                            runCatching { webSocket.close(WS_NORMAL_CLOSURE, "Superseded") }
                            return
                        }
                        Log.i(TAG, "WebSocket connected to 127.0.0.1:$port")
                        _state.update { it.copy(connected = true, lastError = null) }
                        getState()
                        getQueue()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val accepted = synchronized(connLock) {
                        if (gen != connectionGen.get() || manuallyDisconnected.get()) {
                            false
                        } else {
                            _events.tryEmit(text)
                            parseAndApplyMessage(text)
                            true
                        }
                    }
                    if (accepted) {
                        runCatching { onRawEvent?.invoke(text) }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(connLock) {
                        if (gen != connectionGen.get()) {
                            return
                        }
                        runCatching { webSocket.close(WS_NORMAL_CLOSURE, null) }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleSocketEnd(gen, port, attempt, err = null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val msg = t.message ?: t.javaClass.simpleName
                    handleSocketEnd(gen, port, attempt, err = msg)
                }
            },
        )
    }

    private fun handleSocketEnd(gen: Long, port: Int, attempt: Int, err: String?) {
        synchronized(connLock) {
            if (gen != connectionGen.get()) {
                return
            }
            webSocket = null
            _state.update {
                it.copy(
                    connected = false,
                    lastError = err ?: it.lastError,
                )
            }
            if (manuallyDisconnected.get() || activePort != port) {
                return
            }
            scheduleReconnectLocked(port, attempt + 1)
        }
    }

    private fun scheduleReconnectLocked(port: Int, nextAttempt: Int) {
        retryJob?.cancel()
        val backoffMs = (RECONNECT_BASE_MS * (1L shl (nextAttempt - 1).coerceAtMost(4)))
            .coerceAtMost(RECONNECT_MAX_MS)
        retryJob = scope.launch {
            delay(backoffMs)
            if (!isActive || manuallyDisconnected.get()) return@launch
            synchronized(connLock) {
                if (isActive && !manuallyDisconnected.get() && activePort == port && webSocket == null) {
                    openSocketLocked(port, nextAttempt)
                }
            }
        }
    }

    private fun parseAndApplyMessage(text: String) {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring non-JSON WebSocket frame: ${e.message}")
            _state.update {
                it.copy(
                    lastEventRaw = text,
                    lastError = "Malformed JSON event: ${e.message}",
                )
            }
            return
        }

        try {
            val typeStr = optStringClean(root, "type").orEmpty()
            val eventStr = optStringClean(root, "event").orEmpty()
            val msgKind = eventStr.ifEmpty { typeStr }

            val dataObj = root.optJSONObject("data")
            val stateObj = root.optJSONObject("state") ?: dataObj?.optJSONObject("state")
            val trackObj = root.optJSONObject("track")
                ?: dataObj?.optJSONObject("track")
                ?: stateObj?.optJSONObject("track")
            val playbackObj = root.optJSONObject("playback")
                ?: dataObj?.optJSONObject("playback")

            val allSources = listOfNotNull(trackObj, playbackObj, stateObj, dataObj, root)
            val stateSources = listOfNotNull(playbackObj, stateObj, dataObj, root)

            val trackExplicitlyCleared = (root.has("track") && root.isNull("track")) ||
                (dataObj?.has("track") == true && dataObj.isNull("track")) ||
                (stateObj?.has("track") == true && stateObj.isNull("track"))

            val loggedIn = optBooleanAny(stateSources, "logged_in")
            val active = optBooleanAny(stateSources, "active", "is_active")
            val paused = optBooleanAny(stateSources, "paused", "is_paused")
                ?: optBooleanAny(stateSources, "playing", "is_playing")?.let { !it }

            val trackUri = when {
                trackExplicitlyCleared -> ""
                trackObj != null -> optStringAny(listOf(trackObj, dataObj, stateObj, root), "track_uri", "uri")
                else -> optStringAny(stateSources, "track_uri")
            }
            val title = when {
                trackExplicitlyCleared -> ""
                trackObj != null -> optStringAny(listOf(trackObj, dataObj, stateObj, root), "title", "name")
                else -> optStringAny(stateSources, "title")
            }
            val artist = when {
                trackExplicitlyCleared -> ""
                else -> parseArtistAny(allSources)
            }
            val album = when {
                trackExplicitlyCleared -> ""
                else -> parseAlbumAny(allSources)
            }
            val positionMs = optLongAny(allSources, "position_ms", "position")
            val durationMs = when {
                trackExplicitlyCleared -> 0L
                else -> optLongAny(allSources, "duration_ms", "duration")
            }
            val volume = optIntAny(stateSources, "volume")?.coerceIn(0, 100)
            val shuffle = optBooleanAny(stateSources, "shuffle")
            val (repeatContext, repeatTrack) = parseRepeatAny(stateSources)
            val deviceId = optStringAny(stateSources, "device_id")

            val queueArray = findArrayAny(
                listOfNotNull(dataObj, stateObj, root),
                "queue",
                "next_tracks",
                "tracks",
                "items",
            )
            val parsedQueue = queueArray?.let { parseQueueArray(it) }

            val errorUpdate = parseCommandOrEventError(msgKind, root, dataObj)

            _state.update { cur ->
                val nextError = when {
                    errorUpdate.clear -> null
                    errorUpdate.message != null -> errorUpdate.message
                    else -> cur.lastError
                }
                cur.copy(
                    loggedIn = loggedIn ?: cur.loggedIn,
                    active = active ?: cur.active,
                    paused = paused ?: cur.paused,
                    trackUri = trackUri ?: cur.trackUri,
                    title = title ?: cur.title,
                    artist = artist ?: cur.artist,
                    album = album ?: cur.album,
                    positionMs = positionMs ?: cur.positionMs,
                    durationMs = durationMs ?: cur.durationMs,
                    volume = volume ?: cur.volume,
                    shuffle = shuffle ?: cur.shuffle,
                    repeatContext = repeatContext ?: cur.repeatContext,
                    repeatTrack = repeatTrack ?: cur.repeatTrack,
                    queue = parsedQueue ?: cur.queue,
                    lastError = nextError,
                    lastEventRaw = text,
                    deviceId = deviceId ?: cur.deviceId,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing Soloist WS payload: ${e.message}")
            _state.update {
                it.copy(
                    lastEventRaw = text,
                    lastError = e.message ?: "Failed to parse WS event",
                )
            }
        }
    }

    private fun parseCommandOrEventError(
        msgKind: String,
        root: JSONObject,
        dataObj: JSONObject?,
    ): ErrorUpdate {
        val sources = listOfNotNull(root, dataObj)
        val okVal = optBooleanAny(sources, "ok", "success")
        val errText = optStringAny(sources, "error")
        if (okVal == false || !errText.isNullOrBlank()) {
            val msg = errText?.takeIf { it.isNotBlank() }
                ?: optStringAny(sources, "message")?.takeIf { it.isNotBlank() }
                ?: "Command failed (${optStringClean(root, "command").orEmpty().ifEmpty { msgKind }})"
            return ErrorUpdate(clear = false, message = msg)
        }
        if (msgKind == "command_result" && okVal == true) {
            return ErrorUpdate(clear = true, message = null)
        }
        return ErrorUpdate(clear = false, message = null)
    }

    private fun parseRepeatAny(sources: List<JSONObject>): Pair<Boolean?, Boolean?> {
        var ctx = optBooleanAny(sources, "repeat_context")
        var trk = optBooleanAny(sources, "repeat_track")
        if (ctx != null || trk != null) {
            return ctx to trk
        }
        val mode = optStringAny(sources, "repeat", "repeat_mode")?.lowercase() ?: return null to null
        when (mode) {
            "context", "all" -> {
                ctx = true
                trk = false
            }
            "track", "one" -> {
                ctx = false
                trk = true
            }
            "off", "none" -> {
                ctx = false
                trk = false
            }
        }
        return ctx to trk
    }

    private fun parseArtistAny(sources: List<JSONObject>): String? {
        for (src in sources) {
            val single = optStringClean(src, "artist")
            if (single != null) {
                return single
            }
            if (src.has("artists") && !src.isNull("artists")) {
                val raw = src.opt("artists")
                if (raw is JSONArray) {
                    val names = ArrayList<String>(raw.length())
                    for (i in 0 until raw.length()) {
                        val item = raw.opt(i)
                        when (item) {
                            is String -> if (item.isNotBlank()) names += item
                            is JSONObject -> {
                                val name = optStringAny(listOf(item), "name", "title")
                                if (!name.isNullOrBlank()) names += name
                            }
                        }
                    }
                    return names.joinToString(", ")
                }
                if (raw is String) {
                    return raw
                }
            }
        }
        return null
    }

    private fun parseAlbumAny(sources: List<JSONObject>): String? {
        for (src in sources) {
            if (!src.has("album") || src.isNull("album")) continue
            when (val raw = src.opt("album")) {
                is String -> return raw
                is JSONObject -> return optStringAny(listOf(raw), "name", "title") ?: ""
            }
        }
        return null
    }

    private fun parseQueueArray(arr: JSONArray): List<SoloistQueueItem> {
        val items = ArrayList<SoloistQueueItem>(arr.length())
        for (i in 0 until arr.length()) {
            when (val elem = arr.opt(i)) {
                is JSONObject -> {
                    val trackNode = elem.optJSONObject("track")
                    val sources = listOfNotNull(trackNode, elem)
                    items += SoloistQueueItem(
                        uri = optStringAny(sources, "track_uri", "uri").orEmpty(),
                        title = optStringAny(sources, "title", "name").orEmpty(),
                        artist = parseArtistAny(sources).orEmpty(),
                        album = parseAlbumAny(sources).orEmpty(),
                        durationMs = optLongAny(sources, "duration_ms", "duration") ?: 0L,
                    )
                }
                is String -> {
                    if (elem.isNotBlank()) {
                        items += SoloistQueueItem(uri = elem, title = elem)
                    }
                }
            }
        }
        return items
    }

    private fun findArrayAny(sources: List<JSONObject>, vararg keys: String): JSONArray? {
        for (src in sources) {
            for (key in keys) {
                if (src.has(key) && !src.isNull(key)) {
                    src.optJSONArray(key)?.let { return it }
                }
            }
        }
        return null
    }

    private fun optStringClean(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.opt(key) ?: return null
        return if (v is String) v else v.toString()
    }

    private fun optStringAny(sources: List<JSONObject?>, vararg keys: String): String? {
        for (src in sources) {
            if (src == null) continue
            for (key in keys) {
                optStringClean(src, key)?.let { return it }
            }
        }
        return null
    }

    private fun optBooleanAny(sources: List<JSONObject?>, vararg keys: String): Boolean? {
        for (src in sources) {
            if (src == null) continue
            for (key in keys) {
                if (src.has(key) && !src.isNull(key)) {
                    val raw = src.opt(key)
                    if (raw is Boolean) return raw
                    if (raw is Number) return raw.toInt() != 0
                }
            }
        }
        return null
    }

    private fun optIntAny(sources: List<JSONObject?>, vararg keys: String): Int? {
        for (src in sources) {
            if (src == null) continue
            for (key in keys) {
                if (src.has(key) && !src.isNull(key)) {
                    val raw = src.opt(key)
                    if (raw is Number) return raw.toInt()
                    if (raw is String) raw.toIntOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    private fun optLongAny(sources: List<JSONObject?>, vararg keys: String): Long? {
        for (src in sources) {
            if (src == null) continue
            for (key in keys) {
                if (src.has(key) && !src.isNull(key)) {
                    val raw = src.opt(key)
                    if (raw is Number) return raw.toLong()
                    if (raw is String) raw.toLongOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    private data class ErrorUpdate(
        val clear: Boolean,
        val message: String?,
    )

    companion object {
        private const val TAG = "SoloistWsClient"
        private const val WS_NORMAL_CLOSURE = 1000
        private const val RECONNECT_BASE_MS = 500L
        private const val RECONNECT_MAX_MS = 5000L

        private fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
