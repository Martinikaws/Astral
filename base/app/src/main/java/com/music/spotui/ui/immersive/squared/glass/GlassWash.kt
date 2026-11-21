/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/GlassWash.kt at 139441f.
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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.music.spotui.ui.immersive.squared.Ink

/**
 * What the glass in this app holds: the colour of what is playing.
 *
 * The accent comes from the artwork, which means it can be anything — a
 * saturated pink one song and a muddy brown the next. On the app's own mark,
 * one round pane on the page, that is the point. On a row of chips it is not:
 * five saturated capsules read as five buttons demanding a tap, and the labels
 * on them stop being the loudest thing in the row.
 *
 * So a chip wears the same colour pulled most of the way towards the page's
 * ink and at a fraction of the alpha. What survives is the *direction* of the
 * light and a hint of the hue, which is what makes the glass look like glass.
 *
 * @param selected the chosen chip, which carries a little more of it: it is
 *   already filled harder, and the wash should not undo that difference.
 */
@Composable
fun chipWash(selected: Boolean): Color {
    val accent = MaterialTheme.colorScheme.primary
    // Most of the way to the ink: the hue is still there, the saturation
    // mostly is not.
    val quiet = lerp(accent, Ink, 0.55f)
    return quiet.copy(alpha = if (selected) 0.32f else 0.20f)
}
