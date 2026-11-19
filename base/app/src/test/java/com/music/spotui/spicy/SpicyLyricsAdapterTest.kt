// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.spicy

import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.spicy.models.LineRole
import com.music.spotui.spicy.models.LyricsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpicyLyricsAdapterTest {
    private fun lyrics(vararg lines: LyricLine, synced: Boolean = true) = Lyrics(lines.toList(), synced)

    @Test fun `unsynced lyrics are static lines without timing and breaks`() {
        val doc = spicyDocument(lyrics(LyricLine(0, "one"), LyricLine(0, ""), LyricLine(0, "♪"), LyricLine(0, "two"), synced = false))
        assertEquals(LyricsType.Static, doc.type)
        assertEquals(listOf("one", "two"), doc.lines.map { it.words.single().text })
        assertTrue(doc.lines.all { it.startMs == 0L && it.endMs == 0L })
    }

    @Test fun `line synced lyrics end where the next line starts`() {
        val doc = spicyDocument(lyrics(LyricLine(1_000, "one"), LyricLine(3_000, "two")), songDurationMs = 10_000)
        assertEquals(LyricsType.Line, doc.type)
        val lead = doc.lines.filter { it.role == LineRole.LEAD }
        assertEquals(1_000L, lead[0].startMs)
        assertEquals(3_000L, lead[0].endMs)
        assertEquals("one", lead[0].words.single().text)
        assertFalse(lead[0].lineTimed)
    }

    @Test fun `a long wait after a line shows the interlude dots`() {
        val doc = spicyDocument(lyrics(LyricLine(1_000, "one"), LyricLine(30_000, "two")))
        assertEquals(listOf(LineRole.LEAD, LineRole.INTERLUDE, LineRole.LEAD), doc.lines.map { it.role })
        assertTrue(doc.lines.any { it.role == LineRole.INTERLUDE && it.startMs < 30_000 && it.endMs == 30_000L })
    }

    @Test fun `a break line ends the line before it and is not drawn`() {
        val doc = spicyDocument(lyrics(LyricLine(1_000, "one"), LyricLine(2_000, "♪"), LyricLine(20_000, "two")))
        val lead = doc.lines.filter { it.role == LineRole.LEAD }
        assertEquals(listOf("one", "two"), lead.map { it.words.single().text })
        assertEquals(2_000L, lead[0].endMs)
        assertTrue(doc.lines.any { it.role == LineRole.INTERLUDE && it.startMs == 2_000L && it.endMs == 20_000L })
    }

    @Test fun `word synced lyrics keep their words and glue syllables`() {
        val line = LyricLine(
            1_000, "Hello world",
            words = listOf(LyricWord(1_000, 1_300, "Hel"), LyricWord(1_300, 1_600, "lo "), LyricWord(1_700, 2_200, "world")),
        )
        val other = LyricLine(3_000, "next")
        val doc = spicyDocument(lyrics(line, other))
        assertEquals(LyricsType.Syllable, doc.type)
        val words = doc.lines.first { it.role == LineRole.LEAD }.words
        assertEquals(listOf("Hel", "lo", "world"), words.map { it.text })
        assertEquals(listOf(false, true, false), words.map { it.isPartOfWord })
        // The line without words, in a word synced song, stays a whole line.
        val whole = doc.lines.last { it.role == LineRole.LEAD }
        assertTrue(whole.lineTimed)
    }

    @Test fun `a word without an end runs to the next one, but not beyond 700 ms`() {
        val words = syllableWords(
            "a b c",
            listOf(LyricWord(1_000, 0, "a"), LyricWord(1_200, 0, "b"), LyricWord(5_000, 0, "c")),
            lineEnd = 9_000,
        )
        assertEquals(1_200L, words[0].endMs)
        assertEquals(1_900L, words[1].endMs)
        assertEquals(5_700L, words[2].endMs)
    }

    @Test fun `backing vocals follow their line in the same group and widen its window`() {
        val line = LyricLine(
            1_000, "lead",
            words = listOf(LyricWord(1_000, 2_000, "lead")),
            background = "(ooh)",
            backgroundWords = listOf(LyricWord(900, 2_500, "(ooh)")),
        )
        val doc = spicyDocument(lyrics(line, LyricLine(6_000, "end", words = listOf(LyricWord(6_000, 7_000, "end")))))
        val lead = doc.lines.first { it.role == LineRole.LEAD }
        val bg = doc.lines.first { it.role == LineRole.BACKGROUND }
        assertEquals(lead.groupId, bg.groupId)
        assertEquals("ooh", bg.words.single().text)
        assertEquals(900L, lead.startMs)
        assertEquals(2_500L, lead.endMs)
        assertTrue(doc.lines.indexOf(bg) == doc.lines.indexOf(lead) + 1)
    }

    @Test fun `the second singer sits on the opposite side`() {
        val doc = spicyDocument(lyrics(LyricLine(1_000, "a", voice = 1), LyricLine(3_000, "b", voice = 2), LyricLine(5_000, "c", voice = 0)))
        val lead = doc.lines.filter { it.role == LineRole.LEAD }
        assertEquals(listOf(false, true, false), lead.map { it.oppositeAligned })
    }

    @Test fun `romanize swaps in the line's own romanization as a whole line`() {
        val line = LyricLine(
            1_000, "konnichiwa?", romanization = "konnichiwa",
            words = listOf(LyricWord(1_000, 2_000, "こんにちは")),
        )
        val plain = spicyDocument(lyrics(line, LyricLine(4_000, "x")))
        assertEquals("こんにちは", plain.lines.first { it.role == LineRole.LEAD }.words.single().text)
        val roman = spicyDocument(lyrics(line, LyricLine(4_000, "x")), romanize = true)
        val lead = roman.lines.first { it.role == LineRole.LEAD }
        assertEquals("konnichiwa", lead.words.single().text)
        assertTrue(lead.lineTimed)
    }

    @Test fun `a lead keeps its first singer null agent`() {
        val doc = spicyDocument(lyrics(LyricLine(1_000, "a"), LyricLine(2_000, "b")))
        assertNull(doc.lines.first { it.role == LineRole.LEAD }.agent)
    }

    @Test fun `line synced backing vocals are one whole-line line over the lead`() {
        val doc = spicyDocument(lyrics(LyricLine(1_000, "lead", background = "(ooh)"), LyricLine(4_000, "next")))
        assertEquals(LyricsType.Line, doc.type)
        val lead = doc.lines.first { it.role == LineRole.LEAD }
        val bg = doc.lines.first { it.role == LineRole.BACKGROUND }
        assertEquals(lead.groupId, bg.groupId)
        assertEquals("ooh", bg.words.single().text)
        assertEquals(lead.startMs, bg.startMs)
        assertEquals(lead.endMs, bg.endMs)
        assertEquals(lead.endMs, bg.words.single().endMs)
    }

    @Test fun `equal and non-increasing timestamps give zero-length lines`() {
        // Locks in today's behaviour: nothing is reordered or dropped.
        val doc = spicyDocument(lyrics(LyricLine(1_000, "a"), LyricLine(1_000, "b"), LyricLine(500, "c")))
        val lead = doc.lines.filter { it.role == LineRole.LEAD }
        assertEquals(listOf("a", "b", "c"), lead.map { it.words.single().text })
        assertEquals(1_000L, lead[0].endMs)
        assertEquals(1_000L, lead[1].endMs)
        assertEquals(500L, lead[2].startMs)
        assertTrue(lead[2].endMs >= lead[2].startMs)
    }

    @Test fun `only breaks leave nothing to draw`() {
        assertFalse(hasDrawableLines(lyrics(LyricLine(1_000, ""), LyricLine(2_000, "♪"))))
        assertFalse(hasDrawableLines(lyrics()))
        assertTrue(hasDrawableLines(lyrics(LyricLine(1_000, "x"))))
    }

    @Test fun `the first artist of a credit is looked up`() {
        assertEquals("A", primaryArtist("A, B"))
        assertEquals("A", primaryArtist("A & B"))
        assertEquals("Solo", primaryArtist("Solo"))
    }
}
