/*
 * Written for Astral, 2026-11, for the Squared look. Feeds Square's search page
 * (SquaredSearchScreen, from Square's ui/search/SearchScreen.kt) from Astral's search.
 *
 * Changes: new file. SearchViewModel runs the query and holds the text; its results are mapped
 * by toSquared(). A song is played the way Astral's own search plays it (as a radio seeded by
 * the song); the history rows are Astral's recent items; the row menu is Astral's song sheet.
 */
package com.music.spotui.ui.immersive.squared.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.SearchResults
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.RecentItem
import com.music.spotui.data.preferences.addRecentItem
import com.music.spotui.data.preferences.clearRecentItems
import com.music.spotui.data.preferences.getRecentItems
import com.music.spotui.data.preferences.removeRecentItem
import com.music.spotui.data.preferences.toRecentItem
import com.music.spotui.data.preferences.toSong
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.immersive.sheets.ImmersiveSongOptionsSheet
import com.music.spotui.ui.immersive.squared.glass.LocalAppBackdrop
import com.music.spotui.ui.immersive.squared.model.CatalogTrack
import com.music.spotui.ui.immersive.squared.model.SquaredSearchState
import com.music.spotui.ui.immersive.squared.model.indexedByTrack
import com.music.spotui.ui.immersive.squared.model.toHistoryEntry
import com.music.spotui.ui.immersive.squared.model.toSquared
import com.music.spotui.ui.navigation.TabReselect
import com.music.spotui.ui.viewmodel.SearchViewModel

/** The Search tab of the Squared look. */
@Composable
fun SquaredSearchHost(navController: NavController) {
    val vm: SearchViewModel = viewModel()
    val results by vm.results.collectAsState()
    val data = (results as? Response.Success)?.data ?: SearchResults()
    val context = LocalContext.current

    var recents by remember { mutableStateOf(getRecentItems(context)) }
    val history = remember(recents) { recents.mapNotNull { item -> item.toHistoryEntry()?.let { it to item } } }

    // The songs of the answer, found again from the row a screen hands back.
    val byTrack = remember(data) { (data.songs + data.episodes).indexedByTrack() }
    val state = remember(vm.query, results) {
        SquaredSearchState(
            query = vm.query,
            loading = results is Response.Loading && vm.query.isNotBlank(),
            results = data.toSquared(),
            error = (results as? Response.Error)?.error,
            exhausted = true,
        )
    }

    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    menuSong?.let { song ->
        ImmersiveSongOptionsSheet(song = song, navController = navController, context = context, onDismiss = { menuSong = null })
    }

    val nowPlaying by SongPlayer.nowPlaying
    val rememberFound: (SongsModel) -> Unit = { song ->
        addRecentItem(context, song.toRecentItem())
        recents = getRecentItems(context)
    }
    val playSong: (SongsModel) -> Unit = { song ->
        vm.startRadioFromSong(song)
        SongPlayer.playSong(song.url, context, songs = listOf(song), alone = true)
        vm.updateSongState(song.coverUri, song.title, song.singer, true, song.id, 0, song.album)
    }

    SquaredSearchScreen(
        state = state,
        contentPadding = rememberSquaredContentPadding(),
        nowPlayingUri = nowPlaying.uri,
        onPlayTrack = { list: List<CatalogTrack>, index: Int ->
            val track = list.getOrNull(index)
            val song = track?.let { byTrack[it.uri] ?: history.firstOrNull { (e, _) -> e.uri == it.uri }?.second?.toSong() }
            if (song != null) {
                if (song.isEpisode) SongPlayer.playSong(song.url, context, songs = listOf(song), alone = true)
                else { rememberFound(song); playSong(song) }
            }
        },
        history = history.map { it.first },
        onClearHistory = { clearRecentItems(context); recents = emptyList() },
        onRemoveHistory = { uri ->
            history.firstOrNull { it.first.uri == uri }?.second?.let { removeRecentItem(context, it) }
            recents = getRecentItems(context)
        },
        onEnqueue = { track ->
            val song = byTrack[track.uri] ?: history.firstOrNull { it.first.uri == track.uri }?.second?.toSong()
            if (song != null) SongPlayer.queueSongs(listOf(song), next = false) {}
        },
        onTrackMenu = { track -> menuSong = byTrack[track.uri] ?: history.firstOrNull { it.first.uri == track.uri }?.second?.toSong() },
        onOpenContext = { item ->
            val target = com.music.spotui.ui.immersive.squared.model.SquaredUris.parse(item.uri, item.title, item.subtitle)
            recordOpened(context, target, item.artworkUrl)
            recents = getRecentItems(context)
            navController.openSquared(item.uri, item.title, item.subtitle)
        },
        onQuery = { vm.search(it) },
        backdrop = LocalAppBackdrop.current,
    )
}

/** What was opened from the results is kept for the history, as Astral's own search keeps it. */
private fun recordOpened(
    context: android.content.Context,
    target: com.music.spotui.ui.immersive.squared.model.SquaredTarget,
    art: String?,
) {
    val type = when (target.kind) {
        com.music.spotui.ui.immersive.squared.model.SquaredTargetKind.ARTIST -> "artist"
        com.music.spotui.ui.immersive.squared.model.SquaredTargetKind.ALBUM -> "album"
        com.music.spotui.ui.immersive.squared.model.SquaredTargetKind.SHOW -> "show"
        com.music.spotui.ui.immersive.squared.model.SquaredTargetKind.PLAYLIST -> "playlist"
        else -> return
    }
    addRecentItem(
        context,
        RecentItem(
            type = type,
            key = target.id.ifBlank { target.name },
            name = target.name,
            singer = target.artist,
            image = art.orEmpty(),
        ),
    )
}
