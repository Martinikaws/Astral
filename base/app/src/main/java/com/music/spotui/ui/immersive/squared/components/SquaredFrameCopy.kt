/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/FrameCopy.kt at 139441f.
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
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A small copy of what a video's [TextureView] is showing, read without
 * stopping the screen.
 *
 * `TextureView.getBitmap` is the obvious way, and the costly one: it runs on
 * the main thread and waits there for the render thread and the GPU to finish
 * whatever they are doing before it copies the layer. Measured on the player
 * with a moving cover, that was 27 milliseconds a copy, ten copies a second, a
 * frame lost every time, and more than a fifth of the main thread gone.
 *
 * This asks for the last frame the video queued instead, through PixelCopy,
 * which answers on a thread of its own. Nothing on the main thread waits for
 * it. The surface the video draws into is the same one the view shows, so the
 * picture is the same; scaled to [width] by [height] as it is copied, as
 * getBitmap scaled it.
 *
 * Null when there is nothing to copy yet, or the copy failed.
 */
internal suspend fun TextureView.copyFrame(width: Int, height: Int): Bitmap? {
    val texture = surfaceTexture ?: return null
    if (width <= 0 || height <= 0) return null
    return suspendCancellableCoroutine { done ->
        // Asked from the copy thread as well as answered there. Before Android
        // 14 the request itself waits for the render thread to make the copy,
        // and made from the main thread that wait was the main thread's again.
        copies.post {
            if (!done.isActive) return@post
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // A second handle on the same queue, not a second queue: it is only
            // read from, and releasing it leaves the video's own connection
            // alone. Released once the copy is made, never while it is pending.
            val surface = runCatching { Surface(texture) }.getOrNull()
            if (surface == null) {
                bitmap.recycle()
                done.resume(null)
                return@post
            }
            runCatching {
                PixelCopy.request(surface, bitmap, { result ->
                    surface.release()
                    if (result == PixelCopy.SUCCESS) {
                        done.resume(bitmap) { _, unused, _ -> unused?.recycle() }
                    } else {
                        bitmap.recycle()
                        done.resume(null)
                    }
                }, copies)
            }.onFailure {
                surface.release()
                bitmap.recycle()
                done.resume(null)
            }
        }
    }
}

/** Where the copies are answered: off the main thread, and one at a time. */
private val copies: Handler by lazy {
    Handler(HandlerThread("SquareFrameCopy").apply { start() }.looper)
}
