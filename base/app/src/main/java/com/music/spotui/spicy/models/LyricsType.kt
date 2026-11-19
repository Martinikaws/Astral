// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/lyrics/spicy/models/LyricsType.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.models

/**
 * The synchronization granularity of a parsed lyrics document (`Syllable` / `Line` / `Static`).
 *
 * - [Syllable]: word/syllable-timed karaoke (full per-word gradient wipe + springs).
 * - [Line]: line-timed; the whole line fills as one gradient sweep.
 * - [Static]: unsynced text; rendered as a plain, non-interactive list.
 */
enum class LyricsType {
    Syllable,
    Line,
    Static,
}
