package app.myspoti.host.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarActionsTest {
    private fun info(id: String = "spotify:track:x", episode: Boolean = false, liked: Boolean = false,
                     shuffle: Boolean = false, repeat: Int = 0) =
        NowPlayingInfo(id, "t", "a", "al", null, 200_000L, 0L, true,
            liked = liked, shuffle = shuffle, episode = episode, repeat = repeat)

    private fun ids(i: NowPlayingInfo?) = CarActions.forState(i).map { it.id }

    @Test fun musicGetsTheFullSetLikeFirst() {
        assertEquals(listOf("like", "shuffle", "repeat", "radio", "add_to_playlist"), ids(info()))
    }

    @Test fun episodesGetLikeAndSpeedOnly() {
        assertEquals(listOf("like", "speed"), ids(info(episode = true)))
    }

    @Test fun noButtonsDuringAnAdOrBeforeAnythingPlays() {
        assertTrue(ids(info(id = NowPlayingInfo.AD_ID)).isEmpty())
        assertTrue(ids(null).isEmpty())
    }

    @Test fun likeAndShuffleFollowTheState() {
        val off = CarActions.forState(info()).associateBy { it.id }
        val on = CarActions.forState(info(liked = true, shuffle = true)).associateBy { it.id }
        assertFalse(off.getValue("like").icon == on.getValue("like").icon)
        assertFalse(off.getValue("like").label == on.getValue("like").label)
        assertFalse(off.getValue("shuffle").icon == on.getValue("shuffle").icon)
    }

    @Test fun repeatHasAnIconPerMode() {
        val icons = listOf(0, 1, 2).map { m -> CarActions.forState(info(repeat = m)).first { it.id == "repeat" }.icon }
        assertEquals(3, icons.toSet().size)
    }

    @Test fun repeatCyclesOffAllOneOff() {
        assertEquals(CarActions.REPEAT_ALL, CarActions.nextRepeat(CarActions.REPEAT_OFF))
        assertEquals(CarActions.REPEAT_ONE, CarActions.nextRepeat(CarActions.REPEAT_ALL))
        assertEquals(CarActions.REPEAT_OFF, CarActions.nextRepeat(CarActions.REPEAT_ONE))
    }

    @Test fun speedCyclesAndWraps() {
        assertEquals(1.25, CarActions.nextSpeed(1.0), 0.0)
        assertEquals(1.0, CarActions.nextSpeed(2.0), 0.0)
        assertEquals(1.5, CarActions.nextSpeed(1.4), 0.0)
    }

    @Test fun repeatSpeedAndRowAreNewStatesButPositionIsNot() {
        assertFalse(info(repeat = 0).sameAs(info(repeat = 1)))
        assertFalse(info().sameAs(info().copy(speed = 1.5f)))
        assertFalse(info().sameAs(info().copy(browseId = "t|c|x")))
        assertTrue(info().sameAs(info().copy(positionMs = 5_000L)))
    }

    @Test fun explicitAndContextAreMetadata() {
        assertFalse(info().sameMetadata(info().copy(explicit = true)))
        assertFalse(info().sameMetadata(info().copy(contextName = "Road trip")))
    }
}
