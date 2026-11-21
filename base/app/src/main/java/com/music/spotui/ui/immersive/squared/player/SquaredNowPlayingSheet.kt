/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/player/NowPlayingSheet.kt at 139441f.
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
package com.music.spotui.ui.immersive.squared.player

import com.music.spotui.ui.immersive.squared.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The now-playing bar and the player, as one surface.
 *
 * The previous version made the player a navigation destination and tried to
 * animate between the two. That can only ever cross-fade: they are separate
 * screens, both on stage at once, and no amount of shared-element work hides
 * that the bar and the player are two different things pretending to be one.
 *
 * They still are, in the sense that matters: one `progress` value owns both, so
 * the bar and the player are two views of a single state rather than two screens
 * taking turns.
 *
 * How that value is spent went through a growing container — the bar's height
 * stretched up to the window's, with the player already laid out full size
 * inside it. It looked right and it was never quite smooth: resizing that
 * container re-ran a layout pass over a screen holding a video surface and
 * several pieces of live glass, sixty times a second, and deferring every state
 * read only moved the cost around.
 *
 * So the travel is now a transform and nothing else. The player is full size
 * from the first frame, rises a little way into place and fades in as the bar
 * fades out under it. One layer, scaled and faded on the render thread, with no
 * measure or layout anywhere in the frame.
 *
 * The same value is what a drag writes to, which is why dragging down works at
 * any point and can be released anywhere: expansion is a position, not an event.
 *
 * There is no collapsed half any more. There used to be one — a bar the width of
 * the screen, carrying what was playing and the drag that opened the player — and
 * once the tab bar grew its own now-playing pill it stayed behind as an empty
 * strip: invisible, sixty-odd dp tall, sitting across the bottom of every page
 * and still listening for a vertical drag. An upward swipe over the last of a
 * list opened the player, from a thing nobody could see. What opens it now is the
 * pill itself, which is where what is playing actually is.
 *
 * @param progress 0 while collapsed, 1 while expanded. Hoisted because the tab
 *   bar has to fade out against it.
 */
@Composable
fun NowPlayingSheet(
    progress: Animatable<Float, *>,
    onCollapse: () -> Unit,
    /**
     * Drawn under the player, fading in with it.
     *
     * The player used to be a destination stacked over the app's own backdrop
     * and so had no background of its own. As a sheet it sits *above* the page,
     * so without this the home screen showed through every track that has no
     * Canvas to cover it.
     */
    background: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
) {

    // Nothing here reads `progress.value` during composition, and that is the
    // whole performance story of this file.
    //
    // Reading it in the composable body recomposed the sheet — and with it the
    // entire player and the bar — on every frame of the travel, which measured
    // at a 97ms median frame and a 250ms worst case. Read from inside a layout
    // or a graphicsLayer block instead, the same value only re-measures or
    // re-draws, which is two phases cheaper.
    //
    // These two are derived rather than read directly so a recomposition
    // happens when the answer *changes*, not when the number does.
    val expandedVisible by remember { derivedStateOf { progress.value > 0.001f } }

    // Live video and glass return once the expanded sheet stops moving.
    val settled by remember {
        derivedStateOf { progress.value >= 0.999f && !progress.isRunning }
    }

    Box(Modifier.fillMaxSize()) {
        BackHandler(enabled = expandedVisible, onBack = onCollapse)

        // The player, full size from the first frame to the last. Nothing about
        // the travel touches measure or layout any more: the growing container
        // that used to drive it re-ran a layout pass on the whole player every
        // frame, and no amount of deferring state reads makes that free on a
        // screen with a video surface and a stack of glass in it. What is left
        // is one layer being scaled and faded, which the render thread does
        // without waking the UI thread at all.
        if (expandedVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = progress.value.coerceIn(0f, 1f)
                        alpha = f
                        // Rises into place from just below, slightly smaller.
                        // Small numbers on purpose — this is the player arriving
                        // where it already is, not flying in from off-screen.
                        val scale = ENTER_SCALE + (1f - ENTER_SCALE) * f
                        scaleX = scale
                        scaleY = scale
                        translationY = (1f - f) * size.height * ENTER_TRAVEL
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        // Rounded while it is on its way in and square once it
                        // has arrived, so it reads as a sheet rather than as a
                        // screen appearing.
                        shape = RoundedCornerShape(SHEET_CORNER * (1f - f))
                        clip = true
                        // Modulated rather than composited: the default draws
                        // the whole subtree into an offscreen buffer the size of
                        // the window before applying alpha, every frame.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            ) {
                background()
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalGlassEnabled provides settled,
                    LocalPlayerSettled provides settled,
                ) {
                    expandedContent()
                }
            }
        }
    }
}

/** How small the player starts, growing to its own size as it arrives. */
private const val ENTER_SCALE = 0.92f

/** How far below its place it starts, as a fraction of its height. */
private const val ENTER_TRAVEL = 0.06f

private val SHEET_CORNER = 22.dp
