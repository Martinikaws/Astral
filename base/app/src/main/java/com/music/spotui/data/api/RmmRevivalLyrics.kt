// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/network/data/providers/RmmRevivalLyricsProvider.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.data.api

import com.music.spotui.data.entity.Lyrics
import org.json.JSONObject
import java.net.URLEncoder

/**
 * RMM Revival (lyrics.rmmreviv.al): Apple Music's lyrics, relayed by Apple Music id. The id comes
 * from Apple's iTunes search (itunes.apple.com), asked for the song's title and artist, so both
 * services see them. Off until the user turns it on.
 *
 * The network lives in [LyricsApi]; this is the URLs and the parsing, without Android.
 */
internal object RmmRevivalLyrics {
    const val NAME = "RMM Revival"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun searchUrl(title: String, artist: String) = "https://itunes.apple.com/search?term=${enc("$artist $title")}&entity=song&limit=8"
    fun lyricsUrl(appleId: String) = "https://lyrics.rmmreviv.al/lyrics?id=${enc(appleId)}"

    /**
     * The Apple Music id of the iTunes result that is [title] by [artist], the nearest in length
     * ([durationMs] 0: unknown); results more than 4 s off a known length are other versions.
     */
    fun appleId(body: String, title: String, artist: String, durationMs: Long): String? {
        val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull() ?: return null
        return (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { LyricsMatch.key(it.optString("trackName")) == LyricsMatch.key(title) && sameArtist(it.optString("artistName"), artist) }
            .filter { durationMs <= 0 || it.optLong("trackTimeMillis") <= 0 || kotlin.math.abs(it.optLong("trackTimeMillis") - durationMs) <= 4_000 }
            .minByOrNull { kotlin.math.abs(it.optLong("trackTimeMillis") - durationMs) }
            ?.let { it.opt("trackId")?.toString() }?.takeIf { it.isNotBlank() }
    }

    private fun sameArtist(candidate: String, artist: String): Boolean {
        val got = LyricsMatch.key(candidate)
        val wanted = LyricsMatch.key(artist)
        return got.isNotEmpty() && wanted.isNotEmpty() && (got == wanted || got in wanted || wanted in got)
    }

    /** The lyrics of the relay's answer: its first of `ttml`, `content`, `syncedLyrics`, `lyrics` that has text. */
    fun parse(body: String): Lyrics? {
        val data = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val text = listOf("ttml", "content", "syncedLyrics", "lyrics").firstNotNullOfOrNull { key ->
            (data.opt(key) as? String)?.takeIf { it.isNotBlank() }
        } ?: return null
        return UnisonLyrics.textToLyrics(text, "", NAME)
    }
}
