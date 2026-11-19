// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, app/src/main/java/com/tx24/spicyplayer/lyrics/spicy/canvas/kawarp/BackgroundMotion.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.canvas.kawarp

import kotlin.math.exp
import kotlin.math.max

/**
 * The music-driven clock behind the moving background. The time the warp is drawn at is
 * integrated every frame from the playback position, at a flow rate set by the song's energy:
 *
 * - **Energy**: the loudness envelope of the analysis segments, measured against the track's own
 *   quiet and loud parts (not absolute dB, which puts every modern master at the top) and
 *   smoothed over a couple of seconds. A chorus visibly flows faster than its verse.
 * - **Breathing**: energy also zooms the field in a little, so loud passages swell.
 *
 * Pure arithmetic: no Android, nothing allocated per frame.
 */
object BackgroundMotionTuning {
    /** How strongly the background moves while playing: scales the flow rate and the zoom. */
    const val MOTION_INTENSITY = 1.5f

    /** Flow rate (warp time per second) at the track's quietest and loudest. */
    const val RATE_QUIET = 0.66f
    const val RATE_LOUD = 2.76f

    /** Flow rate while paused. Never scaled by [MOTION_INTENSITY]. */
    const val PAUSED_RATE = 0.1f

    /** Flow rate with no analysis to follow (local files, podcasts, a failed fetch). */
    const val RATE_UNANALYZED = 1.44f

    /** Energy assumed with no analysis; the real one eases away from it once it arrives. */
    const val NEUTRAL_ENERGY = 0.35f

    /** Energy smoothing: rises over about a second, falls over about three (seconds). */
    const val ENERGY_ATTACK = 1.1f
    const val ENERGY_RELEASE = 2.8f

    /** How far ahead of the position the envelope is read, so the ramp lands on the change (seconds). */
    const val ENERGY_LEAD = 0.6f

    /** Percentiles of segment loudness that map to energy 0 and 1. */
    const val ENERGY_LOW_PERCENTILE = 0.1f
    const val ENERGY_HIGH_PERCENTILE = 0.95f

    /** Narrowest loudness window used for that mapping, so a flat track doesn't turn noise into motion (dB). */
    const val MIN_ENERGY_RANGE_DB = 6f

    /** Zoom added at full energy. */
    const val SCALE_PER_ENERGY = 0.025f

    /** Zoom added by a full beat pulse (Astral). */
    const val SCALE_PER_BEAT = 0.012f

    /** Easing between playing and paused (time constant, seconds). */
    const val PLAY_EASE = 0.6f

    /** Easing of the music's hold on the motion, when an analysis comes or goes (seconds). */
    const val FOLLOW_EASE = 0.8f

    /** Longest frame step integrated at once, so a stalled window doesn't jump the field (seconds). */
    const val MAX_FRAME_STEP = 0.1f
}

/**
 * One track's loudness, as the clock reads it. Astral feeds it from `TrackAudioSync` (the beat and
 * loudness data the app already has for the playing song); nothing here fetches anything.
 */
interface TrackMotion {
    /** Energy 0..1 at [t] seconds into the song, unsmoothed. */
    fun energyAt(t: Float): Float

    /** A beat's pulse, 0..1: 1 on the beat, falling off after it. 0 when the beats are not known. */
    fun beatPulse(): Float = 0f
}

/** A loudness envelope, mapped against the track's own quiet and loud parts (not absolute dB). */
class EnvelopeMotion(times: DoubleArray, loudnessDb: FloatArray) : TrackMotion {
    private val envTime = times
    private val envLoudness = loudnessDb
    private val quietDb: Float
    private val loudDb: Float
    private var envCursor = 0

    init {
        require(times.size == loudnessDb.size) { "one loudness per time" }
        val levels = ArrayList<Pair<Float, Float>>()
        for (i in times.indices) {
            val span = if (i + 1 < times.size) (times[i + 1] - times[i]).toFloat() else 1f
            levels += loudnessDb[i] to span.coerceAtLeast(0.001f)
        }
        // The track's own quiet and loud, weighted by how long each level lasts.
        val quiet = weightedPercentile(levels, BackgroundMotionTuning.ENERGY_LOW_PERCENTILE)
        val loud = weightedPercentile(levels, BackgroundMotionTuning.ENERGY_HIGH_PERCENTILE)
        val mid = (quiet + loud) / 2f
        val half = max(loud - quiet, BackgroundMotionTuning.MIN_ENERGY_RANGE_DB) / 2f
        quietDb = mid - half
        loudDb = mid + half
    }

    override fun energyAt(t: Float): Float {
        val db = envelopeAt(t.toDouble()) ?: return 0f
        return ((db - quietDb) / (loudDb - quietDb)).coerceIn(0f, 1f)
    }

    private fun envelopeAt(t: Double): Float? {
        val times = envTime
        if (times.size < 2 || t < times[0] || t >= times[times.size - 1]) return null
        var i = envCursor
        // Forward playback walks the cursor; anything else (a seek, a restart) searches.
        if (i >= times.size - 1 || times[i] > t || t - times[i] > 2) {
            i = max(0, upperBound(times, t) - 1)
        } else {
            while (i < times.size - 2 && times[i + 1] <= t) i++
        }
        envCursor = i
        val span = times[i + 1] - times[i]
        val f = if (span > 0) ((t - times[i]) / span).toFloat() else 0f
        return envLoudness[i] + (envLoudness[i + 1] - envLoudness[i]) * f
    }

    private fun weightedPercentile(values: List<Pair<Float, Float>>, p: Float): Float {
        val finite = values.filter { it.first.isFinite() && it.second > 0f }.sortedBy { it.first }
        if (finite.isEmpty()) return -20f
        val total = finite.sumOf { it.second.toDouble() }
        var acc = 0.0
        for ((v, w) in finite) {
            acc += w
            if (acc >= total * p) return v
        }
        return finite.last().first
    }

    /** Index of the first element greater than [t]. */
    private fun upperBound(arr: DoubleArray, t: Double): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid] <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/**
 * The state behind the music-driven clock. Step it once per frame; [time] is what the warp is
 * drawn at and [scale] the zoom, 1 at rest. Easing of play/pause and of having an analysis
 * is per second, not per frame, so it looks the same at any refresh rate.
 */
class MotionClock {
    var time = 0f
        private set
    var scale = 1f
        private set
    var rate = BackgroundMotionTuning.RATE_UNANALYZED
        private set
    var energy = BackgroundMotionTuning.NEUTRAL_ENERGY
        private set

    /** The beat's pulse (0..1) while an analysis drives the motion, eased by play and follow. */
    var pulse = 0f
        private set

    /** 1 while playing, 0 paused; eased. */
    var play = 0f
        private set

    /** 1 while an analysis drives the motion, 0 without one; eased. */
    var follow = 0f
        private set

    /** Continues from [time], e.g. the clock this one takes over from. */
    fun seed(time: Float) {
        if (time.isFinite()) this.time = time
    }

    /** Advances by [dtSeconds] at playback position [positionSeconds]; [track] is null with no analysis. */
    fun step(dtSeconds: Float, positionSeconds: Float, playing: Boolean, track: TrackMotion?) {
        val t = BackgroundMotionTuning
        val dt = dtSeconds.coerceIn(0f, t.MAX_FRAME_STEP)
        play += ((if (playing) 1f else 0f) - play) * ease(dt, t.PLAY_EASE)
        follow += ((if (track != null) 1f else 0f) - follow) * ease(dt, t.FOLLOW_EASE)

        val target = track?.energyAt(positionSeconds + t.ENERGY_LEAD) ?: t.NEUTRAL_ENERGY
        if (dt > 0f) {
            val tau = if (target > energy) t.ENERGY_ATTACK else t.ENERGY_RELEASE
            energy += (target - energy) * ease(dt, tau)
        }

        val musicRate = t.RATE_QUIET + (t.RATE_LOUD - t.RATE_QUIET) * energy
        val playingRate = (t.RATE_UNANALYZED + (musicRate - t.RATE_UNANALYZED) * follow) * t.MOTION_INTENSITY
        rate = t.PAUSED_RATE + (playingRate - t.PAUSED_RATE) * play
        time += dt * rate

        scale = 1f + t.SCALE_PER_ENERGY * energy * t.MOTION_INTENSITY * play * follow
        // Astral: a beat adds a little swell of its own on top of the loudness breathing.
        pulse = (track?.beatPulse() ?: 0f) * play * follow
        scale += t.SCALE_PER_BEAT * pulse
    }

    private fun ease(dt: Float, tau: Float) = 1f - exp(-dt / tau)
}

/** What the background needs each frame to run the music-driven clock. */
class MotionSource(
    /** The playing song's model, or null with no analysis for it. */
    val track: () -> TrackMotion?,
    /** The playback position in seconds. */
    val positionSeconds: () -> Float,
)
