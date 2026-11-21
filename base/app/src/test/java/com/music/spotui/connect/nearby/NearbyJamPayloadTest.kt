package com.music.spotui.connect.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearbyJamPayloadTest {
    @Test
    fun parsesSocialSessionUrlsUrisAndBareTokens() {
        assertEquals("AbC123", NearbyJamPayload.token("https://open.spotify.com/socialsession/AbC123?si=other"))
        assertEquals("AbC123", NearbyJamPayload.token("spotify:socialsession:AbC123"))
        assertEquals("AbC-_123", NearbyJamPayload.token("  AbC-_123  "))
    }

    @Test
    fun rejectsEmptyMalformedAndOpaqueLinks() {
        assertNull(NearbyJamPayload.token("https://spotify.link/invite"))
        assertNull(NearbyJamPayload.token("spotify:socialsession:"))
        assertNull(NearbyJamPayload.token("spotify:socialsession:ABC%20"))
        assertNull(NearbyJamPayload.token("x".repeat(129)))
    }

    @Test
    fun inlineRoundTripFitsLegacyScanResponse() {
        val invitation = JamInvitation("ABC1234", "Ada")
        val bytes = requireNotNull(NearbyJamPayload.encodeInline(invitation))
        assertEquals(NearbyJamPayload.SCAN_RESPONSE_BYTES, bytes.size)
        assertEquals(NearbyJamPayload.Advertisement.Inline(invitation), NearbyJamPayload.decodeAdvertisement(bytes))
        assertNull(NearbyJamPayload.encodeInline(invitation.copy(hostName = "Adal")))
    }

    @Test
    fun referenceAndGattReadRoundTripWithMatchingId() {
        val id = -2345L
        val invitation = JamInvitation("x".repeat(128), "🎵".repeat(24))
        val reference = NearbyJamPayload.encodeReference(id)
        val read = requireNotNull(NearbyJamPayload.encodeRead(id, invitation))
        assertEquals(NearbyJamPayload.Advertisement.Reference(id), NearbyJamPayload.decodeAdvertisement(reference))
        assertEquals(NearbyJamPayload.MAX_READ_BYTES, read.size)
        assertEquals(invitation, NearbyJamPayload.decodeRead(id, read))
        assertNull(NearbyJamPayload.decodeRead(id + 1, read))
        assertNull(NearbyJamPayload.decodeRead(id, read.copyOf(read.size - 1)))
        assertNull(NearbyJamPayload.decodeAdvertisement(read))
    }

    @Test
    fun rejectsCorruptPayloads() {
        val invitation = JamInvitation("ABC123", "Ada")
        val inline = requireNotNull(NearbyJamPayload.encodeInline(invitation))
        val badName = inline.copyOf().apply { this[lastIndex] = 0xff.toByte() }
        assertNull(NearbyJamPayload.decodeAdvertisement(byteArrayOf()))
        assertNull(NearbyJamPayload.decodeAdvertisement(byteArrayOf(99, 1, 0, 65)))
        assertNull(NearbyJamPayload.decodeAdvertisement(inline.copyOf(inline.size - 1)))
        assertNull(NearbyJamPayload.decodeAdvertisement(badName))
        assertNull(NearbyJamPayload.decodeAdvertisement(NearbyJamPayload.encodeReference(42).copyOf(7)))
        assertNull(NearbyJamPayload.encodeInline(JamInvitation("invalid token", null)))
    }

    @Test
    fun clipsLongDisplayNamesAtUnicodeCodePointBoundaries() {
        val invitation = JamInvitation("abc", "🎵".repeat(30))
        val record = requireNotNull(NearbyJamPayload.encodeRead(0, invitation))
        assertEquals(invitation.copy(hostName = "🎵".repeat(24)), NearbyJamPayload.decodeRead(0, record))
    }
}
