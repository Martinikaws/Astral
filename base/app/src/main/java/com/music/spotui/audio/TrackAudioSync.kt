package com.music.spotui.audio

import android.os.SystemClock
import app.myspoti.automix.TrackAnalysis
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.automix.AutoMix
import com.music.spotui.data.DataSaver
import com.music.spotui.di.Graph
import com.music.spotui.di.LocalPlayer
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Collections
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SyncSource {
    NONE,
    SPOTIFY_ANALYSIS,
    AUTOMIX_LOCAL,
    DECORATIVE,
}

/**
 * Per-frame audio synchronization snapshot produced at 60-120fps by [TrackAudioSync.sample].
 *
 * `bands12` and `spectrum64` are cloned or safely snapshotted per frame so Compose draw passes
 * read immutable values without racing background updates.
 */
data class AudioFrame(
    val amplitude: Float,
    val instantAmplitude: Float,
    val beatPulse: Float,
    val beatFired: Boolean,
    val barPulse: Float,
    val barFired: Boolean,
    val beatPhase: Float,
    val bands12: FloatArray,
    val spectrum64: FloatArray,
    val bpm: Float,
    val hasRealData: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val integralTimeSec: Float,
    val seed: Int,
    val source: SyncSource,
) {
    val isDecorative: Boolean get() = !hasRealData

    companion object {
        val Empty = AudioFrame(
            amplitude = 0.08f,
            instantAmplitude = 0.08f,
            beatPulse = 0f,
            beatFired = false,
            barPulse = 0f,
            barFired = false,
            beatPhase = 0f,
            bands12 = FloatArray(12),
            spectrum64 = FloatArray(64),
            bpm = 0f,
            hasRealData = false,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            integralTimeSec = 0f,
            seed = 0,
            source = SyncSource.NONE,
        )
    }
}

/**
 * Synchronizes full-screen visual modules (`BackMusic`, `Visualizer`) to the currently playing
 * track's real beats, downbars, loudness envelope, and 12-band chroma pitches.
 *
 * - Spotify tracks: fetches and LRU-caches Spotify's `audio-analysis` JSON via
 *   `MyspotiRuntime.spotify.raw.get(...)`.
 * - Local / extension / downloaded / cached tracks: reads `AutoMix.analysis(uri)` immediately when
 *   available, and also resolves Spotify `audio-analysis` when a Spotify track ID exists and
 *   `AutoMix` analysis is unavailable.
 * - Playback clock: interpolates smoothly from `SongPlayer` / `LocalPlayer` position anchors using
 *   `SystemClock.elapsedRealtime()` so animations run smoothly at 60-120fps without waiting on 1Hz
 *   status ticks.
 */
object TrackAudioSync {
    private const val ANALYSIS_URL =
        "https://spclient.wg.spotify.com/audio-attributes/v1/audio-analysis/%s?format=json"
    private const val DB_MIN = -28.0
    private const val DB_MAX = -4.0
    private const val DB_SPAN = DB_MAX - DB_MIN
    private const val MOVING_AVG_WINDOW_SEC = 0.15
    private const val CURVE_STEP_SEC = 0.025
    private const val CACHE_MAX_TRACKS = 32
    private const val RETRY_TRANSIENT_MS = 20_000L
    private const val RETRY_UNAVAILABLE_MS = 300_000L
    private const val AUTOMIX_POLL_MS = 400L

    private val SPOTIFY_TRACK_ID_REGEX =
        Regex("(?:spotify:track:|open\\.spotify\\.com/track/)([A-Za-z0-9]{22})")
    private val BASE62_ID_REGEX = Regex("^[A-Za-z0-9]{22}$")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Segment(
        val start: Double,
        val duration: Double,
        val loudnessStart: Double,
        val loudnessMax: Double,
        val loudnessMaxTime: Double,
        val loudnessEnd: Double,
        val pitches: FloatArray,
    )

    private class LoudnessCurve(
        private val stepSec: Double,
        private val values: FloatArray,
        private val cumulativeIntegral: FloatArray,
    ) {
        private val maxTimeSec = if (values.isEmpty()) 0.0 else (values.size - 1) * stepSec

        fun valueAt(timeSec: Double): Float {
            if (values.isEmpty()) return 0.1f
            if (values.size == 1 || timeSec <= 0.0) return values[0]
            val f = timeSec / stepSec
            val idx = f.toInt()
            if (idx >= values.size - 1) return values[values.lastIndex]
            val frac = (f - idx).toFloat()
            return values[idx] + (values[idx + 1] - values[idx]) * frac
        }

        fun integralAt(timeSec: Double): Float {
            if (values.isEmpty() || timeSec <= 0.0) return 0f
            if (values.size == 1) return (values[0] * timeSec).toFloat()
            if (timeSec >= maxTimeSec) {
                return (cumulativeIntegral[values.lastIndex] +
                    values[values.lastIndex] * (timeSec - maxTimeSec)).toFloat()
            }
            val f = timeSec / stepSec
            val idx = f.toInt().coerceIn(0, values.size - 2)
            val dt = (timeSec - idx * stepSec).toFloat()
            val y0 = values[idx]
            val y1 = values[idx + 1]
            val yMid = y0 + (y1 - y0) * (dt / stepSec.toFloat())
            return cumulativeIntegral[idx] + 0.5f * dt * (y0 + yMid)
        }

        fun movingAverage(timeSec: Double, windowSec: Double = MOVING_AVG_WINDOW_SEC): Float {
            if (values.isEmpty()) return 0.1f
            if (values.size == 1) return values[0]
            val half = windowSec * 0.5
            val t0 = (timeSec - half).coerceAtLeast(0.0)
            val t1 = (timeSec + half).coerceAtMost(maxTimeSec)
            return if (t1 > t0 + 1e-4) {
                ((integralAt(t1) - integralAt(t0)) / (t1 - t0).toFloat()).coerceIn(0f, 1f)
            } else {
                valueAt(timeSec).coerceIn(0f, 1f)
            }
        }
    }

    private sealed interface TrackModel {
        val bpm: Float
        val beats: DoubleArray
        val bars: DoubleArray
        val seed: Int
        val source: SyncSource
    }

    private class SpotifyModel(
        override val bpm: Float,
        override val beats: DoubleArray,
        override val bars: DoubleArray,
        val segments: List<Segment>,
        val curve: LoudnessCurve,
        override val seed: Int,
    ) : TrackModel {
        override val source: SyncSource = SyncSource.SPOTIFY_ANALYSIS
    }

    private class AutoMixModel(
        val analysisRef: TrackAnalysis,
        override val bpm: Float,
        override val beats: DoubleArray,
        override val bars: DoubleArray,
        val barBass: FloatArray,
        val barLow: FloatArray,
        val barMid: FloatArray,
        val barHigh: FloatArray,
        val curve: LoudnessCurve,
        override val seed: Int,
    ) : TrackModel {
        override val source: SyncSource = SyncSource.AUTOMIX_LOCAL
    }

    private val spotifyCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, SpotifyModel>(CACHE_MAX_TRACKS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SpotifyModel>?) =
                size > CACHE_MAX_TRACKS
        },
    )

    private val retryAtMs = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 64
        },
    )

    private val inFlightIds = Collections.synchronizedSet(HashSet<String>())

    @Volatile
    private var readyObserverStarted = false

    @Volatile
    private var pendingSpotifyId: String? = null

    // Sampler / clock state (updated from UI frame loop on Main thread).
    private var activeTrackKey: String = ""
    private var activeLocalModel: AutoMixModel? = null
    private var lastModelRef: TrackModel? = null
    private var lastAutoMixCheckRealTimeMs: Long = 0L

    private var anchorPosMs: Long = 0L
    private var anchorRealTimeMs: Long = 0L
    private var lastSampleRealTimeMs: Long = 0L
    private var lastPosSec: Double = 0.0
    private var wasMoving: Boolean = false

    private var lastBeatIdx: Int = -1
    private var lastBarIdx: Int = -1
    private var beatPulse: Float = 0f
    private var barPulse: Float = 0f

    private val smoothBands12 = FloatArray(12)
    private val smoothSpectrum64 = FloatArray(64)
    private var decorativeIntegralSec: Float = 0f

    /**
     * Samples the synchronized audio frame at [nowRealTimeMs] (`SystemClock.elapsedRealtime()`).
     * Intended to be called once per display frame (60-120fps) on the main thread.
     */
    fun sample(nowRealTimeMs: Long = SystemClock.elapsedRealtime()): AudioFrame {
        ensureReadyObserver()

        val dtSec = if (lastSampleRealTimeMs == 0L) {
            1f / 60f
        } else {
            ((nowRealTimeMs - lastSampleRealTimeMs).coerceIn(1L, 64L)) / 1000f
        }
        lastSampleRealTimeMs = nowRealTimeMs

        val localActive = LocalPlayer.active.value && !SongPlayer.routing
        val localTrack = if (localActive) LocalPlayer.current() else null
        val nowPlaying = SongPlayer.nowPlaying.value
        val adPlaying = SongPlayer.adPlaying
        val isEpisode = SongPlayer.isEpisode ||
            (localTrack != null && localTrack.uri.toString().startsWith("episode:"))
        val localSpotifyId = localTrack?.let { LocalPlayer.spotifyIdOf(it) }.orEmpty()
        val trackUri = nowPlaying.uri.ifBlank {
            if (localSpotifyId.isNotBlank()) "spotify:track:$localSpotifyId"
            else localTrack?.uri?.toString().orEmpty()
        }
        val title = Graph.currentSongState.title.value
        val artist = Graph.currentSongState.singer.value
        val hasTrack = !adPlaying && (trackUri.isNotBlank() || title.isNotBlank())

        if (!hasTrack) {
            resetForTrack("")
            decayBuffers(dtSec)
            return AudioFrame.Empty.copy(
                bands12 = smoothBands12.clone(),
                spectrum64 = smoothSpectrum64.clone(),
            )
        }

        val trackKey = trackUri.ifBlank { "$title|$artist" }
        val spotifyTrackId = if (isEpisode) null else extractSpotifyTrackId(trackUri, localSpotifyId)

        if (trackKey != activeTrackKey) {
            resetForTrack(trackKey)
            anchorPosMs = SongPlayer.getCurrentPosition().coerceAtLeast(0L)
            anchorRealTimeMs = nowRealTimeMs
            lastPosSec = anchorPosMs / 1000.0
        }

        // Resolve local AutoMix analysis (cached in memory once resolved).
        if (localTrack != null) {
            val cachedLocal = activeLocalModel
            val needPoll = cachedLocal == null &&
                (lastAutoMixCheckRealTimeMs == 0L || (nowRealTimeMs - lastAutoMixCheckRealTimeMs) >= AUTOMIX_POLL_MS)
            if (cachedLocal == null && needPoll) {
                lastAutoMixCheckRealTimeMs = nowRealTimeMs
                AutoMix.analysis(localTrack.uri)?.let { an ->
                    activeLocalModel = buildAutoMixModel(an, trackKey)
                }
            }
        } else {
            activeLocalModel = null
        }

        // Fetch Spotify audio-analysis if we don't have local AutoMix data yet (or for Spotify tracks).
        val spotifyModel = if (spotifyTrackId != null) {
            val cached = synchronized(spotifyCache) { spotifyCache[spotifyTrackId] }
            if (cached == null && activeLocalModel == null) {
                maybeFetchSpotifyAnalysis(spotifyTrackId, nowRealTimeMs)
            }
            cached
        } else {
            null
        }

        // Prefer local AutoMix analysis when playing a local/extension file (matches exact decoded PCM timing);
        // otherwise use Spotify's audio-analysis.
        val model: TrackModel? = activeLocalModel ?: spotifyModel

        val isPlaying = SongPlayer.isPlaying()
        val isMoving = SongPlayer.positionMoving()
        val durationMs = SongPlayer.getDuration().coerceAtLeast(0L)
        val rawPosMs = SongPlayer.getCurrentPosition().coerceAtLeast(0L)
        val playbackRate = if (localActive) LocalPlayer.rate().coerceIn(0.25f, 3f) else 1f

        // Smooth 60-120fps clock interpolation anchored to SongPlayer/LocalPlayer position.
        val effectivePosMs: Long = if (!isMoving) {
            anchorPosMs = rawPosMs
            anchorRealTimeMs = nowRealTimeMs
            rawPosMs
        } else {
            if (!wasMoving) {
                anchorPosMs = rawPosMs
                anchorRealTimeMs = nowRealTimeMs
            }
            val elapsedMs = ((nowRealTimeMs - anchorRealTimeMs).coerceAtLeast(0L) * playbackRate).toLong()
            val predicted = anchorPosMs + elapsedMs
            val diff = rawPosMs - predicted
            when {
                abs(diff) > 140L -> {
                    // Explicit seek or initial player jump: re-anchor immediately.
                    anchorPosMs = rawPosMs
                    anchorRealTimeMs = nowRealTimeMs
                    rawPosMs
                }
                diff > 28L -> {
                    // Gently pull forward without creating backwards stutter.
                    val nudged = predicted + (diff * 0.25).toLong()
                    anchorPosMs = nudged
                    anchorRealTimeMs = nowRealTimeMs
                    nudged
                }
                diff < -45L -> {
                    // Smooth slight drift without bouncing beats.
                    val nudged = predicted + (diff * 0.18).toLong()
                    anchorPosMs = nudged
                    anchorRealTimeMs = nowRealTimeMs
                    nudged
                }
                else -> predicted
            }
        }.let { pos ->
            if (durationMs > 0L) pos.coerceIn(0L, durationMs) else pos.coerceAtLeast(0L)
        }
        wasMoving = isMoving

        val posSec = effectivePosMs / 1000.0
        val jumped = abs(posSec - lastPosSec) >= 0.6 || posSec < lastPosSec - 0.05 || lastModelRef !== model
        lastModelRef = model

        if (model == null) {
            lastPosSec = posSec
            return sampleDecorative(
                trackKey = trackKey,
                isPlaying = isPlaying,
                isMoving = isMoving,
                positionMs = effectivePosMs,
                durationMs = durationMs,
                dtSec = dtSec,
                nowRealTimeMs = nowRealTimeMs,
            )
        }

        // Beats & downbars tracking.
        val beats = model.beats
        val bars = model.bars
        val beatIdx = findFloorIndex(beats, posSec)
        val barIdx = findFloorIndex(bars, posSec)

        val defaultBeatPeriod = if (model.bpm > 20f) 60.0 / model.bpm else 0.5
        val beatPeriodSec = when {
            beatIdx >= 0 && beatIdx + 1 < beats.size ->
                (beats[beatIdx + 1] - beats[beatIdx]).coerceIn(0.2, 2.0)
            beatIdx > 0 ->
                (beats[beatIdx] - beats[beatIdx - 1]).coerceIn(0.2, 2.0)
            else -> defaultBeatPeriod.coerceIn(0.2, 2.0)
        }

        val beatPhase: Float = if (beatIdx >= 0) {
            val bStart = beats[beatIdx]
            val bEnd = if (beatIdx + 1 < beats.size) beats[beatIdx + 1] else (bStart + beatPeriodSec)
            val span = (bEnd - bStart).coerceAtLeast(0.08)
            ((posSec - bStart) / span).toFloat().coerceIn(0f, 1f)
        } else if (beats.isNotEmpty() && beats[0] > 0.0) {
            (posSec / beats[0]).toFloat().coerceIn(0f, 1f)
        } else {
            ((posSec % beatPeriodSec) / beatPeriodSec).toFloat().coerceIn(0f, 1f)
        }

        var beatFired = false
        var barFired = false
        if (jumped) {
            // Reposition indices silently after a seek without triggering ripple bursts.
            lastBeatIdx = beatIdx
            lastBarIdx = barIdx
        } else if (isMoving) {
            if (beatIdx > lastBeatIdx && beatIdx >= 0) {
                beatFired = true
                beatPulse = 1.0f
                lastBeatIdx = beatIdx
            }
            if (barIdx > lastBarIdx && barIdx >= 0) {
                barFired = true
                barPulse = 1.0f
                lastBarIdx = barIdx
            }
        }

        if (!beatFired) {
            val beatDecayTau = (beatPeriodSec.toFloat() * 0.34f).coerceIn(0.11f, 0.28f)
            beatPulse *= exp(-dtSec / beatDecayTau)
            if (beatPulse < 0.002f) beatPulse = 0f
        }
        if (!barFired) {
            val barDecayTau = (beatPeriodSec.toFloat() * 0.65f).coerceIn(0.18f, 0.52f)
            barPulse *= exp(-dtSec / barDecayTau)
            if (barPulse < 0.002f) barPulse = 0f
        }

        lastPosSec = posSec

        val movingAmp: Float
        val instantAmp: Float
        val integralSec: Float

        when (model) {
            is SpotifyModel -> {
                movingAmp = model.curve.movingAverage(posSec)
                instantAmp = sampleSegmentAmplitude(model.segments, posSec)
                integralSec = model.curve.integralAt(posSec)
                updateSpotifyBands(model.segments, posSec, instantAmp, dtSec, isMoving)
            }

            is AutoMixModel -> {
                movingAmp = model.curve.movingAverage(posSec)
                instantAmp = (movingAmp * 0.72f + beatPulse * 0.28f * movingAmp).coerceIn(0f, 1f)
                integralSec = model.curve.integralAt(posSec)
                updateAutoMixBands(model, posSec, beatPhase, instantAmp, dtSec, isMoving)
            }
        }

        val effectiveAmp = if (isPlaying) movingAmp else (movingAmp * 0.38f).coerceAtLeast(0.05f)

        return AudioFrame(
            amplitude = effectiveAmp,
            instantAmplitude = if (isPlaying) instantAmp else (instantAmp * 0.38f).coerceAtLeast(0.05f),
            beatPulse = if (isPlaying) beatPulse else beatPulse * 0.4f,
            beatFired = beatFired && isPlaying,
            barPulse = if (isPlaying) barPulse else barPulse * 0.4f,
            barFired = barFired && isPlaying,
            beatPhase = beatPhase,
            bands12 = smoothBands12.clone(),
            spectrum64 = smoothSpectrum64.clone(),
            bpm = model.bpm,
            hasRealData = true,
            isPlaying = isPlaying,
            positionMs = effectivePosMs,
            durationMs = durationMs,
            integralTimeSec = integralSec,
            seed = model.seed,
            source = model.source,
        )
    }

    private fun resetForTrack(newKey: String) {
        activeTrackKey = newKey
        activeLocalModel = null
        lastModelRef = null
        lastAutoMixCheckRealTimeMs = 0L
        lastBeatIdx = -1
        lastBarIdx = -1
        beatPulse = 0f
        barPulse = 0f
        lastPosSec = 0.0
        wasMoving = false
        decorativeIntegralSec = 0f
    }

    private fun decayBuffers(dtSec: Float) {
        val decay = exp(-dtSec / 0.14f)
        for (i in smoothBands12.indices) {
            smoothBands12[i] *= decay
        }
        for (i in smoothSpectrum64.indices) {
            smoothSpectrum64[i] *= decay
        }
    }

    private fun sampleDecorative(
        trackKey: String,
        isPlaying: Boolean,
        isMoving: Boolean,
        positionMs: Long,
        durationMs: Long,
        dtSec: Float,
        nowRealTimeMs: Long,
    ): AudioFrame {
        val tSec = nowRealTimeMs / 1000.0
        val energy = if (isPlaying && isMoving) 1.0f else 0.32f
        val breath = (0.5 + 0.5 * sin(tSec * 2.0 * PI * 0.55)).toFloat()
        val sub = (0.5 + 0.5 * sin(tSec * 2.0 * PI * 0.28 + 1.1)).toFloat()
        val amp = (0.08f + (0.14f * breath + 0.08f * sub) * energy).coerceIn(0.05f, 0.36f)
        decorativeIntegralSec += amp * dtSec

        val phase = ((tSec * 0.75) % 1.0).toFloat()
        val softPulse = (0.16f * breath * energy).coerceIn(0f, 0.25f)

        val alpha = 1f - exp(-dtSec / 0.12f)
        for (k in 0 until 12) {
            val wave = (0.5 + 0.5 * sin(tSec * 1.4 + k * 0.52)).toFloat()
            val target = (0.06f + 0.22f * wave * energy)
            smoothBands12[k] += (target - smoothBands12[k]) * alpha
        }
        deriveSpectrum64FromBands(smoothBands12, amp, softPulse, dtSec)

        return AudioFrame(
            amplitude = amp,
            instantAmplitude = amp,
            beatPulse = softPulse,
            beatFired = false,
            barPulse = softPulse * 0.6f,
            barFired = false,
            beatPhase = phase,
            bands12 = smoothBands12.clone(),
            spectrum64 = smoothSpectrum64.clone(),
            bpm = 0f,
            hasRealData = false,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            integralTimeSec = decorativeIntegralSec,
            seed = trackKey.hashCode(),
            source = SyncSource.DECORATIVE,
        )
    }

    private fun updateSpotifyBands(
        segments: List<Segment>,
        posSec: Double,
        instantAmp: Float,
        dtSec: Float,
        isMoving: Boolean,
    ) {
        if (segments.isEmpty()) {
            decayBuffers(dtSec)
            return
        }
        val idx = findSegmentIndex(segments, posSec)
        val seg = segments[idx]
        val next = segments.getOrNull(idx + 1)

        // Blend softly over the last 30ms of a segment into the next segment's chroma pitches.
        val segEnd = seg.start + seg.duration
        val blend = if (next != null && posSec > segEnd - 0.030) {
            ((posSec - (segEnd - 0.030)) / 0.030).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        val scale = if (isMoving) 1f else 0.35f
        val attackAlpha = 1f - exp(-dtSec / 0.035f)
        val releaseAlpha = 1f - exp(-dtSec / 0.11f)

        for (k in 0 until 12) {
            val pCur = seg.pitches[k]
            val pNext = next?.pitches?.get(k) ?: pCur
            val chroma = pCur + (pNext - pCur) * blend
            val env = (0.25f + 0.75f * instantAmp)
            val target = (chroma * env * scale + beatPulse * 0.14f * chroma * scale).coerceIn(0f, 1f)
            val cur = smoothBands12[k]
            val a = if (target > cur) attackAlpha else releaseAlpha
            smoothBands12[k] = cur + (target - cur) * a
        }

        deriveSpectrum64FromBands(smoothBands12, instantAmp * scale, beatPulse * scale, dtSec)
    }

    private fun updateAutoMixBands(
        model: AutoMixModel,
        posSec: Double,
        beatPhase: Float,
        instantAmp: Float,
        dtSec: Float,
        isMoving: Boolean,
    ) {
        val bars = model.bars
        val barIdx = if (bars.isNotEmpty()) findFloorIndex(bars, posSec).coerceAtLeast(0) else 0
        val bass = model.barBass.getOrElse(barIdx) { instantAmp }
        val low = model.barLow.getOrElse(barIdx) { instantAmp }
        val mid = model.barMid.getOrElse(barIdx) { instantAmp }
        val high = model.barHigh.getOrElse(barIdx) { instantAmp }

        val scale = if (isMoving) 1f else 0.35f
        val attackAlpha = 1f - exp(-dtSec / 0.035f)
        val releaseAlpha = 1f - exp(-dtSec / 0.11f)
        val beatDecay = exp(-beatPhase * 2.6f)
        val offBeat = exp(-((beatPhase - 0.5f).let { abs(it) }) * 5.0f)

        for (k in 0 until 12) {
            val baseLevel = when {
                k < 3 -> bass * (0.72f + 0.28f * beatDecay)
                k < 5 -> low * (0.78f + 0.22f * beatDecay)
                k < 8 -> mid * (0.80f + 0.20f * offBeat)
                else -> high * (0.76f + 0.24f * (0.5f + 0.5f * sin((beatPhase + k * 0.17f) * 2f * PI.toFloat())))
            }
            val harmonic = 0.85f + 0.15f * sin(posSec * (2.5 + k * 0.6) + k).toFloat()
            val target = (baseLevel * harmonic * scale + beatPulse * 0.16f * scale).coerceIn(0f, 1f)
            val cur = smoothBands12[k]
            val a = if (target > cur) attackAlpha else releaseAlpha
            smoothBands12[k] = cur + (target - cur) * a
        }

        deriveSpectrum64FromBands(smoothBands12, instantAmp * scale, beatPulse * scale, dtSec)
    }

    /**
     * Maps the 12 real chroma/frequency bands + loudness envelope into 64 smooth frequency bins
     * using raised-cosine channel interpolation (matching `SpectrumVisualizer.tsx` interpolation)
     * plus a subtle spectral contour so low bins carry bass punch and high bins carry shimmer.
     */
    private fun deriveSpectrum64FromBands(
        bands12: FloatArray,
        loudnessEnv: Float,
        pulse: Float,
        dtSec: Float,
    ) {
        val attackAlpha = 1f - exp(-dtSec / 0.030f)
        val releaseAlpha = 1f - exp(-dtSec / 0.105f)
        val envelope = (0.28f + 0.72f * loudnessEnv).coerceIn(0f, 1f)

        // Two-pass: compute interpolated raw 64 bins, then light neighbor smoothing [0.2, 0.6, 0.2].
        val raw = FloatArray(64)
        for (b in 0 until 64) {
            val norm = b / 63f
            // Map 64 channels across two folded pitch octaves so energy distributes cleanly across the spectrum.
            val fPos = norm * 11f
            val i0 = fPos.toInt().coerceIn(0, 11)
            val i1 = (i0 + 1).coerceAtMost(11)
            val frac = fPos - i0
            val mu = (1f - cos(frac * PI.toFloat())) * 0.5f
            val primary = bands12[i0] + (bands12[i1] - bands12[i0]) * mu

            // Secondary harmonic fold across the upper octave for organic spectral richness.
            val hPos = ((norm * 2.3f) % 1f) * 11f
            val h0 = hPos.toInt().coerceIn(0, 11)
            val h1 = (h0 + 1).coerceAtMost(11)
            val hFrac = hPos - h0
            val hMu = (1f - cos(hFrac * PI.toFloat())) * 0.5f
            val harmonic = bands12[h0] + (bands12[h1] - bands12[h0]) * hMu

            val bassWeight = (1f - norm).let { it * it }
            val contour = 0.80f + 0.20f * sin(norm * PI.toFloat())
            val combined = (0.74f * primary + 0.26f * harmonic) * envelope * contour +
                pulse * 0.18f * bassWeight
            raw[b] = combined.coerceIn(0f, 1f)
        }

        for (b in 0 until 64) {
            val prev = raw[(b - 1).coerceAtLeast(0)]
            val curRaw = raw[b]
            val next = raw[(b + 1).coerceAtMost(63)]
            val target = (0.20f * prev + 0.60f * curRaw + 0.20f * next).coerceIn(0f, 1f)
            val cur = smoothSpectrum64[b]
            val a = if (target > cur) attackAlpha else releaseAlpha
            smoothSpectrum64[b] = cur + (target - cur) * a
        }
    }

    private fun ensureReadyObserver() {
        if (readyObserverStarted) return
        if (!MyspotiRuntime.isStarted) return
        readyObserverStarted = true
        scope.launch {
            MyspotiRuntime.spotify.ready
                .filter { it }
                .collectLatest {
                    val pending = pendingSpotifyId
                    if (pending != null) {
                        retryAtMs.remove(pending)
                        maybeFetchSpotifyAnalysis(pending, SystemClock.elapsedRealtime())
                    }
                }
        }
    }

    private fun maybeFetchSpotifyAnalysis(trackId: String, nowRealTimeMs: Long) {
        pendingSpotifyId = trackId
        if (!MyspotiRuntime.isStarted || !MyspotiRuntime.spotify.ready.value) return
        if (DataSaver.active) return
        val nextRetry = synchronized(retryAtMs) { retryAtMs[trackId] ?: 0L }
        if (nowRealTimeMs < nextRetry) return
        if (!inFlightIds.add(trackId)) return

        scope.launch {
            try {
                val root = try {
                    MyspotiRuntime.spotify.raw.get(ANALYSIS_URL.format(trackId)) as? JsonObject
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    retryAtMs[trackId] = SystemClock.elapsedRealtime() + RETRY_TRANSIENT_MS
                    return@launch
                }

                val parsed = root?.let { parseSpotifyAnalysis(trackId, it) }
                if (parsed != null) {
                    spotifyCache[trackId] = parsed
                    retryAtMs.remove(trackId)
                } else {
                    retryAtMs[trackId] = SystemClock.elapsedRealtime() + RETRY_UNAVAILABLE_MS
                }
            } finally {
                inFlightIds.remove(trackId)
            }
        }
    }

    private fun parseSpotifyAnalysis(trackId: String, root: JsonObject): SpotifyModel? {
        val rawSegments = root.arr("segments") ?: return null
        val segments = ArrayList<Segment>(rawSegments.size)
        for (el in rawSegments) {
            val obj = el as? JsonObject ?: continue
            val start = obj.num("start")?.takeIf { it.isFinite() && it >= 0.0 } ?: continue
            val duration = obj.num("duration")?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val lStart = obj.num("loudness_start")?.takeIf { it.isFinite() } ?: -60.0
            val lMax = obj.num("loudness_max")?.takeIf { it.isFinite() } ?: lStart
            val lMaxTime = obj.num("loudness_max_time")?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.05
            val lEnd = obj.num("loudness_end")?.takeIf { it.isFinite() } ?: 0.0
            val rawPitches = obj.arr("pitches")
            val pitches = FloatArray(12) { idx ->
                (rawPitches?.getOrNull(idx) as? JsonPrimitive)?.doubleOrNull
                    ?.takeIf { it.isFinite() }
                    ?.toFloat()
                    ?.coerceIn(0f, 1f)
                    ?: 0f
            }
            segments.add(
                Segment(
                    start = start,
                    duration = duration,
                    loudnessStart = lStart,
                    loudnessMax = lMax,
                    loudnessMaxTime = lMaxTime,
                    loudnessEnd = lEnd,
                    pitches = pitches,
                ),
            )
        }
        if (segments.isEmpty()) return null
        segments.sortBy { it.start }

        // Resolve segment end loudness: Spotify sets `loudness_end` to 0.0 on non-final segments,
        // so treat values >= -0.5 dB on non-final segments as pointing to the next segment's start.
        val resolvedSegments = ArrayList<Segment>(segments.size)
        for (i in segments.indices) {
            val s = segments[i]
            val next = segments.getOrNull(i + 1)
            val effectiveDur = when {
                s.duration > 0.005 -> s.duration
                next != null -> (next.start - s.start).coerceAtLeast(0.01)
                else -> 0.25
            }
            val effectiveEnd = when {
                s.loudnessEnd < -0.5 -> s.loudnessEnd
                next != null -> next.loudnessStart
                else -> s.loudnessStart
            }
            resolvedSegments.add(s.copy(duration = effectiveDur, loudnessEnd = effectiveEnd))
        }

        val beatsList = parseStarts(root.arr("beats"))
        val barsList = parseStarts(root.arr("bars"))

        val trackTempo = root.obj("track")?.num("tempo")?.takeIf { it.isFinite() && it > 10.0 }
        val bpm = (trackTempo ?: estimateBpmFromBeats(beatsList)).toFloat().coerceIn(0f, 300f)

        val totalDurSec = maxOf(
            root.obj("track")?.num("duration")?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0,
            resolvedSegments.last().let { it.start + it.duration },
        ).coerceAtLeast(1.0)

        val curve = buildSpotifyCurve(resolvedSegments, totalDurSec)
        val seed = root.obj("meta")?.num("timestamp")?.takeIf { it.isFinite() }?.toLong()?.toInt()
            ?: trackId.hashCode()

        return SpotifyModel(
            bpm = bpm,
            beats = beatsList,
            bars = barsList,
            segments = resolvedSegments,
            curve = curve,
            seed = seed,
        )
    }

    private fun buildSpotifyCurve(segments: List<Segment>, totalDurSec: Double): LoudnessCurve {
        val sampleCount = ((totalDurSec / CURVE_STEP_SEC).roundToInt() + 2).coerceIn(2, 60_000)
        val values = FloatArray(sampleCount)
        var segIdx = 0
        for (i in 0 until sampleCount) {
            val t = i * CURVE_STEP_SEC
            while (segIdx + 1 < segments.size && segments[segIdx + 1].start <= t) {
                segIdx++
            }
            values[i] = evaluateSegmentPerceptual(segments[segIdx], t)
        }
        val integral = FloatArray(sampleCount)
        val stepF = CURVE_STEP_SEC.toFloat()
        for (i in 1 until sampleCount) {
            integral[i] = integral[i - 1] + 0.5f * stepF * (values[i - 1] + values[i])
        }
        return LoudnessCurve(CURVE_STEP_SEC, values, integral)
    }

    private fun sampleSegmentAmplitude(segments: List<Segment>, tSec: Double): Float {
        if (segments.isEmpty()) return 0.1f
        val idx = findSegmentIndex(segments, tSec)
        return evaluateSegmentPerceptual(segments[idx], tSec)
    }

    private fun evaluateSegmentPerceptual(seg: Segment, tSec: Double): Float {
        val elapsed = (tSec - seg.start).coerceAtLeast(0.0)
        val dur = seg.duration.coerceAtLeast(0.01)
        val maxTime = seg.loudnessMaxTime.coerceIn(0.005, dur)
        val db = if (elapsed <= maxTime) {
            val frac = (elapsed / maxTime).coerceIn(0.0, 1.0)
            seg.loudnessStart + (seg.loudnessMax - seg.loudnessStart) * frac
        } else {
            val rem = (dur - maxTime).coerceAtLeast(0.005)
            val frac = ((elapsed - maxTime) / rem).coerceIn(0.0, 1.0)
            seg.loudnessMax + (seg.loudnessEnd - seg.loudnessMax) * frac
        }
        return dbToPerceptual(db)
    }

    private fun dbToPerceptual(db: Double): Float =
        ((db - DB_MIN) / DB_SPAN).toFloat().coerceIn(0f, 1f)

    private fun buildAutoMixModel(an: TrackAnalysis, trackKey: String): AutoMixModel {
        val beats = when {
            an.beats.isNotEmpty() -> an.beats.filter { it.isFinite() && it >= 0.0 }.sorted().toDoubleArray()
            an.modelBeats.isNotEmpty() -> an.modelBeats.filter { it.isFinite() && it >= 0.0 }.sorted().toDoubleArray()
            else -> DoubleArray(0)
        }
        val bars = an.barStarts.filter { it.isFinite() && it >= 0.0 }.sorted().toDoubleArray()
        val bpm = if (an.bpm.isFinite() && an.bpm > 10.0) an.bpm.toFloat() else estimateBpmFromBeats(beats).toFloat()

        val barCount = an.bars
        val (midMin, midSpan) = percentileBounds(an.barDb)
        val (bassMin, bassSpan) = percentileBounds(an.barBassDb)
        val (lowMin, lowSpan) = percentileBounds(an.barLowDb)
        val (highMin, highSpan) = percentileBounds(an.barHighDb)

        fun normalizeBar(arr: FloatArray, minDb: Double, spanDb: Double): FloatArray =
            FloatArray(barCount) { b ->
                val raw = arr.getOrNull(b)?.takeIf { it.isFinite() }?.toDouble() ?: minDb
                val dyn = ((raw - minDb) / spanDb).toFloat().coerceIn(0f, 1f)
                val perc = dbToPerceptual(raw)
                (0.55f * perc + 0.45f * dyn).coerceIn(0.04f, 1f)
            }

        val barMid = normalizeBar(an.barDb, midMin, midSpan)
        val barBass = normalizeBar(an.barBassDb, bassMin, bassSpan)
        val barLow = normalizeBar(an.barLowDb, lowMin, lowSpan)
        val barHigh = normalizeBar(an.barHighDb, highMin, highSpan)

        val durSec = (if (an.duration.isFinite() && an.duration > 1.0) an.duration else 180.0)
        val sampleCount = ((durSec / CURVE_STEP_SEC).roundToInt() + 2).coerceIn(2, 60_000)
        val beatPeriodSec = if (bpm > 10f) (60.0 / bpm).coerceIn(0.2, 2.0) else 0.5
        val decaySec = (beatPeriodSec * 0.45).coerceIn(0.10, 0.34)

        var bIdx = 0
        var btIdx = 0
        val values = FloatArray(sampleCount) { i ->
            val t = i * CURVE_STEP_SEC
            val barLevel = if (barCount > 0) {
                while (bIdx + 1 < barCount && an.barStarts[bIdx + 1] <= t) bIdx++
                if (t < an.barStarts[0]) {
                    barMid[0] * 0.55f
                } else {
                    val cur = barMid[bIdx]
                    val next = barMid[(bIdx + 1).coerceAtMost(barCount - 1)]
                    val start = an.barStarts[bIdx]
                    val end = an.barEnd(bIdx).takeIf { it.isFinite() }?.coerceAtLeast(start + 0.05) ?: (start + 2.0)
                    val frac = ((t - start) / (end - start)).toFloat().coerceIn(0f, 1f)
                    cur + (next - cur) * frac
                }
            } else {
                0.42f
            }

            val pulse = if (beats.isNotEmpty()) {
                while (btIdx + 1 < beats.size && beats[btIdx + 1] <= t) btIdx++
                if (t >= beats[0]) {
                    val dt = (t - beats[btIdx]).coerceAtLeast(0.0)
                    exp(-dt / decaySec).toFloat()
                } else {
                    0f
                }
            } else {
                0f
            }

            (0.14f + 0.58f * barLevel + 0.28f * barLevel * pulse).coerceIn(0f, 1f)
        }

        val integral = FloatArray(sampleCount)
        val stepF = CURVE_STEP_SEC.toFloat()
        for (i in 1 until sampleCount) {
            integral[i] = integral[i - 1] + 0.5f * stepF * (values[i - 1] + values[i])
        }

        val seed = an.audioKey.takeIf { it.isNotBlank() }?.hashCode() ?: trackKey.hashCode()
        return AutoMixModel(
            analysisRef = an,
            bpm = bpm,
            beats = beats,
            bars = bars,
            barBass = barBass,
            barLow = barLow,
            barMid = barMid,
            barHigh = barHigh,
            curve = LoudnessCurve(CURVE_STEP_SEC, values, integral),
            seed = seed,
        )
    }

    private fun percentileBounds(values: FloatArray): Pair<Double, Double> {
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return -32.0 to 20.0
        val p10 = finite[((finite.size - 1) * 0.10).roundToInt().coerceIn(0, finite.lastIndex)].toDouble()
        val p95 = finite[((finite.size - 1) * 0.95).roundToInt().coerceIn(0, finite.lastIndex)].toDouble()
        return p10 to (p95 - p10).coerceAtLeast(4.0)
    }

    private fun parseStarts(array: JsonArray?): DoubleArray {
        if (array == null || array.isEmpty()) return DoubleArray(0)
        val list = ArrayList<Double>(array.size)
        for (el in array) {
            val start = (el as? JsonObject)?.num("start")?.takeIf { it.isFinite() && it >= 0.0 } ?: continue
            list.add(start)
        }
        list.sort()
        return list.toDoubleArray()
    }

    private fun estimateBpmFromBeats(beats: DoubleArray): Double {
        if (beats.size < 4) return 0.0
        val diffs = DoubleArray(minOf(beats.size - 1, 64)) { i ->
            (beats[i + 1] - beats[i]).coerceIn(0.2, 2.0)
        }.sorted()
        val median = diffs[diffs.size / 2]
        return if (median > 0.05) (60.0 / median).coerceIn(30.0, 240.0) else 0.0
    }

    private fun findFloorIndex(sorted: DoubleArray, target: Double): Int {
        if (sorted.isEmpty() || target < sorted[0]) return -1
        var lo = 0
        var hi = sorted.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] <= target) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun findSegmentIndex(segments: List<Segment>, tSec: Double): Int {
        if (segments.size <= 1 || tSec <= segments[0].start) return 0
        var lo = 0
        var hi = segments.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid].start <= tSec) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun extractSpotifyTrackId(uri: String, localSpotifyId: String): String? {
        SPOTIFY_TRACK_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.let { return it }
        return localSpotifyId.takeIf { BASE62_ID_REGEX.matches(it) }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
}
