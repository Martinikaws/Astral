package com.music.spotui.ui.screens

import com.music.spotui.ui.theme.tone
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppAccent

import com.music.spotui.ui.components.SkeletonKind
import com.music.spotui.ui.components.pressable

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import app.myspoti.engine.R
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.homeShortcuts
import com.music.spotui.data.entity.withoutDj
import com.music.spotui.data.entity.withoutPodcastShows
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.onHomeItemClick
import com.music.spotui.ui.components.prefetchHomeItem
import com.music.spotui.ui.components.prefetchHomeShortcuts
import com.music.spotui.ui.look.isSpotifusionLook
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.GridBackground
import com.music.spotui.ui.theme.SpotifusionCard
import com.music.spotui.ui.theme.SpotifusionGreen
import com.music.spotui.ui.theme.StarryCardOpaque
import com.music.spotui.ui.viewmodel.HomeViewModel


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(navController: NavController){

    val homeViewModel : HomeViewModel = viewModel()
    // An old feed is read again when Home shows again (see HomeViewModel.refreshIfStale).
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { homeViewModel.refreshIfStale() }
    val home by homeViewModel.home.collectAsState()
    val starryEnabled by StarryNightPref.enabled.collectAsState()
    val surfaceColor = if (starryEnabled && AppBackground == Color.Transparent) Color.Transparent else AppBackground

    Surface(
        color = surfaceColor,
        modifier = Modifier.fillMaxSize(),
    ) {
        val feed = (home as? Response.Success)?.data
        val hasFeed = feed != null && feed.sections.isNotEmpty()
        // The feed fades in over the placeholder instead of replacing it in one frame.
        com.music.spotui.ui.components.LoadedFade(!hasFeed) { waiting ->
        when {
            // Preferred: the real personalized Spotify home feed.
            !waiting && feed != null -> {
                HomeFeedContent(navController, feed, homeViewModel.facet.value) { homeViewModel.selectFacet(it) }
            }

            // No feed to show (loading, empty chip, error): keep the chips on screen
            // so the user can always switch back to "Tout".
            else -> androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeHeaderRow(navController, homeViewModel.facet.value) { homeViewModel.selectFacet(it) }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        // Fading out as the feed fades in: still the placeholder.
                        home is Response.Loading || hasFeed -> Loader(SkeletonKind.Home)
                        else -> androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (home is Response.Error) stringResource(R.string.home_error) else stringResource(R.string.home_empty),
                                color = AppText,
                                textAlign = TextAlign.Center,
                            )
                            if (home is Response.Error) androidx.compose.material3.TextButton(onClick = { homeViewModel.retry() }) {
                                Text(stringResource(R.string.home_retry), color = AppAccent)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun HomeFeedContent(navController: NavController, feed: HomeFeedModel, facet: String = "", onFacet: (String) -> Unit = {}) {
    // Mirror open.spotify.com exactly: sections render in the order the feed
    // returns them. The 2-column "shortcuts" grid is only used for the UNTITLED
    // section the web home starts with — if the feed leads with a titled section
    // ("Jump back in", "Made For …"), it renders as a titled carousel first,
    // not force-squeezed into the grid.
    // With podcasts off, their shows and episodes leave the feed (and a section left empty goes).
    // Kept between recompositions: a new list for every one of them would restart the rows below.
    val hideDj = com.music.spotui.data.preferences.HideDjPref.hidden
    val showPodcasts = com.music.spotui.data.api.Features.PODCASTS
    val sections = remember(feed, hideDj, showPodcasts) {
        val all = feed.sections.withoutPodcastShows()
        if (hideDj) all.withoutDj() else all
    }
    val gridSection = remember(sections) { sections.firstOrNull()?.takeIf { it.title.isBlank() } }
    val carousels = remember(sections, gridSection) { if (gridSection != null) sections.drop(1) else sections }
    val carouselKeys = remember(carousels) { uniqueKeys(carousels.map { "section:${it.title}" }) }
    // Liked Songs leads the shortcuts when Spotify's grid does not carry it.
    val likedName = stringResource(R.string.collection_liked_songs_title)
    val shortcuts = remember(gridSection, sections, facet, likedName) { homeShortcuts(gridSection?.items, sections.isEmpty(), facet, likedName) }
    // The shortcuts at the top load in the background, ready before a tap.
    androidx.compose.runtime.LaunchedEffect(shortcuts) { prefetchHomeShortcuts(shortcuts) }

    // Re-tapping the Home tab while on Home scrolls this list back to its top (official
    // behaviour): an animation over the existing items, so nothing is fetched again.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.spotui.ui.navigation.TabReselect.home.collect {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    val pageBg = AppBackground
    val headerFade = remember(pageBg) {
        if (pageBg == Color.Transparent) {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.42f),
                0.6f to Color.Black.copy(alpha = 0.16f),
                1f to Color.Transparent,
            )
        } else {
            Brush.verticalGradient(
                0f to pageBg,
                0.55f to pageBg.copy(alpha = 0.85f),
                1f to Color.Transparent,
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace()),
        modifier = Modifier.fillMaxSize()
    ) {
        // Sticky filter row: content scrolls underneath it, like the official app.
        stickyHeader(key = "home:header", contentType = "home:header") {
            // Fade (not a solid band): opaque or soft starry scrim under the status bar, transparent at the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerFade)
                    .statusBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                HomeHeaderRow(navController, facet, onFacet)
            }
        }
        shortcuts?.let { tiles ->
            item(key = "home:shortcuts", contentType = "home:shortcuts") {
                HomeShortcutGrid(navController, tiles)
            }
        }
        items(
            count = carousels.size,
            key = { carouselKeys[it] },
            contentType = { "home:section" },
        ) { i ->
            HomeFeedSection(navController, carousels[i])
        }
        // Sections from extensions (home.sections), after Spotify's own, each under its extension's name.
        item(key = "home:extensions", contentType = "home:extensions") {
            com.music.spotui.extensions.ExtensionBlocks(app.myspoti.extensions.Hook.HOME, app.myspoti.extensions.Surface.HOME, "{}")
        }
    }
}

/** Spotify-style top row: profile avatar on the left (opens Settings, like the
 *  official app's profile drawer), then the filter pills — one single row. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeHeaderRow(navController: NavController, facet: String, onFacet: (String) -> Unit) {
    var showProfile by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showProfile) ProfileSheet(onOpen = { route -> showProfile = false; navController.navigate(route) }) { showProfile = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.spotui.data.api.ProfileCache.ensure(context)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The row's 10 dp above and below sit in each child instead, so the avatar's tap area
        // takes them in (and the margin up to the separator) without moving anything.
        modifier = Modifier.fillMaxWidth(),
    ) {
        val avatarUrl = com.music.spotui.data.api.ProfileCache.imageUrl
        val initial = com.music.spotui.data.api.ProfileCache.name
            ?.trim()?.firstOrNull()?.uppercase() ?: "•"
        Box(
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showProfile = true }
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8622C)),
        ) {
            if (avatarUrl != null) {
                GlideImage(
                    model = avatarUrl,
                    contentScale = ContentScale.Crop,
                    contentDescription = stringResource(R.string.recently_played_user_profile),
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Text(
                    text = initial,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        }
        WhatsNewBell { navController.navigate(com.music.spotui.ui.navigation.Routes.WhatsNew.route) }
        val spotifusion = isSpotifusionLook()
        // B31: a separator between the profile and the options, reported missing from this
        // header (the sheets already carry one).
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 1.dp, height = 22.dp)
                .background(if (spotifusion) tone(0xFF3E3E3E) else Color.White.copy(alpha = 0.14f)),
        )
        // Scrolls sideways if the pills don't fit, like Spotify's.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            // Spotify's home chips. Once one is picked, as in Spotify: a close chip, the picked one,
            // and joined to it the chips it opens (Following: only what the user follows).
            val musicLabel = stringResource(R.string.recents_content_filter_music)
            val podcastsLabel = stringResource(R.string.ad_category_title_podcasts)
            val audiobooksLabel = stringResource(R.string.audiobooks_title)
            val labels = remember(musicLabel, podcastsLabel, audiobooksLabel) {
                mapOf(
                    "music-chip" to musicLabel,
                    "podcasts-chip" to podcastsLabel,
                    "audiobooks-chip" to audiobooksLabel,
                )
            }
            if (facet.isEmpty()) {
                HomePill(stringResource(R.string.filter_chip_all_label), selected = true) {}
                HomePill(labels.getValue("music-chip")) { onFacet("music-chip") }
                if (com.music.spotui.data.api.Features.PODCASTS) {
                    HomePill(labels.getValue("podcasts-chip")) { onFacet("podcasts-chip") }
                    HomePill(labels.getValue("audiobooks-chip")) { onFacet("audiobooks-chip") }
                }
            } else {
                val parent = com.music.spotui.data.entity.HomeSubChips.parentOf(facet) ?: facet
                val subs = com.music.spotui.data.entity.HomeSubChips.byParent[parent].orEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (spotifusion) tone(0xFF2A2A2A) else GridBackground)
                        .clickable { onFacet("") }
                        .padding(8.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = stringResource(R.string.filter_chip_all_label),
                        tint = AppText, modifier = Modifier.size(18.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    HomePill(labels[parent].orEmpty(), selected = true, shape = if (subs.isEmpty()) RoundedCornerShape(50) else JoinedStart) { onFacet(parent) }
                    subs.forEachIndexed { i, (id, label) ->
                        val shown = remember(id) { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = shown,
                            enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(),
                        ) {
                            HomePill(label, selected = facet == id, shape = if (i == subs.lastIndex) JoinedEnd else RoundedCornerShape(4.dp)) { onFacet(id) }
                        }
                    }
                }
            }
        }
        // Friend activity, out of the tab bar: at the end of the header, as Spotify has it.
        IconButton(onClick = { navController.navigate(com.music.spotui.ui.navigation.Routes.Friends.route) }) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_friends_big), contentDescription = stringResource(R.string.friends_title),
                tint = AppText, modifier = Modifier.size(24.dp))
        }
    }
}

/** The left half of two pills joined (a picked chip and the one it opened), and the right one. */
private val JoinedStart = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15)
private val JoinedEnd = RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50)

@Composable
private fun HomePill(label: String, selected: Boolean = false, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50), onClick: () -> Unit) {
    val spotifusion = isSpotifusionLook()
    val chipBg = if (selected) {
        if (spotifusion) SpotifusionGreen else AppAccent
    } else {
        if (spotifusion) tone(0xFF2A2A2A) else GridBackground
    }
    val chipInk = if (selected) {
        if (spotifusion || chipBg.luminance() > 0.45f) Color.Black else Color.White
    } else {
        AppText
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = chipInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private val SpotifusionCardShape = RoundedCornerShape(4.dp)
private val DefaultCardShape = RoundedCornerShape(8.dp)
private val HomeRowPadding = PaddingValues(10.dp, 0.dp)
private val ShortcutTitleStyle = androidx.compose.ui.text.TextStyle(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
private val SpotifusionSubtitleColor = Color(0xFFB3B3B3)

@Composable
private fun HomeShortcutGrid(navController: NavController, items: List<HomeItem>) {
    // Large windows: more tiles per row (two on phones).
    val cols = com.music.spotui.ui.components.windowSize().columns(2, 280.dp)
    val spotifusion = isSpotifusionLook()
    val tileShape = if (spotifusion) SpotifusionCardShape else DefaultCardShape
    val tileBg = if (spotifusion) SpotifusionCard else GridBackground
    val keys = remember(items) { uniqueKeys(items.map(::homeItemKeyBase)) }
    val rows = remember(items, cols) { items.chunked(cols) }
    Column(modifier = Modifier.padding(8.dp, 4.dp)) {
        var idx = 0
        rows.forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp),
            ) {
                rowItems.forEach { item ->
                    val tileKey = keys[idx++]
                    androidx.compose.runtime.key(tileKey) {
                        HomeShortcutTile(
                            navController = navController,
                            item = item,
                            tileShape = tileShape,
                            tileBg = tileBg,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(cols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeShortcutTile(
    navController: NavController,
    item: HomeItem,
    tileShape: androidx.compose.ui.graphics.Shape,
    tileBg: Color,
    modifier: Modifier = Modifier,
) {
    val onPressHint = remember(item) { { prefetchHomeItem(item) } }
    val onClick = remember(navController, item) { { onHomeItemClick(navController, item) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressable(0.96f, onPressHint = onPressHint, onClick = onClick)
            .height(48.dp)
            .clip(tileShape)
            .background(tileBg),
    ) {
        GlideImage(
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Crop,
            model = item.imageUrl,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Text(
            text = item.name,
            color = AppText,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            // Two balanced lines ("Daily / Mix 2") rather than a lone "2" under the rest.
            style = ShortcutTitleStyle,
            modifier = Modifier.padding(8.dp, 4.dp),
        )
    }
}

private fun homeItemKeyBase(item: HomeItem): String = when (item) {
    is HomeItem.Album -> if (item.id.isNotBlank()) "album:${item.id}" else "album:${item.name}|${item.imageUrl}"
    is HomeItem.Artist -> if (item.id.isNotBlank()) "artist:${item.id}" else "artist:${item.name}|${item.imageUrl}"
    is HomeItem.Playlist -> if (item.id.isNotBlank()) "playlist:${item.id}" else "playlist:${item.name}|${item.imageUrl}"
    is HomeItem.Show -> if (item.id.isNotBlank()) "show:${item.id}:${item.isEpisode}" else "show:${item.name}|${item.imageUrl}"
}

/** A key per entry that stays with it while the others around it change: [raw], with a counter on repeats. */
private fun uniqueKeys(raw: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return raw.map { k ->
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n == 1) k else "$k#$n"
    }
}

@Composable
private fun HomeFeedSection(navController: NavController, section: HomeSection) {
    Text(
        text = section.title,
        color = AppText,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp),
    )
    val keys = remember(section.items) { uniqueKeys(section.items.map(::homeItemKeyBase)) }
    LazyRow(contentPadding = HomeRowPadding) {
        items(
            count = section.items.size,
            key = { keys[it] },
            contentType = { section.items[it]::class },
        ) { i ->
            val item = section.items[i]
            val onClick = remember(navController, item) { { onHomeItemClick(navController, item) } }
            HomeFeedCard(item, onClick)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeFeedCard(item: HomeItem, onClick: () -> Unit) {
    val isArtist = item is HomeItem.Artist
    // Large windows: bigger covers, so a shelf is not a thin strip of phone cards.
    val cardSize = if (com.music.spotui.ui.components.windowSize().large) com.music.spotui.ui.components.WindowSize.LargeHomeCardSize else 150.dp
    val spotifusion = isSpotifusionLook()
    val cardShape = if (isArtist) CircleShape else if (spotifusion) SpotifusionCardShape else DefaultCardShape
    val dotColor = if (spotifusion) SpotifusionGreen else AppAccent
    val subtitleColor = if (spotifusion) SpotifusionSubtitleColor else AppMuted
    val subtitle = when (item) {
        is HomeItem.Album -> item.subtitle
        is HomeItem.Playlist -> item.subtitle
        is HomeItem.Artist -> stringResource(R.string.artist_subtitle)
        is HomeItem.Show -> item.subtitle
    }
    val onPressHint = remember(item) { { prefetchHomeItem(item) } }
    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.pressable(0.96f, onPressHint = onPressHint, onClick = onClick)
            .width(cardSize)
            .padding(6.dp),
    ) {
        Box {
            GlideImage(
                modifier = Modifier
                    .size(cardSize)
                    .clip(cardShape),
                contentScale = ContentScale.Crop,
                model = item.imageUrl,
                loading = placeholder(R.drawable.placeholder),
                failure = placeholder(R.drawable.placeholder),
                contentDescription = "",
            )
            if (item is HomeItem.Show && !item.isEpisode) {
                com.music.spotui.ui.components.NewEpisodeDot(item.id, dotColor, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            color = AppText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        // Always two lines tall: a long artist mix no longer makes one card taller than its row.
        Text(
            text = subtitle,
            color = subtitleColor,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            minLines = 2,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** The bell beside the avatar: opens "What's New". */
@Composable
private fun WhatsNewBell(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp)
            .size(34.dp),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Notifications,
            contentDescription = stringResource(R.string.whats_new_title),
            tint = AppText,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Profile sheet behind the avatar: account name and sign-out (in the Web Player). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
private fun ProfileSheet(onOpen: (route: String) -> Unit = {}, onDismiss: () -> Unit) {
    val name = com.music.spotui.data.api.ProfileCache.name.orEmpty()
    val avatar = com.music.spotui.data.api.ProfileCache.imageUrl
    // Opened in full: half-open, the sheet and its list both moved under the finger.
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isSpotifusionLook()) tone(0xFF282828) else StarryCardOpaque,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars))
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 16.dp),
        ) {
            // Name and picture open your public profile, as in Spotify.
            val myId = com.music.spotui.data.api.ProfileCache.id
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .clickable(enabled = myId != null) { myId?.let { onOpen(com.music.spotui.ui.navigation.profileRoute(it, name)) } }
                .padding(horizontal = 20.dp, vertical = 8.dp)) {
                com.music.spotui.ui.components.ProfileAvatar(url = avatar, name = name, size = 48.dp)
                Text(name.ifBlank { stringResource(R.string.spotify_account) }, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp))
                if (com.music.spotui.data.api.SupportReminder.level != com.music.spotui.data.api.SupportReminder.Level.NONE)
                    com.music.spotui.ui.components.SupporterBadge(Modifier.padding(start = 10.dp))
            }
            if (myId != null) Text(stringResource(R.string.view_profile), color = AppMuted, fontSize = 13.sp,
                modifier = Modifier.padding(start = 82.dp, bottom = 4.dp))
            androidx.compose.material3.HorizontalDivider(color = tone(0xFF3E3E3E), modifier = Modifier.padding(vertical = 8.dp))
            // Sound quality, downloads, data, battery, display: one page.
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { onOpen(com.music.spotui.ui.navigation.Routes.Settings.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.settings_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.settings_detail), color = AppMuted, fontSize = 12.sp)
            }
            // In-app recap: every listen on this phone, Spotify, downloads and local files.
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { onOpen(com.music.spotui.ui.navigation.Routes.Recap.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.recap_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.recap_detail), color = AppMuted, fontSize = 12.sp)
            }
            // The account's own listening history, as Spotify keeps it: it belongs beside the
            // recap (the local counter) because both answer "what did I listen to".
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { onOpen(com.music.spotui.ui.navigation.Routes.Recent.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.recents_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.recents_detail), color = AppMuted, fontSize = 12.sp)
            }
            // Third-party extensions (lyrics, audio, scripts): installed, enabled, repositories.
            val extRegistry by com.music.spotui.extensions.Extensions.registry.collectAsState()
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(com.music.spotui.ui.navigation.Routes.Extensions.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.extensions_title), color = AppText, fontSize = 16.sp)
                    val installed = extRegistry.extensions
                    Text(
                        if (installed.isEmpty()) stringResource(R.string.extensions_row_detail)
                        else stringResource(R.string.extensions_row_count, installed.size, installed.count { it.enabled }),
                        color = AppMuted, fontSize = 12.sp,
                    )
                }
                androidx.compose.material3.Icon(Icons.Filled.Extension, null, tint = AppMuted,
                    modifier = Modifier.padding(start = 12.dp).size(22.dp))
            }
            // The Spotify account page (plan, privacy, devices), in the app's signed-in browser.
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    com.music.spotui.ui.screens.AccountBrowser.open(com.music.spotui.di.Graph.app, com.music.spotui.ui.screens.AccountBrowser.ACCOUNT_URL)
                }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.account_page_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.account_page_detail), color = AppMuted, fontSize = 12.sp)
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            // About: version, Discord, source code and Ko-fi.
            var about by remember { androidx.compose.runtime.mutableStateOf(false) }
            if (about) com.music.spotui.ui.components.AboutSheet(onDismiss = { about = false })
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { about = true }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.creator_biography_section_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.about_detail), color = AppMuted, fontSize = 12.sp)
            }
            var perks by remember { androidx.compose.runtime.mutableStateOf(false) }
            if (perks) com.music.spotui.ui.components.SupporterSheet(onDismiss = { perks = false })
            if (com.music.spotui.data.api.SupportReminder.PERKS_ENABLED) androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { perks = true }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.supporter_perks_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.supporter_perks_detail), color = AppMuted, fontSize = 12.sp)
            }
            // The other Spotify accounts kept on the phone: switch, add, remove.
            var accounts by remember { androidx.compose.runtime.mutableStateOf(false) }
            if (accounts) com.music.spotui.ui.components.AccountsSheet(onDismiss = { accounts = false }, onLeave = { accounts = false; onDismiss() })
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { accounts = true }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.accounts_switch_row), color = AppText, fontSize = 16.sp)
                Text(com.music.spotui.ui.components.switchAccountDetail(), color = AppMuted, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.connect_device_menu_logout),
                color = AppText, fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        com.music.spotui.di.SessionActions.logout(com.music.spotui.di.Graph.app)
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}


