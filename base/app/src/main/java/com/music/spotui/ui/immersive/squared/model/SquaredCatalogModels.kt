/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/data/Catalog.kt, data/SearchResults.kt, data/SpotifyHome.kt and ui/MainViewModel.kt (the state classes) at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed to com.music.spotui.ui.immersive.squared.model.
 * Merged into one file: the catalogue track/artist/playlist shapes, search
 * results, the home shelves, and the state classes Square nests in its
 * MainViewModel (renamed Squared*: UiState, DetailKind, ArtistRelease,
 * FeedState, SearchState, PlaylistState). Dropped everything Astral has no
 * source for: Web API shapes, YouTube and mix fields, download state, the
 * sign-in/connecting flows (UiState keeps them as states the host never
 * enters apart from Loading and Failed). String resources point at the
 * squared_ prefixed copies.
 */

package com.music.spotui.ui.immersive.squared.model

import androidx.annotation.StringRes
import app.myspoti.engine.R

/**
 * A track as shown in the UI.
 *
 * Deliberately flat and free of Web API shapes: it is produced by the native
 * catalogue, and nothing above this layer should care which transport supplied
 * it.
 */
data class CatalogTrack(
    val uri: String,
    val name: String,
    val artist: String,
    /** The first artist's own uri, when the source gives one; see catalog.rs. */
    val artistUri: String? = null,
    /**
     * Every artist credited, named and addressed.
     *
     * [artist] is these names run together for the places that show one line;
     * this is what makes each of them its own way into the app.
     */
    val artists: List<CatalogArtist> = emptyList(),
    val album: String = "",
    /**
     * The record's own address, where the source gives one.
     *
     * What makes the album under a title in the player a way to reach it rather
     * than a caption. Null through the access point, which names the record but
     * does not address it; the player falls back to a lookup there.
     */
    val albumUri: String? = null,
    /**
     * The year the record came out, or empty where the source does not say.
     *
     * Only the four digits: an artist page names a song by the record it is on
     * and when that record happened, and the month and day of a release are not
     * how anybody places an album.
     */
    val year: String = "",
    /**
     * The day the record came out, as precisely as the source knows it: a
     * whole date, a year and month, or only the year (#161). Empty where the
     * source does not say. Shown where a single record or song is the subject;
     * a list keeps to [year].
     */
    val releaseDate: String = "",
    val durationMs: Long = 0,
    val explicit: Boolean = false,
    val smartRecommended: Boolean = false,
    val artworkUrl: String? = null,
    /**
     * When the track was added to the playlist it was read from, ISO-8601.
     *
     * Only the Web API knows this — the access point's context has no such
     * field — so it is null for anything resolved through the fallback path,
     * and sorting by it then leaves the list in playlist order.
     */
    val addedAt: String? = null,
    /**
     * The file on this phone that plays for this entry, where there is one.
     *
     * Set for the phone's own music, and for a local file a Spotify playlist
     * names once a file here has been matched to it (see
     * LocalLibrary.match). Null on a `spotify:local:` entry means this phone
     * has nothing to play for it.
     */
    val localFile: String? = null,
    /**
     * For a podcast episode: when it came out, ISO-8601. Null for music, whose
     * release is a year on its record rather than a day.
     */
    val releasedAt: String? = null,
    /** For an episode: how far into it the listener got, where the source says. */
    val resumeMs: Long = 0,
    /** For an episode: whether the listener heard it to the end. */
    val played: Boolean = false,
    /** For an episode: whether it comes with a picture as well as sound. */
    val hasVideo: Boolean = false,
    /** For an episode: what the show says it is about, as plain text. */
    val summary: String = "",
    val rowUid: String = "",
) {
    /**
     * A podcast episode rather than a song, on either source.
     *
     * What the player and the lists ask before offering what only a song has:
     * lyrics, a heart, a radio, credits.
     */
    val isEpisode: Boolean
        get() = uri.startsWith("spotify:episode:") || releasedAt != null
}

/** A playlist owned by the logged-in account. */
data class CatalogPlaylist(
    val uri: String,
    val name: String,
    /** Null when the playlist has no custom cover; the UI draws a tile instead. */
    val artworkUrl: String? = null,
    /**
     * Who it is by, or what it is: the line a home shelf writes under the name.
     *
     * Null in the account's own library, where every row is the user's and the
     * line would say nothing.
     */
    val subtitle: String? = null,
    /**
     * An artist rather than a record.
     *
     * The only thing the tile does differently, and it does matter: a round
     * portrait among square covers is how a shelf says "these are people".
     */
    val isArtist: Boolean = false,
    /**
     * Whether it is the account's own, where the account's index says: what
     * decides if it can be edited. Null when nobody said.
     */
    val mine: Boolean? = null,
)

/** One credited artist: what to write, and where it leads. */
data class CatalogArtist(val name: String, val uri: String? = null)

/** Anything a search result can point at, flattened for the UI. */
data class SearchItem(
    val uri: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
)

data class SearchResults(
    val tracks: List<CatalogTrack> = emptyList(),
    val artists: List<SearchItem> = emptyList(),
    val albums: List<SearchItem> = emptyList(),
    val playlists: List<SearchItem> = emptyList(),
    /** Videos, music and otherwise (ASMR, sessions); YouTube Music only. Played as videos. */
    val videos: List<CatalogTrack> = emptyList(),
    /**
     * The tracks that were found by their words rather than their titles.
     *
     * Spotify says which those are — a match carries the fields it matched on
     * — and it is worth showing: a list of songs whose names have nothing to
     * do with what was typed looks like a broken search until the reason is on
     * the row. Empty from the Web API, which does not search lyrics at all.
     */
    val lyricMatches: Set<String> = emptySet(),
    /** Shows, which open as their list of episodes. */
    val podcasts: List<SearchItem> = emptyList(),
    /** Single episodes, which play. */
    val episodes: List<CatalogTrack> = emptyList(),
) {
    /** How many rows this holds in total, for telling a page from an echo. */
    val count: Int
        get() = tracks.size + artists.size + albums.size + playlists.size + videos.size +
            podcasts.size + episodes.size

    /**
     * This, with another page's rows after it.
     *
     * De-duplicated by address in every kind: an offset is the server's idea of
     * where a page starts, and a catalogue that has changed under it hands back
     * rows that are already on the screen.
     */
    operator fun plus(page: SearchResults): SearchResults {
        val heldTracks = tracks.mapTo(mutableSetOf()) { it.uri }
        val heldArtists = artists.mapTo(mutableSetOf()) { it.uri }
        val heldAlbums = albums.mapTo(mutableSetOf()) { it.uri }
        val heldLists = playlists.mapTo(mutableSetOf()) { it.uri }
        return SearchResults(
            tracks = tracks + page.tracks.filter { it.uri !in heldTracks },
            artists = artists + page.artists.filter { it.uri !in heldArtists },
            albums = albums + page.albums.filter { it.uri !in heldAlbums },
            playlists = playlists + page.playlists.filter { it.uri !in heldLists },
            videos = videos + page.videos.filter { video -> videos.none { it.uri == video.uri } },
            lyricMatches = lyricMatches + page.lyricMatches,
            podcasts = podcasts + page.podcasts.filter { show -> podcasts.none { it.uri == show.uri } },
            episodes = episodes + page.episodes.filter { episode -> episodes.none { it.uri == episode.uri } },
        )
    }

    val isEmpty: Boolean
        get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() &&
            videos.isEmpty() && podcasts.isEmpty() && episodes.isEmpty()
}

/**
 * One row of an artist page as the source lays it out: its own heading, in
 * the source's own words and order.
 */
data class ArtistShelf(
    val title: String,
    /** Records, lists and artists, opened as pages. */
    val items: List<SearchItem> = emptyList(),
    /** Videos and performances, played where they are. */
    val tracks: List<CatalogTrack> = emptyList(),
    /** The whole of it, where the source has a page for that. */
    val moreUri: String? = null,
)

/** One row of the personalised home: a title, and the things under it. */
data class HomeShelf(
    val title: String,
    val items: List<CatalogPlaylist>,
    /** The section's address, where it came with one. */
    val sectionUri: String = "",
    /** Gathered out of single-tile sections rather than given as a row. */
    val gathered: Boolean = false,
    /** The artist the row is about, where it said. */
    val artist: String? = null,
)

/** What the library, home and search tabs say about the account. */
sealed interface SquaredUiState {
    data object LoggedOut : SquaredUiState
    data object Connecting : SquaredUiState
    data object Loading : SquaredUiState
    data class Ready(
        val displayName: String,
        val playlists: List<CatalogPlaylist>,
        val avatarUrl: String? = null,
    ) : SquaredUiState
    data class Failed(val message: String) : SquaredUiState
}

/** What kind of thing the detail screen is showing. */
enum class SquaredDetailKind(@StringRes val label: Int) {
    PLAYLIST(R.string.squared_playlist),
    ALBUM(R.string.squared_album),
    ARTIST(R.string.squared_artist),
    /** A podcast: its episodes are the page's list. */
    SHOW(R.string.squared_podcast),
}

/** One record, said the way the artist page's top card says it. */
data class SquaredArtistRelease(
    val uri: String,
    val title: String,
    val artworkUrl: String?,
    /** ISO-8601: a full date, a month, or a year. */
    val releaseDate: String,
    val trackCount: Int,
)

/** The home feed's catalogue sections. */
data class SquaredFeedState(
    val newReleases: List<SearchItem> = emptyList(),
    val topArtists: List<SearchItem> = emptyList(),
    val topTracks: List<CatalogTrack> = emptyList(),
    val allTimeTracks: List<CatalogTrack> = emptyList(),
    val fromYourArtists: List<SearchItem> = emptyList(),
    val jumpBackIn: List<SearchItem> = emptyList(),
    val loading: Boolean = false,
)

/** Search box contents and results. */
data class SquaredSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: SearchResults = SearchResults(),
    val error: String? = null,
    val needsSetup: Boolean = false,
    val loadingMore: Boolean = false,
    val exhausted: Boolean = false,
)

/** The playlist, album, artist or show currently open, keyed by its URI. */
data class SquaredPlaylistState(
    val uri: String? = null,
    val name: String = "",
    val artworkUrl: String? = null,
    val tracks: List<CatalogTrack> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val kind: SquaredDetailKind = SquaredDetailKind.PLAYLIST,
    val description: String = "",
    val saved: Boolean? = null,
    val mine: Boolean? = null,
    val albums: List<SearchItem> = emptyList(),
    val singles: List<SearchItem> = emptyList(),
    val appearsOn: List<SearchItem> = emptyList(),
    val artistPlaylists: List<SearchItem> = emptyList(),
    val relatedPlaylists: List<SearchItem> = emptyList(),
    val relatedArtists: List<SearchItem> = emptyList(),
    val verified: Boolean = true,
    val monthlyListeners: String? = null,
    val heroUrl: String? = null,
    val coverUrl: String? = null,
    val notes: String? = null,
    val tintHex: String? = null,
    val inkHex: String? = null,
    val heroAspect: Float? = null,
    val byline: String = "",
    val origin: String? = null,
    val motionUrl: String? = null,
    val heroPending: Boolean = false,
    val logoUrl: String? = null,
    val latest: SquaredArtistRelease? = null,
    val latestSaved: Boolean? = null,
    val followers: Int = 0,
    val genres: List<String> = emptyList(),
    val following: Boolean? = null,
    val needsPermission: Boolean = false,
    val subscribers: String? = null,
    val shelves: List<ArtistShelf> = emptyList(),
    val topSongsMoreUri: String? = null,
)
