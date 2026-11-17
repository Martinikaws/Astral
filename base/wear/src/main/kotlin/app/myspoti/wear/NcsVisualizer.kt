/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Audio-reactive NCS particle sphere ported from Konsl/spicetify-visualizer (MIT License)
 * and Auburn/FastNoiseLite (MIT License).
 */
package app.myspoti.wear

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val DOT_GRID = 44
private const val DOT_COUNT = DOT_GRID * DOT_GRID
private const val DOT_SPACING = 0.9f
private const val DOT_OFFSET = -0.45f
private const val NOISE_FREQUENCY = 4f
private const val NOISE_AMPLITUDE = 0.32f * 0.9f
private const val GAIN = 0.5f
private const val LACUNARITY = 1.5f
private const val FRACTAL_BOUNDING = 1f / 1.75f
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
 * phone. Animates strictly during active playback (`state.playing && !state.buffering`).
 */
@Composable
internal fun NcsVisualizer(
    state: RemoteState,
    modifier: Modifier = Modifier,
) {
    val curve = remember(state.vizSamples, state.vizStepMs) {
        AmplitudeCurve(state.vizSamples, state.vizStepMs)
    }
    val points = remember { FloatArray(DOT_COUNT * 2) }
    val outerGlowPaint = remember { newDotPaint() }
    val midGlowPaint = remember { newDotPaint() }
    val corePaint = remember { newDotPaint() }
    val currentState by rememberUpdatedState(state)
    val currentCurve by rememberUpdatedState(curve)

    var frameOriginNanos by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.playing, state.buffering) {
        while (state.playing && !state.buffering) {
            withFrameNanos { now ->
                if (frameOriginNanos == Long.MIN_VALUE) frameOriginNanos = now
                frameNanos = now
            }
        }
    }

    Canvas(modifier = modifier) {
        // Read frameNanos inside Canvas so active playback invalidates only the draw phase.
        val tickNanos = frameNanos
        val originNanos = frameOriginNanos
        val s = currentState
        val c = currentCurve

        val elapsedMs = if (s.playing && !s.buffering && s.atElapsedMs > 0L) {
            ((SystemClock.elapsedRealtime() - s.atElapsedMs).coerceAtLeast(0L) * s.speed).toLong()
        } else {
            0L
        }
        val progressSec = (s.positionMs + elapsedMs).coerceAtLeast(0L) / 1000f

        val uAmplitude: Float
        val uNoiseOffset: Float
        if (c.isEmpty) {
            val idleSec = if (s.playing && !s.buffering && s.atElapsedMs == 0L && s.positionMs == 0L && originNanos != Long.MIN_VALUE) {
                (tickNanos - originNanos).coerceAtLeast(0L) / 1_000_000_000f
            } else {
                progressSec
            }
            uAmplitude = if (s.playing && !s.buffering) {
                0.08f + 0.025f * sin(idleSec * 1.4f)
            } else {
                0.08f
            }
            uNoiseOffset = idleSec * 0.35f
        } else {
            uAmplitude = c.movingAverage(progressSec)
            uNoiseOffset = (0.5f * progressSec + c.integralAt(progressSec)) * 0.75f
        }

        val uSphereRadius = 0.675f + 0.225f * uAmplitude
        val uFeather = (uAmplitude + 3f) * (uAmplitude + 3f) * (45f / 1568f)
        val featherRadius = uSphereRadius - uFeather

        val centerX = size.width * 0.5f
        val centerY = size.height * 0.5f
        val minDim = size.minDimension
        val halfSize = minDim * 0.5f

        var out = 0
        for (gy in 0 until DOT_GRID) {
            val v = (gy + 0.5f) / DOT_GRID
            val nv = v * NOISE_FREQUENCY
            val baseY = v * DOT_SPACING + DOT_OFFSET
            for (gx in 0 until DOT_GRID) {
                val u = (gx + 0.5f) / DOT_GRID
                val noise = fractalNoise(s.vizSeed, u * NOISE_FREQUENCY, nv, uNoiseOffset) * NOISE_AMPLITUDE
                var dx = u * DOT_SPACING + DOT_OFFSET + noise
                var dy = baseY + noise
                val dz = (noise + 0.5f * NOISE_AMPLITUDE) * uAmplitude * 0.4f

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

                points[out++] = centerX + halfSize * dx
                points[out++] = centerY - halfSize * dy
            }
        }

        val accent = Color(s.accentColor)
        val coreDotPx = (minDim * 0.0085f).coerceAtLeast(1.8f)

        outerGlowPaint.color = accent.copy(alpha = 0.18f).toArgb()
        outerGlowPaint.strokeWidth = coreDotPx * 5.2f

        midGlowPaint.color = accent.copy(alpha = 0.42f).toArgb()
        midGlowPaint.strokeWidth = coreDotPx * 2.5f

        corePaint.color = lerp(accent, Color.White, 0.35f).toArgb()
        corePaint.strokeWidth = coreDotPx

        val native = drawContext.canvas.nativeCanvas
        native.drawPoints(points, 0, out, outerGlowPaint)
        native.drawPoints(points, 0, out, midGlowPaint)
        native.drawPoints(points, 0, out, corePaint)
    }
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

private fun gradCoord(seed: Int, px: Int, py: Int, pz: Int, dx: Float, dy: Float, dz: Float): Float {
    var hash = (seed xor px xor py xor pz) * HASH_MULTIPLIER
    hash = hash xor (hash shr 15)
    hash = hash and (63 shl 2)
    return dx * GRADIENTS_3D[hash] + dy * GRADIENTS_3D[hash or 1] + dz * GRADIENTS_3D[hash or 2]
}

private fun perlinSingle(seed: Int, x: Float, y: Float, z: Float): Float {
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

    val xf00 = lerp(gradCoord(seed, x0p, y0p, z0p, dx0, dy0, dz0), gradCoord(seed, x1p, y0p, z0p, dx1, dy0, dz0), sx)
    val xf10 = lerp(gradCoord(seed, x0p, y1p, z0p, dx0, dy1, dz0), gradCoord(seed, x1p, y1p, z0p, dx1, dy1, dz0), sx)
    val xf01 = lerp(gradCoord(seed, x0p, y0p, z1p, dx0, dy0, dz1), gradCoord(seed, x1p, y0p, z1p, dx1, dy0, dz1), sx)
    val xf11 = lerp(gradCoord(seed, x0p, y1p, z1p, dx0, dy1, dz1), gradCoord(seed, x1p, y1p, z1p, dx1, dy1, dz1), sx)

    val yf0 = lerp(xf00, xf10, sy)
    val yf1 = lerp(xf01, xf11, sy)
    return lerp(yf0, yf1, sz) * PERLIN_SCALE
}

private fun fractalNoise(seed: Int, x: Float, y: Float, z: Float): Float {
    var total = perlinSingle(seed, x, y, z) * FRACTAL_BOUNDING
    var amp = FRACTAL_BOUNDING * GAIN
    var cx = x * LACUNARITY
    var cy = y * LACUNARITY
    var cz = z * LACUNARITY
    total += perlinSingle(seed + 1, cx, cy, cz) * amp
    amp *= GAIN
    cx *= LACUNARITY
    cy *= LACUNARITY
    cz *= LACUNARITY
    total += perlinSingle(seed + 2, cx, cy, cz) * amp
    return total
}
