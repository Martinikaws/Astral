/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/backdrop/backdrops/BackdropFreeze.kt at 139441f.
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
/*
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

private const val BackdropFreezeSafetyNs = 900_000_000L

/**
 * Scroll-driven freeze for a screen-local [layerBackdrop]. Mirrors MainActivity's
 * app-level backdrop freeze (the same pattern measured there: 47ms of layer.record
 * per scroll frame with the freeze off, 1.9ms with it on).
 *
 * The clock is a PLAIN array, deliberately not snapshot state: [frozen] runs in the
 * draw phase, so a snapshot read there registers a draw dependency and every write
 * re-invalidates the frame (the exact trap documented at MainActivity.kt:1253).
 *
 * Attach [connection] to an ancestor of the scrolling list (`.nestedScroll(...)`)
 * and pass [frozen] to [layerBackdrop]'s `frozen` param.
 */
class BackdropFreeze {
    private val scrollClockNs = longArrayOf(0L)

    val frozen: () -> Boolean = {
        val started = scrollClockNs[0]
        // Elapsed check is only a safety net for gestures that never deliver
        // onPostFling (e.g. cancelled); onPostFling is the normal reset path.
        started != 0L && System.nanoTime() - started < BackdropFreezeSafetyNs
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            scrollClockNs[0] = System.nanoTime()
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // End of the whole drag+fling sequence. The content settles, then the
            // screen's chrome redraws and records the fresh capture — no explicit
            // invalidation needed.
            scrollClockNs[0] = 0L
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberBackdropFreeze(): BackdropFreeze = remember { BackdropFreeze() }
