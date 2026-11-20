package com.music.spotui.di

import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.PodcastModel
import com.music.spotui.data.entity.SearchResults
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.AutoVoice.Focus
import com.music.spotui.di.AutoVoice.Pick
import com.music.spotui.di.AutoVoice.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which result "play X" means: the assistant's extras decide the kind, a bare phrase the best exact match. */
class AutoVoiceTest {
    private fun song(n: Int, title: String, artist: String) =
        SongsModel(n, title, "Album", artist, "c", "spotify:track:$n", spotifyTrackId = "$n")

    private val results = SearchResults(
        songs = listOf(song(1, "Thriller (Live)", "Cover Band"), song(2, "Thriller", "Michael Jackson"), song(3, "Billie Jean", "Michael Jackson")),
        artists = listOf(ArtistsModel("Michael Jackson", "c", "mj"), ArtistsModel("Thriller Band", "c", "tb")),
        albums = listOf(AlbumsModel(1, "Michael Jackson", "c", "Thriller", "1982", spotifyId = "alThriller"), AlbumsModel(2, "Other", "c", "Bad", "1987", spotifyId = "alBad")),
        playlists = listOf(LibraryEntry("plRoad", "Road Trip", "", "", true), LibraryEntry("plGym", "Gym Mix", "", "", true)),
        shows = listOf(PodcastModel("shPod", "Daily News", "Pub", "c")),
    )

    @Test fun focusFromTheMediaType() {
        assertEquals(Focus.ARTIST, AutoVoice.focusOf("vnd.android.cursor.item/artist"))
        assertEquals(Focus.ALBUM, AutoVoice.focusOf("vnd.android.cursor.item/album"))
        assertEquals(Focus.SONG, AutoVoice.focusOf("vnd.android.cursor.item/audio"))
        assertEquals(Focus.PLAYLIST, AutoVoice.focusOf("vnd.android.cursor.item/playlist"))
        assertEquals(Focus.ANY, AutoVoice.focusOf("vnd.android.cursor.item/*"))
        assertEquals(Focus.ANY, AutoVoice.focusOf(null))
    }

    @Test fun searchTextUsesTheStructuredExtras() {
        assertEquals("Thriller Michael Jackson", AutoVoice.searchText(Request("x", Focus.SONG, artist = "Michael Jackson", title = "Thriller")))
        assertEquals("Michael Jackson", AutoVoice.searchText(Request("x", Focus.ARTIST, artist = "Michael Jackson")))
        assertEquals("Bad Michael Jackson", AutoVoice.searchText(Request("x", Focus.ALBUM, artist = "Michael Jackson", album = "Bad")))
        assertEquals("some words", AutoVoice.searchText(Request("some words")))
        assertEquals("sing along", AutoVoice.searchText(Request("sing along", Focus.PLAYLIST)))
    }

    @Test fun artistFocusPicksTheExactArtist() {
        val p = AutoVoice.pick(Request("michael jackson", Focus.ARTIST, artist = "Michael Jackson"), results)
        assertEquals("mj", (p as Pick.Artist).artist.id)
    }

    @Test fun albumFocusPicksTheAlbumNotTheSong() {
        val p = AutoVoice.pick(Request("thriller", Focus.ALBUM, album = "Thriller", artist = "Michael Jackson"), results)
        assertEquals("alThriller", (p as Pick.Album).album.spotifyId)
    }

    @Test fun songFocusPicksTheExactTitleBySaidArtist() {
        val p = AutoVoice.pick(Request("thriller", Focus.SONG, title = "Thriller", artist = "Michael Jackson"), results)
        assertEquals("spotify:track:2", (p as Pick.Song).song.url)
    }

    @Test fun playlistFocusMatchesByNameThenContains() {
        assertEquals("plGym", (AutoVoice.pick(Request("gym mix", Focus.PLAYLIST, playlist = "gym mix"), results) as Pick.Playlist).playlist.spotifyId)
        assertEquals("plRoad", (AutoVoice.pick(Request("road", Focus.PLAYLIST, playlist = "road"), results) as Pick.Playlist).playlist.spotifyId)
    }

    @Test fun barePhrasePrefersAnExactArtistThenSongThenAlbum() {
        assertTrue(AutoVoice.pick(Request("Michael Jackson"), results) is Pick.Artist)
        assertEquals("spotify:track:2", (AutoVoice.pick(Request("thriller"), results) as Pick.Song).song.url)
        assertEquals("alBad", (AutoVoice.pick(Request("BAD!"), results) as Pick.Album).album.spotifyId)
        assertEquals("plRoad", (AutoVoice.pick(Request("Road Trip"), results) as Pick.Playlist).playlist.spotifyId)
        assertEquals("shPod", (AutoVoice.pick(Request("daily news"), results) as Pick.Show).show.id)
    }

    @Test fun accentsAndPunctuationDoNotMatter() {
        val r = SearchResults(artists = listOf(ArtistsModel("Beyoncé", "c", "bey")))
        assertEquals("bey", (AutoVoice.pick(Request("beyonce"), r) as Pick.Artist).artist.id)
    }

    @Test fun titleAndArtistInOnePhrase() {
        val p = AutoVoice.pick(Request("billie jean by michael jackson"), results)
        assertEquals("spotify:track:3", (p as Pick.Song).song.url)
    }

    @Test fun unknownPhraseFallsBackToTheFirstSongThenOtherKinds() {
        assertEquals("spotify:track:1", (AutoVoice.pick(Request("zzz"), results) as Pick.Song).song.url)
        val only = SearchResults(playlists = listOf(LibraryEntry("p", "P", "", "", true)))
        assertTrue(AutoVoice.pick(Request("zzz"), only) is Pick.Playlist)
        assertEquals(Pick.None, AutoVoice.pick(Request("zzz"), SearchResults()))
    }

    @Test fun foldersAndRowsWithoutIdsAreNeverPicked() {
        val r = SearchResults(
            playlists = listOf(LibraryEntry("f", "Folder", "", "", true, folderUri = "spotify:folder")),
            artists = listOf(ArtistsModel("No Id", "c", "")),
        )
        assertEquals(Pick.None, AutoVoice.pick(Request("folder"), r))
    }
}
