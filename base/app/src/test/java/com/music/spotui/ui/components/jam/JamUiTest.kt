package com.music.spotui.ui.components.jam

import app.myspoti.api.JamAvailability
import app.myspoti.api.JamMember
import app.myspoti.api.JamSession
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamUiTest {

    @Test
    fun extractTokenFromWebInviteUrl() {
        val token = JamLinks.extractToken("https://open.spotify.com/socialsession/abc123XYZ_99-test?si=deadbeef")
        assertEquals("abc123XYZ_99-test", token)
    }

    @Test
    fun extractTokenFromSocialSessionUri() {
        val token = JamLinks.extractToken("spotify:socialsession:abc123XYZ")
        assertEquals("abc123XYZ", token)
    }

    @Test
    fun extractTokenReturnsNullForNonJamUrls() {
        assertNull(JamLinks.extractToken("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertNull(JamLinks.extractToken("https://example.com/not-a-jam"))
        assertNull(JamLinks.extractToken(""))
        assertNull(JamLinks.extractToken("   "))
    }

    @Test
    fun qrCodeEncodesAndDecodesJamInviteUrl() {
        val url = "https://open.spotify.com/socialsession/abc123XYZ"
        val matrix = JamQr.encodeModules(url)
        assertTrue(matrix.width > 0)
        assertTrue(matrix.height > 0)
        assertEquals(matrix.width, matrix.height)

        val scale = 6
        val quiet = 3
        val side = (matrix.width + 2 * quiet) * scale
        val pixels = IntArray(side * side) { 0xFFFFFF }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0x000000
                        }
                    }
                }
            }
        }
        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels))),
        ).text
        assertEquals(url, decoded)
    }

    @Test
    fun sortJamMembersPlacesHostFirstAndPreservesGuestOrder() {
        val guest1 = JamMember(userUri = "spotify:user:guest1", name = "Alice", imageUrl = null, host = false)
        val guest2 = JamMember(userUri = "spotify:user:guest2", name = "Bob", imageUrl = null, host = false)
        val host = JamMember(userUri = "spotify:user:host1", name = "Charlie", imageUrl = null, host = true)
        val guest3 = JamMember(userUri = "spotify:user:guest3", name = "Dana", imageUrl = null, host = false)

        val sorted = sortJamMembers(listOf(guest1, guest2, host, guest3))
        assertEquals(listOf(host, guest1, guest2, guest3), sorted)
    }

    @Test
    fun availabilityAndPermissionHelpers() {
        assertTrue(isRestricted(JamAvailability.RESTRICTED))
        assertFalse(isRestricted(JamAvailability.AVAILABLE))
        assertFalse(isRestricted(JamAvailability.UNKNOWN))
        assertFalse(isRestricted(null))

        assertFalse(canStartJam(JamAvailability.RESTRICTED))
        assertTrue(canStartJam(JamAvailability.AVAILABLE))
        assertTrue(canStartJam(JamAvailability.UNKNOWN))
        assertTrue(canStartJam(null))
    }

    @Test
    fun memberDisplayNameAndInviteLinkFallbacks() {
        val named = JamMember("spotify:user:u1", "Astral Listener", null, host = false)
        val unnamed = JamMember("spotify:user:fallback_id", "   ", null, host = false)
        assertEquals("Astral Listener", memberDisplayName(named))
        assertEquals("fallback_id", memberDisplayName(unnamed))

        val sessionWithToken = JamSession(
            id = "s1",
            joinToken = "tok123",
            inviteLink = null,
            members = emptyList(),
            isHost = true,
            queueOnly = false,
        )
        assertEquals(
            "https://open.spotify.com/socialsession/tok123",
            resolveInviteLink(null, sessionWithToken),
        )
        assertEquals(
            "https://open.spotify.com/socialsession/custom",
            resolveInviteLink("https://open.spotify.com/socialsession/custom", sessionWithToken),
        )
    }
}
