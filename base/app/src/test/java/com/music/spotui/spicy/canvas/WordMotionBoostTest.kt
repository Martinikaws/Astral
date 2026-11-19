// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, app/src/test/java/com/tx24/spicyplayer/lyrics/spicy/canvas/WordMotionBoostTest.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class WordMotionBoostTest {
    @Test
    fun `growth past 1 is boosted`() {
        assertEquals(1.0625f, boostedScale(1.05f, 1.25f), 1e-5f)
    }

    @Test
    fun `resting scale is left alone`() {
        assertEquals(0.95f, boostedScale(0.95f, 1.25f), 1e-5f)
        assertEquals(1f, boostedScale(1f, 1.25f), 1e-5f)
    }

    @Test
    fun `lift is boosted but the resting dip is not`() {
        assertEquals(-0.025f, boostedLift(-0.02f, 1.25f), 1e-5f)
        assertEquals(0.01f, boostedLift(0.01f, 1.25f), 1e-5f)
    }
}
