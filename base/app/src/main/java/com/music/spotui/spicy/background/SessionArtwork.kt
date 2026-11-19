// Adapted from Spicy Lyrics Mobile (https://github.com/spicylyrics/mobile, app/src/main/java/com/tx24/spicyplayer/ui/nowplaying/SessionArtwork.kt) by TheX24 and contributors, AGPL-3.0. Modified for Astral.
package com.music.spotui.spicy.background

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.myspoti.host.android.Covers
import com.bumptech.glide.Glide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * A cover as a software bitmap no larger than the size asked for, plus a small fingerprint of
 * its pixels, so a cover republished as a new Bitmap (or at another size) is told from a new one.
 */
class SessionArtwork private constructor(val bitmap: Bitmap, private val signature: IntArray) {
    val fingerprint: Int = signature.contentHashCode()

    /** Whether [other] is the same picture, maybe at another size or encoding. */
    fun sameCover(other: SessionArtwork): Boolean {
        if (other.fingerprint == fingerprint) return true
        var diff = 0
        for (i in signature.indices) diff += abs(signature[i] - other.signature[i])
        return diff <= SAME_COVER_MAX_MEAN_DIFF * signature.size
    }

    companion object {
        fun of(bitmap: Bitmap) = SessionArtwork(bitmap, signature(bitmap))
    }
}

/** Mean channel difference (0..255) up to which two covers count as the same picture. */
private const val SAME_COVER_MAX_MEAN_DIFF = 8

/**
 * Loads the cover at [artworkUri] (an image URL, through Glide as the rest of Astral does).
 * Unlike upstream there is no app-logo fallback: with no cover, the result stays null and the
 * background shows what it has.
 */
@Composable
fun rememberSessionArtwork(artworkUri: String?, maxDimension: Int): SessionArtwork? {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf<SessionArtwork?>(null) }
    LaunchedEffect(artworkUri, maxDimension) {
        val cover = loadSessionArtwork(context, artworkUri, maxDimension)
        if (cover == null) {
            if (artworkUri.isNullOrBlank()) loaded = null
            return@LaunchedEffect
        }
        // Keep the one shown when this is the same cover again.
        if (loaded?.sameCover(cover) != true) loaded = cover
    }
    return loaded
}

suspend fun loadSessionArtwork(context: Context, artworkUri: String?, maxDimension: Int): SessionArtwork? {
    if (artworkUri.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        val source = try {
            Glide.with(context.applicationContext).asBitmap().load(Covers.large(artworkUri))
                .disallowHardwareConfig().submit(maxDimension, maxDimension).get()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        val software = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else source
        val longest = maxOf(software.width, software.height)
        val sized = if (longest <= maxDimension) software else Bitmap.createScaledBitmap(
            software,
            (software.width * maxDimension.toFloat() / longest).toInt().coerceAtLeast(1),
            (software.height * maxDimension.toFloat() / longest).toInt().coerceAtLeast(1),
            true,
        )
        SessionArtwork.of(sized)
    }
}

/**
 * The cover's 8x8 block averages, RGB. Averaging every pixel (rather than scaling down, which
 * samples only a few) keeps it stable across sizes and encodings of the same picture.
 */
private fun signature(bitmap: Bitmap): IntArray {
    val width = bitmap.width
    val height = bitmap.height
    val sums = LongArray(SIGNATURE_CELLS * SIGNATURE_CELLS * 3)
    val counts = IntArray(SIGNATURE_CELLS * SIGNATURE_CELLS)
    val row = IntArray(width)
    for (y in 0 until height) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        val cellRow = y * SIGNATURE_CELLS / height * SIGNATURE_CELLS
        for (x in 0 until width) {
            val cell = cellRow + x * SIGNATURE_CELLS / width
            val pixel = row[x]
            sums[cell * 3] += (pixel shr 16 and 0xFF).toLong()
            sums[cell * 3 + 1] += (pixel shr 8 and 0xFF).toLong()
            sums[cell * 3 + 2] += (pixel and 0xFF).toLong()
            counts[cell]++
        }
    }
    return IntArray(sums.size) { i -> (sums[i] / counts[i / 3].coerceAtLeast(1)).toInt() }
}

private const val SIGNATURE_CELLS = 8
