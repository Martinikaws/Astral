/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/SquareApp.kt at 139441f (the floating tab bar, the
 * mini player accessory, the drag-to-expand player sheet and the glass chrome recipe).
 * Modified for Astral, 2026-11.
 *
 * Changes: only the shell is kept. Square's navigation, search field, Connect, YouTube Music and
 * settings are left out; the three tabs (Home, Search, Library) switch Astral's own immersive
 * screens through Astral's tab navigation, the player sheet is tied to Astral's PlayerSheet flag,
 * and the player runs on Astral's playback through SquaredAdapters. The bar folds with Astral's
 * ImmersiveChromeState, which the pages already scroll.
 *
 * Square's file carried the notice "Convx Project (C) 2026, Licensed under GPL-3.0".
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.adamglin.PhosphorIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import com.music.spotui.ui.immersive.sheets.ImmersiveSheetRow
import com.music.spotui.ui.immersive.sheets.ImmersiveSongOptionsSheet
import com.music.spotui.ui.components.shareLyricLines
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.House
import com.adamglin.phosphoricons.fill.MagnifyingGlass
import com.adamglin.phosphoricons.fill.MusicNotes
import com.music.spotui.ui.components.AddToPlaylistSheet
import com.music.spotui.ui.components.DevicesSheet
import com.music.spotui.ui.components.LyricsSourceSheet
import com.music.spotui.ui.immersive.shell.LocalImmersiveChrome
import com.music.spotui.ui.immersive.shell.rememberTabNavigation
import com.music.spotui.ui.immersive.squared.components.AmbientArtworkBackground
import com.music.spotui.ui.immersive.squared.glass.GlassComponent
import com.music.spotui.ui.immersive.squared.glass.GlassEffectConfig
import com.music.spotui.ui.immersive.squared.glass.GlassStyle
import com.music.spotui.ui.immersive.squared.glass.GlassWarmUp
import com.music.spotui.ui.immersive.squared.glass.LiquidBottomTab
import com.music.spotui.ui.immersive.squared.glass.LiquidBottomTabs
import com.music.spotui.ui.immersive.squared.glass.LocalAppBackdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.emptyBackdrop
import com.music.spotui.ui.immersive.squared.glass.LocalGlassEffectConfig
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.layerBackdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.rememberLayerBackdrop
import com.music.spotui.ui.immersive.squared.glass.floatingtabbar.FloatingTabBar
import com.music.spotui.ui.immersive.squared.glass.floatingtabbar.FloatingTabBarDefaults
import com.music.spotui.ui.immersive.squared.glass.floatingtabbar.LocalFloatingTabBarCollapseProgress
import com.music.spotui.ui.immersive.squared.glass.floatingtabbar.LocalFloatingTabBarInline
import com.music.spotui.ui.immersive.squared.glass.floatingtabbar.LocalGeometricFloatingTabBar
import com.music.spotui.ui.immersive.squared.glass.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.music.spotui.ui.immersive.squared.glass.liquidGlass
import com.music.spotui.ui.immersive.squared.player.FloatingMiniPlayer
import com.music.spotui.ui.immersive.squared.player.GlassFilm
import com.music.spotui.ui.immersive.squared.settings.SquaredGlassStore
import com.music.spotui.ui.immersive.squared.player.LocalGlassEnabled
import com.music.spotui.ui.immersive.squared.player.LocalPlayerFilm
import com.music.spotui.ui.immersive.squared.player.MiniPlayerHeight
import com.music.spotui.ui.immersive.squared.player.NowPlayingSheet
import com.music.spotui.ui.immersive.squared.player.PlayerScreen
import com.music.spotui.ui.immersive.squared.player.playerFilmTint
import com.music.spotui.ui.navigation.PlayerSheet
import com.music.spotui.ui.navigation.Routes
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** How the player settles when it is not being dragged. */
private val ExpandSpec = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

private val BarHeight = 61.dp
private val TabIcon = 26.dp
private val BarMargin = 16.dp
private val PlayerInk = Color(0xFFF7F8FA)
private val PlayerFilmFallback = Color(0xFF23232A)

/** The sunk lit slot: the dark side of Square's puck recipe at the default opacity slider. */
private val PuckWash = Color(0.131f, 0.131f, 0.131f, 0.535f)

/**
 * The Squared look's shell around Astral's pages: the glass tab bar with the mini player on it,
 * and the player that opens from the mini player. [content] is the page area (the nav host).
 */
@OptIn(UnstableApi::class)
@Composable
fun SquaredShell(
    navController: NavController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { SquaredCatalog.attach(context) }

    val model = rememberSquaredPlayer(navController)
    val playback = model.playback
    val tabs = rememberTabNavigation(navController)

    val pageBackdrop = rememberLayerBackdrop()
    val playerBackdrop = rememberLayerBackdrop()

    // The player sheet: Astral's PlayerSheet flag is the source of truth for open/closed, and the
    // drag drives the same Animatable.
    val expand = remember { Animatable(if (PlayerSheet.open.value) 1f else 0f) }
    val sheetOpen by PlayerSheet.open
    // While a finger is dragging the mini player the flag is left alone: writing it when the
    // drag crosses halfway would restart the effect below and animate under the finger. It is
    // written once, when the sheet is let go.
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(sheetOpen) { if (!dragging) expand.animateTo(if (sheetOpen) 1f else 0f, ExpandSpec) }
    LaunchedEffect(Unit) {
        snapshotFlow { (expand.targetValue > 0.5f) to dragging }.collect { (open, held) ->
            if (!held && PlayerSheet.open.value != open) PlayerSheet.open.value = open
        }
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.drop(1).collect { expand.animateTo(0f, ExpandSpec) }
    }

    // The bar folds with the chrome state the pages already scroll.
    val chrome = LocalImmersiveChrome.current
    val barScroll = rememberFloatingTabBarScrollConnection()
    // "Bar folds" in the Squared settings: off keeps the bar whole however the page scrolls.
    val glassStore = remember(context) { SquaredGlassStore.of(context) }
    val barFolds by glassStore.barFolds.collectAsState()
    val glassConfig by glassStore.config.collectAsState()
    LaunchedEffect(chrome?.minimized, barFolds) { if (chrome?.minimized == true && barFolds) barScroll.inline() else barScroll.expand() }
    LaunchedEffect(barScroll.isInline) { if (!barScroll.isInline) chrome?.expand() }

    var showDevices by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showLyricSources by remember { mutableStateOf(false) }
    var showSongMenu by remember(playback.mediaId, sheetOpen) { mutableStateOf(false) }

    // Colours: the playing record's own, from the extra catalogue when it answered.
    val art = model.art
    val ambientArt = art?.heroUrl ?: art?.coverUrl ?: playback.artworkUrl
    val ambientFoot by rememberArtworkFootColor(ambientArt)
    val ambientAccent by rememberArtworkColor(ambientArt.takeIf { art?.bgColor == null })
    val playerTone = when {
        art?.bgColor != null -> pageColorForHex(art.bgColor, lift = false)
        ambientFoot != null -> footToneFor(ambientFoot, lift = false)
        else -> pageColorFor(ambientAccent)
    }
    val playerFilm by animateColorAsState(
        targetValue = ambientAccent?.let(::playerFilmTint) ?: PlayerFilmFallback,
        animationSpec = tween(600),
        label = "playerFilm",
    )
    var clipColumns by remember { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(playback.mediaId) { clipColumns = emptyList() }

    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeUp = WindowInsets.ime.getBottom(density) > 0

    Box(modifier.fillMaxSize()) {
        // The page: recorded as the backdrop the glass bends.
        // In-page glass must not sample the layer it is drawn into (that recurses on the render
        // thread), so the page gets an empty backdrop: its glass degrades to tinted glass.
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            CompositionLocalProvider(LocalAppBackdrop provides remember { emptyBackdrop() }) { content() }
        }

        SquaredTheme(seed = ambientAccent, darkTheme = true) {
            CompositionLocalProvider(LocalAppBackdrop provides pageBackdrop) {
                // The glass the listener chose in the Squared settings (the page's profile and
                // sliders, laid over the material this shell was designed against).
                val chromeConfig = glassConfig
                val puckWash = PuckWash.copy(alpha = chromeConfig.puckOpacity.coerceIn(0f, 1f))
                // "Where the glass is" switches: a surface turned off keeps its film, not the lens.
                fun GlassEffectConfig.where(component: GlassComponent) =
                    if (isEnabledFor(component)) this else copy(style = GlassStyle.TRANSPARENT)
                val barShape = RoundedCornerShape(percent = 50)
                val miniGlass = Modifier.liquidGlass(
                    config = chromeConfig.where(GlassComponent.MINI_PLAYER),
                    shape = barShape,
                    highlightAlpha = 0.3f,
                )
                val glass = Modifier.liquidGlass(
                    config = chromeConfig.where(GlassComponent.NAV_BAR),
                    shape = barShape,
                    highlightAlpha = 0.3f,
                )
                val playerTravelPx = with(density) {
                    (LocalConfiguration.current.screenHeightDp.dp - MiniPlayerHeight).toPx()
                }
                GlassWarmUp(pageBackdrop)

                if (!tabs.fullScreen && !imeUp) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = (1f - expand.value * 3f).coerceIn(0f, 1f) },
                    ) {
                        val accessory: (@Composable androidx.compose.animation.SharedTransitionScope.(
                            Modifier,
                            androidx.compose.animation.AnimatedVisibilityScope,
                        ) -> Unit)? = if (playback.hasItem) {
                            { accessoryModifier, _ ->
                                CompositionLocalProvider(LocalGlassEffectConfig provides chromeConfig) {
                                    FloatingMiniPlayer(
                                        state = playback,
                                        positionMs = model.positionMs,
                                        remoteLabel = null,
                                        modifier = accessoryModifier
                                            .fillMaxWidth()
                                            .then(miniGlass)
                                            .draggable(
                                                state = rememberDraggableState { delta ->
                                                    scope.launch {
                                                        if (playerTravelPx > 0f) {
                                                            expand.snapTo((expand.value - delta / playerTravelPx).coerceIn(0f, 1f))
                                                        }
                                                    }
                                                },
                                                orientation = Orientation.Vertical,
                                                onDragStarted = { dragging = true },
                                                onDragStopped = { velocity ->
                                                    val target = when {
                                                        velocity < -800f -> 1f
                                                        velocity > 800f -> 0f
                                                        expand.value > 0.4f -> 1f
                                                        else -> 0f
                                                    }
                                                    PlayerSheet.open.value = target == 1f
                                                    dragging = false
                                                    scope.launch { expand.animateTo(target, ExpandSpec) }
                                                },
                                            ),
                                        inline = LocalFloatingTabBarInline.current,
                                        collapseProgress = LocalFloatingTabBarCollapseProgress.current?.invoke(),
                                        onClick = { scope.launch { expand.animateTo(1f, ExpandSpec) } },
                                        onTogglePlay = model.actions.playPause,
                                        onNext = model.actions.next,
                                        onPrevious = model.actions.previous,
                                        onSeek = model.actions.seekTo,
                                    )
                                }
                            }
                        } else {
                            null
                        }

                        val surfaceTint = ambientAccent
                        val barInk = inkTintedBy(surfaceTint)
                        val tabAccent = PlayerInk
                        val active = tabs.selectedTab
                        val tabRoutes = remember { listOf(Routes.Home, Routes.Search, Routes.Library) }
                        val labels = listOf(
                            stringResource(R.string.squared_tab_home),
                            stringResource(R.string.squared_tab_search),
                            stringResource(R.string.squared_tab_library),
                        )
                        val icons = listOf(PhosphorIcons.Fill.House, PhosphorIcons.Fill.MagnifyingGlass, PhosphorIcons.Fill.MusicNotes)
                        val here = tabRoutes.indexOfFirst { it.route == active }.coerceAtLeast(0)
                        val hereState = rememberUpdatedState(here)

                        CappedFontScale {
                            FloatingTabBar(
                                selectedTabKey = active,
                                scrollConnection = barScroll,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = BarMargin)
                                    .padding(bottom = navBar + 8.dp),
                                tabBarContentModifier = glass,
                                colors = FloatingTabBarDefaults.colors(
                                    backgroundColor = GlassFilm,
                                    accessoryBackgroundColor = GlassFilm,
                                    selectedTabBackgroundColor = puckWash,
                                    selectedStandaloneTabBackgroundColor = puckWash,
                                ),
                                sizes = FloatingTabBarDefaults.sizes(
                                    tabBarContentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
                                    tabExpandedContentPadding = PaddingValues(vertical = 8.dp, horizontal = 14.dp),
                                    tabInlineContentPadding = PaddingValues(14.dp),
                                    componentSpacing = 14.dp,
                                ),
                                expandedTabs = { tabsModifier, _ ->
                                    CompositionLocalProvider(LocalGlassEffectConfig provides chromeConfig) {
                                        LiquidBottomTabs(
                                            selectedTabIndex = { hereState.value },
                                            onTabSelected = { index -> tabs.onTabClick(tabRoutes[index]) },
                                            backdrop = pageBackdrop,
                                            tabsCount = tabRoutes.size,
                                            accentColor = tabAccent,
                                            containerColor = GlassFilm,
                                            indicatorColor = puckWash,
                                            height = BarHeight,
                                            lensDepth = 20.dp,
                                            modifier = tabsModifier,
                                            drawContainer = !LocalGeometricFloatingTabBar.current,
                                        ) {
                                            tabRoutes.forEachIndexed { i, route ->
                                                BarTab(labels[i], icons[i], active == route.route, tabAccent, barInk) {
                                                    tabs.onTabClick(route)
                                                }
                                            }
                                        }
                                    }
                                },
                                contentKey = listOf(active, tabAccent, barInk, labels),
                                expandedTabsHeight = BarHeight,
                                standaloneInExpanded = false,
                                tabsFillWidth = true,
                                inlineAccessory = accessory,
                                expandedAccessory = accessory,
                            ) {
                                tabRoutes.forEachIndexed { i, route ->
                                    tab(
                                        key = route.route,
                                        title = {
                                            Text(
                                                labels[i],
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (active == route.route) tabAccent else barInk,
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                icons[i],
                                                contentDescription = labels[i],
                                                tint = if (active == route.route) tabAccent else barInk,
                                                modifier = Modifier.size(TabIcon),
                                            )
                                        },
                                        onClick = { tabs.onTabClick(route) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (playback.hasItem) {
                    NowPlayingSheet(
                        progress = expand,
                        background = {
                            Box(Modifier.fillMaxSize().layerBackdrop(playerBackdrop)) {
                                AmbientArtworkBackground(
                                    artworkModel = ambientArt,
                                    coverAspect = 3f / 4f,
                                    tone = playerTone,
                                    toneFloor = 0.08f,
                                    scrimFloor = 0.22f,
                                    motion = false,
                                    columns = clipColumns,
                                )
                            }
                        },
                        expandedContent = {
                            CompositionLocalProvider(
                                LocalGlassEnabled provides true,
                                LocalInkOverride provides PlayerInk,
                                LocalContentColor provides PlayerInk,
                                LocalPlayerFilm provides playerFilm.copy(alpha = 0.5f),
                                LocalGlassEffectConfig provides chromeConfig.where(GlassComponent.PLAYER).copy(darkGlass = true, surfaceTintColor = playerFilm),
                            ) {
                                PlayerScreen(
                                    state = playback,
                                    positionMs = model.positionMs,
                                    onCollapse = { scope.launch { expand.animateTo(0f, ExpandSpec) } },
                                    onTogglePlay = model.actions.playPause,
                                    onNext = model.actions.next,
                                    onPrevious = model.actions.previous,
                                    onSeek = model.actions.seekTo,
                                    onToggleShuffle = model.actions.toggleShuffle,
                                    onCycleRepeat = model.actions.cycleRepeat,
                                    queue = model.queue,
                                    lyrics = model.lyrics,
                                    lyricsLoading = model.lyricsLoading,
                                    onOpenLyricsSources = { showLyricSources = true },
                                    canTranslate = model.canTranslate,
                                    translationOn = model.translationOn,
                                    translating = model.translating,
                                    onTranslate = model.onTranslate,
                                    onPlayQueueItem = model.queueActions.onPlay,
                                    queueActions = model.queueActions,
                                    backdrop = playerBackdrop,
                                    canvas = model.canvas,
                                    catalogPalette = art?.inkPalette.orEmpty(),
                                    onClipColumns = { clipColumns = it },
                                    coverHeroUrl = art?.heroUrl,
                                    coverMotionUrl = art?.motionUrl,
                                    coverSquareUrl = art?.coverUrl,
                                    coverPending = model.artPending,
                                    onOpenDevices = { showDevices = true },
                                    onAddToPlaylist = { if (model.song != null) showPlaylists = true },
                                    onOpenSongMenu = if (model.song != null) ({ showSongMenu = true }) else null,
                                    onOpenAlbum = if (playback.album.isNotBlank()) model.actions.openAlbum else null,
                                    alreadySaved = model.liked,
                                    inLikedSongs = model.liked,
                                    onToggleLike = model.actions.toggleLike,
                                    onAnotherDevice = model.onAnotherDevice,
                                )
                            }
                        },
                    )
                }
            }
        }

        // Astral's own sheets, over everything.
        if (showDevices) DevicesSheet { showDevices = false }
        val song = model.song
        if (showPlaylists && song != null) AddToPlaylistSheet(song, context, onDismiss = { showPlaylists = false })
        if (showLyricSources) LyricsSourceSheet(onDismiss = { showLyricSources = false })
        if (showSongMenu && song != null) {
            ImmersiveSongOptionsSheet(
                song = song,
                navController = navController,
                context = context,
                nowPlaying = true,
                onDismiss = { showSongMenu = false },
                extraRows = {
                    val lyrics = model.shareableLyrics
                    if (lyrics != null) {
                        val indices = lyrics.lines.indices.filter { index ->
                            lyrics.lines[index].text.trim().let { it.isNotEmpty() && it != "♪" }
                        }
                        if (indices.isNotEmpty()) ImmersiveSheetRow(Icons.Rounded.Share, stringResource(R.string.lyrics_share)) {
                            val index = if (lyrics.synced) {
                                indices.lastOrNull { lyrics.lines[it].timeMs <= model.positionMs.value } ?: indices.first()
                            } else indices.first()
                            showSongMenu = false
                            shareLyricLines(context, lyrics, index..index, song.title, song.singer)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RowScope.BarTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val tint = if (selected) accent else ink
    LiquidBottomTab(onClick) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(TabIcon))
        Text(label, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    }
}

/** White, carrying a colour's hue (Square's inkTintedBy, dark side only). */
@Composable
private fun inkTintedBy(color: Color?): Color {
    if (color == null) return Ink
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    if (hsv[1] < 0.06f) return Ink
    hsv[1] = hsv[1].coerceIn(0.5f, 1f)
    hsv[2] = 0.9f
    return androidx.compose.ui.graphics.lerp(Ink, Color(android.graphics.Color.HSVToColor(hsv)), 0.3f)
}
