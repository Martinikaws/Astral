package com.music.spotui.data.api

import com.music.spotui.data.preferences.LyricsSourcePref
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

/**
 * The sources added from Spicy Lyrics Mobile (Unison, LRCMux, QQ Music, Kuwo, RMM Revival): what
 * their answers parse to. Hand-written samples of each answer's shape (upstream has none); no network.
 */
class LyricsExtraSourcesTest {

    // ── Unison ──

    private val unisonSearch = """{"data":[
        {"id":"u0","song":"Hello Again","artist":"Adele"},
        {"id":"u1","song":"Hello","artist":"Adele, Someone","submitter":{"displayName":"x"}}]}"""

    @Test fun unisonSearchHitIsTheSongByItsTitleAndArtist() {
        val hit = UnisonLyrics.searchHit(unisonSearch, "Hello", "Adele")!!
        assertEquals("u1", hit.getString("id"))
        assertNull(UnisonLyrics.searchHit(unisonSearch, "Hello", "Beyonce"))
        assertNull(UnisonLyrics.searchHit("not json", "Hello", "Adele"))
    }

    @Test fun unisonLrcIsLineSynced() {
        val data = UnisonLyrics.detailData("""{"data":{"format":"lrc","lyrics":"[00:12.50]Hello\n[00:15.00]It's me\n[00:18.25]I was wondering"}}""")!!
        val l = UnisonLyrics.lyricsOf(data)!!
        assertTrue(l.synced)
        assertEquals("Unison", l.provider)
        assertEquals(listOf(12_500L, 15_000L, 18_250L), l.lines.map { it.timeMs })
        assertEquals("It's me", l.lines[1].text)
    }

    @Test fun unisonLrcIsRecognisedWithoutAFormat() {
        val l = UnisonLyrics.lyricsOf(JSONObject("""{"lyrics":"[01:02.5]a\n[01:03.5]b"}"""))!!
        assertTrue(l.synced)
        assertEquals(62_500L, l.lines[0].timeMs)
    }

    @Test fun unisonPlainTextIsUnsynced() {
        val l = UnisonLyrics.lyricsOf(JSONObject("""{"format":"plain","lyrics":"first\n\nsecond\n"}"""))!!
        assertFalse(l.synced)
        assertEquals(listOf("first", "second"), l.lines.map { it.text })
    }

    @Test fun unisonYamlAndEmptyAnswersGiveNothing() {
        assertNull(UnisonLyrics.lyricsOf(JSONObject("""{"format":"yaml","lyrics":"a: b"}""")))
        assertNull(UnisonLyrics.lyricsOf(JSONObject("""{"format":"lrc","lyrics":"   "}""")))
        assertNull(UnisonLyrics.lyricsOf(JSONObject("{}")))
    }

    @Test fun unisonTitleLookupMustBeTheSameSong() {
        val ok = UnisonLyrics.directData("""{"data":{"title":"Hello","artist":"Adele","lyrics":"x"}}""", "hello", "ADELE")
        assertNotNull(ok)
        assertNull(UnisonLyrics.directData("""{"data":{"title":"Hello","artist":"Lionel Richie","lyrics":"x"}}""", "Hello", "Adele"))
    }

    @Test fun unisonUrlsCarryTitleAndArtistOnly() {
        assertEquals("https://unison.boidu.dev/lyrics/search?q=Adele+Hello+%26+Co", UnisonLyrics.searchUrl("Hello & Co", "Adele"))
        assertEquals("https://unison.boidu.dev/lyrics?song=Hello&artist=Adele", UnisonLyrics.directUrl("Hello", "Adele"))
        assertEquals("https://unison.boidu.dev/lyrics/u1", UnisonLyrics.detailUrl("u1"))
    }

    // ── LRCMux ──

    @Test fun lrcMuxWordsInMillisecondsKeepTheirSpaces() {
        val root = JSONObject("""{"meta":{"source":{"name":"KuGou"}},"track":{"title":"Dynamite","album":"BE"},"lines":[
            {"start":6403,"end":8000,"text":"I'm ready now","words":[{"text":"I'm","start":6403,"end":6900},{"text":"ready","start":6900,"end":7400},{"text":"now","start":7400,"end":8000}]},
            {"start":9000,"end":10000,"text":"Go","words":[{"text":"Go","start":9000,"end":10000}]}]}""")
        val l = LrcMuxLyrics.parse(root)!!
        assertTrue(l.wordSynced)
        assertEquals("LRCMux · KuGou", l.provider)
        assertEquals(6403L, l.lines[0].timeMs)
        assertEquals("I'm ready now", l.lines[0].text)
        assertEquals(listOf("I'm ", "ready ", "now"), l.lines[0].words!!.map { it.text })
        assertEquals(6900L to 7400L, l.lines[0].words!![1].let { it.startMs to it.endMs })
    }

    @Test fun lrcMuxSecondsAreDecidedForTheWholeAnswer() {
        // A line in the first ten seconds (6.403) must not be read as milliseconds.
        val root = JSONObject("""{"lines":[{"start":6.403,"end":9.0,"text":"Early"},{"start":12.5,"end":15.0,"text":"Later"}]}""")
        val l = LrcMuxLyrics.parse(root)!!
        assertTrue(l.synced)
        assertFalse(l.wordSynced)
        assertEquals(listOf(6403L, 12_500L), l.lines.map { it.timeMs })
        assertEquals("LRCMux", l.provider)
    }

    @Test fun lrcMuxCjkWordsAreNotSpaced() {
        val root = JSONObject("""{"lines":[{"start":1000,"end":3000,"text":"你好吗","words":[{"text":"你","start":1000,"end":1500},{"text":"好","start":1500,"end":2000},{"text":"吗","start":2000,"end":3000}]}]}""")
        assertEquals(listOf("你", "好", "吗"), LrcMuxLyrics.parse(root)!!.lines[0].words!!.map { it.text })
    }

    @Test fun lrcMuxWithoutTimesIsPlain() {
        val l = LrcMuxLyrics.parse(JSONObject("""{"lines":[{"text":"one"},{"text":"two"}]}"""))!!
        assertFalse(l.synced)
        assertEquals(listOf("one", "two"), l.lines.map { it.text })
    }

    @Test fun lrcMuxEmptyGivesNothing() {
        assertNull(LrcMuxLyrics.parse(JSONObject("""{"lines":[]}""")))
        assertNull(LrcMuxLyrics.parse(JSONObject("{}")))
    }

    @Test fun lrcMuxRefusesAnotherVersionOfTheSong() {
        val remix = JSONObject("""{"track":{"title":"Dynamite (NightTime Version)","album":"Dynamite"}}""")
        assertTrue(LrcMuxLyrics.otherVersion(remix, "Dynamite BE"))
        assertFalse(LrcMuxLyrics.otherVersion(remix, "Dynamite (NightTime Version) Dynamite"))
        assertFalse(LrcMuxLyrics.otherVersion(JSONObject("""{"track":{"title":"Dynamite","album":"BE"}}"""), "Dynamite BE"))
        assertFalse(LrcMuxLyrics.otherVersion(JSONObject("{}"), "Dynamite"))
    }

    @Test fun otherVersionLooksAtBracketsAndDashesOnly() {
        assertTrue(LyricsVersions.otherVersion("Song (Remix)", "Song"))
        assertTrue(LyricsVersions.otherVersion("Song - Live at Wembley", "Song"))
        assertTrue(LyricsVersions.otherVersion("Song [Sped Up]", "Song"))
        assertFalse(LyricsVersions.otherVersion("Song (Remix)", "Song (Remix)"))
        // Same recording: notes about the release, not the recording.
        assertFalse(LyricsVersions.otherVersion("Song (Deluxe Edition)", "Song"))
        assertFalse(LyricsVersions.otherVersion("Song (2015 Remaster)", "Song"))
        assertFalse(LyricsVersions.otherVersion("Song (Taylor's Version)", "Song"))
    }

    @Test fun wordsThatMerelyContainAVersionMarkerAreNotOtherVersions() {
        assertFalse(LyricsVersions.otherVersion("Oliver", "Oliver"))
        assertFalse(LyricsVersions.otherVersion("Oliver", "Song"))
        assertFalse(LyricsVersions.otherVersion("Credits", "Song"))
        assertFalse(LyricsVersions.otherVersion("Song (Credits)", "Song"))
        assertFalse(LyricsVersions.otherVersion("Song Deluxe Edition", "Song"))
        assertFalse(LyricsVersions.otherVersion("Song (Deluxe Edition)", "Song"))
        assertFalse(LyricsVersions.otherVersion("Alive Olive", "Song"))
    }

    @Test fun lrcMuxAnswerTrackIsReadWithItsLength() {
        val t = LrcMuxLyrics.answerTrack(JSONObject("""{"track":{"title":"Dynamite","artist":"BTS","duration":199.4}}"""))!!
        assertEquals("Dynamite", t.title)
        assertEquals("BTS", t.artist)
        assertEquals(199_400L, t.durationMs)
        assertEquals(199_000L, LrcMuxLyrics.answerTrack(JSONObject("""{"track":{"title":"D","artists":["BTS"],"duration":199000}}"""))!!.durationMs)
        assertEquals("BTS, V", LrcMuxLyrics.answerTrack(JSONObject("""{"track":{"title":"D","artists":["BTS","V"]}}"""))!!.artist)
        assertNull(LrcMuxLyrics.answerTrack(JSONObject("{}")))
    }

    @Test fun lrcMuxUrlAsksForWords() {
        assertEquals("https://api.lrcmux.dev/get?artist=BTS&title=Dynamite&duration=199&format=json&level=word", LrcMuxLyrics.url("Dynamite", "BTS", 199))
    }

    // ── QQ Music ──

    @Test fun qqSearchListsSongsWithTheirSingersAndLength() {
        val songs = QqLyrics.searchSongs("""{"code":0,"req_1":{"code":0,"data":{"body":{"song":{"list":[
            {"name":"晴天","interval":269,"mid":"0039MnYb0qxYhV","singer":[{"name":"周杰伦"},{"name":"X"}]}]}}}}}""")!!
        assertEquals(CatalogueSong("晴天", "周杰伦, X", 269, "0039MnYb0qxYhV"), songs.single())
    }

    @Test fun qqThrottledSearchIsNullSoTheSuggestionsAreAsked() {
        assertNull(QqLyrics.searchSongs("""{"code":0,"req_1":{"code":2001,"data":{}}}"""))
        assertNull(QqLyrics.searchSongs("""{"code":500}"""))
        assertEquals(emptyList<CatalogueSong>(), QqLyrics.searchSongs("""{"code":0,"req_1":{"code":0,"data":{}}}"""))
    }

    @Test fun qqSuggestionsGiveMidsAndTrackDetailsGiveLengths() {
        assertEquals(listOf("m1", "m2"), QqLyrics.suggestedMids("""{"data":{"song":{"itemlist":[{"mid":"m1"},{"name":"x"},{"mid":"m2"}]}}}"""))
        val tracks = QqLyrics.trackInfoSongs("""{"req_1":{"code":0,"data":{"tracks":[{"name":"A","interval":200,"mid":"m1","singer":[{"name":"S"}]}]}}}""")!!
        assertEquals(200, tracks.single().durationSec)
        assertNull(QqLyrics.trackInfoSongs("""{"req_1":{"code":1}}"""))
    }

    @Test fun qqLyricsInPlainAndBase64() {
        val lrc = "[00:01.00]一\n[00:02.00]二\n[00:03.50]三"
        val plain = QqLyrics.lyricsOf(JSONObject().put("lyric", lrc).toString())!!
        assertEquals(listOf(1000L, 2000L, 3500L), plain.lines.map { it.timeMs })
        assertEquals("QQ Music", plain.provider)
        val b64 = Base64.getEncoder().encodeToString(lrc.toByteArray())
        assertEquals(plain.lines, QqLyrics.lyricsOf(JSONObject().put("lyric", b64).toString())!!.lines)
        assertNull(QqLyrics.lyricsOf("""{"lyric":""}"""))
    }

    @Test fun qqRequestsCarryTheQueryAndTheMids() {
        val search = JSONObject(QqLyrics.searchBody("周杰伦 晴天")).getJSONObject("req_1").getJSONObject("param")
        assertEquals("周杰伦 晴天", search.getString("query"))
        assertEquals(listOf(0, 0), JSONObject(QqLyrics.trackInfoBody(listOf("a", "b"))).getJSONObject("req_1").getJSONObject("param")
            .getJSONArray("types").let { a -> List(a.length()) { a.getInt(it) } })
    }

    // ── Kuwo ──

    @Test fun kuwoSearchReadsSingleQuotedJsonAndHtmlSpaces() {
        val raw = """{'TOTAL':'2','abslist':[
            {'SONGNAME':'Blinding&nbsp;Lights','ARTIST':'The&nbsp;Weeknd','DURATION':'200','MUSICRID':'MUSIC_123'},
            {'NAME':'Other','ARTIST':'X','DURATION':'abc','DC_TARGETID':'456'},{'SONGNAME':'No id'}]}"""
        val songs = KuwoLyrics.searchSongs(raw)
        assertEquals(listOf(CatalogueSong("Blinding Lights", "The Weeknd", 200, "MUSIC_123"), CatalogueSong("Other", "X", 0, "MUSIC_456")), songs)
    }

    @Test fun kuwoCutsAreOnlyAcceptedWhenOursIsOneToo() {
        val live = CatalogueSong("晴天 现场", "周杰伦", 270, "MUSIC_1")
        assertFalse(KuwoLyrics.notACut(live, "晴天"))
        assertTrue(KuwoLyrics.notACut(live, "晴天 现场"))
        assertTrue(KuwoLyrics.notACut(CatalogueSong("晴天", "周杰伦", 270, "MUSIC_2"), "晴天"))
    }

    // [kuwo:42] is octal 34: k1 = 3, k2 = 4. A word starts |a+b| / 6 ms into its line and lasts |a-b| / 8 ms.
    private val kuwoWords = listOf(
        "[kuwo:42]",
        "[00:01.00]<0,60>Hel<60,60>lo",
        "[00:03.00]<0,0>你好",
        "[00:03.00]<0,120>Good<120,120>bye",
        "[00:05.50]<0,48>La<48,48>la",
    ).joinToString("\n")

    @Test fun kuwoWordTimingsFollowTheOctalFactor() {
        val l = KuwoLyrics.words(kuwoWords)!!
        assertTrue(l.wordSynced)
        assertEquals("Kuwo", l.provider)
        assertEquals(3, l.lines.size)
        val first = l.lines[0]
        assertEquals(1000L, first.timeMs)
        assertEquals("Hello", first.text)
        assertEquals(listOf(1010L to 1017L, 1020L to 1020L), first.words!!.map { it.startMs to it.endMs })
        // The line whose words are all <0,0> is a translation: left out.
        assertEquals(listOf("Hello", "Goodbye", "Lala"), l.lines.map { it.text })
        assertEquals(3000L, l.lines[1].timeMs)
    }

    @Test fun kuwoWithoutAFactorHasNoWordTimings() {
        assertNull(KuwoLyrics.words("[00:01.00]<0,60>Hel\n[00:02.00]<0,60>lo"))
        assertNull(KuwoLyrics.words("[kuwo:0]\n[00:01.00]<0,60>a"))
        assertNull(KuwoLyrics.words("[00:01.00]plain"))
    }

    @Test fun kuwoLinesDropItsTagsAndTranslationNotes() {
        val l = KuwoLyrics.lines("[kuwo:42]\n[ml:1]\n[00:01.00]一\n歌词翻译由某某提供\n[00:02.00]二\n[00:03.00]三")!!
        assertFalse(l.wordSynced)
        assertEquals(listOf("一", "二", "三"), l.lines.map { it.text })
    }

    private fun kuwoBody(inflated: ByteArray, header: String = "tp=content\r\n\r\n"): ByteArray {
        val packed = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(inflated) } }.toByteArray()
        return header.toByteArray() + packed
    }

    @Test fun kuwoAnswerIsInflatedAndReadAsGb18030() {
        val body = kuwoBody("[00:01.00]你好\n[00:02.00]世界\n[00:03.00]再见".toByteArray(charset("GB18030")))
        val l = KuwoLyrics.parse(body)!!
        assertEquals(listOf("你好", "世界", "再见"), l.lines.map { it.text })
        assertEquals(listOf(1000L, 2000L, 3000L), l.lines.map { it.timeMs })
    }

    @Test fun kuwoAnswerXoredAndBase64dIsDecodedToo() {
        val text = "[kuwo:42]\n[00:01.00]<0,60>a<60,60>b\n[00:02.00]<0,60>c\n[00:03.00]<0,60>d".toByteArray(charset("GB18030"))
        val packed = Base64.getEncoder().encode(KuwoLyrics.xor(text))
        val l = KuwoLyrics.parse(kuwoBody(packed))!!
        assertTrue(l.wordSynced)
        assertEquals(listOf("ab", "c", "d"), l.lines.map { it.text })
    }

    @Test fun kuwoAnswerThatIsNotContentGivesNothing() {
        assertNull(KuwoLyrics.parse(kuwoBody("[00:01.00]a".toByteArray(), header = "tp=error\r\n\r\n")))
        assertNull(KuwoLyrics.parse("no header at all".toByteArray()))
    }

    @Test fun kuwoLyricRequestIsTheXoredQueryInBase64() {
        val url = KuwoLyrics.lyricUrl("MUSIC_123")
        val query = url.substringAfter("newlyric.lrc?")
        val plain = String(KuwoLyrics.xor(Base64.getDecoder().decode(query)))
        assertEquals("user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_123&lrcx=1", plain)
    }

    // ── RMM Revival ──

    private val itunes = """{"resultCount":3,"results":[
        {"trackId":111,"trackName":"Blinding Lights","artistName":"The Weeknd","trackTimeMillis":200040},
        {"trackId":222,"trackName":"Blinding Lights","artistName":"The Weeknd","trackTimeMillis":260000},
        {"trackId":333,"trackName":"Blinding Lights (Remix)","artistName":"The Weeknd","trackTimeMillis":200000}]}"""

    @Test fun rmmTakesTheAppleIdOfTheNearestSameSong() {
        assertEquals("111", RmmRevivalLyrics.appleId(itunes, "Blinding Lights", "The Weeknd", 200_000))
        // Only a version of another length: not the song.
        assertNull(RmmRevivalLyrics.appleId(itunes, "Blinding Lights", "The Weeknd", 230_000))
        assertNull(RmmRevivalLyrics.appleId(itunes, "Blinding Lights", "Dua Lipa", 200_000))
        assertEquals("111", RmmRevivalLyrics.appleId(itunes, "blinding lights", "weeknd", 0))
        assertNull(RmmRevivalLyrics.appleId("""{"results":[]}""", "x", "y", 0))
    }

    @Test fun rmmAnswerInLrcOrPlainText() {
        val lrc = RmmRevivalLyrics.parse("""{"syncedLyrics":"[00:01.00]a\n[00:02.00]b"}""")!!
        assertTrue(lrc.synced)
        assertEquals("RMM Revival", lrc.provider)
        val plain = RmmRevivalLyrics.parse("""{"lyrics":"just\ntext"}""")!!
        assertFalse(plain.synced)
        assertEquals(2, plain.lines.size)
    }

    @Test fun rmmTakesTheFirstFieldThatHasText() {
        val l = RmmRevivalLyrics.parse("""{"ttml":"","content":"[00:05.00]from content","lyrics":"ignored"}""")!!
        assertEquals("from content", l.lines.single().text)
        assertNull(RmmRevivalLyrics.parse("""{"ttml":"  "}"""))
        assertNull(RmmRevivalLyrics.parse("nope"))
    }

    // ── Credits ──

    @Test fun kuwoDropsCreditLinesLikeKugou() {
        val lrc = "[00:01.00]作词：某某\n[00:02.00]作曲 : 某某\n[00:03.00]一\n[00:04.00]二\n[00:05.00]三"
        assertEquals(listOf("一", "二", "三"), KuwoLyrics.lines(lrc)!!.lines.map { it.text })
        val words = listOf("[kuwo:42]", "[00:01.00]<0,60>作词：<60,60>某", "[00:02.00]<0,60>a", "[00:03.00]<0,60>b", "[00:04.00]<0,60>c").joinToString("\n")
        assertEquals(listOf("a", "b", "c"), KuwoLyrics.words(words)!!.lines.map { it.text })
    }

    // ── Cache and switches ──

    @Suppress("UNCHECKED_CAST")
    private fun cacheOf() = LyricsApi::class.java.getDeclaredField("cache").apply { isAccessible = true }.get(null) as MutableMap<String, Any>

    @Test fun switchingASourceForgetsMissesButKeepsFoundLyrics() {
        val cache = cacheOf()
        val found = com.music.spotui.data.entity.Lyrics(listOf(com.music.spotui.data.entity.LyricLine(0, "kept")), false)
        val missCls = Class.forName("com.music.spotui.data.api.LyricsApi\$Miss")
        val miss = missCls.getDeclaredConstructor(Long::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(System.currentTimeMillis())
        cache["t-found"] = found
        cache["t-miss"] = miss
        try {
            LyricsApi.forgetMisses()
            assertEquals(found, cache["t-found"])
            assertNull(cache["t-miss"])
        } finally { cache.remove("t-found"); cache.remove("t-miss") }
    }

    private fun lostCount(): Long {
        val health = LyricsApi::class.java.getDeclaredField("health").apply { isAccessible = true }.get(null) as LyricsHealth
        return health.lostCount()
    }

    @Test fun aSourceThatIsOffIsNotAskedByHandEither() {
        val track = LyricsApi.Track("", "Song", "Artist", "Album", 200_000)
        val before = lostCount()
        for (id in listOf("qq", "kuwo", "rmm")) {
            assertNull(id, kotlinx.coroutines.runBlocking { LyricsApi.fetchFrom(track, id) })
        }
        // A request made (and failed, offline) would have counted as lost.
        assertEquals(before, lostCount())
    }

    // ── HTTP ──

    private fun withServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit, block: (String) -> Unit) {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { ex -> handler(ex); ex.close() }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}") } finally { server.stop(0) }
    }

    private fun reply(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.write(bytes)
    }

    private fun httpGet(url: String): String? =
        LyricsApi::class.java.getDeclaredMethod("httpGet", String::class.java, Int::class.javaPrimitiveType, String::class.java)
            .apply { isAccessible = true }.invoke(LyricsApi, url, 3000, null) as String?

    @Test fun httpGetStillReturnsTheBodyOfA200WithAstralsUserAgent() {
        var agent: String? = null
        var method: String? = null
        withServer({ ex -> agent = ex.requestHeaders.getFirst("User-Agent"); method = ex.requestMethod; reply(ex, 200, "{\"ok\":\"é\"}") }) { base ->
            assertEquals("{\"ok\":\"é\"}", httpGet("$base/x"))
        }
        assertEquals("GET", method)
        assertTrue(agent!!.startsWith("Astral"))
    }

    @Test fun httpFetchPostsABodyWithItsHeadersAndGivesBytes() {
        var got: String? = null
        var referer: String? = null
        var method: String? = null
        withServer({ ex -> got = ex.requestBody.readBytes().decodeToString(); referer = ex.requestHeaders.getFirst("Referer"); method = ex.requestMethod; reply(ex, 200, "pong") }) { base ->
            val bytes = LyricsApi::class.java.getDeclaredMethod("httpFetch", String::class.java, Int::class.javaPrimitiveType, String::class.java,
                String::class.java, ByteArray::class.java, Map::class.java).apply { isAccessible = true }
                .invoke(LyricsApi, "$base/x", 3000, null, "POST", "ping".toByteArray(), mapOf("Referer" to "https://y.qq.com/")) as ByteArray?
            assertEquals("pong", bytes!!.decodeToString())
        }
        assertEquals("POST", method)
        assertEquals("ping", got)
        assertEquals("https://y.qq.com/", referer)
    }

    // ── Switches ──

    @Test fun unisonAndLrcMuxAreOnByDefaultTheOthersOff() {
        assertTrue(LyricsSourcePref.on("unison"))
        assertTrue(LyricsSourcePref.on("lrcmux"))
        assertFalse(LyricsSourcePref.on("qq"))
        assertFalse(LyricsSourcePref.on("kuwo"))
        assertFalse(LyricsSourcePref.on("rmm"))
    }
}
