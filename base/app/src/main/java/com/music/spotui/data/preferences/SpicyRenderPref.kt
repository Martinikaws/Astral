package com.music.spotui.data.preferences

import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SpicyRenderSettings(
    val bloom: Boolean = false,
    val lowEffects: Boolean = false,
    val stillBackground: Boolean = false,
    val musicHaptics: Boolean = false,
)

internal object SpicyRenderPref {
    private val prefs get() = Graph.app.getSharedPreferences("spicy_renderer_options", 0)
    private val values by lazy {
        MutableStateFlow(
            SpicyRenderSettings(
                bloom = prefs.getBoolean("bloom", false),
                lowEffects = prefs.getBoolean("low_effects", false),
                stillBackground = prefs.getBoolean("still", false),
                musicHaptics = prefs.getBoolean("music_haptics", false),
            ),
        )
    }
    val state get() = values.asStateFlow()

    fun update(change: (SpicyRenderSettings) -> SpicyRenderSettings) {
        val next = change(values.value)
        prefs.edit().putBoolean("bloom", next.bloom).putBoolean("low_effects", next.lowEffects)
            .putBoolean("still", next.stillBackground)
            .putBoolean("music_haptics", next.musicHaptics).apply()
        values.value = next
    }
}

/** Timing belongs to the recording. A positive delay shows each lyric later. */
internal object LyricsTimingPref {
    const val LIMIT = 5_000
    const val STEP = 100
    private val prefs get() = Graph.app.getSharedPreferences("spicy_lyrics_timing", 0)
    private val offsets by lazy {
        MutableStateFlow(prefs.all.mapNotNull { (key, value) ->
            (value as? Int)?.let { key to it.coerceIn(-LIMIT, LIMIT) }
        }.toMap())
    }
    val state get() = offsets.asStateFlow()

    fun set(trackKey: String, ms: Int) {
        val value = ms.coerceIn(-LIMIT, LIMIT)
        val edit = prefs.edit()
        offsets.value = if (value == 0) {
            edit.remove(trackKey)
            offsets.value - trackKey
        } else {
            edit.putInt(trackKey, value)
            offsets.value + (trackKey to value)
        }
        edit.apply()
    }

    fun calibrationOffset(heardPositionMs: Long, lineStartMs: Long): Int =
        (heardPositionMs - lineStartMs).coerceIn(-LIMIT.toLong(), LIMIT.toLong()).toInt()

    fun apply(lyrics: Lyrics, offsetMs: Int): Lyrics {
        if (!lyrics.synced || offsetMs == 0) return lyrics
        fun time(ms: Long) = (ms + offsetMs).coerceAtLeast(0)
        fun shift(word: LyricWord): LyricWord = word.copy(
            startMs = time(word.startMs),
            endMs = if (word.endMs == 0L) 0L else maxOf(1L, time(word.endMs)),
        )
        return lyrics.copy(lines = lyrics.lines.map { line ->
            line.copy(timeMs = time(line.timeMs), words = line.words?.map(::shift),
                backgroundWords = line.backgroundWords?.map(::shift))
        })
    }
}
