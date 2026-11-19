package com.music.spotui.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBlurBitmapTest {

    @Test
    fun radiusZeroAndInvalidDimensionsReturnCopySafely() {
        val src = intArrayOf(0xFF112233.toInt(), 0x80445566.toInt())

        val zeroRadius = rgbaBoxBlur3Pass(src, width = 2, height = 1, radius = 0)
        assertNotSame(src, zeroRadius)
        assertArrayEquals(src, zeroRadius)

        val zeroWidth = rgbaBoxBlur3Pass(src, width = 0, height = 1, radius = 4)
        assertNotSame(src, zeroWidth)
        assertArrayEquals(src, zeroWidth)

        val shortPixels = rgbaBoxBlur3Pass(src, width = 2, height = 2, radius = 2)
        assertNotSame(src, shortPixels)
        assertArrayEquals(src, shortPixels)
    }

    @Test
    fun singlePixelImageIsPreservedForAnyRadius() {
        val single = intArrayOf(0xCC336699.toInt())
        val out = rgbaBoxBlur3Pass(single, width = 1, height = 1, radius = 12)
        assertEquals(single[0], out[0])
    }

    @Test
    fun centeredWhitePixelDiffusesSymmetricallyWithoutDarkFringe() {
        val size = 9
        val pixels = IntArray(size * size)
        val center = size / 2
        pixels[center * size + center] = 0xFFFFFFFF.toInt()

        val out = rgbaBoxBlur3Pass(pixels, width = size, height = size, radius = 1)

        val centerAlpha = (out[center * size + center] ushr 24) and 0xFF
        assertTrue("Center should remain brightest after diffusion", centerAlpha > 0)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val c = out[y * size + x]
                val a = (c ushr 24) and 0xFF
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF

                // Symmetric around center horizontally and vertically.
                assertEquals(c, out[y * size + (size - 1 - x)])
                assertEquals(c, out[(size - 1 - y) * size + x])

                if (a > 0) {
                    assertTrue("Center alpha ($centerAlpha) should be >= ($x,$y) alpha ($a)", centerAlpha >= a)
                    assertEquals("Pure white must not bleed dark RGB into semi-transparent halo at ($x,$y)", 255, r)
                    assertEquals("Pure white must not bleed dark RGB into semi-transparent halo at ($x,$y)", 255, g)
                    assertEquals("Pure white must not bleed dark RGB into semi-transparent halo at ($x,$y)", 255, b)
                } else {
                    assertEquals(0, c)
                }
            }
        }
    }

    @Test
    fun premultipliedAlphaPreventsDarkOrJunkRgbBleedingIntoTransparentPadding() {
        val width = 21
        val height = 21
        val pixels = IntArray(width * height)
        // Put opaque pure bright cyan in a 7x7 core surrounded by transparent 0x00000000 and
        // transparent white 0x00FFFFFF to verify alpha=0 RGB values are ignored.
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = when {
                    x in 7..13 && y in 7..13 -> 0xFF00FFFF.toInt()
                    (x + y) % 2 == 0 -> 0x00000000
                    else -> 0x00FFFFFF
                }
            }
        }

        val out = rgbaBoxBlur3Pass(pixels, width, height, radius = 3)

        var sawSemiTransparentHalo = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = out[y * width + x]
                val a = (c ushr 24) and 0xFF
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                if (a in 1..254) {
                    sawSemiTransparentHalo = true
                    assertEquals("Transparent neighbors must not bleed red into cyan halo at ($x,$y)", 0, r)
                    assertEquals("Halo green must stay full brightness at ($x,$y)", 255, g)
                    assertEquals("Halo blue must stay full brightness at ($x,$y)", 255, b)
                }
            }
        }
        assertTrue("Expected semi-transparent halo pixels around padded region", sawSemiTransparentHalo)
        // Outer corners far from the core remain transparent.
        assertEquals(0, out[0])
    }
}
