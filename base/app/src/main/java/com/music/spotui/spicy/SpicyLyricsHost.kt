package com.music.spotui.spicy

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.myspoti.power.EnergySettings
import com.music.spotui.audio.AudioFrame
import com.music.spotui.audio.TrackAudioSync
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.SpicyBackgroundPref
import com.music.spotui.data.preferences.SpicyBackgroundSettings
import com.music.spotui.di.Graph
import com.music.spotui.spicy.background.SpicySessionBackground
import com.music.spotui.spicy.canvas.LyricsView
import com.music.spotui.spicy.canvas.LyricsViewState
import com.music.spotui.spicy.canvas.kawarp.MotionSource
import com.music.spotui.spicy.canvas.kawarp.TrackMotion
import kotlinx.coroutines.flow.first

/** The renderer's look: Astral's own choices over upstream's defaults. */
internal fun spicyRenderConfig(reduceMotion: Boolean): RenderConfig = RenderConfig(
    glowEnabled = !reduceMotion,
    distanceBlurEnabled = !reduceMotion,
    scroll = ScrollConfig(smooth = !reduceMotion),
)

/**
 * The lyrics drawn by the ported Spicy Lyrics renderer. [currentTimeMs] is the song position in
 * milliseconds, read once a frame inside the renderer's own frame loop (never in composition);
 * [onSeek] is asked to seek when a line is tapped.
 */
@Composable
fun SpicyLyricsLayer(
    lyrics: Lyrics,
    documentKey: Any,
    currentTimeMs: () -> Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    romanize: Boolean = false,
    songDurationMs: Long = 0L,
    reduceMotion: Boolean = false,
    viewState: LyricsViewState? = null,
    /** How far up from the bottom the lyrics are covered (controls drawn over them): they fade out above it. */
    maskBottomPx: () -> Float = { 0f },
) {
    val doc = remember(lyrics, romanize, songDurationMs) { spicyDocument(lyrics, songDurationMs, romanize) }
    val config = remember(reduceMotion) { spicyRenderConfig(reduceMotion) }
    LyricsView(
        lines = doc.lines,
        documentId = "$documentKey|${lyrics.provider}|${lyrics.lines.size}|${lyrics.lines.firstOrNull()?.timeMs}",
        currentTimeMs = currentTimeMs,
        onSeekWord = onSeek,
        modifier = modifier,
        config = config,
        lyricsType = doc.type,
        isPlaying = playing,
        viewState = viewState,
        maskBottomPx = maskBottomPx,
    )
}

/**
 * The background preset behind the lyrics, from [SpicyBackgroundPref]. [coverUri] is the playing
 * cover; the artist-header presets look [artist]'s header up once and show the cover while it
 * loads, or when the artist has none.
 */
@Composable
fun SpicyBackgroundLayer(
    coverUri: String,
    artist: String,
    playing: Boolean,
    positionMs: () -> Long,
    settings: SpicyBackgroundSettings,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val saver by EnergySettings.state.collectAsState()
    val animate = !settings.still && !reduceMotion && !saver.policy.saverEnabled
    val header = rememberArtistHeader(artist, settings.type.usesArtistHeader)
    val position by rememberUpdatedState(positionMs)
    val motion = remember(settings.beatReactive) {
        if (settings.beatReactive) beatMotionSource { position() / 1000f } else null
    }
    SpicySessionBackground(
        artworkUri = coverUri.takeIf { it.isNotBlank() },
        isPlaying = playing,
        modifier = modifier.fillMaxSize(),
        type = settings.type,
        animate = animate,
        blurDp = settings.blur,
        artistHeaderUrl = header.url,
        artistHeaderPending = header.pending,
        motion = motion,
        customColor = Color(settings.customColor),
    )
}

/**
 * Where the moving background gets its beat: Astral's own [TrackAudioSync] (the loudness and beat
 * of the playing song, from Spotify's analysis or AutoMix's), read once a frame. When it has
 * nothing real for the song, [MotionSource.track] is null and the background keeps its steady,
 * unreactive motion. Nothing is fetched here beyond what [TrackAudioSync] already does for Astral.
 */
internal fun beatMotionSource(positionSeconds: () -> Float): MotionSource {
    val frameMotion = FrameMotion()
    return MotionSource(
        track = {
            val frame = TrackAudioSync.sample()
            if (frame.hasRealData) frameMotion.also { it.frame = frame } else null
        },
        positionSeconds = positionSeconds,
    )
}

/** One frame of [TrackAudioSync], read as a [TrackMotion]. */
internal class FrameMotion : TrackMotion {
    var frame: AudioFrame = AudioFrame.Empty
    override fun energyAt(t: Float): Float = frame.amplitude.coerceIn(0f, 1f)
    override fun beatPulse(): Float = frame.beatPulse.coerceIn(0f, 1f)
}

/** An artist's header picture: [url] null with none; [pending] while it is being looked up. */
class ArtistHeader(val url: String?, val pending: Boolean)

private val headerCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** The first artist of a credit line such as "A, B" or "A & B". */
internal fun primaryArtist(credit: String): String =
    credit.split(',', '&', ';').first().trim()

/**
 * [artist]'s header picture from Astral's own artist page (the Api's cached overview), when
 * [enabled]; the cover stands in whenever it is not there.
 */
@Composable
fun rememberArtistHeader(artist: String, enabled: Boolean): ArtistHeader {
    val name = primaryArtist(artist)
    val state: State<ArtistHeader> = produceState(
        initialValue = ArtistHeader(null, pending = enabled && name.isNotEmpty() && !headerCache.containsKey(name)),
        enabled, name,
    ) {
        if (!enabled || name.isEmpty()) { value = ArtistHeader(null, false); return@produceState }
        headerCache[name]?.let { value = ArtistHeader(it.takeIf(String::isNotEmpty), false); return@produceState }
        value = ArtistHeader(null, true)
        val image = runCatching {
            val result = Graph.api.getArtistOverview(name).first { it !is Response.Loading }
            (result as? Response.Success)?.data?.headerImage.orEmpty()
        }.getOrDefault("")
        // A failed lookup is remembered as "none" for this run, the cover shows.
        headerCache[name] = image
        value = ArtistHeader(image.takeIf(String::isNotEmpty), false)
    }
    return state.value
}

/** True where the moving Default preset can run its shader (Android 13+; older phones get its circles). */
val kawarpAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
