package com.music.spotui.ui.immersive.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
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
 * bottom. An offscreen layer plus a DstIn gradient, so it works on a video view too.
 */
internal fun Modifier.coverFadeMask(start: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                start to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** The cover itself, full width and [height] tall, with a light scrim on top and the fade at its foot. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun PlayerFullBleedCover(
    coverUrl: String,
    blend: SongBlend?,
    style: CoverStyle,
    height: Dp,
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
        // Keeps the status bar and the top buttons readable over a bright cover.
        Box(
            Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent)),
            ),
        )
    }
}

/**
 * A copy of the cover under the mesh and the fade, so the fade lands on the cover's own colours.
 * Blurred with a render effect on API 31+; before that a tiny decode drawn large and faint.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun PlayerCoverBlurBackdrop(coverUrl: String, modifier: Modifier = Modifier) {
    val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    GlideImage(
        model = coverUrl.takeIf { it.isNotBlank() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = if (blurred) 0.6f else 0.35f }
            .let { if (blurred) it.blur(60.dp) else it },
        requestBuilderTransform = { it.override(if (blurred) 160 else 24) },
    )
}

/** A frosted strip: a faint white wash (no live backdrop blur is available on a Compose layer). */
internal fun Modifier.frostedStrip(): Modifier = this.background(
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))),
    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
)
