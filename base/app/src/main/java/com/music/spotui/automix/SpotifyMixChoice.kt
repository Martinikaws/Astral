// Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, ui/MainViewModel.kt (mixOn, toggleMix) and playback/Mixes.kt (offHere) at 860b326. Modified for Astral, 2026-11.
// Changes: the choice is kept in AutoMix's per-playlist override (shared with the player's ring) instead of Square's own prefs; the mixed state comes from api.mix.
package com.music.spotui.automix

import androidx.compose.runtime.mutableStateMapOf
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import app.myspoti.host.android.MyspotiRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A playlist mixed in Spotify's app: whether it is, and whether its Mix is on here (on unless switched off). */
object SpotifyMixChoice {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mixed = mutableStateMapOf<String, Boolean>()
    private val asked = HashSet<String>()

    /** Asks Spotify once per session whether [uri] is mixed; reading [isMixed] in Compose follows the answer. */
    fun load(uri: String) {
        if (!uri.startsWith("spotify:playlist:") || !synchronized(asked) { asked.add(uri) }) return
        scope.launch {
            val answer = runCatching { MyspotiRuntime.api.mix.isMixed(uri) }.getOrDefault(false)
            if (answer) mixed[uri] = true else synchronized(asked) { asked.remove(uri) }
        }
    }

    fun isMixed(uri: String): Boolean = mixed[uri] == true

    /** Mixed in Spotify's app and not switched off here. */
    fun on(uri: String): Boolean = isMixed(uri) && (AutoMix.playlistChoice(uri) ?: true)

    fun toggle(uri: String) = AutoMix.setPlaylistMix(uri, !on(uri))

    /** The Connect device playing is Soloist (the app's own, named "Astral"). */
    fun soloistOutput(): Boolean {
        val device = SongPlayer.activeDevice.value ?: return false
        val own = runCatching { com.soloist.android.service.SoloistRuntime.get(Graph.app).playbackState.value.deviceId }.getOrDefault("")
        return device.name == "Astral" || (own.isNotBlank() && device.id == own)
    }

    /** Played as its own Connect context, so Soloist mixes it itself. */
    fun asContext(uri: String): Boolean = on(uri) && soloistOutput()
}
