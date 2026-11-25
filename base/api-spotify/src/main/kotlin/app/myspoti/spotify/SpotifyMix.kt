// Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, native/src/catalog.rs (playlist_mix, mix_state_in, extension) at 860b326. Modified for Astral, 2026-11.
// Changes: Rust to Kotlin over SpotifyHttp.sendProto; only the "is it mixed" answer, no per-row transitions; every failure answers false.
package app.myspoti.spotify

import app.myspoti.api.MixStates
import app.myspoti.spotify.gql.SpotifyHttp
import java.util.concurrent.ConcurrentHashMap

/**
 * A playlist mixed in Spotify's app: its `MixState` extension (225) reads `{1: 1}`. Read through
 * extended-metadata; when that call fails the `mix` lens of the playlist is asked for the
 * transition recipes saved on its rows instead. Answers are kept for the session.
 */
class SpotifyMix(private val http: SpotifyHttp) : MixStates {
    private val known = ConcurrentHashMap<String, Boolean>()

    override suspend fun isMixed(playlistUri: String): Boolean {
        val id = playlistUri.removePrefix("spotify:playlist:").takeIf { playlistUri.startsWith("spotify:playlist:") && it.isNotBlank() }
            ?: return false
        known[id]?.let { return it }
        val answer = runCatching { viaExtension(playlistUri) }.getOrNull()
            ?: runCatching { viaLens(id) }.getOrNull()
        // A failed ask is not kept: it is asked again next time.
        return answer?.also { known[id] = it } ?: false
    }

    private suspend fun viaExtension(uri: String): Boolean? {
        val (code, bytes) = http.sendProto("POST", "https://spclient.wg.spotify.com/extended-metadata/v0/extended-metadata", mixStateRequest(uri))
        return if (code == 200) mixStateIn(bytes) == 1L else null
    }

    private suspend fun viaLens(id: String): Boolean? {
        val (code, bytes) = http.sendProto("GET", "https://spclient.wg.spotify.com/playlist/v2/playlist/$id", null, mapOf("spotify-apply-lenses" to "mix,auto"))
        return if (code == 200) hasRecipe(bytes) else null
    }

    internal companion object {
        const val MIX_STATE = 225L
        private val RECIPE = "automix.auto_transition_recipe".toByteArray()

        fun mixStateRequest(uri: String): ByteArray = proto {
            message(2) {
                string(1, uri)
                message(2) { varint(1, MIX_STATE) }
            }
        }

        /** The state in a MixState reply: the `Any`'s value, its field 1. Null when absent or malformed. */
        fun mixStateIn(bytes: ByteArray): Long? = runCatching {
            parseProto(bytes).messages(2).forEach { array ->
                array.messages(3).forEach { data ->
                    val value = data.message(3)?.bytes(2) ?: return@forEach
                    return parseProto(value).long(1)
                }
            }
            null
        }.getOrNull()

        fun hasRecipe(bytes: ByteArray): Boolean = (0..bytes.size - RECIPE.size).any { i -> RECIPE.indices.all { bytes[i + it] == RECIPE[it] } }
    }
}
