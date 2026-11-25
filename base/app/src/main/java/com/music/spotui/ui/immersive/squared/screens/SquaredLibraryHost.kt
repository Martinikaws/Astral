/*
 * Written for Astral, 2026-11, for the Squared look. Feeds Square's library page
 * (SquaredLibraryScreen, from Square's ui/library/LibraryScreen.kt) from Astral's library.
 *
 * Changes: new file. LibraryViewModel's entries become the playlists (folders and the phone's
 * own lists among them) and the saved albums, its followed artists and shows the other chips;
 * ListenHistory feeds the month in music. A long press opens Astral's own item sheet and the
 * new-playlist button its create sheet. Not carried over: Square's "recently opened" order
 * (Astral keeps none, so that sort follows the account's order) and pull to refresh.
 */
package com.music.spotui.ui.immersive.squared.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.ProfileCache
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.local.ListenHistory
import com.music.spotui.ui.immersive.squared.glass.LocalAppBackdrop
import com.music.spotui.ui.immersive.squared.model.CatalogPlaylist
import com.music.spotui.ui.immersive.squared.model.ListeningEvent
import com.music.spotui.ui.immersive.squared.model.SquaredUiState
import com.music.spotui.ui.immersive.squared.model.toCatalogPlaylist
import com.music.spotui.ui.immersive.squared.model.toListeningEvent
import com.music.spotui.ui.immersive.squared.model.toSearchItem
import com.music.spotui.ui.immersive.squared.model.toShowTile
import com.music.spotui.ui.screens.LibraryCreateSheet
import com.music.spotui.ui.screens.LibraryItemSheet
import com.music.spotui.ui.screens.LOCAL_PLAYLIST_PREFIX
import com.music.spotui.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Library tab of the Squared look. */
@Composable
fun SquaredLibraryHost(navController: NavController) {
    val vm: LibraryViewModel = viewModel()
    val entries by vm.entries.collectAsState()
    val followedArtists by vm.followedArtists.collectAsState()
    val showsLoad by vm.followedShows.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ProfileCache.ensure(context)
        vm.loadFollowedShows()
    }

    val likedName = stringResource(R.string.collection_liked_songs_title)
    val localName = stringResource(R.string.local_files_title)
    val downloadsName = stringResource(R.string.collection_downloaded)
    val localSubtitle = stringResource(R.string.assisted_curation_search_playlist) + " • " +
        stringResource(R.string.local_playlist_subtitle)
    val packageName = context.packageName
    val localShown = com.music.spotui.data.preferences.LocalFilesPref.shown
    val hasDownloads = com.music.spotui.data.offline.OfflineStore.tracks.isNotEmpty() ||
        com.music.spotui.data.offline.OfflineDownloader.requests.isNotEmpty()

    // The account's rows, with the ones that live on this phone put in under Liked Songs.
    val all: List<LibraryEntry> = remember(entries, likedName, localShown, hasDownloads) {
        val base = (entries as? Response.Success)?.data.orEmpty().map {
            if (it.spotifyId == Api.HomeCache.LIKED_SONGS_ID) it.copy(name = likedName, subtitle = "") else it
        }
        val at = base.indexOfFirst { it.spotifyId == Api.HomeCache.LIKED_SONGS_ID } + 1
        val local = LibraryEntry(
            spotifyId = Api.HomeCache.LOCAL_FILES_ID, name = localName, subtitle = "",
            coverUri = "android.resource://$packageName/drawable/local_files_cover", isPlaylist = true,
        )
        val downloads = LibraryEntry(
            spotifyId = Api.HomeCache.DOWNLOADS_ID, name = downloadsName, subtitle = "", coverUri = "", isPlaylist = true,
        )
        val phoneLists = com.music.spotui.data.local.LocalPlaylists.list().map { p ->
            LibraryEntry(
                spotifyId = LOCAL_PLAYLIST_PREFIX + p.id, name = p.name,
                subtitle = localSubtitle, coverUri = "", isPlaylist = true,
            )
        }
        base.take(at) + (if (localShown) listOf(local) else emptyList()) +
            (if (hasDownloads) listOf(downloads) else emptyList()) + phoneLists + base.drop(at)
    }
    val lists = remember(all) { all.filter { it.isPlaylist || it.isFolder } }
    val albums = remember(all) { all.filter { !it.isPlaylist && !it.isFolder } }
    val byUri = remember(all) { all.associateBy { it.toCatalogPlaylist().uri } }

    val state: SquaredUiState = when (val e = entries) {
        is Response.Loading -> SquaredUiState.Loading
        is Response.Error -> SquaredUiState.Failed(e.error)
        is Response.Success -> SquaredUiState.Ready(
            displayName = ProfileCache.name.orEmpty(),
            playlists = lists.map { it.toCatalogPlaylist() },
            avatarUrl = ProfileCache.imageUrl,
        )
    }

    val events by produceState<List<ListeningEvent>?>(null) {
        value = withContext(Dispatchers.IO) { ListenHistory.all().map { it.toListeningEvent() } }
    }

    var creating by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<LibraryEntry?>(null) }
    if (creating) LibraryCreateSheet(navController, onDismiss = { creating = false }, onLibraryChanged = { vm.load() })
    itemMenu?.let { LibraryItemSheet(it, onDismiss = { itemMenu = null }, onChanged = { vm.load() }) }

    SquaredLibraryScreen(
        state = state,
        contentPadding = rememberSquaredContentPadding(),
        onRetry = { vm.load() },
        onOpenPlaylist = { navController.openSquared(it.uri, it.name, it.subtitle.orEmpty()) },
        playlistOrder = emptyList(),
        pinned = remember(all) { all.filter { it.pinned }.map { it.toCatalogPlaylist().uri } },
        canEdit = true,
        onCreatePlaylist = { creating = true },
        onPlaylistMenu = { item: CatalogPlaylist -> byUri[item.uri]?.takeIf { it.uri.isNotBlank() }?.let { itemMenu = it } },
        artists = remember(followedArtists) { followedArtists.map { it.toSearchItem() } },
        onOpenArtist = { navController.openSquared(it.uri, it.title, it.subtitle) },
        albums = remember(albums) { albums.map { it.toCatalogPlaylist() } },
        podcasts = remember(showsLoad) {
            (showsLoad as? Response.Success)?.data.orEmpty().map { it.toShowTile() }
        },
        songs = remember(all) {
            val ids = setOf(Api.HomeCache.LIKED_SONGS_ID, Api.HomeCache.LOCAL_FILES_ID, Api.HomeCache.DOWNLOADS_ID)
            all.filter { it.spotifyId in ids }.map { it.toCatalogPlaylist() }
        },
        listeningEvents = events,
        backdrop = LocalAppBackdrop.current,
    )
}
