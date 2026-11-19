// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/network/data/providers/LrcMuxLyricsProvider.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
// That file is itself an MIT port of Lyrica's lrcmux adapter (credit kept here; Lyrica's own copyright notice is not in the upstream file).
package com.music.spotui.data.api

import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.data.entity.Lyrics
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * LRCMux (api.lrcmux.dev): lyrics from several catalogues behind one lookup by title, artist and
 * length, word-timed when it can. Asked at word level first; the answer says where it found them.
 *
 * The network lives in [LyricsApi]; this is the URL and the parsing, without Android.
 */
internal object LrcMuxLyrics {
    const val NAME = "LRCMux"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun url(title: String, artist: String, durationSec: Int) =
        "https://api.lrcmux.dev/get?artist=${enc(artist)}&title=${enc(title)}&duration=$durationSec&format=json&level=word"

    /**
     * LRCMux matches on title and length alone, so a remix as long as the song can answer for it
     * ("Dynamite (NightTime Version)" runs six seconds behind the original). True when its track
     * names a version (in brackets or after a dash) that [ours] ("title album") does not.
     */
    fun otherVersion(root: JSONObject, ours: String): Boolean {
        val track = root.optJSONObject("track") ?: return false
        return LyricsVersions.otherVersion(listOf(track.optString("title"), track.optString("album")).joinToString(" "), ours)
    }

    /** The track an answer says it is for: title, artists and length in ms (0: unknown). */
    class AnswerTrack(val title: String, val artist: String, val durationMs: Long)

    /**
     * The answer's `track` (its real fields: `title`, `artist`, `album`, `duration` in seconds, `isrc`,
     * `cover`), or null without one. A field it does not have is blank, or 0 for the length.
     */
    fun answerTrack(root: JSONObject): AnswerTrack? {
        val t = root.optJSONObject("track") ?: return null
        return AnswerTrack(t.optString("title"), t.optString("artist"), (t.optDouble("duration", 0.0) * 1000).toLong())
    }

    /**
     * Whether the answer's [track] is the song asked for ([same]: Astral's own comparison of title,
     * artist and length). Each of the three is checked on its own: one the answer does not give is not
     * checked, one it gives and that differs refuses the answer.
     */
    fun sameTrack(
        track: AnswerTrack, title: String, artist: String, durationMs: Long,
        same: (candTitle: String, candArtist: String, candDurationMs: Long, title: String, artist: String, durationMs: Long) -> Boolean,
    ): Boolean = same(
        track.title.ifBlank { title }, track.artist.ifBlank { artist }, track.durationMs, title, artist, durationMs,
    )

    /** The lyrics in an answer: word-timed when its lines carry words, else line-synced, else plain. Null when empty. */
    fun parse(root: JSONObject): Lyrics? {
        val lines = root.optJSONArray("lines")?.takeIf { it.length() > 0 } ?: return null
        val origin = root.optJSONObject("meta")?.optJSONObject("source")?.optString("name")?.takeIf { it.isNotBlank() }
        val provider = if (origin != null) "$NAME · $origin" else NAME
        // Milliseconds or seconds, decided once for the whole answer: judged value by value, a line
        // in the first ten seconds read as seconds (6403 ms became 6403 s, an hour and three quarters in).
        val times = (0 until lines.length()).flatMap { i ->
            lines.optJSONObject(i)?.let { o -> listOfNotNull(o.number("start"), o.number("end")) }.orEmpty()
        }
        val toMs = if ((times.maxOrNull() ?: 0.0) > 1_000) 1.0 else 1_000.0
        val out = ArrayList<LyricLine>()
        var timed = false
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val text = line.optString("text")
            val words = wordsOf(line.optJSONArray("words"), text, toMs)
            val startMs = line.number("start")?.let { (it * toMs).toLong() } ?: words?.firstOrNull()?.startMs
            if (words != null) {
                out += LyricLine(startMs ?: 0, words.joinToString("") { it.text }.trim(), words)
                timed = true
            } else if (text.isNotBlank()) {
                out += LyricLine(startMs ?: 0, text.trim())
                if (startMs != null) timed = true
            }
        }
        if (out.isEmpty()) return null
        return if (timed) Lyrics(lines = out, synced = true, provider = provider)
        else Lyrics(lines = out.map { it.copy(timeMs = 0) }, synced = false, provider = provider)
    }

    private fun wordsOf(array: JSONArray?, lineText: String, toMs: Double): List<LyricWord>? {
        if (array == null) return null
        val raw = (0 until array.length()).mapNotNull { i ->
            val w = array.optJSONObject(i) ?: return@mapNotNull null
            val text = w.optString("text").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val start = w.number("start") ?: return@mapNotNull null
            val end = w.number("end") ?: return@mapNotNull null
            Triple(text, (start * toMs).toLong(), (end * toMs).toLong())
        }
        if (raw.isEmpty()) return null
        // The words carry their spaces in Astral's lines ("I'm ", "ready"): add them when the line's own
        // text shows they are missing, never between words that run together (CJK).
        val glued = raw.joinToString("") { it.first }
        val spaced = raw.joinToString(" ") { it.first }
        val gap = lineText.isNotBlank() && glued != lineText.trim() && spaced == lineText.trim()
        return raw.mapIndexed { i, (text, s, e) -> LyricWord(s, e, if (gap && i < raw.lastIndex) "$text " else text) }
    }

    private fun JSONObject.number(key: String): Double? =
        if (has(key) && get(key) is Number) getDouble(key) else null
}
