package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Playlist / Album / Artist page style, unified with [LookPref.look]:
 * - [ORPHEON_BASIC]: lightweight, fast-scrolling layout (Astral Basic).
 * - [ORPHEON_GLASS]: translucent frosted cards with dynamic cover glow (Interactive / Glassify / LiquidMusic).
 * - [SPOTIFUSION]: Spotify-faithful 1:1 header, track rows, and action bar (Spotifusion 1:1).
 */
enum class PageStyle {
    ORPHEON_BASIC,
    ORPHEON_GLASS,
    SPOTIFUSION;

    companion object {
        fun of(name: String?): PageStyle = entries.firstOrNull { it.name == name } ?: ORPHEON_GLASS

        fun forLook(look: Look): PageStyle = when (look) {
            Look.CLASSIC -> ORPHEON_BASIC
            Look.SPOTIFUSION -> SPOTIFUSION
            Look.IMMERSIVE, Look.GLASSIFY, Look.LIQUID_MUSIC -> ORPHEON_GLASS
        }
    }
}

object PageStylePref {
    private const val PREF = "settings_prefs"
    private const val KEY = "page_style"
    private const val KEY_OVERRIDE = "page_style_override"
    private const val KEY_CHOSEN = "page_style_chosen"

    private var overrideStyle by mutableStateOf(
        runCatching { prefs(com.music.spotui.di.Graph.app) }.getOrNull()
            ?.takeIf { it.getBoolean(KEY_OVERRIDE, false) }
            ?.getString(KEY, null)
            ?.let { name -> PageStyle.entries.firstOrNull { it.name == name } },
    )

    /** Follows [LookPref.look] automatically unless explicitly overridden via [set]. */
    val style: PageStyle
        get() = overrideStyle ?: PageStyle.forLook(LookPref.look)

    fun set(c: Context, value: PageStyle) {
        overrideStyle = value
        prefs(c).edit().putString(KEY, value.name).putBoolean(KEY_OVERRIDE, true).apply()
    }

    fun clearOverride(c: Context) {
        overrideStyle = null
        prefs(c).edit().remove(KEY).putBoolean(KEY_OVERRIDE, false).apply()
    }

    fun isChosen(c: Context): Boolean = prefs(c).getBoolean(KEY_CHOSEN, false) || LookPref.isChosen(c)

    fun setChosen(c: Context) {
        prefs(c).edit().putBoolean(KEY_CHOSEN, true).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
