/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/EdgeFade.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed (and its imports); otherwise verbatim.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.components

import com.music.spotui.ui.immersive.squared.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row that scrolls sideways fading out at an edge it has more beyond,
 * rather than ending in a cut through whatever is passing it: a chip or a
 * lit pill sliced at the edge reads as a mistake (#166). Each edge fades only
 * while there is more of the row past it, and only as far as there is.
 */
fun Modifier.horizontalEdgeFade(state: ScrollState, width: Dp = 24.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = width.toPx()
        if (fade <= 0f || size.width <= 0f) return@drawWithContent
        val start = (state.value / fade).coerceIn(0f, 1f)
        val end = ((state.maxValue - state.value) / fade).coerceIn(0f, 1f)
        if (start <= 0f && end <= 0f) return@drawWithContent
        val share = (fade / size.width).coerceAtMost(0.5f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 1f - start),
                share to Color.Black,
                1f - share to Color.Black,
                1f to Color.Black.copy(alpha = 1f - end),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * For a row that scrolls sideways from one edge of the screen to the other:
 * keeps the system's back gesture off it. With gesture navigation a drag that
 * starts at either edge is "back", and a row of chips or presets runs right to
 * both, so trying to scroll it from where it starts left the tab or closed the
 * page instead (#172). The row is short, well inside the room Android allows an
 * app to take from each edge, and the back gesture still works everywhere else.
 */
val SideScrollNoBack: Modifier = Modifier.systemGestureExclusion()
