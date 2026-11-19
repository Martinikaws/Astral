package com.music.spotui.spicy

import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.spicy.models.Line
import com.music.spotui.spicy.models.LineRole
import com.music.spotui.spicy.models.LyricsType
import com.music.spotui.spicy.models.Word
import com.music.spotui.spicy.models.buildDisplayTimeline

/** What [LyricsView][com.music.spotui.spicy.canvas.LyricsView] is fed: the lines, how they are synced. */
data class SpicyDocument(
    val lines: List<Line>,
    val type: LyricsType,
)

/**
 * Astral's [Lyrics] as the renderer's lines (written for Astral; the renderer's model is
 * upstream's `Line`/`Word`, see `models/`).
 *
 * - Not synced: [LyricsType.Static], one untimed word per line.
 * - Some line has words: [LyricsType.Syllable]. A line without words stays a whole-line line
 *   (`lineTimed`), the way upstream keeps a line-timed `<p>` in a word-timed document.
 * - Otherwise [LyricsType.Line].
 *
 * A lead line is followed by its backing vocals (a BACKGROUND line of the same group), the lead's
 * window widened to cover them. Blank and "♪" lines are the source's own breaks: they are not
 * drawn, the interlude dots come from the gap they leave ([buildDisplayTimeline]). A second singer
 * ([LyricLine.voice] above 1) is drawn on the opposite side.
 *
 * [romanize] shows a line's own romanization (when the source gave one) in place of its text, as
 * one whole-line word.
 */
fun spicyDocument(lyrics: Lyrics, songDurationMs: Long = 0L, romanize: Boolean = false, minimalMode: Boolean = false): SpicyDocument {
    if (!lyrics.synced) return SpicyDocument(staticLines(lyrics, romanize), LyricsType.Static)
    val type = if (lyrics.wordSynced) LyricsType.Syllable else LyricsType.Line
    val source = lyrics.lines
    val out = ArrayList<Line>(source.size + 4)
    var group = 0
    source.forEachIndexed { index, line ->
        if (isBreak(line)) return@forEachIndexed
        val next = source.drop(index + 1).firstOrNull()?.timeMs?.takeIf { it > line.timeMs }
        val nextNonBreak = source.drop(index + 1).firstOrNull { !isBreak(it) }
        val limit = next ?: songDurationMs.takeIf { it > line.timeMs }
        val opposite = (line.voice ?: 1) > 1
        val agent = line.voice?.toString()
        val roman = if (romanize) line.romanization?.trim()?.takeIf { it.isNotEmpty() && !it.equals(line.text.trim(), ignoreCase = true) } else null
        val id = group++

        val leadWords: List<Word>
        val lineTimed: Boolean
        val leadStart: Long
        var leadEnd: Long
        val timed = line.words?.takeIf { it.isNotEmpty() && type == LyricsType.Syllable && roman == null }
        if (timed != null) {
            leadWords = syllableWords(line.text, timed, limit)
            lineTimed = false
            leadStart = minOf(line.timeMs, timed.first().startMs)
            leadEnd = leadWords.lastOrNull()?.endMs ?: leadStart
        } else {
            val text = (roman ?: line.text).trim()
            leadStart = line.timeMs
            val breakFollows = next != null && isBreak(source[index + 1])
            leadEnd = wholeLineEnd(line, text, if (breakFollows) next else nextNonBreak?.timeMs ?: limit, breakFollows)
            leadWords = listOf(Word(text, leadStart, leadEnd, romanizedText = null))
            lineTimed = type == LyricsType.Syllable
        }

        // The backing vocals, under the lead, in the same group.
        val background = backgroundLine(line, limit, type, id, agent, opposite)
        if (background != null) {
            leadEnd = maxOf(leadEnd, background.endMs)
        }
        val lead = Line(
            words = leadWords,
            startMs = if (background != null) minOf(leadStart, background.startMs) else leadStart,
            endMs = leadEnd.coerceAtLeast(leadStart),
            agent = agent, role = LineRole.LEAD, groupId = id, oppositeAligned = opposite, lineTimed = lineTimed,
        )
        out += lead
        if (background != null) out += background
    }
    return SpicyDocument(buildDisplayTimeline(out, minimalMode), type)
}

private fun staticLines(lyrics: Lyrics, romanize: Boolean): List<Line> = lyrics.lines.mapNotNull { line ->
    val text = (if (romanize) line.romanization?.takeIf { it.isNotBlank() } else null) ?: line.text
    if (text.isBlank() || text.trim() == "♪") return@mapNotNull null
    Line(listOf(Word(text.trim(), 0L, 0L)), 0L, 0L, oppositeAligned = (line.voice ?: 1) > 1)
}

/** A line the source uses as a break between two sung ones. */
internal fun isBreak(line: LyricLine): Boolean {
    val text = line.text.trim()
    return text.isEmpty() || text == "♪"
}

/** Longest a word whose end the source did not give is taken to be sung (the immersive panel's own rule). */
private const val UNKNOWN_WORD_MAX_MS = 700L

/** A whole-line line shows for at least this long, and at most [MAX_LINE_MS] before the dots take over. */
private const val MIN_LINE_MS = 2_500L
private const val MAX_LINE_MS = 7_000L
private const val MS_PER_CHAR = 90L

/**
 * Where a line sung as a whole ends: where the next one starts, but when that leaves more than
 * the interlude threshold (3 s) after the time the text takes to sing, there where it is sung, so
 * the gap shows its dots. A break line after it ends it at once.
 */
internal fun wholeLineEnd(line: LyricLine, text: String, nextStart: Long?, breakFollows: Boolean): Long {
    val sung = line.timeMs + (text.length * MS_PER_CHAR).coerceIn(MIN_LINE_MS, MAX_LINE_MS)
    if (nextStart == null) return sung
    if (breakFollows) return nextStart.coerceAtLeast(line.timeMs)
    return if (nextStart - sung > INTERLUDE_AFTER_MS) sung else nextStart.coerceAtLeast(line.timeMs)
}

private const val INTERLUDE_AFTER_MS = 3_000L

/**
 * The line's words as renderer words: each trimmed, glued to the one before when the line's own
 * text has nothing between them (a syllable), a word with no end running to the next word's start.
 */
internal fun syllableWords(lineText: String, words: List<LyricWord>, lineEnd: Long?): List<Word> {
    val out = ArrayList<Word>(words.size)
    var offset = 0
    words.forEachIndexed { index, w ->
        val text = w.text.trim()
        if (text.isEmpty()) return@forEachIndexed
        val at = lineText.indexOf(text, offset)
        val glued = if (at >= 0) {
            val gap = lineText.substring(offset, at)
            offset = at + text.length
            out.isNotEmpty() && gap.isEmpty()
        } else {
            // Not found in the line's text: a separate word.
            false
        }
        val following = words.drop(index + 1).firstOrNull { it.text.isNotBlank() }?.startMs ?: lineEnd ?: (w.startMs + 500L)
        val end = if (w.endMs > w.startMs) w.endMs else minOf(following.coerceAtLeast(w.startMs), w.startMs + UNKNOWN_WORD_MAX_MS)
        out += Word(text, w.startMs, end.coerceAtLeast(w.startMs), isPartOfWord = glued)
    }
    return out
}

private fun backgroundLine(line: LyricLine, limit: Long?, type: LyricsType, group: Int, agent: String?, opposite: Boolean): Line? {
    if (type != LyricsType.Syllable) return null
    val timed = line.backgroundWords.orEmpty()
        .map { it.copy(text = it.text.replace("(", "").replace(")", "")) }
        .filter { it.text.isNotBlank() }
    val words: List<Word> = if (timed.isNotEmpty()) {
        syllableWords(timed.joinToString("") { it.text }.trim(), timed, limit)
    } else {
        val text = line.background?.replace("(", "")?.replace(")", "")?.trim().orEmpty()
        if (text.isEmpty()) return null
        // Not timed: sung over its lead's words.
        val start = line.words?.firstOrNull()?.startMs ?: line.timeMs
        val end = line.words?.lastOrNull()?.endMs?.takeIf { it > start } ?: limit?.takeIf { it > start } ?: (start + MIN_LINE_MS)
        listOf(Word(text, start, end))
    }
    if (words.isEmpty()) return null
    return Line(
        words = words, startMs = words.first().startMs, endMs = words.last().endMs,
        agent = agent, role = LineRole.BACKGROUND, groupId = group, oppositeAligned = opposite,
    )
}
