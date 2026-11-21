package com.music.spotui.ui.immersive.squared

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the words are drawn: one at a time as they are sung, or line by line. */
enum class SquaredLyricsStyle { ANIMATED, CLASSIC }

/**
 * The few settings the Squared look keeps for itself, in their own preferences file so that no
 * other look reads or writes them.
 */
class SquaredPrefs private constructor(private val prefs: SharedPreferences) {
    private val _lyricsStyle = MutableStateFlow(
        SquaredLyricsStyle.entries.firstOrNull { it.name == prefs.getString(KEY_LYRICS_STYLE, null) }
            ?: SquaredLyricsStyle.ANIMATED,
    )
    val lyricsStyle: StateFlow<SquaredLyricsStyle> = _lyricsStyle.asStateFlow()

    fun setLyricsStyle(style: SquaredLyricsStyle) {
        _lyricsStyle.value = style
        prefs.edit().putString(KEY_LYRICS_STYLE, style.name).apply()
    }

    private val _animatedCovers = MutableStateFlow(prefs.getBoolean(KEY_ANIMATED_COVERS, true))

    /** "Animated covers & artist artwork": the extra catalogue pictures and moving covers. On by default. */
    val animatedCovers: StateFlow<Boolean> = _animatedCovers.asStateFlow()

    fun setAnimatedCovers(on: Boolean) {
        _animatedCovers.value = on
        prefs.edit().putBoolean(KEY_ANIMATED_COVERS, on).apply()
    }

    companion object {
        private const val KEY_LYRICS_STYLE = "lyrics_style"
        private const val KEY_ANIMATED_COVERS = "animated_covers"

        @Volatile
        private var instance: SquaredPrefs? = null

        fun of(context: Context): SquaredPrefs = instance ?: synchronized(this) {
            instance ?: SquaredPrefs(
                context.applicationContext.getSharedPreferences("squared", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
