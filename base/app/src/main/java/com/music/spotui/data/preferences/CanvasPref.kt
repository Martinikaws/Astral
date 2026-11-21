package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The now-playing Canvas as the background of the whole player. [background]: the clip fills the
 * player behind everything (default on). [blur]: how blurred it is, 0..100 percent (default 0, the
 * sharp clip); only drawn on Android 12 and later. Both are Compose state, read by the player.
 */
object CanvasPref {
    private const val PREF = "settings_prefs"
    private const val KEY_BACKGROUND = "canvas_background"
    private const val KEY_BLUR = "canvas_blur"
    private const val KEY_BEHIND_LYRICS = "canvas_behind_lyrics"

    /** The Canvas as the full-screen background of the lyrics (replacing the Spicy mesh when the song has one; default on). */
    var behindLyrics by mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY_BEHIND_LYRICS, true))
        private set

    fun setBehindLyrics(c: Context, value: Boolean) {
        behindLyrics = value
        prefs(c).edit().putBoolean(KEY_BEHIND_LYRICS, value).apply()
    }

    var background by mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY_BACKGROUND, true))
        private set

    var blur by mutableIntStateOf(prefs(com.music.spotui.di.Graph.app).getInt(KEY_BLUR, 0).coerceIn(0, 100))
        private set

    fun setBackground(c: Context, value: Boolean) {
        background = value
        prefs(c).edit().putBoolean(KEY_BACKGROUND, value).apply()
    }

    fun setBlur(c: Context, value: Int) {
        val v = value.coerceIn(0, 100)
        blur = v
        prefs(c).edit().putInt(KEY_BLUR, v).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
