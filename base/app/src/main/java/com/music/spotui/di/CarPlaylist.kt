package com.music.spotui.di

import android.content.Context

/**
 * The playlist the car's "Add to playlist" button adds the playing song to. A car cannot open a
 * picker from a player button, so the choice is made in the browse tree (Library > Add to playlist),
 * and only there: adding on the phone never sets or replaces it. It stays until changed.
 */
object CarPlaylist {
    private const val PREFS = "car_prefs"
    private fun prefs() = Graph.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val id: String? get() = prefs().getString("playlist_id", null)?.takeIf { it.isNotBlank() }
    val name: String? get() = prefs().getString("playlist_name", null)?.takeIf { it.isNotBlank() }

    fun set(id: String, name: String) {
        prefs().edit().putString("playlist_id", id).putString("playlist_name", name).apply()
    }

    /** Sign-out: the next account has other playlists. */
    fun clear() { prefs().edit().clear().apply() }
}
