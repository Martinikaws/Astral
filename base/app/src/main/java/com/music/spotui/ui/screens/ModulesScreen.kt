package com.music.spotui.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.myspoti.extensions.UiStyle
import app.myspoti.power.EnergySettings
import app.myspoti.engine.R
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.music.spotui.audio.AudioFrame
import com.music.spotui.audio.TrackAudioSync
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.di.Graph
import com.music.spotui.di.Palette
import com.music.spotui.di.SongPlayer
import com.music.spotui.extensions.ExtensionLook
import com.music.spotui.ui.components.windowSize
import com.music.spotui.ui.look.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private enum class ModuleMode(val label: String) {
    BackMusic("BackMusic"),
    Visualizer("Visualizer"),
}

private enum class WaveMode(val label: String) {
    Rings("Rings"),
    Bars("Bars"),
    Both("Both"),
}

private enum class ParticleOverlay(val label: String) {
    Stars("Stars"),
    Snow("Snow"),
    Fireflies("Fireflies"),
    Dust("Dust"),
}

private enum class VisualizerStyle(val label: String) {
    Ncs("NCS"),
    Spectrum("Spectrum"),
    Waveform("Waveform"),
}

private data class SyncBadgeState(
    val hasRealData: Boolean = false,
    val bpm: Int = 0,
)

private class RippleRing(
    var spawnMs: Long = 0L,
    var strength: Float = 0.7f,
    var downbeat: Boolean = false,
    var active: Boolean = false,
)

private class RipplePool(capacity: Int = 16) {
    val rings = Array(capacity) { RippleRing() }
    private var cursor = 0
    private var lastAmbientSpawnMs = 0L

    fun onFrame(frame: AudioFrame, nowMs: Long) {
        if (frame.beatFired) {
            spawn(nowMs, (0.55f + 0.45f * frame.amplitude).coerceIn(0.4f, 1f), frame.barPulse > 0.7f)
            lastAmbientSpawnMs = nowMs
        } else if (frame.isDecorative && frame.isPlaying) {
            if (lastAmbientSpawnMs == 0L || nowMs - lastAmbientSpawnMs >= 1_450L) {
                spawn(nowMs, 0.35f, downbeat = false)
                lastAmbientSpawnMs = nowMs
            }
        }
    }

    private fun spawn(nowMs: Long, strength: Float, downbeat: Boolean) {
        val slot = rings[cursor]
        slot.spawnMs = nowMs
        slot.strength = strength
        slot.downbeat = downbeat
        slot.active = true
        cursor = (cursor + 1) % rings.size
    }
}

private class AmbientParticle(
    var x: Float,
    var y: Float,
    val baseVx: Float,
    val baseVy: Float,
    val sizeDp: Float,
    val speed: Float,
    val phase: Float,
    val depth: Float,
)

private class ParticleFieldState(count: Int = 84) {
    val particles: List<AmbientParticle> = List(count) { i ->
        val rng = Random(1337 + i * 31)
        AmbientParticle(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            baseVx = (rng.nextFloat() - 0.5f) * 0.032f,
            baseVy = 0.020f + rng.nextFloat() * 0.055f,
            sizeDp = 1.2f + rng.nextFloat() * 3.4f,
            speed = 0.45f + rng.nextFloat() * 0.9f,
            phase = rng.nextFloat() * (2f * PI.toFloat()),
            depth = 0.35f + rng.nextFloat() * 0.65f,
        )
    }

    fun step(dtSec: Float, overlay: ParticleOverlay, frame: AudioFrame, reduceMotion: Boolean) {
        if (reduceMotion) return
        val beatBoost = if (frame.isPlaying) {
            1.0f + 1.55f * frame.beatPulse + 0.75f * frame.amplitude
        } else {
            0.22f
        }
        val dt = dtSec * beatBoost
        for (p in particles) {
            when (overlay) {
                ParticleOverlay.Stars -> {
                    p.x = (p.x + 0.006f * p.speed * dt + 1f) % 1f
                    p.y = (p.y - 0.002f * p.speed * dt + 1f) % 1f
                }

                ParticleOverlay.Snow -> {
                    p.y = (p.y + p.baseVy * p.speed * dt) % 1f
                    p.x = (p.x + p.baseVx * 0.45f * dt + 1f) % 1f
                }

                ParticleOverlay.Fireflies -> {
                    p.x = (p.x + p.baseVx * 0.75f * dt + 1f) % 1f
                    p.y = (p.y - p.baseVy * 0.25f * dt + 1f) % 1f
                }

                ParticleOverlay.Dust -> {
                    p.x = (p.x + p.baseVx * 0.85f * dt + 1f) % 1f
                    p.y = (p.y - p.baseVy * 0.45f * p.speed * dt + 1f) % 1f
                }
            }
        }
    }
}

private class NcsOrbitParticle(
    var angle: Float,
    var radiusScale: Float,
    val angularSpeed: Float,
    val sizeDp: Float,
    val phase: Float,
)

private class NcsOrbitField(count: Int = 96) {
    val particles: List<NcsOrbitParticle> = List(count) { idx ->
        val rng = Random(4200 + idx * 17)
        NcsOrbitParticle(
            angle = rng.nextFloat() * (2f * PI.toFloat()),
            radiusScale = 1.02f + rng.nextFloat() * 0.44f,
            angularSpeed = (0.18f + rng.nextFloat() * 0.42f) * if (idx % 2 == 0) 1f else -1f,
            sizeDp = 1.1f + rng.nextFloat() * 2.2f,
            phase = rng.nextFloat() * (2f * PI.toFloat()),
        )
    }

    fun step(dtSec: Float, frame: AudioFrame, reduceMotion: Boolean) {
        if (reduceMotion) return
        val speedMul = if (frame.isPlaying) {
            0.75f + 1.45f * frame.amplitude + 1.35f * frame.beatPulse
        } else {
            0.18f
        }
        for (p in particles) {
            p.angle = (p.angle + p.angularSpeed * speedMul * dtSec) % (2f * PI.toFloat())
        }
    }
}

private class SpectrumPeakState {
    val peaks = FloatArray(64)

    fun update(spectrum: FloatArray, dtSec: Float) {
        val fall = dtSec * 0.55f
        for (i in 0 until 64) {
            val v = spectrum[i]
            if (v >= peaks[i]) {
                peaks[i] = v
            } else {
                peaks[i] = (peaks[i] - fall).coerceAtLeast(v)
            }
        }
    }
}

/**
 * Full-screen **Modules** tab driven at 60-120fps by [TrackAudioSync]:
 * 1. **BackMusic** (`rolandyangg/backmusic_extension`) — 1.3x blurred cover backdrop, beat-triggered
 *    ripple rings & mirrored horizontal spectrum bars (`SoundWaves.jsx`), beat-reactive particles
 *    (`Particles.jsx`: Stars, Snow, Fireflies, Dust), rounded `24.dp` floating album art
 *    (`viz__center--art`) with 6s vertical bob & beat scale pop, and bottom-left `NowPlayingLabel`
 *    with animated 3-bar equalizer + auto-hiding top-right pill switcher, customize & hide-UI controls.
 * 2. **Visualizer** (`Konsl/spicetify-visualizer`) — authentic 3D Perlin NCS particle sphere with
 *    orbiting beat particles (`NCSVisualizer.tsx`), 64-channel audio spectrum curve + glowing bars
 *    (`SpectrumVisualizer.tsx`), and multi-band beat-synced Waveform.
 *
 * Respects `StarryNightPref` and `EnergySettings.oledBlack` so `StarryNightBackdrop` shines behind
 * a subtle translucent scrim when enabled.
 */
@Composable
fun ModulesScreen(navController: NavHostController) {
    val context = LocalContext.current
    remember(context) { StarryNightPref.init(context); true }

    val starryNightEnabled by StarryNightPref.enabled.collectAsState()
    val energyState by EnergySettings.state.collectAsState()
    val oledBlack = EnergySettings.oledBlack || energyState.policy.oledBlack
    val extensionBg = ExtensionLook.color(UiStyle.BACKGROUND)
    val starryNightActive = starryNightEnabled && !oledBlack && extensionBg == null
    val reduceMotion = LocalReduceMotion.current == true

    val songState = Graph.currentSongState
    val nowPlaying by SongPlayer.nowPlaying
    val title by songState.title
    val artist by songState.singer
    val coverUri by songState.coverUri
    val isPlaying by songState.playingState

    var activeModule by rememberSaveable { mutableStateOf(ModuleMode.BackMusic) }
    var waveMode by rememberSaveable { mutableStateOf(WaveMode.Both) }
    var particleOverlay by rememberSaveable { mutableStateOf(ParticleOverlay.Stars) }
    var visualizerStyle by rememberSaveable { mutableStateOf(VisualizerStyle.Ncs) }

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var customizeOpen by rememberSaveable { mutableStateOf(false) }
    var hideUiMode by rememberSaveable { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    var rawPrimary by remember { mutableStateOf(Color(0xFF1A2842)) }
    var rawAccent by remember { mutableStateOf(Color(0xFF53B8FF)) }

    LaunchedEffect(coverUri, nowPlaying.uri) {
        if (coverUri.isBlank()) {
            rawPrimary = Color(0xFF1A2842)
            rawAccent = Color(0xFF53B8FF)
        } else {
            val palette = Palette()
            palette.extractFirstColorFromImageUrl(context, coverUri) { color ->
                rawPrimary = if (color == Palette.NEUTRAL) Color(0xFF1A2842) else color
            }
            palette.extractSecondColorFromCoverUrl(context, coverUri) { color ->
                rawAccent = if (color == Palette.NEUTRAL) Color(0xFF53B8FF) else color
            }
        }
    }

    val primaryColor by animateColorAsState(
        targetValue = rawPrimary,
        animationSpec = tween(durationMillis = 650),
        label = "modulePrimary",
    )
    val accentColor by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 650),
        label = "moduleAccent",
    )

    val frameState = remember { mutableStateOf(AudioFrame.Empty) }
    val realTimeNowState = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var syncBadge by remember { mutableStateOf(SyncBadgeState()) }

    val ripplePool = remember { RipplePool() }
    val particleField = remember { ParticleFieldState() }
    val ncsOrbitField = remember { NcsOrbitField() }
    val spectrumPeaks = remember { SpectrumPeakState() }
    val bobPhaseState = remember { mutableStateOf(0f) }

    LaunchedEffect(activeModule, particleOverlay, visualizerStyle, reduceMotion) {
        var lastFrameMs = 0L
        while (true) {
            withFrameNanos {
                val nowMs = SystemClock.elapsedRealtime()
                val dtSec = if (lastFrameMs == 0L) {
                    1f / 60f
                } else {
                    ((nowMs - lastFrameMs).coerceIn(1L, 64L)) / 1000f
                }
                lastFrameMs = nowMs

                val frame = TrackAudioSync.sample(nowMs)
                frameState.value = frame
                realTimeNowState.longValue = nowMs

                val nextBpm = if (frame.hasRealData && frame.bpm > 0f) frame.bpm.roundToInt() else 0
                if (syncBadge.hasRealData != frame.hasRealData || syncBadge.bpm != nextBpm) {
                    syncBadge = SyncBadgeState(frame.hasRealData, nextBpm)
                }

                if (activeModule == ModuleMode.BackMusic) {
                    ripplePool.onFrame(frame, nowMs)
                    particleField.step(dtSec, particleOverlay, frame, reduceMotion)
                    if (!reduceMotion) {
                        val bobRate = if (frame.isPlaying) 1f / 6f else 0.35f / 6f
                        bobPhaseState.value = (bobPhaseState.value + dtSec * bobRate) % 1f
                    }
                } else {
                    when (visualizerStyle) {
                        VisualizerStyle.Ncs -> ncsOrbitField.step(dtSec, frame, reduceMotion)
                        VisualizerStyle.Spectrum -> spectrumPeaks.update(frame.spectrum64, dtSec)
                        VisualizerStyle.Waveform -> Unit
                    }
                }
            }
        }
    }

    // Auto-hide top bar after inactivity when not customizing.
    LaunchedEffect(lastInteractionMs, controlsVisible, customizeOpen, hideUiMode) {
        if (controlsVisible && !customizeOpen && !hideUiMode) {
            delay(6_000L)
            controlsVisible = false
        }
    }

    val bumpInteraction = {
        lastInteractionMs = SystemClock.elapsedRealtime()
        if (hideUiMode) {
            hideUiMode = false
            controlsVisible = true
        } else if (!controlsVisible) {
            controlsVisible = true
        }
    }

    val ws = windowSize()
    val hasTrack = title.isNotBlank()
    val bottomClearance: Dp = when {
        ws.navigationRail || ws.chromeInOneRow -> 84.dp
        hasTrack -> 138.dp
        else -> 88.dp
    }

    val rootModifier = Modifier
        .fillMaxSize()
        .then(
            when {
                oledBlack -> Modifier.background(Color.Black)
                extensionBg != null -> Modifier.background(extensionBg)
                starryNightActive -> Modifier // Transparent so StarryNightBackdrop shines behind.
                else -> Modifier.background(Color(0xFF07070C))
            },
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            lastInteractionMs = SystemClock.elapsedRealtime()
            if (hideUiMode) {
                hideUiMode = false
                controlsVisible = true
            } else {
                controlsVisible = !controlsVisible
                if (!controlsVisible) customizeOpen = false
            }
        }

    Box(modifier = rootModifier) {
        AnimatedContent(
            targetState = activeModule,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(200)) },
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
                    starryNightActive = starryNightActive,
                    oledBlack = oledBlack,
                    reduceMotion = reduceMotion,
                    waveMode = waveMode,
                    particleOverlay = particleOverlay,
                    frameState = frameState,
                    realTimeNowState = realTimeNowState,
                    bobPhaseState = bobPhaseState,
                    ripplePool = ripplePool,
                    particleField = particleField,
                    customizeOpen = customizeOpen && !hideUiMode,
                    controlsVisible = controlsVisible && !hideUiMode,
                    hideUiMode = hideUiMode,
                    bottomClearance = bottomClearance,
                    onSelectWaveMode = {
                        waveMode = it
                        bumpInteraction()
                    },
                    onSelectParticle = {
                        particleOverlay = it
                        bumpInteraction()
                    },
                    onTogglePlayPause = {
                        if (isPlaying) SongPlayer.pause() else SongPlayer.play()
                        bumpInteraction()
                    },
                    onPrevious = {
                        SongPlayer.previous()
                        bumpInteraction()
                    },
                    onNext = {
                        SongPlayer.next()
                        bumpInteraction()
                    },
                )

                ModuleMode.Visualizer -> VisualizerModule(
                    title = title,
                    artist = artist,
                    coverUri = coverUri,
                    isPlaying = isPlaying,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    starryNightActive = starryNightActive,
                    oledBlack = oledBlack,
                    reduceMotion = reduceMotion,
                    style = visualizerStyle,
                    frameState = frameState,
                    realTimeNowState = realTimeNowState,
                    ncsOrbitField = ncsOrbitField,
                    spectrumPeaks = spectrumPeaks,
                    controlsVisible = controlsVisible && !hideUiMode,
                    hideUiMode = hideUiMode,
                    customizeOpen = customizeOpen && !hideUiMode,
                    bottomClearance = bottomClearance,
                    onSelectStyle = {
                        visualizerStyle = it
                        bumpInteraction()
                    },
                    onTogglePlayPause = {
                        if (isPlaying) SongPlayer.pause() else SongPlayer.play()
                        bumpInteraction()
                    },
                    onPrevious = {
                        SongPlayer.previous()
                        bumpInteraction()
                    },
                    onNext = {
                        SongPlayer.next()
                        bumpInteraction()
                    },
                )
            }
        }

        // Top-left sync status badge + top-right pill switcher / customize / hide-UI controls.
        AnimatedVisibility(
            visible = controlsVisible && !hideUiMode,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(260)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ModuleTopBar(
                syncBadge = syncBadge,
                activeModule = activeModule,
                customizeOpen = customizeOpen,
                accentColor = accentColor,
                onSelectModule = {
                    activeModule = it
                    lastInteractionMs = SystemClock.elapsedRealtime()
                },
                onToggleCustomize = {
                    customizeOpen = !customizeOpen
                    controlsVisible = true
                    lastInteractionMs = SystemClock.elapsedRealtime()
                },
                onHideUi = {
                    hideUiMode = true
                    customizeOpen = false
                    controlsVisible = false
                },
            )
        }
    }
}

@Composable
private fun ModuleTopBar(
    syncBadge: SyncBadgeState,
    activeModule: ModuleMode,
    customizeOpen: Boolean,
    accentColor: Color,
    onSelectModule: (ModuleMode) -> Unit,
    onToggleCustomize: () -> Unit,
    onHideUi: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SyncStatusBadge(syncBadge = syncBadge, accentColor = accentColor)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModuleTopSwitcher(
                selected = activeModule,
                onSelect = onSelectModule,
                accentColor = accentColor,
            )

            TopActionButton(
                selected = customizeOpen,
                accentColor = accentColor,
                onClick = onToggleCustomize,
                contentDescription = "Customize module",
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Customize",
                    tint = if (customizeOpen) Color.White else Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(18.dp),
                )
            }

            TopActionButton(
                selected = false,
                accentColor = accentColor,
                onClick = onHideUi,
                contentDescription = "Hide UI",
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = "Hide UI",
                    tint = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SyncStatusBadge(
    syncBadge: SyncBadgeState,
    accentColor: Color,
) {
    val label = when {
        syncBadge.hasRealData && syncBadge.bpm > 0 -> "Beat-synced · ${syncBadge.bpm} BPM"
        syncBadge.hasRealData -> "Beat-synced"
        else -> "Ambient"
    }
    val dotColor = if (syncBadge.hasRealData) {
        lerp(accentColor, Color(0xFF4ADE80), 0.45f)
    } else {
        Color.White.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopActionButton(
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) accentColor.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f),
        ),
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
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
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = mode.label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BlurredArtworkBackdrop(
    coverUri: String,
    primaryColor: Color,
    accentColor: Color,
    starryNightActive: Boolean,
    oledBlack: Boolean,
    frameState: State<AudioFrame>,
    centerYFraction: Float,
    artworkStrength: Float = 1f,
) {
    val context = LocalContext.current
    var legacyBlurredArt by remember(coverUri) { mutableStateOf<ImageBitmap?>(null) }

    // Software blur fallback for Android 10-11 where Modifier.blur() is a no-op.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        DisposableEffect(coverUri) {
            if (coverUri.isBlank()) {
                legacyBlurredArt = null
                onDispose {}
            } else {
                val target = object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        legacyBlurredArt = softwareBoxBlur(resource, radius = 6)?.asImageBitmap()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        legacyBlurredArt = null
                    }
                }
                Glide.with(context)
                    .asBitmap()
                    .load(coverUri)
                    .override(72, 72)
                    .disallowHardwareConfig()
                    .into(target)
                onDispose {
                    runCatching { Glide.with(context).clear(target) }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (coverUri.isNotBlank() && artworkStrength > 0.01f) {
            val artModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val f = frameState.value
                    val baseAlpha = when {
                        starryNightActive -> 0.25f + 0.06f * f.amplitude
                        oledBlack -> 0.20f + 0.05f * f.amplitude
                        else -> 0.44f + 0.08f * f.amplitude
                    } * artworkStrength
                    alpha = baseAlpha.coerceIn(0f, 0.72f)
                    scaleX = 1.34f
                    scaleY = 1.34f
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlideImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = artModifier.blur(56.dp, BlurredEdgeTreatment.Rectangle),
                )
            } else {
                legacyBlurredArt?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = artModifier,
                    )
                }
            }
        }

        // Translucent radial vignette & subtle color wash (lets StarryNightBackdrop shine when active).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val f = frameState.value
            val center = Offset(size.width * 0.5f, size.height * centerYFraction)
            val radius = size.maxDimension * 0.85f

            if (starryNightActive) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.20f + 0.08f * f.amplitude),
                            Color(0x4A070B16),
                            Color(0x8804060D),
                        ),
                        center = center,
                        radius = radius,
                    ),
                )
            } else if (oledBlack) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.16f + 0.08f * f.amplitude),
                            Color(0xD9000000),
                            Color.Black,
                        ),
                        center = center,
                        radius = radius,
                    ),
                )
            } else {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.36f + 0.12f * f.amplitude),
                            Color(0xC40A0B14),
                            Color(0xEB050509),
                        ),
                        center = center,
                        radius = radius,
                    ),
                )
            }

            // Subtle ambient audio glow around center.
            val haloRadius = size.minDimension * (0.48f + 0.14f * f.amplitude + 0.06f * f.beatPulse)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = (0.14f + 0.22f * f.amplitude) * if (starryNightActive) 0.8f else 1f),
                        primaryColor.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = center,
            )
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
    starryNightActive: Boolean,
    oledBlack: Boolean,
    reduceMotion: Boolean,
    waveMode: WaveMode,
    particleOverlay: ParticleOverlay,
    frameState: State<AudioFrame>,
    realTimeNowState: State<Long>,
    bobPhaseState: State<Float>,
    ripplePool: RipplePool,
    particleField: ParticleFieldState,
    customizeOpen: Boolean,
    controlsVisible: Boolean,
    hideUiMode: Boolean,
    bottomClearance: Dp,
    onSelectWaveMode: (WaveMode) -> Unit,
    onSelectParticle: (ParticleOverlay) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artSize = minOf(maxWidth * 0.50f, 204.dp).coerceAtLeast(148.dp)
        val centerYFraction = 0.43f
        // Offset from Box's Alignment.Center (0.50f) to match Canvas center (centerYFraction) pixel-for-pixel.
        val centerYOffset = maxHeight * (centerYFraction - 0.5f)

        // Layer 1: Blown-up 1.3x heavily blurred cover art + translucent radial scrim.
        BlurredArtworkBackdrop(
            coverUri = coverUri,
            primaryColor = primaryColor,
            accentColor = accentColor,
            starryNightActive = starryNightActive,
            oledBlack = oledBlack,
            frameState = frameState,
            centerYFraction = centerYFraction,
            artworkStrength = 1.0f,
        )

        // Layer 2 (SoundWaves.jsx) + Layer 3 (Particles.jsx)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = frameState.value
            val nowMs = realTimeNowState.value
            val center = Offset(size.width * 0.5f, size.height * centerYFraction)
            val artHalfPx = artSize.toPx() * 0.5f

            drawBackMusicParticles(
                field = particleField,
                overlay = particleOverlay,
                frame = frame,
                nowMs = nowMs,
                accentColor = accentColor,
            )

            if (waveMode == WaveMode.Bars || waveMode == WaveMode.Both) {
                drawMirroredHorizontalBars(
                    center = center,
                    baseRadiusPx = artHalfPx,
                    frame = frame,
                    accentColor = accentColor,
                    primaryColor = primaryColor,
                )
            }

            if (waveMode == WaveMode.Rings || waveMode == WaveMode.Both) {
                drawSoundWaveRings(
                    center = center,
                    baseRadiusPx = artHalfPx,
                    frame = frame,
                    nowMs = nowMs,
                    ripplePool = ripplePool,
                    accentColor = accentColor,
                )
            }
        }

        // Layer 4 (Visualizer.css viz__center--art): Floating rounded 24.dp album art centerpiece
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = centerYOffset),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val frame = frameState.value
                        val scalePop = if (reduceMotion) 1f else 1.0f + 0.04f * frame.beatPulse
                        val bobPx = if (reduceMotion) 0f else sin(bobPhaseState.value * 2f * PI.toFloat()) * 7.dp.toPx()
                        scaleX = scalePop
                        scaleY = scalePop
                        translationY = bobPx
                    }
                    .size(artSize)
                    .shadow(
                        elevation = 28.dp,
                        shape = shape,
                        ambientColor = accentColor,
                        spotColor = primaryColor,
                    )
                    .clip(shape)
                    .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
                    .background(Color(0xFF131622))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlayPause,
                    ),
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

        // Layer 5 (NowPlayingLabel.jsx): Bottom-left track info + 3-bar mini equalizer, plus optional customize strip.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = customizeOpen,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.56f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Waves",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(54.dp),
                            )
                            WaveMode.entries.forEach { mode ->
                                ModeChip(
                                    label = mode.label,
                                    selected = mode == waveMode,
                                    accentColor = accentColor,
                                    onClick = { onSelectWaveMode(mode) },
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Particles",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(54.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ParticleOverlay.entries.forEach { mode ->
                                    ModeChip(
                                        label = mode.label,
                                        selected = mode == particleOverlay,
                                        accentColor = accentColor,
                                        onClick = { onSelectParticle(mode) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !hideUiMode,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(220)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MiniEqualizerBars(
                            frameState = frameState,
                            accentColor = accentColor,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { "Nothing playing" },
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (artist.isNotBlank()) {
                                Text(
                                    text = artist,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = controlsVisible && !hideUiMode,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(220)),
                    ) {
                        Row {
                            Spacer(Modifier.width(12.dp))
                            QuickPlaybackControls(
                                isPlaying = isPlaying,
                                accentColor = accentColor,
                                onPrevious = onPrevious,
                                onTogglePlayPause = onTogglePlayPause,
                                onNext = onNext,
                            )
                        }
                    }
                }
            }
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
    starryNightActive: Boolean,
    oledBlack: Boolean,
    reduceMotion: Boolean,
    style: VisualizerStyle,
    frameState: State<AudioFrame>,
    realTimeNowState: State<Long>,
    ncsOrbitField: NcsOrbitField,
    spectrumPeaks: SpectrumPeakState,
    controlsVisible: Boolean,
    hideUiMode: Boolean,
    customizeOpen: Boolean,
    bottomClearance: Dp,
    onSelectStyle: (VisualizerStyle) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val ncsPoints = remember { FloatArray(NCS_DOT_COUNT * 2) }
    val outerGlowPaint = remember { newNcsDotPaint() }
    val midGlowPaint = remember { newNcsDotPaint() }
    val corePaint = remember { newNcsDotPaint() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Subtle blurred cover aura (much lighter so crisp visualizer graphics dominate; transparent to StarryNight).
        BlurredArtworkBackdrop(
            coverUri = coverUri,
            primaryColor = primaryColor,
            accentColor = accentColor,
            starryNightActive = starryNightActive,
            oledBlack = oledBlack,
            frameState = frameState,
            centerYFraction = 0.43f,
            artworkStrength = if (starryNightActive) 0.45f else 0.55f,
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = frameState.value
            val nowMs = realTimeNowState.value
            when (style) {
                VisualizerStyle.Ncs -> drawNcsSphereVisualizer(
                    frame = frame,
                    nowMs = nowMs,
                    orbitField = ncsOrbitField,
                    points = ncsPoints,
                    outerGlowPaint = outerGlowPaint,
                    midGlowPaint = midGlowPaint,
                    corePaint = corePaint,
                    accentColor = accentColor,
                    primaryColor = primaryColor,
                    reduceMotion = reduceMotion,
                )

                VisualizerStyle.Spectrum -> drawSpectrumVisualizer(
                    frame = frame,
                    peaks = spectrumPeaks.peaks,
                    accentColor = accentColor,
                    primaryColor = primaryColor,
                )

                VisualizerStyle.Waveform -> drawBeatWaveformVisualizer(
                    frame = frame,
                    nowMs = nowMs,
                    accentColor = accentColor,
                    primaryColor = primaryColor,
                )
            }
        }

        // Bottom track info overlay + style selector pills
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = (controlsVisible || customizeOpen) && !hideUiMode,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(180)) { it / 2 },
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
            }

            Spacer(Modifier.height(14.dp))

            AnimatedVisibility(
                visible = !hideUiMode,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(220)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.44f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlideImage(
                            model = coverUri.takeIf { it.isNotBlank() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                        )
                        Spacer(Modifier.width(12.dp))
                        MiniEqualizerBars(
                            frameState = frameState,
                            accentColor = accentColor,
                        )
                        Spacer(Modifier.width(10.dp))
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
                        if (controlsVisible) {
                            Spacer(Modifier.width(8.dp))
                            QuickPlaybackControls(
                                isPlaying = isPlaying,
                                accentColor = accentColor,
                                onPrevious = onPrevious,
                                onTogglePlayPause = onTogglePlayPause,
                                onNext = onNext,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniEqualizerBars(
    frameState: State<AudioFrame>,
    accentColor: Color,
) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 16.dp)) {
        val f = frameState.value
        val barW = 3.dp.toPx()
        val gap = (size.width - barW * 3f) / 2f
        val maxH = size.height
        val minH = 3.dp.toPx()

        val b0 = f.bands12[1]
        val b1 = f.bands12[5]
        val b2 = f.bands12[9]
        val amp = f.amplitude
        val pulse = f.beatPulse

        val heights = floatArrayOf(
            (0.25f + 0.50f * amp + 0.30f * b0 + 0.18f * pulse),
            (0.32f + 0.48f * amp + 0.34f * b1 + 0.24f * pulse),
            (0.22f + 0.48f * amp + 0.32f * b2 + 0.16f * pulse),
        )

        for (i in 0 until 3) {
            val norm = if (f.isPlaying) heights[i].coerceIn(0.18f, 1f) else 0.20f
            val h = minH + (maxH - minH) * norm
            val x = i * (barW + gap)
            drawRoundRect(
                color = lerp(accentColor, Color.White, 0.25f),
                topLeft = Offset(x, maxH - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
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
            color = if (selected) Color.White else Color.White.copy(alpha = 0.74f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun QuickPlaybackControls(
    isPlaying: Boolean,
    accentColor: Color,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onPrevious),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.36f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.74f)),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePlayPause),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawBackMusicParticles(
    field: ParticleFieldState,
    overlay: ParticleOverlay,
    frame: AudioFrame,
    nowMs: Long,
    accentColor: Color,
) {
    val w = size.width
    val h = size.height
    val tSec = nowMs / 1000f
    val pulseScale = 1f + 0.42f * frame.beatPulse + 0.18f * frame.amplitude
    val alphaBoost = 0.55f + 0.45f * frame.amplitude + 0.30f * frame.beatPulse

    for (p in field.particles) {
        val px = p.x * w
        val py = p.y * h
        val baseR = p.sizeDp.dp.toPx() * pulseScale * (0.75f + 0.40f * p.depth)
        when (overlay) {
            ParticleOverlay.Stars -> {
                val twinkle = 0.25f + 0.75f * (0.5f + 0.5f * sin(tSec * 2.2f * p.speed + p.phase))
                val a = (twinkle * alphaBoost).coerceIn(0f, 0.96f)
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = baseR * 0.65f,
                    center = Offset(px, py),
                )
                if (frame.beatPulse > 0.35f && p.depth > 0.7f) {
                    val flare = baseR * (1.3f + 0.9f * frame.beatPulse)
                    drawLine(
                        color = Color.White.copy(alpha = a * 0.45f),
                        start = Offset(px - flare, py),
                        end = Offset(px + flare, py),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = a * 0.45f),
                        start = Offset(px, py - flare),
                        end = Offset(px, py + flare),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            ParticleOverlay.Snow -> {
                val swayPx = sin(tSec * 1.4f * p.speed + p.phase) * 14.dp.toPx()
                val x = (px + swayPx + w) % w
                val a = ((0.35f + 0.45f * p.depth) * alphaBoost.coerceAtMost(1.1f)).coerceIn(0f, 0.92f)
                drawCircle(
                    color = Color.White.copy(alpha = a * 0.28f),
                    radius = baseR * 1.6f,
                    center = Offset(x, py),
                )
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = baseR * 0.78f,
                    center = Offset(x, py),
                )
            }

            ParticleOverlay.Fireflies -> {
                val wx = (px + sin(tSec * 1.1f * p.speed + p.phase) * 18.dp.toPx() + w) % w
                val wy = (py + cos(tSec * 0.9f * p.speed + p.phase) * 14.dp.toPx() + h) % h
                val glow = 0.30f + 0.70f * (0.5f + 0.5f * sin(tSec * 2.8f + p.phase))
                val c = if (p.phase > PI) accentColor else Color(0xFFFFD166)
                drawCircle(
                    color = c.copy(alpha = (glow * 0.24f * alphaBoost).coerceIn(0f, 0.42f)),
                    radius = baseR * 3.1f,
                    center = Offset(wx, wy),
                )
                drawCircle(
                    color = lerp(c, Color.White, 0.35f).copy(alpha = (glow * 0.90f).coerceIn(0f, 1f)),
                    radius = baseR * 0.85f,
                    center = Offset(wx, wy),
                )
            }

            ParticleOverlay.Dust -> {
                val driftX = (px + sin(tSec * 0.7f * p.speed + p.phase) * 12.dp.toPx() + w) % w
                val shimmer = 0.35f + 0.65f * (0.5f + 0.5f * sin(tSec * 1.6f + p.phase))
                val dustColor = lerp(accentColor, Color(0xFFFFF3C4), 0.45f)
                val dustRadius = baseR * (1.25f + 0.65f * (1f - p.depth))
                drawCircle(
                    color = dustColor.copy(alpha = (shimmer * 0.16f * alphaBoost).coerceIn(0f, 0.34f)),
                    radius = dustRadius * 2.6f,
                    center = Offset(driftX, py),
                )
                drawCircle(
                    color = Color.White.copy(alpha = (shimmer * 0.55f * p.depth).coerceIn(0f, 0.80f)),
                    radius = dustRadius * 0.70f,
                    center = Offset(driftX, py),
                )
            }
        }
    }
}

private fun DrawScope.drawSoundWaveRings(
    center: Offset,
    baseRadiusPx: Float,
    frame: AudioFrame,
    nowMs: Long,
    ripplePool: RipplePool,
    accentColor: Color,
) {
    val maxExpandPx = size.minDimension * 0.48f

    // Outer pulsing glow halo tracking real amplitude & beatPulse.
    val haloR = baseRadiusPx * (1.24f + 0.36f * frame.amplitude + 0.18f * frame.beatPulse)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = (0.14f + 0.32f * frame.amplitude + 0.14f * frame.beatPulse).coerceIn(0f, 0.65f)),
                accentColor.copy(alpha = 0.08f),
                Color.Transparent,
            ),
            center = center,
            radius = haloR,
        ),
        radius = haloR,
        center = center,
    )

    // Subtle crisp aura ring hugging the album art edge.
    drawCircle(
        color = lerp(accentColor, Color.White, 0.35f).copy(
            alpha = (0.18f + 0.42f * frame.beatPulse + 0.18f * frame.amplitude).coerceIn(0f, 0.85f),
        ),
        radius = baseRadiusPx * (1.03f + 0.045f * frame.beatPulse),
        center = center,
        style = Stroke(width = (1.6f + 1.4f * frame.beatPulse).dp.toPx()),
    )

    // Expanding beat-triggered ripple rings.
    val ringDurationMs = 1_150f
    for (ring in ripplePool.rings) {
        if (!ring.active) continue
        val ageMs = (nowMs - ring.spawnMs).toFloat()
        if (ageMs < 0f || ageMs >= ringDurationMs) {
            ring.active = false
            continue
        }
        val p = (ageMs / ringDurationMs).coerceIn(0f, 1f)
        val easeOut = 1f - (1f - p).pow(2.2f)
        val radius = baseRadiusPx * 0.96f + easeOut * maxExpandPx
        val alpha = ((1f - p).pow(1.5f) * (0.48f * ring.strength + if (ring.downbeat) 0.24f else 0f))
            .coerceIn(0f, 0.82f)
        val strokeDp = if (ring.downbeat) 3.0f - 1.4f * p else 2.0f - 0.9f * p
        val ringColor = if (ring.downbeat) lerp(accentColor, Color.White, 0.32f) else accentColor
        drawCircle(
            color = ringColor.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = strokeDp.coerceAtLeast(0.6f).dp.toPx()),
        )
    }
}

private fun DrawScope.drawMirroredHorizontalBars(
    center: Offset,
    baseRadiusPx: Float,
    frame: AudioFrame,
    accentColor: Color,
    primaryColor: Color,
) {
    // 36 symmetric pairs mirrored left/right from center and mirrored vertically above/below center.y.
    val halfCount = 18
    val totalBars = halfCount * 2
    val marginPx = 22.dp.toPx()
    val usableW = (size.width - marginPx * 2f).coerceAtLeast(1f)
    val slotW = usableW / totalBars
    val barW = (slotW * 0.56f).coerceIn(2.dp.toPx(), 7.dp.toPx())
    val maxHalfH = maxOf(baseRadiusPx + 58.dp.toPx(), size.minDimension * 0.34f)
    val minHalfH = 4.dp.toPx()
    val centerX = size.width * 0.5f
    val padGlow = 2.dp.toPx()

    for (i in 0 until halfCount) {
        val norm = i.toFloat() / (halfCount - 1).coerceAtLeast(1)
        // Map inner bars to energetic lower/mid bands and outer bars to higher pitch bands.
        val bandPos = norm * 11f
        val b0 = bandPos.toInt().coerceIn(0, 11)
        val b1 = (b0 + 1).coerceAtMost(11)
        val frac = bandPos - b0
        val mu = (1f - cos(frac * PI.toFloat())) * 0.5f
        val bandVal = frame.bands12[b0] + (frame.bands12[b1] - frame.bands12[b0]) * mu

        val pulseBoost = frame.beatPulse * (0.30f * (1f - norm * 0.55f))
        val level = (bandVal * 0.85f + frame.amplitude * 0.25f + pulseBoost).coerceIn(0f, 1f)
        val halfH = minHalfH + maxHalfH * level
        val barColor = lerp(accentColor, primaryColor, norm * 0.45f)
        val glowColor = lerp(barColor, Color.White, 0.35f)
        val glowAlpha = 0.22f + 0.24f * level
        val coreAlpha = 0.58f + 0.38f * level

        val offsetFromCenter = (i + 0.5f) * slotW
        val leftX = centerX - offsetFromCenter - barW * 0.5f
        val rightX = centerX + offsetFromCenter - barW * 0.5f

        drawRoundRect(
            color = barColor.copy(alpha = glowAlpha),
            topLeft = Offset(leftX - barW * 0.35f, center.y - halfH - padGlow),
            size = Size(barW * 1.7f, (halfH + padGlow) * 2f),
            cornerRadius = CornerRadius(barW, barW),
        )
        drawRoundRect(
            color = glowColor.copy(alpha = coreAlpha),
            topLeft = Offset(leftX, center.y - halfH),
            size = Size(barW, halfH * 2f),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
        drawRoundRect(
            color = barColor.copy(alpha = glowAlpha),
            topLeft = Offset(rightX - barW * 0.35f, center.y - halfH - padGlow),
            size = Size(barW * 1.7f, (halfH + padGlow) * 2f),
            cornerRadius = CornerRadius(barW, barW),
        )
        drawRoundRect(
            color = glowColor.copy(alpha = coreAlpha),
            topLeft = Offset(rightX, center.y - halfH),
            size = Size(barW, halfH * 2f),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}

// Reused per-draw scratch (draw pass runs on the main thread only; each is reset/overwritten before use).
private val spectrumCurvePath = Path()
private val spectrumAreaPath = Path()
private val spectrumXs = FloatArray(64)
private val spectrumYs = FloatArray(64)
private val waveformPath = Path()

private fun DrawScope.drawSpectrumVisualizer(
    frame: AudioFrame,
    peaks: FloatArray,
    accentColor: Color,
    primaryColor: Color,
) {
    val count = 64
    val padX = 20.dp.toPx()
    val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
    val slotW = usableW / count
    val barW = (slotW * 0.62f).coerceAtLeast(1.8.dp.toPx())
    val baselineY = size.height * 0.58f
    val maxUpH = size.height * 0.34f
    val maxReflectH = size.height * 0.11f

    // Subtle glowing baseline reacting to downbeats.
    drawLine(
        color = lerp(accentColor, Color.White, 0.4f).copy(alpha = 0.22f + 0.45f * frame.barPulse),
        start = Offset(padX, baselineY),
        end = Offset(size.width - padX, baselineY),
        strokeWidth = (1.2f + 1.2f * frame.barPulse).dp.toPx(),
    )

    val curvePath = spectrumCurvePath.also { it.reset() }
    val areaPath = spectrumAreaPath.also { it.reset() }
    val xs = spectrumXs
    val ys = spectrumYs

    for (i in 0 until count) {
        val norm = i / (count - 1f)
        val v = frame.spectrum64[i].coerceIn(0f, 1f)
        val h = (4.dp.toPx() + maxUpH * v)
        val xCenter = padX + (i + 0.5f) * slotW
        val topY = baselineY - h
        xs[i] = xCenter
        ys[i] = topY

        val barColor = lerp(primaryColor, accentColor, norm)
        val tipColor = lerp(barColor, Color.White, (v * 0.55f).coerceIn(0f, 0.65f))

        // Vertical glowing spectrum bar.
        drawRoundRect(
            color = tipColor.copy(alpha = 0.35f + 0.55f * v),
            topLeft = Offset(xCenter - barW * 0.5f, topY),
            size = Size(barW, h),
            cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
        )

        // Subtle mirror reflection beneath baseline.
        val reflectH = (2.dp.toPx() + maxReflectH * v)
        drawRoundRect(
            color = barColor.copy(alpha = 0.16f * v + 0.04f),
            topLeft = Offset(xCenter - barW * 0.5f, baselineY + 2.dp.toPx()),
            size = Size(barW, reflectH),
            cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
        )

        // Gravity-falling peak cap.
        val peakH = (4.dp.toPx() + maxUpH * peaks[i].coerceIn(0f, 1f))
        val peakY = baselineY - peakH - 3.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.55f + 0.35f * frame.beatPulse),
            topLeft = Offset(xCenter - barW * 0.5f, peakY),
            size = Size(barW, 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
    }

    // Smooth 64-channel audio spectrum curve + translucent fill.
    curvePath.moveTo(xs[0], ys[0])
    areaPath.moveTo(xs[0], baselineY)
    areaPath.lineTo(xs[0], ys[0])

    for (i in 0 until count - 1) {
        val midX = (xs[i] + xs[i + 1]) * 0.5f
        val midY = (ys[i] + ys[i + 1]) * 0.5f
        curvePath.quadraticTo(xs[i], ys[i], midX, midY)
        areaPath.quadraticTo(xs[i], ys[i], midX, midY)
    }
    curvePath.lineTo(xs[count - 1], ys[count - 1])
    areaPath.lineTo(xs[count - 1], ys[count - 1])
    areaPath.lineTo(xs[count - 1], baselineY)
    areaPath.close()

    drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.24f + 0.14f * frame.amplitude),
                primaryColor.copy(alpha = 0.05f),
                Color.Transparent,
            ),
            startY = baselineY - maxUpH,
            endY = baselineY,
        ),
    )

    drawPath(
        path = curvePath,
        brush = Brush.horizontalGradient(
            colors = listOf(primaryColor, accentColor, Color.White.copy(alpha = 0.9f)),
        ),
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawBeatWaveformVisualizer(
    frame: AudioFrame,
    nowMs: Long,
    accentColor: Color,
    primaryColor: Color,
) {
    val w = size.width
    val centerY = size.height * 0.44f
    val tSec = nowMs / 1000f
    val maxAmpPx = size.height * 0.23f
    val steps = 112

    // Center horizon beat line reacting to downbeat barPulse.
    drawLine(
        color = lerp(accentColor, Color.White, 0.5f).copy(alpha = 0.14f + 0.52f * frame.barPulse),
        start = Offset(16.dp.toPx(), centerY),
        end = Offset(w - 16.dp.toPx(), centerY),
        strokeWidth = (1.0f + 1.8f * frame.barPulse).dp.toPx(),
    )

    val layers = 3
    for (layer in 0 until layers) {
        val path = waveformPath.also { it.reset() }
        val layerScale = when (layer) {
            0 -> 1.0f
            1 -> 0.72f
            else -> 0.48f
        }
        val phaseShift = layer * 1.25f

        for (s in 0..steps) {
            val u = s / steps.toFloat()
            val x = u * w
            val window = sin(u * PI.toFloat()).pow(1.25f)

            // Sum real 12 chroma bands into an organic multi-harmonic waveform.
            var harmonicSum = 0f
            var weightSum = 0f
            for (k in 0 until 12) {
                val band = frame.bands12[k]
                val spatialFreq = (1.5f + k * 0.95f) * (2f * PI.toFloat())
                val motionRate = (2.2f + k * 0.55f) * if (frame.isPlaying) 1f else 0.22f
                val wBand = 1f / (1f + k * 0.14f)
                harmonicSum += band * wBand * sin(u * spatialFreq + tSec * motionRate + phaseShift)
                weightSum += wBand
            }
            val normalizedWave = if (weightSum > 0f) harmonicSum / weightSum else 0f
            val gain = (0.28f + 0.72f * frame.amplitude + 0.45f * frame.beatPulse + 0.25f * frame.barPulse)
            val y = centerY + normalizedWave * maxAmpPx * window * gain * layerScale

            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val strokeColor = when (layer) {
            0 -> lerp(accentColor, Color.White, 0.35f).copy(alpha = 0.92f)
            1 -> accentColor.copy(alpha = 0.56f)
            else -> lerp(primaryColor, accentColor, 0.5f).copy(alpha = 0.38f)
        }
        val strokeDp = when (layer) {
            0 -> 2.8f + 1.2f * frame.beatPulse
            1 -> 4.6f
            else -> 7.2f
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = strokeDp.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// ---------------------------------------------------------------------------
// NCS Particle Sphere Visualizer (ported from Konsl/spicetify-visualizer)
// ---------------------------------------------------------------------------

private const val NCS_DOT_GRID = 44
private const val NCS_DOT_COUNT = NCS_DOT_GRID * NCS_DOT_GRID
private const val NCS_DOT_SPACING = 0.9f
private const val NCS_DOT_OFFSET = -0.45f
private const val NCS_NOISE_FREQUENCY = 4f
private const val NCS_NOISE_AMPLITUDE = 0.32f * 0.9f
private const val NCS_GAIN = 0.5f
private const val NCS_LACUNARITY = 1.5f
private const val NCS_FRACTAL_BOUNDING = 1f / 1.75f
private const val NCS_PERLIN_SCALE = 0.9649214f
private const val NCS_PRIME_X = 501125321
private const val NCS_PRIME_Y = 1136930381
private const val NCS_PRIME_Z = 1720413743
private const val NCS_HASH_MULTIPLIER = 0x27d4eb2d

private val NCS_GRADIENTS_3D = floatArrayOf(
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

private fun newNcsDotPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}

private fun DrawScope.drawNcsSphereVisualizer(
    frame: AudioFrame,
    nowMs: Long,
    orbitField: NcsOrbitField,
    points: FloatArray,
    outerGlowPaint: Paint,
    midGlowPaint: Paint,
    corePaint: Paint,
    accentColor: Color,
    primaryColor: Color,
    reduceMotion: Boolean,
) {
    val centerX = size.width * 0.5f
    val centerY = size.height * 0.42f
    val minDim = size.minDimension
    val halfSize = minDim * 0.45f

    val progressSec = frame.positionMs / 1000f
    val uAmplitude = frame.amplitude.coerceIn(0f, 1f)
    val uNoiseOffset = if (reduceMotion) {
        0f
    } else if (frame.hasRealData) {
        (0.5f * progressSec + frame.integralTimeSec) * 0.75f
    } else {
        (nowMs / 1000f) * 0.35f + frame.integralTimeSec * 0.75f
    }

    val uSphereRadius = 0.64f + 0.23f * uAmplitude + 0.055f * frame.beatPulse
    val uFeather = (uAmplitude + 3f) * (uAmplitude + 3f) * (45f / 1568f)
    val featherRadius = uSphereRadius - uFeather
    val spherePxRadius = halfSize * uSphereRadius

    // Atmospheric radial bloom behind the NCS particle sphere.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = (0.22f + 0.28f * uAmplitude + 0.14f * frame.beatPulse).coerceIn(0f, 0.7f)),
                primaryColor.copy(alpha = 0.14f),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = spherePxRadius * 1.9f,
        ),
        radius = spherePxRadius * 1.9f,
        center = Offset(centerX, centerY),
    )

    // Concentric shockwave rings echoing beat & downbeat pulses.
    val ring1R = spherePxRadius * (1.03f + 0.14f * frame.beatPulse)
    drawCircle(
        color = lerp(accentColor, Color.White, 0.45f).copy(alpha = 0.24f + 0.52f * frame.beatPulse),
        radius = ring1R,
        center = Offset(centerX, centerY),
        style = Stroke(width = (1.6f + 1.8f * frame.beatPulse).dp.toPx()),
    )
    if (frame.barPulse > 0.04f) {
        val expand = 1f - frame.barPulse
        drawCircle(
            color = accentColor.copy(alpha = (frame.barPulse * 0.55f).coerceIn(0f, 0.7f)),
            radius = spherePxRadius * (1.08f + expand * 0.55f),
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.0.dp.toPx()),
        )
    }

    // 44x44 Perlin fractal noise dot sphere.
    var out = 0
    for (gy in 0 until NCS_DOT_GRID) {
        val v = (gy + 0.5f) / NCS_DOT_GRID
        val nv = v * NCS_NOISE_FREQUENCY
        val baseY = v * NCS_DOT_SPACING + NCS_DOT_OFFSET
        for (gx in 0 until NCS_DOT_GRID) {
            val u = (gx + 0.5f) / NCS_DOT_GRID
            val noise = ncsFractalNoise(frame.seed, u * NCS_NOISE_FREQUENCY, nv, uNoiseOffset) * NCS_NOISE_AMPLITUDE
            var dx = u * NCS_DOT_SPACING + NCS_DOT_OFFSET + noise
            var dy = baseY + noise
            val dz = (noise + 0.5f * NCS_NOISE_AMPLITUDE) * (uAmplitude + 0.25f * frame.beatPulse) * 0.42f

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

    val coreDotPx = (minDim * 0.0072f).coerceAtLeast(1.8f)

    outerGlowPaint.color = accentColor.copy(alpha = 0.16f + 0.10f * frame.beatPulse).toArgb()
    outerGlowPaint.strokeWidth = coreDotPx * 5.2f

    midGlowPaint.color = accentColor.copy(alpha = 0.42f + 0.14f * uAmplitude).toArgb()
    midGlowPaint.strokeWidth = coreDotPx * 2.5f

    corePaint.color = lerp(accentColor, Color.White, 0.38f).toArgb()
    corePaint.strokeWidth = coreDotPx

    val native = drawContext.canvas.nativeCanvas
    native.drawPoints(points, 0, out, outerGlowPaint)
    native.drawPoints(points, 0, out, midGlowPaint)
    native.drawPoints(points, 0, out, corePaint)

    // Crisp orbiting particles tracking amplitude & beatPulse.
    val tSec = nowMs / 1000f
    for (p in orbitField.particles) {
        val radialPulse = p.radiusScale + 0.16f * frame.beatPulse + 0.10f * frame.amplitude * sin(tSec * 2.3f + p.phase)
        val r = spherePxRadius * radialPulse
        val px = centerX + cos(p.angle) * r
        val py = centerY + sin(p.angle) * r
        val dotR = p.sizeDp.dp.toPx() * (0.85f + 0.45f * frame.beatPulse)
        val alpha = (0.40f + 0.45f * frame.amplitude + 0.30f * frame.beatPulse).coerceIn(0f, 0.95f)
        drawCircle(
            color = accentColor.copy(alpha = alpha * 0.25f),
            radius = dotR * 2.4f,
            center = Offset(px, py),
        )
        drawCircle(
            color = lerp(accentColor, Color.White, 0.48f).copy(alpha = alpha),
            radius = dotR,
            center = Offset(px, py),
        )
    }
}

private fun ncsSmootherStep(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

private fun ncsFloorToInt(x: Float): Int {
    val xi = x.toInt()
    return if (x < xi) xi - 1 else xi
}

private fun ncsLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun ncsGradCoord(seed: Int, px: Int, py: Int, pz: Int, dx: Float, dy: Float, dz: Float): Float {
    var hash = (seed xor px xor py xor pz) * NCS_HASH_MULTIPLIER
    hash = hash xor (hash shr 15)
    hash = hash and (63 shl 2)
    return dx * NCS_GRADIENTS_3D[hash] + dy * NCS_GRADIENTS_3D[hash or 1] + dz * NCS_GRADIENTS_3D[hash or 2]
}

private fun ncsPerlinSingle(seed: Int, x: Float, y: Float, z: Float): Float {
    val x0 = ncsFloorToInt(x)
    val y0 = ncsFloorToInt(y)
    val z0 = ncsFloorToInt(z)

    val dx0 = x - x0
    val dy0 = y - y0
    val dz0 = z - z0
    val dx1 = dx0 - 1f
    val dy1 = dy0 - 1f
    val dz1 = dz0 - 1f

    val sx = ncsSmootherStep(dx0)
    val sy = ncsSmootherStep(dy0)
    val sz = ncsSmootherStep(dz0)

    val x0p = x0 * NCS_PRIME_X
    val y0p = y0 * NCS_PRIME_Y
    val z0p = z0 * NCS_PRIME_Z
    val x1p = x0p + NCS_PRIME_X
    val y1p = y0p + NCS_PRIME_Y
    val z1p = z0p + NCS_PRIME_Z

    val xf00 = ncsLerp(ncsGradCoord(seed, x0p, y0p, z0p, dx0, dy0, dz0), ncsGradCoord(seed, x1p, y0p, z0p, dx1, dy0, dz0), sx)
    val xf10 = ncsLerp(ncsGradCoord(seed, x0p, y1p, z0p, dx0, dy1, dz0), ncsGradCoord(seed, x1p, y1p, z0p, dx1, dy1, dz0), sx)
    val xf01 = ncsLerp(ncsGradCoord(seed, x0p, y0p, z1p, dx0, dy0, dz1), ncsGradCoord(seed, x1p, y0p, z1p, dx1, dy0, dz1), sx)
    val xf11 = ncsLerp(ncsGradCoord(seed, x0p, y1p, z1p, dx0, dy1, dz1), ncsGradCoord(seed, x1p, y1p, z1p, dx1, dy1, dz1), sx)

    val yf0 = ncsLerp(xf00, xf10, sy)
    val yf1 = ncsLerp(xf01, xf11, sy)
    return ncsLerp(yf0, yf1, sz) * NCS_PERLIN_SCALE
}

private fun ncsFractalNoise(seed: Int, x: Float, y: Float, z: Float): Float {
    var total = ncsPerlinSingle(seed, x, y, z) * NCS_FRACTAL_BOUNDING
    var amp = NCS_FRACTAL_BOUNDING * NCS_GAIN
    var cx = x * NCS_LACUNARITY
    var cy = y * NCS_LACUNARITY
    var cz = z * NCS_LACUNARITY
    total += ncsPerlinSingle(seed + 1, cx, cy, cz) * amp
    amp *= NCS_GAIN
    cx *= NCS_LACUNARITY
    cy *= NCS_LACUNARITY
    cz *= NCS_LACUNARITY
    total += ncsPerlinSingle(seed + 2, cx, cy, cz) * amp
    return total
}

// ---------------------------------------------------------------------------
// Pre-Android-12 fast 3-pass box blur fallback for cover art backdrop
// ---------------------------------------------------------------------------

private fun softwareBoxBlur(source: Bitmap, radius: Int): Bitmap? {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
    return runCatching {
        val soft = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        } ?: return null
        val w = soft.width
        val h = soft.height
        val pixels = IntArray(w * h)
        soft.getPixels(pixels, 0, w, 0, 0, w, h)
        if (soft !== source) soft.recycle()

        val tmp = IntArray(w * h)
        repeat(3) {
            boxBlur1D(pixels, tmp, lines = h, len = w, lineStride = w, pixelStride = 1, radius = radius)
            boxBlur1D(tmp, pixels, lines = w, len = h, lineStride = 1, pixelStride = w, radius = radius)
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()
}

private fun boxBlur1D(
    src: IntArray,
    dst: IntArray,
    lines: Int,
    len: Int,
    lineStride: Int,
    pixelStride: Int,
    radius: Int,
) {
    val div = radius * 2 + 1
    val half = div / 2
    val maxIdx = len - 1
    for (line in 0 until lines) {
        val base = line * lineStride
        var rSum = 0
        var gSum = 0
        var bSum = 0
        for (k in -radius..radius) {
            val c = src[base + k.coerceIn(0, maxIdx) * pixelStride]
            rSum += (c ushr 16) and 0xFF
            gSum += (c ushr 8) and 0xFF
            bSum += c and 0xFF
        }
        for (i in 0 until len) {
            val r = ((rSum + half) / div).coerceIn(0, 255)
            val g = ((gSum + half) / div).coerceIn(0, 255)
            val b = ((bSum + half) / div).coerceIn(0, 255)
            dst[base + i * pixelStride] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            if (i + 1 < len) {
                val outC = src[base + (i - radius).coerceIn(0, maxIdx) * pixelStride]
                val inC = src[base + (i + radius + 1).coerceIn(0, maxIdx) * pixelStride]
                rSum += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
                gSum += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
                bSum += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }
}
