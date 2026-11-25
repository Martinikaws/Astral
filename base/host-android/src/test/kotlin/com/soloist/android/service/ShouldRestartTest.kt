package com.soloist.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldRestartTest {
    @Test fun firstDeathRestartsEvenWhenQuick() = assertTrue(shouldRestart(1_000, afterRestart = false))
    @Test fun quickDeathAfterRestartGivesUp() = assertFalse(shouldRestart(29_999, afterRestart = true))
    @Test fun healthyRunAfterRestartRestartsAgain() = assertTrue(shouldRestart(30_000, afterRestart = true))
}
