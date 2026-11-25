package app.myspoti.spotify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotifyMixTest {
    private fun reply(value: ByteArray) = proto {
        message(2) { message(3) { message(3) { string(1, "type.googleapis.com/MixState"); bytes(2, value) } } }
    }

    @Test fun mixedPlaylistReadsOne() = assertEquals(1L, SpotifyMix.mixStateIn(reply(proto { varint(1, 1) })))

    @Test fun otherPlaylistHasEmptyValue() = assertNull(SpotifyMix.mixStateIn(reply(ByteArray(0))))

    @Test fun garbageIsNotMixed() {
        assertNull(SpotifyMix.mixStateIn(byteArrayOf(0x12, 0x7f, 1)))
        assertNull(SpotifyMix.mixStateIn(ByteArray(0)))
    }

    @Test fun requestCarriesUriAndExtension() {
        val entity = parseProto(SpotifyMix.mixStateRequest("spotify:playlist:abc")).message(2)!!
        assertEquals("spotify:playlist:abc", entity.string(1))
        assertEquals(225L, entity.message(2)!!.long(1))
    }

    @Test fun lensRecipeIsFoundInRawBytes() {
        assertTrue(SpotifyMix.hasRecipe("xx automix.auto_transition_recipe yy".toByteArray()))
        assertFalse(SpotifyMix.hasRecipe("plain".toByteArray()))
    }
}
