// Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, ui/MainViewModel.kt (mixOn, toggleMix) and playback/Mixes.kt (offHere) at 860b326. Modified for Astral, 2026-11.
// Changes: the choice is kept in AutoMix's per-playlist override (shared with the player's ring) instead of Square's own prefs; the Mix follows AutoMix's setting for the playlist (global default unless overridden); the mixed state comes from api.mix.
package com.music.spotui.automix

import androidx.compose.runtime.mutableStateMapOf
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import app.myspoti.host.android.MyspotiRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A playlist mixed in Spotify's app: whether it is, and whether its Mix is on here (AutoMix's setting for it: the global one unless overridden). */
object SpotifyMixChoice {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mixed = mutableStateMapOf<String, Boolean>()
    private val asked = HashSet<String>()

    /** Asks Spotify once per session whether [uri] is mixed; reading [isMixed] in Compose follows the answer. */
    fun load(uri: String) {
        if (!uri.startsWith("spotify:playlist:") || !synchronized(asked) { asked.add(uri) }) return
        scope.launch {
            val answer = runCatching { MyspotiRuntime.api.mix.isMixed(uri) }.getOrDefault(false)
            if (answer) markMixed(uri) else synchronized(asked) { asked.remove(uri) }
        }
    }

    internal fun markMixed(uri: String) { mixed[uri] = true }

    fun isMixed(uri: String): Boolean = mixed[uri] == true

    /** Mixed in Spotify's app and AutoMix on for it (its override, else the global setting). */
    fun on(uri: String): Boolean = isMixed(uri) && AutoMix.enabledFor(uri)

    fun toggle(uri: String) {
        val next = !on(uri)
        // An override only where it differs from the global AutoMix setting.
        if (next == AutoMix.on) AutoMix.clearPlaylistMix(uri) else AutoMix.setPlaylistMix(uri, next)
    }

    /** The Connect device playing is Soloist: its device id when known, the name "Astral" only as a fallback while the id is unknown. */
    fun soloistOutput(): Boolean {
        val device = SongPlayer.activeDevice.value ?: return false
        val own = runCatching { com.soloist.android.service.SoloistRuntime.get(Graph.app).playbackState.value.deviceId }.getOrDefault("")
        return if (own.isNotBlank()) device.id == own else device.name == "Astral"
    }

    /** Played as its own Connect context, so Soloist mixes it itself. */
    fun asContext(uri: String): Boolean = on(uri) && soloistOutput()
}
