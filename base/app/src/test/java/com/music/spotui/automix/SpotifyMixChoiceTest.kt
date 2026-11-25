/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import com.music.spotui.di.Graph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Mix chip follows AutoMix's setting for the playlist and can be turned both ways whatever the global one is. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SpotifyMixChoiceTest {
    private val uri = "spotify:playlist:abc"

    @Before fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, RuntimeEnvironment.getApplication())
        SpotifyMixChoice.markMixed(uri)
        AutoMix.clearPlaylistMix(uri)
    }

    @Test fun globalOffTogglesOnThenBackOffWithoutLeavingAnOverride() {
        AutoMix.set(false)
        assertFalse(SpotifyMixChoice.on(uri))
        SpotifyMixChoice.toggle(uri)
        assertTrue(SpotifyMixChoice.on(uri))
        assertEquals(true, AutoMix.playlistChoice(uri))
        SpotifyMixChoice.toggle(uri)
        assertFalse(SpotifyMixChoice.on(uri))
        assertNull(AutoMix.playlistChoice(uri))
    }

    @Test fun globalOnTogglesOffThenBackOn() {
        AutoMix.set(true)
        assertTrue(SpotifyMixChoice.on(uri))
        SpotifyMixChoice.toggle(uri)
        assertFalse(SpotifyMixChoice.on(uri))
        assertEquals(false, AutoMix.playlistChoice(uri))
        SpotifyMixChoice.toggle(uri)
        assertTrue(SpotifyMixChoice.on(uri))
        assertNull(AutoMix.playlistChoice(uri))
        AutoMix.set(false)
    }
}
