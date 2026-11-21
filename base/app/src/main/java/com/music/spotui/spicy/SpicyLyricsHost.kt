// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.spicy

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.music.spotui.di.Graph
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
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
import com.music.spotui.audio.TrackAudioSync
import app.myspoti.api.best
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.SpicyBackgroundPref
import com.music.spotui.data.preferences.SpicyBackgroundSettings
import com.music.spotui.spicy.background.SpicySessionBackground
import com.music.spotui.spicy.canvas.LyricsView
import com.music.spotui.spicy.canvas.LyricsViewState
import com.music.spotui.spicy.canvas.kawarp.MotionSource
import com.music.spotui.spicy.canvas.kawarp.TrackMotion

/** The renderer's look: Astral's own choices over upstream's defaults. */
internal fun spicyRenderConfig(
    reduceMotion: Boolean,
    options: com.music.spotui.data.preferences.SpicyRenderSettings = com.music.spotui.data.preferences.SpicyRenderSettings(),
): RenderConfig {
    val simple = reduceMotion || options.lowEffects
    return RenderConfig(
        bloom = options.bloom && !simple,
        glowEnabled = !simple,
        distanceBlurEnabled = !simple,
        lettersEnabled = !simple,
        wordMotionBoost = if (simple) 0f else 1f,
        scroll = ScrollConfig(smooth = !simple),
    )
}

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
    /** How far up from the bottom the lyrics are covered (notices drawn over them): they fade out above it. */
    maskBottomPx: () -> Float = { 0f },
    /** The colour of the notices under the lyrics (unsynced note, provider credit). */
    noticeColor: Color = Color.White,
    translations: List<String>? = null,
) {
    val options by com.music.spotui.data.preferences.SpicyRenderPref.state.collectAsState()
    val doc = remember(lyrics, romanize, songDurationMs, translations) {
        spicyDocument(lyrics, songDurationMs, romanize, translations = translations, romanizationAsNote = true)
    }
    val config = remember(reduceMotion, options) { spicyRenderConfig(reduceMotion, options) }
    // The notices sit over the bottom of the view: the lyrics fade out above them (and 12dp clear of
    // them) and scroll no further under them.
    var noticeHeightPx by remember { mutableIntStateOf(0) }
    val clearPx = with(LocalDensity.current) { 12.dp.toPx() }
    val noticeHeight by rememberUpdatedState(noticeHeightPx)
    Box(modifier) {
    LyricsView(
        lines = doc.lines,
        documentId = "$documentKey|${lyrics.provider}|${lyrics.lines.size}|${lyrics.lines.firstOrNull()?.timeMs}",
        currentTimeMs = currentTimeMs,
        onSeekWord = onSeek,
        modifier = Modifier.fillMaxSize(),
        config = config,
        lyricsType = doc.type,
        isPlaying = playing,
        viewState = viewState,
        maskBottomPx = { maxOf(maskBottomPx(), if (noticeHeight > 0) noticeHeight + clearPx else 0f) },
    )
    SpicyLyricsNotices(
        lyrics, noticeColor,
        Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp),
        onHeight = { noticeHeightPx = it },
    )
    }
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
    val options by com.music.spotui.data.preferences.SpicyRenderPref.state.collectAsState()
    val animate = !settings.still && !options.stillBackground && !options.lowEffects && !reduceMotion && !saver.policy.saverEnabled
    val header = rememberArtistHeader(artist, settings.type.usesArtistHeader)
    val position by rememberUpdatedState(positionMs)
    val reactive = settings.beatReactive && animate
    val motion = remember(reactive) {
        if (reactive) beatMotionSource { position() / 1000f } else null
    }
    // Fetch beat data only when this view can use it.
    if (reactive) {
        // Keyed on the track's uri: a remaster after the original (same title and artist) still fetches.
        LaunchedEffect(com.music.spotui.di.SongPlayer.nowPlaying.value.uri) { TrackAudioSync.requestAnalysis() }
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
 * Where the moving background gets its beat: the loudness and beat Astral holds for the playing
 * song ([TrackAudioSync.peekMotion]: Spotify's analysis, or AutoMix's), read once a frame without
 * touching [TrackAudioSync]'s shared sampler. Only built when the user turned beat-reactive on; the
 * song's beat data is then asked for once ([TrackAudioSync.requestAnalysis]). With none for the
 * song, [MotionSource.track] is null and the background keeps its steady, unreactive motion.
 */
internal fun beatMotionSource(positionSeconds: () -> Float): MotionSource {
    val frameMotion = FrameMotion()
    return MotionSource(
        track = {
            val peek = TrackAudioSync.peekMotion((positionSeconds() * 1000f).toLong())
            if (peek != null) frameMotion.also { it.peek = peek } else null
        },
        positionSeconds = positionSeconds,
    )
}

/** One frame of [TrackAudioSync.peekMotion], read as a [TrackMotion]. */
internal class FrameMotion : TrackMotion {
    var peek: TrackAudioSync.MotionPeek = TrackAudioSync.MotionPeek(0f, 0f)
    override fun energyAt(t: Float): Float = peek.energy
    override fun beatPulse(): Float = peek.beatPulse
}

/** An artist's header picture: [url] null with none; [pending] while it is being looked up. */
class ArtistHeader(val url: String?, val pending: Boolean)

private val headerCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** The first artist of a credit line such as "A, B" or "A & B". */
internal fun primaryArtist(credit: String): String =
    credit.split(',', '&', ';').first().trim()

/**
 * [artist]'s header picture from Astral's artist page (a search and one page request), when
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
        // Just the artist's search hit and its page's header images: not Api.getArtistOverview, which
        // loads the discography too and registers the artist's top tracks as the playing context.
        val image = try {
            withContext(Dispatchers.IO) {
                val api = MyspotiRuntime.api
                val hits = api.search.artists(name, limit = 5).items
                val artist = hits.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: hits.firstOrNull()
                if (artist == null) "" else api.catalog.artistPage(artist.uri).headerImages.best(1280)?.url.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        // A failed lookup (offline, signed out) shows the cover now and is tried again next time;
        // an artist with no header is remembered as having none.
        if (image == null) { value = ArtistHeader(null, false); return@produceState }
        headerCache[name] = image
        value = ArtistHeader(image.takeIf(String::isNotEmpty), false)
    }
    return state.value
}

/** True where the moving Default preset can run its shader (Android 13+; older phones get its circles). */
val kawarpAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * What the classic list prints around the lyrics, for the Spicy view: a note when they are not
 * time-synced and who provided them. (The list's Genius hint is left out: the Spicy view has no
 * long-press on a line to open a note.)
 */
@Composable
fun SpicyLyricsNotices(lyrics: Lyrics, color: Color, modifier: Modifier = Modifier, onHeight: (Int) -> Unit = {}) {
    val provider = lyrics.provider?.takeIf { it.isNotBlank() }
    if (lyrics.synced && provider == null) {
        onHeight(0)
        return
    }
    Column(modifier.onSizeChanged { onHeight(it.height) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!lyrics.synced) Text(
            stringResource(R.string.lyrics_fullscreen_unsynced),
            color = color.copy(alpha = 0.8f), fontSize = 13.sp,
        )
        if (provider != null) Text(
            stringResource(R.string.spicy_lyrics_by, provider),
            color = color.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
        )
    }
}
