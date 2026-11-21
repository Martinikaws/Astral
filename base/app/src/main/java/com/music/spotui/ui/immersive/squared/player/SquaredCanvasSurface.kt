/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/player/CanvasSurface.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: Astral's PlayerView layout and track-change handling.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.components.SquaredLoopVideo
import com.music.spotui.ui.immersive.squared.components.copyFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A video loop and the colors sampled from its lower edge for the player background. */
@Composable
fun CanvasSurface(
    url: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onFrameVisible: (Boolean) -> Unit = {},
    onTone: (List<Color>) -> Unit = {},
) {
    SquaredLoopVideo(
        url = url,
        layout = R.layout.squared_canvas_surface,
        playing = isPlaying,
        modifier = modifier,
        onFrameVisible = onFrameVisible,
        sampleEveryMs = TONE_INTERVAL_MS,
        sampleFrame = { view ->
            view.copyFrame(TONE_SAMPLE, TONE_SAMPLE)?.let { frame ->
                try {
                    val columns = withContext(Dispatchers.Default) { frame.footColumns() }
                    onTone(columns)
                } finally {
                    frame.recycle()
                }
            }
        },
    )
}

/**
 * The foot of a frame, as a handful of colours across its width.
 *
 * Not one average. What a still cover does below its own edge is carry its last
 * row downwards, so a red shape at the bottom of the picture goes on being red
 * under it while a dark corner stays dark. One colour for the whole width
 * throws exactly that away — a frame with a red flood across the middle and
 * black at the sides would come out an even maroon. These are column averages
 * of the bottom band, which the field below paints as a gradient across.
 *
 * The band, not the whole frame: what the fade meets is the foot of the
 * picture, and a clip whose top half is a bright sky and whose bottom is a dark
 * street averages to neither.
 */
private fun android.graphics.Bitmap.footColumns(): List<Color> {
    val from = (height * 0.72f).toInt().coerceIn(0, height - 1)
    val band = (height - from).coerceAtLeast(1)
    val step = (width / TONE_COLUMNS).coerceAtLeast(1)
    return (0 until TONE_COLUMNS).map { column ->
        val x0 = (column * step).coerceAtMost(width - 1)
        val x1 = (x0 + step).coerceAtMost(width)
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in from until height) {
            for (x in x0 until x1) {
                val pixel = getPixel(x, y)
                r += android.graphics.Color.red(pixel)
                g += android.graphics.Color.green(pixel)
                b += android.graphics.Color.blue(pixel)
                count++
            }
        }
        if (count == 0) Color.Black
        else Color(r / count / 255f, g / count / 255f, b / count / 255f)
    }.also { require(band > 0) }
}

/**
 * How many columns the foot is read as.
 *
 * Eight kept the shape and showed the machinery: a gradient interpolates
 * straight between its stops, so eight of them across a phone is a stop every
 * 45 points and the eye finds each one — the field came out as vertical bands.
 * Twenty-four is one every fifteen points, which reads as a wash.
 */
private const val TONE_COLUMNS = 24

/** How often the clip is read back, in milliseconds. */
private const val TONE_INTERVAL_MS = 900L

/**
 * Side of the thumbnail the colours are taken from.
 *
 * Wide enough that each of the columns above still averages several pixels
 * rather than reading one, which is the difference between a colour and a
 * speck.
 */
private const val TONE_SAMPLE = 96
