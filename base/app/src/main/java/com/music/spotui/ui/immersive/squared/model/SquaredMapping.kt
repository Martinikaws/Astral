/*
 * Written for Astral, 2026-11, for the Squared look. Not part of Square, but shaped after what
 * Square's own data layer hands its screens (data/TabContents.kt, data/PlaylistOrderStore.kt,
 * data/PinnedPlaylistStore.kt, data/FriendActivity.kt): the pure mapping from Astral's entities
 * (home sections, library entries, search results, collection pages, artist and show pages) into
 * the models Square's screens draw, and the rules that order and file them. Kept free of Compose,
 * Android and the player so it can be unit-tested (SquaredMappingTest).
 */
package com.music.spotui.ui.immersive.squared.model

import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistOverviewModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.PodcastModel
import com.music.spotui.data.entity.SongsModel

/** What kind of page an address in the Squared screens opens. */
enum class SquaredTargetKind { ALBUM, ARTIST, PLAYLIST, LIKED, SHOW, EPISODE, FOLDER, ENTRY, UNKNOWN }

/** An address from the screens, taken apart; the host turns it into an Astral route. */
data class SquaredTarget(
    val kind: SquaredTargetKind,
    /** The Spotify id, or empty where the page is found by name. */
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    /** A library folder's full uri. */
    val uri: String = "",
)

/**
 * The addresses the Squared screens pass around.
 *
 * Spotify's own where an id is known (spotify:album:ID), and a private "squared:" form where
 * Astral only has a name to find the page by.
 */
object SquaredUris {
    const val LIKED = "spotify:collection:tracks"
    private const val BY_NAME_ALBUM = "squared:album:"
    private const val BY_NAME_ARTIST = "squared:artist:"
    private const val SEP = '\u0001'

    fun album(id: String, name: String, artist: String): String =
        if (id.isNotBlank()) "spotify:album:$id" else "$BY_NAME_ALBUM$name$SEP$artist"

    fun artist(id: String, name: String): String =
        if (id.isNotBlank()) "spotify:artist:$id" else "$BY_NAME_ARTIST$name"

    fun playlist(id: String): String = if (id == LIKED_ID) LIKED else "spotify:playlist:$id"

    fun show(id: String): String = "spotify:show:$id"

    private const val ENTRY = "squared:entry:"

    /** Astral's own library rows (local files, downloads, the phone's playlists, your episodes). */
    fun entry(spotifyId: String): String = "$ENTRY$spotifyId"

    /** The library ids that are Astral's own rather than Spotify's, and open pages of their own. */
    fun isOwnEntry(spotifyId: String): Boolean =
        spotifyId == "local-files" || spotifyId == "downloads" || spotifyId == "your-episodes" ||
            spotifyId == "new-episodes" || spotifyId.startsWith("local-playlist:")

    fun episode(id: String): String = "spotify:episode:$id"

    /** Astral's own id for Liked Songs; see Api.HomeCache. */
    const val LIKED_ID = "liked-songs"

    fun parse(uri: String, name: String = "", subtitle: String = ""): SquaredTarget = when {
        uri == LIKED || uri == "spotify:collection" || uri.endsWith(":collection") -> SquaredTarget(SquaredTargetKind.LIKED)
        ":folder:" in uri -> SquaredTarget(SquaredTargetKind.FOLDER, name = name, uri = uri)
        uri.startsWith(ENTRY) -> SquaredTarget(SquaredTargetKind.ENTRY, uri.removePrefix(ENTRY), name)
        uri.startsWith("spotify:user:") && ":playlist:" in uri ->
            SquaredTarget(SquaredTargetKind.PLAYLIST, uri.substringAfter(":playlist:"), name)
        uri.startsWith("spotify:playlist:") -> SquaredTarget(SquaredTargetKind.PLAYLIST, uri.removePrefix("spotify:playlist:"), name)
        uri.startsWith("spotify:album:") -> SquaredTarget(SquaredTargetKind.ALBUM, uri.removePrefix("spotify:album:"), name, subtitle)
        uri.startsWith("spotify:artist:") -> SquaredTarget(SquaredTargetKind.ARTIST, uri.removePrefix("spotify:artist:"), name)
        uri.startsWith("spotify:show:") -> SquaredTarget(SquaredTargetKind.SHOW, uri.removePrefix("spotify:show:"), name)
        uri.startsWith("spotify:episode:") -> SquaredTarget(SquaredTargetKind.EPISODE, uri.removePrefix("spotify:episode:"), name)
        uri.startsWith(BY_NAME_ALBUM) -> {
            val body = uri.removePrefix(BY_NAME_ALBUM)
            SquaredTarget(SquaredTargetKind.ALBUM, name = body.substringBefore(SEP), artist = body.substringAfter(SEP, ""))
        }
        uri.startsWith(BY_NAME_ARTIST) -> SquaredTarget(SquaredTargetKind.ARTIST, name = uri.removePrefix(BY_NAME_ARTIST))
        else -> SquaredTarget(SquaredTargetKind.UNKNOWN, uri = uri, name = name)
    }
}

// ---------------------------------------------------------------------------------------------
// Tracks

/** A song or episode of Astral's as Square's lists draw it. */
fun SongsModel.toCatalogTrack(): CatalogTrack {
    val episode = isEpisode
    val uri = when {
        episode -> SquaredUris.episode(url.removePrefix("episode:"))
        spotifyTrackId.isNotBlank() -> "spotify:track:$spotifyTrackId"
        else -> url
    }
    val names = singer.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return CatalogTrack(
        rowUid = rowUid,
        uri = uri,
        name = title,
        artist = singer,
        artistUri = artistIds.firstOrNull()?.let { "spotify:artist:$it" },
        artists = names.mapIndexed { i, n -> CatalogArtist(n, artistIds.getOrNull(i)?.let { "spotify:artist:$it" }) },
        album = album,
        albumUri = albumId.takeIf { it.isNotBlank() }?.let { "spotify:album:$it" },
        year = releaseDate.take(4),
        releaseDate = releaseDate,
        durationMs = durationMs.toLong(),
        explicit = explicit,
        artworkUrl = coverUri.takeIf { it.isNotBlank() },
        addedAt = addedAt.takeIf { it.isNotBlank() },
        releasedAt = if (episode) releaseDate.ifBlank { "1970-01-01" } else null,
        resumeMs = playedPositionMs,
        played = played,
        summary = if (episode) description else "",
    )
}

/** The key a track is found by again when a screen hands it back: its address and its row. */
fun trackKey(uri: String, rowUid: String): String = if (rowUid.isBlank()) uri else "$uri#$rowUid"

/** Astral's songs by [trackKey], so a [CatalogTrack] a screen hands back finds its song again. */
fun List<SongsModel>.indexedByTrack(): Map<String, SongsModel> {
    val map = LinkedHashMap<String, SongsModel>(size)
    forEach { song ->
        val uri = song.toCatalogTrack().uri
        map.putIfAbsent(uri, song)
        if (song.rowUid.isNotBlank()) map[trackKey(uri, song.rowUid)] = song
    }
    return map
}

// ---------------------------------------------------------------------------------------------
// Home

private val RELEASE_PREFIX = Regex("^(Album|Single|EP|Compilation)\\s*[•·-]\\s*", RegexOption.IGNORE_CASE)

/** One card of Astral's home feed as Square's shelves draw it. */
fun HomeItem.toCatalogPlaylist(): CatalogPlaylist = when (this) {
    is HomeItem.Album -> CatalogPlaylist(
        uri = SquaredUris.album(id, name, artists.ifBlank { subtitle }),
        name = name,
        artworkUrl = imageUrl.takeIf { it.isNotBlank() },
        subtitle = subtitle.takeIf { it.isNotBlank() },
    )
    is HomeItem.Artist -> CatalogPlaylist(
        uri = SquaredUris.artist(id, name),
        name = name,
        artworkUrl = imageUrl.takeIf { it.isNotBlank() },
        subtitle = null,
        isArtist = true,
    )
    is HomeItem.Show -> CatalogPlaylist(
        uri = if (isEpisode) SquaredUris.episode(id) else SquaredUris.show(id),
        name = name,
        artworkUrl = imageUrl.takeIf { it.isNotBlank() },
        subtitle = subtitle.takeIf { it.isNotBlank() },
    )
    is HomeItem.Playlist -> CatalogPlaylist(
        uri = if (id.isBlank()) SquaredUris.album(id, name, "") else SquaredUris.playlist(id),
        name = name,
        artworkUrl = imageUrl.takeIf { it.isNotBlank() },
        subtitle = subtitle.takeIf { it.isNotBlank() },
    )
}

/** Astral's home shelves as Square's, dropping the ones with nothing in them. */
fun List<HomeSection>.toShelves(): List<HomeShelf> = mapNotNull { section ->
    val items = section.items.map { it.toCatalogPlaylist() }
    if (items.isEmpty()) null else HomeShelf(section.title, items, sectionUri = section.uri)
}

/** The shelves under one of Home's chips, or the podcast ones. */
fun List<HomeShelf>.podcastShelves(): List<HomeShelf> = mapNotNull { shelf ->
    val items = shelf.items.filter { it.uri.startsWith("spotify:show:") || it.uri.startsWith("spotify:episode:") }
    if (items.isEmpty()) null else shelf.copy(items = items)
}

/** Without podcasts: the music home. */
fun List<HomeShelf>.withoutPodcasts(): List<HomeShelf> = mapNotNull { shelf ->
    val items = shelf.items.filterNot { it.uri.startsWith("spotify:show:") || it.uri.startsWith("spotify:episode:") }
    if (items.isEmpty()) null else shelf.copy(items = items)
}

/** Which tab a row of Home's goes under; Square's TabContents, kept to the part Home reads. */
object TabContents {
    enum class Chip { FOR_YOU, DISCOVER, ARTISTS }

    enum class Kind { STATION, MIX, DAILY_MIX, DAYLIST, BLEND, OTHER }

    private const val PLAYLIST = "spotify:playlist:"

    fun kind(item: CatalogPlaylist): Kind {
        val id = item.uri.removePrefix(PLAYLIST)
        if (id == item.uri) return Kind.OTHER
        return when {
            id.startsWith("37i9dQZF1E4") -> Kind.STATION
            id.startsWith("37i9dQZF1E3") -> Kind.DAILY_MIX
            id.startsWith("37i9dQZF1EP") -> Kind.DAYLIST
            id.startsWith("37i9dQZF1EJ") -> Kind.BLEND
            id.startsWith("37i9dQZF1EQ") || id.startsWith("37i9dQZF1EI") || id.startsWith("37i9dQZF1EV") -> Kind.MIX
            else -> Kind.OTHER
        }
    }

    private fun personal(item: CatalogPlaylist): Boolean {
        val id = item.uri.removePrefix(PLAYLIST)
        return kind(item) in setOf(Kind.DAILY_MIX, Kind.DAYLIST, Kind.BLEND) ||
            id.startsWith("37i9dQZEVXc") || id.startsWith("37i9dQZF1Ep") || id.startsWith("37i9dQZF1CA")
    }

    /** The chip a row goes under: what is made for the account, whose artists, or the editors'. */
    fun chipOf(row: HomeShelf): Chip? = when {
        row.artist != null -> Chip.ARTISTS
        row.gathered -> Chip.FOR_YOU
        row.items.any { personal(it) } -> Chip.FOR_YOU
        row.items.any { it.uri.startsWith("spotify:artist:") || it.isArtist } ||
            row.items.firstOrNull()?.let { kind(it) == Kind.STATION } == true -> Chip.ARTISTS
        else -> Chip.DISCOVER
    }
}

/** What one person the account follows was last heard playing; Astral has none of it yet. */
data class FriendListen(
    val userUri: String,
    val userName: String,
    val avatarUrl: String?,
)

// ---------------------------------------------------------------------------------------------
// Library

/** Lifts the pinned lists to the front, whatever the sort underneath. */
fun List<CatalogPlaylist>.withPinnedFirst(pinned: List<String>): List<CatalogPlaylist> {
    if (pinned.isEmpty()) return this
    val rank = pinned.withIndex().associate { (index, uri) -> uri to index }
    val (top, rest) = partition { rank.containsKey(it.uri) }
    return top.sortedBy { rank.getValue(it.uri) } + rest
}

/** Moves Liked Songs to the front of the list. */
fun List<CatalogPlaylist>.withLikedSecond(): List<CatalogPlaylist> {
    val liked = firstOrNull { it.uri == SquaredUris.LIKED } ?: return this
    return listOf(liked) + filterNot { it.uri == liked.uri }
}

/** The phone's own music comes first; Astral's library keeps it as its own row. */
fun List<CatalogPlaylist>.withLocalFilesFirst(): List<CatalogPlaylist> = this

/** Most recently opened first; the rest keep their order. */
fun List<CatalogPlaylist>.sortedByRecentlyOpened(order: List<String>): List<CatalogPlaylist> {
    if (order.isEmpty()) return this
    val rank = order.withIndex().associate { (index, uri) -> uri to index }
    val (opened, rest) = partition { rank.containsKey(it.uri) }
    return opened.sortedBy { rank.getValue(it.uri) } + rest
}

/** A library row of Astral's as a Square list tile: a playlist, folder, or saved album. */
fun LibraryEntry.toCatalogPlaylist(): CatalogPlaylist = CatalogPlaylist(
    uri = when {
        isFolder -> folderUri
        SquaredUris.isOwnEntry(spotifyId) -> SquaredUris.entry(spotifyId)
        isPlaylist -> uri.ifBlank { SquaredUris.playlist(spotifyId) }
        else -> SquaredUris.album(spotifyId, name, artists.ifBlank { subtitle })
    },
    name = name,
    artworkUrl = coverUri.takeIf { it.isNotBlank() },
    subtitle = subtitle.takeIf { it.isNotBlank() },
    mine = null,
)

/** A followed show as a Square tile. */
fun LibraryEntry.toShowTile(): CatalogPlaylist = CatalogPlaylist(
    uri = SquaredUris.show(spotifyId),
    name = name,
    artworkUrl = coverUri.takeIf { it.isNotBlank() },
    subtitle = subtitle.takeIf { it.isNotBlank() },
)

fun ArtistsModel.toSearchItem(): SearchItem = SearchItem(
    uri = SquaredUris.artist(id, name),
    title = name,
    subtitle = "",
    artworkUrl = coverUri.takeIf { it.isNotBlank() },
)

fun AlbumsModel.toSearchItem(): SearchItem = SearchItem(
    uri = SquaredUris.album(spotifyId, name, artists),
    title = name,
    subtitle = listOf(year, artists).filter { it.isNotBlank() }.joinToString(" · "),
    artworkUrl = coverUri.takeIf { it.isNotBlank() },
)

fun PodcastModel.toSearchItem(): SearchItem = SearchItem(
    uri = SquaredUris.show(id),
    title = name,
    subtitle = publisher,
    artworkUrl = coverUri.takeIf { it.isNotBlank() },
)

fun LibraryEntry.toSearchItem(): SearchItem = SearchItem(
    uri = if (isPlaylist) SquaredUris.playlist(spotifyId) else SquaredUris.album(spotifyId, name, artists.ifBlank { subtitle }),
    title = name,
    subtitle = subtitle,
    artworkUrl = coverUri.takeIf { it.isNotBlank() },
)

// ---------------------------------------------------------------------------------------------
// Search

/** Astral's search answer as Square's SearchResults. */
fun com.music.spotui.data.entity.SearchResults.toSquared(): SearchResults = SearchResults(
    tracks = songs.map { it.toCatalogTrack() },
    artists = artists.map { it.toSearchItem() },
    albums = albums.map { it.toSearchItem() },
    playlists = playlists.map { it.toSearchItem() },
    podcasts = shows.map { it.toSearchItem() },
    episodes = episodes.map { it.toCatalogTrack() },
)

// ---------------------------------------------------------------------------------------------
// Collection pages

/** A single, EP or album, from the artist page's release rows. */
fun AlbumsModel.isSingle(): Boolean = type.equals("single", ignoreCase = true) || type.equals("ep", ignoreCase = true)

/** The artist page's listener count as a line: 1234567 -> "1.2M"; null when unknown. */
fun compactCount(n: Long?): String? = when {
    n == null || n <= 0 -> null
    n >= 1_000_000_000 -> "%.1fB".format(java.util.Locale.ROOT, n / 1_000_000_000.0).replace(".0B", "B")
    n >= 1_000_000 -> "%.1fM".format(java.util.Locale.ROOT, n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> "${n / 1_000}K"
    else -> n.toString()
}

/** An artist page's overview as Square's page state; the songs are filled in by the caller. */
fun ArtistOverviewModel.toState(uri: String): SquaredPlaylistState {
    val (singles, albums) = popularReleases.partition { it.isSingle() }
    return SquaredPlaylistState(
        uri = uri,
        name = name,
        artworkUrl = avatarImage.takeIf { it.isNotBlank() } ?: headerImage.takeIf { it.isNotBlank() },
        heroUrl = headerImage.takeIf { it.isNotBlank() },
        kind = SquaredDetailKind.ARTIST,
        tracks = topTracks.map { it.song.toCatalogTrack() },
        albums = albums.map { it.toSearchItem() },
        singles = singles.map { it.toSearchItem() },
        appearsOn = appearsOn.map { it.toSearchItem() },
        relatedArtists = relatedArtists.map { it.toSearchItem() },
        verified = verified,
        monthlyListeners = compactCount(monthlyListeners),
        notes = biography?.takeIf { it.isNotBlank() },
    )
}

// ---------------------------------------------------------------------------------------------
// Recently played

/**
 * The songs listened to most recently, newest first, each once, as Square's "play again" row
 * draws them. The listen's key is what the player is asked to play; it travels as the row's uri.
 */
fun recentTracks(
    listens: List<com.music.spotui.data.local.ListenHistory.Listen>,
    limit: Int = 20,
): List<CatalogTrack> = listens
    .asSequence()
    .filter { it.key.isNotBlank() && it.source != com.music.spotui.data.local.ListenHistory.Source.LOCAL }
    .sortedByDescending { it.atMs }
    .distinctBy { it.key }
    .take(limit)
    .map {
        CatalogTrack(
            uri = it.key,
            name = it.title,
            artist = it.artist,
            album = it.album,
            artworkUrl = it.cover.takeIf { c -> c.isNotBlank() },
        )
    }
    .toList()

// ---------------------------------------------------------------------------------------------
// Search history

/** One row of what was found and opened before, as Square's search page lists it. */
data class SquaredSearchHistoryEntry(
    /** For a song, the same as [track]'s uri; otherwise the page's address. */
    val uri: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    /** Set for a song: it plays where it is. */
    val track: CatalogTrack? = null,
)

/** Astral's recent search item as a history row. */
fun com.music.spotui.data.preferences.RecentItem.toHistoryEntry(): SquaredSearchHistoryEntry? {
    val art = image.takeIf { it.isNotBlank() }
    val id = key.takeIf { it != name }.orEmpty()
    return when (type) {
        "song" -> {
            val uri = if (spotifyTrackId.isNotBlank()) "spotify:track:$spotifyTrackId" else songUrl
            if (uri.isBlank()) return null
            SquaredSearchHistoryEntry(
                uri = uri,
                title = name,
                subtitle = singer,
                artworkUrl = art,
                track = CatalogTrack(
                    uri = uri,
                    name = name,
                    artist = singer,
                    album = songAlbum,
                    durationMs = durationMs.toLong(),
                    explicit = explicit,
                    artworkUrl = art,
                ),
            )
        }
        "artist" -> SquaredSearchHistoryEntry(SquaredUris.artist(id, name), name, "", art)
        "album" -> SquaredSearchHistoryEntry(SquaredUris.album(id, name, singer), name, singer, art)
        "show" -> SquaredSearchHistoryEntry(SquaredUris.show(key), name, singer, art)
        "playlist" -> SquaredSearchHistoryEntry(SquaredUris.playlist(key), name, singer, art)
        else -> null
    }
}
