/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRemoteBatteryTest {
    private fun baseSignature(
        playing: Boolean = true,
        buffering: Boolean = false,
        positionMs: Long = 30_000L,
        atElapsedMs: Long = 100_000L,
        speed: Float = 1f,
    ) = StateSignature(
        hasSession = true,
        title = "Let It Happen",
        artist = "Tame Impala",
        album = "Currents",
        contextTitle = "Currents",
        playing = playing,
        buffering = buffering,
        episode = false,
        explicit = false,
        liked = true,
        durationMs = 467_000L,
        speed = speed,
        shuffle = false,
        repeat = "off",
        volume = 70,
        deviceName = "",
        deviceType = "",
        bgColor = 0xFF201020.toInt(),
        accentColor = 0xFF1ED760.toInt(),
        artKey = "track1|https://img.example/cover|2",
        vizKey = "spotify:track:1",
        positionMs = positionMs,
        atElapsedMs = atElapsedMs,
    )

    @Test
    fun publishModeGatesOnWatchPresenceAndActiveUi() {
        assertEquals(PublishMode.NONE, publishModeFor(hasWatch = false, watchActive = false))
        assertEquals(PublishMode.NONE, publishModeFor(hasWatch = false, watchActive = true))
        assertEquals(PublishMode.MINIMAL_LAZY, publishModeFor(hasWatch = true, watchActive = false))
        assertEquals(PublishMode.FULL_URGENT, publishModeFor(hasWatch = true, watchActive = true))
    }

    @Test
    fun watchActiveTtlExpiresAfterTenMinutesOrDisconnect() {
        assertEquals(600_000L, WATCH_ACTIVE_TTL_MS)
        val until = 1_000L + WATCH_ACTIVE_TTL_MS
        assertTrue(isWatchActiveAt(watchActive = true, hasWatch = true, nowElapsedMs = 1_000L, untilElapsedMs = until))
        assertTrue(isWatchActiveAt(watchActive = true, hasWatch = true, nowElapsedMs = until - 1L, untilElapsedMs = until))
        assertFalse(isWatchActiveAt(watchActive = true, hasWatch = true, nowElapsedMs = until, untilElapsedMs = until))
        assertFalse(isWatchActiveAt(watchActive = true, hasWatch = false, nowElapsedMs = 2_000L, untilElapsedMs = until))
        assertFalse(isWatchActiveAt(watchActive = false, hasWatch = true, nowElapsedMs = 2_000L, untilElapsedMs = until))
    }

    @Test
    fun artKeyRemainsStableAcrossPublishesUntilArtVersionChanges() {
        val k1 = buildArtKey("id1", "https://cdn/art.jpg", artVersion = 3)
        val k2 = buildArtKey("id1", "https://cdn/art.jpg", artVersion = 3)
        val k3 = buildArtKey("id1", "https://cdn/art.jpg", artVersion = 4)
        assertEquals(k1, k2)
        assertNotEquals(k1, k3)
    }

    @Test
    fun semanticDedupIgnoresEpochAndExpectedPlaybackDrift() {
        val prev = baseSignature(playing = true, positionMs = 30_000L, atElapsedMs = 100_000L, speed = 1f)
        assertFalse(isSemanticallyDuplicateState(null, prev))

        // 5s later with exact playback progression -> duplicate.
        val exactProgression = prev.copy(positionMs = 35_000L, atElapsedMs = 105_000L)
        assertTrue(isSemanticallyDuplicateState(prev, exactProgression))

        // Within 1,500 ms drift tolerance -> duplicate.
        val minorDrift = prev.copy(positionMs = 36_200L, atElapsedMs = 105_000L)
        assertTrue(isSemanticallyDuplicateState(prev, minorDrift))

        // Speed 1.5x progression -> duplicate.
        val prevFast = baseSignature(playing = true, positionMs = 20_000L, atElapsedMs = 100_000L, speed = 1.5f)
        val fastProgression = prevFast.copy(positionMs = 26_000L, atElapsedMs = 104_000L)
        assertTrue(isSemanticallyDuplicateState(prevFast, fastProgression))

        // Real seek (> 1,500 ms from expected) -> NOT duplicate.
        val seekForward = prev.copy(positionMs = 37_000L, atElapsedMs = 105_000L)
        assertFalse(isSemanticallyDuplicateState(prev, seekForward))

        val seekBack = prev.copy(positionMs = 25_000L, atElapsedMs = 105_000L)
        assertFalse(isSemanticallyDuplicateState(prev, seekBack))
    }

    @Test
    fun semanticDedupDetectsControlAndMetadataChangesAndPausedState() {
        val paused = baseSignature(playing = false, positionMs = 40_000L, atElapsedMs = 200_000L)
        assertTrue(isSemanticallyDuplicateState(paused, paused.copy(atElapsedMs = 230_000L)))
        assertFalse(isSemanticallyDuplicateState(paused, paused.copy(positionMs = 45_000L, atElapsedMs = 230_000L)))

        val playing = baseSignature(playing = true, positionMs = 40_000L, atElapsedMs = 200_000L)
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(playing = false)))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(buffering = true)))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(title = "New Song")))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(liked = false)))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(shuffle = true)))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(repeat = "all")))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(volume = 80)))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(deviceName = "Living Room", deviceType = "speaker")))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(artKey = "track2|url|3")))
        assertFalse(isSemanticallyDuplicateState(playing, playing.copy(vizKey = "spotify:track:2")))
    }
}
