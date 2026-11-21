/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/Artwork.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: the image loader is Glide (Astral's own) instead of Coil; the downloads / local-files
 * tiles and the offline switch are left out. The generated cover, the Spotify print-size helpers
 * and canonicalArtworkKey are Square's.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.music.spotui.ui.components.spotifyImageUrl
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Square artwork, falling back to a generated cover.
 *
 * The fallback is derived from the title rather than one grey placeholder: a
 * wall of identical squares makes a list unreadable, while a stable per-title
 * colour gives each row something to recognise.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SquaredArtwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    /** Decode size, so a 48dp row does not decode a 640px cover. */
    decodeSize: Dp = 0.dp,
    /** How long a picture takes to come up once it has loaded. */
    crossfadeMs: Int = ARTWORK_APPEAR_MS,
    /** Whether a missing picture is worth drawing a stand-in for. */
    fallback: Boolean = true,
) {
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val density = LocalDensity.current
    val model = remember(url, decodeSize) {
        val resolved = url?.takeIf { it.isNotBlank() }?.let { spotifyImageUrl(it) ?: it }
        if (resolved != null && decodeSize > 0.dp) {
            spotifyResizedUrl(resolved, with(density) { decodeSize.toPx() }.roundToInt())
        } else resolved
    }

    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (model != null) {
            var arrived by remember(model) { mutableStateOf(false) }
            var failed by remember(model) { mutableStateOf(false) }
            val appear by animateFloatAsState(
                targetValue = if (arrived || crossfadeMs <= 0) 1f else 0f,
                animationSpec = if (crossfadeMs <= 0) snap() else tween(crossfadeMs),
                label = "artwork",
            )
            if (fallback && failed) GeneratedCover(title, corner)
            if (fallback && !arrived && !failed) {
                Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.07f)))
            }
            GlideImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (crossfadeMs > 0) Modifier.graphicsLayer { alpha = appear } else Modifier),
            ) { request ->
                request.addListener(
                    object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            failed = true
                            return false
                        }

                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            arrived = true
                            failed = false
                            return false
                        }
                    },
                )
            }
        } else if (fallback) {
            GeneratedCover(title, corner)
        }
    }
}

/**
 * What to hand the image loader for [url]. Square swaps in a downloaded copy while offline; Astral's
 * Glide stack and cache already cover that, so it is the url itself.
 */
fun artSource(url: String): Any = url

/** How long a newly loaded picture takes to come up. */
const val ARTWORK_APPEAR_MS = 220

/**
 * A small decode of [url] for colour work (palette, ambient light), off the main thread. Null on any
 * failure. Replaces Square's Coil `imageLoader.execute` calls.
 */
internal fun squaredSmallBitmap(context: Context, url: String, size: Int = 96): Bitmap? =
    runCatching {
        val model = spotifyImageUrl(url) ?: url
        Glide.with(context.applicationContext).asBitmap().load(model).override(size, size).submit(size, size).get()
    }.getOrNull()

/**
 * Requests a smaller print of a Spotify CDN image when a small decode target
 * is given, rather than fetching 640 px and throwing most of it away.
 * (Spotify image IDs: forty hex characters, the first sixteen encode the resolution.)
 */
fun spotifyResizedUrl(url: String, targetPx: Int): String {
    if (targetPx <= 0) return url
    val id = url.substringAfterLast('/')
    if (id.length != SPOTIFY_ID_LEN || !id.all { it.isDigit() || it in 'a'..'f' }) return url
    val family = id.take(8)
    val rendition = when (family) {
        "ab67616d" -> when {
            targetPx <= 64 -> "00004851"
            targetPx <= 300 -> "00001e02"
            else -> return url
        }
        "ab676161" -> when {
            targetPx <= 160 -> "0000f178"
            targetPx <= 320 -> "00005174"
            else -> return url
        }
        else -> return url
    }
    val tail = id.drop(SPOTIFY_SIZE_PREFIX_LEN)
    return url.dropLast(SPOTIFY_ID_LEN) + family + rendition + tail
}

/**
 * The largest print the CDN publishes of the same picture, for one drawn across the whole screen.
 * Anything unrecognised comes back unchanged.
 */
fun spotifyLargeUrl(url: String): String {
    if (url.startsWith("https://mosaic.scdn.co/")) {
        val rest = url.removePrefix("https://mosaic.scdn.co/")
        val size = rest.substringBefore('/')
        if (size.all(Char::isDigit) && size != "640") return "https://mosaic.scdn.co/640/" + rest.substringAfter('/')
        return url
    }
    val id = url.substringAfterLast('/')
    if (id.length != SPOTIFY_ID_LEN || !id.all { it.isDigit() || it in 'a'..'f' }) return url
    val family = id.take(8)
    val rendition = when (family) {
        "ab67706f" -> "00000004"
        "ab67706c" -> "0000bebb"
        "ab67616d" -> "000082c1"
        "ab676161" -> "0000e5eb"
        else -> return url
    }
    val tail = id.drop(SPOTIFY_SIZE_PREFIX_LEN)
    return url.dropLast(SPOTIFY_ID_LEN) + family + rendition + tail
}

/**
 * A canonical cache key for an image URL: any size variant of the same Spotify picture (row thumbnail,
 * list print, 640 px cover) shares one palette and tint.
 */
fun canonicalArtworkKey(url: String): String {
    val id = url.substringAfterLast('/')
    return if (id.length == SPOTIFY_ID_LEN && id.all { it.isDigit() || it in 'a'..'f' }) {
        id.takeLast(SPOTIFY_ID_LEN - SPOTIFY_SIZE_PREFIX_LEN)
    } else {
        url
    }
}

/** Length of a Spotify image ID, in hex characters. */
const val SPOTIFY_ID_LEN = 40

/** How many of those characters encode the image size. */
const val SPOTIFY_SIZE_PREFIX_LEN = 16

@Composable
private fun GeneratedCover(title: String, corner: Dp) {
    val seed = remember(title) { title.hashCode().absoluteValue }
    val initial = remember(title) { title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪" }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val direction = seed % 4
            val start = when (direction) {
                0 -> Offset(0f, 0f)
                1 -> Offset(size.width, 0f)
                2 -> Offset(0f, size.height)
                else -> Offset(size.width, size.height)
            }
            val end = Offset(size.width - start.x, size.height - start.y)
            val lightness = 0.16f + (seed / 4 % 5) * 0.035f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.hsl(220f, 0.06f, lightness + 0.07f), Color.hsl(230f, 0.08f, lightness)),
                    start = start,
                    end = end,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * (0.25f + (seed / 20 % 4) * 0.17f), size.height * 0.22f),
                    radius = size.minDimension * 0.85f,
                ),
            )
        }
        Text(
            text = initial,
            style = MaterialTheme.typography.displayLarge,
            fontSize = with(LocalDensity.current) { ((corner * 2.6f).coerceIn(16.dp, 96.dp)).toSp() },
            color = Color.White.copy(alpha = 0.20f),
            textAlign = TextAlign.Center,
        )
    }
}
