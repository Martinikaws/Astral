// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, app/src/main/java/com/tx24/spicyplayer/lyrics/spicy/canvas/LyricsFooterRows.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the lyrics screen and [LyricsView] share: which way the active line lies when it has been
 * scrolled out of view, and a way to go back to it. (Upstream's credits and profile requests are
 * not ported.)
 */
class LyricsViewState {
    /** Set while the user has scrolled the active line out of view; null otherwise. */
    var activeLineDirection by mutableStateOf<ActiveLineDirection?>(null)
        internal set

    internal var scrollToActiveRequests by mutableIntStateOf(0)
        private set

    fun scrollToActive() {
        scrollToActiveRequests++
    }
}

enum class ActiveLineDirection { Above, Below }
