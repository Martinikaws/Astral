package com.music.spotui.ui.immersive.cupertino

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.data.local.LibraryChip
import com.music.spotui.data.preferences.ModulesPref
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.shell.immersiveGlassSurface
import com.music.spotui.ui.immersive.shell.rememberTabNavigation
import com.music.spotui.ui.immersive.shell.tabLabel
import com.music.spotui.ui.navigation.Routes

/** The sidebar's column: the panel, its margin included. */
val CupertinoSideBarWidth = 264.dp

private val PanelMargin = 12.dp
private val PanelShape = RoundedCornerShape(26.dp)
private val RowShape = RoundedCornerShape(10.dp)

/**
 * Large windows, Cupertino Look: the iPad sidebar of Apple Music. A wide translucent panel down the
 * left with a row per tab (a red glyph and its label, the current one on a rounded grey fill), then a
 * "Library" section of shortcuts to the library's kinds (Playlists, Artists, Albums, Songs), which
 * open the Library tab already filtered ([CupertinoNav]). The mini player stays at the bottom of the
 * page area ([com.music.spotui.ui.immersive.shell.ImmersiveBottomChrome]); hidden on the full-screen routes.
 */
@Composable
fun CupertinoSideBar(navController: NavController, modifier: Modifier = Modifier) {
    val tabs = rememberTabNavigation(navController)
    if (tabs.fullScreen) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { ModulesPref.init(context) }
    val modulesEnabled by ModulesPref.enabled.collectAsState()
    val items = remember(modulesEnabled) {
        if (modulesEnabled) listOf(Routes.Home, Routes.Search, Routes.Modules) else listOf(Routes.Home, Routes.Search)
    }
    val libraryKind = CupertinoNav.libraryKind
    val onLibrary = tabs.selectedTab == Routes.Library.route
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(CupertinoSideBarWidth)
            // The same veil the pages draw, so no bare backdrop strip shows beside them.
            .background(com.music.spotui.ui.navigation.pageBackgroundVeil(immersive = true))
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .padding(start = PanelMargin, top = PanelMargin, bottom = PanelMargin),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(CupertinoSideBarWidth - PanelMargin - 4.dp)
                .immersiveGlassSurface(PanelShape, liquid = false)
                .clip(PanelShape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 14.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { item ->
                SideRow(
                    label = tabLabel(item),
                    selected = tabs.selectedTab == item.route,
                    onClick = { tabs.onTabClick(item) },
                ) { tint -> Icon(painterResource(item.icon), null, tint = tint, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            // The Library as a section: its page (all kinds) first, then a shortcut per kind.
            Text(
                text = stringResource(R.string.cupertino_library),
                style = Immersive.Headline.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Immersive.TextSecondary),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).semantics { heading() },
            )
            SideRow(
                label = stringResource(R.string.cupertino_recently_added),
                selected = onLibrary && libraryKind == LibraryChip.ALL,
                onClick = { CupertinoNav.openLibrary(LibraryChip.ALL, tabs.onTabClick) },
            ) { tint -> Icon(painterResource(Routes.Library.icon), null, tint = tint, modifier = Modifier.size(22.dp)) }
            LibraryKinds.forEach { (kind, label, icon) ->
                SideRow(
                    label = stringResource(label),
                    selected = onLibrary && libraryKind == kind,
                    onClick = { CupertinoNav.openLibrary(kind, tabs.onTabClick) },
                ) { tint -> Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
            }
            // The page's own list (Downloaded) is not a kind of the library: its own screen.
            SideRow(
                label = stringResource(R.string.collection_downloaded),
                selected = false,
                onClick = { navController.navigate(Routes.Downloads.route) },
            ) { tint -> Icon(Icons.Rounded.Download, null, tint = tint, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** The library kinds the sidebar and the Library page's list offer, in Apple Music's order. */
internal val LibraryKinds: List<Triple<LibraryChip, Int, ImageVector>> = listOf(
    Triple(LibraryChip.PLAYLISTS, R.string.library_chip_playlists, Icons.Rounded.QueueMusic),
    Triple(LibraryChip.BY_ME, R.string.library_chip_by_me, Icons.Rounded.AccountCircle),
    Triple(LibraryChip.ARTISTS, R.string.library_chip_artists, Icons.Rounded.Person),
    Triple(LibraryChip.ALBUMS, R.string.library_chip_albums, Icons.Rounded.Album),
    Triple(LibraryChip.SONGS, R.string.library_chip_songs, Icons.Rounded.MusicNote),
    Triple(LibraryChip.PODCASTS, R.string.library_chip_podcasts, Icons.Rounded.Mic),
)

/** One row: a red glyph and a 17sp label, on a rounded grey fill while it is the current one. */
@Composable
private fun SideRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val fill = when {
        selected -> Color.White.copy(alpha = 0.14f)
        pressed -> Color.White.copy(alpha = 0.10f)
        hovered -> Color.White.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RowShape)
            .background(fill, RowShape)
            .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon(Immersive.Accent) }
        Text(
            text = label,
            style = Immersive.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
