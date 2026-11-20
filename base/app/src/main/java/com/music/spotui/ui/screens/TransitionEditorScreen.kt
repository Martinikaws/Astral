package com.music.spotui.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.automix.CustomTransition
import app.myspoti.automix.EnvelopePoint
import app.myspoti.automix.MixPlanner
import app.myspoti.automix.TrackAnalysis
import app.myspoti.automix.TransitionPreset
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.automix.AutoMix
import com.music.spotui.automix.CustomTransitionStore
import com.music.spotui.automix.TransitionEditorBridge
import com.music.spotui.automix.TransitionPairSession
import com.music.spotui.automix.TransitionPreviewPlayer
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.di.LocalPlayer
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AstralLavender
import com.music.spotui.ui.theme.AstralTrailLight
import com.music.spotui.ui.theme.AstralTrailMid
import com.music.spotui.ui.theme.GridBackground
import com.music.spotui.ui.theme.StarryBlue
import com.music.spotui.ui.theme.tone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class EditorMode {
    VOLUME,
    OVERLAP,
}

private enum class HarmonicMatch(@androidx.annotation.StringRes val labelRes: Int, val color: Color) {
    COMPATIBLE(R.string.transition_harmonic_compatible, Color(0xFF34D399)),
    NEAR(R.string.transition_harmonic_near, Color(0xFFFBBF24)),
    CLASH(R.string.transition_harmonic_clash, Color(0xFFF87171)),
    UNKNOWN(R.string.transition_harmonic_unknown, Color(0xFF94A3B8)),
}

private val OutgoingDeckColor = Color(0xFF38BDF8) // Astral Sky Cyan
private val IncomingDeckColor = Color(0xFFC084FC) // Astral Neon Orchid

@Composable
fun TransitionEditorScreen(navController: NavController) {
    val context = LocalContext.current
    val session by TransitionEditorBridge.currentPair.collectAsState()
    val previewState by TransitionPreviewPlayer.state.collectAsState()
    val storeVersion by CustomTransitionStore.version.collectAsState()
    val localActive by LocalPlayer.active
    val starryEnabled by StarryNightPref.enabled.collectAsState()

    // Periodically refresh bridge while on the screen so completed background analyses or track skips appear immediately.
    LaunchedEffect(localActive, storeVersion) {
        while (true) {
            TransitionEditorBridge.refresh()
            kotlinx.coroutines.delay(750L)
        }
    }

    // The bridge only works while this screen is open; the preview stops when the screen goes
    // to the background (ON_STOP) or is left, so ExoPlayer decks never leak or play unseen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        TransitionEditorBridge.openCurrentPair()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) TransitionPreviewPlayer.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            TransitionPreviewPlayer.stop()
            TransitionEditorBridge.close()
        }
    }

    // Another song took over (skip, the queue moved on): the preview of the old pair stops.
    LaunchedEffect(session?.fromTrackId, session?.toTrackId) {
        TransitionPreviewPlayer.stop()
    }

    val pairKey = session?.let { "${it.fromTrackId}->${it.toTrackId}@${it.contextUri}" } ?: "none"
    var working by remember(pairKey) {
        mutableStateOf(
            session?.initialTransition ?: CustomTransition(
                fromTrackId = "",
                toTrackId = "",
                exitA = 0.0,
                entryB = 0.0,
                overlapBars = 8,
                overlapSec = 8.0,
            ),
        )
    }
    var hasUnsavedEdits by remember(pairKey) { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf(EditorMode.VOLUME) }

    // Update working transition if background analysis completes and the user hasn't made manual edits yet.
    LaunchedEffect(session?.ready, session?.savedCustom, session?.autoPlan) {
        val s = session ?: return@LaunchedEffect
        if (!hasUnsavedEdits) {
            working = s.initialTransition
        }
    }

    val hasCustomSaved = session?.let { s ->
        remember(s.fromTrackId, s.toTrackId, s.contextUri, storeVersion) {
            CustomTransitionStore.hasCustom(s.fromTrackId, s.toTrackId, s.contextUri)
        }
    } ?: false

    fun applyWorkingChange(updated: CustomTransition, markEdited: Boolean = true) {
        working = updated
        if (markEdited) hasUnsavedEdits = true
        TransitionPreviewPlayer.updateLive(updated)
    }

    fun resetToAuto() {
        val s = session ?: return
        TransitionPreviewPlayer.stop()
        CustomTransitionStore.remove(s.fromTrackId, s.toTrackId, s.contextUri)
        val autoTr = CustomTransition.fromAutoPlan(
            fromTrackId = s.fromTrackId,
            toTrackId = s.toTrackId,
            analysisA = s.analysisA,
            analysisB = s.analysisB,
            autoPlan = s.autoPlan,
        )
        working = autoTr
        hasUnsavedEdits = false
        Toast.makeText(context, context.getString(R.string.transition_toast_reset), Toast.LENGTH_SHORT).show()
    }

    fun saveTransition() {
        val s = session ?: return
        val normalized = working.copy(
            fromTrackId = s.fromTrackId,
            toTrackId = s.toTrackId,
            outgoingCurve = working.resolvedOutgoingCurve(s.autoPlan),
            incomingCurve = working.resolvedIncomingCurve(s.autoPlan),
            updatedAtMs = System.currentTimeMillis(),
        )
        working = normalized
        CustomTransitionStore.save(normalized, s.contextUri)
        hasUnsavedEdits = false
        Toast.makeText(context, context.getString(R.string.transition_toast_saved), Toast.LENGTH_SHORT).show()
    }

    val pageBg = if (starryEnabled && AppBackground == Color.Transparent) Color.Transparent else AppBackground
    val accentInk = if (AppAccent.luminance() > 0.45f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (pageBg == Color.Transparent) Modifier else Modifier.background(pageBg))
            .statusBarsPadding(),
    ) {
        // ── Header ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = {
                TransitionPreviewPlayer.stop()
                navController.popBackStack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.accessibility_navigate_back),
                    tint = AppText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.transition_editor_title),
                        color = AppText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    PresetBadge(preset = working.preset, hasCustomSaved = hasCustomSaved || hasUnsavedEdits)
                }
                Text(
                    text = stringResource(R.string.transition_editor_subtitle),
                    color = AppMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (session != null && (hasCustomSaved || hasUnsavedEdits || working.preset != TransitionPreset.AUTO)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(tone(0xFF242B3D))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .clickable { resetToAuto() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AppText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.transition_reset_auto), color = AppText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(6.dp))
            }
            if (session != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppAccent)
                        .clickable { saveTransition() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = accentInk, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (hasUnsavedEdits) stringResource(R.string.transition_apply) else stringResource(R.string.transition_save_short),
                        color = accentInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Limitation / Status Banner ──
            if (!localActive || session == null) {
                LimitationBanner(localActive = localActive, hasSession = session != null)
            }

            val currentSession = session
            if (currentSession != null) {
                // ── Dual-Track Cards & Metadata Badges ──
                DualTrackHeaderCards(
                    session = currentSession,
                    transition = working,
                )

                // ── Mode Toggle (Volume | Overlap) + EQ Bass Swap ──
                ModeAndEqBar(
                    mode = editorMode,
                    onModeChange = { editorMode = it },
                    eqSwap = working.eqSwap,
                    onToggleEqSwap = {
                        applyWorkingChange(working.copy(eqSwap = !working.eqSwap))
                    },
                )

                // ── Shared Dual-Waveform Timeline Canvas ──
                DualWaveformTimelineCard(
                    session = currentSession,
                    transition = working,
                    mode = editorMode,
                    previewState = previewState,
                    onTransitionChange = { updated ->
                        applyWorkingChange(updated)
                    },
                )

                // ── Mode-Specific Fine Controls (Overlap Bar Picker / Cue Nudge OR Volume Curve Summary) ──
                AnimatedVisibility(visible = editorMode == EditorMode.OVERLAP) {
                    OverlapControlsPanel(
                        session = currentSession,
                        transition = working,
                        onTransitionChange = { updated ->
                            applyWorkingChange(updated)
                        },
                    )
                }

                AnimatedVisibility(visible = editorMode == EditorMode.VOLUME) {
                    VolumeControlsPanel(
                        session = currentSession,
                        transition = working,
                        onTransitionChange = { updated ->
                            applyWorkingChange(updated)
                        },
                    )
                }

                // ── Preset Selector Bar (Auto | Fade | Rise | Custom) ──
                PresetSelectorBar(
                    selected = working.preset,
                    onSelectPreset = { picked ->
                        val curves = CustomTransition.defaultCurvesFor(picked, currentSession.autoPlan)
                        val updated = if (picked == TransitionPreset.AUTO && currentSession.autoPlan != null) {
                            val baseAuto = CustomTransition.fromAutoPlan(
                                fromTrackId = currentSession.fromTrackId,
                                toTrackId = currentSession.toTrackId,
                                analysisA = currentSession.analysisA,
                                analysisB = currentSession.analysisB,
                                autoPlan = currentSession.autoPlan,
                            )
                            working.copy(
                                preset = TransitionPreset.AUTO,
                                exitA = baseAuto.exitA,
                                entryB = baseAuto.entryB,
                                overlapBars = baseAuto.overlapBars,
                                overlapSec = baseAuto.overlapSec,
                                outgoingCurve = curves.first,
                                incomingCurve = curves.second,
                                eqSwap = baseAuto.eqSwap,
                            )
                        } else {
                            working.copy(
                                preset = picked,
                                outgoingCurve = curves.first,
                                incomingCurve = curves.second,
                            )
                        }
                        applyWorkingChange(updated)
                    },
                )

                // ── Preview Transport & Save Bar ──
                PreviewTransportAndSaveCard(
                    session = currentSession,
                    transition = working,
                    previewState = previewState,
                    hasUnsavedEdits = hasUnsavedEdits,
                    hasCustomSaved = hasCustomSaved,
                    onTogglePreview = {
                        if (previewState.playing || previewState.preparing) {
                            TransitionPreviewPlayer.stop()
                        } else {
                            TransitionPreviewPlayer.play(currentSession, working)
                        }
                    },
                    onSave = { saveTransition() },
                )
            }

            Spacer(Modifier.height(com.music.spotui.ui.components.bottomBarsSpace()))
        }
    }
}

@Composable
private fun PresetBadge(preset: TransitionPreset, hasCustomSaved: Boolean) {
    val label = when (preset) {
        TransitionPreset.AUTO -> if (hasCustomSaved) stringResource(R.string.transition_preset_auto_edited) else stringResource(R.string.transition_preset_auto)
        TransitionPreset.FADE -> stringResource(R.string.transition_preset_fade)
        TransitionPreset.RISE -> stringResource(R.string.transition_preset_rise)
        TransitionPreset.CUSTOM -> stringResource(R.string.transition_preset_custom)
    }
    val tint = when (preset) {
        TransitionPreset.AUTO -> OutgoingDeckColor
        TransitionPreset.FADE -> AstralTrailLight
        TransitionPreset.RISE -> IncomingDeckColor
        TransitionPreset.CUSTOM -> AppAccent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LimitationBanner(localActive: Boolean, hasSession: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E293B).copy(alpha = 0.92f),
                        Color(0xFF0F172A).copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), shape)
            .padding(14.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (!localActive) {
                    stringResource(R.string.transition_banner_engine_title)
                } else {
                    stringResource(R.string.transition_banner_queue_title)
                },
                color = AppText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (!localActive) {
                    stringResource(R.string.transition_banner_engine_body)
                } else if (!hasSession) {
                    stringResource(R.string.transition_banner_queue_body)
                } else {
                    ""
                },
                color = AppMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DualTrackHeaderCards(
    session: TransitionPairSession,
    transition: CustomTransition,
) {
    val analysisA = session.analysisA
    val analysisB = session.analysisB
    val harmonic = remember(analysisA, analysisB) { computeHarmonicMatch(analysisA, analysisB) }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GridBackground.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Track A (Outgoing)
        TrackDeckRow(
            deckLabel = stringResource(R.string.transition_deck_a),
            deckColor = OutgoingDeckColor,
            title = session.titleA.ifBlank { stringResource(R.string.transition_track_outgoing) },
            artist = session.artistA.ifBlank { stringResource(R.string.transition_artist_unknown) },
            coverUrl = session.coverA,
            bpm = analysisA?.tail?.bpm ?: analysisA?.bpm,
            camelot = analysisA?.camelotKey ?: "—",
            cueLabel = stringResource(R.string.transition_cue_exit, formatTimeSec(transition.exitA)),
            analyzing = session.analyzing && analysisA == null,
        )

        // Harmonic & Tempo Bridge Pill Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(harmonic.color),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.transition_harmonic_fmt, stringResource(harmonic.labelRes)),
                    color = harmonic.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "${transition.overlapBars} bars · ${String.format(Locale.US, "%.1f s", transition.overlapSec)} overlap",
                color = AppText.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Track B (Incoming)
        TrackDeckRow(
            deckLabel = stringResource(R.string.transition_deck_b),
            deckColor = IncomingDeckColor,
            title = session.titleB.ifBlank { stringResource(R.string.transition_track_incoming) },
            artist = session.artistB.ifBlank { stringResource(R.string.transition_artist_unknown) },
            coverUrl = session.coverB,
            bpm = analysisB?.head?.bpm ?: analysisB?.bpm,
            camelot = analysisB?.camelotKey ?: "—",
            cueLabel = stringResource(R.string.transition_cue_entry, formatTimeSec(transition.entryB)),
            analyzing = session.analyzing && analysisB == null,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun TrackDeckRow(
    deckLabel: String,
    deckColor: Color,
    title: String,
    artist: String,
    coverUrl: String?,
    bpm: Double?,
    camelot: String,
    cueLabel: String,
    analyzing: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tone(0xFF1E2433))
                .border(1.dp, deckColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!coverUrl.isNullOrBlank()) {
                GlideImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = deckColor, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = deckLabel,
                    color = deckColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· $cueLabel",
                    color = AppMuted,
                    fontSize = 10.sp,
                )
            }
            Text(
                text = title,
                color = AppText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = AppMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (analyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = deckColor,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.transition_analyzing), color = deckColor, fontSize = 11.sp)
                }
            } else {
                // BPM Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(deckColor.copy(alpha = 0.14f))
                        .border(0.5.dp, deckColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (bpm != null && bpm.isFinite() && bpm > 30.0) {
                            stringResource(R.string.transition_bpm_fmt, bpm.roundToInt().toString())
                        } else {
                            stringResource(R.string.transition_bpm_unknown)
                        },
                        color = deckColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Camelot Key Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.transition_key_fmt, camelot),
                    color = AppText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ModeAndEqBar(
    mode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    eqSwap: Boolean,
    onToggleEqSwap: () -> Unit,
) {
    val accentInk = if (AppAccent.luminance() > 0.45f) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Segmented Mode Toggle: Volume | Overlap
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GridBackground)
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                .padding(3.dp),
        ) {
            listOf(
                EditorMode.VOLUME to stringResource(R.string.transition_mode_volume),
                EditorMode.OVERLAP to stringResource(R.string.transition_mode_overlap),
            ).forEach { (m, label) ->
                val selected = mode == m
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AppAccent else Color.Transparent)
                        .clickable { onModeChange(m) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        color = if (selected) accentInk else AppText,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // EQ Bass Swap Chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (eqSwap) OutgoingDeckColor.copy(alpha = 0.18f) else GridBackground)
                .border(
                    1.dp,
                    if (eqSwap) OutgoingDeckColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(50),
                )
                .clickable { onToggleEqSwap() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = if (eqSwap) OutgoingDeckColor else AppMuted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (eqSwap) stringResource(R.string.transition_bass_swap_on) else stringResource(R.string.transition_bass_swap_off),
                color = if (eqSwap) OutgoingDeckColor else AppMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DualWaveformTimelineCard(
    session: TransitionPairSession,
    transition: CustomTransition,
    mode: EditorMode,
    previewState: TransitionPreviewPlayer.PreviewState,
    onTransitionChange: (CustomTransition) -> Unit,
) {
    val analysisA = session.analysisA
    val analysisB = session.analysisB
    val overlapSec = transition.overlapSec.coerceAtLeast(1.0)
    val preWindow = max(3.0, min(8.0, overlapSec * 0.45))
    val postWindow = max(3.0, min(8.0, overlapSec * 0.45))
    val totalWindowSec = preWindow + overlapSec + postWindow

    val bins = 96
    val waveA = remember(analysisA, transition.exitA, overlapSec, preWindow, postWindow) {
        val fromA = transition.exitA - preWindow
        val toA = transition.exitA + overlapSec + postWindow
        analysisA?.sampleWaveform(fromA, toA, bins) ?: syntheticFallbackWaveform(bins, seed = 1)
    }
    val waveB = remember(analysisB, transition.entryB, overlapSec, preWindow, postWindow) {
        val fromB = transition.entryB - preWindow
        val toB = transition.entryB + overlapSec + postWindow
        analysisB?.sampleWaveform(fromB, toB, bins) ?: syntheticFallbackWaveform(bins, seed = 2)
    }

    val outPoints = remember(transition.outgoingCurve, transition.preset, session.autoPlan) {
        ensureFiveControlPoints(transition.resolvedOutgoingCurve(session.autoPlan), defaultOutgoing = true)
    }
    val inPoints = remember(transition.incomingCurve, transition.preset, session.autoPlan) {
        ensureFiveControlPoints(transition.resolvedIncomingCurve(session.autoPlan), defaultOutgoing = false)
    }

    val currentTransition by rememberUpdatedState(transition)
    val currentSession by rememberUpdatedState(session)
    val currentOutPoints by rememberUpdatedState(outPoints)
    val currentInPoints by rememberUpdatedState(inPoints)
    val currentOverlapSec by rememberUpdatedState(overlapSec)
    val currentPreWindow by rememberUpdatedState(preWindow)
    val currentTotalWindowSec by rememberUpdatedState(totalWindowSec)
    val currentOnTransitionChange by rememberUpdatedState(onTransitionChange)

    // Active drag state
    var activeDeck by remember { mutableIntStateOf(0) } // 0 = none, 1 = Deck A (top), 2 = Deck B (bottom)
    var activePointIndex by remember { mutableIntStateOf(-1) }
    var dragAccumSec by remember { mutableStateOf(0.0) }

    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1325),
                        Color(0xFF101B33),
                        Color(0xFF0B1325),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(12.dp),
    ) {
        // Timeline top legend
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (mode == EditorMode.VOLUME) {
                    stringResource(R.string.transition_hint_volume)
                } else {
                    stringResource(R.string.transition_hint_overlap)
                },
                color = AstralTrailLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = "${transition.overlapBars} BARS (${String.format(Locale.US, "%.1fs", overlapSec)})",
                color = AppAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF060B16))
                .pointerInput(mode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val midY = h * 0.5f
                            val preW = currentPreWindow
                            val ovSec = currentOverlapSec
                            val totW = currentTotalWindowSec
                            val ovStartX = (preW / totW).toFloat() * w
                            val ovEndX = ((preW + ovSec) / totW).toFloat() * w
                            val ovW = (ovEndX - ovStartX).coerceAtLeast(1f)
                            val outPts = currentOutPoints
                            val inPts = currentInPoints

                            if (mode == EditorMode.VOLUME) {
                                // Find nearest control point across Deck A (top lane) and Deck B (bottom lane)
                                val topPad = 14f
                                val laneH = midY - topPad * 2f
                                var bestDeck = 0
                                var bestIdx = -1
                                var bestDist = Float.MAX_VALUE

                                outPts.forEachIndexed { idx, pt ->
                                    val px = ovStartX + pt.t * ovW
                                    val py = topPad + (1f - pt.gain) * laneH
                                    val d = hypot(offset.x - px, offset.y - py)
                                    if (d < bestDist) {
                                        bestDist = d
                                        bestDeck = 1
                                        bestIdx = idx
                                    }
                                }
                                inPts.forEachIndexed { idx, pt ->
                                    val px = ovStartX + pt.t * ovW
                                    val py = midY + topPad + (1f - pt.gain) * laneH
                                    val d = hypot(offset.x - px, offset.y - py)
                                    if (d < bestDist) {
                                        bestDist = d
                                        bestDeck = 2
                                        bestIdx = idx
                                    }
                                }
                                if (bestDist <= 72f) {
                                    activeDeck = bestDeck
                                    activePointIndex = bestIdx
                                } else {
                                    // Fallback: pick closest point in the tapped lane
                                    activeDeck = if (offset.y < midY) 1 else 2
                                    val pts = if (activeDeck == 1) outPts else inPts
                                    val normX = ((offset.x - ovStartX) / ovW).coerceIn(0f, 1f)
                                    activePointIndex = pts.indices.minByOrNull { abs(pts[it].t - normX) } ?: 2
                                }
                            } else {
                                // Overlap mode: top lane shifts exitA, bottom lane shifts entryB
                                activeDeck = if (offset.y < midY) 1 else 2
                                activePointIndex = -1
                                dragAccumSec = 0.0
                            }
                        },
                        onDragEnd = {
                            if (mode == EditorMode.OVERLAP && activeDeck != 0) {
                                val s = currentSession
                                val tr = currentTransition
                                // Snap to nearest beat on release when beat grid exists
                                val snapped = snapTransitionToNearestBeat(
                                    transition = tr,
                                    analysisA = s.analysisA,
                                    analysisB = s.analysisB,
                                    durationA = s.durationA,
                                    durationB = s.durationB,
                                    snapA = activeDeck == 1,
                                    snapB = activeDeck == 2,
                                )
                                currentOnTransitionChange(snapped)
                            }
                            activeDeck = 0
                            activePointIndex = -1
                            dragAccumSec = 0.0
                        },
                        onDragCancel = {
                            activeDeck = 0
                            activePointIndex = -1
                            dragAccumSec = 0.0
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val midY = h * 0.5f
                            val preW = currentPreWindow
                            val ovSec = currentOverlapSec
                            val totW = currentTotalWindowSec
                            val ovStartX = (preW / totW).toFloat() * w
                            val ovEndX = ((preW + ovSec) / totW).toFloat() * w
                            val ovW = (ovEndX - ovStartX).coerceAtLeast(1f)
                            val tr = currentTransition
                            val s = currentSession
                            val outPts = currentOutPoints
                            val inPts = currentInPoints

                            if (mode == EditorMode.VOLUME && activePointIndex in 0..4) {
                                val topPad = 14f
                                val laneH = (midY - topPad * 2f).coerceAtLeast(1f)
                                val laneTop = if (activeDeck == 1) topPad else midY + topPad
                                val newGain = (1f - ((change.position.y - laneTop) / laneH)).coerceIn(0f, 1f)

                                if (activeDeck == 1) {
                                    val updatedPts = outPts.toMutableList()
                                    val oldPt = updatedPts[activePointIndex]
                                    val minT = if (activePointIndex == 0) 0f else updatedPts[activePointIndex - 1].t + 0.04f
                                    val maxT = if (activePointIndex == 4) 1f else updatedPts[activePointIndex + 1].t - 0.04f
                                    val newT = if (activePointIndex == 0 || activePointIndex == 4) {
                                        oldPt.t
                                    } else {
                                        ((change.position.x - ovStartX) / ovW).coerceIn(minT, maxT)
                                    }
                                    updatedPts[activePointIndex] = EnvelopePoint(newT, newGain)
                                    currentOnTransitionChange(
                                        tr.copy(
                                            preset = TransitionPreset.CUSTOM,
                                            outgoingCurve = updatedPts,
                                            incomingCurve = inPts,
                                        ),
                                    )
                                } else if (activeDeck == 2) {
                                    val updatedPts = inPts.toMutableList()
                                    val oldPt = updatedPts[activePointIndex]
                                    val minT = if (activePointIndex == 0) 0f else updatedPts[activePointIndex - 1].t + 0.04f
                                    val maxT = if (activePointIndex == 4) 1f else updatedPts[activePointIndex + 1].t - 0.04f
                                    val newT = if (activePointIndex == 0 || activePointIndex == 4) {
                                        oldPt.t
                                    } else {
                                        ((change.position.x - ovStartX) / ovW).coerceIn(minT, maxT)
                                    }
                                    updatedPts[activePointIndex] = EnvelopePoint(newT, newGain)
                                    currentOnTransitionChange(
                                        tr.copy(
                                            preset = TransitionPreset.CUSTOM,
                                            outgoingCurve = outPts,
                                            incomingCurve = updatedPts,
                                        ),
                                    )
                                }
                            } else if (mode == EditorMode.OVERLAP) {
                                // Dragging waveform right shifts track earlier (cue point decreases); dragging left shifts cue later
                                val deltaSec = -(dragAmount.x / w) * totW
                                if (activeDeck == 1) {
                                    val maxExitA = max(0.5, s.durationA - ovSec - 0.5)
                                    val newExitA = (tr.exitA + deltaSec).coerceIn(0.0, maxExitA)
                                    val newOverlapSec = CustomTransition.barsToSeconds(
                                        s.analysisA,
                                        newExitA,
                                        tr.overlapBars,
                                        s.analysisA?.bpm ?: 120.0,
                                    )
                                    currentOnTransitionChange(
                                        tr.copy(
                                            exitA = newExitA,
                                            overlapSec = newOverlapSec,
                                        ),
                                    )
                                } else if (activeDeck == 2) {
                                    val maxEntryB = max(0.5, s.durationB - ovSec - 0.5)
                                    val newEntryB = (tr.entryB + deltaSec).coerceIn(0.0, maxEntryB)
                                    currentOnTransitionChange(
                                        tr.copy(
                                            entryB = newEntryB,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h * 0.5f
                val ovStartX = (preWindow / totalWindowSec).toFloat() * w
                val ovEndX = ((preWindow + overlapSec) / totalWindowSec).toFloat() * w
                val ovW = (ovEndX - ovStartX).coerceAtLeast(1f)

                // 1. Highlighted Overlap Region in the center
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            OutgoingDeckColor.copy(alpha = 0.08f),
                            IncomingDeckColor.copy(alpha = 0.14f),
                            OutgoingDeckColor.copy(alpha = 0.08f),
                        ),
                        startX = ovStartX,
                        endX = ovEndX,
                    ),
                    topLeft = Offset(ovStartX, 0f),
                    size = Size(ovW, h),
                )

                // 2. Beat & Bar Grid Tick Marks inside the Overlap Region
                val barsCount = transition.overlapBars.coerceIn(1, 32)
                for (b in 0..barsCount) {
                    val x = ovStartX + (b.toFloat() / barsCount.toFloat()) * ovW
                    val isMajor = b == 0 || b == barsCount || b % 4 == 0
                    drawLine(
                        color = Color.White.copy(alpha = if (isMajor) 0.25f else 0.10f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = if (isMajor) 1.5f else 1f,
                    )
                }

                // Boundary lines at exitA (ovStartX) and endA (ovEndX)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                drawLine(
                    color = OutgoingDeckColor.copy(alpha = 0.65f),
                    start = Offset(ovStartX, 0f),
                    end = Offset(ovStartX, h),
                    strokeWidth = 2f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = IncomingDeckColor.copy(alpha = 0.65f),
                    start = Offset(ovEndX, 0f),
                    end = Offset(ovEndX, h),
                    strokeWidth = 2f,
                    pathEffect = dashEffect,
                )

                // Center horizontal divider between Deck A and Deck B
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1f,
                )

                // 3. Mirrored Waveform Envelopes for Track A (Top Lane) & Track B (Bottom Lane)
                val barSlotW = w / bins.toFloat()
                val barW = (barSlotW * 0.66f).coerceAtLeast(2f)
                val laneCenterA = midY * 0.5f
                val laneCenterB = midY + midY * 0.5f
                val maxBarHalfH = midY * 0.40f

                for (i in 0 until bins) {
                    val cx = i * barSlotW + barSlotW * 0.5f
                    val inOverlap = cx in ovStartX..ovEndX
                    val overlapNorm = ((cx - ovStartX) / ovW).coerceIn(0f, 1f)

                    // Track A waveform bar (active before overlap + during overlap)
                    val ampA = waveA[i].coerceIn(0.05f, 1f)
                    val envGainA = when {
                        cx < ovStartX -> 1f
                        cx <= ovEndX -> transition.gainAAt(overlapNorm)
                        else -> 0.18f
                    }
                    val alphaA = when {
                        cx < ovStartX -> 0.72f
                        inOverlap -> (0.28f + 0.68f * envGainA).coerceIn(0.22f, 0.96f)
                        else -> 0.16f
                    }
                    val halfHA = (ampA * maxBarHalfH * (if (cx > ovEndX) 0.45f else 0.35f + 0.65f * envGainA)).coerceAtLeast(2f)
                    drawRoundRect(
                        color = OutgoingDeckColor.copy(alpha = alphaA),
                        topLeft = Offset(cx - barW * 0.5f, laneCenterA - halfHA),
                        size = Size(barW, halfHA * 2f),
                        cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
                    )

                    // Track B waveform bar (active during overlap + after overlap)
                    val ampB = waveB[i].coerceIn(0.05f, 1f)
                    val envGainB = when {
                        cx < ovStartX -> 0.18f
                        cx <= ovEndX -> transition.gainBAt(overlapNorm)
                        else -> 1f
                    }
                    val alphaB = when {
                        cx < ovStartX -> 0.16f
                        inOverlap -> (0.28f + 0.68f * envGainB).coerceIn(0.22f, 0.96f)
                        else -> 0.72f
                    }
                    val halfHB = (ampB * maxBarHalfH * (if (cx < ovStartX) 0.45f else 0.35f + 0.65f * envGainB)).coerceAtLeast(2f)
                    drawRoundRect(
                        color = IncomingDeckColor.copy(alpha = alphaB),
                        topLeft = Offset(cx - barW * 0.5f, laneCenterB - halfHB),
                        size = Size(barW, halfHB * 2f),
                        cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f),
                    )
                }

                // 4. Outgoing & Incoming Volume Envelope Curves + Draggable Control Handles
                val topPad = 14f
                val laneH = (midY - topPad * 2f).coerceAtLeast(1f)

                drawEnvelopeCurve(
                    points = outPoints,
                    ovStartX = ovStartX,
                    ovEndX = ovEndX,
                    canvasWidth = w,
                    laneTop = topPad,
                    laneHeight = laneH,
                    color = OutgoingDeckColor,
                    isOutgoing = true,
                    showHandles = mode == EditorMode.VOLUME,
                    activeIndex = if (activeDeck == 1) activePointIndex else -1,
                )

                drawEnvelopeCurve(
                    points = inPoints,
                    ovStartX = ovStartX,
                    ovEndX = ovEndX,
                    canvasWidth = w,
                    laneTop = midY + topPad,
                    laneHeight = laneH,
                    color = IncomingDeckColor,
                    isOutgoing = false,
                    showHandles = mode == EditorMode.VOLUME,
                    activeIndex = if (activeDeck == 2) activePointIndex else -1,
                )

                // 5. Live Preview Playhead Vertical Line
                if (previewState.playing || previewState.preparing) {
                    val playheadX = ovStartX + previewState.overlapProgress * ovW
                    if (playheadX in 0f..w) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.35f),
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, h),
                            strokeWidth = 6f,
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, h),
                            strokeWidth = 2f,
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = Offset(playheadX, midY),
                        )
                    }
                }
            }

            // Lane Corner Labels
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = "A · ${formatTimeSec(transition.exitA)}",
                    color = OutgoingDeckColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = "B · ${formatTimeSec(transition.entryB)}",
                    color = IncomingDeckColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEnvelopeCurve(
    points: List<EnvelopePoint>,
    ovStartX: Float,
    ovEndX: Float,
    canvasWidth: Float,
    laneTop: Float,
    laneHeight: Float,
    color: Color,
    isOutgoing: Boolean,
    showHandles: Boolean,
    activeIndex: Int,
) {
    if (points.isEmpty()) return
    val ovW = (ovEndX - ovStartX).coerceAtLeast(1f)
    val mapped = points.map { pt ->
        Offset(
            x = ovStartX + pt.t.coerceIn(0f, 1f) * ovW,
            y = laneTop + (1f - pt.gain.coerceIn(0f, 1f)) * laneHeight,
        )
    }

    val curvePath = Path().apply {
        if (isOutgoing) {
            moveTo(0f, mapped.first().y)
            lineTo(mapped.first().x, mapped.first().y)
        } else {
            moveTo(mapped.first().x, mapped.first().y)
        }
        for (i in 0 until mapped.size - 1) {
            val p0 = mapped[i]
            val p1 = mapped[i + 1]
            val ctrlX = (p0.x + p1.x) * 0.5f
            cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
        }
        if (!isOutgoing) {
            lineTo(canvasWidth, mapped.last().y)
        }
    }

    // Soft outer glow
    drawPath(
        path = curvePath,
        color = color.copy(alpha = 0.28f),
        style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    // Main crisp curve
    drawPath(
        path = curvePath,
        color = color,
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    if (showHandles) {
        mapped.forEachIndexed { idx, center ->
            val isActive = idx == activeIndex
            drawCircle(
                color = color.copy(alpha = if (isActive) 0.42f else 0.22f),
                radius = if (isActive) 13f else 9f,
                center = center,
            )
            drawCircle(
                color = Color(0xFF060B16),
                radius = if (isActive) 7f else 5.5f,
                center = center,
            )
            drawCircle(
                color = if (isActive) Color.White else color,
                radius = if (isActive) 5.5f else 4f,
                center = center,
            )
        }
    }
}

@Composable
private fun OverlapControlsPanel(
    session: TransitionPairSession,
    transition: CustomTransition,
    onTransitionChange: (CustomTransition) -> Unit,
) {
    val analysisA = session.analysisA
    val analysisB = session.analysisB
    val barChoices = remember { CustomTransition.BAR_CHOICES.toList() }
    val accentInk = if (AppAccent.luminance() > 0.45f) Color.Black else Color.White
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GridBackground.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Bar Count Selector Pill Row (1, 2, 4, 8, 12, 16 bars)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.transition_overlap_length),
                color = AppText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = String.format(Locale.US, "%.1f sec", transition.overlapSec),
                color = AppAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            barChoices.forEach { bars ->
                val selected = transition.overlapBars == bars
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AppAccent else tone(0xFF222A3A))
                        .border(
                            1.dp,
                            if (selected) AppAccent else Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(50),
                        )
                        .clickable {
                            val sec = CustomTransition.barsToSeconds(
                                analysisA = analysisA,
                                exitA = transition.exitA,
                                bars = bars,
                                fallbackBpm = analysisA?.bpm ?: 120.0,
                            )
                            val maxExitA = max(0.0, session.durationA - sec - 0.5)
                            val clampedExitA = transition.exitA.coerceIn(0.0, maxExitA)
                            onTransitionChange(
                                transition.copy(
                                    overlapBars = bars,
                                    overlapSec = sec,
                                    exitA = clampedExitA,
                                ),
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = stringResource(if (bars == 1) R.string.transition_bars_one else R.string.transition_bars_other, bars),
                        color = if (selected) accentInk else AppText,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // Fine Cue Point Stepper Buttons (-1 bar / +1 bar) for Track A and Track B
        CuePointNudgeRow(
            label = stringResource(R.string.transition_cue_a_label),
            timeText = formatTimeSec(transition.exitA),
            accent = OutgoingDeckColor,
            onStepBars = { deltaBars ->
                val barSec = CustomTransition.barsToSeconds(analysisA, transition.exitA, 1, analysisA?.bpm ?: 120.0)
                val maxExitA = max(0.0, session.durationA - transition.overlapSec - 0.5)
                val raw = (transition.exitA + deltaBars * barSec).coerceIn(0.0, maxExitA)
                val snapped = snapTimeToNearestBarOrBeat(analysisA, raw)
                val newOverlapSec = CustomTransition.barsToSeconds(
                    analysisA,
                    snapped,
                    transition.overlapBars,
                    analysisA?.bpm ?: 120.0,
                )
                onTransitionChange(
                    transition.copy(
                        exitA = snapped.coerceIn(0.0, maxExitA),
                        overlapSec = newOverlapSec,
                    ),
                )
            },
        )

        CuePointNudgeRow(
            label = stringResource(R.string.transition_cue_b_label),
            timeText = formatTimeSec(transition.entryB),
            accent = IncomingDeckColor,
            onStepBars = { deltaBars ->
                val barSec = CustomTransition.barsToSeconds(analysisB, transition.entryB, 1, analysisB?.bpm ?: 120.0)
                val maxEntryB = max(0.0, session.durationB - transition.overlapSec - 0.5)
                val raw = (transition.entryB + deltaBars * barSec).coerceIn(0.0, maxEntryB)
                val snapped = snapTimeToNearestBarOrBeat(analysisB, raw)
                onTransitionChange(
                    transition.copy(
                        entryB = snapped.coerceIn(0.0, maxEntryB),
                    ),
                )
            },
        )
    }
}

@Composable
private fun CuePointNudgeRow(
    label: String,
    timeText: String,
    accent: Color,
    onStepBars: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(text = label, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = timeText, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tone(0xFF222A3A))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable { onStepBars(-1) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, tint = AppText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.transition_minus_bar), color = AppText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tone(0xFF222A3A))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable { onStepBars(1) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AppText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.transition_plus_bar), color = AppText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun VolumeControlsPanel(
    session: TransitionPairSession,
    transition: CustomTransition,
    onTransitionChange: (CustomTransition) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val outCurve = remember(transition.outgoingCurve, transition.preset, session.autoPlan) {
        ensureFiveControlPoints(transition.resolvedOutgoingCurve(session.autoPlan), defaultOutgoing = true)
    }
    val inCurve = remember(transition.incomingCurve, transition.preset, session.autoPlan) {
        ensureFiveControlPoints(transition.resolvedIncomingCurve(session.autoPlan), defaultOutgoing = false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GridBackground.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.transition_envelopes_title),
                color = AppText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.transition_reset_curves),
                color = AppAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        val def = CustomTransition.defaultCurvesFor(
                            if (transition.preset == TransitionPreset.CUSTOM) TransitionPreset.FADE else transition.preset,
                            session.autoPlan,
                        )
                        onTransitionChange(
                            transition.copy(
                                outgoingCurve = def.first,
                                incomingCurve = def.second,
                            ),
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.transition_envelopes_hint),
            color = AppMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.transition_deck_a_curve) + outCurve.joinToString(" → ") { "${(it.gain * 100).roundToInt()}%" },
                color = OutgoingDeckColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.transition_deck_b_curve) + inCurve.joinToString(" → ") { "${(it.gain * 100).roundToInt()}%" },
                color = IncomingDeckColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PresetSelectorBar(
    selected: TransitionPreset,
    onSelectPreset: (TransitionPreset) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val accentInk = if (AppAccent.luminance() > 0.45f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GridBackground.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.transition_preset_title),
            color = AppText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                TransitionPreset.AUTO to stringResource(R.string.transition_preset_auto),
                TransitionPreset.FADE to stringResource(R.string.transition_preset_fade),
                TransitionPreset.RISE to stringResource(R.string.transition_preset_rise),
                TransitionPreset.CUSTOM to stringResource(R.string.transition_preset_custom),
            ).forEach { (preset, label) ->
                val isSelected = selected == preset
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AppAccent else tone(0xFF222A3A))
                        .border(
                            1.dp,
                            if (isSelected) AppAccent else Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) accentInk else AppText,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewTransportAndSaveCard(
    session: TransitionPairSession,
    transition: CustomTransition,
    previewState: TransitionPreviewPlayer.PreviewState,
    hasUnsavedEdits: Boolean,
    hasCustomSaved: Boolean,
    onTogglePreview: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val accentInk = if (AppAccent.luminance() > 0.45f) Color.Black else Color.White
    val isPreviewActive = previewState.playing || previewState.preparing
    val animatedProgress by animateFloatAsState(
        targetValue = if (isPreviewActive) previewState.progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 80),
        label = "previewProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GridBackground.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = when {
                        previewState.preparing -> stringResource(R.string.transition_preview_preparing)
                        previewState.playing -> stringResource(R.string.transition_preview_playing)
                        else -> stringResource(R.string.transition_preview_idle)
                    },
                    color = AppText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isPreviewActive) {
                        "${formatTimeSec(previewState.elapsedSec)} / ${formatTimeSec(previewState.totalSec)}"
                    } else {
                        stringResource(R.string.transition_preview_summary, formatTimeSec(transition.exitA), formatTimeSec(transition.entryB), String.format(Locale.US, "%.1f", transition.overlapSec))
                    },
                    color = AppMuted,
                    fontSize = 12.sp,
                )
            }
            if (hasCustomSaved && !hasUnsavedEdits) {
                Text(
                    text = stringResource(R.string.transition_saved),
                    color = Color(0xFF34D399),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(OutgoingDeckColor, IncomingDeckColor),
                        ),
                    ),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Play / Stop Preview Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isPreviewActive) tone(0xFF2B3548) else tone(0xFF222A3A))
                    .border(
                        1.dp,
                        if (isPreviewActive) OutgoingDeckColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onTogglePreview() }
                    .padding(vertical = 12.dp),
            ) {
                if (previewState.preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = OutgoingDeckColor,
                    )
                } else {
                    Icon(
                        imageVector = if (previewState.playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (previewState.playing) OutgoingDeckColor else AppText,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isPreviewActive) stringResource(R.string.transition_stop_preview) else stringResource(R.string.transition_preview_mix),
                    color = AppText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Save Transition Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppAccent)
                    .clickable { onSave() }
                    .padding(vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentInk,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.transition_save),
                    color = accentInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun computeHarmonicMatch(a: TrackAnalysis?, b: TrackAnalysis?): HarmonicMatch {
    if (a == null || b == null || a.key !in 0..23 || b.key !in 0..23) {
        return HarmonicMatch.UNKNOWN
    }
    val (na, la) = MixPlanner.camelot(a.key)
    val (nb, lb) = MixPlanner.camelot(b.key)
    val dist = min(Math.floorMod(na - nb, 12), Math.floorMod(nb - na, 12)) + if (la != lb) 1 else 0
    return when {
        dist <= 1 -> HarmonicMatch.COMPATIBLE
        dist == 2 -> HarmonicMatch.NEAR
        else -> HarmonicMatch.CLASH
    }
}

private fun ensureFiveControlPoints(
    curve: List<EnvelopePoint>,
    defaultOutgoing: Boolean,
): List<EnvelopePoint> {
    if (curve.size == 5) return curve.sortedBy { it.t }
    val defaultTs = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    if (curve.isEmpty()) {
        return defaultTs.map { t ->
            EnvelopePoint(t, if (defaultOutgoing) 1f - t else t)
        }
    }
    val sorted = curve.sortedBy { it.t }
    return defaultTs.map { t ->
        val exact = sorted.firstOrNull { abs(it.t - t) < 1e-4f }
        if (exact != null) {
            exact
        } else {
            val before = sorted.lastOrNull { it.t <= t } ?: sorted.first()
            val after = sorted.firstOrNull { it.t >= t } ?: sorted.last()
            val dt = after.t - before.t
            val g = if (dt <= 1e-5f) before.gain else before.gain + (after.gain - before.gain) * ((t - before.t) / dt)
            EnvelopePoint(t, g.coerceIn(0f, 1f))
        }
    }
}

private fun snapTimeToNearestBarOrBeat(analysis: TrackAnalysis?, timeSec: Double): Double {
    if (analysis == null || !timeSec.isFinite()) return timeSec
    if (analysis.barStarts.isNotEmpty()) {
        val nearestBar = analysis.barStarts.minByOrNull { abs(it - timeSec) }
        if (nearestBar != null && abs(nearestBar - timeSec) <= 1.2) return nearestBar
    }
    if (analysis.beats.isNotEmpty()) {
        val nearestBeat = analysis.beats.minByOrNull { abs(it - timeSec) }
        if (nearestBeat != null && abs(nearestBeat - timeSec) <= 0.45) return nearestBeat
    }
    return timeSec
}

private fun snapTransitionToNearestBeat(
    transition: CustomTransition,
    analysisA: TrackAnalysis?,
    analysisB: TrackAnalysis?,
    durationA: Double,
    durationB: Double,
    snapA: Boolean,
    snapB: Boolean,
): CustomTransition {
    var exitA = transition.exitA
    var entryB = transition.entryB
    if (snapA && analysisA != null) {
        val candidate = when {
            analysisA.beats.isNotEmpty() -> analysisA.beats.minByOrNull { abs(it - exitA) } ?: exitA
            analysisA.barStarts.isNotEmpty() -> analysisA.barStarts.minByOrNull { abs(it - exitA) } ?: exitA
            else -> exitA
        }
        val maxExitA = max(0.0, durationA - transition.overlapSec - 0.5)
        exitA = candidate.coerceIn(0.0, maxExitA)
    }
    if (snapB && analysisB != null) {
        val candidate = when {
            analysisB.beats.isNotEmpty() -> analysisB.beats.minByOrNull { abs(it - entryB) } ?: entryB
            analysisB.barStarts.isNotEmpty() -> analysisB.barStarts.minByOrNull { abs(it - entryB) } ?: entryB
            else -> entryB
        }
        val maxEntryB = max(0.0, durationB - transition.overlapSec - 0.5)
        entryB = candidate.coerceIn(0.0, maxEntryB)
    }
    val overlapSec = CustomTransition.barsToSeconds(
        analysisA = analysisA,
        exitA = exitA,
        bars = transition.overlapBars,
        fallbackBpm = analysisA?.bpm ?: 120.0,
    )
    return transition.copy(
        exitA = exitA,
        entryB = entryB,
        overlapSec = overlapSec,
    )
}

private fun syntheticFallbackWaveform(bins: Int, seed: Int): FloatArray {
    return FloatArray(bins) { i ->
        val phase = (i * 0.37 + seed * 1.9)
        val v = 0.42 + 0.28 * kotlin.math.sin(phase) + 0.16 * kotlin.math.cos(phase * 2.3)
        v.toFloat().coerceIn(0.12f, 0.92f)
    }
}

private fun formatTimeSec(sec: Double): String {
    if (!sec.isFinite() || sec < 0.0) return "0:00.0"
    val totalTenths = (sec * 10.0).roundToInt()
    val mins = totalTenths / 600
    val remTenths = totalTenths % 600
    val secs = remTenths / 10
    val tenths = remTenths % 10
    return String.format(Locale.US, "%d:%02d.%d", mins, secs, tenths)
}
