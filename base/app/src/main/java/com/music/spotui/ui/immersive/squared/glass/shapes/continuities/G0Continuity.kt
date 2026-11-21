/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/shapes/continuities/G0Continuity.kt at 139441f.
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
 * capsule/src/main/java/com/kyant/capsule/continuities/G0Continuity.kt
 *
 * Vendored so the shape ships as source with this app. Package renamed
 * accordingly (com.kyant.capsule -> com.music.spotui.ui.immersive.squared.glass.shapes).
 */
package com.music.spotui.ui.immersive.squared.glass.shapes.continuities

import androidx.compose.runtime.Immutable
import com.music.spotui.ui.immersive.squared.glass.shapes.Continuity
import com.music.spotui.ui.immersive.squared.glass.shapes.path.PathSegments
import com.music.spotui.ui.immersive.squared.glass.shapes.path.buildPathSegments

@Immutable
data object G0Continuity : Continuity {

    override fun createRoundedRectanglePathSegments(
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double
    ): PathSegments {
        return buildPathSegments {
            moveTo(0.0, topLeft)
            if (topLeft > 0.0) {
                lineTo(topLeft, 0.0)
            }
            lineTo(width - topRight, 0.0)
            if (topRight > 0.0) {
                lineTo(width, topRight)
            }
            lineTo(width, height - bottomRight)
            if (bottomRight > 0.0) {
                lineTo(width - bottomRight, height)
            }
            lineTo(bottomLeft, height)
            if (bottomLeft > 0.0) {
                lineTo(0.0, height - bottomLeft)
            }
            close()
        }
    }

    override fun lerp(stop: Continuity, fraction: Double): Continuity {
        return when (stop) {
            is G0Continuity,
            is G1Continuity,
            is G2Continuity -> if (fraction < 0.5) this else stop

            else -> stop.lerp(this, 1f - fraction)
        }
    }
}
