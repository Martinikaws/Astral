/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/ScrollActivity.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed to com.music.spotui.ui.immersive.squared.glass (and R to Astral's);
 * otherwise verbatim.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Whether a page is being scrolled right now.
 *
 * The glass surfaces ask this during draw and stop re-sampling the page while
 * it moves; see LocalGlassFrozen. It used to be a flag on the tab bar's own
 * scroll connection — a local addition to a vendored library — which meant the
 * library could not be updated without carrying it forward. This is the same
 * flag, kept where it belongs: a connection of this app's own, chained beside
 * the bar's rather than welded into it.
 *
 * Deliberately consumes nothing. Every callback reports what it saw and returns
 * zero, so the list scrolls exactly as it would with nothing attached.
 */
class ScrollActivity(
    /** Called once a scroll down has gone far enough to fold the bar. */
    private val onFold: () -> Unit = {},
    /** Called when a scroll up arrives with nowhere left to go: the top. */
    private val onTop: () -> Unit = {},
    /** How far down the page has to move before the bar folds. */
    private val foldThresholdPx: Float = 0f,
) : NestedScrollConnection {

    private var downwards = 0f

    /**
     * Read during draw, so it is a plain field rather than a state.
     *
     * Nothing recomposes when it changes — the surfaces that care call it back
     * on their own frame — which is the point: a scroll would otherwise
     * recompose the whole window twice, once to say it started and once to say
     * it stopped.
     */
    var scrolling: Boolean = false
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y != 0f) scrolling = true

        // Folding is a matter of travel, not of direction alone: a few pixels
        // either way while a finger settles must not close the bar.
        if (available.y < 0f) {
            downwards += -available.y
            if (foldThresholdPx > 0f && downwards >= foldThresholdPx) {
                downwards = 0f
                onFold()
            }
        } else if (available.y > 0f) {
            downwards = 0f
        }
        return Offset.Zero
    }

    /**
     * What is left of a scroll once the page has taken its share.
     *
     * A page that is already at its top consumes nothing of a downward drag, so
     * the whole of it arrives here — which is the one moment that means "the
     * top", and the only one the bar opens on. Anything less and the bar came
     * back on any small upward flick, halfway down a list, over the very rows
     * the reader was going back to.
     */
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y > 0f) onTop()
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        scrolling = true
        return Velocity.Zero
    }

    /**
     * A fling ends here, and so does a drag that never became one.
     *
     * The frames right after are the ones worth having sharp again: the page has
     * stopped and the glass is being looked at rather than moved.
     */
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        scrolling = false
        return Velocity.Zero
    }
}
