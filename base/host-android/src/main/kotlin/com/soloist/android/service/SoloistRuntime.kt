package com.soloist.android.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
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

object SoloistRuntime {
    @Volatile private var instance: SoloistController? = null

    fun get(context: Context): SoloistController = instance ?: synchronized(this) {
        instance ?: SoloistController(context.applicationContext).also { instance = it }
    }

    internal fun serviceStarted() = instance?.launchPending()
    internal fun serviceDestroyed() = instance?.stopDaemon()
    internal val active get() = instance?.active == true
}

class SoloistController internal constructor(private val context: Context) {
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
    private val pending = AtomicReference<Boolean?>(null)
    private var multicast: WifiManager.MulticastLock? = null
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
            daemonState.collectLatest {
                if (it is DaemonState.Error || it is DaemonState.Stopped) {
                    changes.withLock {
                        if (manager.daemonState.value == it && (multicast != null || it is DaemonState.Error)) {
                            client.disconnect()
                            releaseMulticast()
                            PlaybackService.soloistIdle()
                        }
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
        manager.stopDaemon()
        client.disconnect()
        releaseMulticast()
        PlaybackService.soloistIdle()
        manager.setupApiKey(apiKey).also { refresh() }
    }

    fun startDaemon(pairMode: Boolean = false) {
        pending.set(pairMode)
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } catch (e: RuntimeException) {
            pending.set(null)
            manager.reportStartFailure(e)
        }
    }

    internal fun launchPending() {
        if (pending.get() == null) return
        scope.launch {
            changes.withLock {
                val pair = pending.getAndSet(null) ?: return@withLock
                try {
                    val wifi = context.getSystemService(WifiManager::class.java)
                    if (multicast == null) {
                        multicast = wifi?.createMulticastLock("Astral:Soloist")?.apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                    }
                    client.disconnect()
                    manager.startDaemon("Astral", 50, pair, client::connect)
                } catch (e: Exception) {
                    manager.stopDaemon()
                    releaseMulticast()
                    manager.reportStartFailure(e)
                }
                refresh()
            }
        }
    }

    fun stopDaemon() {
        pending.set(null)
        scope.launch {
            changes.withLock {
                manager.stopDaemon()
                client.disconnect()
                releaseMulticast()
                PlaybackService.soloistIdle()
                refresh()
            }
        }
    }
}
