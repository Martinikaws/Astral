/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/backdrop/effects/Blur.kt at 139441f.
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
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.music.spotui.ui.immersive.squared.glass.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import com.music.spotui.ui.immersive.squared.glass.backdrop.BackdropEffectScope
import com.music.spotui.ui.immersive.squared.glass.backdrop.isRenderEffectSupported

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    renderEffect =
        BlurEffect(
            renderEffect,
            radius,
            radius,
            edgeTreatment
        )
}
