/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Audio-reactive NCS particle sphere ported from Konsl/spicetify-visualizer (MIT License)
 * and Auburn/FastNoiseLite (MIT License).
 */
package app.myspoti.wear

import android.graphics.Paint
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val GRID_SIZE = 24
internal const val DOT_COUNT = GRID_SIZE * GRID_SIZE
internal const val OCTAVES = 2
internal const val NORMAL_FRAME_INTERVAL_NS = 33_333_333L
internal const val POWER_SAVE_FRAME_INTERVAL_NS = 66_666_666L
private const val FRAME_PACE_TOLERANCE_NS = 1_500_000L
private const val POWER_SAVE_CHECK_INTERVAL_NS = 1_000_000_000L

private const val DOT_SPACING = 0.9f
private const val DOT_OFFSET = -0.45f
private const val NOISE_FREQUENCY = 4f
private const val NOISE_AMPLITUDE = 0.32f * 0.9f
private const val GAIN = 0.5f
private const val LACUNARITY = 1.5f
private const val FRACTAL_BOUNDING = 1f / (1f + GAIN)
private const val PERLIN_SCALE = 0.9649214f
private const val MOVING_AVG_WINDOW_SEC = 0.15f
private const val PRIME_X = 501125321
private const val PRIME_Y = 1136930381
private const val PRIME_Z = 1720413743
private const val HASH_MULTIPLIER = 0x27d4eb2d

private val GRADIENTS_3D = floatArrayOf(
    0f, 1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f,
    1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, -1f, 0f, -1f, 0f,
    1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f, 0f,
    0f, 1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f,
    1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, -1f, 0f, -1f, 0f,
    1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f, 0f,
    0f, 1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f,
    1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, -1f, 0f, -1f, 0f,
    1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f, 0f,
    0f, 1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f,
    1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, -1f, 0f, -1f, 0f,
    1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f, 0f,
    0f, 1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f,
    1f, 0f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, -1f, 0f, -1f, 0f,
    1f, 1f, 0f, 0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f, -1f, 0f, 0f,
    1f, 1f, 0f, 0f, 0f, -1f, 1f, 0f, -1f, 1f, 0f, 0f, 0f, -1f, -1f, 0f,
)

private val OCTAVE_SCALES = FloatArray(OCTAVES) { o ->
    var s = 1f
    repeat(o) { s *= LACUNARITY }
    s
}

private val OCTAVE_AMPS = FloatArray(OCTAVES) { o ->
    var a = FRACTAL_BOUNDING
    repeat(o) { a *= GAIN }
    a
}

/** Precomputed static grid coordinates for [GRID_SIZE] across [OCTAVES] so frames do zero X/Y setup. */
private object GridCoordCache {
    val baseCoord = FloatArray(GRID_SIZE) { i -> ((i + 0.5f) / GRID_SIZE) * DOT_SPACING + DOT_OFFSET }
    val x0p = IntArray(GRID_SIZE * OCTAVES)
    val x1p = IntArray(GRID_SIZE * OCTAVES)
    val dx0 = FloatArray(GRID_SIZE * OCTAVES)
    val dx1 = FloatArray(GRID_SIZE * OCTAVES)
    val sx = FloatArray(GRID_SIZE * OCTAVES)

    val y0p = IntArray(GRID_SIZE * OCTAVES)
    val y1p = IntArray(GRID_SIZE * OCTAVES)
    val dy0 = FloatArray(GRID_SIZE * OCTAVES)
    val dy1 = FloatArray(GRID_SIZE * OCTAVES)
    val sy = FloatArray(GRID_SIZE * OCTAVES)

    init {
        for (i in 0 until GRID_SIZE) {
            val coord = ((i + 0.5f) / GRID_SIZE) * NOISE_FREQUENCY
            for (o in 0 until OCTAVES) {
                val idx = i * OCTAVES + o
                val scaled = coord * OCTAVE_SCALES[o]
                val cell = floorToInt(scaled)
                val d0 = scaled - cell
                val p0 = cell * PRIME_X
                val py0 = cell * PRIME_Y
                x0p[idx] = p0
                x1p[idx] = p0 + PRIME_X
                y0p[idx] = py0
                y1p[idx] = py0 + PRIME_Y
                dx0[idx] = d0
                dx1[idx] = d0 - 1f
                dy0[idx] = d0
                dy1[idx] = d0 - 1f
                sx[idx] = smootherStep(d0)
                sy[idx] = sx[idx]
            }
        }
    }
}

/** Pre-allocated per-frame hoisted Z and per-row hoisted Y/perm scratch buffers. */
internal class SphereNoiseScratch {
    val z0p = IntArray(OCTAVES)
    val z1p = IntArray(OCTAVES)
    val dz0 = FloatArray(OCTAVES)
    val dz1 = FloatArray(OCTAVES)
    val sz = FloatArray(OCTAVES)

    val yz00 = IntArray(OCTAVES)
    val yz10 = IntArray(OCTAVES)
    val yz01 = IntArray(OCTAVES)
    val yz11 = IntArray(OCTAVES)
}

private class PaintCache {
    var accentArgb: Int = Int.MIN_VALUE
    var minDim: Float = -1f
}

internal fun frameIntervalNanos(powerSaveMode: Boolean): Long =
    if (powerSaveMode) POWER_SAVE_FRAME_INTERVAL_NS else NORMAL_FRAME_INTERVAL_NS

private class AmplitudeCurve(samples: ByteArray, stepMs: Int) {
    private val stepSec = stepMs.coerceAtLeast(20) / 1000f
    private val amplitudes = FloatArray(samples.size) { i -> (samples[i].toInt() and 0xFF) / 255f }
    private val cumulativeIntegral = FloatArray(samples.size).also { acc ->
        for (i in 1 until amplitudes.size) {
            acc[i] = acc[i - 1] + 0.5f * stepSec * (amplitudes[i - 1] + amplitudes[i])
        }
    }
    private val maxTimeSec = if (amplitudes.isEmpty()) 0f else (amplitudes.size - 1) * stepSec
    val isEmpty: Boolean get() = amplitudes.isEmpty()

    fun amplitudeAt(timeSec: Float): Float {
        if (amplitudes.isEmpty()) return 0.08f
        if (amplitudes.size == 1 || timeSec <= 0f) return amplitudes[0]
        val f = timeSec / stepSec
        val idx = f.toInt()
        if (idx >= amplitudes.size - 1) return amplitudes[amplitudes.lastIndex]
        val frac = f - idx
        return amplitudes[idx] + (amplitudes[idx + 1] - amplitudes[idx]) * frac
    }

    fun integralAt(timeSec: Float): Float {
        if (amplitudes.isEmpty() || timeSec <= 0f) return 0f
        if (amplitudes.size == 1) return amplitudes[0] * timeSec
        if (timeSec >= maxTimeSec) {
            return cumulativeIntegral[amplitudes.lastIndex] + amplitudes[amplitudes.lastIndex] * (timeSec - maxTimeSec)
        }
        val f = timeSec / stepSec
        val idx = f.toInt().coerceIn(0, amplitudes.size - 2)
        val dt = timeSec - idx * stepSec
        val y0 = amplitudes[idx]
        val y1 = amplitudes[idx + 1]
        val yMid = y0 + (y1 - y0) * (dt / stepSec)
        return cumulativeIntegral[idx] + 0.5f * dt * (y0 + yMid)
    }

    fun movingAverage(timeSec: Float): Float {
        if (amplitudes.isEmpty()) return 0.08f
        if (amplitudes.size == 1) return amplitudes[0]
        val half = MOVING_AVG_WINDOW_SEC * 0.5f
        val t0 = (timeSec - half).coerceAtLeast(0f)
        val t1 = (timeSec + half).coerceAtMost(maxTimeSec)
        return if (t1 > t0 + 1e-4f) {
            ((integralAt(t1) - integralAt(t0)) / (t1 - t0)).coerceIn(0f, 1f)
        } else {
            amplitudeAt(timeSec).coerceIn(0f, 1f)
        }
    }
}

/**
 * Audio-reactive NCS sphere visualizer driven by precomputed waveform amplitude samples from the
 * phone. Animates strictly while resumed and actively playing (or in [demo] mode), frame-paced
 * to 30 fps (15 fps in device battery-saver mode).
 */
@Composable
internal fun NcsVisualizer(
    state: RemoteState,
    viz: VizSnapshot = VizSnapshot(),
    demo: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val curve = remember(viz.samples, viz.stepMs) {
        AmplitudeCurve(viz.samples, viz.stepMs)
    }
    val points = remember { FloatArray(DOT_COUNT * 2) }
    val scratch = remember { SphereNoiseScratch() }
    val glowPaint = remember { newDotPaint() }
    val corePaint = remember { newDotPaint() }
    val paintCache = remember { PaintCache() }

    val currentState by rememberUpdatedState(state)
    val currentViz by rememberUpdatedState(viz)
    val currentCurve by rememberUpdatedState(curve)
    val currentDemo by rememberUpdatedState(demo)

    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val frameOriginNanos = remember { mutableLongStateOf(Long.MIN_VALUE) }
    val frameNanos = remember { mutableLongStateOf(0L) }
    val running = (state.playing && !state.buffering) || demo

    LaunchedEffect(running, lifecycleOwner) {
        if (!running) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastDrawnNanos = Long.MIN_VALUE
            var lastPowerCheckNs = 0L
            var isPowerSaveMode = false
            while (isActive) {
                withFrameNanos { now ->
                    if (frameOriginNanos.longValue == Long.MIN_VALUE) {
                        frameOriginNanos.longValue = now
                    }
                    if (lastPowerCheckNs == 0L || now - lastPowerCheckNs >= POWER_SAVE_CHECK_INTERVAL_NS) {
                        isPowerSaveMode = powerManager?.isPowerSaveMode == true
                        lastPowerCheckNs = now
                    }
                    val interval = frameIntervalNanos(isPowerSaveMode)
                    if (lastDrawnNanos == Long.MIN_VALUE || now - lastDrawnNanos >= interval - FRAME_PACE_TOLERANCE_NS) {
                        lastDrawnNanos = now
                        frameNanos.longValue = now
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        // Read frameNanos inside Canvas so active playback invalidates only the draw phase.
        val tickNanos = frameNanos.longValue
        val originNanos = frameOriginNanos.longValue
        val s = currentState
        val v = currentViz
        val c = currentCurve
        val isDemo = currentDemo
        val activePlayback = s.playing && !s.buffering

        val elapsedMs = if (activePlayback && s.atElapsedMs > 0L) {
            ((SystemClock.elapsedRealtime() - s.atElapsedMs).coerceAtLeast(0L) * s.speed).toLong()
        } else {
            0L
        }
        val progressSec = (s.positionMs + elapsedMs).coerceAtLeast(0L) / 1000f

        val uAmplitude: Float
        val uNoiseOffset: Float
        if (c.isEmpty) {
            val useClock = originNanos != Long.MIN_VALUE && ((activePlayback && s.atElapsedMs == 0L && s.positionMs == 0L) || (isDemo && !activePlayback))
            val idleSec = if (useClock) {
                (tickNanos - originNanos).coerceAtLeast(0L) / 1_000_000_000f
            } else {
                progressSec
            }
            uAmplitude = if (activePlayback || isDemo) {
                0.08f + 0.025f * sin(idleSec * 1.4f)
            } else {
                0.08f
            }
            uNoiseOffset = idleSec * 0.35f
        } else {
            uAmplitude = c.movingAverage(progressSec)
            uNoiseOffset = (0.5f * progressSec + c.integralAt(progressSec)) * 0.75f
        }

        val minDim = size.minDimension
        val out = fillSpherePoints(
            seed = v.seed,
            noiseOffset = uNoiseOffset,
            amplitude = uAmplitude,
            centerX = size.width * 0.5f,
            centerY = size.height * 0.5f,
            halfSize = minDim * 0.5f,
            outPoints = points,
            scratch = scratch,
        )

        if (paintCache.accentArgb != s.accentColor || paintCache.minDim != minDim) {
            paintCache.accentArgb = s.accentColor
            paintCache.minDim = minDim
            val accent = Color(s.accentColor)
            val coreDotPx = (minDim * 0.0085f).coerceAtLeast(1.8f)

            glowPaint.color = accent.copy(alpha = 0.30f).toArgb()
            glowPaint.strokeWidth = coreDotPx * 3.8f

            corePaint.color = lerp(accent, Color.White, 0.35f).toArgb()
            corePaint.strokeWidth = coreDotPx
        }

        val native = drawContext.canvas.nativeCanvas
        native.drawPoints(points, 0, out, glowPaint)
        native.drawPoints(points, 0, out, corePaint)
    }
}

/**
 * Evaluates the [GRID_SIZE]x[GRID_SIZE] particle sphere into [outPoints], hoisting all Z setup
 * per frame and all Y/permutation bases outside the inner X loop.
 */
internal fun fillSpherePoints(
    seed: Int,
    noiseOffset: Float,
    amplitude: Float,
    centerX: Float,
    centerY: Float,
    halfSize: Float,
    outPoints: FloatArray,
    scratch: SphereNoiseScratch = SphereNoiseScratch(),
): Int {
    val uSphereRadius = 0.675f + 0.225f * amplitude
    val uFeather = (amplitude + 3f) * (amplitude + 3f) * (45f / 1568f)
    val featherRadius = uSphereRadius - uFeather

    // Hoist Z coordinates and permutation primes across the entire frame.
    for (o in 0 until OCTAVES) {
        val z = noiseOffset * OCTAVE_SCALES[o]
        val z0 = floorToInt(z)
        val dz0 = z - z0
        val z0p = z0 * PRIME_Z
        scratch.z0p[o] = z0p
        scratch.z1p[o] = z0p + PRIME_Z
        scratch.dz0[o] = dz0
        scratch.dz1[o] = dz0 - 1f
        scratch.sz[o] = smootherStep(dz0)
    }

    var out = 0
    for (gy in 0 until GRID_SIZE) {
        val baseY = GridCoordCache.baseCoord[gy]
        val rowBase = gy * OCTAVES

        // Hoist all Y coordinates and (seed, y, z) perm hashes outside the inner X loop.
        for (o in 0 until OCTAVES) {
            val yIdx = rowBase + o
            val y0p = GridCoordCache.y0p[yIdx]
            val y1p = GridCoordCache.y1p[yIdx]
            val z0p = scratch.z0p[o]
            val z1p = scratch.z1p[o]
            val s = seed + o
            scratch.yz00[o] = s xor y0p xor z0p
            scratch.yz10[o] = s xor y1p xor z0p
            scratch.yz01[o] = s xor y0p xor z1p
            scratch.yz11[o] = s xor y1p xor z1p
        }

        for (gx in 0 until GRID_SIZE) {
            val colBase = gx * OCTAVES
            var fbmTotal = 0f
            for (o in 0 until OCTAVES) {
                val xIdx = colBase + o
                val yIdx = rowBase + o
                val x0p = GridCoordCache.x0p[xIdx]
                val x1p = GridCoordCache.x1p[xIdx]
                val dx0 = GridCoordCache.dx0[xIdx]
                val dx1 = GridCoordCache.dx1[xIdx]
                val sx = GridCoordCache.sx[xIdx]

                val dy0 = GridCoordCache.dy0[yIdx]
                val dy1 = GridCoordCache.dy1[yIdx]
                val sy = GridCoordCache.sy[yIdx]

                val dz0 = scratch.dz0[o]
                val dz1 = scratch.dz1[o]
                val sz = scratch.sz[o]

                val yz00 = scratch.yz00[o]
                val yz10 = scratch.yz10[o]
                val yz01 = scratch.yz01[o]
                val yz11 = scratch.yz11[o]

                val xf00 = lerp(gradHash(yz00 xor x0p, dx0, dy0, dz0), gradHash(yz00 xor x1p, dx1, dy0, dz0), sx)
                val xf10 = lerp(gradHash(yz10 xor x0p, dx0, dy1, dz0), gradHash(yz10 xor x1p, dx1, dy1, dz0), sx)
                val xf01 = lerp(gradHash(yz01 xor x0p, dx0, dy0, dz1), gradHash(yz01 xor x1p, dx1, dy0, dz1), sx)
                val xf11 = lerp(gradHash(yz11 xor x0p, dx0, dy1, dz1), gradHash(yz11 xor x1p, dx1, dy1, dz1), sx)

                val yf0 = lerp(xf00, xf10, sy)
                val yf1 = lerp(xf01, xf11, sy)
                fbmTotal += (lerp(yf0, yf1, sz) * PERLIN_SCALE) * OCTAVE_AMPS[o]
            }

            val noise = fbmTotal * NOISE_AMPLITUDE
            var dx = GridCoordCache.baseCoord[gx] + noise
            var dy = baseY + noise
            val dz = (noise + 0.5f * NOISE_AMPLITUDE) * amplitude * 0.4f

            val rawDist = sqrt(dx * dx + dy * dy + dz * dz)
            if (rawDist > 1e-5f) {
                val inv = 1f / rawDist
                dx *= inv
                dy *= inv
                val clampedDist = min(uSphereRadius, rawDist)
                dx *= clampedDist
                dy *= clampedDist

                val featherStrength = 1f - ((clampedDist - featherRadius) / uFeather).coerceIn(0f, 1f)
                val scale = featherStrength * (uSphereRadius / clampedDist.coerceAtLeast(1e-5f) - 1f) + 1f
                dx *= scale
                dy *= scale
            }

            outPoints[out++] = centerX + halfSize * dx
            outPoints[out++] = centerY - halfSize * dy
        }
    }
    return out
}

internal fun fieldSample(seed: Int, u: Float, v: Float, noiseOffset: Float): Float =
    fbm2(seed, u * NOISE_FREQUENCY, v * NOISE_FREQUENCY, noiseOffset) * NOISE_AMPLITUDE

internal fun fbm2(seed: Int, x: Float, y: Float, z: Float): Float {
    var total = 0f
    var cx = x
    var cy = y
    var cz = z
    for (o in 0 until OCTAVES) {
        total += perlin2(seed + o, cx, cy, cz) * OCTAVE_AMPS[o]
        cx *= LACUNARITY
        cy *= LACUNARITY
        cz *= LACUNARITY
    }
    return total
}

internal fun perlin2(seed: Int, x: Float, y: Float, z: Float): Float {
    val x0 = floorToInt(x)
    val y0 = floorToInt(y)
    val z0 = floorToInt(z)

    val dx0 = x - x0
    val dy0 = y - y0
    val dz0 = z - z0
    val dx1 = dx0 - 1f
    val dy1 = dy0 - 1f
    val dz1 = dz0 - 1f

    val sx = smootherStep(dx0)
    val sy = smootherStep(dy0)
    val sz = smootherStep(dz0)

    val x0p = x0 * PRIME_X
    val y0p = y0 * PRIME_Y
    val z0p = z0 * PRIME_Z
    val x1p = x0p + PRIME_X
    val y1p = y0p + PRIME_Y
    val z1p = z0p + PRIME_Z

    val yz00 = seed xor y0p xor z0p
    val yz10 = seed xor y1p xor z0p
    val yz01 = seed xor y0p xor z1p
    val yz11 = seed xor y1p xor z1p

    val xf00 = lerp(gradHash(yz00 xor x0p, dx0, dy0, dz0), gradHash(yz00 xor x1p, dx1, dy0, dz0), sx)
    val xf10 = lerp(gradHash(yz10 xor x0p, dx0, dy1, dz0), gradHash(yz10 xor x1p, dx1, dy1, dz0), sx)
    val xf01 = lerp(gradHash(yz01 xor x0p, dx0, dy0, dz1), gradHash(yz01 xor x1p, dx1, dy0, dz1), sx)
    val xf11 = lerp(gradHash(yz11 xor x0p, dx0, dy1, dz1), gradHash(yz11 xor x1p, dx1, dy1, dz1), sx)

    val yf0 = lerp(xf00, xf10, sy)
    val yf1 = lerp(xf01, xf11, sy)
    return lerp(yf0, yf1, sz) * PERLIN_SCALE
}

private fun newDotPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}

private fun smootherStep(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

private fun floorToInt(x: Float): Int {
    val xi = x.toInt()
    return if (x < xi) xi - 1 else xi
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun gradHash(combinedSeed: Int, dx: Float, dy: Float, dz: Float): Float {
    var hash = combinedSeed * HASH_MULTIPLIER
    hash = hash xor (hash shr 15)
    hash = hash and (63 shl 2)
    return dx * GRADIENTS_3D[hash] + dy * GRADIENTS_3D[hash or 1] + dz * GRADIENTS_3D[hash or 2]
}
