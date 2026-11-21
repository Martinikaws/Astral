/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/floatingtabbar/FoldGeometry.kt at 139441f.
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
package com.music.spotui.ui.immersive.squared.glass.floatingtabbar

import kotlin.math.roundToInt

internal data class FoldGeometry(
    val height: Int,
    val rowY: Int,
    val navWidth: Int,
    val searchWidth: Int,
    val searchX: Int,
    val selectedX: Int,
    val playerX: Int,
    val playerY: Int,
)

/** Pure coordinates: reversing a fold retraces exactly the same geometry. */
internal fun foldGeometry(
    width: Int, expandedRowHeight: Int, spacing: Int, playerHeight: Int, hasPlayer: Boolean,
    fold: Float, search: Float, tabsCount: Int, selectedIndex: Int, inset: Float, collapsedRowHeight: Int,
): FoldGeometry {
    fun mix(a: Float, b: Float, progress: Float) = a + (b - a) * progress
    val p = fold.coerceIn(0f, 1f)
    val s = search.coerceIn(0f, 1f)
    val rowHeight = mix(expandedRowHeight.toFloat(), collapsedRowHeight.toFloat(), p).roundToInt()
    val circle = rowHeight.coerceAtMost(width)
    val rowY = if (hasPlayer) ((playerHeight + spacing) * (1f - p)).roundToInt() else 0
    val tabWidth = (width - 2 * inset) / tabsCount.coerceAtLeast(1)
    val searchCenter = inset + tabWidth * (tabsCount - 0.5f)
    val searchWidth = mix(circle.toFloat(), (width - circle - spacing).coerceAtLeast(circle).toFloat(), s * (1f - p)).roundToInt()
    val detachedSearchX = mix(searchCenter - circle / 2f, (width - circle).toFloat(), p)
    return FoldGeometry(
        height = rowHeight + rowY,
        rowY = rowY,
        navWidth = mix(mix(width.toFloat(), circle.toFloat(), s), circle.toFloat(), p).roundToInt(),
        searchWidth = searchWidth,
        searchX = mix(detachedSearchX, (width - searchWidth).toFloat(), s).roundToInt(),
        selectedX = mix(inset + tabWidth * (selectedIndex + 0.5f) - circle / 2f, 0f, maxOf(p, s)).roundToInt(),
        playerX = mix(0f, (circle + spacing).toFloat(), p).roundToInt(),
        playerY = (rowY - mix((playerHeight + spacing).toFloat(), (playerHeight - rowHeight) / 2f, p)).roundToInt(),
    )
}
