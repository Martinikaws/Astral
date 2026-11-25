/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed and the screen's inputs reshaped for Astral. Removed: the YouTube
 * Music blocks and service switch, the signed-out/connecting/offline screens (Astral signs in
 * elsewhere, so a failed feed shows its message and a retry), the app lockup, the per-service
 * chip row, and Square's local-files filter. The header takes its title as a parameter (Spotify's
 * greeting, or "Home") and shows the account name above it. The podcasts chip appears only when
 * the feed has podcast rows. Types are the Squared* ones in squared/model; the artwork is
 * SquaredArtwork. Fed by SquaredHomeHost.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.*
import com.music.spotui.ui.immersive.squared.components.*
import com.music.spotui.ui.immersive.squared.glass.*
import com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.*
import com.music.spotui.ui.immersive.squared.glass.shapes.*
import com.music.spotui.ui.immersive.squared.glass.morph.*
import com.music.spotui.ui.immersive.squared.player.*
import com.music.spotui.ui.immersive.squared.model.*
import com.music.spotui.ui.immersive.squared.screens.*

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Icon
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Bell
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import java.util.Calendar

/**
 * What the feed is showing.
 *
 * Chips rather than a second tab bar: they sit inside the page, under the name,
 * and "Tutto" is genuinely the whole thing rather than a fourth view. The
 * sections keep their order in every one of them, so switching never rearranges
 * what stays on screen.
 *
 * Each chip is a part of Spotify's own home: what it made for the account,
 * what the editors put there, and the rows about an artist; see
 * TabContents.chipOf. There used to be a chip for the library and one for new
 * releases, which were two other tabs of this app said again on this one.
 */
private enum class Feed(@StringRes val label: Int, val chip: com.music.spotui.ui.immersive.squared.model.TabContents.Chip?) {
    ALL(R.string.squared_feed_all, null),
    FOR_YOU(R.string.squared_feed_for_you, com.music.spotui.ui.immersive.squared.model.TabContents.Chip.FOR_YOU),
    ARTISTS(R.string.squared_artists, com.music.spotui.ui.immersive.squared.model.TabContents.Chip.ARTISTS),
    /**
     * Spotify's own podcast page rather than a part of this one: its shows
     * and episodes come from a home of their own, asked for when the chip is.
     */
    PODCASTS(R.string.squared_podcasts, null),
}

/**
 * The home page.
 *
 * Rewritten from scratch rather than adjusted. The previous version had grown
 * from a plain Material list — filled buttons, section headings, a progress
 * spinner in the middle of the page — and adding glass cards on top of it left
 * two design languages sharing a screen. Everything here is drawn on the same
 * material as the bars and the player: no Material containers, no elevation, no
 * accent-filled buttons.
 */
@Composable
fun SquaredHomeScreen(
    state: SquaredUiState,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
    onOpenPlaylist: (CatalogPlaylist) -> Unit,
    /** URIs most recently opened first. */
    playlistOrder: List<String>,
    recent: List<CatalogTrack>,
    onPlayRecent: (List<CatalogTrack>, Int) -> Unit,
    /** Plays a row of the feed, told which row it was so the player can say so. */
    onPlayFeed: (List<CatalogTrack>, Int, String) -> Unit,
    feed: SquaredFeedState,
    onOpenItem: (SearchItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNews: (() -> Unit)? = null,
    /** The page's title: Spotify's greeting where the feed carried one. */
    title: String,
    /** Friend activity, for the faces beside the account picture. */
    friends: List<FriendListen> = emptyList(),
    onOpenFriends: () -> Unit = {},
    /** The layer the glass on this page refracts. */
    backdrop: Backdrop,
    /** Spotify's own personalised shelves, empty when the feed said nothing. */
    shelves: List<HomeShelf> = emptyList(),
    /** The shelves are on their way; their outline stands in until then. */
    shelvesLoading: Boolean = false,
    /** Spotify's podcast home, for the chip of it; asked for with [onWantPodcasts]. */
    podcastShelves: List<HomeShelf> = emptyList(),
    podcastsLoading: Boolean = false,
    onWantPodcasts: () -> Unit = {},
    /** An episode tile: played, not opened. */
    onPlayEpisode: (CatalogPlaylist, String) -> Unit = { _, _ -> },
) {
    when (state) {
        SquaredUiState.Connecting,
        SquaredUiState.Loading,
        -> Centered { CircularProgressIndicator(color = InkDim, strokeWidth = 2.dp) }

        // Astral signs in elsewhere, so a signed-out account reads as the page failing to load.
        SquaredUiState.LoggedOut,
        is SquaredUiState.Failed,
        -> Centered {
            Text(
                (state as? SquaredUiState.Failed)?.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            GlassAction(stringResource(R.string.squared_retry), backdrop, onRetry)
        }

        is SquaredUiState.Ready -> {
            // Saved rather than remembered, like the scroll position below it:
            // both are undone by leaving for a playlist and coming back, and a
            // page that forgets which shelf you were reading is the same
            // complaint as one that forgets where you were in it.
            var filterName by rememberSaveable { mutableStateOf(Feed.ALL.name) }
            // Only the chips that have something under them, and none at all
            // when Spotify's home did not answer: the page is then this app's
            // own sections, which is one view with nothing to filter.
            val chips = remember(shelves, podcastShelves, podcastsLoading) {
                listOf(Feed.ALL) + Feed.entries.filter { entry ->
                    entry.chip != null &&
                        shelves.any { com.music.spotui.ui.immersive.squared.model.TabContents.chipOf(it) == entry.chip }
                } + listOfNotNull(Feed.PODCASTS.takeIf { podcastShelves.isNotEmpty() || podcastsLoading })
            }
            val filter = chips.firstOrNull { it.name == filterName } ?: Feed.ALL
            LaunchedEffect(filter) { if (filter == Feed.PODCASTS) onWantPodcasts() }
            // Read once here rather than inside the rows: the label travels
            // with the play so the player can say where the song came from,
            // and that is a plain string by the time it leaves this screen.
            val onRepeatLabel = stringResource(R.string.squared_on_repeat)
            val classicsLabel = stringResource(R.string.squared_all_time_favourites)
            // Spotify's rootlist arrives in the order the account added them,
            // which for an old account is close to arbitrary — the playlist
            // opened every day can sit thirtieth.
            val playlists = remember(state.playlists, playlistOrder) {
                // The phone's own music belongs to the library rather than to
                // this page: home is what the service put together for the
                // listener, and a folder of files is not that.
                state.playlists
                    .filterNot { false }
                    .sortedByRecentlyOpened(playlistOrder)
            }
            val listState = rememberLazyListState()

            // How far the header has collapsed, 0 to 1.
            //
            // Read from the list rather than driven by a nested-scroll
            // connection: the header is a sibling of the list, not part of it,
            // so it never consumes scroll and the list keeps its own fling
            // untouched. Past the first item the header is simply fully
            // collapsed — asking for the exact offset of something scrolled far
            // off screen means measuring items that no longer exist.
            val collapse by remember {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0) 1f
                    else (listState.firstVisibleItemScrollOffset / COLLAPSE_DISTANCE_PX)
                        .coerceIn(0f, 1f)
                }
            }

            // Back to the top when the view changes. The lists have nothing in
            // common, so keeping the old offset drops the new one in the middle
            // of itself.
            //
            // The first run is skipped, and that is the whole point: an effect
            // keyed on the filter also runs when this screen is composed, which
            // is every time the listener comes back from a playlist. The page
            // they left halfway down was being scrolled to the top under them
            // for a filter that had not changed at all.
            var scrolledFor by rememberSaveable { mutableStateOf(filter.name) }
            LaunchedEffect(filter) {
                if (scrolledFor != filter.name) {
                    scrolledFor = filter.name
                    listState.scrollToItem(0)
                }
            }

            Column(Modifier.fillMaxSize()) {
                Header(
                    name = state.displayName,
                    avatarUrl = state.avatarUrl,
                    title = title,
                    collapse = { collapse },
                    // The chip that is lit is the one that was tapped. It used
                    // to follow whichever section the scroll had reached, which
                    // read as a control changing itself: the chips look like a
                    // filter, so a lit one has to mean "this is what you are
                    // looking at because you asked for it".
                    highlighted = filter,
                    backdrop = backdrop,
                    topPadding = contentPadding.calculateTopPadding(),
                    onFilter = { filterName = it.name },
                    chips = chips,
                    showFilters = chips.size > 1,
                    onOpenSettings = onOpenSettings,
                    onOpenNews = onOpenNews,
                    friends = friends,
                    onOpenFriends = onOpenFriends,
                )

                AnimatedContent(
                    targetState = filter,
                    transitionSpec = {
                        // Short and vertical: the sections do not move sideways
                        // when they are filtered, so neither should the change.
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 14 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "feed",
                ) { current ->
                // "Everything" means Spotify's own home when there is one.
                //
                // The sections below it are this app standing in for a home
                // page it could not read: what the account plays most, what it
                // came back to, new releases. Once the real one arrives they
                // are a second, worse answer to the same question, printed
                // underneath the first, so they stand in only while there is
                // no real one; the chips are the real one's.
                // Not while Spotify's are on their way: shown first and then
                // replaced, the stand-in page was a page that changed under
                // the listener a few seconds after opening.
                val ownFeed = shelves.isEmpty() && !shelvesLoading
                val showOwn = current == Feed.ALL && ownFeed
                val showReleases = showOwn
                val showArtists = showOwn
                val showLibrary = showOwn
                val showForYou = showOwn
                val filter = current

                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        // Fades the first rows out under the header instead of
                        // cutting them off square. DstIn needs a layer of its
                        // own, or the mask would erase the page behind the list
                        // as well.
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            if (collapse > 0.01f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        (FADE_FRACTION * collapse) to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                    state = listState,
                    // The header already covers the status bar, so only the
                    // bottom inset is left for the list.
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                // Why the page is shorter than usual, before the page.
                //
                // Added to the list only while it is actually offline, not
                // added always and left to draw nothing: an item of zero height
                // is still an item, so the first scrolled pixel moved the list
                // past it and the header — which reads its collapse off the
                // first visible item — snapped shut instead of easing.
                // Spotify's own shelves come first, because they are what the
                // listener recognises as their home and the only rows here that
                // this app could not have built itself. What the account has
                // been playing follows: it is worth having, and it is also the
                // same answer for weeks at a time.
                //
                // Every feed section keeps its place while it is still being
                // fetched. The account's playlists and its recent tracks are
                // held on the device and draw at once; everything else is a Web
                // API round trip a second or two behind, and without a
                // placeholder the page arrived in two halves and shifted under
                // whatever was being read.
                run {
                    val visible = when (filter) {
                        Feed.ALL -> shelves
                        Feed.PODCASTS -> podcastShelves
                        else -> shelves.filter { com.music.spotui.ui.immersive.squared.model.TabContents.chipOf(it) == filter.chip }
                    }
                    // Their outline while they come, and a fade as they land
                    // rather than the rows appearing in one frame.
                    if (visible.isEmpty() &&
                        ((shelvesLoading && filter == Feed.ALL) || (podcastsLoading && filter == Feed.PODCASTS))
                    ) {
                        items(SHELF_OUTLINES, key = { "shelf-outline-$it" }, contentType = { "skeleton" }) {
                            Box(Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = tween(200))) {
                                SkeletonSection(tileWidth = 152.dp, tileHeight = 152.dp)
                            }
                        }
                    }
                    // Keyed by place as well as title: Spotify can send two rows
                    // with the same title, and a key used twice crashes the list.
                    visible.forEachIndexed { index, shelf ->
                        item(key = "shelf-heading-$index-${shelf.title}", contentType = "shelf") {
                            Box(Modifier.animateItem(fadeInSpec = tween(320), placementSpec = null, fadeOutSpec = null)) {
                                // The first sits just under the chips, which
                                // carry the header's margin already.
                                Heading(shelf.title, top = if (index == 0) 12.dp else 30.dp)
                            }
                        }
                        item(key = "shelf-$index-${shelf.title}", contentType = "shelf") {
                            Box(Modifier.animateItem(fadeInSpec = tween(320), placementSpec = null, fadeOutSpec = null)) {
                                Carousel(shelf.items, key = { it.uri }) { entry ->
                                    PlaylistTile(entry) {
                                        if (entry.uri.startsWith("spotify:episode:")) {
                                            onPlayEpisode(entry, shelf.title)
                                        } else {
                                            onOpenPlaylist(entry)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showForYou && feed.topTracks.isEmpty() && feed.loading) {
                    item(contentType = "skeleton") {
                        SkeletonSection(tileWidth = 152.dp, tileHeight = 152.dp)
                    }
                }

                if (showForYou && feed.topTracks.isNotEmpty()) {
                    item(contentType = Feed.FOR_YOU.name) { Heading(stringResource(R.string.squared_on_repeat)) }
                    item(contentType = Feed.FOR_YOU.name) {
                        TrackRow(feed.topTracks) { index ->
                            onPlayFeed(feed.topTracks, index, onRepeatLabel)
                        }
                    }
                }

                if (showForYou && feed.jumpBackIn.isEmpty() && feed.loading) {
                    item(contentType = "skeleton") {
                        SkeletonSection(tileWidth = 152.dp, tileHeight = 152.dp)
                    }
                }

                if (showForYou && feed.jumpBackIn.isNotEmpty()) {
                    item(contentType = Feed.FOR_YOU.name) { Heading(stringResource(R.string.squared_jump_back_in)) }
                    item(contentType = Feed.FOR_YOU.name) {
                        Carousel(feed.jumpBackIn, key = { it.uri }) { item ->
                            FeedTile(item) { onOpenItem(item) }
                        }
                    }
                }

                if (showLibrary) {
                    item(contentType = "library") { Heading(stringResource(R.string.squared_your_playlists)) }
                    item(contentType = "library") {
                        Carousel(
                            playlists.take(if (showLibrary) LIBRARY_SIZE else CAROUSEL_SIZE),
                            key = { it.uri },
                        ) { playlist ->
                            PlaylistTile(playlist) { onOpenPlaylist(playlist) }
                        }
                    }
                }

                if (showReleases && feed.newReleases.isEmpty() && feed.loading) {
                    item(contentType = "skeleton") {
                        SkeletonSection(tileWidth = 0.dp, tileHeight = 200.dp, card = true)
                    }
                }

                if (showReleases && feed.newReleases.isNotEmpty()) {
                    item(contentType = "releases") { Heading(stringResource(R.string.squared_feed_releases)) }
                    // Cards rather than another row of thumbnails. A carousel
                    // says "here is a list, pick one"; this is meant to be
                    // looked at, so each release gets the width of the page and
                    // the cover carries it.
                    items(
                        feed.newReleases.take(FEED_SIZE),
                        key = { it.uri },
                        contentType = { "releases" },
                    ) { item ->
                        FeedCard(item) { onOpenItem(item) }
                    }
                }

                // Novità, but only from artists the account listens to. Sits
                // under the catalogue-wide releases on purpose: it is the
                // narrower of the two and the one worth reaching first.
                if ((showReleases || showForYou) && feed.fromYourArtists.isNotEmpty()) {
                    item(contentType = Feed.FOR_YOU.name) { Heading(stringResource(R.string.squared_new_from_your_artists)) }
                    item(contentType = Feed.FOR_YOU.name) {
                        Carousel(feed.fromYourArtists, key = { it.uri }) { item ->
                            FeedTile(item) { onOpenItem(item) }
                        }
                    }
                }

                if (showArtists && feed.topArtists.isEmpty() && feed.loading) {
                    item(contentType = "skeleton") {
                        SkeletonSection(tileWidth = 120.dp, tileHeight = 120.dp, round = true)
                    }
                }

                if (showArtists && feed.topArtists.isNotEmpty()) {
                    item(contentType = Feed.ARTISTS.name) { Heading(stringResource(R.string.squared_listening_artists)) }
                    // A grid rather than a carousel. A row shows three artists
                    // and hides the rest behind a sideways scroll nobody makes
                    // twice; the same list down the page is read at a glance.
                    // Rows of a list rather than a nested grid, which cannot go
                    // inside a scrolling column without being given a height.
                    items(
                        feed.topArtists.chunked(ARTIST_COLUMNS),
                        key = { row -> row.first().uri },
                        contentType = { Feed.ARTISTS.name },
                    ) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { artist ->
                                ArtistCell(artist, Modifier.weight(1f)) { onOpenItem(artist) }
                            }
                            // The last row keeps the others\' spacing instead of
                            // stretching two artists across the page.
                            repeat(ARTIST_COLUMNS - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (recent.isNotEmpty() && filter == Feed.ALL) {
                    item(contentType = "library") { Heading(stringResource(R.string.squared_play_again)) }
                    item(contentType = "library") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            itemsIndexed(recent, key = { _, track -> track.uri }) { index, track ->
                                TrackTile(track) { onPlayRecent(recent, index) }
                            }
                        }
                    }
                }

                // Last, because it is the least of a surprise: everything above
                // changes month to month, this is the account's own constants.
                if (showForYou && feed.allTimeTracks.isNotEmpty()) {
                    item(contentType = Feed.FOR_YOU.name) { Heading(stringResource(R.string.squared_all_time_favourites)) }
                    item(contentType = Feed.FOR_YOU.name) {
                        TrackRow(feed.allTimeTracks) { index ->
                            onPlayFeed(feed.allTimeTracks, index, classicsLabel)
                        }
                    }
                }

                item(contentType = "tail") { Box(Modifier.height(24.dp)) }
                }
                }
            }
        }
    }
}

/**
 * Name, picture and filters, always on screen.
 *
 * It shrinks rather than sliding away: the filter chips are a control, and a
 * control that has to be scrolled back to before it can be used may as well not
 * be there. What collapses is only the part that is decoration — the greeting
 * line and the size of the name.
 */
@Composable
private fun Header(
    modifier: Modifier = Modifier,
    name: String,
    avatarUrl: String?,
    /** The page's name, at the size the app's own name was in Square. */
    title: String,
    /**
     * How far collapsed, as a lambda rather than a value.
     *
     * Deliberate, and the whole reason this scrolls smoothly: a `Float`
     * parameter is read when the header is composed, so every pixel of scroll
     * invalidated the composable that produced it — the home page, list content
     * lambdas included — several dozen times a second. Read inside `layout` and
     * `graphicsLayer` blocks instead, the same movement costs a measure pass and
     * no recomposition at all.
     */
    collapse: () -> Float,
    /** The chip drawn as active: the one that was tapped. */
    highlighted: Feed,
    backdrop: Backdrop,
    topPadding: Dp,
    onFilter: (Feed) -> Unit,
    chips: List<Feed> = Feed.entries.toList(),
    onOpenSettings: () -> Unit,
    onOpenNews: (() -> Unit)? = null,
    /** Who the account follows and what they play; empty hides the control. */
    friends: List<com.music.spotui.ui.immersive.squared.model.FriendListen> = emptyList(),
    onOpenFriends: () -> Unit = {},
    /**
     * False on a page with no feed to filter.
     *
     * The chips are the one part of this header that is Spotify's: they pick
     * between sections built out of that account's data, and YouTube's page is
     * whatever shelves the service itself sent.
     */
    showFilters: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            // Seats the header on the page instead of leaving it floating over
            // whatever the list has scrolled underneath it.
            //
            // In the page's own colour rather than in black. Black was right
            // while every page was a tinted wash and a little more darkness at
            // the top read as depth; over a neutral ground it is a grey band
            // across the top of every screen, and on the light side it is a
            // smudge. Fading from the ground colour does the same job — the
            // rows disappear under the header instead of running into it — and
            // leaves no band when there is nothing scrolled under it.
            .background(
                Brush.verticalGradient(
                    0f to com.music.spotui.ui.immersive.squared.pageGround(),
                    1f to Color.Transparent,
                ),
            )
            .padding(top = topPadding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp)
                // The top padding, as a layout pass: `padding()` takes a Dp,
                // which would have to be computed while composing.
                .layout { measurable, constraints ->
                    val top = lerp(18.dp, 2.dp, collapse()).roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height + top) {
                        placeable.place(0, top)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The account, small and above the title. Height goes with the alpha, so the
                // collapsed header is a bar rather than a bar with a blank line in it.
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = ((1f - collapse()) * placeable.height).toInt()
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            clip = true
                            alpha = (1f - collapse() * 1.6f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                )
                // It shrinks a little as the header collapses rather than leaving, because a bar
                // with nothing in it says nothing about where you are.
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .graphicsLayer {
                            val scale = 1f - 0.22f * collapse()
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                )
            }

            // Falls back to the generated cover keyed on the name, which is what
            // every other missing image in the app gets rather than a grey
            // circle.
            // The way into the settings. They have no tab of their own — see
            // SettingsScreen — and the account picture is where anyone looks for
            // them anyway.
            // The people the account follows, as a huddle of faces.
            //
            // Beside the account's own picture because that is what it is about
            // — who is listening — and because a list nobody has asked for does
            // not deserve a tab. Absent rather than empty when nobody is
            // playing: a control that opens onto nothing is worse than no
            // control.
            if (friends.isNotEmpty()) {
                FriendFaces(friends, onOpenFriends)
            }

            if (onOpenNews != null) {
                LiquidButton(onClick = onOpenNews, backdrop = backdrop, contentHeight = 46.dp, contentPadding = 11.dp) {
                    Icon(PhosphorIcons.Regular.Bell, stringResource(R.string.whats_new_title), tint = Ink)
                }
            }

            val profileLabel = stringResource(R.string.recently_played_user_profile)
            com.music.spotui.ui.immersive.squared.components.ProfilePicture(
                url = avatarUrl,
                name = name,
                size = 46.dp,
                modifier = Modifier
                    .padding(start = 14.dp)
                    // Measured from the scroll rather than sized in
                    // composition; see the note on `collapse`.
                    .layout { measurable, _ ->
                        val side = lerp(46.dp, 36.dp, collapse()).roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.place(0, 0) }
                    }
                    .clip(CircleShape)
                    .pressable(onOpenSettings, pressedScale = 0.90f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = profileLabel
                        role = Role.Button
                    }
                    .softShadow(CircleShape, elevation = 10.dp),
                decodeSize = 46.dp,
            )
        }

        // The chips carry the header's bottom margin with them; without them
        // the list would start against the app's own name.
        if (showFilters) FilterRow(highlighted, chips, backdrop, onFilter) else Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FilterRow(selected: Feed, chips: List<Feed>, backdrop: Backdrop, onSelect: (Feed) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).then(com.music.spotui.ui.immersive.squared.components.SideScrollNoBack),
    ) {
        items(chips, key = { it.name }) { entry ->
            com.music.spotui.ui.immersive.squared.components.FilterChip(
                backdrop = backdrop,
                label = stringResource(entry.label),
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/**
 * One release, the full width of the page.
 *
 * The caption sits in a pane of glass over the cover rather than under it, the
 * way the bars over the rest of the app do. Text straight on artwork needs a
 * gradient to survive a light cover, and a gradient large enough to do that
 * ends up dimming the picture it is supposed to be showing.
 */
@Composable
private fun FeedCard(item: SearchItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .softShadow(shape, elevation = 26.dp, spot = 0.55f)
            .clip(shape)
            .pressable(onClick, pressedScale = 0.98f),
    ) {
        // The same cover twice, and that is the point.
        //
        // Spotify serves album art at 640px and no larger, so a cover stretched
        // across a 1080px-wide card is upscaled by nearly two and looks soft —
        // which is exactly the "low quality covers" this replaced. The
        // background is allowed to be soft, so it takes the full width; the copy
        // that has to be sharp is drawn well under its native size.
        //
        // Softened by decoding it tiny and letting the upscale blur it, rather
        // than by `Modifier.blur`. That modifier renders into a layer of its own
        // which is not bound by this box's clip, so it painted a hard black
        // rectangle past the card's rounded corners and up into the header.
        SquaredArtwork(
            url = item.artworkUrl,
            title = item.title,
            modifier = Modifier.matchParentSize(),
            corner = 0.dp,
            decodeSize = 24.dp,
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.42f)))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SquaredArtwork(
                url = item.artworkUrl,
                title = item.title,
                modifier = Modifier
                    .size(COVER_SIZE)
                    .softShadow(RoundedCornerShape(18.dp), elevation = 24.dp, spot = 0.5f),
                corner = 18.dp,
                decodeSize = COVER_SIZE,
            )
            Text(
                item.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 18.dp),
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** How many artists fit across the page without the names going to three lines. */
private const val ARTIST_COLUMNS = 3

/**
 * One cell of the artist grid: the same round portrait, sized by its column.
 *
 * Separate from [ArtistTile], which is fixed at the carousel's width because a
 * horizontal row has no column to take its width from.
 */
@Composable
private fun ArtistCell(artist: SearchItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SquaredArtwork(
            url = artist.artworkUrl,
            title = artist.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .softShadow(CircleShape, elevation = 14.dp),
            corner = 200.dp,
            decodeSize = 160.dp,
        )
        Text(
            artist.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Round, because that is how every music app has drawn an artist for a decade. */
@Composable
private fun ArtistTile(artist: SearchItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(110.dp)
            .pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SquaredArtwork(
            url = artist.artworkUrl,
            title = artist.title,
            modifier = Modifier
                .size(110.dp)
                .softShadow(CircleShape, elevation = 14.dp),
            corner = 55.dp,
            decodeSize = 110.dp,
        )
        Text(
            artist.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun PlaylistTile(playlist: CatalogPlaylist, onClick: () -> Unit) {
    // A portrait among covers: the one shape difference a shelf of people
    // needs, and the reason an artist row reads as people at a glance.
    val corner = if (playlist.isArtist) 76.dp else 20.dp
    Column(
        Modifier
            .width(152.dp)
            .pressable(onClick),
    ) {
        SquaredArtwork(
            url = playlist.artworkUrl,
            title = playlist.name,
            modifier = Modifier
                .size(152.dp)
                .softShadow(RoundedCornerShape(corner), elevation = 18.dp),
            corner = corner,
        )
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            // One line where there is something written under it, or the two
            // together push the next shelf off its own baseline.
            maxLines = if (playlist.subtitle == null) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        playlist.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun TrackTile(track: CatalogTrack, onClick: () -> Unit) {
    Column(
        // No clip on the column: it would cut off the artwork's drop shadow,
        // which spreads wider than the tile itself.
        Modifier
            .width(128.dp)
            .pressable(onClick),
    ) {
        SquaredArtwork(
            url = track.artworkUrl,
            title = track.name,
            modifier = Modifier
                .size(128.dp)
                .softShadow(RoundedCornerShape(18.dp), elevation = 14.dp),
            corner = 18.dp,
            decodeSize = 128.dp,
        )
        Text(
            track.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = InkDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

/** A row of tracks that play where they are tapped. */
/**
 * A section's shape, before the section has arrived.
 *
 * Deliberately still — no shimmer. A sweep across half the page draws the eye
 * to the part of it that has nothing to look at, and these are gone within a
 * second or two anyway. What they are for is the layout: holding the space
 * means the rows that land later land in the place they already occupied.
 */
@Composable
private fun SkeletonSection(
    tileWidth: Dp,
    tileHeight: Dp,
    round: Boolean = false,
    card: Boolean = false,
) {
    val fill = Ink.copy(alpha = 0.07f)
    Column(Modifier.padding(top = 22.dp)) {
        // Stands in for the heading.
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(fill),
        )
        if (card) {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(tileHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(fill),
            )
        } else {
            Row(
                Modifier
                    .padding(start = 20.dp, top = 14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(SKELETON_TILES) {
                    Box(
                        Modifier
                            .width(tileWidth)
                            .height(tileHeight)
                            .clip(if (round) CircleShape else RoundedCornerShape(18.dp))
                            .background(fill),
                    )
                }
            }
        }
    }
}

/** Enough to reach the edge of the screen, which is all a placeholder row is. */
private const val SKELETON_TILES = 3

@Composable
private fun TrackRow(tracks: List<CatalogTrack>, onPlay: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
            TrackTile(track) { onPlay(index) }
        }
    }
}

/** An album or a playlist in a carousel: square art, name, who it is by. */
@Composable
private fun FeedTile(item: SearchItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(152.dp)
            .pressable(onClick),
    ) {
        SquaredArtwork(
            url = item.artworkUrl,
            title = item.title,
            modifier = Modifier
                .size(152.dp)
                .softShadow(RoundedCornerShape(20.dp), elevation = 18.dp),
            corner = 20.dp,
        )
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = InkDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun <T> Carousel(
    items: List<T>,
    key: (T) -> Any,
    item: @Composable (T) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        items(items, key = key) { item(it) }
    }
}

@Composable
private fun Heading(text: String, strapline: String? = null, top: androidx.compose.ui.unit.Dp = 30.dp) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = top)) {
        // Above the title, smaller and quieter, the way the service writes it:
        // "MIX", "START RADIO FROM A SONG", an artist's name. It is most of
        // what tells two shelves with the same title apart.
        strapline?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.headlineLarge)
    }
}

/**
 * A shelf of songs as a block rather than a row.
 *
 * Four to a column, and the columns scroll a screen at a time — the shape the
 * official app gives its longest shelves. A song here is a line, not a tile:
 * small art, the title, who it is by. Twenty of these as tiles would be a
 * carousel nobody reaches the end of.
 */
@Composable
private fun QuickPicks(tracks: List<CatalogTrack>, onPlay: (Int) -> Unit) {
    val columns = tracks.chunked(QUICK_PICK_ROWS)
    // A phone's width at most: wider, in landscape or on a tablet, a column
    // was the whole screen with two lines of text at its edges and nothing
    // between them (#180).
    val width = (LocalConfiguration.current.screenWidthDp.dp - 44.dp).coerceAtMost(400.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(rememberLazyListState()),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        itemsIndexed(columns, key = { _, column -> column.first().uri }) { columnIndex, column ->
            Column(
                Modifier.width(width),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                column.forEachIndexed { rowIndex, track ->
                    QuickPickRow(track) {
                        onPlay(columnIndex * QUICK_PICK_ROWS + rowIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickRow(track: CatalogTrack, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquaredArtwork(
            url = track.artworkUrl,
            title = track.name,
            modifier = Modifier.size(52.dp),
            corner = 10.dp,
            decodeSize = 52.dp,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                track.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A glass pill, for the handful of places that need a button at all. */
@Composable
private fun GlassAction(label: String, backdrop: Backdrop, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        contentHeight = 50.dp,
        contentPadding = 26.dp,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** How many shelves' outlines stand in while Spotify's are on their way. */
private const val SHELF_OUTLINES = 4

@StringRes
private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..12 -> R.string.squared_good_morning
    in 13..17 -> R.string.squared_good_afternoon
    else -> R.string.squared_good_evening
}

/** A harder film for the chip that is on; see the note at the call site. */
private val SelectedFilm = Color.White.copy(alpha = 0.26f)

/**
 * How far the list scrolls before the header is fully collapsed, in pixels.
 *
 * Pixels rather than dp because it is compared against a scroll offset, which
 * the list reports in pixels; converting per frame to compare two numbers would
 * be work for nothing.
 */
private const val COLLAPSE_DISTANCE_PX = 140f


/** Comfortably under the 640px Spotify serves, so it is never upscaled. */
private val COVER_SIZE = 210.dp

/** How much of the list's height the fade under the header covers. */
private const val FADE_FRACTION = 0.045f

private const val FEED_SIZE = 6
private const val CAROUSEL_SIZE = 8

/** With the library filter on, the carousel is the whole point of the page. */
private const val LIBRARY_SIZE = 30

/**
 * Up to three friends, overlapping, as one control.
 *
 * Stacked rather than listed: the header has room for a gesture, not for a row
 * of names, and overlapping faces are the one shape everybody already reads as
 * "these people, together". The count is left off deliberately — a badge would
 * turn a glance into a number to keep at zero.
 */
@Composable
private fun FriendFaces(
    friends: List<com.music.spotui.ui.immersive.squared.model.FriendListen>,
    onClick: () -> Unit,
) {
    val shown = friends.take(3)
    // Measured rather than laid out in a row: each face sits on the one before
    // it, so the box is one face wide plus what the others peek out by.
    val width = FACE + FACE_PEEK * (shown.size - 1)
    Box(
        Modifier
            .padding(start = 8.dp)
            .width(width)
            .height(FACE)
            .pressable(onClick, pressedScale = 0.92f),
    ) {
        shown.forEachIndexed { index, friend ->
            SquaredArtwork(
                url = friend.avatarUrl,
                title = friend.userName,
                modifier = Modifier
                    .offset(x = FACE_PEEK * index)
                    .size(FACE)
                    .clip(CircleShape)
                    // The first face on top, so the stack leans the way a hand
                    // of cards does rather than away from the reader.
                    .zIndex((shown.size - index).toFloat()),
                corner = FACE / 2,
                decodeSize = FACE,
            )
        }
    }
}

/** One face, and how much of the one behind it is left showing. */
private val FACE = 30.dp
private val FACE_PEEK = 20.dp

/** A shelf of at least this many songs is drawn as a block; see [QuickPicks]. */
private const val QUICK_PICK_MINIMUM = 8

/** How deep that block goes before it starts a new column. */
private const val QUICK_PICK_ROWS = 4
