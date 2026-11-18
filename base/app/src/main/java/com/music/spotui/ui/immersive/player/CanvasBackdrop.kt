package com.music.spotui.ui.immersive.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import app.myspoti.engine.R
import com.music.spotui.data.preferences.CanvasPref
import com.music.spotui.ui.components.CanvasPlayer
import com.music.spotui.ui.immersive.sheets.ImmersiveSheetRow
import com.music.spotui.ui.immersive.sheets.LocalImmersiveSheetTint

/** Dim under the credits and controls over a Canvas: light at the top, clear in the middle, strong at the foot. */
internal val CanvasScrimBrush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.25f),
    0.25f to Color.Transparent,
    0.45f to Color.Transparent,
    0.70f to Color.Black.copy(alpha = 0.65f),
    1f to Color.Black.copy(alpha = 0.65f),
)

/** The extra uniform dim behind the lyrics or the queue, on top of [CanvasScrimBrush]. */
internal const val CANVAS_PANEL_DIM = 0.55f

/** The blur radius at 100 percent, in dp. */
private const val CANVAS_MAX_BLUR_DP = 36f

/**
 * The Canvas clip as the player's background: the shared [CanvasPlayer]'s one ExoPlayer on one
 * texture-view PlayerView, cropped to fill. [blurPercent] blurs the view with a render effect
 * (Android 12+, ignored before); the effect is rebuilt only when the amount changes, never per
 * frame. Plays only while [playing] and the lifecycle is started; the decoder is given back in the
 * background (see [CanvasPlayer.onBackground]). [onFirstFrame]: the clip is visible.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun CanvasBackgroundVideo(
    url: String,
    playing: Boolean,
    blurPercent: Int,
    onFirstFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exo = remember(url) { CanvasPlayer.get(context, url) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val requested by rememberUpdatedState(playing)
    val shown by rememberUpdatedState(onFirstFrame)
    LaunchedEffect(exo, playing, lifecycle) {
        exo.playWhenReady = playing && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    DisposableEffect(exo, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // Back on screen: the clip given back in the background is loaded again.
            if (event == Lifecycle.Event.ON_START) CanvasPlayer.get(context, url)
            exo.playWhenReady = requested && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        val removeFrame = CanvasPlayer.onFirstFrame { shown() }
        onDispose {
            lifecycle.removeObserver(observer)
            removeFrame()
            exo.playWhenReady = false
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // Inflated: the surface type (texture view) can only be set from XML. Zoom resize mode = crop to fill.
            (android.view.LayoutInflater.from(ctx).inflate(R.layout.canvas_player_view, null) as PlayerView).apply {
                setArtworkDisplayMode(PlayerView.ARTWORK_DISPLAY_MODE_OFF)
                setDefaultArtwork(null)
                player = exo
            }
        },
        update = { view ->
            if (view.player !== exo) view.player = exo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyBlur(view, blurPercent)
        },
    )
}

/** Sets (or clears) the blur on [view]; only touches the view when the amount differs from the last one set. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
private fun applyBlur(view: PlayerView, percent: Int) {
    if (view.tag == percent) return
    view.tag = percent
    if (percent <= 0) {
        view.setRenderEffect(null)
    } else {
        val radius = percent / 100f * CANVAS_MAX_BLUR_DP * view.resources.displayMetrics.density
        view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }
}

/** The player menu's Canvas rows: the background switch, and (when on) the blur amount. */
@Composable
internal fun CanvasMenuRows() {
    val context = LocalContext.current
    val on = CanvasPref.background
    ImmersiveSheetRow(
        Icons.Rounded.Videocam, "Canvas background",
        value = if (on) "On" else "Off",
        tint = if (on) LocalImmersiveSheetTint.current.accent else null,
    ) { CanvasPref.setBackground(context, !on) }
    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Dragging stays local and is saved when let go.
        var drag by remember { mutableFloatStateOf(CanvasPref.blur.toFloat()) }
        val accent = LocalImmersiveSheetTint.current.accent
        ImmersiveSheetRow(Icons.Rounded.BlurOn, "Canvas blur", value = "${drag.toInt()}%", onClick = {})
        Column(Modifier.padding(horizontal = 22.dp)) {
            Slider(
                value = drag,
                onValueChange = { drag = it },
                onValueChangeFinished = { CanvasPref.setBlur(context, drag.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            )
        }
    }
}
