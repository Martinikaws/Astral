package com.music.spotui.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.myspoti.extensions.UiStyle
import app.myspoti.power.EnergySettings
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.extensions.ExtensionLook
import com.music.spotui.ui.look.LocalReduceMotion
import com.music.spotui.ui.navigation.PlayerSheet
import com.music.spotui.ui.theme.StarrySkyBottom
import com.music.spotui.ui.theme.StarrySkyTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class StarSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
    val cycles: Int,
    val phase: Float,
)

private data class ShootingStarSpec(
    val startCycle: Float,
    val durationCycle: Float,
    val startXFraction: Float,
    val startYFraction: Float,
    val deltaXFraction: Float,
    val deltaYFraction: Float,
    val tailLengthDp: Float,
)

private const val CYCLE_DURATION_NANOS = 24_000_000_000f
private const val TWO_PI_F = (2.0 * PI).toFloat()
private const val PI_F = PI.toFloat()

private val ShootingStars = listOf(
    ShootingStarSpec(
        startCycle = 0.08f,
        durationCycle = 0.06f,
        startXFraction = 0.82f,
        startYFraction = 0.10f,
        deltaXFraction = -0.36f,
        deltaYFraction = 0.22f,
        tailLengthDp = 72f,
    ),
    ShootingStarSpec(
        startCycle = 0.42f,
        durationCycle = 0.055f,
        startXFraction = 0.22f,
        startYFraction = 0.08f,
        deltaXFraction = 0.34f,
        deltaYFraction = 0.20f,
        tailLengthDp = 64f,
    ),
    ShootingStarSpec(
        startCycle = 0.76f,
        durationCycle = 0.06f,
        startXFraction = 0.68f,
        startYFraction = 0.24f,
        deltaXFraction = -0.32f,
        deltaYFraction = 0.19f,
        tailLengthDp = 68f,
    ),
)

/**
 * Spicetify StarryNight `[Base]` animated night-sky backdrop: vertical gradient from `#000000`
 * to `#142B44` with ~55 twinkling stars and periodic diagonal shooting stars.
 */
@Composable
fun StarryNightBackdrop(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember(ctx) { StarryNightPref.init(ctx); true }
    val enabled by StarryNightPref.enabled.collectAsState()
    val shootingEnabled by StarryNightPref.shootingStars.collectAsState()
    val energy by EnergySettings.state.collectAsStateWithLifecycle()
    val oledBlack = EnergySettings.oledBlack || energy.policy.oledBlack
    if (!enabled || oledBlack || ExtensionLook.color(UiStyle.BACKGROUND) != null) return

    val reduceMotion = LocalReduceMotion.current == true
    val saverEnabled = energy.policy.saverEnabled
    val active = !reduceMotion && !PlayerSheet.open.value && !saverEnabled
    val staticStarsOnly = reduceMotion || saverEnabled
    val targetFps = minOf(if (shootingEnabled) 30 else 15, energy.policy.progressFps.coerceIn(1, 120))

    val stars = remember {
        val rng = Random(42)
        List(55) {
            val minA = 0.20f + rng.nextFloat() * 0.25f
            val maxA = (minA + 0.30f + rng.nextFloat() * 0.30f).coerceAtMost(0.95f)
            StarSpec(
                xFraction = rng.nextFloat(),
                yFraction = rng.nextFloat() * 0.92f,
                radiusDp = 1.0f + rng.nextFloat(),
                minAlpha = minA,
                maxAlpha = maxA,
                cycles = 2 + rng.nextInt(4),
                phase = rng.nextFloat(),
            )
        }
    }

    val cycleProgressState = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, targetFps, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val cadence = FrameCadence(targetFps)
            val periodMs = 1000L / targetFps
            var prevNanos = -1L
            while (isActive) {
                val nowNanos = withFrameNanos { it }
                if (prevNanos < 0L) {
                    prevNanos = nowNanos
                } else if (cadence.shouldTick(nowNanos)) {
                    val dtNanos = (nowNanos - prevNanos).coerceIn(0L, 250_000_000L)
                    prevNanos = nowNanos
                    cycleProgressState.floatValue =
                        (cycleProgressState.floatValue + dtNanos / CYCLE_DURATION_NANOS) % 1f
                }
                delay((periodMs - 4L).coerceAtLeast(1L))
            }
        }
    }

    val staticStarsState = rememberUpdatedState(staticStarsOnly)
    val shootingEnabledState = rememberUpdatedState(shootingEnabled)

    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val skyBrush = Brush.verticalGradient(
                colors = listOf(StarrySkyTop, StarrySkyBottom),
                startY = 0f,
                endY = h,
            )

            val starCount = stars.size
            val starX = FloatArray(starCount)
            val starY = FloatArray(starCount)
            val starRadius = FloatArray(starCount)
            val starMinAlpha = FloatArray(starCount)
            val starAlphaSpan = FloatArray(starCount)
            val starMidAlpha = FloatArray(starCount)
            val starCycles = IntArray(starCount)
            val starPhase = FloatArray(starCount)
            for (i in 0 until starCount) {
                val star = stars[i]
                starX[i] = star.xFraction * w
                starY[i] = star.yFraction * h
                starRadius[i] = star.radiusDp.dp.toPx()
                starMinAlpha[i] = star.minAlpha
                starAlphaSpan[i] = star.maxAlpha - star.minAlpha
                starMidAlpha[i] = (star.minAlpha + star.maxAlpha) * 0.5f
                starCycles[i] = star.cycles
                starPhase[i] = star.phase
            }

            val meteorCount = ShootingStars.size
            val meteorStartX = FloatArray(meteorCount)
            val meteorStartY = FloatArray(meteorCount)
            val meteorDeltaX = FloatArray(meteorCount)
            val meteorDeltaY = FloatArray(meteorCount)
            val meteorDirX = FloatArray(meteorCount)
            val meteorDirY = FloatArray(meteorCount)
            val meteorTailPx = FloatArray(meteorCount)
            val meteorStrokePx = 2.dp.toPx()
            for (i in 0 until meteorCount) {
                val m = ShootingStars[i]
                meteorStartX[i] = m.startXFraction * w
                meteorStartY[i] = m.startYFraction * h
                val dx = m.deltaXFraction * w
                val dy = m.deltaYFraction * h
                val len = hypot(dx, dy).coerceAtLeast(1f)
                meteorDeltaX[i] = dx
                meteorDeltaY[i] = dy
                meteorDirX[i] = dx / len
                meteorDirY[i] = dy / len
                meteorTailPx[i] = m.tailLengthDp.dp.toPx()
            }

            onDrawBehind {
                drawRect(brush = skyBrush)

                val staticOnly = staticStarsState.value
                val cycleProgress = cycleProgressState.floatValue

                for (i in 0 until starCount) {
                    val alpha = if (staticOnly) {
                        starMidAlpha[i]
                    } else {
                        val wave = 0.5f + 0.5f * sin(TWO_PI_F * (cycleProgress * starCycles[i] + starPhase[i]))
                        starMinAlpha[i] + starAlphaSpan[i] * wave
                    }
                    drawCircle(
                        color = Color.White,
                        radius = starRadius[i],
                        center = Offset(starX[i], starY[i]),
                        alpha = alpha,
                    )
                }

                if (!staticOnly && shootingEnabledState.value) {
                    for (i in 0 until meteorCount) {
                        val m = ShootingStars[i]
                        val endCycle = m.startCycle + m.durationCycle
                        if (cycleProgress in m.startCycle..endCycle) {
                            val p = (cycleProgress - m.startCycle) / m.durationCycle
                            val envelope = sin(PI_F * p).coerceIn(0f, 1f)
                            val headX = meteorStartX[i] + meteorDeltaX[i] * p
                            val headY = meteorStartY[i] + meteorDeltaY[i] * p
                            val tailLen = meteorTailPx[i] * (0.4f + 0.6f * envelope)
                            val ux = meteorDirX[i]
                            val uy = meteorDirY[i]

                            val seg1 = Offset(headX - ux * tailLen, headY - uy * tailLen)
                            val seg2 = Offset(headX - ux * (tailLen * 0.62f), headY - uy * (tailLen * 0.62f))
                            val seg3 = Offset(headX - ux * (tailLen * 0.28f), headY - uy * (tailLen * 0.28f))
                            val head = Offset(headX, headY)

                            drawLine(
                                color = Color.White,
                                start = seg1,
                                end = seg2,
                                strokeWidth = meteorStrokePx * 0.55f,
                                cap = StrokeCap.Round,
                                alpha = 0.22f * envelope,
                            )
                            drawLine(
                                color = Color.White,
                                start = seg2,
                                end = seg3,
                                strokeWidth = meteorStrokePx * 0.80f,
                                cap = StrokeCap.Round,
                                alpha = 0.52f * envelope,
                            )
                            drawLine(
                                color = Color.White,
                                start = seg3,
                                end = head,
                                strokeWidth = meteorStrokePx,
                                cap = StrokeCap.Round,
                                alpha = 0.85f * envelope,
                            )
                            drawCircle(
                                color = Color.White,
                                radius = meteorStrokePx,
                                center = head,
                                alpha = 0.95f * envelope,
                            )
                        }
                    }
                }
            }
        },
    )
}
