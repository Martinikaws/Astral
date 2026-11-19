package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/**
 * The lyrics sources added from Spicy Lyrics Mobile, switched on or off one by one in the "Try
 * another source" sheet ([com.music.spotui.ui.components.LyricsSourceSheet]).
 *
 * On by default (their public APIs say any app may use them): Unison, LRCMux. Off until the user
 * turns them on, after a one-time notice of who is asked and what is sent: QQ Music, Kuwo, RMM
 * Revival. Read as state by the sheet and off the main thread by LyricsApi (the defaults before the
 * app is set up, in tests).
 */
object LyricsSourcePref {
    private const val PREF = "settings_prefs"
    private fun key(id: String) = "lyrics_source_$id"
    private fun seenKey(id: String) = "lyrics_source_${id}_notice_seen"

    /** Source ids ([com.music.spotui.data.api.LyricsApi.Source.id]) this covers. */
    val DEFAULT_ON = listOf("unison", "lrcmux")
    val OPT_IN = listOf("qq", "kuwo", "rmm")
    val ALL = DEFAULT_ON + OPT_IN

    private val state = mutableStateMapOf<String, Boolean>()

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Whether [id] is asked: the user's choice, else its default. */
    fun on(id: String): Boolean = state[id] ?: runCatching {
        prefs(com.music.spotui.di.Graph.app).getBoolean(key(id), id in DEFAULT_ON).also { state[id] = it }
    }.getOrDefault(id in DEFAULT_ON)

    fun set(c: Context, id: String, value: Boolean) {
        state[id] = value
        prefs(c).edit().putBoolean(key(id), value).apply()
        // Songs not found are asked again; what was found (maybe picked by hand) is kept.
        com.music.spotui.data.api.LyricsApi.forgetMisses()
    }

    /** Whether the one-time notice ("who is asked, what is sent") was already shown for [id]. */
    fun noticeSeen(c: Context, id: String): Boolean = prefs(c).getBoolean(seenKey(id), false)

    fun markNoticeSeen(c: Context, id: String) = prefs(c).edit().putBoolean(seenKey(id), true).apply()
}
