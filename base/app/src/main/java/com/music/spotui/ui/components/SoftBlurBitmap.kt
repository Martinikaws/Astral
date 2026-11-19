package com.music.spotui.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bounded in-memory cache of pre-blurred artwork bitmaps keyed by `"$url|$sizePx|$blurRadius|$padPx"`. */
private val softBlurCache = LruCache<String, ImageBitmap>(16)

/**
 * Pure JVM 3-pass separable premultiplied-alpha box blur over ARGB_8888 [pixels].
 *
 * Clamping coordinates at edges keeps full-bleed covers opaque to the border when `padPx == 0`
 * and keeps transparent borders at alpha = 0 when `padPx > 0`. Keeping RGB premultiplied across
 * all three passes and unpremultiplying at the end avoids dark edge fringes on transparent halos.
 */
internal fun rgbaBoxBlur3Pass(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    if (width <= 0 || height <= 0 || radius <= 0) return pixels.copyOf()
    val countLong = width.toLong() * height.toLong()
    if (countLong > pixels.size.toLong()) return pixels.copyOf()
    val count = countLong.toInt()

    val bufA = IntArray(count)
    val bufC = IntArray(count)
    val tmp = IntArray(count)
    val out = IntArray(count)

    // Blur alpha first (scaled by 255 to match premultiplied R/G/B resolution).
    for (i in 0 until count) {
        bufA[i] = ((pixels[i] ushr 24) and 0xFF) * 255
    }
    blurChannel3Pass(bufA, tmp, width, height, radius)
    for (i in 0 until count) {
        val outA = ((bufA[i] + 127) / 255).coerceIn(0, 255)
        out[i] = outA shl 24
    }

    // Blur premultiplied R, G, B independently using the blurred alpha sum for unpremultiplication.
    blurAndPackChannel(pixels, out, bufA, bufC, tmp, width, height, radius, shift = 16)
    blurAndPackChannel(pixels, out, bufA, bufC, tmp, width, height, radius, shift = 8)
    blurAndPackChannel(pixels, out, bufA, bufC, tmp, width, height, radius, shift = 0)

    return out
}

private fun blurAndPackChannel(
    pixels: IntArray,
    out: IntArray,
    bufA: IntArray,
    bufC: IntArray,
    tmp: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    shift: Int,
) {
    val count = width * height
    for (i in 0 until count) {
        val p = pixels[i]
        val a = (p ushr 24) and 0xFF
        val c = (p ushr shift) and 0xFF
        bufC[i] = c * a
    }
    blurChannel3Pass(bufC, tmp, width, height, radius)
    for (i in 0 until count) {
        if ((out[i] ushr 24) != 0) {
            val sumA = bufA[i].toLong()
            val half = sumA / 2L
            val c = ((bufC[i].toLong() * 255L + half) / sumA).toInt().coerceIn(0, 255)
            out[i] = out[i] or (c shl shift)
        }
    }
}

private fun blurChannel3Pass(
    buf: IntArray,
    tmp: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    repeat(3) {
        boxBlurChannel1D(
            src = buf,
            dst = tmp,
            lines = height,
            len = width,
            lineStride = width,
            pixelStride = 1,
            radius = radius,
        )
        boxBlurChannel1D(
            src = tmp,
            dst = buf,
            lines = width,
            len = height,
            lineStride = 1,
            pixelStride = width,
            radius = radius,
        )
    }
}

private fun boxBlurChannel1D(
    src: IntArray,
    dst: IntArray,
    lines: Int,
    len: Int,
    lineStride: Int,
    pixelStride: Int,
    radius: Int,
) {
    val div = radius.toLong() * 2L + 1L
    val half = div / 2L
    val maxIdx = len - 1
    for (line in 0 until lines) {
        val base = line * lineStride
        var sum = radius.toLong() * src[base]
        val rightEdge = minOf(radius, maxIdx)
        for (k in 0..rightEdge) {
            sum += src[base + k * pixelStride]
        }
        if (radius > maxIdx) {
            sum += (radius - maxIdx).toLong() * src[base + maxIdx * pixelStride]
        }
        for (i in 0 until len) {
            dst[base + i * pixelStride] = ((sum + half) / div).toInt()
            if (i + 1 < len) {
                val outIdx = (i - radius).coerceIn(0, maxIdx)
                val inIdx = (i + radius + 1).coerceIn(0, maxIdx)
                sum += src[base + inIdx * pixelStride].toLong() - src[base + outIdx * pixelStride].toLong()
            }
        }
    }
}

/** Runs [rgbaBoxBlur3Pass] on [src] and returns a newly allocated `ARGB_8888` [Bitmap]. */
internal fun rgbaBoxBlur3Pass(src: Bitmap, radius: Int): Bitmap {
    val w = src.width.coerceAtLeast(1)
    val h = src.height.coerceAtLeast(1)
    if (src.isRecycled || src.width <= 0 || src.height <= 0) {
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }
    val software = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
        src.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        src
    } ?: return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)
    software.getPixels(pixels, 0, w, 0, 0, w, h)
    if (software !== src) software.recycle()
    val blurred = rgbaBoxBlur3Pass(pixels, w, h, radius)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(blurred, 0, w, 0, 0, w, h)
    }
}

/**
 * Always creates a newly owned `ARGB_8888` bitmap (never returns [resource] directly, even when
 * dimensions match), scaling [resource] centered into `(targetWidth + 2 * padPx) x (targetHeight + 2 * padPx)`.
 */
internal fun createOwnedScaledCopy(
    resource: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    padPx: Int = 0,
): Bitmap {
    val w = targetWidth.coerceAtLeast(1)
    val h = targetHeight.coerceAtLeast(1)
    val pad = padPx.coerceAtLeast(0)
    val out = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
    if (resource.isRecycled || resource.width <= 0 || resource.height <= 0) return out

    val src = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && resource.config == Bitmap.Config.HARDWARE) {
        resource.copy(Bitmap.Config.ARGB_8888, false) ?: return out
    } else {
        resource
    }
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        src,
        Rect(0, 0, src.width, src.height),
        Rect(pad, pad, pad + w, pad + h),
        paint,
    )
    if (src !== resource) src.recycle()
    return out
}

/**
 * Loads [url] via Glide at [sizePx], copies it into an owned software bitmap, runs
 * [rgbaBoxBlur3Pass] on `Dispatchers.Default`, and caches the resulting [ImageBitmap] in memory.
 */
@Composable
internal fun rememberSoftBlurredBitmap(
    url: String?,
    sizePx: Int = 96,
    blurRadius: Int = 8,
    padPx: Int = 0,
): State<ImageBitmap?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeSize = sizePx.coerceAtLeast(1)
    val key = if (url.isNullOrBlank()) null else "$url|$safeSize|$blurRadius|$padPx"
    val cached = key?.let { softBlurCache.get(it) }
    val state = remember { mutableStateOf(cached) }

    DisposableEffect(url, safeSize, blurRadius, padPx) {
        var job: Job? = null
        var target: CustomTarget<Bitmap>? = null

        if (url.isNullOrBlank() || key == null) {
            state.value = null
        } else {
            val hit = softBlurCache.get(key)
            if (hit != null) {
                state.value = hit
            } else {
                val appContext = context.applicationContext
                target = Glide.with(appContext)
                    .asBitmap()
                    .load(url)
                    .override(safeSize, safeSize)
                    .disallowHardwareConfig()
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            val owned = createOwnedScaledCopy(resource, safeSize, safeSize, padPx)
                            job?.cancel()
                            job = scope.launch(Dispatchers.Default) {
                                val blurred = rgbaBoxBlur3Pass(owned, blurRadius)
                                owned.recycle()
                                val imageBitmap = blurred.asImageBitmap()
                                softBlurCache.put(key, imageBitmap)
                                withContext(Dispatchers.Main) {
                                    if (isActive) state.value = imageBitmap
                                }
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) = Unit

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            job?.cancel()
                            state.value = null
                        }
                    })
            }
        }

        onDispose {
            job?.cancel()
            target?.let { Glide.with(context.applicationContext).clear(it) }
        }
    }

    return state
}
