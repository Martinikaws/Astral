package com.music.spotui.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoloistRemoteTest {
    @Test fun playsOnlyWhenActiveDeviceIsOwnAndPlaying() {
        assertTrue(SoloistRemote.plays("a", "a", connected = true, active = true, paused = false))
        assertFalse(SoloistRemote.plays("b", "a", true, true, false))
        assertFalse(SoloistRemote.plays(null, "a", true, true, false))
        assertFalse(SoloistRemote.plays("a", "a", true, true, true))
        assertFalse(SoloistRemote.plays("a", "a", false, true, false))
        assertFalse(SoloistRemote.plays("", "", true, true, false))
    }
}
