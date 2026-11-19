package com.music.spotui.wear

import android.content.Context
import app.myspoti.host.android.Logbook
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Standalone watch sync endpoints for Wear OS.
 *
 * Astral runs Wear OS in **Phone Remote only** mode (`Watch plays music without the phone: NO`),
 * so standalone watch session, library, tracks, extensions, and listen-sync endpoints are disabled
 * and return `"disabled: phone remote only"`.
 */
object WatchAnswers {
    const val SESSION = "/orpheon/session"
    const val LIBRARY = "/orpheon/library"
    const val TRACKS = "/orpheon/tracks"
    const val EXTENSIONS = "/orpheon/extensions"
    const val LISTEN = "/orpheon/listen"
    private const val DISABLED_RESPONSE = "disabled: phone remote only"

    @Suppress("UNUSED_PARAMETER")
    suspend fun answer(context: Context, path: String, body: String): String = when (path) {
        SESSION, LIBRARY, TRACKS, EXTENSIONS, LISTEN -> DISABLED_RESPONSE
        else -> error("unknown question")
    }
}

/**
 * Wearable Data Layer service for Astral: routes `/orpheon/remote/...` to [WatchRemote] (Phone
 * Remote) and returns `"disabled: phone remote only"` for standalone watch sync requests.
 */
class WearLinkService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        WatchRemote.init(applicationContext)
    }

    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray>? {
        if (path !in setOf(WatchAnswers.SESSION, WatchAnswers.LIBRARY, WatchAnswers.TRACKS, WatchAnswers.EXTENSIONS, WatchAnswers.LISTEN)) return null
        val out = TaskCompletionSource<ByteArray>()
        scope.launch {
            val answer = runCatching { withTimeout(ANSWER_MS) { WatchAnswers.answer(applicationContext, path, data.decodeToString()) } }
                .getOrElse { Logbook.log("watch: $path not answered (${it.message})"); "error: ${it.message ?: it.javaClass.simpleName}" }
            out.setResult(answer.encodeToByteArray())
        }
        return out.task
    }

    /** The watch's "Phone" mode: remote commands for what this phone plays ([WatchRemote]). */
    override fun onMessageReceived(event: MessageEvent) {
        if (!event.path.startsWith(WatchRemote.PREFIX)) return
        when (event.path.removePrefix(WatchRemote.COMMAND)) {
            "watch_open" -> WatchRemote.setWatchActive(true)
            "watch_close" -> WatchRemote.setWatchActive(false)
            else -> WatchRemote.onMessage(applicationContext, event.sourceNodeId, event.path, event.data)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != WatchAnswers.EXTENSIONS) return
        scope.launch {
            val client = Wearable.getChannelClient(applicationContext)
            runCatching {
                val text = runCatching { WatchAnswers.answer(applicationContext, channel.path, "") }.getOrElse { "error: ${it.message}" }
                client.getOutputStream(channel).await().use { it.write(text.encodeToByteArray()) }
            }.onFailure { Logbook.log("watch: extensions channel error (${it.message})") }
            runCatching { client.close(channel).await() }
        }
    }

    private companion object {
        const val ANSWER_MS = 25_000L
    }
}

/**
 * Standalone watch Wi-Fi sync ([PassLink]) is disabled in Astral Phone Remote only mode.
 * Kept as a no-op stub so existing callers compile cleanly.
 */
object WatchLan {
    class Link(val secret: String, val port: Int, val id: String)

    @Suppress("UNUSED_PARAMETER")
    fun startIfLinked(context: Context) {
        // Disabled: Astral Wear OS is Phone Remote only.
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized fun ensure(context: Context): Link? = null
}
