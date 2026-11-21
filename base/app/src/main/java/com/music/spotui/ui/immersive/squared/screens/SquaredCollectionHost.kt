/*
 * Written for Astral, 2026-11, for the Squared look. Feeds Square's album, playlist, artist and
 * podcast page (SquaredCollectionScreen, from Square's ui/library/PlaylistScreen.kt) from
 * Astral's pages.
 *
 * Changes: new file. ContextViewModel's page (album, playlist, Liked Songs, a liked-by-artist
 * list) becomes a SquaredPlaylistState, ArtistViewModel's overview the artist state and
 * ShowViewModel's page the podcast state. Play, shuffle, queue, like, follow, the track menu and
 * the add-to-playlist sheet go to Astral's own player and sheets. The artist's tall portrait and
 * moving cover come from SquaredCatalog, behind the "Animated covers & artist artwork" setting
 * and the same data and battery rules as the player; the lookups run off the main thread.
 */
package com.music.spotui.ui.immersive.squared.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.ContextPageModel
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.AlbumOptionsSheet
import com.music.spotui.ui.components.PlaylistOptionsSheet
import com.music.spotui.ui.immersive.sheets.ImmersiveSongOptionsSheet
import com.music.spotui.ui.immersive.squared.SquaredCatalog
import com.music.spotui.ui.immersive.squared.SquaredPrefs
import com.music.spotui.ui.immersive.squared.model.CatalogTrack
import com.music.spotui.ui.immersive.squared.model.SquaredDetailKind
import com.music.spotui.ui.immersive.squared.model.SquaredPlaylistState
import com.music.spotui.ui.immersive.squared.model.indexedByTrack
import com.music.spotui.ui.immersive.squared.model.toCatalogTrack
import com.music.spotui.ui.immersive.squared.model.toSearchItem
import com.music.spotui.ui.immersive.squared.model.toState
import com.music.spotui.ui.immersive.squared.rememberSquaredMotionAllowed
import com.music.spotui.ui.navigation.profileRoute
import com.music.spotui.ui.screens.ContextViewModel
import com.music.spotui.ui.screens.SmartShuffle
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.ui.viewmodel.ArtistViewModel
import com.music.spotui.ui.viewmodel.ShowViewModel
import kotlinx.coroutines.CancellationException

private const val SORT_PREFS = "squared_track_sort"

private fun sortPrefs(context: Context) = context.getSharedPreferences(SORT_PREFS, Context.MODE_PRIVATE)

/** An album, playlist, Liked Songs or liked-by-artist page: [type] is the ContextViewModel's. */
@Composable
fun SquaredCollectionHost(navController: NavController, type: String, id: String) {
    val vm: ContextViewModel = viewModel(key = "squared:$type:$id")
    // During composition, so a cached page is there for the first frame.
    remember(type, id) { vm.load(type, id) }
    val resp by vm.page.collectAsState()
    val saved by vm.contextSaved
    val editable by vm.editableContext
    val recs by vm.recs
    val context = LocalContext.current
    val playerVm: PlayerViewModel = viewModel()
    val kind = if (type == "album") SquaredDetailKind.ALBUM else SquaredDetailKind.PLAYLIST

    val page = (resp as? Response.Success)?.data
    val byTrack = remember(page) { page?.tracks.orEmpty().indexedByTrack() }
    val byRec = remember(recs) { recs.orEmpty().indexedByTrack() }
    val state = remember(resp, saved, editable, kind) {
        when (val r = resp) {
            is Response.Loading -> SquaredPlaylistState(kind = kind, loading = true)
            is Response.Error -> SquaredPlaylistState(kind = kind, error = r.error)
            is Response.Success -> r.data.toCollectionState(kind, saved = saved, mine = editable)
        }
    }

    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    var pageMenu by remember { mutableStateOf(false) }
    menuSong?.let { song ->
        ImmersiveSongOptionsSheet(
            song = song, navController = navController, context = context, onDismiss = { menuSong = null },
            onRemoveFromPlaylist = if (editable) ({ vm.dropTrack(song); menuSong = null }) else null,
            currentPlaylistId = page?.uri?.substringAfterLast(':')?.takeIf { type == "playlist" },
        )
    }
    if (pageMenu && page != null) {
        if (page.type == "album" || type == "liked") {
            AlbumOptionsSheet(albumUri = page.uri, name = page.name, known = page, onDismiss = { pageMenu = false })
        } else {
            PlaylistOptionsSheet(
                page = page,
                onDismiss = { pageMenu = false },
                onChanged = { vm.reload() },
                onEdited = { _, _ -> vm.reload() },
                onDeleted = { pageMenu = false; navController.navigateUp() },
                onProfile = { navController.navigate(profileRoute(it, page.ownerName)) },
            )
        }
    }

    LaunchedEffect(page?.uri, editable) {
        if (page != null && editable && page.type == "playlist") vm.loadRecs(page)
    }
    val nowPlaying by SongPlayer.nowPlaying
    val songOf: (CatalogTrack) -> SongsModel? = { byTrack[it.uri] ?: byRec[it.uri] }

    SquaredCollectionScreen(
        state = state,
        contentPadding = rememberSquaredContentPadding(),
        nowPlayingUri = nowPlaying.uri,
        nowPlaying = Graph.currentSongState.playingState.value,
        onTogglePlay = { if (Graph.currentSongState.playingState.value) SongPlayer.pause() else SongPlayer.play() },
        onBack = { navController.navigateUp() },
        onPlay = { list, index, inOrder ->
            val song = list.getOrNull(index)?.let(songOf)
            if (song != null && page != null) {
                if (inOrder) vm.play(page, song)
                else SongPlayer.playSong(song.url, context, songs = list.mapNotNull(songOf))
            }
        },
        onEnqueue = { track -> songOf(track)?.let { SongPlayer.queueSongs(listOf(it), next = false) {} } },
        onShuffle = { list ->
            val songs = list.mapNotNull(songOf)
            if (page != null && songs.size == page.tracks.size && songs.isNotEmpty()) {
                // As Astral's own page: smart shuffle mixes the list itself; otherwise start
                // shuffled from a random song, another one on every tap.
                if (SmartShuffle.on.value == page.uri) vm.playSmart(page, null)
                else {
                    playerVm.updateShuffleState(true)
                    val others = songs.filter { it.toCatalogTrack().uri != nowPlaying.uri }
                    vm.play(page, others.ifEmpty { songs }.random())
                }
            } else SongPlayer.shufflePlay(songs)
        },
        onPlayInOrder = { list, inOrder ->
            val songs = list.mapNotNull(songOf)
            val first = songs.firstOrNull()
            if (first != null && page != null) {
                if (inOrder) vm.play(page, first) else SongPlayer.playSong(first.url, context, songs = songs)
            }
        },
        onTrackMenu = { track -> menuSong = songOf(track) },
        onOpenItem = { navController.openSquared(it.uri, it.title, it.subtitle) },
        onMenu = { pageMenu = true },
        onToggleSaved = { page?.let(vm::toggleContextSaved) },
        suggestions = remember(recs) { recs.orEmpty().map { it.toCatalogTrack() } },
        suggestionsLoading = editable && recs == null,
        onAddSuggestion = { track ->
            val song = songOf(track)
            if (song != null && page != null) {
                com.music.spotui.data.api.SpotifySync.addTrackToPlaylist(context, page.uri.substringAfterLast(':'), song.spotifyTrackId) { ok ->
                    if (ok) { vm.takeRec(page, song); vm.reload() }
                }
            }
        },
        onRefreshSuggestions = { page?.let { vm.loadRecs(it, refresh = true) } },
        storedSort = page?.let { sortPrefs(context).getString(it.uri, null) },
        onSortChange = { v -> page?.let { sortPrefs(context).edit().putString(it.uri, v).apply() } },
        storedSortDescending = page?.let { sortPrefs(context).getBoolean(it.uri + ":desc", false) } ?: false,
        onSortDescendingChange = { v -> page?.let { sortPrefs(context).edit().putBoolean(it.uri + ":desc", v).apply() } },
    )
}

/** An album resolved by name first when the route carries no id, as Astral's own album route does. */
@Composable
fun SquaredAlbumHost(navController: NavController, name: String, artist: String, id: String) {
    var resolved by remember(name, artist, id) { mutableStateOf(id.ifBlank { null }) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(name, artist, id) {
        if (resolved == null) {
            resolved = Graph.api.resolveAlbumId(name, artist)
            if (resolved == null) failed = true
        }
    }
    val albumId = resolved
    if (albumId != null) SquaredCollectionHost(navController, "album", albumId)
    else SquaredCollectionScreen(
        state = SquaredPlaylistState(kind = SquaredDetailKind.ALBUM, name = name, loading = !failed, error = if (failed) name else null),
        contentPadding = rememberSquaredContentPadding(),
        nowPlayingUri = null,
        onBack = { navController.navigateUp() },
        onPlay = { _, _, _ -> }, onEnqueue = {}, onShuffle = {}, onPlayInOrder = { _, _ -> },
        onTrackMenu = {},
    )
}

/** An artist's page: Spotify's overview, with the catalogue's portrait and moving cover over it. */
@Composable
fun SquaredArtistHost(navController: NavController, name: String, id: String) {
    val vm: ArtistViewModel = viewModel(key = "squared:artist:$name:$id")
    remember(name, id) { vm.loadArtistOverview(name, id) }
    val resp by vm.overview.collectAsState()
    val context = LocalContext.current
    val enabled by SquaredPrefs.of(context).animatedCovers.collectAsState()
    val motion = rememberSquaredMotionAllowed()
    val overview = (resp as? Response.Success)?.data
    val artistName = overview?.name?.takeIf { it.isNotBlank() } ?: name
    val uriId = overview?.id?.takeIf { it.isNotBlank() } ?: id

    // The catalogue's portrait for the artist, off the main thread, not under the data saver.
    val art by produceState<SquaredCatalog.Artist?>(null, artistName, enabled) {
        value = null
        if (!enabled || com.music.spotui.data.DataSaver.active || artistName.isBlank()) return@produceState
        value = try {
            SquaredCatalog.artist(artistName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    var follow by remember(uriId) { mutableStateOf<Boolean?>(null) }
    val followed = follow ?: SongPlayer.library["spotify:artist:$uriId"]
    var tracksMenu by remember { mutableStateOf<SongsModel?>(null) }
    val byTrack = remember(overview) { overview?.topTracks.orEmpty().map { it.song }.indexedByTrack() }
    tracksMenu?.let { ImmersiveSongOptionsSheet(song = it, navController = navController, context = context, onDismiss = { tracksMenu = null }) }

    val state = remember(resp, art, motion, followed, uriId) {
        when (val r = resp) {
            is Response.Loading -> SquaredPlaylistState(kind = SquaredDetailKind.ARTIST, name = name, loading = true)
            is Response.Error -> SquaredPlaylistState(kind = SquaredDetailKind.ARTIST, name = name, error = r.error)
            is Response.Success -> r.data.toState("spotify:artist:$uriId").let { s ->
                val a = art
                s.copy(
                    following = followed,
                    heroUrl = a?.heroUrl ?: s.heroUrl,
                    heroAspect = a?.heroAspect,
                    tintHex = a?.bgColor,
                    inkHex = a?.textHex,
                    origin = a?.origin,
                    logoUrl = a?.logoUrl,
                    notes = s.notes ?: a?.bio,
                    heroPending = enabled && a == null && !com.music.spotui.data.DataSaver.active,
                )
            }
        }
    }
    val nowPlaying by SongPlayer.nowPlaying
    val songs = overview?.topTracks.orEmpty().map { it.song }
    SquaredCollectionScreen(
        state = state,
        contentPadding = rememberSquaredContentPadding(),
        nowPlayingUri = nowPlaying.uri,
        nowPlaying = Graph.currentSongState.playingState.value,
        onTogglePlay = { if (Graph.currentSongState.playingState.value) SongPlayer.pause() else SongPlayer.play() },
        onBack = { navController.navigateUp() },
        onPlay = { list, index, _ ->
            list.getOrNull(index)?.let { byTrack[it.uri] }?.let {
                SongPlayer.playSong(it.url, context, songs = list.mapNotNull { t -> byTrack[t.uri] })
            }
        },
        onEnqueue = { t -> byTrack[t.uri]?.let { SongPlayer.queueSongs(listOf(it), next = false) {} } },
        onShuffle = { list -> SongPlayer.shufflePlay(list.mapNotNull { byTrack[it.uri] }.ifEmpty { songs }) },
        onPlayInOrder = { list, _ ->
            val l = list.mapNotNull { byTrack[it.uri] }.ifEmpty { songs }
            l.firstOrNull()?.let { SongPlayer.playSong(it.url, context, songs = l) }
        },
        onTrackMenu = { t -> tracksMenu = byTrack[t.uri] },
        onOpenItem = { navController.openSquared(it.uri, it.title, it.subtitle) },
        onToggleFollow = {
            val on = !(followed ?: false)
            follow = on
            SongPlayer.setSaved("spotify:artist:$uriId", on)
        },
    )
}

/** A podcast's page: the show and its episodes. */
@Composable
fun SquaredShowHost(navController: NavController, showId: String, showName: String) {
    val vm: ShowViewModel = viewModel(key = "squared:show:$showId")
    remember(showId) { vm.loadShow(showId) }
    val resp by vm.page.collectAsState()
    val following by vm.following
    val context = LocalContext.current
    val page = (resp as? Response.Success)?.data
    val byTrack = remember(page) { page?.items.orEmpty().indexedByTrack() }
    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    menuSong?.let { ImmersiveSongOptionsSheet(song = it, navController = navController, context = context, onDismiss = { menuSong = null }) }

    val state = remember(resp, following) {
        when (val r = resp) {
            is Response.Loading -> SquaredPlaylistState(kind = SquaredDetailKind.SHOW, name = showName, loading = true)
            is Response.Error -> SquaredPlaylistState(kind = SquaredDetailKind.SHOW, name = showName, error = r.error)
            is Response.Success -> SquaredPlaylistState(
                uri = "spotify:show:$showId",
                name = r.data.show.name,
                artworkUrl = r.data.coverUri.ifBlank { r.data.show.coverUri }.takeIf { it.isNotBlank() },
                kind = SquaredDetailKind.SHOW,
                tracks = r.data.items.map { it.toCatalogTrack() },
                description = r.data.show.description,
                byline = r.data.show.publisher,
                following = following,
            )
        }
    }
    val nowPlaying by SongPlayer.nowPlaying
    SquaredCollectionScreen(
        state = state,
        contentPadding = rememberSquaredContentPadding(),
        nowPlayingUri = nowPlaying.uri,
        nowPlaying = Graph.currentSongState.playingState.value,
        onTogglePlay = { if (Graph.currentSongState.playingState.value) SongPlayer.pause() else SongPlayer.play() },
        onBack = { navController.navigateUp() },
        onPlay = { list, index, _ ->
            list.getOrNull(index)?.let { byTrack[it.uri] }?.let { SongPlayer.playSong(it.url, context, songs = listOf(it), alone = true) }
        },
        onEnqueue = { t -> byTrack[t.uri]?.let { SongPlayer.queueSongs(listOf(it), next = false) {} } },
        onShuffle = { list -> list.firstOrNull()?.let { byTrack[it.uri] }?.let { SongPlayer.playSong(it.url, context, songs = listOf(it), alone = true) } },
        onPlayInOrder = { list, _ -> list.firstOrNull()?.let { byTrack[it.uri] }?.let { SongPlayer.playSong(it.url, context, songs = listOf(it), alone = true) } },
        onTrackMenu = { t -> menuSong = byTrack[t.uri] },
        onOpenItem = { navController.openSquared(it.uri, it.title, it.subtitle) },
        onToggleFollow = { vm.setFollow(showId, !following) },
    )
}

/** A collection page of Astral as the state Square's page draws. */
internal fun ContextPageModel.toCollectionState(kind: SquaredDetailKind, saved: Boolean, mine: Boolean): SquaredPlaylistState =
    SquaredPlaylistState(
        uri = uri,
        name = name,
        artworkUrl = coverUri.takeIf { it.isNotBlank() },
        tracks = tracks.map { it.toCatalogTrack() },
        loadingMore = !complete,
        kind = kind,
        description = description,
        saved = saved,
        mine = mine,
        byline = ownerName.ifBlank { metaLine },
    )
