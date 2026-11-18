package com.music.spotui.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.di.COVER_DECODE_PX
import com.music.spotui.ui.look.artworkMeshColors
import com.music.spotui.ui.look.isSpotifusionLook
import com.music.spotui.ui.look.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a glow is drawn from: the cover's own colours and a tiny copy of the cover. */
@Immutable
class CoverGlowData(val colors: List<Color>, val copy: ImageBitmap)

/** The side of the copy the glow blurs (decoded once, at most 96 px). */
private const val GLOW_COPY_PX = 96

private const val GLOW_FADE_MS = 350

/** How far the blurred copy spreads (API 31+); the halo reads as ~40-60 dp beyond the cover. */
private val GLOW_BLUR = 48.dp

/** The halo of the gradient fallback beyond the cover's edges (below API 31). */
private val GLOW_REACH = 56.dp

/** Spotifusion keeps its flat look: a subtle glow; the other Looks full. */
@Composable
fun coverGlowStrength(): Float =
    if (isSpotifusionLook() || LookPref.look == Look.SPOTIFUSION) 0.4f else 1f

/**
 * The cover's glow data, null until its colours are ready. The last cover's glow stays until the
 * next one is known, so a change crossfades from one to the other.
 */
@Composable
fun rememberCoverGlow(url: String?): CoverGlowData? {
    val context = LocalContext.current
    var data by remember { mutableStateOf<CoverGlowData?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    DisposableEffect(url) {
        var job: kotlinx.coroutines.Job? = null
        val target = if (url.isNullOrBlank()) null else Glide.with(context.applicationContext)
            .asBitmap()
            .load(url)
            .override(COVER_DECODE_PX, COVER_DECODE_PX) // the player's decode: Glide reuses it
            .disallowHardwareConfig()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Glide may recycle its bitmap once cleared: a copy of our own, off the main thread.
                    val small = Bitmap.createScaledBitmap(resource, GLOW_COPY_PX, GLOW_COPY_PX, true)
                    job = scope.launch(Dispatchers.Default) {
                        val colors = artworkMeshColors(small).take(3)
                        withContext(Dispatchers.Main) { data = CoverGlowData(colors, small.asImageBitmap()) }
                    }
                }
                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
        if (url.isNullOrBlank()) data = null
        onDispose { job?.cancel(); target?.let { Glide.with(context.applicationContext).clear(it) } }
    }
    return data
}

/**
 * A soft halo of the cover's colours behind it. Put it in a box the size of the cover, before the
 * cover itself (and outside any clip or shadow modifier of the cover), as
 * `CoverGlow(url, Modifier.matchParentSize())`. It draws beyond its bounds, fades in once the
 * colours are ready (at once with reduce motion) and crossfades when the cover changes.
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
            if (Build.VERSION.SDK_INT >= 31) BlurredCopy(g, Modifier.fillMaxSize().alpha(0.9f * strength))
            else GradientHalo(g.colors, Modifier.fillMaxSize(), strength)
        } else Box(Modifier.fillMaxSize())
    }
}

/** API 31+: the 96 px copy of the cover, blurred; the blur is unbounded so the halo is not cut at the cover. */
@Composable
private fun BlurredCopy(glow: CoverGlowData, modifier: Modifier) {
    Image(
        bitmap = glow.copy,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.blur(GLOW_BLUR, BlurredEdgeTreatment.Unbounded),
    )
}

/** Below API 31: layered radial gradients of the palette, each fading to nothing at its own edge. */
@Composable
private fun GradientHalo(colors: List<Color>, modifier: Modifier, strength: Float) {
    Box(
        modifier.drawWithCache {
            val reach = GLOW_REACH.toPx()
            val w = size.width
            val h = size.height
            val radius = maxOf(w, h) / 2f + reach
            // Brushes are built once per size / palette, not per frame.
            val blobs = colors.mapIndexed { i, c ->
                val center = when (i) {
                    0 -> Offset(w * 0.5f, h * 0.5f)
                    1 -> Offset(w * 0.15f, h * 0.85f)
                    else -> Offset(w * 0.85f, h * 0.15f)
                }
                val tint = c.copy(alpha = 0.55f * strength)
                val r = if (i == 0) radius else radius * 0.8f
                Triple(center, r, Brush.radialGradient(
                    0f to tint,
                    0.55f to tint.copy(alpha = tint.alpha * 0.45f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ))
            }
            onDrawBehind { blobs.forEach { (center, r, brush) -> drawCircle(brush, r, center) } }
        },
    )
}
