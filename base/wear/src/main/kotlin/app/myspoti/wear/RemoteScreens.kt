@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package app.myspoti.wear

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.VerticalPagerScaffold
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private val PlayerBase = Color.Black
private val CoverScrim = Brush.verticalGradient(
    0f to Color(0x80000000),
    0.55f to Color(0xCC000000),
    1f to Color(0xE8000000),
)
private val Secondary = Color(0xFFB3B3B3)
private val LikedPurple = Color(0xFF4B2BD6)
private const val CROWN_PX_PER_STEP = 48f
private const val VOLUME_DEBOUNCE_MS = 120L

/**
 * Root Watch remote UI: horizontal pager between Now Playing (3 vertical pages) and the
 * phone's library (`RemoteLibrary`). Both pagers pass `rotaryScrollableBehavior = null` so the
 * active page keeps rotary focus for bezel/crown volume or `TransformingLazyColumn` scrolling.
 */
@Composable
internal fun RemoteApp() {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        RemoteLink.start(context)
        onStopOrDispose { RemoteLink.stop() }
    }
    val state by RemoteLink.state.collectAsStateWithLifecycle()
    val viz by RemoteLink.viz.collectAsStateWithLifecycle()
    val cover by RemoteLink.cover.collectAsStateWithLifecycle()
    val coverImage = remember(cover) { cover?.asImageBitmap() }
    val connected by RemoteLink.connected.collectAsStateWithLifecycle()
    val queue by RemoteLink.queue.collectAsStateWithLifecycle()
    val devices by RemoteLink.devices.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 2 }
    var picking by remember { mutableStateOf(false) }
    val ui = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage != 0 && picking) picking = false
    }

    AppScaffold {
        HorizontalPagerScaffold(pagerState = pager) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !picking,
                rotaryScrollableBehavior = null,
            ) { page ->
                if (page == 0) {
                    NowPlayingPages(
                        state = state,
                        viz = viz,
                        cover = coverImage,
                        connected = connected,
                        queue = queue,
                        devices = devices,
                        picking = picking,
                        onPickingChange = { picking = it },
                        onOpenLibrary = { ui.launch { pager.animateScrollToPage(1) } },
                    )
                } else {
                    RemoteLibrary(onPlayed = { ui.launch { pager.animateScrollToPage(0) } })
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPages(
    state: RemoteState,
    viz: VizSnapshot = VizSnapshot(),
    cover: ImageBitmap? = null,
    connected: Boolean?,
    queue: RemoteQueue,
    devices: RemoteDevices?,
    picking: Boolean,
    onPickingChange: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val vertical = rememberPagerState(initialPage = 1) { 3 }
    val ui = rememberCoroutineScope()
    BackHandler(enabled = picking) { onPickingChange(false) }

    if (picking) {
        DevicePicker(
            accent = Color(state.accentColor),
            devices = devices,
            onDone = { onPickingChange(false) },
        )
        return
    }

    VerticalPagerScaffold(pagerState = vertical) {
        VerticalPager(state = vertical, rotaryScrollableBehavior = null) { page ->
            when (page) {
                0 -> VisualizerScreen(state = state, viz = viz, cover = cover)
                1 -> RemotePlayer(
                    state = state,
                    connected = connected,
                    onDevices = { onPickingChange(true) },
                    cover = cover,
                    onOpenLibrary = onOpenLibrary,
                )
                else -> UpNext(
                    state = state,
                    queue = queue,
                    onDevices = { onPickingChange(true) },
                    onPicked = { ui.launch { vertical.animateScrollToPage(1) } },
                )
            }
        }
    }
}

@Composable
private fun RotaryVolumeScreen(
    state: RemoteState,
    cover: ImageBitmap? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val volume = rememberRemoteVolume(state.volume, state.maxVolume)
    ScreenScaffold(
        scrollIndicator = {
            VolumeLevelIndicator(
                volumeUiState = { VolumeUiState(volume.percent, 100) },
                displayIndicatorEvents = volume.shown,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PlayerBase)
                .onRotaryScrollEvent { e ->
                    val px = if (e.verticalScrollPixels != 0f) e.verticalScrollPixels else e.horizontalScrollPixels
                    volume.rotate(px)
                    true
                }
                .requestFocusOnHierarchyActive()
                .focusable(),
            contentAlignment = contentAlignment,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CoverScrim),
                )
            }
            content()
        }
    }
}

/**
 * Primary Spotify-inspired remote player page: blurred album-art backdrop, active Connect
 * device pill or context title, marquee title, explicit badge + artist, previous/skip-back-15,
 * progress-ring Play/Pause, next/skip-forward-15, and Like / Shuffle / Devices actions.
 */
@Composable
internal fun RemotePlayer(
    state: RemoteState,
    connected: Boolean?,
    onDevices: () -> Unit,
    cover: ImageBitmap? = null,
    onOpenLibrary: (() -> Unit)? = null,
) {
    val accent = Color(state.accentColor)
    RotaryVolumeScreen(state = state, cover = cover) {
        val ready = connected != false && state.hasSession
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            if (!ready) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(if (connected == false) R.string.wear_no_phone else R.string.wear_nothing_on_phone),
                    color = Secondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(0.84f),
                )
                if (connected != false && onOpenLibrary != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenLibrary,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = { Icon(WatchIcons.Library, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    ) {
                        Text(stringResource(R.string.wear_library), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            TopInfoLine(state = state, accent = accent, onDevices = onDevices)
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.86f).basicMarquee(iterations = 2),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(0.86f),
            ) {
                if (state.explicit) {
                    Icon(
                        imageVector = WatchIcons.Explicit,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = state.artist,
                    color = Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.episode) {
                    GlyphButton(
                        vector = WatchIcons.SkipBack15,
                        label = stringResource(R.string.wear_back_15),
                        iconSize = 24.dp,
                        touchSize = 40.dp,
                    ) { RemoteLink.send("seek_back") }
                } else {
                    GlyphButton(
                        drawableRes = R.drawable.remote_ic_previous,
                        label = stringResource(R.string.wear_previous),
                        iconSize = 26.dp,
                        touchSize = 40.dp,
                    ) { RemoteLink.send("previous") }
                }
                Spacer(Modifier.width(8.dp))
                PlayButton(state = state, accent = accent)
                Spacer(Modifier.width(8.dp))
                if (state.episode) {
                    GlyphButton(
                        vector = WatchIcons.SkipForward15,
                        label = stringResource(R.string.wear_forward_15),
                        iconSize = 24.dp,
                        touchSize = 40.dp,
                    ) { RemoteLink.send("seek_forward") }
                } else {
                    GlyphButton(
                        drawableRes = R.drawable.remote_ic_next,
                        label = stringResource(R.string.wear_next),
                        iconSize = 26.dp,
                        touchSize = 40.dp,
                    ) { RemoteLink.send("next") }
                }
            }

            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(
                    vector = if (state.liked) WatchIcons.HeartFilled else WatchIcons.HeartOutline,
                    label = stringResource(if (state.liked) R.string.wear_unlike else R.string.wear_like),
                    iconSize = 20.dp,
                    touchSize = 34.dp,
                    tint = if (state.liked) accent else Color.White,
                ) { RemoteLink.send("like") }
                GlyphButton(
                    vector = WatchIcons.Shuffle,
                    label = stringResource(R.string.wear_shuffle),
                    iconSize = 20.dp,
                    touchSize = 34.dp,
                    tint = when {
                        state.episode -> Secondary.copy(alpha = 0.35f)
                        state.shuffle -> accent
                        else -> Color.White
                    },
                    dot = state.shuffle && !state.episode,
                    enabled = !state.episode,
                ) { RemoteLink.send("shuffle") }
                GlyphButton(
                    vector = deviceIcon(state.deviceType),
                    label = stringResource(R.string.wear_devices),
                    iconSize = 20.dp,
                    touchSize = 34.dp,
                    tint = if (state.deviceName.isNotBlank()) accent else Color.White,
                    onClick = onDevices,
                )
            }
        }
    }
}

@Composable
private fun TopInfoLine(state: RemoteState, accent: Color, onDevices: () -> Unit) {
    when {
        state.deviceName.isNotBlank() -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDevices)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Icon(
                    imageVector = deviceIcon(state.deviceType),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.deviceName,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.contextTitle.isNotBlank() || state.album.isNotBlank() -> {
            Text(
                text = state.contextTitle.ifBlank { state.album },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
        else -> Spacer(Modifier.height(14.dp))
    }
}

/** White Play/Pause button inside a 60dp progress ring ticking at 1 Hz only while resumed & playing. */
@Composable
private fun PlayButton(state: RemoteState, accent: Color) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val nowState = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(state.playing, state.buffering, state.atElapsedMs, state.positionMs, lifecycleOwner) {
        nowState.longValue = SystemClock.elapsedRealtime()
        if (!state.playing || state.buffering) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            nowState.longValue = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000)
                nowState.longValue = SystemClock.elapsedRealtime()
            }
        }
    }
    val bufferRotation = if (state.buffering) {
        rememberInfiniteTransition(label = "buffering").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
            label = "sweep",
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(60.dp)
            .drawBehind {
                // Read nowState inside drawBehind so 1 Hz progress ticks never recompose PlayButton.
                val s = currentState
                val now = nowState.longValue
                val elapsed = if (s.playing && !s.buffering) {
                    ((now - s.atElapsedMs).coerceAtLeast(0L) * s.speed).toLong()
                } else {
                    0L
                }
                val position = (s.positionMs + elapsed).coerceAtLeast(0L)
                val progress = if (s.durationMs > 0L) (position.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(Color.White.copy(alpha = 0.20f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                when {
                    bufferRotation != null -> drawArc(
                        color = accent,
                        startAngle = bufferRotation.value - 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    progress > 0f -> drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { RemoteLink.send("toggle") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (state.playing) R.drawable.remote_ic_pause else R.drawable.remote_ic_play),
                contentDescription = stringResource(if (state.playing) R.string.wear_pause else R.string.wear_play),
                tint = Color.Black,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun GlyphButton(
    label: String,
    iconSize: Dp,
    touchSize: Dp,
    tint: Color = Color.White,
    dot: Boolean = false,
    enabled: Boolean = true,
    background: Color = Color.Transparent,
    vector: ImageVector? = null,
    drawableRes: Int? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            vector != null -> Icon(vector, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
            drawableRes != null -> Icon(painterResource(drawableRes), contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
        }
        if (dot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

private class RemoteVolume(
    private val percentState: State<Int>,
    val shown: MutableSharedFlow<Unit>,
    val rotate: (Float) -> Unit,
) {
    val percent: Int get() = percentState.value
}

@Composable
private fun rememberRemoteVolume(volume: Int, maxVolume: Int): RemoteVolume {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bezel = remember { context.packageManager.hasSystemFeature("android.hardware.rotaryencoder.lowres") }
    val max = maxVolume.coerceAtLeast(1)
    val initialPercent = ((volume * 100 + max / 2) / max).coerceIn(0, 100)
    val percentState = remember { mutableIntStateOf(initialPercent) }
    val localHoldUntil = remember { mutableLongStateOf(0L) }
    var pixels by remember { mutableFloatStateOf(0f) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    val shown = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    LaunchedEffect(initialPercent) {
        if (SystemClock.elapsedRealtime() >= localHoldUntil.longValue) {
            percentState.intValue = initialPercent
        }
    }

    fun step(direction: Int) {
        val current = percentState.intValue
        val target = (((current + 5) / 10) * 10 + 10 * direction).coerceIn(0, 100)
        if (target == current) return
        percentState.intValue = target
        localHoldUntil.longValue = SystemClock.elapsedRealtime() + 1_500L
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        shown.tryEmit(Unit)
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(VOLUME_DEBOUNCE_MS)
            RemoteLink.send("volume", target.toString())
        }
    }

    return remember(bezel, view) {
        RemoteVolume(percentState, shown) { px ->
            if (px == 0f) return@RemoteVolume
            if (bezel) {
                step(if (px > 0f) 1 else -1)
                return@RemoteVolume
            }
            if ((pixels > 0f && px < 0f) || (pixels < 0f && px > 0f)) pixels = 0f
            pixels += px
            if (abs(pixels) >= CROWN_PX_PER_STEP) {
                step(if (pixels > 0f) 1 else -1)
                pixels = 0f
            }
        }
    }
}

/** Swipe-down NCS visualizer page: blurred cover backdrop, audio-reactive particle sphere, rotary volume. */
@Composable
internal fun VisualizerScreen(
    state: RemoteState,
    viz: VizSnapshot = VizSnapshot(),
    cover: ImageBitmap? = null,
) {
    RotaryVolumeScreen(state = state, cover = cover) {
        val density = LocalDensity.current
        val compactThresholdPx = remember(density) { with(density) { 210.dp.roundToPx() } }
        val initialCompact = LocalConfiguration.current.screenWidthDp < 210
        var compact by remember { mutableStateOf(initialCompact) }
        val sphereTop = if (compact) 6.dp else 8.dp
        val sphereSize = if (compact) 120.dp else 132.dp
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    if (sz.width > 0) compact = sz.width < compactThresholdPx
                },
        ) {
            if (state.hasSession) {
                NcsVisualizer(
                    state = state,
                    viz = viz,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = sphereTop)
                        .size(sphereSize)
                        .clip(CircleShape)
                        .clickable { RemoteLink.send("toggle") },
                )
            } else {
                Text(
                    text = stringResource(R.string.wear_nothing_on_phone),
                    color = Secondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.75f),
                )
            }
            if (state.title.isNotBlank() || state.artist.isNotBlank()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp)
                        .fillMaxWidth(0.56f),
                ) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee(iterations = 2),
                    )
                    Text(
                        text = state.artist,
                        color = Secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Swipe-up queue & secondary controls page (Like, Shuffle, Repeat, Devices + Up Next list). */
@Composable
private fun UpNext(
    state: RemoteState,
    queue: RemoteQueue,
    onDevices: () -> Unit,
    onPicked: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val accent = Color(state.accentColor)
    val activeIndex = queue.items.indexOfFirst { it.id == queue.active.toString() }
    LaunchedEffect(activeIndex) {
        if (activeIndex > 0) listState.scrollToItem(activeIndex + 2)
    }

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "controls", contentType = "controls") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    val chipBg = Color.White.copy(alpha = 0.08f)
                    GlyphButton(
                        vector = if (state.liked) WatchIcons.HeartFilled else WatchIcons.HeartOutline,
                        label = stringResource(if (state.liked) R.string.wear_unlike else R.string.wear_like),
                        iconSize = 18.dp,
                        touchSize = 34.dp,
                        tint = if (state.liked) accent else Color.White,
                        background = chipBg,
                    ) { RemoteLink.send("like") }
                    GlyphButton(
                        vector = WatchIcons.Shuffle,
                        label = stringResource(R.string.wear_shuffle),
                        iconSize = 18.dp,
                        touchSize = 34.dp,
                        tint = when {
                            state.episode -> Secondary.copy(alpha = 0.35f)
                            state.shuffle -> accent
                            else -> Color.White
                        },
                        dot = state.shuffle && !state.episode,
                        enabled = !state.episode,
                        background = chipBg,
                    ) { RemoteLink.send("shuffle") }
                    GlyphButton(
                        vector = if (state.repeat == "one") WatchIcons.RepeatOne else WatchIcons.Repeat,
                        label = stringResource(R.string.wear_repeat),
                        iconSize = 18.dp,
                        touchSize = 34.dp,
                        tint = when {
                            !state.canRepeat -> Secondary.copy(alpha = 0.35f)
                            state.repeat != "off" -> accent
                            else -> Color.White
                        },
                        dot = state.repeat != "off",
                        enabled = state.canRepeat,
                        background = chipBg,
                    ) { RemoteLink.send("repeat") }
                    GlyphButton(
                        vector = deviceIcon(state.deviceType),
                        label = stringResource(R.string.wear_devices),
                        iconSize = 18.dp,
                        touchSize = 34.dp,
                        tint = if (state.deviceName.isNotBlank()) accent else Color.White,
                        background = chipBg,
                        onClick = onDevices,
                    )
                }
            }
            item(key = "header", contentType = "header") {
                ListHeader(Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(if (state.shuffle && !state.episode) R.string.wear_shuffling_from else R.string.wear_up_next),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        val contextName = state.contextTitle.ifBlank { state.album }
                        if (contextName.isNotBlank()) {
                            Text(
                                text = contextName,
                                style = MaterialTheme.typography.bodySmall,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if (queue.items.isEmpty()) {
                item(key = "empty", contentType = "status") {
                    Text(
                        text = stringResource(R.string.wear_queue_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                itemsIndexed(
                    queue.items,
                    key = { index, song -> "${song.id}_$index" },
                    contentType = { _, _ -> "queue_item" },
                ) { _, song ->
                    val playing = song.id == queue.active.toString()
                    Button(
                        onClick = {
                            RemoteLink.send("queue", song.id)
                            onPicked()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = {
                            Icon(
                                imageVector = WatchIcons.MusicNote,
                                contentDescription = null,
                                tint = if (playing) accent else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        secondaryLabel = song.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (playing) accent else Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spotify-inspired phone library browser: top-level hubs matching `AutoBrowser` (`liked`, `home`,
 * `playlists`, `albums`, `artists`, `podcasts`, `downloads`) plus cached folder drill-down.
 */
@Composable
internal fun RemoteLibrary(
    onPlayed: () -> Unit,
    pages: Map<String, RemotePage>? = null,
    initialOpened: List<Pair<String, String>> = emptyList(),
) {
    val livePages by RemoteLink.pages.collectAsStateWithLifecycle()
    val resolvedPages = pages ?: livePages
    var opened by remember { mutableStateOf(initialOpened) }
    val current = opened.lastOrNull()
    val parent = current?.first

    if (pages == null) {
        LaunchedEffect(parent) {
            if (parent != null) RemoteLink.browse(parent)
        }
    }
    BackHandler(enabled = opened.isNotEmpty()) { opened = opened.dropLast(1) }

    val listState = rememberTransformingLazyColumnState()
    LaunchedEffect(parent) { listState.scrollToItem(0) }

    val play: (RemoteItem) -> Unit = {
        RemoteLink.send("play", it.id)
        opened = emptyList()
        onPlayed()
    }
    val open: (RemoteItem) -> Unit = { opened = opened + (it.id to it.title) }
    val rowModifier = Modifier.fillMaxWidth()

    val rootHubs = listOf(
        RemoteItem("liked", stringResource(R.string.wear_liked_songs), browsable = true),
        RemoteItem("home", stringResource(R.string.wear_home), browsable = true),
        RemoteItem("playlists", stringResource(R.string.wear_playlists), browsable = true),
        RemoteItem("albums", stringResource(R.string.wear_albums), browsable = true),
        RemoteItem("artists", stringResource(R.string.wear_artists), browsable = true),
        RemoteItem("podcasts", stringResource(R.string.wear_podcasts), browsable = true),
        RemoteItem("downloads", stringResource(R.string.wear_downloads), browsable = true),
    )

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "header", contentType = "header") {
                ListHeader(rowModifier) {
                    Text(
                        text = current?.second ?: stringResource(R.string.wear_library),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (parent == null) {
                itemsIndexed(
                    rootHubs,
                    key = { _, hub -> hub.id },
                    contentType = { _, _ -> "library_row" },
                ) { _, hub ->
                    LibraryRow(item = hub, onClick = { open(hub) })
                }
            } else {
                val page = resolvedPages[parent]
                when {
                    page == null -> item(key = "loading", contentType = "status") {
                        Text(
                            text = stringResource(R.string.wear_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = rowModifier,
                        )
                    }
                    page.error != null -> {
                        item(key = "error", contentType = "status") {
                            Text(
                                text = page.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = rowModifier,
                            )
                        }
                        item(key = "retry", contentType = "action") {
                            Button(
                                onClick = { RemoteLink.browse(parent, force = true) },
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                modifier = rowModifier,
                            ) { Text(stringResource(R.string.wear_retry)) }
                        }
                    }
                    page.items.isEmpty() -> {
                        item(key = "empty", contentType = "status") {
                            Text(
                                text = stringResource(R.string.wear_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = rowModifier,
                            )
                        }
                        item(key = "refresh", contentType = "action") {
                            Button(
                                onClick = { RemoteLink.browse(parent, force = true) },
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                modifier = rowModifier,
                            ) { Text(stringResource(R.string.wear_refresh)) }
                        }
                    }
                    else -> {
                        val first = page.items.firstOrNull { it.playable && !it.id.startsWith("radio|") }
                            ?: page.items.firstOrNull { it.playable }
                        if (first != null) {
                            item(key = "play", contentType = "play_action") {
                                Button(
                                    onClick = { play(first) },
                                    colors = ButtonDefaults.buttonColors(),
                                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                    modifier = rowModifier,
                                ) { Text(stringResource(R.string.wear_play_list)) }
                            }
                        }
                        itemsIndexed(
                            page.items,
                            key = { index, item -> "${item.id}_$index" },
                            contentType = { _, _ -> "library_row" },
                        ) { _, item ->
                            LibraryRow(
                                item = item,
                                onClick = {
                                    if (item.browsable) open(item) else if (item.playable) play(item)
                                },
                            )
                        }
                        item(key = "refresh", contentType = "action") {
                            Button(
                                onClick = { RemoteLink.browse(parent, force = true) },
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                modifier = rowModifier,
                            ) { Text(stringResource(R.string.wear_refresh)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(item: RemoteItem, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { LibraryItemIcon(item) },
        secondaryLabel = item.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryItemIcon(item: RemoteItem) {
    if (item.id == "liked") {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(LikedPurple),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = WatchIcons.HeartFilled,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        return
    }
    Icon(
        imageVector = libraryVector(item),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(20.dp),
    )
}

private fun libraryVector(item: RemoteItem): ImageVector = when {
    item.id == "home" -> WatchIcons.Home
    item.id == "playlists" || item.id.startsWith("pl:") -> WatchIcons.Playlist
    item.id == "albums" || item.id.startsWith("al:") -> WatchIcons.Album
    item.id == "artists" || item.id.startsWith("ar:") -> WatchIcons.Artist
    item.id == "podcasts" || item.id == "newep" || item.id.startsWith("sh:") -> WatchIcons.Podcasts
    item.id == "downloads" || item.id == "dsongs" || item.id.startsWith("dl:") -> WatchIcons.Downloads
    item.id.startsWith("radio|") -> WatchIcons.Radio
    item.id.startsWith("t|") || item.id.startsWith("d|") -> WatchIcons.MusicNote
    item.browsable -> WatchIcons.QueueMusic
    else -> WatchIcons.MusicNote
}

/** Spotify Connect output picker: this phone and available Connect targets. */
@Composable
private fun DevicePicker(
    accent: Color,
    devices: RemoteDevices?,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) { RemoteLink.askDevices() }
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "header", contentType = "header") {
                ListHeader(Modifier.fillMaxWidth()) { Text(stringResource(R.string.wear_devices)) }
            }
            when {
                devices == null -> item(key = "loading", contentType = "status") {
                    Text(
                        text = stringResource(R.string.wear_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                devices.error != null && devices.items.isEmpty() -> {
                    item(key = "error", contentType = "status") {
                        Text(
                            text = devices.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "retry", contentType = "action") {
                        Button(
                            onClick = { RemoteLink.askDevices() },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.wear_retry)) }
                    }
                }
                else -> itemsIndexed(
                    devices.items,
                    key = { index, d -> "${d.id}_$index" },
                    contentType = { _, _ -> "device" },
                ) { _, d ->
                    Button(
                        onClick = {
                            RemoteLink.send("device", d.id)
                            onDone()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = {
                            Icon(
                                imageVector = deviceIcon(d.type, d.self),
                                contentDescription = null,
                                tint = if (d.active) accent else Color.White,
                            )
                        },
                        secondaryLabel = if (d.self) {
                            { Text(stringResource(R.string.wear_this_phone), maxLines = 1) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = d.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (d.active) accent else Color.Unspecified,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (d.active) {
                                Icon(
                                    imageVector = WatchIcons.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun deviceIcon(type: String, self: Boolean = false): ImageVector = when {
    self -> WatchIcons.Phone
    type.isBlank() -> WatchIcons.Devices
    type.equals("computer", true) -> WatchIcons.Computer
    type.equals("tv", true) || type.equals("castvideo", true) || type.equals("cast_video", true) || type.equals("stb", true) -> WatchIcons.Tv
    type.equals("smartphone", true) || type.equals("tablet", true) -> WatchIcons.Phone
    type.equals("watch", true) || type.equals("wearable", true) -> WatchIcons.Watch
    else -> WatchIcons.Speaker
}
