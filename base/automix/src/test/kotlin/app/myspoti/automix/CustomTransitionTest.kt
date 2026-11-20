/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.automix

import app.myspoti.automix.segue.LaneSide
import app.myspoti.automix.segue.SegueDeck
import app.myspoti.automix.segue.SegueFormat
import app.myspoti.automix.segue.SegueValidator
import app.myspoti.automix.segue.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CustomTransitionTest {

    private fun syntheticAnalysis(
        duration: Double = 60.0,
        bpm: Double = 120.0,
        key: Int = 0,
        loudnessDb: Double = -14.0,
        audioKey: String = "sha256-track",
        rawWaveform: FloatArray = FloatArray(0),
    ): TrackAnalysis {
        val period = 60.0 / bpm
        val beatCount = ((duration - 1.0) / period).toInt().coerceAtLeast(16)
        val beats = DoubleArray(beatCount) { 0.5 + it * period }
        val barStarts = beats.filterIndexed { index, _ -> index % 4 == 0 }.toDoubleArray()
        val bars = barStarts.size
        val grid = Grid(0.5, period, beatCount, 0.95, 5.0, 1.0)
        val barDb = FloatArray(bars) { i -> (-18f + (i % 8) * 1.2f) }
        val barLow = FloatArray(bars) { i -> (-20f + (i % 4) * 1.5f) }
        val barHigh = FloatArray(bars) { -22f }
        val barCentre = FloatArray(bars) { 0f }
        val barNovelty = FloatArray(bars) { i -> if (i % 8 == 0) 1.5f else 0.2f }
        return TrackAnalysis(
            duration = duration,
            contentStart = 0.5,
            contentEnd = duration - 0.5,
            loudnessDb = loudnessDb,
            key = key,
            keyStrength = 0.85,
            bpm = bpm,
            steady = true,
            head = grid,
            tail = grid,
            beats = beats,
            barStarts = barStarts,
            barDb = barDb,
            barLowDb = barLow,
            barHighDb = barHigh,
            barCentre = barCentre,
            barNovelty = barNovelty,
            phraseOffset = 0,
            stereo = true,
            headDown = 0.5,
            tailDown = 0.5,
            audioKey = audioKey,
            rawWaveform10Hz = rawWaveform,
        )
    }

    @Test
    fun camelotOfCoversAll24KeysAndUnknown() {
        assertEquals("—", TrackAnalysis.camelotOf(-1))
        assertEquals("—", TrackAnalysis.camelotOf(24))
        assertEquals("8B", TrackAnalysis.camelotOf(0))   // C major
        assertEquals("8A", TrackAnalysis.camelotOf(21))  // A minor
        assertEquals("9B", TrackAnalysis.camelotOf(7))   // G major
        assertEquals("5A", TrackAnalysis.camelotOf(12))  // C minor

        val seen = HashSet<String>()
        for (k in 0..23) {
            val code = TrackAnalysis.camelotOf(k)
            assertTrue("valid format for $k: $code", code.matches(Regex("([1-9]|1[0-2])[AB]")))
            seen += code
        }
        assertEquals("24 distinct Camelot codes", 24, seen.size)
        val analysis = syntheticAnalysis(key = 12)
        assertEquals("5A", analysis.camelotKey)
    }

    @Test
    fun waveform10HzSynthesizesFallbackAndRespectsRawAndResamples() {
        val raw = FloatArray(200) { i -> ((i % 20) / 19f) }
        val withRaw = syntheticAnalysis(duration = 20.0, rawWaveform = raw)
        assertEquals(200, withRaw.waveform10Hz.size)
        for (i in raw.indices) {
            assertEquals(raw[i], withRaw.waveform10Hz[i], 1e-5f)
        }

        val synth = syntheticAnalysis(duration = 32.0, rawWaveform = FloatArray(0))
        val wf = synth.waveform10Hz
        assertEquals(320, wf.size)
        assertTrue(wf.all { it in 0f..1f })
        assertTrue("audible body has non-zero energy", wf.slice(10..300).average() > 0.15)

        // Resampling inside and outside bounds
        val sampled = synth.sampleWaveform(-5.0, 37.0, 42)
        assertEquals(42, sampled.size)
        // First 5 bins (-5..0s) and last 5 bins (32..37s) are out of bounds -> 0f
        for (b in 0 until 5) assertEquals("leading pad bin $b", 0f, sampled[b], 1e-4f)
        for (b in 37 until 42) assertEquals("trailing pad bin $b", 0f, sampled[b], 1e-4f)
        assertTrue("interior bins have signal", sampled.slice(6..35).all { it > 0.01f })

        // Zoomed-in linear interpolation check
        val zoomed = withRaw.sampleWaveform(1.0, 1.5, 25)
        assertEquals(25, zoomed.size)
        assertTrue(zoomed.all { it in 0f..1f })

        assertEquals(0, synth.sampleWaveform(0.0, 10.0, 0).size)
        assertTrue(synth.sampleWaveform(10.0, 5.0, 10).all { it == 0f })
    }

    @Test
    fun trackAnalysisWriteReadRoundtripAndBackwardsCompatibleWithoutTrailer() {
        val raw = FloatArray(150) { i -> (0.1f + 0.8f * ((i % 15) / 14f)) }
        val original = syntheticAnalysis(duration = 15.0, key = 7, rawWaveform = raw)
        val bos = ByteArrayOutputStream()
        original.write(bos)
        val bytes = bos.toByteArray()

        val restored = TrackAnalysis.read(ByteArrayInputStream(bytes))
        assertNotNull(restored)
        assertEquals(original.camelotKey, restored!!.camelotKey)
        assertEquals(original.waveform10Hz.size, restored.waveform10Hz.size)
        for (i in original.waveform10Hz.indices) {
            assertEquals("quantized within 1/255 at $i", original.waveform10Hz[i], restored.waveform10Hz[i], 1f / 255f + 1e-4f)
        }

        // Strip the WF10 trailer (4-byte magic + 4-byte size + 150 bytes = 158 bytes) to simulate older v4_10 cache file
        val trailerBytes = 4 + 4 + raw.size
        val legacyBytes = bytes.copyOf(bytes.size - trailerBytes)
        val legacy = TrackAnalysis.read(ByteArrayInputStream(legacyBytes))
        assertNotNull(legacy)
        assertEquals("empty rawWaveform10Hz on legacy read", 0, legacy!!.rawWaveform10Hz.size)
        assertEquals("synthesized 10Hz waveform on legacy read", 150, legacy.waveform10Hz.size)
        assertTrue(legacy.waveform10Hz.any { it > 0.2f })
    }

    @Test
    fun writeOmitsWaveformSectionWhenNoMeasuredWaveform() {
        val estimated = syntheticAnalysis(duration = 15.0, key = 7, rawWaveform = FloatArray(0))
        val measured = syntheticAnalysis(duration = 15.0, key = 7, rawWaveform = FloatArray(150) { 0.5f })
        val a = ByteArrayOutputStream().also { estimated.write(it) }.toByteArray()
        val m = ByteArrayOutputStream().also { measured.write(it) }.toByteArray()
        assertEquals("measured adds magic + size + samples", a.size + 4 + 4 + 150, m.size)
        val restored = TrackAnalysis.read(ByteArrayInputStream(a))
        assertNotNull(restored)
        assertEquals(0, restored!!.rawWaveform10Hz.size)
        assertTrue(restored.waveform10Hz.isNotEmpty())
    }

    @Test
    fun defaultCurvesAndGainEvaluationForAllPresets() {
        val a = syntheticAnalysis(duration = 90.0, bpm = 124.0, key = 0)
        val b = syntheticAnalysis(duration = 90.0, bpm = 122.0, key = 7)
        val autoPlan = MixPlanner.plan(a, b)

        for (preset in TransitionPreset.values()) {
            val (outCurve, inCurve) = CustomTransition.defaultCurvesFor(preset, autoPlan)
            assertEquals("$preset outgoing has 5 points", 5, outCurve.size)
            assertEquals("$preset incoming has 5 points", 5, inCurve.size)
            assertEquals(0f, outCurve.first().t, 1e-6f)
            assertEquals(1f, outCurve.last().t, 1e-6f)
            assertEquals(0f, inCurve.first().t, 1e-6f)
            assertEquals(1f, inCurve.last().t, 1e-6f)
        }

        // FADE equal-power evaluation
        val fade = CustomTransition("a", "b", exitA = 60.0, entryB = 0.5, overlapBars = 8, overlapSec = 16.0, preset = TransitionPreset.FADE)
        assertEquals(1f, fade.gainAAt(0f), 1e-4f)
        assertEquals(0f, fade.gainBAt(0f), 1e-4f)
        assertEquals(cos(PI / 4).toFloat(), fade.gainAAt(0.5f), 1e-3f)
        assertEquals(sin(PI / 4).toFloat(), fade.gainBAt(0.5f), 1e-3f)
        assertEquals(0f, fade.gainAAt(1f), 1e-4f)
        assertEquals(1f, fade.gainBAt(1f), 1e-4f)

        // RISE evaluation
        val rise = fade.copy(preset = TransitionPreset.RISE)
        assertEquals(1f, rise.gainAAt(0.25f), 1e-4f)
        assertTrue("RISE outgoing holds high at midpoint", rise.gainAAt(0.5f) >= 0.9f)
        assertTrue("RISE incoming rises early at midpoint", rise.gainBAt(0.5f) >= 0.78f)
        assertEquals(0f, rise.gainAAt(1f), 1e-4f)
        assertEquals(1f, rise.gainBAt(1f), 1e-4f)

        // CUSTOM piecewise-linear curve interpolation
        val custom = fade.copy(
            preset = TransitionPreset.CUSTOM,
            outgoingCurve = listOf(EnvelopePoint(0f, 1f), EnvelopePoint(0.5f, 0.8f), EnvelopePoint(1f, 0f)),
            incomingCurve = listOf(EnvelopePoint(0f, 0f), EnvelopePoint(0.5f, 0.2f), EnvelopePoint(1f, 1f)),
        )
        assertEquals(0.9f, custom.gainAAt(0.25f), 1e-4f)
        assertEquals(0.4f, custom.gainAAt(0.75f), 1e-4f)
        assertEquals(0.1f, custom.gainBAt(0.25f), 1e-4f)
        assertEquals(0.6f, custom.gainBAt(0.75f), 1e-4f)
    }

    @Test
    fun barsToSecondsAndCompileToMixPlanPassSegueValidatorAndSegueDeckPlayable() {
        val a = syntheticAnalysis(duration = 120.0, bpm = 120.0, key = 0, audioKey = "keyA")
        val b = syntheticAnalysis(duration = 120.0, bpm = 124.0, key = 7, audioKey = "keyB")

        // At 120 BPM, 1 bar = 2.0s, 8 bars = 16.0s
        val sec8 = CustomTransition.barsToSeconds(a, exitA = 80.5, bars = 8)
        assertEquals(16.0, sec8, 0.05)
        assertEquals(8, CustomTransition.secondsToNearestBars(a, exitA = 80.5, overlapSec = 15.8))
        assertEquals(16.0, CustomTransition.barsToSeconds(null, exitA = 80.0, bars = 8, fallbackBpm = 120.0), 1e-6)

        val autoPlan = MixPlanner.plan(a, b)
        val initial = CustomTransition.fromAutoPlan("trackA", "trackB", a, b, autoPlan)
        assertEquals("trackA", initial.fromTrackId)
        assertEquals("trackB", initial.toTrackId)

        for (preset in TransitionPreset.values()) {
            for (eqSwap in listOf(true, false)) {
                val curves = CustomTransition.defaultCurvesFor(preset, autoPlan)
                val transition = CustomTransition(
                    fromTrackId = "trackA",
                    toTrackId = "trackB",
                    exitA = 88.5,
                    entryB = 2.5,
                    overlapBars = 8,
                    overlapSec = 16.0,
                    preset = preset,
                    outgoingCurve = curves.first,
                    incomingCurve = curves.second,
                    eqSwap = eqSwap,
                )
                val compiled = CustomTransition.compileToMixPlan(transition, a, b, autoPlan)
                val segue = compiled.segue
                assertNotNull("segue attached for $preset eqSwap=$eqSwap", segue)
                val violations = SegueValidator.structural(segue!!)
                assertTrue("no structural violations for $preset: $violations", violations.isEmpty())
                assertTrue("playable by SegueDeck for $preset", SegueDeck.playable(segue))
                assertEquals(120.0 / 124.0, compiled.speedB, 0.01)

                // Check endpoint silence knots when curves start/end at 0
                val outLevel = segue.lanes.first { it.side == LaneSide.OUTGOING && it.target == Target.LEVEL }
                val inLevel = segue.lanes.first { it.side == LaneSide.INCOMING && it.target == Target.LEVEL }
                assertEquals(0, outLevel.knots.first().t)
                assertEquals(SegueFormat.T_MAX, outLevel.knots.last().t)
                assertEquals(null, outLevel.knots.last().valueMilli)
                assertEquals(0, inLevel.knots.first().t)
                assertEquals(null, inLevel.knots.first().valueMilli)
                assertEquals(SegueFormat.T_MAX, inLevel.knots.last().t)
            }
        }
    }

    @Test
    fun edgeCasesHandleNaNInfiniteZeroOverlapDuplicateKnotsAndInteriorSilence() {
        val a = syntheticAnalysis(duration = 90.0, bpm = 120.0, key = 0, audioKey = "keyA")
        val b = syntheticAnalysis(duration = 90.0, bpm = 120.0, key = 0, audioKey = "keyB")

        val degenerate = CustomTransition(
            fromTrackId = "",
            toTrackId = "",
            exitA = Double.NaN,
            entryB = Double.POSITIVE_INFINITY,
            overlapBars = -4,
            overlapSec = -10.0,
            preset = TransitionPreset.CUSTOM,
            outgoingCurve = listOf(
                EnvelopePoint(Float.NaN, 0.5f),
                EnvelopePoint(0.5f, Float.POSITIVE_INFINITY),
            ),
            incomingCurve = listOf(
                EnvelopePoint(Float.NEGATIVE_INFINITY, Float.NaN),
            ),
        )

        // All-non-finite curves fall back cleanly to preset default curves, and NaN x evaluates at u = 0f
        assertTrue(degenerate.gainAAt(Float.NaN).isFinite())
        assertTrue(degenerate.gainBAt(Float.NaN).isFinite())
        assertEquals(1f, degenerate.gainAAt(Float.NaN), 1e-4f)
        assertEquals(0f, degenerate.gainBAt(Float.NaN), 1e-4f)
        assertEquals(5, degenerate.resolvedOutgoingCurve().size)
        assertEquals(5, degenerate.resolvedIncomingCurve().size)

        val compiledDegenerate = CustomTransition.compileToMixPlan(degenerate, a, b)
        assertNotNull(compiledDegenerate.segue)
        assertTrue(SegueValidator.structural(compiledDegenerate.segue!!).isEmpty())
        assertTrue(SegueDeck.playable(compiledDegenerate.segue!!))

        // Unsorted, duplicate-t, and interior gain <= 0.01f silence knots
        val interiorSilence = CustomTransition(
            fromTrackId = "trackA",
            toTrackId = "trackB",
            exitA = 60.0,
            entryB = 0.0,
            overlapBars = 4,
            overlapSec = 0.0, // zero overlap -> falls back to barsToSeconds
            preset = TransitionPreset.CUSTOM,
            outgoingCurve = listOf(
                EnvelopePoint(0.75f, 0.005f), // interior silence (<= 0.01f)
                EnvelopePoint(0f, 1f),
                EnvelopePoint(0.5f, 0.4f),
                EnvelopePoint(0.5f, 0.6f),   // duplicate t
                EnvelopePoint(Float.NaN, 0.9f),
                EnvelopePoint(1f, 0f),
            ),
            incomingCurve = listOf(
                EnvelopePoint(1f, 1f),
                EnvelopePoint(0.25f, 0.008f), // interior silence (<= 0.01f)
                EnvelopePoint(0f, 0f),
            ),
            eqSwap = true,
        )
        assertEquals(0.6f, interiorSilence.gainAAt(0.5f), 1e-4f)
        val plan = CustomTransition.compileToMixPlan(interiorSilence, a, b)
        val segue = plan.segue!!
        assertTrue(SegueValidator.structural(segue).isEmpty())
        assertTrue(SegueDeck.playable(segue))
        assertEquals(8.0, plan.length, 0.1)

        val outLevel = segue.lanes.first { it.side == LaneSide.OUTGOING && it.target == Target.LEVEL }
        val inLevel = segue.lanes.first { it.side == LaneSide.INCOMING && it.target == Target.LEVEL }
        val outKnot75 = outLevel.knots.first { it.t == 750_000 }
        val inKnot25 = inLevel.knots.first { it.t == 250_000 }
        assertEquals("outgoing interior gain <= 0.01f emits null silence knot", null, outKnot75.valueMilli)
        assertEquals("incoming interior gain <= 0.01f emits null silence knot", null, inKnot25.valueMilli)
    }
}
