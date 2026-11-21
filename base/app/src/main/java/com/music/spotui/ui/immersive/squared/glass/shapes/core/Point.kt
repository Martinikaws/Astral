/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/shapes/core/Point.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed to com.music.spotui.ui.immersive.squared.glass (and R to Astral's);
 * otherwise verbatim.
 *
 * Astral's and Square's changes are under the GNU General Public License, version 3
 * (base/licenses/GPL-3.0.txt). The original work's own notice follows and stays in force for
 * the code it covers.
 */
/*
 * Vendored from Kyant0/Capsule
 * https://github.com/Kyant0/Capsule — Copyright 2025 Kyant0, Apache License 2.0
 * capsule/src/main/java/com/kyant/capsule/core/Point.kt
 *
 * Vendored so the shape ships as source with this app. Package renamed
 * accordingly (com.kyant.capsule -> com.music.spotui.ui.immersive.squared.glass.shapes).
 */
package com.music.spotui.ui.immersive.squared.glass.shapes.core

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.music.spotui.ui.immersive.squared.glass.shapes.lerp
import kotlin.math.sqrt

@Immutable
data class Point(val x: Double, val y: Double) {

    @Stable
    operator fun unaryMinus(): Point {
        return Point(-x, -y)
    }

    @Stable
    operator fun minus(other: Point): Point {
        return Point(x - other.x, y - other.y)
    }

    @Stable
    operator fun plus(other: Point): Point {
        return Point(x + other.x, y + other.y)
    }

    @Stable
    operator fun times(operand: Double): Point {
        return Point(x * operand, y * operand)
    }

    @Stable
    operator fun div(operand: Double): Point {
        return Point(x / operand, y / operand)
    }

    @Stable
    fun normalized(): Point {
        val length = sqrt(x * x + y * y)
        return if (length != 0.0) this / length else Zero
    }

    companion object {

        @Stable
        val Zero: Point = Point(0.0, 0.0)
    }
}

@Stable
fun lerp(start: Point, stop: Point, fraction: Double): Point {
    return Point(
        lerp(start.x, stop.x, fraction),
        lerp(start.y, stop.y, fraction)
    )
}
