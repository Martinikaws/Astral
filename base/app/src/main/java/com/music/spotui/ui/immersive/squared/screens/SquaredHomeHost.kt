/*
 * Written for Astral, 2026-11, for the Squared look. Feeds Square's home page (SquaredHomeScreen,
 * from Square's ui/home/HomeScreen.kt) from Astral's data.
 *
 * Changes: new file. HomeViewModel's feed becomes the shelves, the library's playlists the
 * "your playlists" row, and ListenHistory the "play again" row. Opening goes through
 * [openSquared]; the profile disc opens Astral's own profile sheet.
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
import com.music.spotui.data.api.ProfileCache
import com.music.spotui.data.api.Response
import com.music.spotui.data.local.ListenHistory
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.immersive.screens.ImmersiveProfileSheet
import com.music.spotui.ui.immersive.squared.glass.LocalAppBackdrop
import com.music.spotui.ui.immersive.squared.model.CatalogTrack
import com.music.spotui.ui.immersive.squared.model.SquaredFeedState
import com.music.spotui.ui.immersive.squared.model.SquaredUiState
import com.music.spotui.ui.immersive.squared.model.podcastShelves
import com.music.spotui.ui.immersive.squared.model.recentTracks
import com.music.spotui.ui.immersive.squared.model.toCatalogPlaylist
import com.music.spotui.ui.immersive.squared.model.toShelves
import com.music.spotui.ui.immersive.squared.model.withoutPodcasts
import com.music.spotui.ui.viewmodel.HomeViewModel
import com.music.spotui.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Home tab of the Squared look. */
@Composable
fun SquaredHomeHost(navController: NavController) {
    val homeViewModel: HomeViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val home by homeViewModel.home.collectAsState()
    val entries by libraryViewModel.entries.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { ProfileCache.ensure(context) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        homeViewModel.refreshIfStale()
    }

    val feed = (home as? Response.Success)?.data
    val allShelves = remember(feed) { feed?.sections.orEmpty().toShelves() }
    val shelves = remember(allShelves) { allShelves.withoutPodcasts() }
    val podcasts = remember(allShelves) { allShelves.podcastShelves() }
    val playlists = remember(entries) {
        (entries as? Response.Success)?.data.orEmpty().map { it.toCatalogPlaylist() }
    }
    val state: SquaredUiState = when (val h = home) {
        is Response.Loading -> SquaredUiState.Loading
        is Response.Error -> SquaredUiState.Failed(h.error)
        is Response.Success -> SquaredUiState.Ready(
            displayName = ProfileCache.name.orEmpty(),
            playlists = playlists,
            avatarUrl = ProfileCache.imageUrl,
        )
    }

    // The "play again" row: the history is a file, so it is read off the main thread.
    val recent by produceState(emptyList<CatalogTrack>(), home) {
        value = withContext(Dispatchers.IO) { recentTracks(ListenHistory.all()) }
    }

    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) {
        ImmersiveProfileSheet(
            onOpen = { route -> showProfile = false; navController.navigate(route) },
            onDismiss = { showProfile = false },
        )
    }

    SquaredHomeScreen(
        state = state,
        contentPadding = rememberSquaredContentPadding(),
        onRetry = homeViewModel::retry,
        onOpenPlaylist = { navController.openSquared(it.uri, it.name, it.subtitle.orEmpty()) },
        playlistOrder = emptyList(),
        recent = recent,
        onPlayRecent = { list, index -> list.getOrNull(index)?.let { SongPlayer.playSong(it.uri) } },
        onPlayFeed = { list, index, _ -> list.getOrNull(index)?.let { SongPlayer.playSong(it.uri) } },
        feed = SquaredFeedState(),
        onOpenItem = { navController.openSquared(it.uri, it.title, it.subtitle) },
        onOpenSettings = { showProfile = true },
        title = feed?.greeting?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.navigationbar_musicappitems_home_title),
        backdrop = LocalAppBackdrop.current,
        shelves = shelves,
        shelvesLoading = home is Response.Loading,
        podcastShelves = podcasts,
        podcastsLoading = false,
        onPlayEpisode = { item, _ -> navController.openSquared(item.uri, item.name, item.subtitle.orEmpty()) },
    )
}
