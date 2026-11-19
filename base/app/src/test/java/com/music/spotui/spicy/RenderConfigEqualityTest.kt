// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, app/src/test/java/com/tx24/spicyplayer/lyrics/spicy/RenderConfigEqualityTest.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lyrics view keys its layout cache on a config copied with a fresh `ScrollConfig()` on every
 * recomposition. If two default scroll configs ever differ, every recomposition measures the whole
 * lyrics again and keeps the result.
 */
class RenderConfigEqualityTest {
    @Test
    fun `two default scroll configs are equal`() {
        assertEquals(ScrollConfig(), ScrollConfig())
        assertEquals(ScrollConfig().hashCode(), ScrollConfig().hashCode())
    }

    @Test
    fun `a config copied with a fresh default scroll equals the last one`() {
        val config = RenderConfig(bloom = true)
        assertEquals(config.copy(scroll = ScrollConfig()), config.copy(scroll = ScrollConfig()))
    }
}
