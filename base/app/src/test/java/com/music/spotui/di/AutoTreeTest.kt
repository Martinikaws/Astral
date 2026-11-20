package com.music.spotui.di

import android.support.v4.media.MediaDescriptionCompat
import androidx.media.utils.MediaConstants
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.PodcastModel
import com.music.spotui.data.entity.SearchResults
import com.music.spotui.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android Auto browse tree: its shape, grouping and hints, and how it is kept between runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AutoTreeTest {
    private val s = AutoStrings()
    private val art: (String?) -> String? = { it?.let { u -> "content://art/$u" } }

    private fun song(n: Int, explicit: Boolean = false) =
        SongsModel(n, "Song $n", "Album", "Artist $n", "cover$n", "spotify:track:$n", spotifyTrackId = "$n", explicit = explicit)

    private fun episode(n: Int, played: Boolean = false, at: Long = 0, duration: Int = 0) = SongsModel(
        n, "Ep $n", "The Show", "Pub", "c$n", "episode:$n", durationMs = duration, played = played,
        playedPositionMs = at, releaseDate = "2026-05-0${n}T00:00:00Z", description = "x".repeat(300),
    )

    @Test fun fourTabsWithIconsInOrder() {
        val tabs = AutoTree.rootTabs(s)
        assertEquals(listOf("home", "library", "podcasts", "downloads"), tabs.map { it.id })
        assertTrue(tabs.all { it.iconRes != null && !it.playable })
        assertEquals(AutoTree.GRID, tabs.first().childrenStyle)
    }

    @Test fun libraryTabIsShortcutsAndNamesTheAddTarget() {
        val lib = AutoTree.libraryTab(s, "Road trip")
        assertEquals(listOf("liked", "playlists", "albums", "artists", "addto"), lib.map { it.id })
        assertEquals("Road trip", lib.last().subtitle)
        assertEquals(s.addToPlaylistNone, AutoTree.libraryTab(s, null).last().subtitle)
    }

    @Test fun listsStartWithPlayShuffleRadio() {
        val h = AutoTree.contextHeader(s, "spotify:playlist:p", "Mix", "spotify:playlist:p", "spotify:track:1")
        assertEquals(
            listOf("p|spotify:playlist:p|play", "p|spotify:playlist:p|shuffle", "radio|spotify:playlist:p|spotify:track:1"),
            h.map { it.id },
        )
        assertTrue(h.all { it.playable })
        assertEquals(2, AutoTree.contextHeader(s, "dl:", "Downloads", null, "").size)
    }

    @Test fun songRowsCarryExplicitDownloadAndGroup() {
        val n = AutoTree.song("spotify:album:a", song(1, explicit = true), "Songs", true, art)
        assertEquals("t|spotify:album:a|spotify:track:1", n.id)
        assertTrue(n.explicit)
        assertEquals(AutoNode.DOWNLOAD_YES, n.download)
        assertEquals("content://art/cover1", n.iconUri)
        assertEquals("Songs", n.group)
        assertNull(AutoTree.song("", song(2), null, false, art).download)
    }

    @Test fun episodesShowProgress() {
        val full = AutoTree.song("c", episode(1, played = true), null, false, art)
        assertEquals(MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED, full.completion)
        val partly = AutoTree.song("c", episode(2, at = 30_000, duration = 120_000), null, false, art)
        assertEquals(MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED, partly.completion)
        assertEquals(0.25, partly.completionPercent!!, 1e-9)
        assertEquals(MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED, AutoTree.song("c", episode(3), null, false, art).completion)
        assertEquals(140, partly.description!!.length)
        assertEquals("The Show · 2026-05-02", partly.subtitle)
    }

    @Test fun artistPageIsHeaderThenTopSongsThenAlbums() {
        val rel = listOf(
            AlbumsModel(1, "Artist", "cov", "Rel", "2020-01-01", spotifyId = "al1"),
            AlbumsModel(2, "Artist", "cov", "NoId", "2019"),
        )
        val page = AutoTree.artistPage(s, "ar1", "Artist", listOf(song(1), song(2)), rel, { false }, art)
        assertEquals(
            listOf("p|spotify:artist:ar1|play", "p|spotify:artist:ar1|shuffle", "radio|spotify:artist:ar1|"),
            page.take(3).map { it.id },
        )
        assertEquals(listOf(s.topSongs, s.topSongs), page.drop(3).take(2).map { it.group })
        assertEquals(listOf("al:al1"), page.drop(5).map { it.id })
        assertEquals(s.albums, page.last().group)
    }

    @Test fun searchIsGroupedByKindInOrder() {
        val r = SearchResults(
            songs = listOf(song(1), song(2)),
            artists = listOf(ArtistsModel("A", "ac", "ar1"), ArtistsModel("NoId", "", "")),
            albums = listOf(AlbumsModel(1, "Art", "cov", "Alb", "2020", spotifyId = "al1")),
            playlists = listOf(
                LibraryEntry("pl1", "PL", "by me", "pc", true),
                LibraryEntry("f", "Folder", "", "", true, folderUri = "spotify:folder"),
            ),
            shows = listOf(PodcastModel("sh1", "Show", "Pub", "sc")),
            episodes = listOf(episode(1)),
        )
        val out = AutoTree.search(s, r, { false }, art)
        assertTrue(out.first().id.startsWith("radio|"))
        assertEquals(
            listOf(s.songs, s.artists, s.albums, s.playlists, s.podcasts, s.episodes),
            out.mapNotNull { it.group }.distinct(),
        )
        assertEquals(listOf("ar:ar1|A", "al:al1", "pl:pl1", "sh:sh1"), out.filter { !it.playable }.map { it.id })
        assertEquals(out, AutoTree.contiguous(out))
    }

    @Test fun contiguousKeepsGroupsTogetherInFirstSeenOrder() {
        val rows = listOf(
            AutoNode("1", "a", group = "X"), AutoNode("2", "b", group = "Y"),
            AutoNode("3", "c", group = "X"), AutoNode("4", "d"),
        )
        assertEquals(listOf("1", "3", "2", "4"), AutoTree.contiguous(rows).map { it.id })
    }

    @Test fun mediaItemCarriesTheHints() {
        val n = AutoNode(
            "t|c|k", "Title", "Sub", iconUri = "content://art/1", playable = true, group = "Songs",
            explicit = true, download = AutoNode.DOWNLOAD_YES,
            completion = MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
            completionPercent = 0.5,
        )
        val item = n.toMediaItem("app.test")
        assertTrue(item.isPlayable)
        val e = item.description.extras!!
        assertEquals("Songs", e.getString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE))
        assertEquals(MediaConstants.METADATA_VALUE_ATTRIBUTE_PRESENT, e.getLong(MediaConstants.METADATA_KEY_IS_EXPLICIT))
        assertEquals(MediaDescriptionCompat.STATUS_DOWNLOADED, e.getLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS))
        assertEquals(0.5, e.getDouble(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE), 1e-9)
        assertEquals("content://art/1", item.description.iconUri.toString())
    }

    @Test fun tabIconIsAResourceUriOfThePackage() {
        val item = AutoTree.rootTabs(s).first().toMediaItem("app.test")
        assertTrue(item.isBrowsable)
        assertEquals("android.resource://app.test/drawable/aa_ic_home", item.description.iconUri.toString())
        assertEquals(AutoTree.GRID, item.description.extras!!.getInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE))
        assertFalse(item.description.extras!!.containsKey(MediaConstants.METADATA_KEY_IS_EXPLICIT))
    }

    @Test fun nodesSurviveTheTextTheyAreKeptAs() {
        val nodes = AutoTree.rootTabs(s) + AutoTree.song("c", episode(2, at = 1, duration = 4), "G", true, art) + AutoNode("x", "X")
        assertEquals(nodes, AutoNodeJson.decode(AutoNodeJson.encode(nodes)))
        assertNull(AutoNodeJson.decode("not json"))
    }

    @Test fun contextOfNodeIds() {
        assertEquals(AutoIds.LIKED_URI, AutoIds.contextOfNode("liked"))
        assertEquals("spotify:playlist:p", AutoIds.contextOfNode("pl:p"))
        assertEquals("spotify:artist:a", AutoIds.contextOfNode("ar:a|Name"))
        assertNull(AutoIds.contextOfNode("home"))
    }
}
