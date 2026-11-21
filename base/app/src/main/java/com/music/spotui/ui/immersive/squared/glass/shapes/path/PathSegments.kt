/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/shapes/path/PathSegments.kt at 139441f.
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
 * capsule/src/main/java/com/kyant/capsule/path/PathSegments.kt
 *
 * Vendored so the shape ships as source with this app. Package renamed
 * accordingly (com.kyant.capsule -> com.music.spotui.ui.immersive.squared.glass.shapes).
 */
package com.music.spotui.ui.immersive.squared.glass.shapes.path

import androidx.compose.ui.graphics.Path

typealias PathSegments = List<PathSegment>

fun PathSegments.toPath(): Path {
    return Path().apply {
        if (isEmpty()) return@apply
        val startPoint = first().from
        moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
        forEach { it.drawTo(this) }
    }
}
