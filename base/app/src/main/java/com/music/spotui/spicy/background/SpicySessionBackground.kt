// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, app/src/main/java/com/tx24/spicyplayer/ui/background/SpicySessionBackground.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.background

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.music.spotui.spicy.canvas.DynamicBackgroundView
import com.music.spotui.spicy.canvas.kawarp.KawarpBackground
import com.music.spotui.spicy.canvas.kawarp.MotionSource
import com.music.spotui.data.preferences.SpicyBackgroundType
import android.graphics.Bitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The lyrics page's background, of the chosen [type], from the cover at [artworkUri].
 *
 * The artist-header types show [artistHeaderUrl], or the cover when the artist has none; while
 * [artistHeaderPending] they keep what they show. [motion] drives the moving background from the
 * song's energy while playing (null: the normal speed). A change of [type] crossfades in place.
 */
@Composable
fun SpicySessionBackground(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    type: SpicyBackgroundType = SpicyBackgroundType.DEFAULT,
    /** False holds the moving backgrounds still (low performance mode, or the setting). */
    animate: Boolean = true,
    /**
     * How blurred: for [SpicyBackgroundType.DEFAULT] a strength in percent (60 is the reference's),
     * for the others in dp. [SpicyBackgroundType.COLOR] and [SpicyBackgroundType.CUSTOM] have nothing to blur.
     */
    blurDp: Int = 0,
    artistHeaderUrl: String? = null,
    artistHeaderPending: Boolean = false,
    motion: MotionSource? = null,
    /** [SpicyBackgroundType.CUSTOM]'s colour. */
    customColor: Color = Color.Black,
) {
    @Composable
    fun background(type: SpicyBackgroundType, modifier: Modifier, retired: Boolean) = BackgroundOfType(
        artworkUri, isPlaying, modifier, type, animate && !retired, blurDp, artistHeaderUrl,
        artistHeaderPending, motion, retired, customColor,
    )
    // A new type fades in over the old one, which holds still beneath it until the new one has
    // settled and is then dropped, its renderer (and any engine in it) with it.
    val layers = remember { mutableStateListOf(TypeLayer(type, shown = true)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(type) {
        if (layers.last().type == type) return@LaunchedEffect
        val beneath = layers.toList()
        beneath.forEach { it.retired = true }
        val layer = TypeLayer(type, shown = false)
        layers += layer
        scope.launch { layer.alpha.animateTo(1f, tween(TYPE_FADE_MS, easing = TypeFadeEasing)) }
        scope.launch {
            delay(LAYER_SETTLE_MS.toLong())
            layers.removeAll(beneath)
        }
    }
    Box(modifier.background(Color.Black)) {
        for (layer in layers) {
            key(layer) {
                background(
                    layer.type,
                    Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha.value },
                    retired = layer.retired,
                )
            }
        }
    }
}

private class TypeLayer(val type: SpicyBackgroundType, shown: Boolean) {
    val alpha = Animatable(if (shown) 1f else 0f)
    var retired by mutableStateOf(false)
}

/** A type change fades in over `900ms cubic-bezier(0.45, 0, 0.25, 1)`, like a refined picture. */
private val TypeFadeEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private const val TYPE_FADE_MS = 900
/** A newly picked custom colour fades in, like a type change. */
private const val CUSTOM_COLOR_MS = TYPE_FADE_MS

/** One type's renderer. [retired] (on its way out) takes nothing new and holds still. */
@Composable
private fun BackgroundOfType(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier,
    type: SpicyBackgroundType,
    animate: Boolean,
    blurDp: Int,
    artistHeaderUrl: String?,
    artistHeaderPending: Boolean,
    motion: MotionSource?,
    retired: Boolean,
    customColor: Color,
) {
    when (type) {
        SpicyBackgroundType.DEFAULT, SpicyBackgroundType.LEGACY -> {
            val softwareArtwork = rememberSessionArtwork(artworkUri, maxDimension = 256)?.bitmap
            if (type == SpicyBackgroundType.LEGACY) {
                LegacyBackground(coverArtBitmap = softwareArtwork, modifier = modifier, animate = animate, blurDp = blurDp)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                KawarpBackground(
                    coverArtBitmap = softwareArtwork,
                    modifier = modifier,
                    isPlaying = isPlaying,
                    animate = animate,
                    blurIntensity = blurDp,
                    motion = motion,
                )
            } else {
                DynamicBackgroundView(
                    coverArtBitmap = softwareArtwork,
                    modifier = modifier,
                    blurIntensity = blurDp,
                    animate = animate,
                )
            }
        }
        SpicyBackgroundType.COLOR -> {
            ColorBackground(rememberSessionArtwork(artworkUri, maxDimension = 256), modifier, refined = true, retired = retired)
        }
        SpicyBackgroundType.COVER_ART -> {
            Box(modifier) {
                StillImageBackground(rememberSessionArtwork(artworkUri, maxDimension = STILL_COVER_PX), blurDp, refined = true, retired = retired)
            }
        }
        SpicyBackgroundType.CUSTOM -> {
            val shown by animateColorAsState(customColor, tween(CUSTOM_COLOR_MS), label = "customColor")
            Box(modifier.background(shown))
        }
        SpicyBackgroundType.AUTO, SpicyBackgroundType.ARTIST_HEADER -> {
            val cover = rememberSessionArtwork(artworkUri, maxDimension = STILL_COVER_PX)
            val context = LocalContext.current
            // Which header URL has loaded, and what it gave (null: it failed, so the cover shows).
            // A change of type, or the background retiring, drops a load under way.
            val header by produceState<Pair<String, SessionArtwork?>?>(null, artistHeaderUrl, type, retired) {
                if (retired) return@produceState
                val url = artistHeaderUrl ?: return@produceState
                value = url to loadSessionArtwork(context, url, STILL_HEADER_PX)
            }
            val loaded = header?.takeIf { it.first == artistHeaderUrl }
            val image = when {
                artistHeaderPending -> null
                artistHeaderUrl != null && loaded == null -> null
                else -> loaded?.second ?: cover
            }
            StillImageBackground(image, blurDp, modifier, refined = true, retired = retired)
        }
    }
}

private const val STILL_COVER_PX = 1024
private const val STILL_HEADER_PX = 1600
