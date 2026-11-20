package com.music.spotui.di

import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.PodcastModel
import com.music.spotui.data.entity.SearchResults
import com.music.spotui.data.entity.SongsModel
import java.text.Normalizer

/**
 * "Play X" by voice (Assistant, the car's microphone): what was asked, and which search result it
 * means. The assistant fills [android.provider.MediaStore.EXTRA_MEDIA_FOCUS] and the artist / album /
 * title extras when it understood the kind ("play the album Thriller"); with a bare phrase the best
 * exact match of any kind wins. Pure: no Android, no network.
 */
object AutoVoice {
    enum class Focus { ANY, SONG, ARTIST, ALBUM, PLAYLIST }

    /** What was asked. [query] is the spoken phrase, the rest the assistant's structured extras (blank: none). */
    data class Request(
        val query: String,
        val focus: Focus = Focus.ANY,
        val artist: String = "",
        val album: String = "",
        val title: String = "",
        val playlist: String = "",
    )

    /** The search result a request resolves to. */
    sealed interface Pick {
        data class Song(val song: SongsModel) : Pick
        data class Artist(val artist: ArtistsModel) : Pick
        data class Album(val album: AlbumsModel) : Pick
        data class Playlist(val playlist: LibraryEntry) : Pick
        data class Show(val show: PodcastModel) : Pick
        data object None : Pick
    }

    // MediaStore's content types (EXTRA_MEDIA_FOCUS), as literals so this stays plain Kotlin.
    private const val FOCUS_SONG = "vnd.android.cursor.item/audio"
    private const val FOCUS_ARTIST = "vnd.android.cursor.item/artist"
    private const val FOCUS_ALBUM = "vnd.android.cursor.item/album"
    private const val FOCUS_PLAYLIST = "vnd.android.cursor.item/playlist"

    fun focusOf(mediaFocus: String?): Focus = when (mediaFocus) {
        FOCUS_SONG -> Focus.SONG
        FOCUS_ARTIST -> Focus.ARTIST
        FOCUS_ALBUM -> Focus.ALBUM
        FOCUS_PLAYLIST -> Focus.PLAYLIST
        else -> Focus.ANY
    }

    /** The words to search Spotify for. */
    fun searchText(r: Request): String {
        val text = when (r.focus) {
            Focus.SONG -> listOf(r.title, r.artist)
            Focus.ARTIST -> listOf(r.artist)
            Focus.ALBUM -> listOf(r.album, r.artist)
            Focus.PLAYLIST -> listOf(r.playlist)
            Focus.ANY -> emptyList()
        }.filter { it.isNotBlank() }.joinToString(" ")
        // Structured extras of an unlabelled request ("play Thriller by Michael Jackson"): all of them.
        return text.ifBlank { r.query.ifBlank { listOf(r.title, r.album, r.playlist, r.artist).filter { it.isNotBlank() }.joinToString(" ") } }
    }

    /** Lower case, no accents, no punctuation, single spaces: names compare the way they are spoken. */
    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    /** The result [r] means among [results], or [Pick.None]. */
    fun pick(r: Request, results: SearchResults): Pick {
        val songs = results.songs
        val artists = results.artists.filter { it.id.isNotBlank() }
        val albums = results.albums.filter { it.spotifyId.isNotBlank() }
        val playlists = results.playlists.filter { it.spotifyId.isNotBlank() && !it.isFolder }
        val shows = results.shows.filter { it.id.isNotBlank() }
        val said = normalize(searchText(r))
        val artistSaid = normalize(r.artist)

        fun songBy(title: String): SongsModel? {
            val t = normalize(title)
            val named = songs.filter { normalize(it.title) == t }.ifEmpty { songs.filter { t.isNotEmpty() && normalize(it.title).contains(t) } }
            return named.firstOrNull { artistSaid.isEmpty() || normalize(it.singer).contains(artistSaid) } ?: named.firstOrNull()
        }
        fun artistNamed(n: String) = artists.firstOrNull { normalize(it.name) == n }
        fun albumNamed(n: String) = albums.firstOrNull { normalize(it.name) == n && (artistSaid.isEmpty() || normalize(it.artists).contains(artistSaid)) }
            ?: albums.firstOrNull { normalize(it.name) == n }
        fun playlistNamed(n: String) = playlists.firstOrNull { normalize(it.name) == n }

        when (r.focus) {
            Focus.SONG -> (songBy(r.title.ifBlank { r.query }) ?: songs.firstOrNull())?.let { return Pick.Song(it) }
            Focus.ARTIST -> (artistNamed(normalize(r.artist.ifBlank { r.query })) ?: artists.firstOrNull())?.let { return Pick.Artist(it) }
            Focus.ALBUM -> (albumNamed(normalize(r.album.ifBlank { r.query })) ?: albums.firstOrNull())?.let { return Pick.Album(it) }
            Focus.PLAYLIST -> (playlistNamed(normalize(r.playlist.ifBlank { r.query }))
                ?: playlists.firstOrNull { normalize(it.name).contains(normalize(r.playlist.ifBlank { r.query })) }
                ?: playlists.firstOrNull())?.let { return Pick.Playlist(it) }
            Focus.ANY -> {}
        }
        // A bare phrase: the exact name of an artist, then a song, an album, a playlist, a show.
        artistNamed(said)?.let { return Pick.Artist(it) }
        songs.firstOrNull { normalize(it.title) == said }?.let { return Pick.Song(it) }
        albumNamed(said)?.let { return Pick.Album(it) }
        playlistNamed(said)?.let { return Pick.Playlist(it) }
        shows.firstOrNull { normalize(it.name) == said }?.let { return Pick.Show(it) }
        // "Blinding Lights by The Weeknd": a song whose title and artist are both in the phrase.
        songs.firstOrNull { val t = normalize(it.title); t.isNotEmpty() && said.contains(t) && said.contains(normalize(it.singer.substringBefore(','))) }
            ?.let { return Pick.Song(it) }
        // Nothing exact: Spotify's best song, as a voice search always did, then the other kinds.
        songs.firstOrNull()?.let { return Pick.Song(it) }
        artists.firstOrNull()?.let { return Pick.Artist(it) }
        albums.firstOrNull()?.let { return Pick.Album(it) }
        playlists.firstOrNull()?.let { return Pick.Playlist(it) }
        shows.firstOrNull()?.let { return Pick.Show(it) }
        return Pick.None
    }
}
