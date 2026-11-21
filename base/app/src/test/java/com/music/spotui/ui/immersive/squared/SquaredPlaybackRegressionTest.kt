package com.music.spotui.ui.immersive.squared

import com.music.spotui.data.api.SpicyLyricsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SquaredPlaybackRegressionTest {
    @Test fun flickAndReleaseBelowHalfwayCanOpenTheSheet() {
        assertTrue(squaredDragOpens(0.1f, -810f))
        assertTrue(squaredDragOpens(0.45f, 0f))
        assertFalse(squaredDragOpens(0.41f, 810f))
        assertFalse(squaredDragOpens(0.39f, 0f))
    }

    @Test fun spicyProviderAcceptsOnlyPublishableKeys() {
        assertEquals("sl_pk_test", SpicyLyricsProvider.publishableKey(" sl_pk_test "))
        assertNull(SpicyLyricsProvider.publishableKey(""))
        assertNull(SpicyLyricsProvider.publishableKey("sl_sk_test"))
        assertNull(SpicyLyricsProvider.publishableKey("invalid"))
    }
}
