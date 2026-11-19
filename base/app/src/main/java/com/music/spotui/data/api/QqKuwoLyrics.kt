// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, core/src/main/kotlin/com/tx24/spicyplayer/network/data/providers/QqAndKuwoProviders.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.data.api

import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.data.entity.Lyrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.InflaterInputStream

/** A song in QQ Music's or Kuwo's search: [id] is QQ's `mid` or Kuwo's `MUSIC_` id. */
internal data class CatalogueSong(val title: String, val artist: String, val durationSec: Int, val id: String)

/**
 * QQ Music (u.y.qq.com, c.y.qq.com), line-synced LRC. Off until the user turns it on. Its search
 * answers with a throttle code now and then; the search box's own suggestions answer then.
 *
 * Not ported: the word-timed QRC lyrics (an encrypted format whose decryption code upstream
 * took from elsewhere). The network lives in [LyricsApi]; this is the requests' bodies and the parsing.
 */
internal object QqLyrics {
    const val NAME = "QQ Music"
    const val ENDPOINT = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    const val REFERER = "https://y.qq.com/"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun searchBody(query: String): String = JSONObject().put("req_1", JSONObject()
        .put("module", "music.search.SearchCgiService").put("method", "DoSearchForQQMusicDesktop")
        .put("param", JSONObject().put("num_per_page", 8).put("page_num", 1).put("query", query).put("search_type", 0))).toString()

    fun suggestUrl(query: String) = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=${enc(query)}&format=json"

    fun trackInfoBody(mids: List<String>): String = JSONObject()
        .put("comm", JSONObject().put("ct", "19").put("cv", "1859").put("uin", "0"))
        .put("req_1", JSONObject().put("module", "music.trackInfo.UniformRuleCtrl").put("method", "CgiGetTrackInfo")
            .put("param", JSONObject().put("types", JSONArray(mids.map { 0 })).put("mids", JSONArray(mids)))).toString()

    fun lyricUrl(mid: String) =
        "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${enc(mid)}&format=json&nobase64=1"

    /**
     * The songs of a search answer, or null when QQ throttled it (a non-zero `code`, an empty list):
     * ask [suggestUrl] then. An answer without a list is "no songs" (empty).
     */
    fun searchSongs(body: String): List<CatalogueSong>? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val code = root.optInt("code", 0).takeIf { it != 0 } ?: root.optJSONObject("req_1")?.optInt("code", 0)?.takeIf { it != 0 }
        if (code != null) return null
        val list = root.optJSONObject("req_1")?.optJSONObject("data")?.optJSONObject("body")?.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()
        return songs(list)
    }

    /** The `mid`s of the suggestion search's songs. */
    fun suggestedMids(body: String): List<String> {
        val items = runCatching { JSONObject(body).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("itemlist") }.getOrNull()
            ?: return emptyList()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optString("mid")?.takeIf { m -> m.isNotBlank() } }
    }

    /** The songs of a track-details answer (their lengths too), or null when it failed. */
    fun trackInfoSongs(body: String): List<CatalogueSong>? {
        val req = runCatching { JSONObject(body).optJSONObject("req_1") }.getOrNull() ?: return null
        if (req.optInt("code", -1) != 0) return null
        return songs(req.optJSONObject("data")?.optJSONArray("tracks") ?: return null)
    }

    private fun songs(list: JSONArray): List<CatalogueSong> = (0 until list.length()).mapNotNull { i ->
        val s = list.optJSONObject(i) ?: return@mapNotNull null
        val singers = s.optJSONArray("singer")
        CatalogueSong(
            title = s.optString("name"),
            artist = singers?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") }.joinToString(", ") }.orEmpty(),
            durationSec = s.optInt("interval", 0),
            id = s.optString("mid"),
        )
    }

    /** The song's lyrics from the lyric answer (plain text or base64 of an LRC), or null. */
    fun lyricsOf(body: String): Lyrics? {
        var text = runCatching { JSONObject(body).optString("lyric") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        if (!text.trim().startsWith("[")) text = runCatching { String(Base64.getDecoder().decode(text.trim()), Charsets.UTF_8) }.getOrDefault(text)
        val lines = LyricsApi.lrcLines(text).filterNot { KuwoLyrics.isCredit(it.text) }
        return lines.takeIf { it.size >= 3 }?.let { Lyrics(lines = it, synced = true, provider = NAME) }
    }
}

/**
 * Kuwo (search.kuwo.cn, newlyric.kuwo.cn): word-timed lyrics where it has them, else line-synced LRC.
 * Off until the user turns it on. Its search answers in single-quoted JavaScript-style JSON, with
 * `&nbsp;` for spaces in names.
 */
internal object KuwoLyrics {
    const val NAME = "Kuwo"
    const val REFERER = "https://www.kuwo.cn/"
    const val TRIES = 3

    /** Kuwo's own version markers (backing track, excerpt, live, cover, ringtone, DJ edit, preview). */
    val CUT = Regex("伴奏|片段|现场|翻唱|铃声|DJ版|试听")
    private val KEY = "yeelion".encodeToByteArray()
    private val TOKEN = Regex("<(-?\\d+),(-?\\d+)>([^<]*)")
    private val LINE = Regex("^\\[(\\d+):(\\d+)(?:\\.(\\d+))?]")
    /** "Translation by …" notes that open some machine-translated lyrics. */
    private val NOTE = Regex("歌词翻译由.{0,24}提供")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun searchUrl(query: String) = "https://search.kuwo.cn/r.s?all=${enc(query)}&ft=music&client=kt&cluster=0&pn=0&rn=8&rformat=json&encoding=utf8" +
        // Without these the search leaves out licensed originals and returns only covers and remixes.
        "&vipver=1&strategy=2012&mobi=1&issubtitle=1&show_copyright_off=1"

    /** The lyrics request for Kuwo song [rid]: its query is XORed with a fixed key, then base64. */
    fun lyricUrl(rid: String): String {
        val query = Base64.getEncoder().encodeToString(xor("user=12345,web,web,web&requester=localhost&req=1&rid=$rid&lrcx=1".toByteArray()))
        return "https://newlyric.kuwo.cn/newlyric.lrc?$query"
    }

    fun xor(raw: ByteArray): ByteArray = ByteArray(raw.size) { i -> (raw[i].toInt() xor KEY[i % KEY.size].toInt()).toByte() }

    /** The songs of a search answer. */
    fun searchSongs(raw: String): List<CatalogueSong> {
        val list = runCatching { JSONObject(raw).optJSONArray("abslist") }.getOrNull() ?: return emptyList()
        fun JSONObject.text(name: String) = optString(name).replace("&nbsp;", " ").replace("&amp;", "&").trim()
        return (0 until list.length()).mapNotNull { i ->
            val record = list.optJSONObject(i) ?: return@mapNotNull null
            val rid = record.text("MUSICRID").ifBlank { record.text("DC_TARGETID") }.ifBlank { return@mapNotNull null }
            CatalogueSong(
                title = record.text("SONGNAME").ifBlank { record.text("NAME") },
                artist = record.text("ARTIST"),
                durationSec = record.text("DURATION").toIntOrNull() ?: 0,
                id = if (rid.startsWith("MUSIC_")) rid else "MUSIC_$rid",
            )
        }
    }

    /** Whether [song] may be the one asked for: Kuwo's cuts (backing track, live...) only when ours is one too. */
    fun notACut(song: CatalogueSong, asked: String) = !CUT.containsMatchIn(song.title) || CUT.containsMatchIn(asked)

    /**
     * The lyrics in Kuwo's answer ([body]: headers, a blank line, then the deflated lyrics), or null
     * when it is not lyrics. Word-timed when the text has word timings.
     */
    fun parse(body: ByteArray): Lyrics? {
        val split = indexOf(body, "\r\n\r\n".toByteArray())
        if (split < 0) return null
        if (!body.copyOfRange(0, split).toString(Charsets.US_ASCII).startsWith("tp=content")) return null
        val text = text(InflaterInputStream(ByteArrayInputStream(body.copyOfRange(split + 4, body.size))).readBytes())
        return words(text) ?: lines(text)
    }

    /** The inflated body as text: LRC itself, or base64 of it XORed with the request key; GB18030 either way. */
    fun text(inflated: ByteArray): String {
        val plain = if (inflated.dropWhile { it.toInt().toChar().isWhitespace() }.firstOrNull() == '['.code.toByte()) inflated
        else xor(Base64.getDecoder().decode(String(inflated, Charsets.US_ASCII).trim()))
        return String(plain, charset("GB18030")).removePrefix("﻿")
    }

    /**
     * Word-timed LRCX. With k the `[kuwo:]` value read as octal, a word starts |a+b| / 2(k / 10) ms
     * into its line and lasts |a-b| / 2(k % 10) ms. Lines whose words are all `<0,0>` are translations.
     * Null without word timing.
     */
    fun words(text: String): Lyrics? {
        if (!TOKEN.containsMatchIn(text)) return null
        val k = Regex("\\[kuwo:(\\d+)]").find(text)?.groupValues?.get(1)?.toIntOrNull(8) ?: return null
        val k1 = k / 10
        val k2 = k % 10
        if (k1 == 0 || k2 == 0) return null
        val out = text.lineSequence().mapNotNull { raw ->
            val m = LINE.find(raw.trimEnd('\r')) ?: return@mapNotNull null
            val tokens = TOKEN.findAll(raw.substring(m.range.last + 1)).map { it.groupValues }.toList()
            if (tokens.isEmpty() || tokens.all { it[1] == "0" && it[2] == "0" }) return@mapNotNull null
            val at = (m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 + m.groupValues[3].padEnd(3, '0').take(3).toLong()
            val words = tokens.map { (_, a, b, w) ->
                val x = a.toLong()
                val y = b.toLong()
                val start = at + Math.floorDiv(kotlin.math.abs(x + y), 2L * k1)
                LyricWord(start, start + Math.floorDiv(kotlin.math.abs(x - y), 2L * k2), w)
            }
            val line = words.joinToString("") { it.text }.trim()
            if (line.isEmpty() || isCredit(line)) null else LyricLine(at, line, words)
        }.toList()
        return out.takeIf { it.size >= 3 }?.let { Lyrics(lines = it, synced = true, provider = NAME) }
    }

    /** The text as plain LRC lines: Kuwo's own tag lines and translation notes dropped. */
    fun lines(text: String): Lyrics? {
        val lrc = text.lineSequence().map { it.trimEnd('\r') }
            .filterNot { it.startsWith("[kuwo:") || it.startsWith("[ml:") || NOTE.containsMatchIn(it) }
            .joinToString("\n")
        val lines = LyricsApi.lrcLines(lrc).filter { it.text.isNotBlank() && !isCredit(it.text) }
        return lines.takeIf { it.size >= 3 }?.let { Lyrics(lines = it, synced = true, provider = NAME) }
    }

    /** The credit lines that open the lyrics ("作词：…", "作曲：…"): not lyrics, as Kugou's are not. */
    fun isCredit(text: String) = text.contains('：') || CREDIT.containsMatchIn(text)
    private val CREDIT = Regex("^(?:作词|作曲|编曲|词|曲|制作人?|混音|录音|监制)\\s*:")

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
