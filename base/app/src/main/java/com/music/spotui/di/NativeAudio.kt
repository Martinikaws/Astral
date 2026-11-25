// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package com.music.spotui.di

import android.content.Context
import android.content.Intent
import app.myspoti.engine.DrmPlayer
import app.myspoti.engine.EngineEvent
import app.myspoti.engine.Http
import app.myspoti.engine.HttpRequest
import app.myspoti.engine.HttpResponse
import app.myspoti.engine.NativeEngine
import app.myspoti.engine.PlayerEvent
import app.myspoti.engine.android.AndroidEnvironment
import app.myspoti.engine.android.ChromiumHttp
import app.myspoti.engine.android.Media3DrmPlayer
import app.myspoti.engine.android.OkHttpWebSockets
import app.myspoti.engine.android.WidevineInfo
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.host.android.NativeTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The native engine (:engine-android) as the account's audio ([AccountAudio]): with its setting
 * on, the app's playback commands go to it and it plays the account's songs itself (Media3 and the
 * phone's own Widevine), the Web Player page staying as the account's remote: the state, the queue
 * and the tokens. Off (the default while it is being tried), the page plays as before. The engine
 * registers a device of its own on the account, shown as this phone.
 *
 * An ad the account's player is given is never asked for nor played by the engine: it moves on
 * to the next song. While an extension plays, the engine is the listen mirror's device
 * ([MirrorEngine], [AccountSync]): it plays the extension's song unheard, without the audio focus,
 * at its smallest file, and the page is not used for it. With the engine off or left aside, the
 * mirror is the page's, and a running engine leaves the account's devices meanwhile ([leaveForMirror]).
 *
 * Debug builds drive it by hand too (TestControlReceiver): native-play <uri> [--ez l3 true],
 * native-pause, native-resume, native-seek <ms>, native-next [<uri>], native-prev, native-transfer,
 * native-stop, native-status, native-log [<lines>], native-audio on|off. Every step goes to the
 * Logbook as "native: …": HTTP calls with state PUT debug_source and melody types, licence sizes
 * and latency, CDN ranges, Widevine level, time to first audio.
 */
object NativeAudio : app.myspoti.spotify.AccountAudio, app.myspoti.spotify.sync.MirrorEngine {
    private const val PREFS = "native_audio"

    /** The setting: the engine plays the account's songs (on unless turned off). */
    @Volatile var enabled: Boolean = Graph.app.getSharedPreferences(PREFS, 0).getBoolean("on", true)
        private set

    /** Until when the engine is left aside after it could not play: the page plays meanwhile. */
    @Volatile private var asideUntil = 0L

    override val on: Boolean get() = enabled && System.currentTimeMillis() >= asideUntil

    /** The engine could not play ([why]): the page is the player for [ASIDE_MS], then the engine is tried again. */
    private fun setAside(why: String) {
        asideUntil = System.currentTimeMillis() + ASIDE_MS
        log("left aside for ${ASIDE_MS / 60_000} min, the page plays: $why")
        scope.launch { runCatching { stop() } }
    }

    private const val ASIDE_MS = 15 * 60_000L

    /**
     * The page's listen-sync mirror starts (an extension plays, the page plays the song unheard): the engine
     * stops and leaves the account's devices, so the page's play is the page's own. The next play of
     * the account's songs starts it again.
     */
    fun leaveForMirror() {
        if (engine == null) return
        log("the listen mirror plays on the page: the engine stops until the account's songs play again")
        scope.launch { runCatching { stop() } }
    }

    fun set(context: Context, value: Boolean) {
        enabled = value
        asideUntil = 0L
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("on", value).apply()
        log("the engine plays the account's songs: ${if (value) "on" else "off"}")
        if (!value) scope.launch { runCatching { stop() } }
    }

    override val deviceId: String? get() = engine?.deviceId
    override val holdsPlayback: Boolean get() = engine?.now?.value != null

    /**
     * The listen mirror holds the engine ([EngineSyncPage], from its first play to its end), or the
     * engine is muted by it: its songs are the mirror's, not the account's to show or follow.
     */
    @Volatile private var mirrorHolds = false
    override fun mirrorHeld(on: Boolean) { mirrorHolds = on }
    override val mirrors: Boolean get() = engine?.let { mirrorHolds || it.muted } == true

    /**
     * The engine's own playback, live from its player: [app.myspoti.spotify.SpotifyPlayer] shows it
     * over the page's state of it, which comes late and from the account's servers (a resume shown
     * six seconds back, a new song eight seconds in).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val now: kotlinx.coroutines.flow.StateFlow<NativeEngine.Now?> by lazy {
        _current.flatMapLatest { it?.now ?: kotlinx.coroutines.flow.flowOf(null) }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    }

    /** Hands the engine to the account's player and its device list; once, when the runtime is up. */
    fun attach() {
        val spotify = MyspotiRuntime.spotify
        spotify.player.audio = this
        spotify.devices.ownDeviceId = { deviceId }
        val prefs = Graph.app.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)
        if (on &&
            !com.music.spotui.extensions.Extensions.hasAudioSource() &&
            !com.music.spotui.data.DataSaver.active &&
            SongPlayer.accountPlays &&
            prefs.getBoolean("LoggedIn", false)
        ) {
            scope.launch {
                if (!spotify.http.hasToken()) {
                    spotify.ready.first { it }
                    if (!runCatching { MyspotiRuntime.isSignedIn() }.getOrDefault(false)) return@launch
                } else if (MyspotiRuntime.host.loginRequired.value) {
                    return@launch
                }
                if (on &&
                    !com.music.spotui.extensions.Extensions.hasAudioSource() &&
                    !com.music.spotui.data.DataSaver.active &&
                    SongPlayer.accountPlays &&
                    com.music.spotui.data.offline.OfflineStore.isOnline(Graph.app)
                ) {
                    runCatching { ready() }
                }
            }
        }
    }

    /** The engine, started and registered first if it was not (the account's player, or the listen mirror's device). */
    override suspend fun ready(): NativeEngine = started(Graph.app, forceL3 = false)

    private val _current = kotlinx.coroutines.flow.MutableStateFlow<NativeEngine?>(null)
    /** The engine registered on the account now, or null. */
    override val current: kotlinx.coroutines.flow.StateFlow<NativeEngine?> = _current

    override suspend fun play(contextUri: String, trackUri: String?, index: Int?, positionMs: Long?, order: List<String>?, shuffle: Boolean?) {
        val e = runCatching { ready() }.getOrElse { setAside("it did not start (${it.message})"); throw it }
        foreground()
        commandAtMs = System.currentTimeMillis()
        if (tapAtMs > 0) log("tap -> engine command: ${commandAtMs - tapAtMs} ms")
        val since = e.clockMs()
        // The song's file, urls and licence are fetched while the command is on its way: the load then finds them ready.
        (trackUri ?: order?.firstOrNull() ?: contextUri).let { warm(it, forPlay = true) }
        // A song played alone is its own context.
        if (trackUri != null && trackUri != contextUri) e.play(contextUri, trackUri, index, order = order?.takeIf { it.isNotEmpty() }, shuffle = shuffle)
        else e.play(contextUri, shuffle = shuffle)
        if (tapAtMs > 0) log("tap -> command POST done: ${System.currentTimeMillis() - tapAtMs} ms")
        // The seek once the song is loaded and reported here: sent before, it put the song played before back.
        if (positionMs != null && positionMs > 0 && e.awaitLoaded(trackUri ?: contextUri.takeIf { it.startsWith("spotify:track:") }, since, LOAD_WAIT_MS)) e.seekTo(positionMs)
    }
    override suspend fun pause() { engine?.pause() }
    override suspend fun resume() { val e = ready(); foreground(); e.resume() }

    /** The playback service in the foreground before the engine's player asks for the audio (Android refuses it otherwise). */
    private suspend fun foreground() {
        if (app.myspoti.host.android.PlaybackService.isForeground) return
        app.myspoti.host.android.PlaybackService.ensureStarted(Graph.app)
        kotlinx.coroutines.withTimeoutOrNull(FOREGROUND_MS) {
            while (!app.myspoti.host.android.PlaybackService.isForeground) kotlinx.coroutines.delay(5)
        }
    }
    private const val FOREGROUND_MS = 2_000L
    private const val LOAD_WAIT_MS = 6_000L
    override suspend fun seek(positionMs: Long) { engine?.seekTo(positionMs) }
    override suspend fun next() { commandAtMs = System.currentTimeMillis(); ready().skipNext() }
    override suspend fun previous() { ready().previous() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var engine: NativeEngine? = null
    private var player: Media3DrmPlayer? = null
    private var http: ChromiumHttp? = null
    @Volatile private var commandAtMs = 0L
    @Volatile private var tapAtMs = 0L

    /** A tap on a song: the start of the tap -> first audio line in the log (kept until it is read or 5 s old). */
    fun tapped() {
        val now = System.currentTimeMillis()
        if (tapAtMs == 0L || now - tapAtMs > 5_000) tapAtMs = now
    }
    @Volatile private var lastEvent = ""
    private val ring = ArrayDeque<String>()

    private fun log(line: String) {
        Logbook.log("native: $line")
        synchronized(ring) {
            ring.addLast("${System.currentTimeMillis() % 100_000_000} $line")
            while (ring.size > 400) ring.removeFirst()
        }
    }

    /** Runs [intent]'s native-* command; returns the short reply the broadcast carries back. */
    fun command(context: Context, cmd: String, intent: Intent): String {
        val arg = intent.getStringExtra("arg")?.trim().orEmpty()
        val app = context.applicationContext
        if (cmd == "native-status") return status()
        if (cmd == "native-audio") { if (arg == "on" || arg == "off") set(app, arg == "on"); return "native audio ${if (enabled) "on" else "off"}${if (enabled && !on) " (left aside, the page plays)" else ""}" }
        if (cmd == "native-log") return synchronized(ring) { ring.toList().takeLast(arg.toIntOrNull()?.coerceIn(1, 400) ?: 60).joinToString("\n") }
        if (!MyspotiRuntime.isStarted) return "error: the app's runtime is not started (open the app once)"
        log("command $cmd ${arg.take(80)}")
        scope.launch {
            runCatching {
                when (cmd) {
                    "native-play" -> {
                        require(arg.startsWith("spotify:")) { "native-play needs a spotify: uri" }
                        val e = started(app, intent.getBooleanExtra("l3", false))
                        commandAtMs = System.currentTimeMillis()
                        e.play(arg)
                    }
                    "native-pause" -> engine?.pause()
                    "native-resume" -> engine?.resume()
                    "native-seek" -> engine?.seekTo(arg.toLongOrNull() ?: error("native-seek needs a position in ms"))
                    "native-next" -> engine?.let { e ->
                        commandAtMs = System.currentTimeMillis()
                        if (arg.startsWith("spotify:")) e.play(arg) else e.skipNext()
                    }
                    "native-prev" -> engine?.previous()
                    "native-transfer" -> started(app, intent.getBooleanExtra("l3", false)).transfer("restore")
                    "native-stop" -> stop()
                    else -> log("unknown command $cmd")
                }
            }.onFailure { log("$cmd failed: ${it.javaClass.simpleName}: ${it.message}") }
        }
        return "sent $cmd"
    }

    private fun status(): String {
        val e = engine
        return "engine=${if (e == null) "stopped" else "device ${e.deviceId.take(8)}… on ${e.spclient.substringAfter("://")}"} " +
            "widevine=${WidevineInfo.summary} http=${http?.status ?: "-"} last=$lastEvent"
    }

    private suspend fun started(context: Context, forceL3: Boolean): NativeEngine = lock.withLock {
        engine?.let { return it }
        val spotify = MyspotiRuntime.spotify
        val chromium = ChromiumHttp(context).also { http = it }
        val logged = LoggedHttp(chromium)
        val p = Media3DrmPlayer(context, chromium, log = ::log, forceL3 = forceL3).also { player = it }
        var eventsJob: kotlinx.coroutines.Job? = null
        var created: NativeEngine? = null
        try {
            val config = NativeTokens.config(spotify.http)
            val e = NativeEngine(logged, NativeTokens(spotify.http), OkHttpWebSockets(MyspotiRuntime.apiHttp), Observed(p), AndroidEnvironment, config, scope)
            created = e
            // A pause the player made itself (focus lost, headphones out) becomes a pause command, so
            // the account's state follows and the engine reports it when the push comes back.
            p.onPlayerPause = { why -> log("player paused itself: $why"); scope.launch { runCatching { e.pause() } } }
            eventsJob = scope.launch {
                e.events.collect { ev ->
                    lastEvent = ev.toString().take(120)
                    if (ev !is EngineEvent.Dealer) log("event $ev")
                    if (ev is EngineEvent.Dealer) {
                        // Routine frames ("type uri payloads=…") are throttled; failures are not.
                        if (" payloads=" in ev.line) Logbook.logEvery("native-dealer:" + ev.line.substringBefore(' '), 10_000L, "native: dealer ${ev.line}")
                        else log("dealer ${ev.line}")
                    }
                    if (ev is EngineEvent.CommandAcked && tapAtMs > 0) log("tap -> state push: ${System.currentTimeMillis() - tapAtMs} ms (POST ${ev.postMs} ms, push ${ev.pushMs} ms after its start)")
                    if (ev is EngineEvent.Failure || ev is EngineEvent.Unauthorized) log("attention: $ev")
                    // The player itself failed (the licence refused, the file unreadable): the page takes over.
                    if (ev is EngineEvent.Failure && ev.message.startsWith("player:")) setAside(ev.message)
                    // The dealer closed: no command reaches the engine any more; the next use starts it again.
                    if (ev is EngineEvent.Disconnected) dropped(e)
                }
            }
            log("starting: widevine ${WidevineInfo.summary}, ${config.deviceName}, app ${config.appVersion}")
            e.start()
            log("registered ${e.deviceId.take(8)}… on ${e.spclient.substringAfter("://")}, http ${chromium.status}")
            engine = e
            startedAt = System.currentTimeMillis()
            _current.value = e
            scope.launch { e.prewarmDrm() }
            e
        } catch (t: Throwable) {
            eventsJob?.cancel()
            created?.let { runCatching { it.close() } }
            p.release()
            player = null
            http = null
            throw t
        }
    }

    private fun dropped(e: NativeEngine) {
        scope.launch {
            lock.withLock {
                if (engine !== e) return@withLock
                log("the dealer closed: the engine leaves the account and is started again in the background")
                runCatching { e.close() }
                player?.release()
                engine = null; player = null; _current.value = null
                cancelWarm()
                // A short life is a flapping connection: the backoff goes on instead of starting over.
                if (System.currentTimeMillis() - startedAt > HEALTHY_MS) restartAttempt = 0
                scheduleRestart()
            }
        }
    }

    @Volatile private var startedAt = 0L
    private var restartJob: kotlinx.coroutines.Job? = null
    private var restartAttempt = 0

    /** Starts the engine again after a drop, [restartDelayMs] apart, for as long as the account wants it (see [wantsEngine]). */
    private fun scheduleRestart() {
        restartJob?.cancel()
        restartJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(restartDelayMs(restartAttempt++))
                if (engine != null) return@launch
                if (!wantsEngine()) return@launch log("no restart: the engine is not wanted now")
                if (!com.music.spotui.data.offline.OfflineStore.isOnline(Graph.app)) continue
                runCatching { started(Graph.app, forceL3 = false) }
                    .onSuccess { return@launch }
                    .onFailure { log("restart failed (attempt $restartAttempt): ${it.javaClass.simpleName}: ${it.message}") }
            }
        }
    }

    /** Logged in, the engine setting on and not left aside, nothing else holding playback: another device picked, the mirror, an extension. */
    private fun wantsEngine(): Boolean =
        on && !mirrorHolds && MyspotiRuntime.isStarted && !MyspotiRuntime.host.loginRequired.value && SongPlayer.accountPlays && SongPlayer.chosenRemoteId == null &&
            !com.music.spotui.extensions.Extensions.hasAudioSource() && !com.music.spotui.data.DataSaver.active &&
            Graph.app.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE).getBoolean("LoggedIn", false)

    // ── Warming: the tapped song resolved and licensed before its play ──
    private class Warm(val job: kotlinx.coroutines.Job, val atMs: Long)
    private val warming = LinkedHashMap<String, Warm>()

    /**
     * A press or a screen opening on [uri]: its file, CDN urls and licence are fetched now, so the play starts from memory.
     * Nothing for episodes, local files, offline, a stopped engine or another device playing; at most [WARM_MAX] songs kept
     * (the oldest is freed in the player), and one is warmed again once [REWARM_MS] old (its CDN entry may have expired).
     */
    fun warm(uri: String, forPlay: Boolean = false) {
        val e = engine ?: return
        if (!on || mirrors || !uri.startsWith("spotify:track:") || !com.music.spotui.data.offline.OfflineStore.isOnline(Graph.app)) return
        if (com.music.spotui.data.DataSaver.active || com.music.spotui.extensions.Extensions.hasAudioSource() || SongPlayer.chosenRemoteId != null) return
        // A play makes this device the active one, so another device holding playback does not matter then.
        if (!forPlay && remoteActiveId(holdsPlayback, e.cluster.value?.activeDeviceId, e.deviceId) != null) return
        val now = System.currentTimeMillis()
        synchronized(warming) {
            warming[uri]?.let { if (now - it.atMs < REWARM_MS) return }
            warming.remove(uri)?.job?.cancel()
            while (warming.size >= WARM_MAX) warming.keys.first().let { old -> warming.remove(old)?.job?.cancel(); scope.launch { runCatching { e.unwarm(old) } } }
            warming[uri] = Warm(scope.launch { runCatching { e.warm(uri) }.onFailure { if (it !is kotlinx.coroutines.CancellationException) log("warm failed: ${it.message}") } }, now)
        }
    }

    private fun cancelWarm() = synchronized(warming) { warming.values.forEach { it.job.cancel() }; warming.clear() }
    private const val REWARM_MS = 15 * 60_000L
    private const val WARM_MAX = 3
    private const val HEALTHY_MS = 60_000L

    private suspend fun stop() = lock.withLock {
        restartJob?.cancel(); restartAttempt = 0; cancelWarm()
        engine?.let { runCatching { it.close() }.onFailure { t -> log("close failed: ${t.message}") } }
        player?.release()
        engine = null
        player = null
        _current.value = null
        log("stopped")
    }

    /** The player as the engine sees it, plus the time from the command to the first audio. */
    private class Observed(private val inner: DrmPlayer) : DrmPlayer by inner {
        override fun setListener(listener: (PlayerEvent) -> Unit) = inner.setListener { ev ->
            if (ev is PlayerEvent.Playing && commandAtMs > 0) {
                val now = System.currentTimeMillis()
                log("time to first audio ${now - commandAtMs} ms from the command" + if (tapAtMs > 0) ", ${now - tapAtMs} ms from the tap" else "")
                commandAtMs = 0
                tapAtMs = 0
            }
            if (ev is PlayerEvent.Error || ev is PlayerEvent.Ended || ev is PlayerEvent.Loaded || ev is PlayerEvent.Seeked) log("player $ev")
            listener(ev)
        }
    }

    /** The engine's HTTP with one line per call: what was reported, never a token or a query string. */
    private class LoggedHttp(private val inner: Http) : Http {
        override suspend fun request(request: HttpRequest): HttpResponse {
            val t0 = System.currentTimeMillis()
            val what = describe(request)
            return try {
                inner.request(request).also { r ->
                    log("${request.method} $what -> ${r.status} ${r.body.size} B in ${System.currentTimeMillis() - t0} ms")
                    // What Spotify says of a request it refuses (an error text, never a token).
                    if (r.status in 400..499 && r.status != 401) log("refused ${r.status}: ${r.text().take(300)}")
                }
            } catch (t: Throwable) {
                log("${request.method} $what failed after ${System.currentTimeMillis() - t0} ms: ${t.message}")
                throw t
            }
        }

        private fun describe(r: HttpRequest): String {
            val path = Logbook.noQuery(r.url).substringAfter("://")
            val body = r.body ?: return path
            val text = body.decodeToString()
            return runCatching {
                when {
                    path.endsWith("/state") -> Json.parseToJsonElement(text).jsonObject.let { o ->
                        val sub = o["sub_state"] as? kotlinx.serialization.json.JsonObject
                        "state ${o["debug_source"]?.jsonPrimitive?.content} seq=${o["seq_num"]} pos=${sub?.get("position")} prev=${o["previous_position"]}"
                    }
                    "/melody/v1/msg/batch" in path -> "melody " + Json.parseToJsonElement(text).jsonObject["messages"]!!.jsonArray
                        .joinToString(",") { it.jsonObject["type"]!!.jsonPrimitive.content }
                    "/audio/license" in path -> "licence challenge ${body.size} B"
                    else -> path
                }
            }.getOrDefault(path)
        }
    }
}

/** Milliseconds between a dropped engine and its restart: 1, 2, 4, 8, 16, then 30 at most. */
internal fun restartDelayMs(attempt: Int): Long = minOf(1_000L shl attempt.coerceIn(0, 5), 30_000L)
