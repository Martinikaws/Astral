package com.music.spotui.ui.immersive.squared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SquaredPlaybackRegressionTest {
    @Test fun flickAndReleaseBelowHalfwayCanOpenTheSheet() {
        assertTrue(squaredDragOpens(0.1f, -810f))
        assertTrue(squaredDragOpens(0.45f, 0f))
        assertFalse(squaredDragOpens(0.41f, 810f))
        assertFalse(squaredDragOpens(0.39f, 0f))
    }

}
