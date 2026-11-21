// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

/** How the car's immersive player lays itself out in the part of the screen it may use. */
enum class CarLayoutMode {
    /** Cover, credits and progress on the left, the synced lyrics on the right (a tablet in landscape). */
    WIDE,

    /** Cover and credits as a compact header, the lyrics below (a split-screen column, a portrait strip). */
    STACKED,

    /** Cover, credits and the line being sung only: too small for a list of lyrics. */
    COMPACT,
}

/** The thresholds of [carLayoutMode], in dp. */
object CarLayoutRules {
    /** A wide layout needs at least this much width... */
    const val WIDE_MIN_WIDTH_DP = 600f

    /** ...and a width/height ratio of at least this (a near-square area stacks instead). */
    const val WIDE_MIN_ASPECT = 1.25f

    /** Below this height no wide layout fits a lyric list. */
    const val WIDE_MIN_HEIGHT_DP = 200f

    /** A stacked layout needs room for the header and a few lyric lines. */
    const val STACKED_MIN_WIDTH_DP = 240f
    const val STACKED_MIN_HEIGHT_DP = 320f
}

/** The layout for a visible area of [widthDp] x [heightDp] (what the car host leaves free of its own UI). */
fun carLayoutMode(widthDp: Float, heightDp: Float): CarLayoutMode = when {
    widthDp <= 0f || heightDp <= 0f -> CarLayoutMode.COMPACT
    widthDp >= CarLayoutRules.WIDE_MIN_WIDTH_DP &&
        heightDp >= CarLayoutRules.WIDE_MIN_HEIGHT_DP &&
        widthDp / heightDp >= CarLayoutRules.WIDE_MIN_ASPECT -> CarLayoutMode.WIDE
    widthDp >= CarLayoutRules.STACKED_MIN_WIDTH_DP && heightDp >= CarLayoutRules.STACKED_MIN_HEIGHT_DP -> CarLayoutMode.STACKED
    else -> CarLayoutMode.COMPACT
}

/** The margins, in pixels, between the edges of the surface and the area the content may use. */
data class CarInsetsPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val None = CarInsetsPx(0, 0, 0, 0)
    }
}

/**
 * The margins that leave only the visible rectangle ([left], [top], [right], [bottom], in surface pixels)
 * of a [surfaceWidth] x [surfaceHeight] surface. An empty or unknown rectangle means the whole surface.
 */
fun carInsets(surfaceWidth: Int, surfaceHeight: Int, left: Int, top: Int, right: Int, bottom: Int): CarInsetsPx {
    if (right <= left || bottom <= top) return CarInsetsPx.None
    val l = left.coerceIn(0, surfaceWidth)
    val t = top.coerceIn(0, surfaceHeight)
    val r = right.coerceIn(l, surfaceWidth)
    val b = bottom.coerceIn(t, surfaceHeight)
    if (r <= l || b <= t) return CarInsetsPx.None
    return CarInsetsPx(l, t, surfaceWidth - r, surfaceHeight - b)
}

/**
 * The index of the lyric line being sung at [positionMs]: the last one that started, or -1 before the
 * first (and for [lineTimesMs] without any real time, which is not synced).
 */
fun activeLyricLine(lineTimesMs: List<Long>, positionMs: Long): Int {
    if (lineTimesMs.none { it > 0L }) return -1
    var active = -1
    for (i in lineTimesMs.indices) {
        if (lineTimesMs[i] <= positionMs) active = i else break
    }
    return active
}

/** A rectangle of the surface, in pixels (a plain type so the area choice can be unit-tested). */
data class CarAreaPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = right <= left || bottom <= top
}

/**
 * The margins for the content of a [surfaceWidth] x [surfaceHeight] surface: the [stable] area (it does not
 * change when the host shows or hides the action strip, so the layout does not jump), else the [visible]
 * one, else the whole surface. Empty or unreported areas are skipped.
 */
fun carContentInsets(surfaceWidth: Int, surfaceHeight: Int, stable: CarAreaPx?, visible: CarAreaPx?): CarInsetsPx {
    val area = listOfNotNull(stable, visible).firstOrNull { !it.isEmpty } ?: return CarInsetsPx.None
    return carInsets(surfaceWidth, surfaceHeight, area.left, area.top, area.right, area.bottom)
}
