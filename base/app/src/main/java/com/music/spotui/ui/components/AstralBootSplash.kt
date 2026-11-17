package com.music.spotui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.theme.AstralLavender
import com.music.spotui.ui.theme.AstralMidnight
import com.music.spotui.ui.theme.AstralNavy
import com.music.spotui.ui.theme.AstralTrailLight
import com.music.spotui.ui.theme.AstralTrailMid
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private data class SparkleSpec(
    val tThreshold: Float,
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
)

private val Sparkles = listOf(
    SparkleSpec(0.18f, 0.21f, 0.23f, 2.2f),
    SparkleSpec(0.32f, 0.28f, 0.34f, 3.0f),
    SparkleSpec(0.45f, 0.36f, 0.31f, 2.0f),
    SparkleSpec(0.58f, 0.42f, 0.43f, 2.8f),
    SparkleSpec(0.72f, 0.38f, 0.47f, 2.4f),
    SparkleSpec(0.84f, 0.56f, 0.44f, 2.6f),
)

/**
 * Full-screen falling-star boot animation shown once on cold start while the app
 * composes underneath, fading out smoothly after ~1050 ms.
 */
@Composable
fun AstralBootSplash(onFinished: () -> Unit) {
    val starProgress = remember { Animatable(0f) }
    val starRotation = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                starProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 720,
                        easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
                    ),
                )
            }
            launch {
                starRotation.animateTo(
                    targetValue = 180f,
                    animationSpec = tween(durationMillis = 1050, easing = LinearEasing),
                )
            }
            launch {
                delay(420)
                wordmarkAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360),
                )
            }
            launch {
                delay(1050)
                overlayAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300),
                )
                onFinished()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(
                Brush.verticalGradient(
                    colors = listOf(AstralMidnight, AstralNavy),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = starProgress.value
            if (progress <= 0f) return@Canvas

            val w = size.width
            val h = size.height

            // Primary arc: top-left sweeping down into the center-bottom emblem point.
            val primaryPath = Path().apply {
                moveTo(w * 0.14f, h * 0.16f)
                quadraticTo(w * 0.34f, h * 0.42f, w * 0.50f, h * 0.48f)
            }
            val primaryMeasure = PathMeasure().apply { setPath(primaryPath, false) }
            val primaryLen = primaryMeasure.length

            val trailStart = (progress - 0.58f).coerceAtLeast(0f)
            val drawnPrimary = Path()
            primaryMeasure.getSegment(
                startDistance = primaryLen * trailStart,
                stopDistance = primaryLen * progress,
                destination = drawnPrimary,
                startWithMoveTo = true,
            )

            // Outer glow + crisp primary trail.
            drawPath(
                path = drawnPrimary,
                color = AstralTrailLight.copy(alpha = 0.26f),
                style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = drawnPrimary,
                color = AstralTrailLight,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
            )

            // Secondary curved trail slightly trailing and offset from the primary arc.
            val secondaryPath = Path().apply {
                moveTo(w * 0.11f, h * 0.21f)
                quadraticTo(w * 0.31f, h * 0.46f, w * 0.50f, h * 0.48f)
            }
            val secondaryMeasure = PathMeasure().apply { setPath(secondaryPath, false) }
            val secondaryLen = secondaryMeasure.length
            val secondaryStop = (progress * 0.96f).coerceIn(0f, 1f)
            val secondaryStart = (secondaryStop - 0.48f).coerceAtLeast(0f)
            val drawnSecondary = Path()
            secondaryMeasure.getSegment(
                startDistance = secondaryLen * secondaryStart,
                stopDistance = secondaryLen * secondaryStop,
                destination = drawnSecondary,
                startWithMoveTo = true,
            )
            drawPath(
                path = drawnSecondary,
                color = AstralTrailMid.copy(alpha = 0.78f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Lavender sparkle dots twinkling around the arc.
            for (sparkle in Sparkles) {
                val age = ((progress - sparkle.tThreshold) / 0.35f).coerceIn(0f, 1f)
                if (age > 0f) {
                    val twinkle = sin(age * Math.PI).toFloat().coerceIn(0.25f, 1f)
                    val center = Offset(w * sparkle.xFraction, h * sparkle.yFraction)
                    val radius = sparkle.radiusDp.dp.toPx() * twinkle
                    drawCircle(
                        color = AstralLavender.copy(alpha = 0.85f * twinkle),
                        radius = radius,
                        center = center,
                    )
                }
            }

            // Comet tip: soft radial halo + rotating/pulsing 4-pointed white star.
            val headPos = primaryMeasure.getPosition(primaryLen * progress)
            val pulse = 1f + 0.14f * sin(progress * Math.PI * 3.0).toFloat()
            val haloRadius = 26.dp.toPx() * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        AstralTrailLight.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    center = headPos,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = headPos,
            )
            drawFourPointStar(
                center = headPos,
                outerRadius = 13.dp.toPx() * pulse,
                innerRadius = 3.5.dp.toPx() * pulse,
                rotationDeg = starRotation.value,
                color = Color.White,
            )
        }

        Text(
            text = "Astral",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 6.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 112.dp)
                .graphicsLayer { alpha = wordmarkAlpha.value },
        )
    }
}

private fun DrawScope.drawFourPointStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    rotationDeg: Float,
    color: Color,
) {
    val starPath = Path().apply {
        moveTo(center.x, center.y - outerRadius)
        quadraticTo(center.x, center.y, center.x + innerRadius, center.y - innerRadius)
        lineTo(center.x + outerRadius, center.y)
        quadraticTo(center.x, center.y, center.x + innerRadius, center.y + innerRadius)
        lineTo(center.x, center.y + outerRadius)
        quadraticTo(center.x, center.y, center.x - innerRadius, center.y + innerRadius)
        lineTo(center.x - outerRadius, center.y)
        quadraticTo(center.x, center.y, center.x - innerRadius, center.y - innerRadius)
        close()
    }
    rotate(degrees = rotationDeg, pivot = center) {
        drawPath(path = starPath, color = color)
    }
}
