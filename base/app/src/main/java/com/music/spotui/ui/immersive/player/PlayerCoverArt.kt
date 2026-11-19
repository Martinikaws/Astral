package com.music.spotui.ui.immersive.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.data.preferences.Look
import com.music.spotui.ui.components.SongBlend
import com.music.spotui.ui.components.SongBlendCover
import com.music.spotui.ui.components.rememberSoftBlurredBitmap

/** Narrower than this the player uses the full-bleed cover; wider keeps the centred card. */
internal val FULL_BLEED_MAX_WIDTH = 600.dp

/** How one Look dresses the full-bleed cover. */
internal data class CoverStyle(
    /** Where, as a fraction of the cover's height, the fade to the background begins. */
    val fadeStart: Float,
    /** A frosted glass strip behind the controls. */
    val glassStrip: Boolean,
    /** A pure black background under the fade instead of the mesh. */
    val blackBackdrop: Boolean,
)

/** The full-bleed treatment for [look]; null when the Look keeps the flat card (Spotifusion). */
internal fun coverStyleFor(look: Look): CoverStyle? = when (look) {
    Look.SPOTIFUSION -> null
    Look.GLASSIFY -> CoverStyle(fadeStart = 0.40f, glassStrip = true, blackBackdrop = false)
    Look.LIQUID_MUSIC -> CoverStyle(fadeStart = 0.55f, glassStrip = false, blackBackdrop = true)
    else -> CoverStyle(fadeStart = 0.55f, glassStrip = false, blackBackdrop = false)
}

/** Height of the full-bleed cover: a bit taller than square, but never so tall the controls lose room. */
internal fun fullBleedCoverHeight(width: Dp, windowHeight: Dp): Dp =
    minOf(width * 1.2f, windowHeight * 0.58f).coerceAtLeast(minOf(width, windowHeight * 0.4f))

/**
 * Fades the content out vertically: opaque down to [start] of its height, transparent at the
 * bottom. An offscreen layer plus a DstIn gradient, so it works on a video view too. The brush
 * is built once in the draw cache, not per frame (a Canvas redraws on every video frame).
 */
internal fun Modifier.coverFadeMask(start: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val brush = Brush.verticalGradient(
            0f to Color.Black,
            start to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush, blendMode = BlendMode.DstIn)
        }
    }

/** Keeps the status bar and the handle readable over a bright cover. */
private val TopScrimBrush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent))

/** Under the credits, over the half-faded foot of the cover (short phones). */
internal val CreditsScrimBrush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.38f),
    1f to Color.Transparent,
)

/** The cover itself, full width and [height] tall, with a light scrim on top and the fade at its foot. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun PlayerFullBleedCover(
    coverUrl: String,
    blend: SongBlend?,
    style: CoverStyle,
    height: Dp,
    topScrimHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(height).coverFadeMask(style.fadeStart)) {
        GlideImage(
            model = coverUrl.takeIf { it.isNotBlank() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.fillMaxSize(),
            requestBuilderTransform = { it.override(640) },
        )
        SongBlendCover(blend, Modifier.fillMaxSize()) { it.override(640) }
        // The status bar plus the handle.
        Box(Modifier.fillMaxWidth().height(topScrimHeight).align(Alignment.TopCenter).background(TopScrimBrush))
    }
}

/**
 * A pre-blurred copy of the cover drawn over the mesh (at 0.6 alpha), so the fade lands on the
 * cover's own colours at every API level with zero RenderEffect. A track change keeps the
 * previous blurred cover underneath while the new one fades in.
 */
@Composable
internal fun PlayerCoverBlurBackdrop(coverUrl: String, modifier: Modifier = Modifier) {
    val loaded by rememberSoftBlurredBitmap(
        url = coverUrl.takeIf { it.isNotBlank() },
        sizePx = 96,
        blurRadius = 12,
    )
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    var current by remember { mutableStateOf<ImageBitmap?>(null) }

    if (loaded == null && coverUrl.isBlank()) {
        previous = null
        current = null
    } else if (loaded != null && loaded !== current) {
        previous = current
        current = loaded
    }

    val cur = current
    val fadeIn = remember(cur) { Animatable(if (previous != null) 0f else 1f) }
    LaunchedEffect(cur) {
        if (cur != null && previous != null && previous !== cur) {
            fadeIn.animateTo(1f, tween(400))
            previous = null
        }
    }

    val prev = previous
    if (cur == null && prev == null) return

    val showPrev by remember(prev, fadeIn) { derivedStateOf { prev != null && fadeIn.value < 0.999f } }
    val showCurr by remember(cur, prev, fadeIn) { derivedStateOf { cur != null && (prev == null || fadeIn.value > 0.001f) } }

    Box(modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }) {
        if (showPrev && prev != null) {
            Image(
                bitmap = prev,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showCurr && cur != null) {
            Image(
                bitmap = cur,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fadeIn.value },
            )
        }
    }
}

private val FrostedStripBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f)),
)
private val FrostedStripShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** A frosted strip: a faint white wash (no live backdrop blur is available on a Compose layer). */
internal fun Modifier.frostedStrip(): Modifier = this.background(FrostedStripBrush, FrostedStripShape)
