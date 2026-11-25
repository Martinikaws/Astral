package com.music.spotui.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnappyPlayTest {
    @Test fun restartBackoffDoublesAndCapsAt30s() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..6).map(::restartDelayMs))
        assertEquals(1_000L, restartDelayMs(-3))
        assertEquals(30_000L, restartDelayMs(Int.MAX_VALUE))
    }

    @Test fun takeBackOnlyWhenAnotherDeviceHoldsPlayback() {
        assertNull(remoteActiveId(holdsPlayback = false, activeId = null, ownId = "me"))
        assertNull(remoteActiveId(holdsPlayback = false, activeId = "me", ownId = "me"))
        assertNull(remoteActiveId(holdsPlayback = true, activeId = "tv", ownId = "me"))
        assertEquals("tv", remoteActiveId(holdsPlayback = false, activeId = "tv", ownId = "me"))
    }
}
