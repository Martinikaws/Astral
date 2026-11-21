package com.music.spotui.ui.screens

import com.music.spotui.ui.components.secondaryClick
import com.music.spotui.ui.look.lastPress
import com.music.spotui.ui.look.tapTime
import com.music.spotui.ui.look.toSwept
import com.music.spotui.ui.components.pressable
import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import com.music.spotui.data.entity.LyricWord
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.ui.look.GLOW_ALPHA
import com.music.spotui.ui.look.GLOW_ROOM
import com.music.spotui.ui.look.LINE_FALLOFF_ALPHA
import com.music.spotui.ui.look.LINE_FALLOFF_BLUR
import com.music.spotui.ui.look.LYRIC_EASING
import com.music.spotui.ui.look.LYRIC_SETTLE_MS
import com.music.spotui.ui.look.SUNG_ALPHA
import com.music.spotui.ui.look.SweptLine
import com.music.spotui.ui.look.SweptLyricLine
import com.music.spotui.ui.look.UNSUNG_ALPHA
import com.music.spotui.ui.look.rememberArtworkColors
import com.music.spotui.ui.look.rememberReduceMotion
import com.music.spotui.ui.look.scaleForDistance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.power.EnergySettings
import com.music.spotui.data.api.LyricsApi
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.AstralThemePref
import com.music.spotui.data.preferences.LyricsRomanizationPref
import com.music.spotui.data.preferences.SpicyBackgroundPref
import com.music.spotui.data.preferences.SpicyThemePreset
import com.music.spotui.spicy.SpicyBackgroundButton
import com.music.spotui.spicy.SpicyBackgroundLayer
import com.music.spotui.spicy.SpicyBackgroundSheet
import com.music.spotui.spicy.SpicyLyricsLayer
import com.music.spotui.spicy.hasDrawableLines
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.data.preferences.getLyricTranslateLang
import com.music.spotui.data.preferences.isLyricsExtraSources
import com.music.spotui.data.preferences.setLyricsExtraSources
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.StarryNightBackdrop
import com.music.spotui.ui.components.rememberFrameClock
import com.music.spotui.ui.components.shareLyricLines
import com.music.spotui.ui.components.cappedLyricsWidth
import com.music.spotui.ui.components.titleMarquee
import com.music.spotui.ui.theme.StarryAccent
import com.music.spotui.ui.viewmodel.LyricsViewModel
import app.myspoti.engine.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Spotify's lyrics, as the official Android app shows them: the lyrics card in the
 * Now Playing scroll and the full-screen view, both on the track's own colour
 * (color-lyrics `colors`). Sung and current lines are in the highlight colour,
 * upcoming lines in the text colour; tapping a line seeks to it.
 */

/** The three colours of a lyrics view: background, upcoming text, sung / current text. */
private data class LyricsPalette(val background: Color, val text: Color, val highlight: Color)

private fun paletteFor(lyrics: Lyrics?, accent: Color): LyricsPalette {
    lyrics?.colors?.let { c ->
        // Spotify's colours for the song, with the sung white kept readable: on some songs the
        // background is light enough for white words to fade into it (reported on one song,
        // fine on another). The background is darkened only as much as that takes.
        val hi = Color(c.highlight)
        var bg = Color(c.background)
        var step = 0
        while (contrast(hi, bg) < MIN_CONTRAST && step < 8) { bg = lerp(bg, if (hi.luminance() > 0.5f) Color.Black else Color.White, 0.12f); step++ }
        // Upcoming lines (often black in Spotify's colours) stay readable on that background too.
        val text = Color(c.text).let { t -> if (contrast(t.copy(alpha = 1f), bg) < 1.8f) hi.copy(alpha = 0.55f) else t }
        return LyricsPalette(bg, text, hi)
    }
    // No Spotify colours (fallback source): the artwork colour, like Spotify's own fallback.
    val bg = lerp(accent, Color.Black, 0.15f)
    // B23: upcoming lines were black at 55 % — invisible on a dark (AMOLED) card. On a dark
    // background they are drawn in a slightly dimmed white instead, the sung lines staying
    // the full highlight colour.
    val text = if (bg.luminance() > 0.35f) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.72f)
    return LyricsPalette(bg, text, Color.White)
}

/** WCAG contrast ratio of two colours (1 = the same, 21 = black on white). */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** What the sung (and karaoke) words need against the background to read at a glance. */
private const val MIN_CONTRAST = 3.2f

/** Playback position, re-read on every frame while playing (smooth word highlight). */
@Composable
private fun rememberPosition(running: Boolean): () -> Long {
    val frame = rememberFrameClock(running, fps = 30)
    return remember(frame) {
        // What is heard: Bluetooth plays it a little later than the player is.
        { frame.value; (SongPlayer.getCurrentPosition() - com.music.spotui.di.OutputLag.ms()).coerceAtLeast(0L) }
    }
}

/**
 * How early a line turns current and a word lights, the same for both: words lit only when sung,
 * after their line had turned current early, looked late next to line-timed lyrics (reported).
 */
private const val LINE_LEAD_MS = 150L

private fun activeIndexFor(lyrics: Lyrics, positionMs: Long): Int {
    if (!lyrics.synced) return -1
    // Binary search: last line starting at or before the position.
    var lo = 0
    var hi = lyrics.lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lyrics.lines[mid].timeMs <= positionMs + LINE_LEAD_MS) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}

/**
 * How many characters of a word-timed line are lit at [p] (ms): each word lights up whole the
 * moment it is sung (asked for instead of a fill sliding through the words).
 */
private fun sungChars(words: List<LyricWord>, starts: List<Int>, p: Long): Float {
    var out = 0f
    for (i in words.indices) {
        if (p < words[i].startMs) break
        out = (starts[i] + words[i].text.length).toFloat()
    }
    return out
}

/** Seek to a tapped synced line and make sure we're playing. */
private fun jumpTo(timeMs: Long) {
    com.music.spotui.di.Haptics.click()
    SongPlayer.logLyricJump(timeMs)
    SongPlayer.seekTo(timeMs)
    if (!SongPlayer.isPlaying()) SongPlayer.play()
}

private fun shareLyrics(context: Context, title: String, artist: String, line: String?) {
    val id = SongPlayer.currentTrackId
    val text = buildString {
        if (!line.isNullOrBlank() && line != "♪") append("« ").append(line).append(" »\n")
        append(title).append(" · ").append(artist)
        if (id.isNotBlank()) append("\nhttps://open.spotify.com/track/").append(id)
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.lyrics_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
internal fun rememberLyricsState(vm: LyricsViewModel, title: String, artist: String, hold: Boolean = false): LyricsViewModel.State {
    // currentTrackId is plain data, not Compose state: the playing item's uri re-runs the load on a skip.
    val playingUri = SongPlayer.nowPlaying.value.uri
    // The lyrics extensions changed (one installed, removed…): the song is read again from the new set.
    val sources by com.music.spotui.data.api.LyricsApi.sourcesTick.collectAsState()
    var seen by remember { mutableStateOf(sources) }
    LaunchedEffect(title, artist, playingUri, SongPlayer.currentTrackId, sources, hold) {
        // The first half of a mix: the words of the song going out stay, the new song's load after it.
        if (hold) return@LaunchedEffect
        val again = seen != sources
        seen = sources
        SongPlayer.currentLyricsTrack()?.let { vm.load(it, force = again) }
    }
    return vm.state.collectAsState().value
}

/**
 * The screen stays on while [active] (the lyrics shown and the song playing), unless turned off in
 * the settings ([com.music.spotui.data.preferences.LyricsKeepAwakePref]). Both looks. For
 * [LYRICS_AWAKE_MS] after the last touch, or with no limit while the phone charges: a phone left
 * on a table with the lyrics open stayed lit (and drew them) until its battery ran out.
 */
@Composable
internal fun KeepScreenOnForLyrics(active: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    val on = active && com.music.spotui.data.preferences.LyricsKeepAwakePref.on
    LaunchedEffect(view, on) {
        if (!on) return@LaunchedEffect
        val battery = view.context.getSystemService(android.os.BatteryManager::class.java)
        try {
            while (true) {
                val charging = runCatching { battery?.isCharging == true }.getOrDefault(false)
                val touchedAt = com.music.spotui.ui.components.UserTouch.lastAt.value
                val left = LYRICS_AWAKE_MS - (android.os.SystemClock.uptimeMillis() - touchedAt)
                view.keepScreenOn = charging || left > 0
                // Looked at again at the limit, a touch, or now and then for the charger.
                kotlinx.coroutines.withTimeoutOrNull(if (!charging && left > 0) left else LYRICS_AWAKE_RECHECK_MS) {
                    com.music.spotui.ui.components.UserTouch.lastAt.first { it != touchedAt }
                }
            }
        } finally { view.keepScreenOn = false }
    }
}

/** The lyrics keep the screen on this long after the last touch (see [KeepScreenOnForLyrics]). */
private const val LYRICS_AWAKE_MS = 10 * 60_000L
/** How often a kept screen looks whether the phone was put on or off its charger. */
private const val LYRICS_AWAKE_RECHECK_MS = 60_000L

/** Frame-synced clock for [SweptLyricLine] in the Classic lyrics views. */
@Composable
private fun rememberLyricsClock(playing: Boolean, position: () -> Long): MutableLongState {
    val clock = remember { mutableLongStateOf(position()) }
    LaunchedEffect(playing) {
        clock.longValue = position()
        if (!playing) return@LaunchedEffect
        while (true) {
            withFrameNanos { clock.longValue = position() }
        }
    }
    return clock
}

private const val CLASSIC_STAGGER_STEPS = 4
private const val CLASSIC_STAGGER_FRACTION = 0.065f

/** Tracks a single spring scroll handover so rows can stagger softly behind the focal line. */
private class ClassicScrollRun(val id: Int, val delta: Float, val durationMs: Int)

private fun isRtlText(text: String): Boolean {
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

private fun toBackingSwept(line: LyricLine, nextLineMs: Long?): SweptLine? {
    val words = line.backgroundWords.orEmpty()
        .map { it.copy(text = it.text.replace("(", "").replace(")", "")) }
        .filter { it.text.isNotBlank() }
    if (words.isEmpty()) return null
    val text = words.joinToString("") { it.text }.trim()
    return LyricLine(words.first().startMs, text, words).toSwept(nextLineMs).takeIf { it.isWordSynced }
}

/** Look-ahead index so scrolling begins slightly before the next line lands. */
private fun focusIndexFor(lyrics: Lyrics, positionMs: Long): Int {
    if (!lyrics.synced) return -1
    var lo = 0
    var hi = lyrics.lines.size - 1
    var found = -1
    val target = positionMs + LINE_LEAD_MS + 160L
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lyrics.lines[mid].timeMs <= target) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}

/**
 * Keeps the active line centered around a third down the view using a critically-damped spring
 * and publishes each scroll run so neighboring rows stagger gently into focus.
 */
@Composable
private fun AutoFollow(
    listState: LazyListState,
    activeIndex: Int,
    offsetPx: Int,
    enabled: Boolean,
    reduceMotion: Boolean,
    onBrowsingChange: (Boolean) -> Unit,
    onScrollRun: (ClassicScrollRun) -> Unit,
) {
    var userScrollAt by remember { mutableLongStateOf(0L) }
    var browsing by remember { mutableStateOf(false) }
    var runId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                userScrollAt = System.currentTimeMillis()
                browsing = true
                onBrowsingChange(true)
            } else if (it is DragInteraction.Stop || it is DragInteraction.Cancel) {
                userScrollAt = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(browsing, listState.isScrollInProgress) {
        if (browsing && !listState.isScrollInProgress) {
            val wait = (3000L - (System.currentTimeMillis() - userScrollAt)).coerceAtLeast(350L)
            delay(wait)
            browsing = false
            onBrowsingChange(false)
        }
    }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(activeIndex, enabled, browsing) {
        if (!enabled || browsing || activeIndex < 0) return@LaunchedEffect
        if (!placed) { placed = true; listState.scrollToItem(activeIndex, -offsetPx); return@LaunchedEffect }
        val wait = 3000 - (System.currentTimeMillis() - userScrollAt)
        if (wait > 0) delay(wait)
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (visible != null) {
            val delta = (visible.offset - offsetPx).toFloat()
            if (!reduceMotion && abs(delta) > 1f) {
                runId++
                onScrollRun(ClassicScrollRun(runId.toInt(), delta, LYRIC_SETTLE_MS))
                listState.animateScrollBy(
                    value = delta,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 240f),
                )
            } else if (abs(delta) > 1f) {
                listState.scrollToItem(activeIndex, -offsetPx)
            }
        } else if (reduceMotion) {
            listState.scrollToItem(activeIndex, -offsetPx)
        } else {
            listState.animateScrollToItem(activeIndex, -offsetPx)
        }
    }
}

/**
 * Pill-shaped three-dot animated progress bubble for intros > 4 s and instrumental gaps > 4 s.
 * Dots scale and illuminate sequentially across the gap's progress.
 */
@Composable
private fun InterludeDots(
    startMs: Long,
    endMs: Long,
    clock: MutableLongState,
    color: Color,
    glowColor: Color,
    alignEnd: Boolean,
    collapsible: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val active by remember(startMs, endMs, clock) {
        derivedStateOf(structuralEqualityPolicy()) { clock.longValue in startMs..endMs }
    }
    val reduceMotion = rememberReduceMotion()
    val swell by animateFloatAsState(
        targetValue = when {
            active -> 1f
            collapsible -> 0f
            else -> 0.72f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 280f),
        label = "interludeSwell",
    )
    if (collapsible && swell <= 0.01f) return
    val rowHeight = if (collapsible) 48.dp * swell else 48.dp
    Box(
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clipToBounds()
            .padding(horizontal = GLOW_ROOM)
            .then(if (onTap != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap) else Modifier),
    ) {
        val pillShape = RoundedCornerShape(50)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 78.dp, height = 34.dp)
                .graphicsLayer {
                    val s = 0.82f + 0.18f * swell
                    scaleX = s
                    scaleY = s
                    alpha = swell
                    transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                }
                .clip(pillShape)
                .background(color.copy(alpha = if (active) 0.14f else 0.08f))
                .border(1.dp, color.copy(alpha = if (active) 0.26f else 0.12f), pillShape),
        ) {
            Canvas(modifier = Modifier.size(width = 52.dp, height = 18.dp)) {
                val span = (endMs - startMs).coerceAtLeast(1L)
                val through = ((clock.longValue - startMs).toFloat() / span).coerceIn(0f, 1f)
                val baseRadius = 4.6.dp.toPx()
                val stride = 17.dp.toPx()
                val startX = (size.width - stride * 2f) / 2f
                val wavePhase = if (reduceMotion) 0f else (clock.longValue % 1_800L) / 1_800f * (2f * PI.toFloat())
                repeat(3) { dot ->
                    val lit = (through * 3f - dot).coerceIn(0f, 1f)
                    val wave = if (active && !reduceMotion) sin(wavePhase - dot * 0.9f).toFloat() else 0f
                    val scale = 0.78f + 0.34f * lit + 0.10f * sin(lit * PI).toFloat() + 0.08f * wave
                    val center = Offset(startX + dot * stride, size.height / 2f - wave * 1.6.dp.toPx())
                    if (active && lit > 0.05f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowColor.copy(alpha = 0.36f * lit), Color.Transparent),
                                center = center,
                                radius = baseRadius * scale * 2.1f,
                            ),
                            radius = baseRadius * scale * 2.1f,
                            center = center,
                        )
                    }
                    drawCircle(
                        color = color.copy(alpha = 0.28f + 0.72f * lit),
                        radius = baseRadius * scale,
                        center = center,
                    )
                }
            }
        }
    }
}

/**
 * One lyric line. Word-synced lines lift & bloom word by word; line-synced lines sweep
 * progressively across their duration so every synced song feels alive.
 */
@Composable
private fun LyricLineText(
    line: LyricLine,
    state: Int, // -1 sung, 0 current, 1 upcoming, 2 unsynced
    palette: LyricsPalette,
    fontSize: TextUnit,
    position: () -> Long,
    translation: String?,
    /** Gets the time to play from: the start of the word tapped on a word-synced line. */
    onTap: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
    /** The line in Latin letters, shown under it (and under its backing vocals); null when it is off or there is none. */
    romanization: String? = null,
    /** The song has two singers: the second one's lines sit at the end of the row. */
    duet: Boolean = false,
    annotated: Boolean = false,
    onHold: (() -> Unit)? = null,
    /** Picked to be shared: the line sits on a soft pill. */
    selected: Boolean = false,
    /** Distance from the active line (0 = active), used for scale/blur/opacity falloff. */
    distance: Int = 0,
    /** End timestamp of the line (next line's start), for line-level sweep fallback. */
    nextLineMs: Long? = null,
    /** Shared frame clock for smooth word/line sweep. */
    clock: MutableLongState? = null,
    /** Whether to apply subtle distance scale/blur falloff (full-screen mode). */
    depthFalloff: Boolean = true,
    /** Tint for starlight/accent bloom and backing vocals. */
    glowColor: Color = lerp(palette.highlight, Color.White, 0.35f),
    /** True while user is manually scrolling so lines sharpen cleanly for reading. */
    browsing: Boolean = false,
    /** Total song duration for the final line's illumination window. */
    songDurationMs: Long = 0L,
    /** Staggered scroll state for cascading row motion. */
    scrollRun: ClassicScrollRun? = null,
    scrollSince: FloatState? = null,
    lineIndex: Int = 0,
    focusIndex: Int = -1,
) {
    val target = when (state) {
        1 -> palette.text
        else -> palette.highlight
    }
    val color by animateColorAsState(target, tween(220), label = "lyricColor")
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    // Where the swept text sits inside the line (right-aligned lines wrap-width and hug the right edge).
    var textX by remember { mutableFloatStateOf(0f) }
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    val effectiveEndMs = remember(line, nextLineMs, songDurationMs) {
        nextLineMs ?: songDurationMs.takeIf { it > line.timeMs } ?: (line.timeMs + (line.text.length * 90L).coerceIn(2_500L, 6_000L))
    }
    val swept = remember(line, effectiveEndMs) {
        line.toSwept(effectiveEndMs).let {
            if (it.words.isEmpty() && effectiveEndMs > line.timeMs) it.copy(sungUntilMs = effectiveEndMs) else it
        }
    }
    val backingSwept = remember(line, effectiveEndMs) { toBackingSwept(line, effectiveEndMs) }
    val picked by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "lyricPicked")
    val isSynced = state != 2
    val isActive = state == 0
    val falloffStep = distance.coerceIn(0, LINE_FALLOFF_ALPHA.lastIndex)
    val lineScale by animateFloatAsState(
        targetValue = if (!isSynced || !depthFalloff || selected || browsing) 1f else scaleForDistance(falloffStep),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f),
        label = "classicScale",
    )
    val lineAlpha by animateFloatAsState(
        targetValue = when {
            !isSynced || !depthFalloff || isActive || selected -> 1f
            browsing -> 0.80f
            else -> LINE_FALLOFF_ALPHA[falloffStep]
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f),
        label = "classicAlpha",
    )
    val lineBlur by animateDpAsState(
        targetValue = if (!isSynced || !depthFalloff || isActive || selected || browsing || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.dp else LINE_FALLOFF_BLUR[falloffStep],
        animationSpec = tween(220, easing = LYRIC_EASING),
        label = "classicBlur",
    )
    // Opposite-aligned duets: lead (voice 1 or null or 0) aligns left, voice 2 or > 1 aligns right.
    val isOppositeVoice = duet && (line.voice ?: 1) > 1
    val laneLocked = duet && (line.voice == null || line.voice >= 1)
    val align = if (isOppositeVoice) TextAlign.End else if (!laneLocked && isRtlText(line.text)) TextAlign.End else TextAlign.Start
    val originX = if (isOppositeVoice || (!laneLocked && isRtlText(line.text))) 1f else 0f
    val usesSwept = isSynced && clock != null && (line.words != null || swept.endMs > swept.timeMs)
    val roomDp = if (usesSwept && depthFalloff) GLOW_ROOM else 0.dp
    val roomPx = with(LocalDensity.current) { roomDp.toPx() }
    val lineModifier = modifier
        .fillMaxWidth()
        .padding(
            start = if (duet && isOppositeVoice) 24.dp else 0.dp,
            end = if (duet && !isOppositeVoice && laneLocked) 24.dp else 0.dp,
        )
        .graphicsLayer {
            scaleX = lineScale
            scaleY = lineScale
            transformOrigin = TransformOrigin(originX, 0.5f)
            alpha = lineAlpha
            if (scrollRun != null && scrollSince != null && depthFalloff && focusIndex >= 0) {
                val behind = if (scrollRun.delta >= 0f) lineIndex - focusIndex else focusIndex - lineIndex
                val staggerDelay = behind.coerceIn(0, CLASSIC_STAGGER_STEPS) * CLASSIC_STAGGER_FRACTION * scrollRun.durationMs
                if (staggerDelay > 0f) {
                    val elapsed = scrollSince.floatValue
                    translationY = scrollRun.delta * (
                        LYRIC_EASING.transform((elapsed / scrollRun.durationMs).coerceIn(0f, 1f)) -
                            LYRIC_EASING.transform(((elapsed - staggerDelay) / scrollRun.durationMs).coerceIn(0f, 1f))
                        )
                }
            }
            val r = lineBlur.toPx()
            renderEffect = if (r > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(r, r, TileMode.Decal) else null
        }
        .drawBehind {
            if (picked > 0f) drawRoundRect(
                palette.highlight.copy(alpha = 0.2f * picked),
                Offset(-6.dp.toPx(), -4.dp.toPx()),
                Size(size.width + 12.dp.toPx(), size.height + 8.dp.toPx()),
                CornerRadius(12.dp.toPx()),
            )
        }
        .then(
            if (onTap != null || onHold != null) Modifier.secondaryClick(onHold)
                .lastPress { at, _ -> pressAt = at }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onHold,
                    onClick = { onTap?.invoke(layout?.let { tapTime(swept, it, pressAt - Offset(roomPx + (if (usesSwept) textX else 0f), roomPx)) } ?: line.timeMs) },
                ) else Modifier
        )
    val underline = if (!annotated) Modifier else Modifier.drawBehind {
        val t = layout ?: return@drawBehind
        val dot = 2.dp.toPx()
        val effect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(0.01f, dot * 2.5f))
        for (r in 0 until t.lineCount) {
            val y = t.getLineBaseline(r) + roomPx + 5.dp.toPx()
            drawLine(
                color.copy(alpha = color.alpha * 0.55f),
                Offset(t.getLineLeft(r) + roomPx, y),
                Offset(t.getLineRight(r) + roomPx, y),
                strokeWidth = dot,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                pathEffect = effect,
            )
        }
    }
    val subPad = if (depthFalloff) Modifier.padding(horizontal = GLOW_ROOM) else Modifier
    Column(lineModifier) {
        val trimmed = line.text.trim()
        if (isSynced && (trimmed.isEmpty() || trimmed == "♪") && clock != null && nextLineMs != null) {
            InterludeDots(
                startMs = line.timeMs,
                endMs = nextLineMs,
                clock = clock,
                color = palette.highlight,
                glowColor = glowColor,
                alignEnd = isOppositeVoice,
                collapsible = false,
                onTap = onTap?.let { { it(line.timeMs) } },
            )
            return@Column
        }
        if (usesSwept && clock != null) {
            val baseStyle = androidx.compose.ui.text.TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                fontWeight = FontWeight.Bold,
                textAlign = align,
            )
            val dimAlpha by animateFloatAsState(
                targetValue = when (state) {
                    -1 -> SUNG_ALPHA
                    0 -> UNSUNG_ALPHA
                    else -> 0.90f
                },
                animationSpec = tween(220, easing = LYRIC_EASING),
                label = "classicDim",
            )
            SweptLyricLine(
                line = swept,
                clock = clock,
                style = baseStyle,
                dimAlpha = dimAlpha,
                glowAlpha = if (isActive && !browsing) GLOW_ALPHA else 0f,
                glowRoom = roomDp,
                feather = isActive,
                rise = line.words != null,
                alignEnd = isOppositeVoice,
                laneLocked = laneLocked,
                color = palette.highlight,
                glowColor = glowColor,
                onTextLayout = { layout = it },
                textModifier = Modifier
                    .onPlaced { textX = it.positionInParent().x }
                    .then(underline),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = line.text,
                color = color,
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                fontWeight = FontWeight.Bold,
                textAlign = align,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().then(subPad).then(underline),
            )
        }
        // Under the line: smaller, italicized, softly tinted backing vocals, then romanization, then translation.
        if (!line.background.isNullOrBlank()) {
            val backingColor = lerp(color, glowColor, 0.42f).copy(alpha = color.alpha * 0.66f)
            val backingStyle = androidx.compose.ui.text.TextStyle(
                fontSize = (fontSize.value * 0.66f).sp,
                lineHeight = (fontSize.value * 0.86f).sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                textAlign = align,
            )
            if (isSynced && clock != null && backingSwept != null) {
                SweptLyricLine(
                    line = backingSwept,
                    clock = clock,
                    style = backingStyle,
                    dimAlpha = if (state == 0) UNSUNG_ALPHA else 0.85f,
                    rise = false,
                    alignEnd = isOppositeVoice,
                    laneLocked = laneLocked,
                    color = backingColor,
                    modifier = Modifier.fillMaxWidth().then(subPad).padding(top = 2.dp),
                )
            } else {
                Text(
                    text = line.background.orEmpty(),
                    style = backingStyle,
                    color = backingColor,
                    modifier = Modifier.fillMaxWidth().then(subPad).padding(top = 2.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = !romanization.isNullOrBlank(),
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(260)),
        ) {
            Text(
                text = romanization.orEmpty(),
                color = color.copy(alpha = color.alpha * 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().then(subPad).padding(top = 2.dp),
            )
        }
        AnimatedVisibility(
            visible = !translation.isNullOrBlank(),
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(260)),
        ) {
            Text(
                text = translation.orEmpty(),
                color = color.copy(alpha = color.alpha * 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().then(subPad).padding(top = if (romanization.isNullOrBlank()) 2.dp else 8.dp),
            )
        }
    }
}

private fun lineState(lyrics: Lyrics, index: Int, active: Int): Int = when {
    !lyrics.synced -> 2
    index < active -> -1
    index == active -> 0
    else -> 1
}

/**
 * Fade + small rise of one part of the screen while the container settles. [reveal] is the
 * open progress (0..1), read in the draw phase only: nothing recomposes per frame.
 */
private fun Modifier.revealIn(reveal: (() -> Float)?, start: Float, riseDp: Float, span: Float = 0.3f): Modifier =
    if (reveal == null) this else graphicsLayer {
        val a = ((reveal() - start) / span).coerceIn(0f, 1f)
        alpha = a
        translationY = (1f - a) * riseDp.dp.toPx()
    }

/**
 * Multi-blob slowly drifting Kawarp-style mesh backdrop extracted from the track's cover colors,
 * overridden by [SpicyThemePreset] when active, or layered gently over [StarryNightBackdrop]
 * when Starry Night is enabled.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SpicyLyricsBackdrop(
    coverUri: String,
    meshColors: List<Color>,
    baseColor: Color,
    customSpicy: Boolean,
    starryActive: Boolean,
    oledBlack: Boolean,
    reduceMotion: Boolean,
    reveal: (() -> Float)?,
) {
    val animatedColors = (0 until 4).map { idx ->
        animateColorAsState(
            targetValue = meshColors.getOrElse(idx) { baseColor },
            animationSpec = tween(1_100),
            label = "kawarpBlob$idx",
        ).value
    }
    val animatedBase by animateColorAsState(
        targetValue = if (oledBlack) Color.Black else baseColor,
        animationSpec = tween(900),
        label = "kawarpBase",
    )
    // Kept as State and read inside the draw lambda so the drift does not recompose each frame.
    val meshPhaseState = rememberInfiniteTransition(label = "spicyMesh").animateFloat(
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
                    .graphicsLayer { alpha = if (reveal == null) 1f else (reveal() / 0.12f).coerceIn(0f, 1f) },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val a = if (reveal == null) 1f else (reveal() / 0.08f).coerceIn(0f, 1f)
                        drawRect(animatedBase, alpha = a)
                    },
            )
            if (!customSpicy && !oledBlack && coverUri.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlideImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (if (reveal == null) 1f else (reveal() / 0.15f).coerceIn(0f, 1f)) * 0.22f
                        }
                        .blur(68.dp),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val meshPhase = if (reduceMotion) 0f else meshPhaseState.value
                    val a = if (reveal == null) 1f else (reveal() / 0.10f).coerceIn(0f, 1f)
                    val blobStrength = when {
                        oledBlack -> 0.14f
                        starryActive -> 0.24f
                        else -> 0.42f
                    }
                    val anchors = arrayOf(
                        Offset(0.20f, 0.22f),
                        Offset(0.82f, 0.26f),
                        Offset(0.76f, 0.78f),
                        Offset(0.24f, 0.74f),
                    )
                    val speeds = floatArrayOf(1f, -0.75f, 0.85f, -1.1f)
                    for (i in 0 until 4) {
                        val angle = meshPhase * speeds[i] + i * 1.55f
                        val center = Offset(
                            size.width * (anchors[i].x + 0.14f * cos(angle)),
                            size.height * (anchors[i].y + 0.12f * sin(angle * 0.9f)),
                        )
                        val radius = size.maxDimension * (0.64f + 0.06f * (i % 2))
                        val alpha = blobStrength * (if (i % 2 == 0) 1f else 0.78f) * a
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
                    if (!starryActive && !oledBlack) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    animatedBase.copy(alpha = 0.30f * a),
                                    Color.Transparent,
                                    animatedBase.copy(alpha = 0.56f * a),
                                ),
                            ),
                        )
                    }
                },
        )
    }
}

/**
 * The Canvas of the song for the lyrics background ("Canvas behind lyrics"): null when the setting is
 * off, the song has none, or the usual allowances (Canvases and energy saving, data saver, Wi-Fi only,
 * no ad) say no. Looked up through the same cache as the players.
 */
@Composable
private fun rememberLyricsCanvas(trackId: String): String? {
    val context = LocalContext.current
    val energy by EnergySettings.state.collectAsState()
    val allowed = com.music.spotui.data.preferences.CanvasPref.behindLyrics && trackId.isNotBlank() &&
        energy.policy.allowCanvas && !com.music.spotui.data.DataSaver.active &&
        !(com.music.spotui.data.DataSaver.metered && com.music.spotui.data.preferences.isCanvasWifiOnly(context)) &&
        !SongPlayer.adPlaying
    val known = remember(trackId, allowed) {
        if (allowed) com.music.spotui.di.Graph.api.knownCanvas(trackId)?.getOrNull() else null
    }
    val fetched by androidx.compose.runtime.produceState<String?>(known, trackId, allowed) {
        value = known
        if (allowed && known == null) {
            value = runCatching { com.music.spotui.di.Graph.api.getCanvasUrl(trackId) }.getOrNull()
        }
    }
    return (fetched ?: known).takeIf { allowed && !it.isNullOrBlank() }
}

/** Full-screen lyrics (Spotify's "Lyrics" view). [reveal] drives the staggered entrance, null = all shown. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    onClose: () -> Unit,
    reveal: (() -> Float)? = null,
) {
    val context = LocalContext.current
    remember(context) { StarryNightPref.init(context); AstralThemePref.init(context); SpicyBackgroundPref.init(context); true }
    val vm: LyricsViewModel = viewModel()
    val state = rememberLyricsState(vm, title, artist)
    val lyrics = (state as? LyricsViewModel.State.Loaded)?.lyrics
    val coverUri = com.music.spotui.di.Graph.currentSongState.coverUri.value
    val rawArtwork = rememberArtworkColors(coverUri.takeIf { it.isNotBlank() })
    // The palette always carries stock fallback colours; only trust it when there is a cover.
    val artworkPalette = if (coverUri.isNotBlank()) rawArtwork else com.music.spotui.ui.look.MeshPalette(emptyList())
    val spicyPreset by AstralThemePref.spicyTheme.collectAsState()
    val starryEnabled by StarryNightPref.enabled.collectAsState()
    val oledBlack = EnergySettings.oledBlack
    val customSpicy = spicyPreset != SpicyThemePreset.ASTRAL_DEFAULT
    // As in the Immersive panel: the stars follow the Starry Night setting (the Look, or the extra), not a theme mode.
    val starryActive = !customSpicy && starryEnabled && !oledBlack
    val reduceMotion = rememberReduceMotion()

    val effectiveAccent = if (customSpicy) Color(spicyPreset.accentHex)
    else artworkPalette.colors.firstOrNull() ?: accentColor

    val palette = remember(lyrics, effectiveAccent, spicyPreset, customSpicy, starryActive, oledBlack) {
        if (customSpicy) {
            LyricsPalette(
                background = Color(spicyPreset.bgBottomHex),
                text = Color.White.copy(alpha = 0.80f),
                highlight = Color(spicyPreset.accentHex),
            )
        } else if (starryActive) {
            LyricsPalette(
                background = Color(0xFF0A1422),
                text = Color.White.copy(alpha = 0.80f),
                highlight = Color.White,
            )
        } else {
            val base = paletteFor(lyrics, effectiveAccent)
            val darkBase = if (oledBlack) Color.Black else lerp(effectiveAccent, Color.Black, 0.78f)
            val bg = if (lyrics?.colors != null && !oledBlack) lerp(base.background, Color.Black, 0.45f) else darkBase
            val hi = if (contrast(base.highlight, bg) >= MIN_CONTRAST) base.highlight else Color.White
            LyricsPalette(
                background = bg,
                text = Color.White.copy(alpha = 0.80f),
                highlight = hi,
            )
        }
    }
    val spicyGlow = when {
        customSpicy -> Color(spicyPreset.glowHex)
        starryActive -> artworkPalette.colors.firstOrNull()?.let { lerp(it, StarryAccent, 0.45f) } ?: StarryAccent
        else -> lerp(artworkPalette.colors.getOrElse(1) { effectiveAccent }, Color.White, 0.28f)
    }
    val meshColors = remember(customSpicy, spicyPreset, artworkPalette, effectiveAccent, spicyGlow) {
        if (customSpicy) {
            listOf(
                Color(spicyPreset.accentHex),
                Color(spicyPreset.glowHex),
                Color(spicyPreset.bgBottomHex),
                lerp(Color(spicyPreset.bgTopHex), Color(spicyPreset.accentHex), 0.40f),
            )
        } else {
            (artworkPalette.colors + listOf(effectiveAccent, spicyGlow, palette.background)).take(4)
        }
    }
    val meshBase = if (customSpicy) lerp(Color(spicyPreset.bgTopHex), Color(spicyPreset.bgBottomHex), 0.5f) else palette.background

    val playing = com.music.spotui.di.Graph.currentSongState.playingState.value
    val position = rememberPosition(playing)
    val clock = rememberLyricsClock(playing, position)
    val songDurationMs = SongPlayer.getDuration().coerceAtLeast(0L)
    KeepScreenOnForLyrics(playing && lyrics != null)
    val targetLanguage = getLyricTranslateLang(context).ifBlank { Locale.getDefault().language.ifBlank { "en" } }
    var menu by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var spicySheet by remember { mutableStateOf(false) }
    val spicy by SpicyBackgroundPref.state.collectAsState()
    // Lines picked to share: a long press on a line, or "Share lyrics" in the menu, starts it.
    val selecting by vm.selecting.collectAsState()
    val picked by vm.picked.collectAsState()
    androidx.activity.compose.BackHandler(enabled = selecting) { vm.stopSelecting() }
    DisposableEffect(Unit) { onDispose { vm.stopSelecting() } }
    val lang = com.music.spotui.ui.components.readerLanguage()
    val trackId = SongPlayer.nowPlaying.value.uri.substringAfterLast(':')
    val meaning = com.music.spotui.data.api.GeniusMeaning.currentFor(trackId)
    var meaningOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf<Int?>(null) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hintPrefs = remember { context.getSharedPreferences("lyrics", android.content.Context.MODE_PRIVATE) }
    var hintSeen by remember { mutableStateOf(hintPrefs.getBoolean("genius_hold_hint_seen", false)) }
    LaunchedEffect(trackId, title, artist) {
        if (meaning == null) com.music.spotui.data.api.GeniusMeaning.prefetch(trackId, title, artist, lang)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        // The Spicy Lyrics renderer and its background preset, unless it is switched off in the background
        // sheet, or a translation is shown or lines are being picked to share (the classic list carries those).
        val useSpicy = spicy.renderer && !selecting && (state as? LyricsViewModel.State.Loaded)?.showTranslation != true &&
            (lyrics == null || hasDrawableLines(lyrics))
        if (useSpicy) SpicyBackgroundLayer(
            coverUri = coverUri,
            artist = artist,
            playing = playing,
            positionMs = position,
            settings = spicy,
            reduceMotion = reduceMotion,
        ) else SpicyLyricsBackdrop(
            coverUri = coverUri,
            meshColors = meshColors,
            baseColor = meshBase,
            customSpicy = customSpicy,
            starryActive = starryActive,
            oledBlack = oledBlack,
            reduceMotion = reduceMotion,
            reveal = reveal,
        )
        // "Canvas behind lyrics": the song's Canvas, cropped to fill, fades in over the Spicy background
        // above (which stays as the fallback when there is none) under the same scrim as the Immersive player.
        val canvasUrl = rememberLyricsCanvas(trackId)
        if (canvasUrl != null) {
            var canvasShown by remember(canvasUrl) {
                mutableStateOf(com.music.spotui.ui.components.CanvasPlayer.isReady(canvasUrl))
            }
            val canvasAlpha = animateFloatAsState(if (canvasShown) 1f else 0f, tween(300), label = "lyricsCanvas")
            com.music.spotui.ui.immersive.player.CanvasBackgroundVideo(
                url = canvasUrl,
                onFirstFrame = { canvasShown = true },
                playing = playing,
                blurPercent = com.music.spotui.data.preferences.CanvasPref.blur,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha.value },
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha.value }
                    .background(com.music.spotui.ui.immersive.player.CanvasScrimBrush),
            )
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = canvasAlpha.value * com.music.spotui.ui.immersive.player.CANVAS_PANEL_DIM }
                    .background(Color.Black),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
        // ── Spicy-style song header: close, cover thumbnail + title / artist, meaning, more ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp).revealIn(reveal, 0.3f, 8f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose,
                ),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.lyrics_fullscreen_close_button_readout_value), tint = palette.highlight, modifier = Modifier.size(30.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            ) {
                GlideImage(
                    model = coverUri.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder),
                    failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = palette.highlight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.titleMarquee(),
                    )
                    Text(
                        text = artist,
                        color = palette.highlight.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            meaning?.takeIf { !it.about.isNullOrBlank() }?.let { m ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { meaningOpen = true },
                ) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = stringResource(R.string.lyrics_meaning_title), tint = palette.highlight)
                }
                if (meaningOpen) com.music.spotui.ui.components.AboutSheet(m, palette.background) { meaningOpen = false }
            }
            SpicyBackgroundButton(onClick = { spicySheet = true }, tint = palette.highlight, modifier = Modifier.padding(horizontal = 4.dp))
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { menu = true },
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_description_context_menu), tint = palette.highlight)
                }
                LyricsMenu(
                    expanded = menu,
                    onDismiss = { menu = false },
                    canTranslate = state is LyricsViewModel.State.Loaded,
                    translating = (state as? LyricsViewModel.State.Loaded)?.showTranslation == true,
                    onTranslate = { vm.toggleTranslation(targetLanguage) },
                    onReload = { SongPlayer.currentLyricsTrack()?.let { vm.load(it, force = true) } },
                    canShare = state is LyricsViewModel.State.Loaded,
                    onShare = { vm.startSelecting() },
                    canRomanize = lyrics?.hasRomanization == true,
                    romanizing = LyricsRomanizationPref.on,
                    onRomanize = { LyricsRomanizationPref.set(context, !LyricsRomanizationPref.on) },
                    canPickSource = state !is LyricsViewModel.State.Loading,
                    onPickSource = { sourcesOpen = true },
                )
            }
        }

        if (sourcesOpen) com.music.spotui.ui.components.LyricsSourceSheet(onDismiss = { sourcesOpen = false }, vm = vm)
        if (spicySheet) SpicyBackgroundSheet { spicySheet = false }

        when (state) {
            is LyricsViewModel.State.Loading ->
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    com.music.spotui.ui.components.SkeletonLyrics(palette.highlight)
                }
            is LyricsViewModel.State.NotFound ->
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.lyrics_fullscreen_no_lyrics_error_version_two), color = palette.highlight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            is LyricsViewModel.State.Loaded -> {
                val l = state.lyrics
                val m = meaning
                noteOpen?.let { n -> if (m != null) com.music.spotui.ui.components.NoteSheet(m, n, palette.background) { noteOpen = null } }
                val activeForShare by remember(l) { derivedStateOf { activeIndexFor(l, position()) } }
                androidx.compose.animation.Crossfade(
                    targetState = l, animationSpec = tween(450), label = "lyricsSwap",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { l ->
                if (useSpicy) {
                    val spicyRomanize = LyricsRomanizationPref.on && l.hasRomanization
                    // A source that calls itself synced with no real time on any line is static, as in the Immersive panel.
                    val spicyLyrics = if (l.synced && l.lines.none { it.timeMs > 0L }) l.copy(synced = false) else l
                    SpicyLyricsLayer(
                        lyrics = spicyLyrics,
                        documentKey = "$title|$artist",
                        currentTimeMs = position,
                        playing = playing && spicyLyrics.synced,
                        onSeek = { jumpTo(it) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        romanize = spicyRomanize,
                        songDurationMs = songDurationMs,
                        reduceMotion = reduceMotion,
                        noticeColor = palette.highlight,
                    )
                    return@Crossfade
                }
                val active by remember(l) { derivedStateOf { activeIndexFor(l, position()) } }
                val focus by remember(l) { derivedStateOf { focusIndexFor(l, position()) } }
                val focusLine = if (focus >= 0) focus else active
                val notes = remember(l, meaning) { meaning?.let { m -> com.music.spotui.ui.components.notesByLine(m, l.lines.map { it.text }) }.orEmpty() }
                val listState = rememberLazyListState()
                val duet = remember(l) { l.hasDuet || l.lines.any { (it.voice ?: 0) > 1 } }
                val romanizing = LyricsRomanizationPref.on && l.hasRomanization
                val staggerBase = remember(l) { (active - 4).coerceAtLeast(0) }
                val firstSung = remember(l) { l.lines.firstOrNull { it.text.isNotBlank() && it.text.trim() != "♪" } }
                val hasIntro = l.synced && firstSung != null && firstSung.timeMs > 4_000L
                val headerCount = (if (notes.isNotEmpty() && !hintSeen) 1 else 0) +
                    (if (!l.synced) 1 else 0) +
                    (if (hasIntro) 1 else 0)
                val scrollTarget = if (focusLine < 0) -1 else focusLine + headerCount
                var browsing by remember { mutableStateOf(false) }
                var scrollRun by remember { mutableStateOf(ClassicScrollRun(0, 0f, LYRIC_SETTLE_MS)) }
                val scrollSince = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(scrollRun.id) {
                    if (scrollRun.id == 0) return@LaunchedEffect
                    animate(
                        initialValue = 0f,
                        targetValue = scrollRun.durationMs.toFloat(),
                        animationSpec = tween(scrollRun.durationMs, easing = LinearEasing),
                    ) { value, _ -> scrollSince.floatValue = value }
                }
                val hPad = (24.dp - GLOW_ROOM).coerceAtLeast(0.dp)
                BoxWithConstraints(Modifier.fillMaxHeight().cappedLyricsWidth()) {
                    val offsetPx = with(LocalDensity.current) { (maxHeight * 0.34f).roundToPx() }
                    AutoFollow(
                        listState = listState,
                        activeIndex = scrollTarget,
                        offsetPx = offsetPx,
                        enabled = l.synced && !selecting,
                        reduceMotion = reduceMotion,
                        onBrowsingChange = { browsing = it },
                        onScrollRun = {
                            scrollSince.floatValue = 0f
                            scrollRun = it
                        },
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(hPad, 8.dp, hPad, 48.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (notes.isNotEmpty() && !hintSeen) item(key = "genius-hint") {
                            Text(
                                stringResource(R.string.lyrics_meaning_hint),
                                color = palette.highlight.copy(alpha = 0.8f), fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = GLOW_ROOM).padding(bottom = 8.dp).revealIn(reveal, 0.35f, 22f),
                            )
                        }
                        if (!l.synced) item {
                            Text(
                                stringResource(R.string.lyrics_fullscreen_unsynced),
                                color = palette.highlight.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = GLOW_ROOM).padding(bottom = 8.dp).revealIn(reveal, 0.35f, 22f),
                            )
                        }
                        if (hasIntro && firstSung != null) item(key = "intro-countdown") {
                            InterludeDots(
                                startMs = 0L,
                                endMs = firstSung.timeMs,
                                clock = clock,
                                color = palette.highlight,
                                glowColor = spicyGlow,
                                alignEnd = duet && (firstSung.voice ?: 1) > 1,
                                collapsible = true,
                                onTap = if (!selecting) ({ jumpTo(0L) }) else null,
                            )
                        }
                        itemsIndexed(l.lines, key = { i, _ -> i }) { i, line ->
                            val nextLineMs = l.lines.getOrNull(i + 1)?.timeMs
                            val dist = if (active < 0) 0 else abs(i - active)
                            Column {
                                LyricLineText(
                                    line = line,
                                    state = lineState(l, i, active),
                                    palette = palette,
                                    fontSize = 24.sp,
                                    position = position,
                                    translation = if (state.showTranslation) state.translations?.getOrNull(i) else null,
                                    romanization = if (romanizing) line.romanization?.trim()?.takeIf { it.isNotEmpty() && !it.equals(line.text.trim(), ignoreCase = true) } else null,
                                    duet = duet,
                                    onTap = if (selecting) ({ _ ->
                                        if (!vm.pick(i)) android.widget.Toast.makeText(context, context.getString(R.string.lyrics_select_max, LyricsViewModel.MAX_SHARED_LINES), android.widget.Toast.LENGTH_SHORT).show()
                                    }) else if (l.synced) ({ jumpTo(it) }) else null,
                                    annotated = i in notes,
                                    onHold = if (selecting) null else notes[i]?.let { n -> {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        noteOpen = n
                                        if (!hintSeen) { hintSeen = true; hintPrefs.edit().putBoolean("genius_hold_hint_seen", true).apply() }
                                    } } ?: {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        vm.startSelecting(i)
                                    },
                                    selected = picked?.let { i in it } == true,
                                    distance = dist,
                                    nextLineMs = nextLineMs,
                                    clock = clock,
                                    depthFalloff = true,
                                    glowColor = spicyGlow,
                                    browsing = browsing || selecting,
                                    songDurationMs = songDurationMs,
                                    scrollRun = scrollRun,
                                    scrollSince = scrollSince,
                                    lineIndex = i,
                                    focusIndex = focusLine,
                                    modifier = Modifier.revealIn(reveal, 0.35f + 0.045f * (i - staggerBase).coerceIn(0, 8), 22f),
                                )
                                val lastWordEnd = line.words?.lastOrNull()?.endMs
                                val nextLine = l.lines.getOrNull(i + 1)
                                val nextText = nextLine?.text?.trim().orEmpty()
                                if (l.synced && lastWordEnd != null && nextLineMs != null && nextLineMs - lastWordEnd > 4_000L && nextText.isNotEmpty() && nextText != "♪") {
                                    val gapVoice = nextLine?.voice ?: line.voice
                                    InterludeDots(
                                        startMs = lastWordEnd + 200L,
                                        endMs = nextLineMs,
                                        clock = clock,
                                        color = palette.highlight,
                                        glowColor = spicyGlow,
                                        alignEnd = duet && (gapVoice ?: 1) > 1,
                                        collapsible = true,
                                        onTap = if (!selecting) ({ jumpTo(lastWordEnd + 200L) }) else null,
                                    )
                                }
                            }
                        }
                        l.provider?.takeIf { it.isNotBlank() }?.let { p ->
                            item(key = "provider-credit") {
                                Text(
                                    "Lyrics by $p",
                                    color = palette.highlight.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = GLOW_ROOM).padding(top = 24.dp).revealIn(reveal, 0.71f, 22f),
                                )
                            }
                        }
                    }
                }
                }
                val sameLanguage = LyricsViewModel.sameLanguage(state.sourceLanguage, targetLanguage)
                if (selecting) LyricsSelectionBar(
                    palette = palette,
                    canShare = picked != null,
                    onCancel = { vm.stopSelecting() },
                    onShare = { picked?.let { shareLyricLines(context, l, it, title, artist); vm.stopSelecting() } },
                ) else LyricsControls(
                    palette = palette,
                    position = position,
                    showTranslate = state.translationChecked && !sameLanguage,
                    translationActive = state.showTranslation,
                    translating = state.translating && state.translationRequested,
                    onTranslate = { vm.toggleTranslation(targetLanguage) },
                    showRomanize = l.hasRomanization,
                    romanizationActive = LyricsRomanizationPref.on,
                    onRomanize = { LyricsRomanizationPref.set(context, !LyricsRomanizationPref.on) },
                    onShare = { shareLyrics(context, title, artist, l.lines.getOrNull(activeForShare)?.text) },
                    modifier = Modifier.revealIn(reveal, 0.5f, 16f, span = 0.5f),
                )
            }
        }
    }
    }
}

@Composable
private fun LyricsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canTranslate: Boolean,
    translating: Boolean,
    onTranslate: () -> Unit,
    onReload: () -> Unit,
    canShare: Boolean,
    onShare: () -> Unit,
    canRomanize: Boolean,
    romanizing: Boolean,
    onRomanize: () -> Unit,
    canPickSource: Boolean,
    onPickSource: () -> Unit,
) {
    val context = LocalContext.current
    var extra by remember { mutableStateOf(isLyricsExtraSources(context)) }
    var words by remember { mutableStateOf(com.music.spotui.data.preferences.isLyricsWordSync(context)) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Word by word (karaoke) or line by line: the app then picks the source by itself.
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(R.string.lyrics_word_sync_title))
                    Text(stringResource(if (words) R.string.lyrics_word_sync_on else R.string.lyrics_word_sync_off), fontSize = 12.sp, color = Color.Gray)
                }
            },
            trailingIcon = { if (words) Icon(Icons.Default.Check, contentDescription = null) },
            onClick = {
                words = !words
                com.music.spotui.data.preferences.setLyricsWordSync(context, words)
                LyricsApi.wordSync = words
                LyricsApi.forgetAll()
                onDismiss()
                onReload()
            },
        )
        if (canTranslate) DropdownMenuItem(
            text = { Text(stringResource(if (translating) R.string.lyrics_fullscreen_translate_button_enabled_readout_value else R.string.lyrics_fullscreen_context_menu_main_page_translate_lyrics_action)) },
            onClick = { onDismiss(); onTranslate() },
        )
        // The lines in Latin letters, when the lyrics have them.
        if (canRomanize) DropdownMenuItem(
            text = { Text(stringResource(R.string.lyrics_romanization)) },
            trailingIcon = { if (romanizing) Icon(Icons.Default.Check, contentDescription = null) },
            onClick = { onDismiss(); onRomanize() },
        )
        // Pick lines of the song and send them as text.
        if (canShare) DropdownMenuItem(
            text = { Text(stringResource(R.string.lyrics_share)) },
            onClick = { onDismiss(); onShare() },
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(R.string.lyrics_extra_title))
                    Text(stringResource(R.string.lyrics_extra_summary), fontSize = 12.sp, color = Color.Gray)
                }
            },
            trailingIcon = { if (extra) Icon(Icons.Default.Check, contentDescription = null) },
            onClick = {
                extra = !extra
                setLyricsExtraSources(context, extra)
                LyricsApi.extraSources = extra
                // Turned off by hand: Genius is no longer asked even when nothing else has the song.
                LyricsApi.extraSourcesOff = !extra
                onDismiss()
                onReload()
            },
        )
        if (canPickSource) DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_other_source)) }, onClick = { onDismiss(); onPickSource() })
        DropdownMenuItem(text = { Text(stringResource(R.string.lyrics_reload)) }, onClick = { onDismiss(); onReload() })
    }
}

@Composable
private fun LyricsChip(label: String, icon: Painter?, active: Boolean, loading: Boolean, palette: LyricsPalette, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) palette.highlight else Color.Black.copy(alpha = 0.22f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (loading) CircularProgressIndicator(color = palette.highlight, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        else if (icon != null) Icon(icon, contentDescription = null, tint = if (active) palette.background else palette.highlight, modifier = Modifier.size(16.dp))
        if (loading || icon != null) Spacer(Modifier.width(6.dp))
        Text(label, color = if (active) palette.background else palette.highlight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Where the controls are while lines are being picked: what to do, then Cancel and Share. */
@Composable
private fun LyricsSelectionBar(palette: LyricsPalette, canShare: Boolean, onCancel: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) {
        Text(stringResource(R.string.lyrics_select_hint), color = palette.highlight.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LyricsChip(stringResource(R.string.cancel), null, false, false, palette, onCancel)
            // Nothing picked yet: the chip waits, dimmed.
            Box(Modifier.graphicsLayer { alpha = if (canShare) 1f else 0.4f }) {
                LyricsChip(stringResource(R.string.lyrics_fullscreen_chip_label_share), painterResource(R.drawable.ic_share), true, false, palette) { if (canShare) onShare() }
            }
        }
    }
}

@Composable
private fun LyricsControls(
    palette: LyricsPalette,
    position: () -> Long,
    showTranslate: Boolean,
    translationActive: Boolean,
    translating: Boolean,
    onTranslate: () -> Unit,
    showRomanize: Boolean,
    romanizationActive: Boolean,
    onRomanize: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = SongPlayer.getDuration().coerceAtLeast(0L)
    val playing = com.music.spotui.di.Graph.currentSongState.playingState.value
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            LyricsChip(stringResource(R.string.lyrics_fullscreen_chip_label_share), painterResource(R.drawable.ic_share), false, false, palette, onShare)
            if (showTranslate) LyricsChip(stringResource(R.string.lyrics_fullscreen_chip_label_translate), painterResource(R.drawable.ic_translate_lyrics), translationActive, translating, palette, onTranslate)
            if (showRomanize) LyricsChip(stringResource(R.string.lyrics_romanize), rememberVectorPainter(Icons.Rounded.Abc), romanizationActive, false, palette, onRomanize)
        }
        LyricsSeekBar(
            progress = { if (durationMs > 0) (position().toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            color = palette.highlight,
            onSeek = { SongPlayer.seekTo((durationMs * it).toLong()) },
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            val pos by remember { derivedStateOf { position() / 1000 } }
            Text(formatLyricsTime(pos * 1000), color = palette.highlight.copy(alpha = 0.75f), fontSize = 12.sp)
            Text(formatLyricsTime(durationMs), color = palette.highlight.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(palette.highlight)
                .pressable(0.92f) { if (SongPlayer.isPlaying()) SongPlayer.pause() else SongPlayer.play() },
        ) {
            com.music.spotui.ui.components.PlayPauseIcon(
                playing = playing,
                contentDescription = if (playing) stringResource(R.string.content_description_pause_button) else stringResource(R.string.content_description_play_button),
                tint = palette.background,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun LyricsSeekBar(progress: () -> Float, color: Color, onSeek: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragValue = (dragValue + delta / widthPx).coerceIn(0f, 1f) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(widthPx) {
                detectTapGestures { onSeek((it.x / widthPx).coerceIn(0f, 1f)) }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = { dragging = true; dragValue = progress() },
                onDragStopped = { dragging = false; onSeek(dragValue) },
            ),
    ) {
        val value = if (dragging) dragValue else progress()
        val h = 3.dp.toPx()
        val y = size.height / 2f
        drawRoundRect(color.copy(alpha = 0.3f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * value, h), CornerRadius(h / 2))
        drawCircle(color, radius = 6.dp.toPx(), center = Offset(size.width * value, y))
    }
}

private fun formatLyricsTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

/** Height of the lines window in the Now Playing card. */
private val CARD_LINES_HEIGHT = 296.dp

/**
 * The lyrics card in the Now Playing scroll. Hidden while loading and when the
 * track has no lyrics, as in the official app.
 */
@Composable
fun InlineLyrics(
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    onExpand: () -> Unit,
) {
    val context = LocalContext.current
    val vm: LyricsViewModel = viewModel()
    // Only read: the player's page loads the song. This card enters the list whenever a scroll
    // reaches it, and a load from here asked a song without lyrics again each time: Loading gave
    // the page its lyrics peek back, NotFound took it away, and the page shook under a held finger (#192).
    val state = vm.state.collectAsState().value
    val s = state as? LyricsViewModel.State.Loaded ?: return
    val l = s.lyrics
    val palette = paletteFor(l, accentColor)
    val playing = com.music.spotui.di.Graph.currentSongState.playingState.value
    val position = rememberPosition(playing)
    val clock = rememberLyricsClock(playing, position)
    val active by remember(l) { derivedStateOf { activeIndexFor(l, position()) } }
    val listState = rememberLazyListState()
    LaunchedEffect(active, l) {
        if (l.synced && active >= 0) listState.animateScrollToItem((active - 1).coerceAtLeast(0))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp, 16.dp, 40.dp)
            // The card's live bounds are read when the lyrics open / close (see LyricsOverlay).
            .onGloballyPositioned { LyricsCardAnchor.coords = it }
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onExpand() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (l.uncapped) R.string.lyrics_card_title else R.string.lyrics_card_title_preview),
                color = palette.highlight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CardIconButton(palette, stringResource(R.string.lyrics_share), { Icon(painterResource(R.drawable.ic_share), null, tint = palette.highlight, modifier = Modifier.size(16.dp)) }) {
                shareLyrics(context, title, artist, l.lines.getOrNull(active)?.text)
            }
            Spacer(Modifier.width(8.dp))
            CardIconButton(palette, stringResource(R.string.lyrics_fullscreen), { Icon(Icons.Default.OpenInFull, null, tint = palette.highlight, modifier = Modifier.size(16.dp)) }, onExpand)
        }
        Spacer(Modifier.height(12.dp))
        val inlineDuet = remember(l) { l.hasDuet || l.lines.any { (it.voice ?: 0) > 1 } }
        val songDurationMs = SongPlayer.getDuration().coerceAtLeast(0L)
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(CARD_LINES_HEIGHT),
        ) {
            itemsIndexed(l.lines, key = { i, _ -> i }) { i, line ->
                LyricLineText(
                    line = line,
                    state = lineState(l, i, active),
                    palette = palette,
                    fontSize = 22.sp,
                    position = position,
                    translation = null,
                    onTap = null,
                    duet = inlineDuet,
                    distance = if (active < 0) 0 else abs(i - active),
                    nextLineMs = l.lines.getOrNull(i + 1)?.timeMs,
                    clock = clock,
                    depthFalloff = false,
                    songDurationMs = songDurationMs,
                )
            }
        }
        if (!l.uncapped) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.highlight)
                    .clickable { onExpand() }
                    .padding(16.dp, 8.dp),
            ) {
                Text(stringResource(R.string.lyrics_card_show_lyrics_button), color = palette.background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CardIconButton(palette: LyricsPalette, description: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onClick),
    ) { icon() }
}

/** The inline card's layout node, so the full screen can grow out of it and fold back into it. */
private object LyricsCardAnchor { var coords: LayoutCoordinates? = null }

/** The visible part of the container: a rounded rectangle that moves from the card to the screen. */
private class LyricsWindow(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

private val CARD_CORNER = 12.dp

/**
 * Hosts [LyricsScreen] as a container transform: the screen is laid out once at full size and
 * revealed through a window that grows from the inline card's bounds (position, size, corner
 * radius, same colour) to the whole page, while its lines rise in staggered; closing plays
 * it backwards into the card. Only graphicsLayer / draw state moves per frame, no recomposition.
 * Without a visible card (scrolled away) it slides up as it always did.
 */
@Composable
fun LyricsOverlay(
    visible: Boolean,
    title: String,
    artist: String,
    album: String,
    accentColor: Color,
    onClose: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    // Window the container starts from, in this host's pixels; null = slide-up fallback.
    var from by remember { mutableStateOf<Rect?>(null) }
    val host = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // "Remove animations": the lyrics are there, or gone, at once.
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()

    fun cardRect(): Rect? {
        val h = host[0]?.takeIf { it.isAttached } ?: return null
        val card = LyricsCardAnchor.coords?.takeIf { it.isAttached } ?: return null
        val r = h.localBoundingBoxOf(card, clipBounds = false)
        val screen = Rect(0f, 0f, h.size.width.toFloat(), h.size.height.toFloat())
        val seen = r.intersect(screen)
        // Mostly off-screen card: growing out of it would look odd, slide up instead.
        return if (r.height > 0f && seen.height >= r.height * 0.4f && seen.width > 0f) r else null
    }

    LaunchedEffect(visible) {
        if (visible) {
            from = cardRect()
            mounted = true
            val spec = if (from != null) tween<Float>(560, easing = FastOutSlowInEasing) else tween(250, easing = EaseOut)
            if (reduce) progress.snapTo(1f) else progress.animateTo(1f, spec)
        } else if (mounted) {
            // Fold into the card where it is now; a slide-up opening slides back down.
            if (from != null) cardRect()?.let { from = it }
            val spec = if (from != null) tween<Float>(470, easing = FastOutSlowInEasing) else tween(200, easing = EaseOut)
            if (reduce) progress.snapTo(0f) else progress.animateTo(0f, spec)
            mounted = false
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { host[0] = it }) {
        if (!mounted) return@Box
        val window = from
        val cornerPx = with(LocalDensity.current) { CARD_CORNER.toPx() }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                if (window == null) {
                    translationY = (1f - p) * size.height
                } else {
                    shape = LyricsWindow(
                        Rect(
                            window.left * (1f - p), window.top * (1f - p),
                            window.right + (size.width - window.right) * p, window.bottom + (size.height - window.bottom) * p,
                        ),
                        cornerPx * (1f - p),
                    )
                    clip = true
                }
            },
        ) {
            LyricsScreen(
                title = title,
                artist = artist,
                album = album,
                accentColor = accentColor,
                onClose = onClose,
                reveal = if (window == null) null else ({ progress.value }),
            )
        }
    }
}

/**
 * The line being sung and the one after it, for the picture-in-picture window ([SongPip]): both
 * null while the song has no synced lyrics (or none were found).
 */
@Composable
internal fun rememberPipLyricLines(title: String, artist: String): Pair<String?, String?> {
    val state = rememberLyricsState(viewModel(), title, artist)
    val l = (state as? LyricsViewModel.State.Loaded)?.lyrics?.takeIf { it.synced } ?: return null to null
    val position = rememberPosition(com.music.spotui.di.Graph.currentSongState.playingState.value)
    val active by remember(l) { derivedStateOf { activeIndexFor(l, position()) } }
    val now = l.lines.getOrNull(active)?.text?.takeIf { it.isNotBlank() }
    val next = l.lines.drop(active + 1).firstOrNull { it.text.isNotBlank() }?.text
    return now to next
}
