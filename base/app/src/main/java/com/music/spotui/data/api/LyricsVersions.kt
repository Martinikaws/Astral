// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/network/data/providers/GeniusSongPages.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.data.api

/** Whether a catalogue's title names another recording of the song (a remix, a live take, a sped-up cut). */
internal object LyricsVersions {
    /** [theirs] names a version (in brackets or after a dash) that [ours] doesn't. */
    fun otherVersion(theirs: String, ours: String): Boolean {
        val extras = BRACKETED.findAll(theirs).map { it.value } + listOfNotNull(theirs.substringAfter(" - ", "").takeIf(String::isNotEmpty))
        return extras.filterNot(SAME_RECORDING::containsMatchIn)
            .any { extra -> VERSION.findAll(extra).any { it.value.lowercase() !in ours.lowercase() } }
    }

    /** Version notes that keep the recording: "(Deluxe Version)", "(Taylor's Version)", "(2015 Remaster)". */
    private val SAME_RECORDING = Regex("""deluxe|expanded|edition|taylor|explicit|clean|remaster|anniversary|original|album|single""", RegexOption.IGNORE_CASE)

    private val VERSION = Regex("""\b(?:ver(?:sion)?\b\.?|remix|live|acoustic|instrumental|inst\.|sped up|slowed|tv size|demo)""", RegexOption.IGNORE_CASE)

    private val BRACKETED = Regex("[(\\[{].*?[)\\]}]")
}
