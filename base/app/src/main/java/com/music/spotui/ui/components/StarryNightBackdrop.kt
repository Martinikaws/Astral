package com.music.spotui.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.myspoti.extensions.UiStyle
import app.myspoti.power.EnergySettings
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.extensions.ExtensionLook
import com.music.spotui.ui.look.LocalReduceMotion
import com.music.spotui.ui.theme.StarrySkyBottom
import com.music.spotui.ui.theme.StarrySkyTop
import kotlin.math.PI
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
    if (!enabled || EnergySettings.oledBlack || ExtensionLook.color(UiStyle.BACKGROUND) != null) return

    val reduceMotion = LocalReduceMotion.current == true
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

    val cycleProgress: Float = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "starryNight")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "starryNightCycle",
        )
        animated
    }

    val skyBrush = remember {
        Brush.verticalGradient(colors = listOf(StarrySkyTop, StarrySkyBottom))
    }

    Canvas(modifier = modifier) {
        drawRect(brush = skyBrush)

        val w = size.width
        val h = size.height
        val twoPi = (2.0 * PI).toFloat()

        for (star in stars) {
            val alpha = if (reduceMotion) {
                (star.minAlpha + star.maxAlpha) * 0.5f
            } else {
                val wave = 0.5f + 0.5f * sin(twoPi * (cycleProgress * star.cycles + star.phase))
                star.minAlpha + (star.maxAlpha - star.minAlpha) * wave
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.xFraction * w, star.yFraction * h),
            )
        }

        if (!reduceMotion && shootingEnabled) {
            for (meteor in ShootingStars) {
                val endCycle = meteor.startCycle + meteor.durationCycle
                if (cycleProgress in meteor.startCycle..endCycle) {
                    val p = (cycleProgress - meteor.startCycle) / meteor.durationCycle
                    val envelope = sin(PI.toFloat() * p).coerceIn(0f, 1f)
                    val headX = (meteor.startXFraction + meteor.deltaXFraction * p) * w
                    val headY = (meteor.startYFraction + meteor.deltaYFraction * p) * h
                    val dx = meteor.deltaXFraction * w
                    val dy = meteor.deltaYFraction * h
                    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                    val tailPx = meteor.tailLengthDp.dp.toPx() * (0.4f + 0.6f * envelope)
                    val tailX = headX - (dx / len) * tailPx
                    val tailY = headY - (dy / len) * tailPx

                    val head = Offset(headX, headY)
                    val tail = Offset(tailX, tailY)
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * envelope)),
                            start = tail,
                            end = head,
                        ),
                        start = tail,
                        end = head,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f * envelope),
                        radius = 2.dp.toPx(),
                        center = head,
                    )
                }
            }
        }
    }
}
