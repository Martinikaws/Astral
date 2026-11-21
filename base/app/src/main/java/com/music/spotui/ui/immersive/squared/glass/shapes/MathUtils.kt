/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/shapes/MathUtils.kt at 139441f.
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
 * capsule/src/main/java/com/kyant/capsule/MathUtils.kt
 *
 * Vendored so the shape ships as source with this app. Package renamed
 * accordingly (com.kyant.capsule -> com.music.spotui.ui.immersive.squared.glass.shapes).
 */
package com.music.spotui.ui.immersive.squared.glass.shapes

@Suppress("NOTHING_TO_INLINE")
internal inline fun lerp(start: Double, stop: Double, fraction: Double): Double {
    return start + (stop - start) * fraction
}
