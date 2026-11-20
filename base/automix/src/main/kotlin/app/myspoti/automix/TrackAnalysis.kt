/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.automix

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Distance of measured beats from a grid: its median and 95th percentile, microseconds. */
data class PhaseStats(val medianUs: Long, val p95Us: Long)

/**
 * What AutoMix knows of a song. Times in seconds of the song's own timeline.
 *
 * Bars are 4 beats: bar i starts at [barStarts][i]. Per bar: level (dB), low-band level (dB),
 * high-band level (dB), centre (voice-band mid over side, dB above the song's median) and novelty
 * (how much the 4 bars from here differ from the 4 before). Strong local changes can restart a
 * phrase; [phraseOffset] is the fallback when no local boundary was found.
 */
class TrackAnalysis(
    val duration: Double,
    val contentStart: Double,
    val contentEnd: Double,
    val loudnessDb: Double,
    /** 0..11 major (C = 0), 12..23 minor (A minor = 21); -1 unknown. */
    val key: Int,
    val keyStrength: Double,
    val bpm: Double,
    /** The whole song follows one grid (most produced music). */
    val steady: Boolean,
    /** Grids fitted on the song's first and last minute or so (beat-matching uses these). */
    val head: Grid?,
    val tail: Grid?,
    val beats: DoubleArray,
    val barStarts: DoubleArray,
    val barDb: FloatArray,
    val barLowDb: FloatArray,
    val barHighDb: FloatArray,
    val barCentre: FloatArray,
    val barNovelty: FloatArray,
    val phraseOffset: Int,
    val stereo: Boolean,
    /** A downbeat of the [head] and of the [tail] grid (NaN without the grid): bars there start every 4 beats from it. */
    val headDown: Double,
    val tailDown: Double,
    /** Why a start or end got no grid (for the journal), "" otherwise. */
    val note: String = "",
    /**
     * Local risk of a prominent voice-like mid band, 0..1. A cheap spectral heuristic, not a vocal
     * model: centred instruments may also score. Mono is uncertain rather than assumed voice-free.
     */
    val barVocalRisk: FloatArray = FloatArray(barStarts.size),
    /** SHA-256 of the decoded analysis signal: what identifies the audio, whatever its source. */
    val audioKey: String = "",
    /** Level (dB) per bar of the band where two bass lines mask each other. */
    val barBassDb: FloatArray = barLowDb,
    /** How far the model's beats sit from the [head] and [tail] grids; null when too few could be measured. */
    val headPhase: PhaseStats? = null,
    val tailPhase: PhaseStats? = null,
    /** How clearly the downbeats' votes chose one phase on each grid, 0..1 (0: no evidence). */
    val headMetre: Float = 0f,
    val tailMetre: Float = 0f,
    /** The beat model's own beats, seconds; empty without the model. Stretches of bars are judged on these. */
    val modelBeats: DoubleArray = DoubleArray(0),
    /** How sure the model is of a downbeat at each bar start, 0..1; empty without the model. */
    val barDownConfidence: FloatArray = FloatArray(0),
    /** [barVocalRisk] comes from the song's timed lyrics, not from the spectral estimate. Not stored: lyrics come and go. */
    val voiceKnown: Boolean = false,
    /** Raw 10 Hz amplitude envelope in 0f..1f across 0.0..[duration]; synthesized lazily when empty. */
    val rawWaveform10Hz: FloatArray = FloatArray(0),
) {
    val bars get() = barStarts.size

    /** Camelot wheel code for this song's [key], e.g. "5A", "8B", "—". */
    val camelotKey: String get() = camelotOf(key)

    /** 10 samples/sec normalized 0f..1f amplitude envelope across 0.0..[duration]. */
    val waveform10Hz: FloatArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (rawWaveform10Hz.isNotEmpty()) {
            FloatArray(rawWaveform10Hz.size) { i -> rawWaveform10Hz[i].coerceIn(0f, 1f) }
        } else {
            synthesizeWaveform10Hz()
        }
    }

    private fun synthesizeWaveform10Hz(): FloatArray {
        val count = max(1, ceil(max(0.1, duration) * WAVEFORM_HZ).toInt())
        val baseLoud = if (loudnessDb.isFinite() && loudnessDb > -80.0) loudnessDb else -18.0
        var minDb = baseLoud - 24.0
        var maxDb = baseLoud + 6.0
        if (barDb.size >= 4) {
            val sorted = barDb.filter { it.isFinite() && it > -100f }.sorted()
            if (sorted.size >= 2) {
                val p05 = sorted[((sorted.size - 1) * 0.05).toInt()].toDouble()
                val p98 = sorted[((sorted.size - 1) * 0.98).toInt()].toDouble()
                if (p98 > p05 + 3.0) {
                    minDb = min(minDb, p05 - 3.0)
                    maxDb = max(maxDb, p98 + 1.5)
                }
            }
        }
        val dbSpan = max(6.0, maxDb - minDb)
        val out = FloatArray(count)
        for (i in 0 until count) {
            val t = (i + 0.5) / WAVEFORM_HZ
            if (t < contentStart || t > contentEnd) {
                val dist = if (t < contentStart) contentStart - t else t - contentEnd
                out[i] = (0.04f * exp(-dist * 3.0).toFloat()).coerceIn(0f, 1f)
                continue
            }
            val barLevelDb = if (bars == 0) {
                baseLoud
            } else {
                val b = barAt(t)
                when {
                    b < 0 -> barDb[0].toDouble()
                    b >= bars - 1 -> barDb[bars - 1].toDouble()
                    else -> {
                        val t0 = barStarts[b]
                        val t1 = barStarts[b + 1]
                        val frac = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
                        barDb[b] + (barDb[b + 1] - barDb[b]) * frac
                    }
                }
            }
            val normBar = (0.08 + 0.77 * ((barLevelDb - minDb) / dbSpan).coerceIn(0.0, 1.0)).toFloat()
            var accent = 0f
            if (beats.isNotEmpty()) {
                var lo = 0
                var hi = beats.size - 1
                var best = -1
                while (lo <= hi) {
                    val m = (lo + hi) ushr 1
                    if (beats[m] <= t) {
                        best = m
                        lo = m + 1
                    } else {
                        hi = m - 1
                    }
                }
                if (best >= 0) {
                    val dt = t - beats[best]
                    if (dt in 0.0..0.18) {
                        val bIdx = barAt(t).coerceIn(0, max(0, bars - 1))
                        val lowWeight = if (barLowDb.isNotEmpty() && bIdx < barLowDb.size) {
                            ((barLowDb[bIdx] - minDb) / dbSpan).coerceIn(0.35, 1.0).toFloat()
                        } else {
                            0.75f
                        }
                        accent = 0.15f * lowWeight * exp(-dt / 0.05).toFloat()
                    }
                }
            }
            out[i] = (normBar + accent).coerceIn(0f, 1f)
        }
        return out
    }

    /**
     * Resamples [waveform10Hz] over [fromSec]..[toSec] into [bins] values in 0f..1f.
     * Sub-intervals outside 0.0..[duration] are padded with 0f.
     */
    fun sampleWaveform(fromSec: Double, toSec: Double, bins: Int): FloatArray {
        val n = bins.coerceAtLeast(0)
        val out = FloatArray(n)
        if (n == 0 || duration <= 0.0 || !fromSec.isFinite() || !toSec.isFinite() || toSec <= fromSec) return out
        val wf = waveform10Hz
        if (wf.isEmpty()) return out
        val span = toSec - fromSec
        for (b in 0 until n) {
            val t0 = fromSec + span * b / n
            val t1 = fromSec + span * (b + 1) / n
            if (t1 <= 0.0 || t0 >= duration) {
                out[b] = 0f
                continue
            }
            val c0 = t0.coerceIn(0.0, duration)
            val c1 = t1.coerceIn(0.0, duration)
            if (c1 <= c0) {
                out[b] = 0f
                continue
            }
            val coverage = ((c1 - c0) / (t1 - t0)).coerceIn(0.0, 1.0).toFloat()
            val s0 = c0 * WAVEFORM_HZ
            val s1 = c1 * WAVEFORM_HZ
            val value = if (s1 - s0 <= 1.0) {
                val mid = ((s0 + s1) * 0.5 - 0.5).coerceIn(0.0, (wf.size - 1).toDouble())
                val idx = mid.toInt()
                val next = min(wf.size - 1, idx + 1)
                val frac = (mid - idx).toFloat()
                wf[idx] + (wf[next] - wf[idx]) * frac
            } else {
                val i0 = s0.toInt().coerceIn(0, wf.size - 1)
                val i1 = ceil(s1).toInt().coerceIn(i0 + 1, wf.size)
                var peak = 0f
                var sumSq = 0.0
                for (i in i0 until i1) {
                    val v = wf[i]
                    if (v > peak) peak = v
                    sumSq += v * v
                }
                val rms = sqrt(sumSq / (i1 - i0)).toFloat()
                0.65f * peak + 0.35f * rms
            }
            out[b] = (value * coverage).coerceIn(0f, 1f)
        }
        return out
    }

    /** This analysis with its voices judged by [VocalRisk.learned], for a song without timed lyrics. Kept out of the stored analysis so no song is analysed again. */
    fun withLearnedVoices(): TrackAnalysis = TrackAnalysis(duration, contentStart, contentEnd, loudnessDb, key, keyStrength, bpm, steady, head, tail, beats, barStarts,
        barDb, barLowDb, barHighDb, barCentre, barNovelty, phraseOffset, stereo, headDown, tailDown, note,
        VocalRisk.learned(barVocalRisk, barDb, barLowDb, barHighDb, barCentre, barNovelty), audioKey, barBassDb,
        headPhase, tailPhase, headMetre, tailMetre, modelBeats, barDownConfidence, rawWaveform10Hz = rawWaveform10Hz)

    /** This analysis with its voices taken from [spans], seconds from the song's start where it is known to be sung. */
    fun withSung(spans: List<Pair<Double, Double>>): TrackAnalysis {
        // Timed providers can repeat a line (translations, duets) or overlap word and line timing.
        // Coverage is elapsed sung time, so overlapping annotations must never be added twice.
        val sorted = spans.asSequence()
            .filter { it.first.isFinite() && it.second.isFinite() }
            .map { max(contentStart, it.first) to min(contentEnd, it.second) }
            .filter { it.second > it.first }.sortedBy { it.first }.toList()
        val merged = ArrayList<Pair<Double, Double>>()
        for (span in sorted) {
            val last = merged.lastOrNull()
            if (last != null && span.first <= last.second) {
                merged[merged.lastIndex] = last.first to max(last.second, span.second)
            } else merged.add(span)
        }
        var firstSpan = 0
        val risk = FloatArray(bars) { i ->
            val from = barStarts[i]; val to = barEnd(i)
            while (firstSpan < merged.size && merged[firstSpan].second <= from) firstSpan++
            var sung = 0.0
            var j = firstSpan
            while (j < merged.size && merged[j].first < to) {
                val (a, b) = merged[j++]
                sung += max(0.0, min(to, b) - max(from, a))
            }
            if (to > from && sung / (to - from) >= SUNG_BAR_SHARE) SUNG_RISK else UNSUNG_RISK
        }
        return TrackAnalysis(duration, contentStart, contentEnd, loudnessDb, key, keyStrength, bpm, steady, head, tail, beats, barStarts,
            barDb, barLowDb, barHighDb, barCentre, barNovelty, phraseOffset, stereo, headDown, tailDown, note, risk, audioKey, barBassDb,
            headPhase, tailPhase, headMetre, tailMetre, modelBeats, barDownConfidence, voiceKnown = true, rawWaveform10Hz = rawWaveform10Hz)
    }
    /** Identify the grids a plan relies on: a new analysis with another grid invalidates the plan. */
    val headGridKey: String? by lazy(LazyThreadSafetyMode.PUBLICATION) { head?.let { gridKey(it, headDown) } }
    val tailGridKey: String? by lazy(LazyThreadSafetyMode.PUBLICATION) { tail?.let { gridKey(it, tailDown) } }
    /** Identifies the beats and bars a plan was placed on: another analysis of them invalidates the plan. */
    val beatKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val text = StringBuilder()
        for (t in barStarts) text.append(Math.round(t * 1e4)).append(',')
        for (t in modelBeats) text.append(Math.round(t * 1e4)).append(',')
        app.myspoti.automix.segue.SegueCodec.hex(text.toString().toByteArray())
    }
    private val sections by lazy(LazyThreadSafetyMode.PUBLICATION) { Sections.detect(barNovelty) }

    /** Strong local structural changes in bars; no named verse/chorus labels are inferred. */
    val sectionStarts: IntArray get() = sections.starts

    /** Confidence of a local structural change, 0..1 (0 away from the selected boundaries). */
    fun sectionStrength(bar: Int): Double = sections.strengths.getOrElse(bar) { 0f }.toDouble()

    /** The bar containing [t], or -1 before the first. */
    fun barAt(t: Double): Int {
        var lo = 0; var hi = bars - 1; var ans = -1
        while (lo <= hi) { val m = (lo + hi) ushr 1; if (barStarts[m] <= t) { ans = m; lo = m + 1 } else hi = m - 1 }
        return ans
    }

    fun barEnd(i: Int): Double = if (i + 1 < bars) barStarts[i + 1] else contentEnd

    /** 0 at a local phrase start, 4 at its middle; a global 8-bar offset is the fallback. */
    fun phrasePos(i: Int): Int {
        val starts = sectionStarts
        val found = starts.binarySearch(i)
        val before = if (found >= 0) found else -found - 2
        val anchor = if (before >= 0) starts[before] else phraseOffset
        return Math.floorMod(i - anchor, 8)
    }

    fun write(out: OutputStream) {
        val o = DataOutputStream(out)
        o.writeInt(VERSION)
        o.writeDouble(duration); o.writeDouble(contentStart); o.writeDouble(contentEnd); o.writeDouble(loudnessDb)
        o.writeInt(key); o.writeDouble(keyStrength); o.writeDouble(bpm); o.writeBoolean(steady)
        writeGrid(o, head); writeGrid(o, tail)
        o.writeInt(beats.size); beats.forEach(o::writeDouble)
        o.writeInt(bars); barStarts.forEach(o::writeDouble)
        for (a in listOf(barDb, barLowDb, barHighDb, barCentre, barNovelty)) a.forEach(o::writeFloat)
        o.writeInt(phraseOffset); o.writeBoolean(stereo); o.writeDouble(headDown); o.writeDouble(tailDown); o.writeUTF(note.take(2000))
        for (bar in 0 until bars) o.writeFloat(barVocalRisk.getOrElse(bar) { 0f })
        o.writeUTF(audioKey)
        for (bar in 0 until bars) o.writeFloat(barBassDb.getOrElse(bar) { -60f })
        writePhase(o, headPhase); writePhase(o, tailPhase); o.writeFloat(headMetre); o.writeFloat(tailMetre)
        o.writeInt(modelBeats.size); modelBeats.forEach(o::writeDouble)
        o.writeInt(barDownConfidence.size); barDownConfidence.forEach(o::writeFloat)
        // Only a measured waveform is stored; the bar-estimated one is rebuilt on read (never kept as measured).
        if (rawWaveform10Hz.isNotEmpty()) {
            o.writeInt(WAVEFORM_MAGIC)
            o.writeInt(rawWaveform10Hz.size)
            for (v in rawWaveform10Hz) o.writeByte((v.coerceIn(0f, 1f) * 255f).roundToInt())
        }
        o.flush()
    }

    companion object {
        /** A bar with this share of it sung counts as sung. */
        private const val SUNG_BAR_SHARE = 0.3
        private const val SUNG_RISK = 0.95f
        private const val UNSUNG_RISK = 0.02f

        const val VERSION = 4_10
        const val WAVEFORM_HZ = 10.0
        const val WAVEFORM_MAGIC = 0x57463130

        /** Camelot wheel code for [key] (0..11 major, 12..23 minor, -1 unknown), e.g. "5A", "8B", "—". */
        fun camelotOf(key: Int): String {
            if (key !in 0..23) return "—"
            val (num, major) = MixPlanner.camelot(key)
            return "$num${if (major) "B" else "A"}"
        }

        fun read(input: InputStream): TrackAnalysis? {
            val i = DataInputStream(input)
            if (i.readInt() != VERSION) return null
            val duration = i.readDouble(); val cs = i.readDouble(); val ce = i.readDouble(); val loud = i.readDouble()
            val key = i.readInt(); val ks = i.readDouble(); val bpm = i.readDouble(); val steady = i.readBoolean()
            val head = readGrid(i); val tail = readGrid(i)
            val beats = DoubleArray(i.readInt()) { i.readDouble() }
            val n = i.readInt()
            val starts = DoubleArray(n) { i.readDouble() }
            val arrays = List(5) { FloatArray(n) { i.readFloat() } }
            val phrase = i.readInt(); val stereo = i.readBoolean(); val headDown = i.readDouble(); val tailDown = i.readDouble(); val note = i.readUTF()
            val vocal = FloatArray(n) { i.readFloat() }
            val audioKey = i.readUTF()
            val bass = FloatArray(n) { i.readFloat() }
            val headPhase = readPhase(i); val tailPhase = readPhase(i); val headMetre = i.readFloat(); val tailMetre = i.readFloat()
            val modelBeats = DoubleArray(i.readInt()) { i.readDouble() }
            val barDown = FloatArray(i.readInt()) { i.readFloat() }
            val rawWaveform = try {
                val magic = i.readInt()
                val len = i.readInt()
                if (magic == WAVEFORM_MAGIC && len in 0..200_000) {
                    FloatArray(len) { i.readUnsignedByte() / 255f }
                } else {
                    FloatArray(0)
                }
            } catch (_: EOFException) {
                FloatArray(0)
            }
            return TrackAnalysis(duration, cs, ce, loud, key, ks, bpm, steady, head, tail, beats, starts,
                arrays[0], arrays[1], arrays[2], arrays[3], arrays[4], phrase, stereo, headDown, tailDown, note, vocal,
                audioKey, bass, headPhase, tailPhase, headMetre, tailMetre, modelBeats, barDown, rawWaveform10Hz = rawWaveform)
        }

        private fun gridKey(g: Grid, down: Double): String = app.myspoti.automix.segue.SegueCodec.hex(
            "${Math.round(g.first * 1e6)}|${Math.round(g.period * 1e9)}|${g.count}|${if (down.isNaN()) "-" else Math.round(down * 1e6).toString()}".toByteArray())

        private fun writePhase(o: DataOutputStream, p: PhaseStats?) {
            o.writeBoolean(p != null)
            if (p != null) { o.writeLong(p.medianUs); o.writeLong(p.p95Us) }
        }

        private fun readPhase(i: DataInputStream): PhaseStats? = if (!i.readBoolean()) null else PhaseStats(i.readLong(), i.readLong())

        private fun writeGrid(o: DataOutputStream, g: Grid?) {
            o.writeBoolean(g != null)
            if (g != null) { o.writeDouble(g.first); o.writeDouble(g.period); o.writeInt(g.count); o.writeDouble(g.steadiness); o.writeDouble(g.driftMs); o.writeDouble(g.contrast) }
        }

        private fun readGrid(i: DataInputStream): Grid? =
            if (!i.readBoolean()) null else Grid(i.readDouble(), i.readDouble(), i.readInt(), i.readDouble(), i.readDouble(), i.readDouble())
    }
}
