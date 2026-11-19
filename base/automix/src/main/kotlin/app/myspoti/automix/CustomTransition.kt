/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.automix

import app.myspoti.automix.segue.Clock
import app.myspoti.automix.segue.KnotForm
import app.myspoti.automix.segue.LaneCurve
import app.myspoti.automix.segue.LaneSide
import app.myspoti.automix.segue.PulseState
import app.myspoti.automix.segue.SegueDeck
import app.myspoti.automix.segue.SegueFormat
import app.myspoti.automix.segue.SeguePlan
import app.myspoti.automix.segue.SegueUnit
import app.myspoti.automix.segue.SegueValidator
import app.myspoti.automix.segue.Target
import app.myspoti.automix.segue.UnitKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

enum class TransitionPreset { AUTO, FADE, RISE, CUSTOM }

/** Normalized point on a track's volume curve across the overlap: [t] in 0f..1f, [gain] linear 0f..1f (1f = 0 dB, 0f = silent). */
data class EnvelopePoint(val t: Float, val gain: Float)

data class CustomTransition(
    val fromTrackId: String,
    val toTrackId: String,
    val exitA: Double,
    val entryB: Double,
    val overlapBars: Int = 8,
    val overlapSec: Double,
    val preset: TransitionPreset = TransitionPreset.AUTO,
    val outgoingCurve: List<EnvelopePoint> = emptyList(),
    val incomingCurve: List<EnvelopePoint> = emptyList(),
    val eqSwap: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun resolvedOutgoingCurve(autoPlan: MixPlan? = null): List<EnvelopePoint> =
        normalizeCurve(outgoingCurve).ifEmpty { defaultCurvesFor(preset, autoPlan).first }

    fun resolvedIncomingCurve(autoPlan: MixPlan? = null): List<EnvelopePoint> =
        normalizeCurve(incomingCurve).ifEmpty { defaultCurvesFor(preset, autoPlan).second }

    fun gainAAt(x: Float): Float {
        val u = if (x.isFinite()) x.coerceIn(0f, 1f) else 0f
        val valid = normalizeCurve(outgoingCurve)
        if (valid.isNotEmpty()) return evaluateCurve(valid, u)
        return when (preset) {
            TransitionPreset.FADE, TransitionPreset.AUTO, TransitionPreset.CUSTOM ->
                cos(u.toDouble() * PI / 2.0).toFloat().coerceIn(0f, 1f)
            TransitionPreset.RISE -> evaluateCurve(RISE_OUTGOING, u)
        }
    }

    fun gainBAt(x: Float): Float {
        val u = if (x.isFinite()) x.coerceIn(0f, 1f) else 0f
        val valid = normalizeCurve(incomingCurve)
        if (valid.isNotEmpty()) return evaluateCurve(valid, u)
        return when (preset) {
            TransitionPreset.FADE, TransitionPreset.AUTO, TransitionPreset.CUSTOM ->
                sin(u.toDouble() * PI / 2.0).toFloat().coerceIn(0f, 1f)
            TransitionPreset.RISE -> evaluateCurve(RISE_INCOMING, u)
        }
    }

    fun toSeguePlan(
        analysisA: TrackAnalysis? = null,
        analysisB: TrackAnalysis? = null,
        basePlan: MixPlan? = null,
    ): SeguePlan = compileToSeguePlan(this, analysisA, analysisB, basePlan)

    fun toMixPlan(
        analysisA: TrackAnalysis? = null,
        analysisB: TrackAnalysis? = null,
        basePlan: MixPlan? = null,
    ): MixPlan = compileToMixPlan(this, analysisA, analysisB, basePlan)

    companion object {
        val BAR_CHOICES = intArrayOf(1, 2, 4, 8, 12, 16)
        private val KNOT_TS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        private val RISE_OUTGOING = listOf(
            EnvelopePoint(0f, 1f),
            EnvelopePoint(0.25f, 1f),
            EnvelopePoint(0.5f, 0.95f),
            EnvelopePoint(0.75f, 0.45f),
            EnvelopePoint(1f, 0f),
        )

        private val RISE_INCOMING = listOf(
            EnvelopePoint(0f, 0f),
            EnvelopePoint(0.25f, 0.45f),
            EnvelopePoint(0.5f, 0.82f),
            EnvelopePoint(0.75f, 0.95f),
            EnvelopePoint(1f, 1f),
        )

        private fun normalizeCurve(points: List<EnvelopePoint>): List<EnvelopePoint> {
            val sorted = points.asSequence()
                .filter { it.t.isFinite() && it.gain.isFinite() }
                .map { EnvelopePoint(it.t.coerceIn(0f, 1f), it.gain.coerceIn(0f, 1f)) }
                .sortedBy { it.t }
                .toList()
            if (sorted.size <= 1) return sorted
            val dedup = ArrayList<EnvelopePoint>(sorted.size)
            for (pt in sorted) {
                if (dedup.isNotEmpty() && kotlin.math.abs(dedup.last().t - pt.t) <= 1e-6f) {
                    dedup[dedup.lastIndex] = pt
                } else {
                    dedup.add(pt)
                }
            }
            return dedup
        }

        private fun evaluateCurve(points: List<EnvelopePoint>, x: Float): Float {
            val sorted = normalizeCurve(points)
            if (sorted.isEmpty()) return 0f
            val u = if (x.isFinite()) x.coerceIn(0f, 1f) else 0f
            if (u < sorted.first().t) return sorted.first().gain
            if (u >= sorted.last().t) return sorted.last().gain
            for (i in 0 until sorted.size - 1) {
                val p0 = sorted[i]
                val p1 = sorted[i + 1]
                val dt = p1.t - p0.t
                if (dt <= 1e-6f) continue
                if (u in p0.t..p1.t) {
                    val frac = ((u - p0.t) / dt).coerceIn(0f, 1f)
                    return (p0.gain + (p1.gain - p0.gain) * frac).coerceIn(0f, 1f)
                }
            }
            return sorted.last().gain
        }

        fun defaultCurvesFor(
            preset: TransitionPreset,
            autoPlan: MixPlan? = null,
        ): Pair<List<EnvelopePoint>, List<EnvelopePoint>> = when (preset) {
            TransitionPreset.FADE, TransitionPreset.CUSTOM -> {
                val out = KNOT_TS.map { t -> EnvelopePoint(t, cos(t.toDouble() * PI / 2.0).toFloat().coerceIn(0f, 1f)) }
                val inc = KNOT_TS.map { t -> EnvelopePoint(t, sin(t.toDouble() * PI / 2.0).toFloat().coerceIn(0f, 1f)) }
                out to inc
            }
            TransitionPreset.RISE -> RISE_OUTGOING to RISE_INCOMING
            TransitionPreset.AUTO -> {
                if (autoPlan == null) {
                    defaultCurvesFor(TransitionPreset.FADE, null)
                } else {
                    val segue = autoPlan.segue
                    val outLane = segue?.lanes?.firstOrNull { it.side == LaneSide.OUTGOING && it.target == Target.LEVEL }?.let(::LaneCurve)
                    val inLane = segue?.lanes?.firstOrNull { it.side == LaneSide.INCOMING && it.target == Target.LEVEL }?.let(::LaneCurve)
                    if (outLane != null && inLane != null) {
                        val out = KNOT_TS.map { t ->
                            val u = t.toDouble() * SegueFormat.T_MAX
                            val g = if (outLane.isNull(u)) 0f else 10.0.pow(outLane.value(u) / 20.0).toFloat().coerceIn(0f, 1f)
                            EnvelopePoint(t, g)
                        }
                        val inc = KNOT_TS.map { t ->
                            val u = t.toDouble() * SegueFormat.T_MAX
                            val g = if (inLane.isNull(u)) 0f else 10.0.pow(inLane.value(u) / 20.0).toFloat().coerceIn(0f, 1f)
                            EnvelopePoint(t, g)
                        }
                        out to inc
                    } else {
                        val normA = autoPlan.matchA.takeIf { it > 1e-3 } ?: 1.0
                        val normB = autoPlan.matchB.takeIf { it > 1e-3 } ?: 1.0
                        val out = KNOT_TS.map { t ->
                            val l = autoPlan.levels(t.toDouble())
                            EnvelopePoint(t, (l.gainA / normA).toFloat().coerceIn(0f, 1f))
                        }
                        val inc = KNOT_TS.map { t ->
                            val l = autoPlan.levels(t.toDouble())
                            EnvelopePoint(t, (l.gainB / normB).toFloat().coerceIn(0f, 1f))
                        }
                        out to inc
                    }
                }
            }
        }

        /** Snap an overlap bar count (e.g. 1, 2, 4, 8, 12, 16) to seconds of A's timeline at [exitA]. */
        fun barsToSeconds(
            analysisA: TrackAnalysis?,
            exitA: Double,
            bars: Int,
            fallbackBpm: Double = 120.0,
        ): Double {
            val b = bars.coerceIn(1, 64)
            if (analysisA != null) {
                if (analysisA.barStarts.size >= 2) {
                    val barIdx = analysisA.barAt(exitA).coerceAtLeast(0)
                    if (barIdx + b < analysisA.barStarts.size) {
                        val span = analysisA.barStarts[barIdx + b] - analysisA.barStarts[barIdx]
                        if (span.isFinite() && span in (b * 0.8)..(b * 8.0)) {
                            return span
                        }
                    }
                }
                val period = analysisA.tail?.period
                    ?: analysisA.head?.period
                    ?: analysisA.bpm.takeIf { it.isFinite() && it > 30.0 }?.let { 60.0 / it }
                if (period != null && period.isFinite() && period > 0.1) {
                    return b * 4.0 * period
                }
            }
            val safeBpm = if (fallbackBpm.isFinite() && fallbackBpm > 30.0) fallbackBpm.coerceIn(40.0, 240.0) else 120.0
            return b * 4.0 * (60.0 / safeBpm)
        }

        fun secondsToNearestBars(
            analysisA: TrackAnalysis?,
            exitA: Double,
            overlapSec: Double,
            fallbackBpm: Double = 120.0,
        ): Int {
            if (!overlapSec.isFinite() || overlapSec <= 0.0) return 8
            return BAR_CHOICES.minByOrNull { choice ->
                abs(barsToSeconds(analysisA, exitA, choice, fallbackBpm) - overlapSec)
            } ?: 8
        }

        fun fromAutoPlan(
            fromTrackId: String,
            toTrackId: String,
            analysisA: TrackAnalysis?,
            analysisB: TrackAnalysis?,
            autoPlan: MixPlan?,
        ): CustomTransition {
            val curves = defaultCurvesFor(TransitionPreset.AUTO, autoPlan)
            if (autoPlan != null) {
                val bars = secondsToNearestBars(analysisA, autoPlan.exitA, autoPlan.length, analysisA?.bpm ?: 120.0)
                return CustomTransition(
                    fromTrackId = fromTrackId,
                    toTrackId = toTrackId,
                    exitA = autoPlan.exitA,
                    entryB = autoPlan.entryB,
                    overlapBars = bars,
                    overlapSec = autoPlan.length,
                    preset = TransitionPreset.AUTO,
                    outgoingCurve = curves.first,
                    incomingCurve = curves.second,
                    eqSwap = autoPlan.style != MixPlan.Style.FADE || autoPlan.filtered,
                )
            }
            val defaultBars = 8
            val endA = analysisA?.contentEnd ?: analysisA?.duration ?: 180.0
            val estOverlap = barsToSeconds(analysisA, max(0.0, endA - 16.0), defaultBars, analysisA?.bpm ?: 120.0)
            val exitA = max(analysisA?.contentStart ?: 0.0, endA - estOverlap)
            val entryB = analysisB?.contentStart ?: 0.0
            return CustomTransition(
                fromTrackId = fromTrackId,
                toTrackId = toTrackId,
                exitA = exitA,
                entryB = entryB,
                overlapBars = defaultBars,
                overlapSec = estOverlap,
                preset = TransitionPreset.AUTO,
                outgoingCurve = curves.first,
                incomingCurve = curves.second,
                eqSwap = true,
            )
        }

        fun compileToSeguePlan(
            custom: CustomTransition,
            analysisA: TrackAnalysis?,
            analysisB: TrackAnalysis?,
            basePlan: MixPlan? = null,
        ): SeguePlan {
            val (_, segue) = compileInternal(custom, analysisA, analysisB, basePlan)
            return segue
        }

        fun compileToMixPlan(
            custom: CustomTransition,
            analysisA: TrackAnalysis?,
            analysisB: TrackAnalysis?,
            basePlan: MixPlan? = null,
        ): MixPlan = compileInternal(custom, analysisA, analysisB, basePlan).first

        private fun compileInternal(
            custom: CustomTransition,
            analysisA: TrackAnalysis?,
            analysisB: TrackAnalysis?,
            basePlan: MixPlan?,
        ): Pair<MixPlan, SeguePlan> {
            val durA = analysisA?.duration?.takeIf { it.isFinite() && it > 1.0 } ?: Double.MAX_VALUE
            val durB = analysisB?.duration?.takeIf { it.isFinite() && it > 1.0 } ?: Double.MAX_VALUE
            val safeExitA = if (custom.exitA.isFinite()) custom.exitA.coerceIn(0.0, max(0.0, durA - 0.5)) else 0.0
            val safeEntryB = if (custom.entryB.isFinite()) custom.entryB.coerceIn(0.0, max(0.0, durB - 0.5)) else 0.0

            val speedB = resolveSpeedB(analysisA, analysisB, basePlan)
            val remA = if (durA < Double.MAX_VALUE) max(0.5, durA - safeExitA) else 60.0
            val remB = if (durB < Double.MAX_VALUE) max(0.5, (durB - safeEntryB) / speedB) else 60.0
            val maxOverlap = min(60.0, min(remA, remB)).coerceAtLeast(0.5)
            val rawOverlap = if (custom.overlapSec.isFinite() && custom.overlapSec > 0.0) {
                custom.overlapSec
            } else {
                barsToSeconds(analysisA, safeExitA, custom.overlapBars)
            }
            val safeOverlapSec = rawOverlap.coerceIn(0.5, maxOverlap)
            val safeBars = custom.overlapBars.coerceIn(1, 64)

            val (matchA, matchB) = resolveMatchGains(analysisA, analysisB, basePlan)

            val audioKeyA = analysisA?.audioKey?.takeIf { it.isNotBlank() } ?: "custom-out:${custom.fromTrackId.ifBlank { "a" }}"
            val audioKeyB = analysisB?.audioKey?.takeIf { it.isNotBlank() } ?: "custom-in:${custom.toTrackId.ifBlank { "b" }}"
            val gridKeyA = analysisA?.beatKey?.takeIf { it.isNotBlank() } ?: "grid-out:${custom.fromTrackId.ifBlank { "a" }}"
            val gridKeyB = analysisB?.beatKey?.takeIf { it.isNotBlank() } ?: "grid-in:${custom.toTrackId.ifBlank { "b" }}"

            val anchorAUs = (safeExitA * 1_000_000.0).roundToLong().coerceAtLeast(0L)
            val anchorBUs = (safeEntryB * 1_000_000.0).roundToLong().coerceAtLeast(0L)
            val rateBPpm = (speedB * SegueFormat.RATE_NOMINAL).roundToLong().coerceIn(500_000L, 2_000_000L)
            val lengthUs = (safeOverlapSec * 1_000_000.0).roundToLong().coerceAtLeast(100_000L)

            val isFade = custom.preset == TransitionPreset.FADE
            val bpmA = (analysisA?.tail?.bpm ?: analysisA?.bpm)?.takeIf { it.isFinite() && it > 30.0 } ?: 120.0
            val pulse = if (isFade) {
                SeguePlan.Pulse(PulseState.FREE, null, null)
            } else {
                SeguePlan.Pulse(PulseState.LOCKED, (bpmA * 1_000_000.0).roundToLong(), 0L)
            }

            val lanes = buildLanes(custom, matchA, matchB)
            val seguePlan = SeguePlan(
                origin = SeguePlan.Origin("custom:${custom.fromTrackId}->${custom.toTrackId}", custom.preset.name.lowercase()),
                outgoing = SeguePlan.Side(
                    audioKey = audioKeyA,
                    gridKey = if (isFade) null else gridKeyA,
                    anchorUs = anchorAUs,
                    ratePpm = SegueFormat.RATE_NOMINAL,
                ),
                incoming = SeguePlan.Side(
                    audioKey = audioKeyB,
                    gridKey = if (isFade) null else gridKeyB,
                    anchorUs = anchorBUs,
                    ratePpm = rateBPpm,
                ),
                span = SeguePlan.Span(
                    lengthUs = lengthUs,
                    musicalUnits = safeBars,
                    unitKind = UnitKind.BAR,
                    clock = Clock.ELAPSED,
                ),
                pulse = pulse,
                lanes = lanes,
            )

            val validSegue = seguePlan.takeIf { SegueValidator.structural(it).isEmpty() && SegueDeck.playable(it) }
            val style = if (!isFade || abs(speedB - 1.0) > 1e-4) MixPlan.Style.BLEND else MixPlan.Style.FADE
            val beats = safeBars * 4
            val mixPlan = MixPlan(
                style = style,
                exitA = safeExitA,
                entryB = safeEntryB,
                length = safeOverlapSec,
                speedB = speedB,
                beats = beats,
                swapAt = if (custom.eqSwap) 0.5 else 0.0,
                beatShare = 1.0 / max(1, beats),
                matchB = matchB,
                restoreSec = if (abs(speedB - 1.0) < 1e-4) 0.0 else 8.0,
                note = "custom (${custom.preset.name.lowercase()})",
                matchA = matchA,
                segue = validSegue,
            )
            return mixPlan to seguePlan
        }

        private fun resolveSpeedB(
            analysisA: TrackAnalysis?,
            analysisB: TrackAnalysis?,
            basePlan: MixPlan?,
        ): Double {
            if (analysisA != null && analysisB != null) {
                val bpmA = analysisA.tail?.bpm ?: analysisA.bpm
                val bpmB = analysisB.head?.bpm ?: analysisB.bpm
                if (bpmA.isFinite() && bpmB.isFinite() && bpmA > 40.0 && bpmB > 40.0) {
                    val match = MixPlanner.tempoMatch(bpmA, bpmB)
                    if (match.speed in 0.85..1.15) return match.speed
                }
            }
            if (basePlan != null && basePlan.speedB.isFinite() && basePlan.speedB in 0.85..1.15) {
                return basePlan.speedB
            }
            return 1.0
        }

        private fun resolveMatchGains(
            analysisA: TrackAnalysis?,
            analysisB: TrackAnalysis?,
            basePlan: MixPlan?,
        ): Pair<Double, Double> {
            if (analysisA != null && analysisB != null && analysisA.loudnessDb.isFinite() && analysisB.loudnessDb.isFinite()) {
                val d = analysisA.loudnessDb - analysisB.loudnessDb
                val matchB = if (d < -1.5) 10.0.pow(max(d, -6.0) / 20.0) else 1.0
                val matchA = if (d > 1.5) 10.0.pow(-min(d, 6.0) / 20.0) else 1.0
                return matchA to matchB
            }
            if (basePlan != null) {
                return basePlan.matchA.coerceIn(0.25, 1.0) to basePlan.matchB.coerceIn(0.25, 1.0)
            }
            return 1.0 to 1.0
        }

        private fun buildLanes(
            custom: CustomTransition,
            matchA: Double,
            matchB: Double,
        ): List<SeguePlan.Lane> {
            val outPositions = samplePositions(custom.outgoingCurve)
            val inPositions = samplePositions(custom.incomingCurve)

            val outKnots = outPositions.mapIndexed { idx, tInt ->
                val x = tInt.toFloat() / SegueFormat.T_MAX
                val g = custom.gainAAt(x)
                val form = if (idx == 0) KnotForm.Step else KnotForm.Ramp
                if (g <= 0.01f) {
                    SeguePlan.Knot(tInt, null, form)
                } else {
                    val scaled = (g.toDouble() * matchA).coerceAtLeast(1e-4)
                    val db = (20.0 * log10(scaled)).coerceIn(-60.0, 12.0)
                    SeguePlan.Knot(tInt, (db * SegueFormat.VALUE_SCALE).roundToLong(), form)
                }
            }

            val inKnots = inPositions.mapIndexed { idx, tInt ->
                val x = tInt.toFloat() / SegueFormat.T_MAX
                val g = custom.gainBAt(x)
                val form = if (idx == 0) KnotForm.Step else KnotForm.Ramp
                if (g <= 0.01f) {
                    SeguePlan.Knot(tInt, null, form)
                } else {
                    val scaled = (g.toDouble() * matchB).coerceAtLeast(1e-4)
                    val db = (20.0 * log10(scaled)).coerceIn(-60.0, 12.0)
                    SeguePlan.Knot(tInt, (db * SegueFormat.VALUE_SCALE).roundToLong(), form)
                }
            }

            val lanes = ArrayList<SeguePlan.Lane>(4)
            lanes += SeguePlan.Lane(LaneSide.OUTGOING, Target.LEVEL, SegueUnit.DB, outKnots)
            lanes += SeguePlan.Lane(LaneSide.INCOMING, Target.LEVEL, SegueUnit.DB, inKnots)

            if (custom.eqSwap) {
                lanes += SeguePlan.Lane(
                    side = LaneSide.OUTGOING,
                    target = Target.BASS_BAND,
                    unit = SegueUnit.DB,
                    knots = listOf(
                        dbKnot(0.0, 0.0, KnotForm.Step),
                        dbKnot(0.45, 0.0, KnotForm.Ramp),
                        dbKnot(0.65, -15.0, KnotForm.Ramp),
                        dbKnot(1.0, -18.0, KnotForm.Ramp),
                    ),
                )
                lanes += SeguePlan.Lane(
                    side = LaneSide.INCOMING,
                    target = Target.BASS_BAND,
                    unit = SegueUnit.DB,
                    knots = listOf(
                        dbKnot(0.0, -18.0, KnotForm.Step),
                        dbKnot(0.35, -15.0, KnotForm.Ramp),
                        dbKnot(0.55, 0.0, KnotForm.Ramp),
                        dbKnot(1.0, 0.0, KnotForm.Ramp),
                    ),
                )
            }
            return lanes
        }

        private fun samplePositions(userCurve: List<EnvelopePoint>): List<Int> {
            val valid = normalizeCurve(userCurve)
            val base = if (valid.isNotEmpty()) {
                valid.map { (it.t * SegueFormat.T_MAX).roundToInt() }
            } else {
                (0..16).map { it * SegueFormat.T_MAX / 16 }
            }
            return (base + listOf(0, SegueFormat.T_MAX))
                .map { it.coerceIn(0, SegueFormat.T_MAX) }
                .distinct()
                .sorted()
        }

        private fun dbKnot(x: Double, db: Double, form: KnotForm): SeguePlan.Knot = SeguePlan.Knot(
            t = (x * SegueFormat.T_MAX).roundToInt().coerceIn(0, SegueFormat.T_MAX),
            valueMilli = (db * SegueFormat.VALUE_SCALE).roundToLong(),
            form = form,
        )
    }
}
