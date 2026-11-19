// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/network/data/providers/UnisonLyricsProvider.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.data.api

import com.music.spotui.data.entity.Lyrics
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Unison (unison.boidu.dev): community-synced lyrics, asked by the song's title and artist only.
 * Its search finds the song, a second request reads its lyrics by id; its title lookup is the
 * fallback when the search finds nothing. Spicy Lyrics' own copy of Unison (ISRC / YouTube id,
 * through spicylyrics.org) is not used: nothing but title and artist leaves the phone.
 *
 * The network lives in [LyricsApi]; this is the URLs and the parsing, without Android.
 */
internal object UnisonLyrics {
    const val BASE = "https://unison.boidu.dev"
    const val NAME = "Unison"

    private val lrcTime = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun searchUrl(title: String, artist: String) = "$BASE/lyrics/search?q=${enc("$artist $title")}"
    fun detailUrl(id: String) = "$BASE/lyrics/${enc(id)}"
    fun directUrl(title: String, artist: String) = "$BASE/lyrics?song=${enc(title)}&artist=${enc(artist)}"

    /** The first search result that is [title] by [artist] (its `data` array), or null. */
    fun searchHit(body: String, title: String, artist: String): JSONObject? {
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return null
        return (0 until data.length()).mapNotNull { data.optJSONObject(it) }.firstOrNull { matches(it, title, artist) }
    }

    /** The lookup by title's answer: its `data` object when it is [title] by [artist], else null. */
    fun directData(body: String, title: String, artist: String): JSONObject? =
        runCatching { JSONObject(body).optJSONObject("data") }.getOrNull()?.takeIf { matches(it, title, artist) }

    /** The detail answer's `data` object. */
    fun detailData(body: String): JSONObject? = runCatching { JSONObject(body).optJSONObject("data") }.getOrNull()

    /** The same title and artist (either artist may hold the other: "A" and "A, B"). */
    fun matches(entry: JSONObject, title: String, artist: String): Boolean {
        val theirTitle = entry.optString("song").ifBlank { entry.optString("title") }
        val theirArtist = entry.optString("artist")
        if (theirTitle.isBlank() || theirArtist.isBlank()) return false
        if (LyricsMatch.key(theirTitle) != LyricsMatch.key(title)) return false
        val candidate = LyricsMatch.key(theirArtist)
        val wanted = LyricsMatch.key(artist)
        return candidate.isNotEmpty() && wanted.isNotEmpty() && (candidate == wanted || wanted in candidate || candidate in wanted)
    }

    /** The lyrics of a `data` object: TTML, LRC or plain text by its `format`; YAML is not read. */
    fun lyricsOf(data: JSONObject): Lyrics? {
        val text = data.optString("lyrics").takeIf { it.isNotBlank() } ?: return null
        return textToLyrics(text, data.optString("format").lowercase(), NAME)
    }

    /**
     * [text] as lyrics, by [format] when it says (`ttml`, `lrc`, `plain`/`txt`) or by what it looks
     * like: TTML markup, LRC time tags, else plain lines. Null for another format (YAML) or no lines.
     */
    fun textToLyrics(text: String, format: String, provider: String): Lyrics? {
        val lyrics = when {
            format == "ttml" || text.contains("<tt", ignoreCase = true) -> LyricsApi.parseTtml(text)?.copy(provider = provider)
            format == "lrc" || lrcTime.containsMatchIn(text) -> LyricsApi.lrcLines(text).takeIf { it.isNotEmpty() }
                ?.let { Lyrics(lines = it, synced = true, provider = provider) }
            format == "plain" || format == "txt" || format.isEmpty() -> plain(text, provider)
            else -> null
        }
        return lyrics?.takeIf { !it.isEmpty }
    }

    private fun plain(text: String, provider: String): Lyrics? =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
            ?.let { lines -> Lyrics(lines = lines.map { com.music.spotui.data.entity.LyricLine(0, it) }, synced = false, provider = provider) }
}
