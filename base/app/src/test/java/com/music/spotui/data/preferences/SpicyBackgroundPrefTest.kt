package com.music.spotui.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpicyBackgroundPrefTest {
    @Test fun `the renderer is on by default and the Default preset is chosen`() {
        val d = SpicyBackgroundSettings()
        assertTrue(d.renderer)
        assertEquals(SpicyBackgroundType.DEFAULT, d.type)
        assertFalse(d.still)
        assertFalse(d.beatReactive)
    }

    @Test fun `a preset is found by its key and an unknown key falls back to Default`() {
        SpicyBackgroundType.entries.forEach { assertEquals(it, SpicyBackgroundType.fromKey(it.key)) }
        assertEquals(SpicyBackgroundType.DEFAULT, SpicyBackgroundType.fromKey("nonsense"))
        assertEquals(SpicyBackgroundType.DEFAULT, SpicyBackgroundType.fromKey(null))
    }

    @Test fun `the keys are unique`() {
        assertEquals(SpicyBackgroundType.entries.size, SpicyBackgroundType.entries.map { it.key }.toSet().size)
    }

    @Test fun `blur is kept per kind of preset and clamped`() {
        val moving = SpicyBackgroundSettings().withBlur(500)
        assertEquals(SpicyBackgroundSettings.MAX_DYNAMIC_BLUR, moving.blur)
        assertEquals(SpicyBackgroundSettings.DEFAULT_IMAGE_BLUR, moving.imageBlur)
        val still = SpicyBackgroundSettings(type = SpicyBackgroundType.COVER_ART).withBlur(-4)
        assertEquals(0, still.blur)
        assertEquals(SpicyBackgroundSettings.DEFAULT_DYNAMIC_BLUR, still.dynamicBlur)
        assertEquals(SpicyBackgroundSettings.MAX_IMAGE_BLUR, SpicyBackgroundSettings(type = SpicyBackgroundType.AUTO).maxBlur)
    }

    @Test fun `which presets move, blur, and use the artist header`() {
        assertTrue(SpicyBackgroundType.DEFAULT.moving && SpicyBackgroundType.LEGACY.moving)
        assertFalse(SpicyBackgroundType.COVER_ART.moving)
        assertTrue(SpicyBackgroundType.AUTO.usesArtistHeader && SpicyBackgroundType.ARTIST_HEADER.usesArtistHeader)
        assertFalse(SpicyBackgroundType.COVER_ART.usesArtistHeader)
        assertFalse(SpicyBackgroundType.COLOR.blurs)
        assertFalse(SpicyBackgroundType.CUSTOM.blurs)
        assertTrue(SpicyBackgroundType.COVER_ART.blurs)
    }
}
