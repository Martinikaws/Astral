/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/BlurTransformation.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed; Coil's Transformation is replaced by Glide's BitmapTransformation
 * (cache key through updateDiskCacheKey, equals and hashCode kept); the blur itself is unchanged.
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
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Blurs an image while it is being decoded.
 *
 * The page backdrop went through two wrong versions before this one. First
 * `Modifier.blur` at 80dp: correct-looking, but a RuntimeEffect over the whole
 * window re-run on every frame the layer changed, which is most of what made
 * expanding the player stutter. Then a 32px bitmap stretched to the window,
 * which costs nothing and looks it — bilinear upscaling from that size leaves
 * visible square blocks rather than a wash.
 *
 * Blurring at decode time is the version that is both: the work happens once
 * per cover, on a small bitmap, and Coil caches the result. What reaches the
 * screen is genuinely smooth, and drawing it is an ordinary image draw.
 *
 * A box blur run twice rather than a gaussian: two box passes approximate a
 * gaussian closely enough that nobody can tell on an image that is about to be
 * stretched thirty times, and it is a handful of adds per pixel.
 *
 * @param radius in pixels of the *decoded* image, not of the screen.
 */
class BlurTransformation(private val radius: Int = 12, private val passes: Int = 2) :
    BitmapTransformation() {

    private val cacheKey: String = "${javaClass.name}-$radius-$passes"

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(cacheKey.toByteArray(Charsets.UTF_8))
    }

    override fun equals(other: Any?): Boolean = other is BlurTransformation && other.cacheKey == cacheKey

    override fun hashCode(): Int = cacheKey.hashCode()

    override fun transform(pool: BitmapPool, input: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = input.width
        val height = input.height
        if (width <= 1 || height <= 1) return input

        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        val scratch = IntArray(width * height)
        repeat(passes) {
            boxBlurHorizontal(pixels, scratch, width, height, radius)
            boxBlurVertical(scratch, pixels, width, height, radius)
        }

        // ARGB_8888 rather than the input's config: a hardware bitmap cannot be
        // read back, and Glide hands those out unless told otherwise.
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlurHorizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val windowSize = 2 * radius + 1
        for (y in 0 until height) {
            val row = y * width
            val first = source[row]
            val firstA = (first ushr 24) and 0xFF
            val firstR = (first ushr 16) and 0xFF
            val firstG = (first ushr 8) and 0xFF
            val firstB = first and 0xFF

            var a = firstA * (radius + 1)
            var r = firstR * (radius + 1)
            var g = firstG * (radius + 1)
            var b = firstB * (radius + 1)

            for (i in 1..radius) {
                val sample = source[row + i.coerceAtMost(width - 1)]
                a += (sample ushr 24) and 0xFF
                r += (sample ushr 16) and 0xFF
                g += (sample ushr 8) and 0xFF
                b += sample and 0xFF
            }

            for (x in 0 until width) {
                target[row + x] = pack(a / windowSize, r / windowSize, g / windowSize, b / windowSize)

                val outIdx = (x - radius).coerceAtLeast(0)
                val inIdx = (x + radius + 1).coerceAtMost(width - 1)

                val outSample = source[row + outIdx]
                val inSample = source[row + inIdx]

                a += ((inSample ushr 24) and 0xFF) - ((outSample ushr 24) and 0xFF)
                r += ((inSample ushr 16) and 0xFF) - ((outSample ushr 16) and 0xFF)
                g += ((inSample ushr 8) and 0xFF) - ((outSample ushr 8) and 0xFF)
                b += (inSample and 0xFF) - (outSample and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val windowSize = 2 * radius + 1
        for (x in 0 until width) {
            val first = source[x]
            val firstA = (first ushr 24) and 0xFF
            val firstR = (first ushr 16) and 0xFF
            val firstG = (first ushr 8) and 0xFF
            val firstB = first and 0xFF

            var a = firstA * (radius + 1)
            var r = firstR * (radius + 1)
            var g = firstG * (radius + 1)
            var b = firstB * (radius + 1)

            for (i in 1..radius) {
                val sample = source[i.coerceAtMost(height - 1) * width + x]
                a += (sample ushr 24) and 0xFF
                r += (sample ushr 16) and 0xFF
                g += (sample ushr 8) and 0xFF
                b += sample and 0xFF
            }

            for (y in 0 until height) {
                target[y * width + x] = pack(a / windowSize, r / windowSize, g / windowSize, b / windowSize)

                val outIdx = (y - radius).coerceAtLeast(0)
                val inIdx = (y + radius + 1).coerceAtMost(height - 1)

                val outSample = source[outIdx * width + x]
                val inSample = source[inIdx * width + x]

                a += ((inSample ushr 24) and 0xFF) - ((outSample ushr 24) and 0xFF)
                r += ((inSample ushr 16) and 0xFF) - ((outSample ushr 16) and 0xFF)
                g += ((inSample ushr 8) and 0xFF) - ((outSample ushr 8) and 0xFF)
                b += (inSample and 0xFF) - (outSample and 0xFF)
            }
        }
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

}
