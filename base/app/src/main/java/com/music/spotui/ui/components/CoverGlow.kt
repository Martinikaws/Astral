package com.music.spotui.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.music.spotui.di.COVER_DECODE_PX
import com.music.spotui.ui.look.isSpotifusionLook
import com.music.spotui.ui.look.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a glow is drawn from: the cover's own colours and a pre-blurred padded copy of the cover. */
@Immutable
class CoverGlowData(val colors: List<Color>, val copy: ImageBitmap)

/** The side of the cover inside the padded glow bitmap (total size is `96 + 2 * 32 = 160` px). */
private const val GLOW_COPY_PX = 96
private const val GLOW_PAD_PX = 32
private const val GLOW_BLUR_RADIUS = 14

private const val GLOW_FADE_MS = 350

/** How far the pre-blurred halo extends beyond each side of the cover box. */
private val GLOW_HALO_SPREAD = 48.dp

/** Spotifusion keeps its flat look: a subtle glow; the other Looks full. */
@Composable
fun coverGlowStrength(): Float =
    if (isSpotifusionLook()) 0.4f else 1f

/**
 * The cover's glow data, null until its colours and pre-blurred halo bitmap are ready. The last
 * cover's glow stays until the next one is known, so a change crossfades from one to the other.
 */
@Composable
fun rememberCoverGlow(url: String?): CoverGlowData? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<CoverGlowData?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(url) {
        var job: Job? = null
        val target = if (url.isNullOrBlank()) null else Glide.with(context.applicationContext)
            .asBitmap()
            .load(url)
            // Shares the COVER_DECODE_PX software cache entry with Palette/MeshGradient;
            // disallowHardwareConfig() allocates software bitmaps rather than reusing hardware decodes.
            .override(COVER_DECODE_PX, COVER_DECODE_PX)
            .disallowHardwareConfig()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Glide owns and may recycle `resource` on clear; immediately copy into a newly
                    // owned 160x160 padded ARGB_8888 bitmap before switching to Dispatchers.Default.
                    val owned = createOwnedScaledCopy(resource, GLOW_COPY_PX, GLOW_COPY_PX, padPx = GLOW_PAD_PX)
                    job?.cancel()
                    job = scope.launch(Dispatchers.Default) {
                        val blurred = rgbaBoxBlur3Pass(owned, GLOW_BLUR_RADIUS)
                        owned.recycle()
                        val copy = blurred.asImageBitmap()
                        withContext(Dispatchers.Main) {
                            if (isActive) data = CoverGlowData(emptyList(), copy)
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    job?.cancel()
                    data = null
                }
            })
        if (url.isNullOrBlank()) data = null
        onDispose { job?.cancel(); target?.let { Glide.with(context.applicationContext).clear(it) } }
    }
    return data
}

/**
 * A soft halo of the cover's colours behind it. Put it in a box the size of the cover, before the
 * cover itself (and outside any clip or shadow modifier of the cover), as
 * `CoverGlow(url, Modifier.matchParentSize())`. It draws beyond its bounds, fades in once ready
 * (at once with reduce motion) and crossfades when the cover changes.
 */
@Composable
fun CoverGlow(url: String?, modifier: Modifier = Modifier, strength: Float = coverGlowStrength()) {
    val glow = rememberCoverGlow(url)
    val reduce = rememberReduceMotion()
    Crossfade(
        targetState = glow,
        modifier = modifier,
        animationSpec = if (reduce) snap() else tween(GLOW_FADE_MS, easing = LinearOutSlowInEasing),
        label = "coverGlow",
    ) { g ->
        if (g != null) {
            BlurredCopy(g, Modifier.fillMaxSize().alpha(0.9f * strength))
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

/** Plain stretched pre-blurred bitmap expanded by [GLOW_HALO_SPREAD] on every side (no RenderEffect). */
@Composable
private fun BlurredCopy(glow: CoverGlowData, modifier: Modifier) {
    Image(
        bitmap = glow.copy,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.layout { measurable, constraints ->
            val spread = GLOW_HALO_SPREAD.roundToPx()
            val baseW = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val baseH = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
            val placeable = measurable.measure(
                Constraints.fixed(
                    (baseW + spread * 2).coerceAtLeast(0),
                    (baseH + spread * 2).coerceAtLeast(0),
                ),
            )
            val layoutW = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
            val layoutH = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
            layout(layoutW, layoutH) {
                placeable.place(-spread, -spread)
            }
        },
    )
}
