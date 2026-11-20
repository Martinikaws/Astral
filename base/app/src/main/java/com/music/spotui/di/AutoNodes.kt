package com.music.spotui.di

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.utils.MediaConstants
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.PodcastModel
import com.music.spotui.data.entity.SearchResults
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * One row of the Android Auto / Automotive browse tree, as plain data: the tree is built from
 * these ([AutoTree], testable without a device), kept between runs ([AutoNodeJson]) so a folder
 * shows at once, and turned into media items at the end ([toMediaItem]).
 *
 * The car draws the rows itself: what is ours is the content and these hints.
 */
data class AutoNode(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** A content:// URI of the artwork provider (the car loads no web image), or null. */
    val iconUri: String? = null,
    /** A drawable of the app (tabs, shortcuts), shown as `android.resource://`. */
    val iconRes: String? = null,
    val playable: Boolean = false,
    /** How the browsable children of this folder are laid out ([AutoTree.LIST] ...). */
    val childrenStyle: Int = AutoTree.LIST,
    /** How the playable children of this folder are laid out. */
    val playableStyle: Int = AutoTree.LIST,
    /** The section title above this row and the next ones of the same group. */
    val group: String? = null,
    val explicit: Boolean = false,
    /** [DOWNLOAD_NO] / [DOWNLOAD_YES]; null: not shown. */
    val download: Int? = null,
    /** [MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED] ...; null: not an episode. */
    val completion: Int? = null,
    /** How far an episode was played, 0..1; only with [completion] partially played. */
    val completionPercent: Double? = null,
    val description: String? = null,
) {
    companion object {
        const val DOWNLOAD_NO = 0
        const val DOWNLOAD_YES = 2
    }

    /** The media item the car lists. [packageName] resolves [iconRes]. */
    fun toMediaItem(packageName: String): MediaItem {
        val extras = Bundle().apply {
            putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, childrenStyle)
            putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playableStyle)
            group?.let { putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
            if (explicit) putLong(MediaConstants.METADATA_KEY_IS_EXPLICIT, MediaConstants.METADATA_VALUE_ATTRIBUTE_PRESENT)
            download?.let {
                putLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS,
                    if (it == DOWNLOAD_YES) MediaDescriptionCompat.STATUS_DOWNLOADED else MediaDescriptionCompat.STATUS_NOT_DOWNLOADED)
            }
            completion?.let {
                putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS, it)
                if (it == MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED && completionPercent != null) {
                    putDouble(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, completionPercent)
                }
            }
        }
        val icon = iconUri?.let(Uri::parse) ?: iconRes?.let { Uri.parse("android.resource://$packageName/drawable/$it") }
        val d = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle.ifBlank { null })
            .setDescription(description).setIconUri(icon).setExtras(extras).build()
        return MediaItem(d, if (playable) MediaItem.FLAG_PLAYABLE else MediaItem.FLAG_BROWSABLE)
    }
}

/** The nodes kept as text (folders shown at once on the next run). */
object AutoNodeJson {
    fun encode(nodes: List<AutoNode>): String = JSONArray().apply {
        nodes.forEach { n ->
            put(JSONObject().put("i", n.id).put("t", n.title).put("s", n.subtitle)
                .put("u", n.iconUri ?: JSONObject.NULL).put("r", n.iconRes ?: JSONObject.NULL)
                .put("p", n.playable).put("cs", n.childrenStyle).put("ps", n.playableStyle)
                .put("g", n.group ?: JSONObject.NULL).put("e", n.explicit)
                .put("d", n.download ?: JSONObject.NULL).put("c", n.completion ?: JSONObject.NULL)
                .put("cp", n.completionPercent ?: JSONObject.NULL).put("ds", n.description ?: JSONObject.NULL))
        }
    }.toString()

    /** Null when unreadable (an older format): the folder is read from Spotify as if never kept. */
    fun decode(raw: String): List<AutoNode>? = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { k ->
            val o = a.getJSONObject(k)
            fun str(key: String) = o.optString(key).takeUnless { o.isNull(key) }
            fun int(key: String) = if (o.isNull(key)) null else o.optInt(key)
            AutoNode(
                id = o.getString("i"), title = o.getString("t"), subtitle = o.optString("s"),
                iconUri = str("u"), iconRes = str("r"), playable = o.optBoolean("p"),
                childrenStyle = o.optInt("cs", AutoTree.LIST), playableStyle = o.optInt("ps", AutoTree.LIST),
                group = str("g"), explicit = o.optBoolean("e"), download = int("d"), completion = int("c"),
                completionPercent = if (o.isNull("cp")) null else o.optDouble("cp"), description = str("ds"),
            )
        }
    }.getOrNull()
}

/** Every string of the tree, so the builders need no Context. */
data class AutoStrings(
    val home: String = "Home",
    val library: String = "Library",
    val podcasts: String = "Podcasts",
    val downloads: String = "Downloads",
    val recent: String = "Recently played",
    val songs: String = "Songs",
    val albums: String = "Albums",
    val artists: String = "Artists",
    val playlists: String = "Playlists",
    val episodes: String = "Episodes",
    val likedSongs: String = "Liked Songs",
    val startRadio: String = "Start radio",
    val play: String = "Play",
    val shuffle: String = "Shuffle",
    val quickPlay: String = "Quick play",
    val shuffleLiked: String = "Shuffle Liked Songs",
    val topSongs: String = "Top songs",
    val downloadedSongs: String = "Downloaded songs",
    val downloadedLists: String = "Downloaded playlists and albums",
    val addToPlaylist: String = "Add to playlist",
    val addToPlaylistNone: String = "Choose where the add button puts songs",
    val addToPlaylistCurrent: String = "Songs are added here",
) {
    companion object {
        fun from(c: android.content.Context): AutoStrings {
            fun s(id: Int) = c.getString(id)
            return AutoStrings(
                home = s(app.myspoti.engine.R.string.aa_home), library = s(app.myspoti.engine.R.string.aa_library),
                podcasts = s(app.myspoti.engine.R.string.recents_filter_podcasts), downloads = s(app.myspoti.engine.R.string.aa_downloads),
                recent = s(app.myspoti.engine.R.string.aa_recent), songs = s(app.myspoti.engine.R.string.aa_songs),
                albums = s(app.myspoti.engine.R.string.aa_albums), artists = s(app.myspoti.engine.R.string.aa_artists),
                playlists = s(app.myspoti.engine.R.string.aa_playlists), episodes = s(app.myspoti.engine.R.string.show_tab_episodes),
                likedSongs = s(app.myspoti.engine.R.string.collection_liked_songs_title), startRadio = s(app.myspoti.engine.R.string.aa_start_radio),
                play = s(app.myspoti.engine.R.string.aa_play), shuffle = s(app.myspoti.engine.R.string.aa_shuffle),
                quickPlay = s(app.myspoti.engine.R.string.aa_quick_play), shuffleLiked = s(app.myspoti.engine.R.string.aa_shuffle_liked),
                topSongs = s(app.myspoti.engine.R.string.aa_top_songs), downloadedSongs = s(app.myspoti.engine.R.string.aa_downloaded_songs),
                downloadedLists = s(app.myspoti.engine.R.string.aa_downloaded_lists),
                addToPlaylist = s(app.myspoti.engine.R.string.aa_add_to_playlist),
                addToPlaylistNone = s(app.myspoti.engine.R.string.aa_add_to_playlist_none),
                addToPlaylistCurrent = s(app.myspoti.engine.R.string.aa_add_to_playlist_current),
            )
        }
    }
}

/** The media ids of the tree: "t|<context uri>|<song uri>" and the like. */
object AutoIds {
    const val LIKED_URI = "spotify:collection:tracks"
    const val MODE_PLAY = "play"
    const val MODE_SHUFFLE = "shuffle"
    /** Plays the list from its first song and leaves shuffle as it is (a voice request). */
    const val MODE_GO = "go"

    fun song(contextUri: String, key: String) = "t|$contextUri|$key"
    fun downloaded(listUri: String, trackId: String) = "d|$listUri|$trackId"
    fun radio(seed: String, fallbackSong: String) = "radio|$seed|$fallbackSong"
    fun playContext(contextUri: String, mode: String) = "p|$contextUri|$mode"
    fun target(playlistId: String, name: String) = "target|$playlistId|$name"
    fun playlist(id: String) = "pl:$id"
    fun album(id: String) = "al:$id"
    fun artist(id: String, name: String) = "ar:$id|$name"
    fun show(id: String) = "sh:$id"

    /** The browse id of a song in the list it plays from (the car marks that row while it plays). */
    fun playingRow(contextUri: String, songUri: String) = song(contextUri, songUri)

    /** The context uri a "p|..." id plays. */
    fun contextOfNode(node: String): String? = when {
        node == "liked" -> LIKED_URI
        node.startsWith("pl:") -> "spotify:playlist:" + node.removePrefix("pl:")
        node.startsWith("al:") -> "spotify:album:" + node.removePrefix("al:")
        node.startsWith("ar:") -> "spotify:artist:" + node.removePrefix("ar:").substringBefore('|')
        else -> null
    }
}

/** The browse tree as data: no Android, no network, so it is tested as plain Kotlin. */
object AutoTree {
    const val LIST = MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    const val GRID = MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    const val CATEGORY_LIST = MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM

    /**
     * The most a folder of playlists, albums or artists lists, and a list of songs: a car shows a page
     * while driving, and a thousand covers each loaded through the artwork provider froze Android Auto.
     */
    const val MAX_FOLDER = 100
    const val MAX_SONGS = 150
    /** Home: how many recents, shelves and items per shelf. */
    const val HOME_RECENTS = 8
    const val HOME_SHELVES = 6
    const val HOME_PER_SHELF = 10
    /** What a search lists of each kind. */
    const val SEARCH_SONGS = 10
    const val SEARCH_OTHER = 8
    /** An episode's description, cut for the row. */
    private const val DESCRIPTION_MAX = 140

    fun rootTabs(s: AutoStrings): List<AutoNode> = listOf(
        AutoNode("home", s.home, iconRes = "aa_ic_home", childrenStyle = GRID),
        AutoNode("library", s.library, iconRes = "aa_ic_library", childrenStyle = CATEGORY_LIST),
        AutoNode("podcasts", s.podcasts, iconRes = "aa_ic_podcasts", childrenStyle = LIST),
        AutoNode("downloads", s.downloads, iconRes = "aa_ic_downloads", childrenStyle = LIST),
    )

    /** The Library tab: shortcuts only, so it opens with no session and no network. [targetName]: where "+" adds songs. */
    fun libraryTab(s: AutoStrings, targetName: String?): List<AutoNode> = listOf(
        AutoNode("liked", s.likedSongs, iconRes = "aa_ic_liked"),
        AutoNode("playlists", s.playlists, iconRes = "aa_ic_playlists", childrenStyle = GRID),
        AutoNode("albums", s.albums, iconRes = "aa_ic_albums", childrenStyle = GRID),
        AutoNode("artists", s.artists, iconRes = "aa_ic_artists", childrenStyle = GRID),
        AutoNode("addto", s.addToPlaylist, subtitle = targetName ?: s.addToPlaylistNone, iconRes = "aa_ic_playlist_add"),
    )

    /** Home's first group: what a driver wants without browsing. */
    fun quickPlay(s: AutoStrings): List<AutoNode> = listOf(
        AutoNode(AutoIds.playContext(AutoIds.LIKED_URI, AutoIds.MODE_SHUFFLE), s.shuffleLiked,
            iconRes = "aa_ic_shuffle", playable = true, group = s.quickPlay),
    )

    /** "Play", "Shuffle" and, when it has one, "Start radio", at the top of a list of songs. */
    fun contextHeader(s: AutoStrings, contextUri: String, name: String, radioSeed: String?, radioFallback: String): List<AutoNode> =
        listOfNotNull(
            AutoNode(AutoIds.playContext(contextUri, AutoIds.MODE_PLAY), s.play, subtitle = name, iconRes = "aa_ic_play", playable = true),
            AutoNode(AutoIds.playContext(contextUri, AutoIds.MODE_SHUFFLE), s.shuffle, subtitle = name, iconRes = "aa_ic_shuffle", playable = true),
            radioSeed?.let { radio(s, it, radioFallback, name) },
        )

    fun radio(s: AutoStrings, seed: String, fallbackSong: String, name: String) = AutoNode(
        AutoIds.radio(seed, fallbackSong), s.startRadio, subtitle = name, iconRes = "aa_ic_radio", playable = true,
    )

    /** A song (or an episode) row; [art] maps a cover url to its content URI. */
    fun song(contextUri: String, song: SongsModel, group: String?, downloaded: Boolean, art: (String?) -> String?): AutoNode {
        val episode = song.isEpisode
        val subtitle = if (episode) listOf(song.album, song.releaseDate.take(10)).filter { it.isNotBlank() }.joinToString(" · ")
        else song.singer
        return AutoNode(
            id = AutoIds.song(contextUri, song.url), title = song.title, subtitle = subtitle,
            iconUri = art(song.coverUri), playable = true, group = group,
            explicit = song.explicit, download = if (episode) null else if (downloaded) AutoNode.DOWNLOAD_YES else null,
            completion = if (episode) completionOf(song).first else null,
            completionPercent = if (episode) completionOf(song).second else null,
            description = if (episode) song.description.trim().takeIf { it.isNotEmpty() }?.take(DESCRIPTION_MAX) else null,
        )
    }

    /** An episode's progress as the car shows it: not played, partly (with how far) or fully. */
    fun completionOf(song: SongsModel): Pair<Int, Double?> = when {
        song.played -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED to null
        song.playedPositionMs > 0 && song.durationMs > 0 ->
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED to
                (song.playedPositionMs.toDouble() / song.durationMs).coerceIn(0.0, 1.0)
        else -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED to null
    }

    fun browsable(id: String, title: String, icon: String?, subtitle: String = "", group: String? = null, grid: Boolean = true) =
        AutoNode(id, title, subtitle, iconUri = icon, group = group, childrenStyle = if (grid) GRID else LIST)

    fun playlistEntry(e: LibraryEntry, art: (String?) -> String?, group: String? = null) =
        browsable(AutoIds.playlist(e.spotifyId), e.name, art(e.coverUri), e.subtitle, group)

    fun albumNode(id: String, name: String, cover: String?, subtitle: String, art: (String?) -> String?, group: String? = null) =
        browsable(AutoIds.album(id), name, art(cover), subtitle, group)

    fun artistNode(a: ArtistsModel, art: (String?) -> String?, group: String? = null) =
        browsable(AutoIds.artist(a.id, a.name), a.name, art(a.coverUri), group = group)

    /** An artist's page: play / shuffle / radio, then the top songs, then the albums. */
    fun artistPage(
        s: AutoStrings, id: String, name: String, top: List<SongsModel>, releases: List<AlbumsModel>,
        isDownloaded: (SongsModel) -> Boolean, art: (String?) -> String?,
    ): List<AutoNode> {
        val uri = "spotify:artist:$id"
        return contextHeader(s, uri, name, uri, "") +
            top.take(MAX_SONGS).map { song(uri, it, s.topSongs, isDownloaded(it), art) } +
            releases.filter { it.spotifyId.isNotBlank() }.take(12).map { albumNode(it.spotifyId, it.name, it.coverUri, it.year, art, s.albums) }
    }

    /**
     * Search results as titled groups: songs (after a radio of the best one), artists, albums, playlists,
     * podcasts and episodes. The rows of a group stay together, as the car draws a title above each run.
     */
    fun search(s: AutoStrings, r: SearchResults, isDownloaded: (SongsModel) -> Boolean, art: (String?) -> String?): List<AutoNode> {
        val out = ArrayList<AutoNode>()
        val songs = r.songs.take(SEARCH_SONGS)
        songs.firstOrNull()?.let { out += radio(s, it.url, it.url, it.title) }
        songs.forEach { out += song("", it, s.songs, isDownloaded(it), art) }
        r.artists.filter { it.id.isNotBlank() }.take(SEARCH_OTHER).forEach { out += artistNode(it, art, s.artists) }
        r.albums.filter { it.spotifyId.isNotBlank() }.take(SEARCH_OTHER).forEach { out += albumNode(it.spotifyId, it.name, it.coverUri, it.artists, art, s.albums) }
        r.playlists.filter { it.spotifyId.isNotBlank() && !it.isFolder }.take(SEARCH_OTHER).forEach { out += playlistEntry(it, art, s.playlists) }
        r.shows.filter { it.id.isNotBlank() }.take(SEARCH_OTHER).forEach { out += showNode(it, art, s.podcasts) }
        r.episodes.take(SEARCH_OTHER).forEach { out += song("", it, s.episodes, false, art) }
        return out
    }

    fun showNode(p: PodcastModel, art: (String?) -> String?, group: String? = null) =
        browsable(AutoIds.show(p.id), p.name, art(p.coverUri), p.publisher, group, grid = false)

    /**
     * Rows of the same group next to each other, in the order the groups first appear (rows without a
     * group count as one): the car draws a title where a run starts, and a group split in two shows twice.
     */
    fun contiguous(nodes: List<AutoNode>): List<AutoNode> {
        val order = LinkedHashMap<String?, MutableList<AutoNode>>()
        nodes.forEach { order.getOrPut(it.group) { ArrayList() } += it }
        return order.values.flatten()
    }
}
