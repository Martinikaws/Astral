/*
 * Written for Astral, 2026-11, for the Squared look. Not part of Square (whose navigation is its
 * own back stack in SquareApp.kt): the glue that lets Square's screens, which pass addresses
 * around, open Astral's pages.
 *
 * Changes: new file. [openSquared] turns an address the screens hand over (SquaredUris) into the
 * route Astral's NavHost has for it; [rememberSquaredContentPadding] works out the room the bars
 * and mini player take, because the shell does not forward its Scaffold inset to the pages.
 */
package com.music.spotui.ui.immersive.squared.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.immersive.squared.model.SquaredTarget
import com.music.spotui.ui.immersive.squared.model.SquaredTargetKind
import com.music.spotui.ui.immersive.squared.model.SquaredUris
import com.music.spotui.ui.immersive.squared.player.MiniPlayerHeight
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.episodeRoute
import com.music.spotui.ui.navigation.folderRoute
import com.music.spotui.ui.navigation.localPlaylistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.navigation.showRoute

/** The route Astral has for [target], or null for an address nothing opens. */
fun squaredRouteFor(target: SquaredTarget): String? = when (target.kind) {
    SquaredTargetKind.ALBUM -> albumRoute(target.name, target.artist, target.id)
    SquaredTargetKind.ARTIST -> artistRoute(target.name.ifBlank { "artist" }, target.id)
    SquaredTargetKind.PLAYLIST -> playlistRoute(target.id, target.name)
    SquaredTargetKind.LIKED -> Routes.Liked.route
    SquaredTargetKind.SHOW -> showRoute(target.id, target.name)
    SquaredTargetKind.EPISODE -> episodeRoute(target.id, target.name)
    SquaredTargetKind.FOLDER -> folderRoute(target.uri, target.name)
    SquaredTargetKind.ENTRY -> ownEntryRoute(target.id)
    SquaredTargetKind.UNKNOWN -> null
}

/** The page of one of Astral's own library rows. */
private fun ownEntryRoute(id: String): String? = when {
    id == "local-files" -> Routes.LocalFiles.route
    id == "downloads" -> Routes.Downloads.route
    id == "your-episodes" -> Routes.Episodes.route
    id == "new-episodes" -> Routes.NewEpisodes.route
    id.startsWith("local-playlist:") -> localPlaylistRoute(id.removePrefix("local-playlist:"))
    else -> null
}

/** Opens the page an address from the Squared screens stands for; an episode plays instead. */
fun NavController.openSquared(uri: String, name: String = "", subtitle: String = "") {
    val target = SquaredUris.parse(uri, name, subtitle)
    if (target.kind == SquaredTargetKind.EPISODE) {
        SongPlayer.playSong("episode:${target.id}")
        return
    }
    squaredRouteFor(target)?.let { navigate(it) }
}

/**
 * The room a page leaves for the floating bars: the status bar above, and below it the system
 * bar, the tab bar (61 dp), its gap and the mini player.
 */
@Composable
fun rememberSquaredContentPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return remember(top, bottom) {
        PaddingValues(top = top, bottom = bottom + 61.dp + 8.dp + MiniPlayerHeight + 16.dp)
    }
}
