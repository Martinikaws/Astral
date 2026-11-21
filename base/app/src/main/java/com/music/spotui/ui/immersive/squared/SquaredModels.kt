/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/data/Catalog.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: only the lyrics and Canvas models the ported player reads are kept, without
 * kotlinx.serialization; SquaredAdapters.kt fills them from Astral's own lyrics and Canvas data.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared

/** One line of lyrics; [startTimeMs] is null for unsynced lyrics. */
data class LyricLine(
    val startTimeMs: Long?,
    val text: String,
    /** When each word of this line is sung, where the source says so. */
    val words: List<LyricWord> = emptyList(),
    /** The same line in the listener's language, where the source carries it. */
    val translation: String? = null,
    /** The line in Latin letters, where the source carries it. */
    val romanization: String? = null,
)

/** One word, and the moment it belongs to. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    /** The source it came from, by name; null when not recorded. */
    val source: String? = null,
    /** Who to credit under the words, where the source requires it. */
    val credit: LyricsCredit? = null,
)

/** The credit a source asks to be shown wherever its words are. */
data class LyricsCredit(
    val provider: String,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val maker: String? = null,
    val makerUrl: String? = null,
)

/** A track's Canvas; [isVideo] is false for the handful that are a still image. */
data class CanvasClip(val url: String, val isVideo: Boolean)

/** Who made a track, as the credits panel shows it. Astral has no credits source yet. */
data class SquaredCredits(val roles: List<Role>, val sources: List<String> = emptyList()) {
    val isEmpty: Boolean get() = roles.isEmpty()

    data class Role(val title: String, val people: List<Person>)

    data class Person(val name: String, val subroles: List<String> = emptyList())
}
