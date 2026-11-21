package com.music.spotui.connect.nearby

internal data class JamInvitation(val token: String, val hostName: String?)

internal object NearbyJamPayload {
    private const val INLINE: Byte = 1
    private const val REFERENCE: Byte = 2
    private const val ID_BYTES = 8
    private const val TOKEN_BYTES = 128
    // Legacy scan response: 31 bytes minus AD length, type and 128-bit service UUID.
    internal const val SCAN_RESPONSE_BYTES = 13
    internal const val MAX_NAME_BYTES = 96
    internal const val MAX_READ_BYTES = 1 + ID_BYTES + 3 + TOKEN_BYTES + MAX_NAME_BYTES

    private val socialSession = Regex("""socialsession[:/]([A-Za-z0-9_-]+)(?=$|[/?#&\s])""")
    private val tokenPattern = Regex("[A-Za-z0-9_-]{1,$TOKEN_BYTES}")

    fun token(linkOrToken: String): String? {
        val input = linkOrToken.trim()
        val value = socialSession.find(input)?.groupValues?.get(1) ?: input
        return value.takeIf(tokenPattern::matches)
    }

    sealed interface Advertisement {
        data class Inline(val invitation: JamInvitation) : Advertisement
        data class Reference(val id: Long) : Advertisement
    }

    fun encodeInline(invitation: JamInvitation): ByteArray? =
        encode(invitation)?.takeIf { it.size <= SCAN_RESPONSE_BYTES }

    fun encodeReference(id: Long): ByteArray =
        java.nio.ByteBuffer.allocate(1 + ID_BYTES).put(REFERENCE).putLong(id).array()

    fun encodeRead(id: Long, invitation: JamInvitation): ByteArray? =
        encode(invitation)?.let { encodeReference(id) + it }

    fun decodeAdvertisement(bytes: ByteArray): Advertisement? {
        if (bytes.isEmpty() || bytes.size > SCAN_RESPONSE_BYTES) return null
        return when (bytes[0]) {
            INLINE -> decode(bytes)?.let { Advertisement.Inline(it) }
            REFERENCE -> referenceId(bytes)?.let { Advertisement.Reference(it) }
            else -> null
        }
    }

    fun decodeRead(id: Long, bytes: ByteArray): JamInvitation? {
        if (bytes.size !in (ID_BYTES + 5)..MAX_READ_BYTES) return null
        if (referenceId(bytes.copyOfRange(0, 1 + ID_BYTES)) != id) return null
        return decode(bytes.copyOfRange(1 + ID_BYTES, bytes.size))
    }

    private fun referenceId(bytes: ByteArray): Long? =
        if (bytes.size == 1 + ID_BYTES && bytes[0] == REFERENCE) {
            java.nio.ByteBuffer.wrap(bytes, 1, ID_BYTES).getLong()
        } else {
            null
        }

    private fun encode(invitation: JamInvitation): ByteArray? {
        val token = invitation.token.takeIf(tokenPattern::matches)?.encodeToByteArray() ?: return null
        val name = invitation.hostName.orEmpty().let {
            it.substring(0, it.offsetByCodePoints(0, minOf(24, it.codePointCount(0, it.length))))
        }.encodeToByteArray()
        return byteArrayOf(INLINE, token.size.toByte(), name.size.toByte()) + token + name
    }

    private fun decode(bytes: ByteArray): JamInvitation? {
        if (bytes.size < 4 || bytes[0] != INLINE) return null
        val tokenLength = bytes[1].toInt() and 0xff
        val nameLength = bytes[2].toInt() and 0xff
        val nameStart = 3 + tokenLength
        if (tokenLength !in 1..TOKEN_BYTES || nameLength > MAX_NAME_BYTES || bytes.size != nameStart + nameLength) return null
        val token = bytes.decodeToString(3, nameStart).takeIf(tokenPattern::matches) ?: return null
        val name = try {
            bytes.decodeToString(nameStart, bytes.size, throwOnInvalidSequence = true)
                .filterNot { it.isISOControl() }.trim().takeIf(String::isNotEmpty)
        } catch (_: java.nio.charset.CharacterCodingException) {
            return null
        }
        return JamInvitation(token, name)
    }
}
