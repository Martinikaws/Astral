package com.music.spotui.ui.immersive.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
internal val CreditsScrimBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)))

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
 * A blurred copy of the cover drawn over the mesh (at 0.6 alpha), so the fade lands on the cover's
 * own colours. Blurred with a render effect on API 31+; before that a tiny decode drawn large and
 * faint. A track change keeps the previous cover underneath while the new one fades in, so the
 * backdrop never drops to the bare mesh.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun PlayerCoverBlurBackdrop(coverUrl: String, modifier: Modifier = Modifier) {
    val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // The url shown before this one: recomputed only when the url changes, i.e. on a track change.
    val holder = remember { arrayOf(coverUrl) }
    val previous = remember(coverUrl) { holder[0].also { holder[0] = coverUrl } }
    val decode = if (blurred) 160 else 24
    // The new cover dissolves in over the old one, as SongBlendCover does for the cover itself.
    val fadeIn = remember { Animatable(1f) }
    LaunchedEffect(coverUrl) {
        if (previous != coverUrl) {
            fadeIn.snapTo(0f)
            fadeIn.animateTo(1f, tween(400))
        }
    }
    Box(
        modifier.fillMaxSize().graphicsLayer { alpha = if (blurred) 0.6f else 0.35f }
            .let { if (blurred) it.blur(60.dp) else it },
    ) {
        if (previous != coverUrl) {
            GlideImage(
                model = previous.takeIf { it.isNotBlank() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                requestBuilderTransform = { it.override(decode) },
            )
        }
        GlideImage(
            model = coverUrl.takeIf { it.isNotBlank() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fadeIn.value },
            requestBuilderTransform = { it.override(decode) },
        )
    }
}

private val FrostedStripBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f)),
)
private val FrostedStripShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** A frosted strip: a faint white wash (no live backdrop blur is available on a Compose layer). */
internal fun Modifier.frostedStrip(): Modifier = this.background(FrostedStripBrush, FrostedStripShape)
