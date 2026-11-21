package com.music.spotui.ui.immersive.squared

import androidx.media3.common.Player
import com.music.spotui.ui.immersive.player.ImmersivePlayerState
import com.music.spotui.ui.immersive.player.ImmersiveRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SquaredAdapterLogicTest {
    private fun state(repeat: Int = ImmersiveRepeat.OFF, title: String = "T", artist: String = "A") =
        ImmersivePlayerState(title = title, artist = artist, repeatMode = repeat)

    @Test fun repeatModesFollowMedia3() {
        assertEquals(Player.REPEAT_MODE_OFF, state(ImmersiveRepeat.OFF).toPlaybackState("u", null).repeatMode)
        assertEquals(Player.REPEAT_MODE_ALL, state(ImmersiveRepeat.ALL).toPlaybackState("u", null).repeatMode)
        assertEquals(Player.REPEAT_MODE_ONE, state(ImmersiveRepeat.ONE).toPlaybackState("u", null).repeatMode)
    }

    @Test fun repeatOnceShowsAsRepeatOne() =
        assertEquals(Player.REPEAT_MODE_ONE, state(ImmersiveRepeat.ONCE).toPlaybackState("u", null).repeatMode)

    @Test fun artistLineIsSplitAndTrimmed() {
        val artists = state(artist = " Ada ,Bo,, Cy ").toPlaybackState("u", null).artists
        assertEquals(listOf("Ada", "Bo", "Cy"), artists.map { it.name })
        assertTrue(artists.all { it.uri.isEmpty() })
    }

    @Test fun noArtistsWhenTheLineIsBlank() =
        assertTrue(state(artist = "").toPlaybackState("u", null).artists.isEmpty())

    @Test fun hasItemFromTitleOrUri() {
        assertTrue(state(title = "x").toPlaybackState("", null).hasItem)
        assertTrue(state(title = "").toPlaybackState("spotify:track:1", null).hasItem)
        assertFalse(state(title = "  ").toPlaybackState("", null).hasItem)
    }

    @Test fun blankUriAndContextBecomeNull() {
        val s = state().toPlaybackState("", "  ")
        assertNull(s.mediaId)
        assertNull(s.contextUri)
        assertEquals("spotify:album:1", state().toPlaybackState("u", "spotify:album:1").contextUri)
    }

    @Test fun wantsPlayFollowsPlayingOrBuffering() {
        assertTrue(ImmersivePlayerState(buffering = true).toPlaybackState("u", null).wantsPlay)
        assertTrue(ImmersivePlayerState(playing = true).toPlaybackState("u", null).wantsPlay)
        assertFalse(ImmersivePlayerState().toPlaybackState("u", null).wantsPlay)
    }

    private val items = listOf("a", "b", "c", "d")

    @Test fun queueRowNIsItemNMinusOne() {
        assertEquals("a", queueItemAt(items, 1))
        assertEquals("d", queueItemAt(items, 4))
    }

    @Test fun theCurrentRowAndOutOfRangeRowsAreNoItem() {
        assertNull(queueItemAt(items, 0))
        assertNull(queueItemAt(items, 5))
        assertNull(queueItemAt(items, -1))
    }

    @Test fun draggedDownGoesAfterTheTarget() {
        val move = planQueueMove(items, from = 1, to = 3)!!
        assertEquals("a", move.moved)
        assertEquals("c", move.target)
        assertTrue(move.after)
    }

    @Test fun draggedUpGoesBeforeTheTarget() {
        val move = planQueueMove(items, from = 4, to = 2)!!
        assertEquals("d", move.moved)
        assertEquals("b", move.target)
        assertFalse(move.after)
    }

    @Test fun moveOutsideTheListIsNoMove() {
        assertNull(planQueueMove(items, from = 0, to = 2))
        assertNull(planQueueMove(items, from = 2, to = 9))
    }

    @Test fun pollingIsSlowWhileCollapsed() {
        assertEquals(250L, positionPollMs(sheetOpen = false, playing = true))
        assertEquals(250L, positionPollMs(sheetOpen = false, playing = false))
        assertEquals(50L, positionPollMs(sheetOpen = true, playing = true))
        assertEquals(400L, positionPollMs(sheetOpen = true, playing = false))
    }

    @Test fun motionFollowsTheDataRules() {
        assertTrue(motionAllowed(true, true, false, false, true))
        assertFalse(motionAllowed(false, true, false, false, false))
        assertFalse(motionAllowed(true, false, false, false, false))
        assertFalse(motionAllowed(true, true, true, true, false))
        assertFalse(motionAllowed(true, true, false, true, true))
        assertTrue(motionAllowed(true, true, false, true, false))
    }
}
