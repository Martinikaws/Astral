/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerLyrics.kt at edc1e6f.
 * Modified for Myspoti, 2026-10.
 *
 * Changes: LyricsPanel's list, PanelVoice, the gap dots, the loading skeleton and the
 * scroll lead / stagger, fed by Myspoti's LyricsViewModel. Myspoti's lyrics are mostly
 * line-synced, so a line lights whole (BitChord's line-level path) and the word sweep runs
 * only where a line has words. Particles and the player-controls hand-off are left out for now.
 * A line's backing vocals, romanization and translation (the Classic look's, from the same
 * LyricsViewModel) are smaller, dimmer lines under it; the second singer of a duet sits at the end of the row.
 */
package com.music.spotui.ui.immersive.lyrics

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.Translate
import com.music.spotui.ui.components.secondaryClick
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.engine.R
import app.myspoti.power.EnergySettings
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.AstralThemePref
import com.music.spotui.data.preferences.LyricsRomanizationPref
import com.music.spotui.data.preferences.SpicyBackgroundPref
import com.music.spotui.data.preferences.SpicyThemePreset
import com.music.spotui.spicy.SpicyBackgroundButton
import com.music.spotui.spicy.SpicyBackgroundSheet
import com.music.spotui.spicy.hasDrawableLines
import com.music.spotui.spicy.SpicyBackgroundLayer
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.StarryNightBackdrop
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.GLOW_ALPHA
import com.music.spotui.ui.look.GLOW_ROOM
import com.music.spotui.ui.look.INACTIVE_SCALE
import com.music.spotui.ui.look.LINE_FALLOFF_ALPHA
import com.music.spotui.ui.look.LINE_FALLOFF_BLUR
import com.music.spotui.ui.look.LYRIC_EASING
import com.music.spotui.ui.look.LYRIC_SETTLE_MS
import com.music.spotui.ui.look.SUNG_ALPHA
import com.music.spotui.ui.look.SweptLine
import com.music.spotui.ui.look.SweptLyricLine
import com.music.spotui.ui.look.UNSUNG_ALPHA
import com.music.spotui.ui.look.rememberArtworkColors
import com.music.spotui.ui.look.scaleForDistance
import com.music.spotui.ui.look.rememberReduceMotion
import com.music.spotui.ui.look.lastPress
import com.music.spotui.ui.look.tapTime
import com.music.spotui.ui.look.toSwept
import com.music.spotui.ui.theme.StarryAccent
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.music.spotui.ui.immersive.components.ImmersiveButton
import com.music.spotui.ui.immersive.components.immersiveClickable
import com.music.spotui.ui.immersive.glass
import com.music.spotui.ui.components.shareLyricLines
import com.music.spotui.ui.components.cappedLyricsWidth

/**
 * The lyrics of the playing track, inside the player: big tight type, the playing line crisp
 * and everything else falling out of focus the further it is from it. Blur needs API 31+, so
 * alpha carries the same hierarchy on older devices.
 *
 * Scrolling by hand clears the blur and suspends the auto-follow, so you can read ahead; a
 * moment after you stop it snaps back to the song. Tapping a line seeks to it; holding one that Genius explains opens the note,
 * holding any other (or "Share lyrics" in the song options) picks lines to share as text: taps then
 * select instead of seeking, and the follow waits.
 *
 * The list brings its own side gutter and top margin; [contentPadding] is the extra room the
 * list keeps clear of whatever the player draws over it.
 */
@Composable
fun ImmersiveLyricsPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** The background, romanize and translate buttons over the lines; off where nothing can be touched (the car). */
    showControls: Boolean = true,
    /** Keep the device screen on while synced lyrics play. Off in the car's window: it must not hold the phone awake. */
    keepScreenOn: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember(context) { StarryNightPref.init(context); AstralThemePref.init(context); SpicyBackgroundPref.init(context); true }
    val vm: LyricsViewModel = viewModel()
    val song = Graph.currentSongState
    val blend = com.music.spotui.ui.components.rememberSongBlend()
    val hold = blend?.outgoingWords == true
    val title = song.title.value
    val artist = song.singer.value
    val playingUri = SongPlayer.nowPlaying.value.uri
    val state = com.music.spotui.ui.screens.rememberLyricsState(vm, title, artist, hold)
    if (keepScreenOn) com.music.spotui.ui.screens.KeepScreenOnForLyrics(Graph.currentSongState.playingState.value && state is LyricsViewModel.State.Loaded)
    val liveKey = "$playingUri|${SongPlayer.currentTrackId}|$title|$artist"
    val heldKey = remember { arrayOf(liveKey) }
    if (!hold) heldKey[0] = liveKey
    val trackKey = if (hold) heldKey[0] else liveKey
    // Phone and car panels build the same clock from the same mix state; a closing panel only
    // resets it if it is still the one that set it, so it never clobbers the other's.
    DisposableEffect(hold, blend) {
        val mine: () -> Long = if (hold && blend != null) ({ blend.positionMs().coerceAtLeast(0L) }) else ({ SongPlayer.getCurrentPosition().coerceAtLeast(0L) })
        lyricsPosition = mine
        onDispose { if (lyricsPosition === mine) lyricsPosition = { SongPlayer.getCurrentPosition().coerceAtLeast(0L) } }
    }
    val selecting by vm.selecting.collectAsState()
    val picked by vm.picked.collectAsState()
    androidx.activity.compose.BackHandler(enabled = selecting) { vm.stopSelecting() }
    DisposableEffect(vm) { onDispose { vm.stopSelecting() } }

    // The Spicy Lyrics renderer and its background preset, unless it is switched off in the background
    // sheet, a translation is shown, lines are being picked to share (the classic list carries those),
    // or the lyrics have nothing to draw (the list shows its dots). Above the `when`, so the one engine
    // survives Loading and NotFound as well as the change of song: covers crossfade in it.
    val spicy by SpicyBackgroundPref.state.collectAsState()
    val loaded = state as? LyricsViewModel.State.Loaded
    val useSpicy = spicy.renderer && !selecting &&
        (loaded == null || (!loaded.showTranslation && hasDrawableLines(loaded.lyrics)))
    var spicySheet by remember { mutableStateOf(false) }
    if (spicySheet) SpicyBackgroundSheet { spicySheet = false }
    Box(modifier) {
    // Hosted by the player: the background is drawn by it, full screen, from the request published here.
    val backdropSlot = com.music.spotui.spicy.LocalSpicyBackdropSlot.current
    val reduceMotionNow = rememberReduceMotion()
    val playingNow = Graph.currentSongState.playingState.value
    val coverNow = song.coverUri.value
    if (backdropSlot != null) {
        val mine = remember { arrayOfNulls<com.music.spotui.spicy.SpicyBackdropRequest>(1) }
        if (useSpicy) androidx.compose.runtime.SideEffect {
            val r = com.music.spotui.spicy.SpicyBackdropRequest(
                coverUri = coverNow,
                artist = artist,
                playing = playingNow,
                positionMs = { lyricsPosition() },
                settings = spicy,
                reduceMotion = reduceMotionNow,
            )
            mine[0] = r
            backdropSlot.request = r
        }
        androidx.compose.runtime.SideEffect {
            if (!useSpicy && mine[0] != null) {
                if (backdropSlot.request === mine[0]) backdropSlot.request = null
                mine[0] = null
            }
        }
        DisposableEffect(backdropSlot) {
            onDispose { if (backdropSlot.request === mine[0]) backdropSlot.request = null }
        }
    } else if (useSpicy) SpicyBackgroundLayer(
        coverUri = coverNow,
        artist = artist,
        playing = playingNow,
        positionMs = { lyricsPosition() },
        settings = spicy,
        reduceMotion = reduceMotionNow,
    )
    when (val current = state) {
        is LyricsViewModel.State.Loading -> LyricsSkeleton(
            Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = Immersive.Gutter),
        )
        is LyricsViewModel.State.NotFound -> Box(
            Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = Immersive.Gutter),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.immersive_lyrics_none),
                style = Immersive.Headline,
                color = Immersive.TextSecondary,
            )
        }
        is LyricsViewModel.State.Loaded -> {
            Box(Modifier.fillMaxSize()) {
            key(trackKey) {
                val reduceMotion = rememberReduceMotion()
                val coverUri = song.coverUri.value
                val hasCover = coverUri.isNotBlank()
                val artworkPalette = rememberArtworkColors(coverUri.takeIf { it.isNotBlank() })
                // The palette always carries stock fallback colours; only trust it when there is a cover.
                val artworkColors = if (hasCover) artworkPalette.colors else emptyList()
                val spicyPreset by AstralThemePref.spicyTheme.collectAsState()
                val starryEnabled by StarryNightPref.enabled.collectAsState()
                val oledBlack = EnergySettings.oledBlack
                val customSpicy = spicyPreset != SpicyThemePreset.ASTRAL_DEFAULT
                val starryActive = !customSpicy && starryEnabled && !oledBlack

                val meshColors = remember(customSpicy, spicyPreset, artworkColors, Immersive.Accent) {
                    if (customSpicy) {
                        listOf(
                            Color(spicyPreset.accentHex),
                            Color(spicyPreset.glowHex),
                            Color(spicyPreset.bgBottomHex),
                            lerp(Color(spicyPreset.bgTopHex), Color(spicyPreset.accentHex), 0.40f),
                        )
                    } else {
                        (artworkColors + listOf(Immersive.Accent, Color.White)).take(4)
                    }
                }
                val glowTint = when {
                    customSpicy -> Color(spicyPreset.glowHex)
                    starryActive -> artworkColors.firstOrNull()?.let { lerp(it, StarryAccent, 0.45f) } ?: StarryAccent
                    else -> artworkColors.firstOrNull()?.let { lerp(it, Immersive.Accent, 0.45f) } ?: Immersive.Accent
                }
                // The sung line is white, tinted by the Look: starlight gold on Starry Night, a breath of the
                // cover's accent on Glassify.
                val look = com.music.spotui.data.preferences.LookPref.look
                val activeColor = when {
                    customSpicy -> Color(spicyPreset.accentHex)
                    look == com.music.spotui.data.preferences.Look.STARRY_NIGHT -> StarryAccent
                    look == com.music.spotui.data.preferences.Look.GLASSIFY -> lerp(Color.White, Immersive.Accent, 0.25f)
                    else -> Color.White
                }

                Box(Modifier.fillMaxSize()) {
                    if (!useSpicy) SpicyPanelBackdrop(
                        meshColors = meshColors,
                        customSpicy = customSpicy,
                        starryActive = starryActive,
                        oledBlack = oledBlack,
                        reduceMotion = reduceMotion,
                    )
                    // The backdrop spans the panel; the lines and their controls stay in a readable column.
                    Box(Modifier.fillMaxHeight().cappedLyricsWidth()) {
                    androidx.compose.animation.Crossfade(
                        targetState = current.lyrics, animationSpec = tween(LYRICS_SWAP_MS), label = "lyricsSwap",
                        modifier = Modifier.fillMaxSize(),
                    ) { shown ->
                        val live = shown === current.lyrics
                        if (useSpicy) SpicyImmersiveLyrics(
                            shown, trackKey, contentPadding,
                            romanizing = LyricsRomanizationPref.on && shown.hasRomanization,
                            modifier = Modifier.fillMaxSize(),
                        ) else LyricsList(
                            shown, trackKey, Modifier.fillMaxSize(), contentPadding, vm,
                            translations = current.translations.takeIf { live && current.showTranslation },
                            romanizing = LyricsRomanizationPref.on && shown.hasRomanization,
                            activeColor = activeColor,
                            glowTint = glowTint,
                        )
                    }
                    val target = com.music.spotui.ui.components.readerLanguage()
                    val canRomanize = current.lyrics.hasRomanization
                    val canTranslate = !LyricsViewModel.sameLanguage(current.sourceLanguage, target)
                    if (!selecting && showControls) {
                        val layoutDirection = LocalLayoutDirection.current
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = contentPadding.calculateTopPadding(),
                                    end = Immersive.Gutter + contentPadding.calculateEndPadding(layoutDirection),
                                ),
                        ) {
                            SpicyBackgroundButton(onClick = { spicySheet = true })
                            if (canRomanize) LyricsToggle(
                                icon = Icons.Rounded.Abc,
                                description = stringResource(R.string.lyrics_romanize),
                                on = LyricsRomanizationPref.on,
                                onClick = { LyricsRomanizationPref.set(context, !LyricsRomanizationPref.on) },
                            )
                            if (canTranslate) LyricsToggle(
                                icon = Icons.Rounded.Translate,
                                description = stringResource(R.string.lyrics_fullscreen_chip_label_translate),
                                on = current.showTranslation,
                                loading = current.translating && current.translationRequested,
                                onClick = { vm.toggleTranslation(target) },
                            )
                        }
                    }
                    if (selecting) ShareBar(
                        canShare = picked != null,
                        onCancel = { vm.stopSelecting() },
                        onShare = { picked?.let { shareLyricLines(context, current.lyrics, it, title, artist); vm.stopSelecting() } },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(contentPadding).padding(horizontal = Immersive.Gutter),
                    )
                    }
                }
            }
            }
        }
    }
    }
}

/**
 * Multi-blob slowly drifting Kawarp-style mesh backdrop extracted from album artwork (or
 * overridden by [SpicyThemePreset]), blending seamlessly with the surrounding player surface
 * and letting [StarryNightBackdrop] shine subtly underneath when Starry Night is active.
 */
@Composable
private fun SpicyPanelBackdrop(
    meshColors: List<Color>,
    customSpicy: Boolean,
    starryActive: Boolean,
    oledBlack: Boolean,
    reduceMotion: Boolean,
) {
    if (oledBlack && !customSpicy) return
    val animatedColors = (0 until 4).map { idx ->
        animateColorAsState(
            targetValue = meshColors.getOrElse(idx) { Immersive.Accent },
            animationSpec = tween(1_100),
            label = "panelBlob$idx",
        ).value
    }
    // Kept as State and read inside the draw lambda so the drift does not recompose each frame.
    val meshPhaseState = rememberInfiniteTransition(label = "immersiveMesh").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "meshPhase",
    )

    Box(Modifier.fillMaxSize()) {
        if (starryActive) {
            StarryNightBackdrop(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.34f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val fade = 36.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fade,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fade,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val meshPhase = if (reduceMotion) 0f else meshPhaseState.value
                    val strength = when {
                        oledBlack -> 0.14f
                        customSpicy -> 0.32f
                        starryActive -> 0.22f
                        else -> 0.28f
                    }
                    val anchors = arrayOf(
                        Offset(0.22f, 0.24f),
                        Offset(0.80f, 0.26f),
                        Offset(0.76f, 0.76f),
                        Offset(0.26f, 0.72f),
                    )
                    val speeds = floatArrayOf(1f, -0.78f, 0.84f, -1.08f)
                    for (i in 0 until 4) {
                        val angle = meshPhase * speeds[i] + i * 1.55f
                        val center = Offset(
                            size.width * (anchors[i].x + 0.12f * cos(angle)),
                            size.height * (anchors[i].y + 0.10f * sin(angle * 0.9f)),
                        )
                        val radius = size.maxDimension * (0.58f + 0.05f * (i % 2))
                        val alpha = strength * (if (i % 2 == 0) 1f else 0.76f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(animatedColors[i].copy(alpha = alpha), Color.Transparent),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                    }
                },
        )
    }
}

/** A round toggle over the lyrics, lit in the Look's accent while what it shows is on; a spinner while that is fetched. */
@Composable
private fun LyricsToggle(
    icon: ImageVector,
    description: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (on) Immersive.Accent else Color.White.copy(alpha = 0.14f))
            .immersiveClickable(onClick = onClick, enabled = !loading, pressedScale = 0.9f),
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(
                icon,
                contentDescription = description,
                tint = if (on) { if (Immersive.Accent.luminance() > 0.45f) Color.Black else Color.White } else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─── Rows ────────────────────────────────────────────────────────────────────────────────

/**
 * One row of the panel: a line of the song, or the break counted out between two of them.
 * [swept] carries the word timings where the source has them.
 */
private class PanelLine(
    val timeMs: Long,
    val text: String,
    val isGap: Boolean,
    /** Where a break ends; unused for a sung line. */
    val untilMs: Long,
    val swept: SweptLine,
    /** The line's place in the source's lines (the translation's), or -1 for a break of the panel's own. */
    val sourceIndex: Int = -1,
    /** Who sings it (1, 2...; 0 together), null when the source does not say. */
    val voice: Int? = null,
    /** The backing vocals sung over the line, and their word timings where the source has them. */
    val background: String? = null,
    val backgroundSwept: SweptLine? = null,
    val romanization: String? = null,
) {
    val isWordSynced: Boolean get() = !isGap && swept.isWordSynced

    /** When the line stops being sung, as far as the source says. */
    val endMs: Long get() = if (isGap) untilMs else swept.endMs
}

private fun isGapText(text: String): Boolean = text.isBlank() || text.trim() == "♪"

/** Breaks shorter than or equal to this before the first line are not worth counting out. */
private const val INTRO_GAP_MIN_MS = 4_000L

/** How long a break at the very end is counted over. */
private const val TRAILING_GAP_MS = 5_000L

private fun panelLines(lyrics: Lyrics, synced: Boolean, songDurationMs: Long): List<PanelLine> {
    val source = lyrics.lines
    val out = ArrayList<PanelLine>(source.size + 1)
    if (synced) {
        // An intro > 4 s: the panel counts it out rather than sitting on nothing.
        val firstSung = source.firstOrNull { !isGapText(it.text) }
        if (firstSung != null && firstSung.timeMs > INTRO_GAP_MIN_MS && source.first().timeMs > 0L) {
            out += PanelLine(0L, "", true, firstSung.timeMs, SweptLine(0L, ""), voice = firstSung.voice)
        }
    }
    source.forEachIndexed { index, line ->
        val next = source.getOrNull(index + 1)?.timeMs
        if (synced && isGapText(line.text)) {
            out += PanelLine(line.timeMs, "", true, next ?: (line.timeMs + TRAILING_GAP_MS), SweptLine(line.timeMs, ""), index, voice = line.voice)
        } else {
            val background = line.background?.trim()?.takeIf { it.isNotEmpty() }
            val romanization = line.romanization?.trim()?.takeIf { it.isNotEmpty() && !it.equals(line.text.trim(), ignoreCase = true) }
            val heuristicEnd = line.timeMs + (line.text.length * 90L).coerceIn(2_500L, 6_000L)
            val effectiveNext = when {
                next != null -> next.takeIf { it > line.timeMs } ?: heuristicEnd
                else -> songDurationMs.takeIf { it > line.timeMs } ?: heuristicEnd
            }
            val swept = line.toSwept(effectiveNext).let {
                if (synced && it.words.isEmpty() && effectiveNext > line.timeMs) it.copy(sungUntilMs = effectiveNext) else it
            }
            out += PanelLine(
                line.timeMs, line.text, false, 0L, swept, index,
                voice = line.voice, background = background,
                backgroundSwept = if (synced && background != null) backgroundSwept(line, effectiveNext) else null,
                romanization = romanization,
            )
            // Insert animated interlude dots when there is an instrumental gap > 4 s after the last word.
            val lastWordEnd = line.words?.lastOrNull()?.endMs
            if (synced && next != null && lastWordEnd != null && next - lastWordEnd > 4_000L && !isGapText(source[index + 1].text)) {
                val gapStart = lastWordEnd + 200L
                val gapVoice = source[index + 1].voice ?: line.voice
                out += PanelLine(gapStart, "", true, next, SweptLine(gapStart, ""), voice = gapVoice)
            }
        }
    }
    return out
}

/** The backing vocals of [line] as the sweep draws them, brackets off the words; null when they are not timed. */
private fun backgroundSwept(line: LyricLine, nextLineMs: Long?): SweptLine? {
    val words = line.backgroundWords.orEmpty()
        .map { it.copy(text = it.text.replace("(", "").replace(")", "")) }
        .filter { it.text.isNotBlank() }
    if (words.isEmpty()) return null
    val text = words.joinToString("") { it.text }.trim()
    return LyricLine(words.first().startMs, text, words).toSwept(nextLineMs).takeIf { it.isWordSynced }
}

/**
 * Which end of the row a line sits at in a duet: true the end (second vocalist: voice == 2 or > 1),
 * false the start (lead: voice == 1 or null), null for lines sung together (voice == 0) or non-duets.
 */
private fun endLane(row: PanelLine, duet: Boolean): Boolean? =
    if (duet && (row.voice == null || row.voice >= 1)) (row.voice ?: 1) > 1 else null

// ─── Constants (BitChord's, at its sizes) ───────────────────────────────────────────────

/** How tall a break stands while it is playing; nothing when it is not. */
private val GAP_ROW_HEIGHT = 44.dp
private val GAP_ROW_SPACING = 14.dp

/** How long other lyrics for the same song take to fade in over the ones shown. */
private const val LYRICS_SWAP_MS = 350

private const val BROWSING_ALPHA = 0.8f

/** A line under a finger dips, the way a button does. */
private const val PRESSED_SCALE = 0.96f

/** The break between verses, counted out rather than marked. */
private const val GAP_DOTS = 3
private val GAP_DOT_SIZE = 10.dp
private val GAP_DOT_GAP = 7.dp
private const val GAP_DOT_REST = 0.28f
private const val GAP_REST_SCALE = 0.82f
private const val GAP_RUNNING = -1f

/** How long the panel takes to settle on a new line, and how far ahead it starts. */
private const val SCROLL_LEAD_MIN_MS = 350L
private const val SCROLL_LEAD_MAX_MS = 500L

/**
 * How the rows fan out as the panel moves between lines: each row after the one being
 * scrolled to sets off slightly later than the row before it, up to a few rows back.
 */
private const val STAGGER_STEPS = 4
private const val STAGGER_FRACTION = 0.065f

/** One handover: how far the panel is going, and how long it is taking. */
private class ScrollRun(val id: Int, val delta: Float, val durationMs: Int) {
    /** The last row to arrive does so this long after the panel sets off. */
    val spanMs: Float get() = durationMs * (1f + STAGGER_FRACTION * STAGGER_STEPS)
}

/**
 * How long before a line lands the panel starts moving to it — and how long the move then
 * takes, which is the same number: the silence between the last word of the line being sung
 * and the first of the next, bounded either side.
 */
private fun scrollLead(rows: List<PanelLine>, positionMs: Long): Long {
    val current = rows.indexOfLast { it.timeMs <= positionMs }
    if (current < 0) return SCROLL_LEAD_MIN_MS
    val next = rows.getOrNull(current + 1) ?: return SCROLL_LEAD_MIN_MS
    val row = rows[current]
    // Line-synced rows end at the next start, so measure from the last sung word instead.
    val sungEnd = if (row.isGap) row.endMs else (row.swept.words.lastOrNull()?.endMs ?: (row.timeMs + (row.text.length * 90L).coerceIn(2_500L, 6_000L)))
    val gap = next.timeMs - sungEnd
    return gap.coerceIn(SCROLL_LEAD_MIN_MS, SCROLL_LEAD_MAX_MS)
}

/** The row being sung at [positionMs], or -1 before the first one. */
private fun activeRow(rows: List<PanelLine>, positionMs: Long): Int =
    rows.indexOfLast { it.timeMs <= positionMs }

// ─── The list ────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsList(
    lyrics: Lyrics,
    trackKey: String,
    modifier: Modifier,
    contentPadding: PaddingValues,
    vm: LyricsViewModel,
    /** The lines translated, in the source's order; null while no translation is shown. */
    translations: List<String>? = null,
    /** Shows the lines' romanization under them. */
    romanizing: Boolean = false,
    activeColor: Color = Color.White,
    glowTint: Color = Immersive.Accent,
) {
    // Lines picked to share (see ImmersiveLyricsPanel): taps select, the follow waits.
    val selecting by vm.selecting.collectAsState()
    val picked by vm.picked.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSynced = remember(lyrics) { lyrics.synced && lyrics.lines.any { it.timeMs > 0L } }
    val songDurationMs = SongPlayer.getDuration().coerceAtLeast(0L)
    val rows = remember(lyrics, isSynced, songDurationMs) { panelLines(lyrics, isSynced, songDurationMs) }
    val duet = remember(lyrics) { lyrics.hasDuet || lyrics.lines.any { (it.voice ?: 0) > 1 } }
    val playing = Graph.currentSongState.playingState.value
    val wordSynced = remember(lyrics) { lyrics.lines.any { !it.words.isNullOrEmpty() } }
    val head = rememberLyricsPlayhead(trackKey, playing && isSynced, if (wordSynced) LYRICS_WORD_FPS else LYRICS_LINE_FPS)
    val clock = head.clock

    // Genius's notes on the lines, read in the background when the song started (App.kt) and kept
    // by track id. A line it explains sits on a soft pill with a small quote mark after its last
    // word; the mark opens the note, and so does a long press anywhere on the line. Tap still seeks.
    val trackId = SongPlayer.nowPlaying.value.uri.substringAfterLast(':')
    val genTitle = Graph.currentSongState.title.value
    val genArtist = Graph.currentSongState.singer.value
    val genLang = com.music.spotui.ui.components.readerLanguage()
    val meaning = com.music.spotui.data.api.GeniusMeaning.currentFor(trackId)
    LaunchedEffect(trackId, genTitle, genArtist) {
        if (meaning == null) com.music.spotui.data.api.GeniusMeaning.prefetch(trackId, genTitle, genArtist, genLang)
    }
    val notes = remember(rows, meaning) {
        meaning?.let { com.music.spotui.ui.components.notesByLine(it, rows.map { r -> r.text }) }.orEmpty()
    }
    var noteOpen by remember(trackKey) { mutableStateOf<Int?>(null) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    noteOpen?.let { n ->
        com.music.spotui.ui.immersive.sheets.ImmersiveMeaningSheet(
            trackId, genTitle, genArtist, n, coverUrl = Graph.currentSongState.coverUri.value,
        ) { noteOpen = null }
    }
    val noteMeasurer = rememberTextMeasurer(cacheSize = 16)

    val activeRow by remember(rows, isSynced) {
        derivedStateOf { if (!isSynced) -1 else activeRow(rows, clock.longValue) }
    }
    // Where the panel is heading, which is a beat ahead of where the singing is. Movement that
    // starts on the downbeat arrives after it; started during the run-up, the words are under
    // your eye when they land. Kept apart from [activeRow] on purpose: this leads, and the
    // brightness and the sweep must not.
    val leadLine by remember(rows, isSynced) {
        derivedStateOf {
            if (!isSynced) {
                -1
            } else {
                val now = clock.longValue
                activeRow(rows, now + scrollLead(rows, now))
            }
        }
    }
    // What the stack arranges itself around. The outgoing line starts dimming as the panel
    // leaves it rather than when its last word ends, so the dim, the blur and the movement
    // are one gesture.
    val focusLine = if (leadLine >= 0) leadLine else activeRow

    // Opened on the line being sung: a list placed there a frame after it was first drawn showed
    // its top in between.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusLine.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
    // LayoutInfo changes on every scroll frame. Observe only height here so the entire lyrics
    // list is not recomposed for every scrolling pixel.
    val viewportHeight by remember(listState) {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    var browsing by remember { mutableStateOf(false) }

    val reduceMotion = rememberReduceMotion()
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reduceMotion

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            // Suspends the panel's own following, and nothing more.
            if (interaction is DragInteraction.Start) browsing = true
        }
    }

    val currentLine by rememberUpdatedState(focusLine)
    val activeOnScreen by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == currentLine } }
    }
    // Paused, there is no song to follow back to, so a hand scroll should sit wherever it was
    // left rather than snapping back on these timers.
    LaunchedEffect(browsing, activeOnScreen, listState.isScrollInProgress, playing) {
        if (playing && browsing && activeOnScreen && !listState.isScrollInProgress) {
            delay(600)
            browsing = false
        }
    }
    LaunchedEffect(browsing, listState.isScrollInProgress, playing) {
        if (playing && browsing && !listState.isScrollInProgress) {
            delay(5_000)
            browsing = false
        }
    }

    // The panel's own journey, published so each row can work out how far behind it should be
    // running. Held as a plain value plus a frame clock rather than an animation per row:
    // sixty rows each with their own Animatable is sixty animations to start and stop on every
    // handover.
    var run by remember { mutableStateOf(ScrollRun(0, 0f, LYRIC_SETTLE_MS)) }
    val since = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(run.id) {
        if (run.id == 0) return@LaunchedEffect
        animate(
            initialValue = 0f,
            targetValue = run.spanMs,
            animationSpec = tween(run.spanMs.toInt(), easing = LinearEasing),
        ) { value, _ -> since.floatValue = value }
    }
    var placed by remember { mutableStateOf(isSynced && focusLine >= 0) }
    // A newer line replaces an unfinished automatic scroll. Only a user's browsing gesture
    // should suspend following, not our own animation.
    LaunchedEffect(focusLine, browsing, selecting) {
        if (isSynced && !browsing && !selecting && focusLine >= 0 && focusLine in rows.indices) {
            val vh = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val centerOffset = -(vh * 0.34f).toInt()
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusLine }
            when {
                !placed -> {
                    listState.scrollToItem(focusLine, scrollOffset = centerOffset)
                    placed = true
                }
                visible != null -> {
                    val delta = visible.offset.toFloat() + centerOffset
                    val span = scrollLead(rows, clock.longValue).toInt()
                    run = ScrollRun(run.id + 1, delta, span)
                    if (reduceMotion) {
                        listState.scrollToItem(focusLine, scrollOffset = centerOffset)
                    } else {
                        listState.animateScrollBy(
                            value = delta,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 240f),
                        )
                    }
                }
                else -> if (reduceMotion) {
                    listState.scrollToItem(focusLine, scrollOffset = centerOffset)
                } else {
                    listState.animateScrollToItem(focusLine, scrollOffset = centerOffset)
                }
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    LazyColumn(
        state = listState,
        modifier = modifier
            .nestedScroll(keepScroll)
            .fadingEdges(),
        // Each row carries GLOW_ROOM of its own inset for the halo, so the list hands that
        // much back — otherwise the lines would sit a glow's width further apart and further in.
        contentPadding = PaddingValues(
            start = (Immersive.Gutter + contentPadding.calculateStartPadding(layoutDirection) - GLOW_ROOM)
                .coerceAtLeast(0.dp),
            top = contentPadding.calculateTopPadding() + 40.dp - GLOW_ROOM,
            end = (Immersive.Gutter + contentPadding.calculateEndPadding(layoutDirection) - GLOW_ROOM)
                .coerceAtLeast(0.dp),
            // Room for the last lines to rise a little, not to the top: with 80 % of the window
            // under them, the list scrolled on into a blank page at the end of a song (#120).
            bottom = contentPadding.calculateBottomPadding() +
                if (isSynced) with(density) { viewportHeight.toDp() } * LAST_LINE_ROOM else 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(rows) { index, row ->
            PanelRow(
                index = index,
                row = row,
                synced = isSynced,
                activeRow = activeRow,
                focusLine = focusLine,
                run = run,
                since = since,
                // Reading to pick lines flattens the stack the way reading by hand does.
                browsing = browsing || selecting,
                blurOk = blurOk,
                glowing = !reduceMotion,
                reduceMotion = reduceMotion,
                activeColor = activeColor,
                glowTint = glowTint,
                clock = clock,
                noteMeasurer = noteMeasurer,
                translation = translations?.getOrNull(row.sourceIndex)?.trim()?.takeIf { it.isNotEmpty() && !it.equals(row.text.trim(), ignoreCase = true) },
                romanizing = romanizing,
                duet = duet,
                onNote = notes[index]?.let { n -> { noteOpen = n } },
                // A line Genius explains opens its note; any other starts picking lines to share.
                onHold = if (selecting) null else notes[index]?.let { n ->
                    {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        noteOpen = n
                    }
                } ?: row.sourceIndex.takeIf { it >= 0 && !row.isGap }?.let { line ->
                    {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        vm.startSelecting(line)
                    }
                },
                selected = picked?.let { row.sourceIndex in it } == true,
                onPick = if (selecting && !row.isGap && row.sourceIndex >= 0) ({
                    if (!vm.pick(row.sourceIndex)) android.widget.Toast.makeText(context, context.getString(R.string.lyrics_select_max, LyricsViewModel.MAX_SHARED_LINES), android.widget.Toast.LENGTH_SHORT).show()
                }) else null,
                onSeek = { timeMs ->
                    if (selecting) return@PanelRow
                    SongPlayer.logLyricJump(timeMs)
                    head.announceJump()
                    SongPlayer.seekTo(timeMs)
                    SongPlayer.play()
                },
            )
        }
        lyrics.provider?.takeIf { it.isNotBlank() }?.let { p ->
            item(key = "provider-credit") {
                Text(
                    text = "Lyrics by $p",
                    style = Immersive.Caption,
                    color = Immersive.TextSecondary.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = GLOW_ROOM, end = GLOW_ROOM, top = 24.dp),
                )
            }
        }
    }
}

private const val BLUR_SWITCH_MS = 200

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelRow(
    index: Int,
    row: PanelLine,
    synced: Boolean,
    activeRow: Int,
    focusLine: Int,
    run: ScrollRun,
    since: FloatState,
    browsing: Boolean,
    blurOk: Boolean,
    glowing: Boolean,
    reduceMotion: Boolean,
    activeColor: Color,
    glowTint: Color,
    clock: MutableLongState,
    noteMeasurer: TextMeasurer,
    /** The line in the reader's language, shown small under it; null when there is none. */
    translation: String?,
    /** The romanization under the line is shown (the row has one to show: see [PanelLine.romanization]). */
    romanizing: Boolean,
    /** The song has two singers: see [endLane]. */
    duet: Boolean,
    /** Opens the Genius note on this line; null when Genius has none. */
    onNote: (() -> Unit)?,
    onHold: (() -> Unit)?,
    /** Picked to be shared: the row sits on a soft pill. */
    selected: Boolean,
    /** Set while lines are being picked: a tap selects this row instead of seeking. */
    onPick: (() -> Unit)?,
    onSeek: (Long) -> Unit,
) {
    if (!synced && row.isGap) {
        Spacer(Modifier.height(14.dp))
        return
    }

    val lane = endLane(row, duet)
    val offset = if (activeRow < 0) 0 else index - activeRow
    val isActive = synced && index == activeRow
    val step = abs(offset).coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)
    val focusStep = (if (focusLine < 0) 0 else abs(index - focusLine))
        .coerceAtMost(LINE_FALLOFF_BLUR.lastIndex)
    val blur = animateDpAsState(
        targetValue = when {
            !synced || !blurOk || browsing || (focusLine >= 0 && index == focusLine) -> 0.dp
            else -> LINE_FALLOFF_BLUR[focusStep]
        },
        animationSpec = tween(BLUR_SWITCH_MS, easing = LYRIC_EASING),
        label = "lyricBlur",
    )
    val lineAlpha = animateFloatAsState(
        targetValue = when {
            !synced -> 0.95f
            isActive || selected -> 1f
            browsing -> BROWSING_ALPHA
            else -> LINE_FALLOFF_ALPHA[step]
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f),
        label = "lyricAlpha",
    )

    if (row.isGap) {
        GapRow(
            row = row,
            clock = clock,
            isActive = isActive,
            blur = blur,
            lineAlpha = lineAlpha,
            alignEnd = lane == true,
            glowTint = glowTint,
            reduceMotion = reduceMotion,
            onSeek = onSeek,
        )
        return
    }

    val style = if (synced) {
        Immersive.Lyric
    } else {
        Immersive.Lyric.copy(fontSize = 26.sp, lineHeight = 33.sp)
    }
    val lineStyle = remember(style, row.text, lane) { voiceStyle(style, row.text, lane) }
    val sung = offset < 0
    val behind = if (run.delta >= 0f) index - focusLine else focusLine - index
    val staggerDelay = behind.coerceIn(0, STAGGER_STEPS) * STAGGER_FRACTION * run.durationMs
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    var pressWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val scale = animateFloatAsState(
        targetValue = when {
            pressed -> PRESSED_SCALE
            isActive || !synced || browsing -> 1f
            else -> scaleForDistance(step)
        },
        animationSpec = if (pressed) {
            tween(durationMillis = 120, easing = LYRIC_EASING)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f)
        },
        label = "lyricScale",
    )
    val glow by animateFloatAsState(
        targetValue = if (isActive && glowing) GLOW_ALPHA else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "lyricGlow",
    )
    val pickedFill by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        tween(180), label = "lyricPicked",
    )
    val originX = if (lane ?: isRtl(row.text)) 1f else 0f
    val shape = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = TransformOrigin(originX, 0.5f)
            alpha = lineAlpha.value
            translationY = if (staggerDelay <= 0f) {
                0f
            } else {
                val elapsed = since.floatValue
                run.delta * (
                    LYRIC_EASING.transform((elapsed / run.durationMs).coerceIn(0f, 1f)) -
                        LYRIC_EASING.transform(((elapsed - staggerDelay) / run.durationMs).coerceIn(0f, 1f))
                    )
            }
            val radius = blur.value.toPx()
            renderEffect = if (radius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(radius, radius, TileMode.Decal) else null
        }
        .clip(RoundedCornerShape(10.dp))
        .background(pickedFill)
        .then(if (onNote != null) Modifier.drawWithCache {
            val text = measureNoteLine(noteMeasurer, row.text, lineStyle, size.width.toInt(), GLOW_ROOM.roundToPx())
            val room = GLOW_ROOM.toPx()
            val padH = NOTE_PILL_PAD_H.toPx()
            val inset = NOTE_PILL_INSET.toPx()
            val radius = CornerRadius(NOTE_PILL_RADIUS.toPx())
            val pills = (0 until text.lineCount).mapNotNull { i ->
                val left = text.getLineLeft(i)
                val right = text.getLineRight(i)
                if (right - left < 1f) null
                else Rect(room + left - padH, room + text.getLineTop(i) + inset, room + right + padH, room + text.getLineBottom(i) - inset)
            }
            onDrawBehind {
                pills.forEach { r -> drawRoundRect(NOTE_PILL_COLOR, r.topLeft, r.size, radius) }
            }
        } else Modifier)
        .secondaryClick(onHold)
        .lastPress { at, size -> pressAt = at; pressWidth = size.width }
        .combinedClickable(
            enabled = synced || onHold != null || onPick != null, interactionSource = interaction, indication = null,
            onLongClick = onHold,
            onClick = {
                if (onPick != null) onPick()
                else if (synced) {
                    val room = with(density) { GLOW_ROOM.roundToPx() }
                    val laid = measureNoteLine(noteMeasurer, row.text, lineStyle, pressWidth, room)
                    onSeek(tapTime(row.swept, laid, pressAt - Offset(room.toFloat(), room.toFloat())))
                }
            },
        )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (duet && lane == true) 24.dp else 0.dp,
                    end = if (duet && lane == false) 24.dp else 0.dp,
                ),
        ) {
            Column(shape) {
                NoteLayout(
                    text = row.text,
                    style = lineStyle,
                    measurer = noteMeasurer,
                    onNote = onNote,
                ) {
                    PanelVoice(
                        row = row,
                        clock = clock,
                        style = lineStyle,
                        isActive = isActive,
                        sung = sung,
                        synced = synced,
                        browsing = browsing,
                        glowAlpha = glow,
                        room = GLOW_ROOM,
                        lane = lane,
                        activeColor = activeColor,
                        glowTint = glowTint,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val romanized = row.romanization.takeIf { romanizing }
                if (row.background != null) {
                    PanelBackground(
                        row = row,
                        clock = clock,
                        style = voiceStyle(BACKGROUND_STYLE, row.background, lane),
                        isActive = isActive,
                        sung = sung,
                        synced = synced,
                        lane = lane,
                        activeColor = activeColor,
                        glowTint = glowTint,
                        modifier = Modifier.fillMaxWidth().padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = if (romanized != null || translation != null) 4.dp else GLOW_ROOM),
                    )
                }
                if (romanized != null) {
                    Text(
                        text = romanized,
                        style = secondaryStyle(ROMANIZATION_STYLE, lane),
                        color = Immersive.TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = if (translation != null) 10.dp else GLOW_ROOM),
                    )
                }
                if (translation != null) {
                    Text(
                        text = translation,
                        style = secondaryStyle(TRANSLATION_STYLE, lane),
                        color = Immersive.TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM),
                    )
                }
            }
        }
    }
}

/** Over the foot of the panel while lines are picked: what to do, then Cancel and Share. */
@Composable
private fun ShareBar(canShare: Boolean, onCancel: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(Immersive.Tokens.cardRadius + 10.dp)).padding(12.dp)) {
        Text(
            text = stringResource(R.string.lyrics_select_hint),
            style = Immersive.Caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ImmersiveButton(stringResource(R.string.cancel), onCancel, Modifier.weight(1f))
            ImmersiveButton(
                stringResource(R.string.lyrics_fullscreen_chip_label_share), onShare, Modifier.weight(1f),
                icon = Icons.Rounded.Share, filled = true, enabled = canShare,
            )
        }
    }
}

/** A translated line: well below the lyric's 30 sp, so the sung words stay the focus. */
private val TRANSLATION_STYLE = Immersive.Lyric.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)

/** A romanized line: a translation's size, in italics as a transliteration is set, so the two never read alike. */
private val ROMANIZATION_STYLE = TRANSLATION_STYLE.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

/** The backing vocals: smaller, italic and muted beneath the line (see [PanelBackground]). */
private val BACKGROUND_STYLE = Immersive.Lyric.copy(
    fontSize = 18.sp,
    lineHeight = 24.sp,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
)
private const val BACKGROUND_ALPHA = 0.62f

/** [base] set at the end of the row for the second singer of a duet, at its start otherwise. */
private fun secondaryStyle(base: TextStyle, lane: Boolean?): TextStyle =
    if (lane == true) base.copy(textAlign = TextAlign.Right) else base

// ─── Genius notes on the lines ───────────────────────────────────────────────────────────

private val NOTE_PILL_COLOR = Color.White.copy(alpha = 0.09f)
private val NOTE_PILL_PAD_H = 7.dp
private val NOTE_PILL_INSET = 3.dp
private val NOTE_PILL_RADIUS = 12.dp
/** The quote mark after an explained line, and the gap before it. */
private val NOTE_MARK_SIZE = 22.dp
private val NOTE_MARK_GAP = 6.dp
/** What the finger has to hit to open the note: larger than the mark it shows. */
private val NOTE_MARK_TOUCH = 40.dp

/**
 * The line's words laid out as the row lays them out: the row's width less the glow room on
 * each side, left to right. The measurer keeps the result, so the pills and the mark share it.
 */
private fun measureNoteLine(measurer: TextMeasurer, text: String, style: TextStyle, rowWidth: Int, room: Int): TextLayoutResult =
    measurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = (rowWidth - room * 2).coerceAtLeast(0)),
        layoutDirection = LayoutDirection.Ltr,
    )

/**
 * A lyric row, and when Genius explains it ([onNote] set) the small quote mark that opens the
 * note: just after the last word, centred on that row of text (before it for right-to-left
 * lines). When the last row of words runs too close to the edge for it, the mark goes on a row of
 * its own under the words rather than over them, so nothing the singing lights is ever covered.
 */
@Composable
private fun NoteLayout(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    onNote: (() -> Unit)?,
    modifier: Modifier = Modifier,
    voice: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            voice()
            if (onNote != null) NoteMark(onNote)
        },
    ) { measurables, constraints ->
        val line = measurables[0].measure(constraints)
        val markMeasurable = measurables.getOrNull(1)
        if (markMeasurable == null) {
            return@Layout layout(line.width, line.height) { line.place(0, 0) }
        }
        val mark = markMeasurable.measure(Constraints())
        val room = GLOW_ROOM.roundToPx()
        val laid = measureNoteLine(measurer, text, style, line.width, room)
        val last = (laid.lineCount - 1).coerceAtLeast(0)
        val radius = NOTE_MARK_SIZE.toPx() / 2f
        val lead = NOTE_PILL_PAD_H.toPx() + NOTE_MARK_GAP.toPx() + radius
        val rtl = isRtl(text)
        val inlineX = if (rtl) room + laid.getLineLeft(last) - lead else room + laid.getLineRight(last) + lead
        val fits = if (rtl) inlineX - radius >= 0f else inlineX + radius <= line.width
        val centerX: Float
        val centerY: Float
        val height: Int
        if (fits) {
            centerX = inlineX
            centerY = room + (laid.getLineTop(last) + laid.getLineBottom(last)) / 2f
            height = line.height
        } else {
            centerX = if (rtl) line.width - room - radius else room + radius
            centerY = room + laid.size.height + NOTE_MARK_GAP.toPx() + radius
            height = maxOf(line.height, (centerY + radius + room / 2f).toInt())
        }
        layout(line.width, height) {
            line.place(0, 0)
            mark.place((centerX - mark.width / 2f).toInt(), (centerY - mark.height / 2f).toInt())
        }
    }
}

/** The quote mark itself: a small frosted disc, with a finger-sized target around it. */
@Composable
private fun NoteMark(onOpen: () -> Unit) {
    val label = stringResource(R.string.immersive_genius_note_open)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(NOTE_MARK_TOUCH)
            .clip(CircleShape)
            .immersiveClickable(onClick = onOpen, pressedScale = 0.85f)
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(NOTE_MARK_SIZE)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
        ) {
            Icon(
                Icons.Rounded.FormatQuote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun isRtl(text: String): Boolean {
    var rtl = 0
    var ltr = 0
    for (ch in text) {
        if (ch.code in 0xFB1D..0xFB4F) return true
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
        }
    }
    return rtl > ltr
}

/**
 * The style with an absolute alignment taken from the line's own text direction, or from its
 * duet [endLane] (true the right edge, false the left) when the song has two singers.
 */
private fun voiceStyle(base: TextStyle, text: String, endLane: Boolean? = null): TextStyle {
    val rtl = isRtl(text)
    return base.copy(
        textAlign = if (endLane ?: rtl) TextAlign.Right else TextAlign.Left,
        textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
    )
}

/**
 * One voice of a row: the whole line lit at once, or swept word by word where the source has
 * word timings.
 */
@Composable
private fun PanelVoice(
    row: PanelLine,
    clock: MutableLongState,
    style: TextStyle,
    isActive: Boolean,
    /** Whether the panel has already left this line behind. */
    sung: Boolean,
    /** Whether the source stamps its lines at all. */
    synced: Boolean,
    browsing: Boolean,
    glowAlpha: Float,
    room: Dp,
    /** The duet side: see [endLane]. */
    lane: Boolean?,
    activeColor: Color,
    glowTint: Color,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (isActive || !synced) activeColor else lerp(activeColor, Immersive.TextSecondary, 0.35f),
        label = "lyricLineColor",
    )
    if (synced && (row.isWordSynced || row.swept.endMs > row.timeMs)) {
        // Every synced line goes through SweptLyricLine: word-by-word/syllable sweep when
        // word timings are present, and graceful line-level sweep fallback otherwise.
        val tail by animateFloatAsState(
            targetValue = when {
                isActive -> UNSUNG_ALPHA
                sung -> SUNG_ALPHA
                else -> 0.90f
            },
            label = "lyricTail",
        )
        SweptLyricLine(
            line = row.swept,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = if (browsing) 0f else glowAlpha,
            glowRoom = room,
            feather = isActive,
            rise = row.isWordSynced,
            color = color,
            glowColor = lerp(color, glowTint, 0.45f),
            alignEnd = lane == true,
            laneLocked = lane != null,
        )
    } else {
        val lit by animateFloatAsState(
            targetValue = when {
                !synced || isActive -> 1f
                sung -> SUNG_ALPHA
                else -> 0.90f
            },
            label = "lyricLit",
        )
        Text(
            text = row.text,
            style = style,
            color = color.copy(alpha = lit),
            modifier = modifier.padding(room),
        )
    }
}

/**
 * The backing vocals under a line: swept word by word where the source times them, else lit
 * whole with their line. Smaller, italicized, and softly tinted beneath the lead lyric.
 */
@Composable
private fun PanelBackground(
    row: PanelLine,
    clock: MutableLongState,
    style: TextStyle,
    isActive: Boolean,
    sung: Boolean,
    synced: Boolean,
    lane: Boolean?,
    activeColor: Color,
    glowTint: Color,
    modifier: Modifier = Modifier,
) {
    val tinted = lerp(activeColor, glowTint, 0.42f)
    val color by animateColorAsState(
        targetValue = if (isActive || !synced) tinted else lerp(tinted, Immersive.TextSecondary, 0.35f),
        label = "lyricBackgroundColor",
    )
    val dimmer = modifier.graphicsLayer { alpha = BACKGROUND_ALPHA }
    val swept = row.backgroundSwept
    if (swept != null && synced) {
        val tail by animateFloatAsState(
            targetValue = when {
                isActive -> UNSUNG_ALPHA
                sung -> 1f
                else -> 0.85f
            },
            label = "lyricBackgroundTail",
        )
        SweptLyricLine(
            line = swept, clock = clock, style = style, dimAlpha = tail, modifier = dimmer,
            rise = false, color = color, alignEnd = lane == true, laneLocked = lane != null,
        )
    } else {
        val lit by animateFloatAsState(if (!synced || sung || isActive) 1f else 0.85f, label = "lyricBackgroundLit")
        Text(text = row.background.orEmpty(), style = style, color = color.copy(alpha = lit), modifier = dimmer)
    }
}

/**
 * Pill-shaped three-dot animated progress bubble for intros > 4 s and instrumental gaps > 4 s.
 * Dots scale and illuminate sequentially across the break while expanding smoothly into view.
 */
@Composable
private fun GapRow(
    row: PanelLine,
    clock: MutableLongState,
    isActive: Boolean,
    blur: State<Dp>,
    lineAlpha: State<Float>,
    alignEnd: Boolean,
    glowTint: Color,
    reduceMotion: Boolean,
    onSeek: (Long) -> Unit,
) {
    val until = row.untilMs
    val gapFill = remember(row, until, clock) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = clock.longValue
            when {
                now <= row.timeMs -> 0f
                now >= until -> 1f
                else -> GAP_RUNNING
            }
        }
    }
    val swell by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 280f),
        label = "gapSwell",
    )
    if (swell <= 0.01f) {
        Spacer(Modifier.height(0.dp))
        return
    }
    val instrumental = stringResource(R.string.immersive_lyrics_instrumental)
    val pillShape = RoundedCornerShape(50)
    Box(
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height((GAP_ROW_HEIGHT + GAP_ROW_SPACING) * swell)
            .padding(horizontal = GLOW_ROOM)
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val radius = blur.value.toPx()
                    renderEffect = if (radius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(radius, radius, TileMode.Decal) else null
                    val grow = GAP_REST_SCALE + (1f - GAP_REST_SCALE) * swell
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                    alpha = lineAlpha.value * swell
                }
                .size(width = 78.dp, height = 34.dp)
                .clip(pillShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), pillShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onSeek(row.timeMs)
                }
                .drawBehind {
                    val span = (until - row.timeMs).coerceAtLeast(1L)
                    val through = gapFill.value.takeUnless { it == GAP_RUNNING }
                        ?: ((clock.longValue - row.timeMs).toFloat() / span).coerceIn(0f, 1f)
                    val baseRadius = GAP_DOT_SIZE.toPx() / 2f
                    val stride = (GAP_DOT_SIZE + GAP_DOT_GAP).toPx()
                    val startX = (size.width - stride * (GAP_DOTS - 1)) / 2f
                    val wavePhase = if (reduceMotion) 0f else (clock.longValue % 1_800L) / 1_800f * (2f * PI.toFloat())
                    repeat(GAP_DOTS) { dot ->
                        val lit = (through * GAP_DOTS - dot).coerceIn(0f, 1f)
                        val wave = if (isActive && !reduceMotion) sin(wavePhase - dot * 0.9f).toFloat() else 0f
                        val scale = 0.78f + 0.34f * lit + 0.10f * sin(lit * PI).toFloat() + 0.08f * wave
                        val center = Offset(startX + dot * stride, size.height / 2f - wave * 1.6.dp.toPx())
                        if (isActive && lit > 0.05f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowTint.copy(alpha = 0.38f * lit), Color.Transparent),
                                    center = center,
                                    radius = baseRadius * scale * 2.1f,
                                ),
                                radius = baseRadius * scale * 2.1f,
                                center = center,
                            )
                        }
                        drawCircle(
                            color = Color.White.copy(alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * lit),
                            radius = baseRadius * scale,
                            center = center,
                        )
                    }
                }
                .semantics { contentDescription = instrumental },
        )
    }
}

// ─── Loading skeleton ────────────────────────────────────────────────────────────────────

/**
 * The shape of the page the lyrics are going to fill: one entry per line of the song, and one
 * number per row that line wraps to. Text is blocks with ragged bottoms.
 */
private val SKELETON_BLOCKS = listOf(
    floatArrayOf(0.97f, 0.54f),
    floatArrayOf(0.92f, 0.99f, 0.41f),
    floatArrayOf(0.68f),
    floatArrayOf(0.95f, 0.73f),
    floatArrayOf(0.89f, 0.96f, 0.37f),
)
private val SKELETON_BAR = 26.dp
private val SKELETON_LEADING = 15.dp
private val SKELETON_BLOCK_GAP = 35.dp
private const val SKELETON_PERIOD_MS = 1_400

@Composable
private fun LyricsSkeleton(modifier: Modifier = Modifier) {
    // Under "reduce motion" the band stays off the bars: flat placeholders, nothing endless running.
    val sweep = if (rememberReduceMotion()) null else rememberInfiniteTransition(label = "lyricsSkeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_PERIOD_MS, easing = LinearEasing)),
        label = "sweep",
    )
    BoxWithConstraints(modifier.padding(top = 40.dp)) {
        // Every bar sweeps against the width of the column rather than its own, so one band
        // crosses the whole page.
        val column = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_BLOCK_GAP)) {
            SKELETON_BLOCKS.forEach { rows ->
                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LEADING)) {
                    rows.forEach { fraction ->
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(SKELETON_BAR)
                                .clip(RoundedCornerShape(4.dp))
                                // Read in the draw block, not the body: all any bar needs per
                                // frame is a fresh gradient.
                                .drawWithCache {
                                    val full = column.toPx()
                                    val band = full * 0.45f
                                    val startX = -band + (sweep?.value ?: 0f) * (full + band * 2)
                                    val brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.26f),
                                            Color.White.copy(alpha = 0.10f),
                                        ),
                                        startX = startX,
                                        endX = startX + band,
                                    )
                                    onDrawBehind { drawRect(brush) }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** The part of the window left under the last line of synced lyrics (see the list's padding). */
private const val LAST_LINE_ROOM = 0.35f
