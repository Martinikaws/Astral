/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/MotionCover.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: use Astral's shared video, lifecycle and frame-sampling path.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.myspoti.engine.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fades a muted moving cover over the still artwork once its first frame is available. */
@Composable
fun MotionCover(
    url: String,
    modifier: Modifier = Modifier,
    onFrame: ((Bitmap) -> Unit)? = null,
    playing: Boolean = true,
) {
    var firstFrame by remember(url) { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(APPEAR_MS),
        label = "motionAppear",
    )

    SquaredLoopVideo(
        url = url,
        layout = R.layout.squared_motion_cover,
        playing = playing,
        modifier = modifier.graphicsLayer { alpha = appear },
        onFrameVisible = { firstFrame = it },
        sampleEveryMs = FRAME_EVERY_MS,
        sampleFrame = onFrame?.let { consume ->
            { view ->
                val height = (FRAME_PX.toLong() * view.height / view.width).toInt().coerceAtLeast(1)
                view.copyFrame(FRAME_PX, height)?.let { frame ->
                    var delivered = false
                    try {
                        withContext(Dispatchers.Default) {
                            consume(frame)
                            delivered = true
                        }
                    } finally {
                        if (!delivered) frame.recycle()
                    }
                }
                Unit
            }
        },
    )
}

/** How long a moving cover takes to come up over its still. */
private const val APPEAR_MS = 450

/**
 * How wide the copies of the frames are read, and how often.
 *
 * Small, because what is made of them is a blur and a band of colour. Not as
 * small as it was: forty-eight across, stretched to the screen under the
 * cover, showed as a grid of squares down the fade. And as often as the cover
 * changes, about thirty times a second: read less often, the colours under it
 * trailed the picture and stepped from one to the next. The cover only moves
 * on the player's own screen (a panel stills it), so this is the one place it
 * costs anything.
 *
 * Less often since then, about seven times a second. Every copy is a read of
 * the video on the render thread, and redraws the blurred band below the
 * cover; at fifteen a second those two were most of what the player's frames
 * spent. The blend that follows the copies takes bigger steps to keep pace;
 * see FRAME_FOLLOW.
 */
private const val FRAME_PX = 96
private const val FRAME_EVERY_MS = 150L
