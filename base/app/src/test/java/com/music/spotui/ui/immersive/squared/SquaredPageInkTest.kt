package com.music.spotui.ui.immersive.squared

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class SquaredPageInkTest {
    @Test fun fallbackInkMeetsTextContrastOnPaletteColors() {
        val grounds = listOf(
            Color.Black, Color.White, Color(0xFFD6D7D1), Color(0xFF385147),
            Color(0xFF767676), Color(0xFF777777), Color(0xFF597989),
        )
        grounds.forEach { ground -> assertTrue(contrastRatio(inkOn(ground), ground) >= 4.5f) }
    }

    @Test fun fallbackInkMeetsTextContrastAcrossGrayRamp() {
        (0..255).forEach { value ->
            val ground = Color(value, value, value)
            assertTrue("Gray $value has insufficient contrast", contrastRatio(inkOn(ground), ground) >= 4.5f)
        }
    }
}
