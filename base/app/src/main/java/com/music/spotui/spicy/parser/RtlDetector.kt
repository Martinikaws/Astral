// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/lyrics/spicy/parser/RtlDetector.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.parser

/**
 * Detects right-to-left text (Arabic, Hebrew, Persian, …). Used to right-align lines and to
 * suppress per-letter splitting (RTL scripts are never letter-emphasised).
 */
object RtlDetector {
    /** Unicode first-strong direction, defaulting neutral-only text to LTR. */
    fun isRtl(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }
}
