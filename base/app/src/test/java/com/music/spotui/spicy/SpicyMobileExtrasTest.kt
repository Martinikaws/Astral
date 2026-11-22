package com.music.spotui.spicy

import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.LyricsTimingPref
import com.music.spotui.spicy.romanization.OfflineRomanization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import com.music.spotui.ui.immersive.squared.appleMotionUrl
import org.junit.Test

class SpicyMobileExtrasTest {
    @Test fun calibrationUsesOriginalLineTimeAndClampsExtremeOffsets() {
        assertEquals(450, LyricsTimingPref.calibrationOffset(10_450, 10_000))
        assertEquals(-450, LyricsTimingPref.calibrationOffset(9_550, 10_000))
        assertEquals(5_000, LyricsTimingPref.calibrationOffset(20_000, 10_000))
    }

    @Test fun appleMotionAcceptsAppleHlsOnly() {
        val html = """<amp-ambient-video src="https://evil.example/cover.m3u8"></amp-ambient-video>
            <amp-ambient-video src="https://a.mzstatic.com/cover.m3u8?a=1&amp;b=2"></amp-ambient-video>"""
        assertEquals("https://a.mzstatic.com/cover.m3u8?a=1&b=2", appleMotionUrl(html))
        assertEquals("https://a.mzstatic.com/tall.m3u8",
            appleMotionUrl("""{"motionDetailTall":{"video":"https:\/\/a.mzstatic.com\/tall.m3u8"}}"""))
        assertNull(appleMotionUrl("""<amp-ambient-video src="http://a.mzstatic.com/cover.m3u8">"""))
    }
    @Test fun timingAdjustsLinesAndWordTimingsWithoutMovingUntimedLyrics() {
        val fixture = Lyrics(listOf(LyricLine(400, "word",
            words = listOf(LyricWord(400, 650, "word")),
            backgroundWords = listOf(LyricWord(500, 0, "echo")),
        )), synced = true)

        val shifted = LyricsTimingPref.apply(fixture, -500).lines.single()
        assertEquals(0L, shifted.timeMs)
        assertEquals(LyricWord(0, 150, "word"), shifted.words!!.single())
        assertEquals(LyricWord(0, 0, "echo"), shifted.backgroundWords!!.single())
        assertEquals(700L, LyricsTimingPref.apply(fixture, 300).lines.single().timeMs)
        val untimed = fixture.copy(synced = false)
        assertSame(untimed, LyricsTimingPref.apply(untimed, 300))
    }

    @Test fun notesPreserveOriginalWordsAndTimes() {
        val lyric = Lyrics(listOf(LyricLine(1_000, "Любовь", listOf(LyricWord(1_000, 1_200, "Любовь")),
            romanization = "Lyubov")), synced = true)
        val line = spicyDocument(lyric, romanize = true, translations = listOf("Love"), romanizationAsNote = true)
            .lines.single { !it.isBackground && it.words.any { word -> word.text == "Любовь" } }
        assertEquals(1_000L, line.words.single().startMs)
        assertEquals("Lyubov", line.romanizationNote)
        assertEquals("Love", line.translation)
    }

    @Test fun offlineRomanizationLeavesLatinLinesAlone() {
        val result = OfflineRomanization.fill(mapOf(0 to "Привет", 1 to "Latin"), "Привет Latin")
        assertEquals(mapOf(0 to "Privet"), result)
    }
}
