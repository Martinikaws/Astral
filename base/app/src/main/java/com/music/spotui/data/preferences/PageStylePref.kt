package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * First-run Playlist / Album / Artist page style:
 * - [ORPHEON_BASIC]: lightweight, fast-scrolling layout.
 * - [ORPHEON_GLASS]: translucent frosted cards with dynamic cover glow.
 * - [SPOTIFUSION]: Spotify-faithful 1:1 header, track rows, and action bar.
 */
enum class PageStyle {
    ORPHEON_BASIC,
    ORPHEON_GLASS,
    SPOTIFUSION;

    companion object {
        fun of(name: String?): PageStyle = entries.firstOrNull { it.name == name } ?: ORPHEON_GLASS
    }
}

object PageStylePref {
    private const val PREF = "settings_prefs"
    private const val KEY = "page_style"
    private const val KEY_CHOSEN = "page_style_chosen"

    var style by mutableStateOf(PageStyle.of(prefs(com.music.spotui.di.Graph.app).getString(KEY, null)))
        private set

    fun set(c: Context, value: PageStyle) {
        style = value
        prefs(c).edit().putString(KEY, value.name).apply()
    }

    fun isChosen(c: Context): Boolean = prefs(c).getBoolean(KEY_CHOSEN, false)

    fun setChosen(c: Context) {
        prefs(c).edit().putBoolean(KEY_CHOSEN, true).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
