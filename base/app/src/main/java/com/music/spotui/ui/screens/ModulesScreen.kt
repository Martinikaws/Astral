package com.music.spotui.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.di.Graph
import com.music.spotui.di.Palette
import com.music.spotui.di.SongPlayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ModuleMode(val label: String) {
    BackMusic("BackMusic"),
    Visualizer("Visualizer"),
}

private enum class ParticleOverlay(val label: String) {
    Stars("Stars"),
    Snow("Snow"),
    Fireflies("Fireflies"),
}

private enum class VisualizerStyle(val label: String) {
    SpectrumBars("Spectrum Bars"),
    NcsPulse("NCS Pulse"),
    Waveform("Waveform"),
}

private data class ParticleSeed(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val phaseOffset: Float,
    val drift: Float,
)

/**
 * Full-screen **Modules** tab featuring:
 * 1. **BackMusic** (`rolandyangg/backmusic_extension`) — audio-reactive backdrop, concentric sound-wave
 *    rings, radial spectrum bars, floating album art, selectable particle overlays, and quick controls.
 * 2. **Visualizer** (`Konsl/spicetify-visualizer`) — full-screen real-time visualizer with selectable
 *    Spectrum Bars, NCS Pulse, and Waveform styles plus track info overlay.
 */
@Composable
fun ModulesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val songState = Graph.currentSongState
    val nowPlaying by SongPlayer.nowPlaying
    val title by songState.title
    val artist by songState.singer
    val coverUri by songState.coverUri
    val isPlaying by songState.playingState

    var activeModule by rememberSaveable { mutableStateOf(ModuleMode.BackMusic) }
    var particleOverlay by rememberSaveable { mutableStateOf(ParticleOverlay.Stars) }
    var visualizerStyle by rememberSaveable { mutableStateOf(VisualizerStyle.SpectrumBars) }

    var rawPrimary by remember { mutableStateOf(Color(0xFF1A1633)) }
    var rawAccent by remember { mutableStateOf(Color(0xFF38BDF8)) }

    LaunchedEffect(coverUri, nowPlaying.uri) {
        if (coverUri.isBlank()) {
            rawPrimary = Color(0xFF1A1633)
            rawAccent = Color(0xFF38BDF8)
        } else {
            val palette = Palette()
            palette.extractFirstColorFromImageUrl(context, coverUri) { color ->
                rawPrimary = if (color == Palette.NEUTRAL) Color(0xFF1A1633) else color
            }
            palette.extractSecondColorFromCoverUrl(context, coverUri) { color ->
                rawAccent = if (color == Palette.NEUTRAL) Color(0xFF38BDF8) else color
            }
        }
    }

    val primaryColor by animateColorAsState(
        targetValue = rawPrimary,
        animationSpec = tween(durationMillis = 700),
        label = "modulePrimary",
    )
    val accentColor by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 700),
        label = "moduleAccent",
    )

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.16f,
        animationSpec = tween(durationMillis = 550),
        label = "playbackEnergy",
    )

    var animPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        var lastMs = 0L
        while (true) {
            withFrameMillis { frameMs ->
                if (lastMs != 0L) {
                    val dt = ((frameMs - lastMs).coerceIn(0L, 64L)) / 1000f
                    val speed = if (isPlaying) 1f else 0.22f
                    animPhase = (animPhase + dt * speed) % 10_000f
                }
                lastMs = frameMs
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C)),
    ) {
        AnimatedContent(
            targetState = activeModule,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(220)) },
            label = "moduleContent",
        ) { module ->
            when (module) {
                ModuleMode.BackMusic -> BackMusicModule(
                    title = title,
                    artist = artist,
                    coverUri = coverUri,
                    isPlaying = isPlaying,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    phase = animPhase,
                    energy = energy,
                    particleOverlay = particleOverlay,
                    onSelectParticle = { particleOverlay = it },
                )

                ModuleMode.Visualizer -> VisualizerModule(
                    title = title,
                    artist = artist,
                    coverUri = coverUri,
                    isPlaying = isPlaying,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    phase = animPhase,
                    energy = energy,
                    style = visualizerStyle,
                    onSelectStyle = { visualizerStyle = it },
                )
            }
        }

        // Translucent top pill switcher
        ModuleTopSwitcher(
            selected = activeModule,
            onSelect = { activeModule = it },
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
        )
    }
}

@Composable
private fun ModuleTopSwitcher(
    selected: ModuleMode,
    onSelect: (ModuleMode) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.30f) else Color.Transparent,
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) accentColor.copy(alpha = 0.65f) else Color.Transparent,
                            shape = RoundedCornerShape(50),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(mode) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = mode.label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BackMusicModule(
    title: String,
    artist: String,
    coverUri: String,
    isPlaying: Boolean,
    primaryColor: Color,
    accentColor: Color,
    phase: Float,
    energy: Float,
    particleOverlay: ParticleOverlay,
    onSelectParticle: (ParticleOverlay) -> Unit,
) {
    val particles = remember {
        val rng = Random(42)
        List(72) {
            ParticleSeed(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                size = 1.5f + rng.nextFloat() * 3.5f,
                speed = 0.25f + rng.nextFloat() * 0.85f,
                phaseOffset = rng.nextFloat() * (2f * PI.toFloat()),
                drift = (rng.nextFloat() - 0.5f) * 0.12f,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Living audio-reactive backdrop + rings + radial spectrum + particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h * 0.43f)
            val beat = 0.5f + 0.5f * sin(phase * 3.2f)
            val pulseRadius = (w.coerceAtMost(h) * 0.62f) * (0.85f + 0.18f * beat * energy)

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.92f),
                        Color(0xFF0A0A12),
                        Color(0xFF050508),
                    ),
                ),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.34f * energy + 0.08f),
                        primaryColor.copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = pulseRadius,
                ),
                radius = pulseRadius,
                center = center,
            )

            // Particle overlay
            drawParticles(
                particles = particles,
                overlay = particleOverlay,
                phase = phase,
                energy = energy,
                accentColor = accentColor,
            )

            // Concentric sound-wave rings
            val baseArtRadius = 92.dp.toPx()
            for (ring in 1..4) {
                val ringPhase = (phase * 0.55f + ring * 0.25f) % 1f
                val radius = baseArtRadius + ringPhase * 125.dp.toPx() * (0.65f + 0.35f * energy)
                val alpha = (1f - ringPhase) * (0.36f * energy + 0.08f)
                drawCircle(
                    color = accentColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (2.4f - ring * 0.3f).dp.toPx()),
                )
            }

            // Radial spectrum bars around the album-art centerpiece
            val barCount = 64
            val innerR = baseArtRadius + 10.dp.toPx()
            for (i in 0 until barCount) {
                val angle = (i.toFloat() / barCount) * (2f * PI.toFloat()) - PI.toFloat() / 2f
                val harmonic = abs(
                    sin(phase * 3.8f + i * 0.37f) * 0.55f +
                        cos(phase * 2.1f - i * 0.23f) * 0.45f,
                )
                val barLen = (8.dp.toPx() + 40.dp.toPx() * harmonic * energy)
                val start = Offset(
                    x = center.x + cos(angle) * innerR,
                    y = center.y + sin(angle) * innerR,
                )
                val end = Offset(
                    x = center.x + cos(angle) * (innerR + barLen),
                    y = center.y + sin(angle) * (innerR + barLen),
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.45f + 0.45f * harmonic),
                    start = start,
                    end = end,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Floating album-art centerpiece
        val floatOffsetY = sin(phase * 1.6f) * 7f * energy
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = floatOffsetY }
                    .size(168.dp)
                    .shadow(24.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
                    .clip(CircleShape)
                    .border(2.dp, accentColor.copy(alpha = 0.55f), CircleShape)
                    .background(Color(0xFF14141F)),
                contentAlignment = Alignment.Center,
            ) {
                GlideImage(
                    model = coverUri.takeIf { it.isNotBlank() },
                    contentDescription = title.ifBlank { "Album art" },
                    contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder),
                    failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Track info + particle selector + quick controls at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title.ifBlank { "Nothing playing" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (artist.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Particle overlay chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ParticleOverlay.entries.forEach { mode ->
                    ModeChip(
                        label = mode.label,
                        selected = mode == particleOverlay,
                        accentColor = accentColor,
                        onClick = { onSelectParticle(mode) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            QuickPlaybackControls(
                isPlaying = isPlaying,
                accentColor = accentColor,
                onTogglePlayPause = {
                    if (isPlaying) SongPlayer.pause() else SongPlayer.play()
                },
                onNext = { SongPlayer.next() },
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun VisualizerModule(
    title: String,
    artist: String,
    coverUri: String,
    isPlaying: Boolean,
    primaryColor: Color,
    accentColor: Color,
    phase: Float,
    energy: Float,
    style: VisualizerStyle,
    onSelectStyle: (VisualizerStyle) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF06060B),
                        primaryColor.copy(alpha = 0.55f),
                        Color(0xFF050508),
                    ),
                ),
            )

            when (style) {
                VisualizerStyle.SpectrumBars -> drawSpectrumBars(phase, energy, accentColor, primaryColor)
                VisualizerStyle.NcsPulse -> drawNcsPulse(phase, energy, accentColor, primaryColor)
                VisualizerStyle.Waveform -> drawWaveform(phase, energy, accentColor, primaryColor)
            }
        }

        // Bottom track info overlay + style selector + quick controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VisualizerStyle.entries.forEach { item ->
                    ModeChip(
                        label = item.label,
                        selected = item == style,
                        accentColor = accentColor,
                        onClick = { onSelectStyle(item) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Track info overlay card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.48f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlideImage(
                        model = coverUri.takeIf { it.isNotBlank() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifBlank { "Nothing playing" },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (artist.isNotBlank()) {
                            Text(
                                text = artist,
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    QuickPlaybackControls(
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        onTogglePlayPause = {
                            if (isPlaying) SongPlayer.pause() else SongPlayer.play()
                        },
                        onNext = { SongPlayer.next() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.14f),
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun QuickPlaybackControls(
    isPlaying: Boolean,
    accentColor: Color,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePlayPause),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(
    particles: List<ParticleSeed>,
    overlay: ParticleOverlay,
    phase: Float,
    energy: Float,
    accentColor: Color,
) {
    val w = size.width
    val h = size.height
    particles.forEach { p ->
        when (overlay) {
            ParticleOverlay.Stars -> {
                val px = ((p.x + phase * 0.008f * p.speed) % 1f) * w
                val py = p.y * h * 0.82f
                val twinkle = 0.25f + 0.75f * (0.5f + 0.5f * sin(phase * 2.4f * p.speed + p.phaseOffset))
                drawCircle(
                    color = Color.White.copy(alpha = twinkle * (0.45f + 0.55f * energy)),
                    radius = p.size * (0.7f + 0.3f * twinkle),
                    center = Offset(px, py),
                )
            }

            ParticleOverlay.Snow -> {
                val py = ((p.y + phase * 0.06f * p.speed) % 1f) * h
                val sway = sin(phase * 1.4f + p.phaseOffset) * 18f
                val px = (p.x * w + sway + w) % w
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f + 0.25f * energy),
                    radius = p.size * 1.1f,
                    center = Offset(px, py),
                )
            }

            ParticleOverlay.Fireflies -> {
                val px = ((p.x + sin(phase * 0.7f * p.speed + p.phaseOffset) * 0.06f + 1f) % 1f) * w
                val py = ((p.y + cos(phase * 0.55f * p.speed + p.phaseOffset) * 0.05f + 1f) % 1f) * h * 0.85f
                val glow = 0.3f + 0.7f * (0.5f + 0.5f * sin(phase * 3.1f + p.phaseOffset))
                val fireflyColor = if (p.phaseOffset > PI) accentColor else Color(0xFFFFD166)
                drawCircle(
                    color = fireflyColor.copy(alpha = glow * 0.24f),
                    radius = p.size * 4.2f,
                    center = Offset(px, py),
                )
                drawCircle(
                    color = fireflyColor.copy(alpha = glow * 0.9f),
                    radius = p.size * 1.15f,
                    center = Offset(px, py),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrumBars(
    phase: Float,
    energy: Float,
    accentColor: Color,
    primaryColor: Color,
) {
    val barCount = 44
    val horizontalPad = 24.dp.toPx()
    val usableW = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
    val slotW = usableW / barCount
    val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())
    val centerY = size.height * 0.44f
    val maxHalfH = size.height * 0.24f

    for (i in 0 until barCount) {
        val norm = i.toFloat() / (barCount - 1)
        val env = 0.45f + 0.55f * sin(norm * PI.toFloat())
        val wave = abs(
            sin(phase * 4.2f + i * 0.42f) * 0.58f +
                cos(phase * 2.5f - i * 0.27f) * 0.42f,
        )
        val halfH = (8.dp.toPx() + maxHalfH * env * wave * energy)
        val x = horizontalPad + i * slotW + (slotW - barW) / 2f
        val barColor = androidx.compose.ui.graphics.lerp(primaryColor, accentColor, norm)
        drawRoundRect(
            color = barColor.copy(alpha = 0.85f),
            topLeft = Offset(x, centerY - halfH),
            size = Size(barW, halfH * 2f),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNcsPulse(
    phase: Float,
    energy: Float,
    accentColor: Color,
    primaryColor: Color,
) {
    val center = Offset(size.width / 2f, size.height * 0.42f)
    val beat = 0.5f + 0.5f * sin(phase * 4.5f)
    val baseRadius = size.minDimension * 0.20f * (0.92f + 0.16f * beat * energy)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.35f * energy),
                primaryColor.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            center = center,
            radius = baseRadius * 2.3f,
        ),
        radius = baseRadius * 2.3f,
        center = center,
    )

    val spikes = 72
    for (i in 0 until spikes) {
        val angle = (i.toFloat() / spikes) * (2f * PI.toFloat())
        val amp = abs(sin(phase * 5.0f + i * 0.5f) * cos(phase * 2.7f - i * 0.2f))
        val len = 6.dp.toPx() + 58.dp.toPx() * amp * energy
        val start = Offset(center.x + cos(angle) * baseRadius, center.y + sin(angle) * baseRadius)
        val end = Offset(center.x + cos(angle) * (baseRadius + len), center.y + sin(angle) * (baseRadius + len))
        drawLine(
            color = accentColor.copy(alpha = 0.55f + 0.45f * amp),
            start = start,
            end = end,
            strokeWidth = 2.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = baseRadius,
        center = center,
        style = Stroke(width = 3.dp.toPx()),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    phase: Float,
    energy: Float,
    accentColor: Color,
    primaryColor: Color,
) {
    val w = size.width
    val centerY = size.height * 0.43f
    val layers = 3
    for (layer in 0 until layers) {
        val path = Path()
        val freq = 2.2f + layer * 1.15f
        val speed = 3.2f + layer * 0.9f
        val amp = (36.dp.toPx() + layer * 18.dp.toPx()) * (0.25f + 0.75f * energy)
        val steps = 96
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val x = t * w
            val envelope = sin(t * PI.toFloat())
            val y = centerY + sin(t * freq * 2f * PI.toFloat() + phase * speed + layer) * amp * envelope
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val color = when (layer) {
            0 -> accentColor
            1 -> Color.White.copy(alpha = 0.85f)
            else -> primaryColor
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.78f - layer * 0.16f),
            style = Stroke(width = (3.5f - layer * 0.7f).dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
