// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/lyrics/spicy/models/Letter.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.models

/**
 * Represents a single character (letter) with its specific timing within a [Word].
 * Used for granular syllable-level highlighting.
 */
data class Letter(
    val char: String,
    val startMs: Long,
    val endMs: Long,
)
