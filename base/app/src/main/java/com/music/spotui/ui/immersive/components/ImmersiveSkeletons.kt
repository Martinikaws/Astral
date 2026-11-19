/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/components/Skeletons.kt at edc1e6f. Modified for Myspoti, 2026-10.
 */
package com.music.spotui.ui.immersive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.music.spotui.ui.components.leavingPlaceholder
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.rememberReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Grey stand-ins for content still on the wire, laid out to the same metrics as the real rows and
 * cards so nothing jumps when the data lands.
 */

/** How long one highlight sweep takes to cross a placeholder. */
private const val SHIMMER_PERIOD_MS = 1400

private val BlockShape = RoundedCornerShape(6.dp)
private val LineShape = RoundedCornerShape(4.dp)

// Ragged widths, so a run of rows reads as text rather than as a barcode.
private val TitleWidths = listOf(0.68f, 0.46f, 0.58f, 0.74f, 0.52f)
private val SubtitleWidths = listOf(0.34f, 0.44f, 0.27f, 0.38f, 0.31f)

/**
 * Single shared, frame-paced shimmer clock so all visible placeholder blocks sweep in unison
 * without spawning 15-25 individual infinite transitions or waking the display while stopped.
 */
private object ShimmerClock {
    val progress = mutableFloatStateOf(0f)
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    @Volatile private var activeCount = 0
    @Volatile private var currentFps = 30
    @Volatile private var job: Job? = null

    @Synchronized
    fun acquire(fps: Int, frameClock: MonotonicFrameClock? = null) {
        currentFps = fps.coerceIn(1, 60)
        activeCount++
        if (activeCount == 1 || job?.isActive != true) start(frameClock)
    }

    @Synchronized
    fun release() {
        if (activeCount > 0 && --activeCount == 0) {
            job?.cancel()
            job = null
        }
    }

    private fun start(frameClock: MonotonicFrameClock?) {
        if (job?.isActive == true) return
        val clockContext = frameClock ?: AndroidUiDispatcher.Main
        val launched = scope.launch {
            val me = coroutineContext[Job]
            try {
                withContext(clockContext) {
                    val periodNs = SHIMMER_PERIOD_MS * 1_000_000L
                    var startNs = 0L
                    while (isActive && activeCount > 0) {
                        val nowNs = withFrameNanos { it }
                        if (startNs == 0L) startNs = nowNs
                        val elapsed = (nowNs - startNs).coerceAtLeast(0L)
                        progress.floatValue = (elapsed % periodNs).toFloat() / periodNs
                        val framePeriodMs = 1000L / currentFps.coerceIn(1, 60)
                        delay((framePeriodMs - 4).coerceAtLeast(0))
                    }
                }
            } finally {
                synchronized(this@ShimmerClock) {
                    if (job === me) job = null
                }
            }
        }
        if (launched.isActive) {
            job = launched
        }
    }
}

/**
 * One placeholder block, with a highlight sweeping across it.
 *
 * The shared sweep clock is read inside `onDrawBehind` only while the gradient brush is cached in
 * `drawWithCache`, so neither composition nor shader creation runs per animation frame.
 */
@Composable
fun ImmersiveShimmerBox(modifier: Modifier = Modifier, shape: Shape = BlockShape) {
    val base = Immersive.Raised
    val highlight = Immersive.Text.copy(alpha = 0.10f).compositeOver(base)
    val reduce = rememberReduceMotion()
    // Under "reduce motion" the band stays off the block: a flat placeholder, nothing endless running.
    if (!reduce) {
        val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val fps = energy.policy.progressFps.coerceIn(1, 60)
        val frameClock = rememberCoroutineScope().coroutineContext[MonotonicFrameClock]
        DisposableEffect(lifecycle, fps, frameClock) {
            var acquired = false
            fun sync(started: Boolean) {
                if (started && !acquired) {
                    acquired = true
                    ShimmerClock.acquire(fps, frameClock)
                } else if (!started && acquired) {
                    acquired = false
                    ShimmerClock.release()
                }
            }
            sync(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            val observer = LifecycleEventObserver { _, _ ->
                sync(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                sync(false)
            }
        }
    }
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val band = size.width * 0.5f
                if (reduce || band <= 0f || size.height <= 0f) {
                    onDrawBehind { drawRect(base) }
                } else {
                    val brush = Brush.horizontalGradient(
                        colors = listOf(base, highlight, base),
                        startX = 0f,
                        endX = band,
                    )
                    val bandSize = Size(band, size.height)
                    onDrawBehind {
                        drawRect(base)
                        val sweep = ShimmerClock.progress.floatValue
                        // The band travels from fully off one edge to fully off the other, which leaves a
                        // beat of flat grey between passes rather than a highlight parked on the block.
                        val startX = -band + sweep * (size.width + band * 2f)
                        if (startX < size.width && startX + band > 0f) {
                            translate(left = startX) {
                                drawRect(brush = brush, topLeft = Offset.Zero, size = bandSize)
                            }
                        }
                    }
                }
            },
    )
}

/** A placeholder for one line of text, sized as a fraction of its parent. */
@Composable
private fun SkeletonLine(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    ImmersiveShimmerBox(
        modifier = modifier.fillMaxWidth(fraction).height(height),
        shape = LineShape,
    )
}

/** Stands in for a [SectionHeader]. Only the title line is drawn: most shelves have no subtitle. */
@Composable
fun ImmersiveSectionHeaderSkeleton(index: Int = 0) {
    Column(Modifier.padding(horizontal = Immersive.Gutter, vertical = 10.dp)) {
        SkeletonLine(fraction = TitleWidths[index % TitleWidths.size] * 0.7f, height = 20.dp)
    }
}

/**
 * Stands in for one [ImmersiveSongRow], down to the 52 dp cover and the gutter. [circular] matches
 * rows of artists, drawn as circles.
 */
@Composable
fun ImmersiveSongRowSkeleton(index: Int = 0, circular: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Immersive.Gutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImmersiveShimmerBox(Modifier.size(52.dp), if (circular) CircleShape else Immersive.CoverSmall)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}

/** A run of track-row placeholders, for search hits and release track lists. */
fun LazyListScope.immersiveSongListSkeleton(
    count: Int = 8,
    keyPrefix: String = "skeleton:song",
    circular: Boolean = false,
    alpha: Float = 1f,
) {
    items(count, key = { "$keyPrefix:$it" }, contentType = { "skeleton:song" }) { index ->
        Box(Modifier.graphicsLayer { this.alpha = alpha }) {
            ImmersiveSongRowSkeleton(index = index, circular = circular)
        }
    }
}

/**
 * The lead shelf on Home: near-page-width cards that page sideways. Built on a [LazyRow] with
 * scrolling off rather than a plain Row, so a card running past the right edge is measured and
 * clipped exactly as the real shelf's is — which is the whole point of a skeleton.
 */
@Composable
fun ImmersiveHeroShelfSkeleton() {
    Column(Modifier.padding(bottom = 26.dp)) {
        ImmersiveSectionHeaderSkeleton()
        BoxWithConstraints {
            val cardWidth = immersiveHeroCardWidth(maxWidth)
            LazyRow(
                contentPadding = PaddingValues(horizontal = Immersive.Gutter),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false,
            ) {
                items(2, key = { it }, contentType = { "skeleton:hero-card" }) {
                    ImmersiveShimmerBox(
                        modifier = Modifier.width(cardWidth).aspectRatio(ImmersiveHeroCardRatio),
                        shape = Immersive.Card,
                    )
                }
            }
        }
    }
}

/** The compact carousel of square cards used by every shelf below the first. */
@Composable
fun ImmersiveShelfSkeleton(
    index: Int = 0,
    cardWidth: Dp = ImmersiveShelfCardWidth,
    cardShape: Shape = Immersive.CoverMedium,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        ImmersiveSectionHeaderSkeleton(index = index)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Immersive.Gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(3, key = { it }, contentType = { "skeleton:shelf-card" }) { card ->
                Column(Modifier.width(cardWidth)) {
                    ImmersiveShimmerBox(
                        modifier = Modifier.width(cardWidth).aspectRatio(1f),
                        shape = cardShape,
                    )
                    Spacer(Modifier.height(12.dp))
                    SkeletonLine(fraction = TitleWidths[card % TitleWidths.size], height = 13.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(fraction = SubtitleWidths[card % SubtitleWidths.size], height = 11.dp)
                }
            }
        }
    }
}

/** Home while the first page of shelves is still loading; it fades out as the shelves fade in. */
fun LazyListScope.immersiveFeedSkeleton(shelves: Int = 3, firstIsHero: Boolean = true) {
    if (firstIsHero) {
        item(key = "skeleton:hero", contentType = "skeleton:hero") { Box(leavingPlaceholder()) { ImmersiveHeroShelfSkeleton() } }
        items(shelves - 1, key = { "skeleton:shelf:$it" }, contentType = { "skeleton:shelf" }) { index ->
            Box(leavingPlaceholder()) { ImmersiveShelfSkeleton(index = index + 1) }
        }
    } else {
        items(shelves, key = { "skeleton:shelf:$it" }, contentType = { "skeleton:shelf" }) { index ->
            Box(leavingPlaceholder()) { ImmersiveShelfSkeleton(index = index) }
        }
    }
}

/** The signed-in library: saved collections, then a run of rows. */
fun LazyListScope.immersiveLibrarySkeleton() {
    item(key = "skeleton:library:shelf", contentType = "skeleton:shelf") { ImmersiveShelfSkeleton() }
    item(key = "skeleton:library:header", contentType = "skeleton:header") { ImmersiveSectionHeaderSkeleton(index = 1) }
    immersiveSongListSkeleton(count = 7, keyPrefix = "skeleton:library:song")
}

/** The Play / Shuffle pair, which only appears once there is something to play. */
@Composable
fun ImmersivePlayShuffleSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Matches the real pair's inset, height and corner, so the header above them does not
            // shift when the track list lands.
            .padding(horizontal = Immersive.Gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ImmersiveShimmerBox(Modifier.weight(1f).height(ImmersiveActionButtonHeight), Immersive.Button)
        ImmersiveShimmerBox(Modifier.weight(1f).height(ImmersiveActionButtonHeight), Immersive.Button)
    }
}

/**
 * An album, playlist or artist page below its header — the header itself is drawn from what the
 * row that was tapped already knew, so it never waits.
 */
fun LazyListScope.immersiveDetailSkeleton(isArtist: Boolean) {
    item(key = "skeleton:detail:actions", contentType = "skeleton:actions") {
        Column(Modifier.graphicsLayer { alpha = 0.4f }) {
            ImmersivePlayShuffleSkeleton()
            Spacer(Modifier.height(if (isArtist) 22.dp else 20.dp))
        }
    }
    if (isArtist) {
        item(key = "skeleton:detail:top", contentType = "skeleton:top") {
            Column(Modifier.graphicsLayer { alpha = 0.4f }) {
                ImmersiveSectionHeaderSkeleton()
                repeat(4) { index -> ImmersiveSongRowSkeleton(index) }
            }
        }
        item(key = "skeleton:detail:sections", contentType = "skeleton:shelf") {
            Box(Modifier.graphicsLayer { alpha = 0.4f }) {
                ImmersiveShelfSkeleton(index = 2)
            }
        }
    } else {
        immersiveSongListSkeleton(count = 8, keyPrefix = "skeleton:detail:song", alpha = 0.4f)
    }
}
