/*
 * Written for Astral, 2026-11, for the Squared look. Not part of Square: it maps Astral's own
 * player state, queue, lyrics, Canvas and like state onto the plain models Square's ported
 * player screens draw (PlaybackState, QueueEntry, QueueActions, Lyrics, CanvasClip), and turns
 * their actions back into calls on Astral's SongPlayer.
 */
package com.music.spotui.ui.immersive.squared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.immersive.player.ImmersivePlayerActions
import com.music.spotui.ui.immersive.player.ImmersivePlayerState
import com.music.spotui.ui.immersive.player.rememberImmersivePlayer
import com.music.spotui.ui.immersive.squared.player.PlaybackState
import com.music.spotui.ui.immersive.squared.player.QueueActions
import com.music.spotui.ui.immersive.squared.player.QueueEntry
import com.music.spotui.ui.navigation.PlayerSheet
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Everything the ported Square player needs, read from Astral and kept in Square's own shapes. */
@Immutable
class SquaredPlayerModel(
    val playback: PlaybackState,
    val positionMs: State<Long>,
    val queue: List<QueueEntry>,
    val queueActions: QueueActions,
    val lyrics: Lyrics?,
    val shareableLyrics: com.music.spotui.data.entity.Lyrics?,
    val lyricsLoading: Boolean,
    val canTranslate: Boolean,
    val translationOn: Boolean,
    val translating: Boolean,
    val onTranslate: (Boolean) -> Unit,
    val canvas: CanvasClip?,
    /** The extra catalogue's pictures for the playing record, null while off or unanswered. */
    val art: SquaredCatalog.Album?,
    val artPending: Boolean,
    val onAnotherDevice: Boolean,
    val liked: Boolean,
    val actions: ImmersivePlayerActions,
    val song: com.music.spotui.data.entity.SongsModel?,
)

@Composable
fun rememberSquaredPlayer(navController: NavController): SquaredPlayerModel {
    val bridge = rememberImmersivePlayer(navController)
    val s = bridge.state
    val np by SongPlayer.nowPlaying

    val playback = remember(s, np.uri) { s.toPlaybackState(np.uri, np.contextUri) }
    val position = rememberSquaredPosition(s)
    val (queue, queueActions) = rememberSquaredQueue(s)
    val lyrics = rememberSquaredLyrics()
    val canvas = rememberSquaredCanvas(s)
    val art = rememberSquaredArt(s, motionAllowed = rememberSquaredMotionAllowed())

    return SquaredPlayerModel(
        playback = playback,
        positionMs = position,
        queue = queue,
        queueActions = queueActions,
        lyrics = lyrics.lyrics,
        shareableLyrics = lyrics.original,
        lyricsLoading = lyrics.loading,
        canTranslate = lyrics.canTranslate,
        translationOn = lyrics.translationOn,
        translating = lyrics.translating,
        onTranslate = lyrics.onTranslate,
        canvas = canvas,
        art = art.album,
        artPending = art.pending,
        onAnotherDevice = s.deviceName != null,
        liked = s.liked,
        actions = bridge.actions,
        song = s.song,
    )
}

/** The position, read off Astral's player while the screen is on: about every frame while the
 * player is open, a few times a second behind the mini player. */
@Composable
private fun rememberSquaredPosition(state: ImmersivePlayerState): State<Long> {
    val position = remember { mutableLongStateOf(0L) }
    val provider by rememberUpdatedState(state.positionMs)
    val playing by rememberUpdatedState(state.playing)
    val sheetOpen by rememberUpdatedState(PlayerSheet.open.value)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val now = provider()
                if (now != position.longValue) position.longValue = now
                delay(positionPollMs(sheetOpen, playing))
            }
        }
    }
    return position
}

/**
 * Astral's queue as Square's flat list: what was heard is not kept by the engine, so the list is
 * the playing track, what was queued by hand, then what follows. Indices point into [items].
 */
@Composable
private fun rememberSquaredQueue(state: ImmersivePlayerState): Pair<List<QueueEntry>, QueueActions> {
    val engine by SongPlayer.engineQueue
    val np by SongPlayer.nowPlaying
    val items = remember(engine) { engine.queued + engine.nextUp }
    val entries = remember(engine, state.title, state.artist, np.uri) {
        buildList {
            add(
                QueueEntry(
                    index = 0,
                    uri = np.uri.ifBlank { "current" },
                    title = state.title,
                    artist = state.artist,
                    isCurrent = true,
                ),
            )
            val context = np.contextName
            engine.queued.forEach { item ->
                add(
                    QueueEntry(
                        index = size,
                        uri = item.uri + "#" + item.uid,
                        title = item.song.title,
                        artist = item.song.singer,
                        isCurrent = false,
                        queued = true,
                    ),
                )
            }
            engine.nextUp.forEach { item ->
                add(
                    QueueEntry(
                        index = size,
                        uri = item.uri + "#" + item.uid,
                        title = item.song.title,
                        artist = item.song.singer,
                        isCurrent = false,
                        contextLabel = context,
                    ),
                )
            }
        }
    }
    val itemsState = rememberUpdatedState(items)
    val actions = remember {
        fun item(index: Int) = queueItemAt(itemsState.value, index)
        QueueActions(
            onPlay = { index -> item(index)?.let(SongPlayer::skipTo) },
            onRemove = { indices -> indices.mapNotNull(::item).forEach(SongPlayer::removeFromQueue) },
            onMove = { from, to ->
                planQueueMove(itemsState.value, from, to)?.let { SongPlayer.reorder(it.moved, it.target, it.after) }
            },
            onPlayNext = { indices ->
                indices.mapNotNull(::item).asReversed().forEach { item ->
                    // An episode goes through the list path, which knows its uri; playNext
                    // would turn the id into a track.
                    if (item.uri.startsWith("spotify:episode:") || item.song.url.startsWith("episode:")) {
                        SongPlayer.queueSongs(listOf(item.song), next = true) {}
                    } else {
                        SongPlayer.playNext(item.uri.substringAfterLast(':'))
                    }
                }
            },
            editable = true,
        )
    }
    return entries to actions
}

/** Astral's lyrics (and the translation it can fetch) in Square's model. */
private class SquaredLyricsState(
    val lyrics: Lyrics?,
    val original: com.music.spotui.data.entity.Lyrics?,
    val loading: Boolean,
    val canTranslate: Boolean,
    val translationOn: Boolean,
    val translating: Boolean,
    val onTranslate: (Boolean) -> Unit,
)

@Composable
private fun rememberSquaredLyrics(): SquaredLyricsState {
    val vm: LyricsViewModel = viewModel()
    val song = Graph.currentSongState
    // Not loaded while the player is closed: nothing draws them there.
    val state = com.music.spotui.ui.screens.rememberLyricsState(
        vm, song.title.value, song.singer.value, hold = !PlayerSheet.open.value,
    )
    val target = com.music.spotui.ui.components.readerLanguage()
    val loaded = state as? LyricsViewModel.State.Loaded
    val mapped = remember(loaded?.lyrics, loaded?.translations, loaded?.showTranslation) {
        loaded?.let { l ->
            val translations = l.translations.takeIf { l.showTranslation }
            Lyrics(
                lines = l.lyrics.lines.mapIndexed { i, line ->
                    LyricLine(
                        startTimeMs = if (l.lyrics.synced) line.timeMs else null,
                        text = line.text,
                        words = lyricWordsFor(line.text, line.words.orEmpty().map { LyricWord(it.startMs, it.endMs, it.text) }),
                        translation = translations?.getOrNull(i)?.takeIf { it.isNotBlank() },
                        romanization = line.romanization,
                    )
                },
                synced = l.lyrics.synced,
                source = l.lyrics.provider,
            )
        }
    }
    return SquaredLyricsState(
        lyrics = mapped,
        original = loaded?.lyrics,
        loading = state is LyricsViewModel.State.Loading,
        canTranslate = loaded != null && !LyricsViewModel.sameLanguage(loaded.sourceLanguage, target),
        translationOn = loaded?.showTranslation == true,
        translating = loaded?.translating == true && loaded.translationRequested,
        onTranslate = { vm.toggleTranslation(target) },
    )
}

/**
 * Whether moving pictures (the Canvas clip, the catalogue's moving cover) may play: the Canvas
 * setting, the battery policy and the data rules, the same as the other looks.
 */
@Composable
internal fun rememberSquaredMotionAllowed(): Boolean {
    val context = LocalContext.current
    val energy by app.myspoti.power.EnergySettings.state.collectAsState()
    return motionAllowed(
        canvasSetting = com.music.spotui.data.preferences.CanvasPref.background,
        energyAllowsCanvas = energy.policy.allowCanvas,
        dataSaverActive = com.music.spotui.data.DataSaver.active,
        metered = com.music.spotui.data.DataSaver.metered,
        canvasWifiOnly = com.music.spotui.data.preferences.isCanvasWifiOnly(context),
    )
}

/** The track's Canvas clip, under the same rules (setting, battery, data saver) as the other looks. */
@Composable
private fun rememberSquaredCanvas(state: ImmersivePlayerState): CanvasClip? {
    val vm: com.music.spotui.ui.viewmodel.PlayerViewModel = viewModel()
    val allowed = rememberSquaredMotionAllowed()
    val open = PlayerSheet.open.value
    val trackId = state.trackId
    LaunchedEffect(trackId, allowed, open, state.buffering) {
        // Nothing draws the clip while the player is closed, so nothing is fetched for it.
        vm.loadCanvas(if (allowed && open) trackId else "", allowFetch = !state.buffering)
    }
    val canvas = vm.canvasState.value
    val known = remember(trackId) { Graph.api.knownCanvas(trackId)?.getOrNull() }
    val url = (if (canvas.trackId == trackId) canvas.url else known)
        .takeIf { allowed && trackId.isNotBlank() && !state.adPlaying }
    return remember(url) { url?.let { CanvasClip(it, isVideo = !it.substringBefore('?').let { p -> p.endsWith(".jpg") || p.endsWith(".png") || p.endsWith(".webp") }) } }
}

private class SquaredArtState(val album: SquaredCatalog.Album?, val pending: Boolean)

/**
 * The extra catalogue's tall picture, moving cover and colours for the playing record, behind the
 * "Animated covers & artist artwork" setting (on by default).
 */
@Composable
private fun rememberSquaredArt(state: ImmersivePlayerState, motionAllowed: Boolean): SquaredArtState {
    val context = LocalContext.current
    val enabled by SquaredPrefs.of(context).animatedCovers.collectAsState()
    var album by remember { mutableStateOf<SquaredCatalog.Album?>(null) }
    var pending by remember { mutableStateOf(false) }
    val name = state.album
    val artist = state.artist.substringBefore(',').trim()
    val saver = com.music.spotui.data.DataSaver.active
    LaunchedEffect(name, artist, enabled, saver) {
        album = null
        // No catalogue lookups under the data saver.
        if (!enabled || saver || name.isBlank() || state.isEpisode) {
            pending = false
            return@LaunchedEffect
        }
        pending = true
        val found = try {
            SquaredCatalog.album(name, artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (isActive) album = found
        pending = false
    }
    // The moving cover is a video: held to the same data and battery rules as the Canvas.
    val shown = if (motionAllowed) album else album?.copy(motionUrl = null)
    return SquaredArtState(shown, pending)
}
