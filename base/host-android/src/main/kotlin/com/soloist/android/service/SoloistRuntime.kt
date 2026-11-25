package com.soloist.android.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.myspoti.host.android.PlaybackService
import com.soloist.android.runtime.CommandResult
import com.soloist.android.runtime.DaemonState
import com.soloist.android.runtime.SoloistProcessManager
import com.soloist.android.runtime.SoloistAudioEffects
import com.soloist.android.ws.SoloistWsClient
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SoloistRuntime {
    @Volatile private var instance: SoloistController? = null

    fun get(context: Context): SoloistController = instance ?: synchronized(this) {
        instance ?: SoloistController(context.applicationContext).also { instance = it }
    }

    private val boot = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when this build ships the Soloist binary (not on non-ARM64 builds). */
    fun isAvailable(context: Context) =
        java.io.File(context.applicationInfo.nativeLibraryDir, "libsoloist.so").isFile

    /** Every service start, also a cold one with no Activity: bring a set-up Soloist up, off the main thread. */
    internal fun serviceStarted(context: Context) {
        instance?.launchPending()
        if (!isAvailable(context)) return
        boot.launch {
            val controller = get(context)
            controller.resumeSelected()
            controller.launchPending()
        }
    }
    internal fun serviceDestroyed() = instance?.stopDaemon()
    internal val active get() = instance?.active == true
}

class SoloistController internal constructor(private val context: Context) {
    private val preferences = context.getSharedPreferences("astral_soloist", Context.MODE_PRIVATE)
    private val manager = SoloistProcessManager(context)
    val client = SoloistWsClient()
    val playbackState = client.state
    val daemonState = manager.daemonState
    val audioStats = manager.audioStats
    fun setAudioEffects(effects: SoloistAudioEffects) {
        manager.audioBridge.effects = effects
    }
    private val key = MutableStateFlow(false)
    private val credentials = MutableStateFlow(false)
    val hasApiKey = key.asStateFlow()
    val hasCredentials = credentials.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = Mutex()
    private class StartRequest(val pairMode: Boolean)
    private val pending = AtomicReference<StartRequest?>(null)
    private var multicast: WifiManager.MulticastLock? = null
    private var pairing = false
    private var runningSince = 0L
    private var restarted = false
    @Volatile private var stopRequested = false
    internal val active get() = pending.get() != null ||
        daemonState.value is DaemonState.Starting || daemonState.value is DaemonState.Running

    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                client.pause()
            }
        }.build()

    init {
        scope.launch {
            var held = false
            combine(playbackState, manager.audioStats) { playback, audioStats ->
                playback.connected && ((playback.active && !playback.paused) || audioStats.playing)
            }.distinctUntilChanged().collect { playing ->
                if (playing) {
                    held = audio?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    if (!held) client.pause()
                } else if (held) {
                    audio?.abandonAudioFocusRequest(focus)
                    held = false
                }
            }
        }
        scope.launch { refresh() }
        scope.launch {
            daemonState.collect { state ->
                if (state is DaemonState.Running) runningSince = SystemClock.elapsedRealtime()
                if (state !is DaemonState.Error && state !is DaemonState.Stopped) return@collect
                changes.withLock {
                    if (manager.daemonState.value != state) return@withLock
                    val finishedPairing = state is DaemonState.Stopped && pairing
                    val ranMs = if (runningSince == 0L || pairing || stopRequested) -1L
                        else SystemClock.elapsedRealtime() - runningSince
                    runningSince = 0L
                    pairing = false
                    refresh()
                    if (finishedPairing && pending.get() == null && hasCredentials.value &&
                        preferences.getBoolean(AUTO_START, true)) {
                        startLocked(pairMode = false)
                    } else if (ranMs >= 0 && pending.get() == null && preferences.getBoolean(AUTO_START, true) &&
                        shouldRestart(ranMs, restarted)) {
                        restarted = true
                        startLocked(pairMode = false)
                    } else if (multicast != null || state is DaemonState.Error) {
                        client.disconnect()
                        releaseMulticast()
                        PlaybackService.soloistIdle()
                    }
                }
            }
        }
        scope.launch {
            daemonState.collectLatest { state ->
                if (state is DaemonState.Running && state.pairMode) {
                    while (!manager.hasStoredCredentials()) delay(1000)
                    refresh()
                }
            }
        }
        scope.launch {
            playbackState.map { it.loggedIn }.distinctUntilChanged().collectLatest { if (it) refresh() }
        }
    }

    private fun refresh() {
        key.value = manager.hasSavedApiKey()
        credentials.value = manager.hasStoredCredentials()
    }

    private fun releaseMulticast() {
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
    }

    suspend fun setupApiKey(apiKey: String): CommandResult = changes.withLock {
        pending.set(null)
        stopRequested = true
        pairing = false
        manager.stopDaemon()
        client.disconnect()
        releaseMulticast()
        PlaybackService.soloistIdle()
        manager.setupApiKey(apiKey).also { refresh() }
    }

    /** Resume the saved choice when Astral comes to the foreground. Never enter pairing here. */
    suspend fun resumeSelected() = withContext(Dispatchers.IO) {
        // A paired install without this setting already selected Soloist in an earlier Astral.
        if (active || !preferences.getBoolean(AUTO_START, true)) return@withContext
        restarted = false
        if (manager.hasSavedApiKey() && manager.hasStoredCredentials()) requestStart(pairMode = false)
    }

    /** The user opened Soloist setup. Reuse a saved key and only pair if needed. */
    suspend fun startSavedReceiver() {
        if (active) return
        withContext(Dispatchers.IO) {
            if (!manager.hasSavedApiKey()) return@withContext
            // Pairing is an explicit setup action; a plain start respects a Stop pressed in setup.
            if (!manager.hasStoredCredentials()) startDaemon(pairMode = true)
            else if (preferences.getBoolean(AUTO_START, true)) requestStart(pairMode = false)
        }
    }

    fun startDaemon(pairMode: Boolean = false) {
        restarted = false
        preferences.edit().putBoolean(AUTO_START, true).apply()
        requestStart(pairMode)
    }

    private fun requestStart(pairMode: Boolean) {
        val request = StartRequest(pairMode)
        if (active || !pending.compareAndSet(null, request)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } catch (e: RuntimeException) {
            if (pending.compareAndSet(request, null)) manager.reportStartFailure(e)
        }
    }

    internal fun launchPending() {
        if (pending.get() == null) return
        scope.launch {
            changes.withLock {
                val request = pending.get() ?: return@withLock
                try {
                    val state = daemonState.value
                    if (state !is DaemonState.Starting && state !is DaemonState.Running) {
                        startLocked(request.pairMode)
                    }
                } finally {
                    pending.compareAndSet(request, null)
                }
            }
        }
    }

    private suspend fun startLocked(pairMode: Boolean) {
        pairing = pairMode
        stopRequested = false
        try {
            val wifi = context.getSystemService(WifiManager::class.java)
            if (multicast == null) {
                multicast = wifi?.createMulticastLock("Astral:Soloist")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
            client.disconnect()
            manager.startDaemon(DEVICE_NAME, 50, pairMode, client::connect)
        } catch (e: Exception) {
            manager.stopDaemon()
            releaseMulticast()
            manager.reportStartFailure(e)
        }
        refresh()
    }

    fun stopFromSetup() {
        preferences.edit().putBoolean(AUTO_START, false).apply()
        stopDaemon()
    }

    internal fun stopDaemon() {
        pending.set(null)
        scope.launch {
            changes.withLock {
                pairing = false
                stopRequested = true
                manager.stopDaemon()
                client.disconnect()
                releaseMulticast()
                PlaybackService.soloistIdle()
                refresh()
            }
        }
    }

    companion object {
        /** The Connect device name: Soloist is started with it and ZeroConf announces it. */
        const val DEVICE_NAME = "Astral Soloist"
        private const val AUTO_START = "auto_start"
    }
}

/** A daemon that stopped on its own is restarted once; one that dies again within 30 s of a restart is left idle. */
internal fun shouldRestart(ranMs: Long, afterRestart: Boolean) = !(afterRestart && ranMs < 30_000)
