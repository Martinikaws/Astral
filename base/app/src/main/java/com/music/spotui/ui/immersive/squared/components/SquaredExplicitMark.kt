/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/ExplicitMark.kt at 139441f.
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The explicit-content rating, as the small square "E" every music app draws
 * for it.
 *
 * In the colour of the line it sits on rather than a colour of its own: it is
 * a fact about the song, not a warning this app is giving, and the source says
 * only that the rating is there.
 */
@Composable
fun ExplicitMark(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    // Black on a light mark and white on a dark one, worked out from the mark
    // itself: the colour it is drawn in is the colour of whatever line it sits
    // on, which on a page taken from a record can be anything.
    val ink = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = modifier.size(SIZE).background(color, RoundedCornerShape(CORNER)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall,
            fontSize = TEXT,
            lineHeight = TEXT,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
    }
}

private val SIZE = 15.dp
private val CORNER = 3.dp
private val TEXT = 10.sp
