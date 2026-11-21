package com.music.spotui.ui.immersive.squared

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.local.ListenHistory
import com.music.spotui.ui.immersive.squared.model.CatalogPlaylist
import com.music.spotui.ui.immersive.squared.model.CatalogTrack
import com.music.spotui.ui.immersive.squared.model.HomeShelf
import com.music.spotui.ui.immersive.squared.model.SquaredListening
import com.music.spotui.ui.immersive.squared.model.SquaredTargetKind
import com.music.spotui.ui.immersive.squared.model.SquaredUris
import com.music.spotui.ui.immersive.squared.model.TabContents
import com.music.spotui.ui.immersive.squared.model.compactCount
import com.music.spotui.ui.immersive.squared.model.indexedByTrack
import com.music.spotui.ui.immersive.squared.model.recentTracks
import com.music.spotui.ui.immersive.squared.model.sortedByRecentlyOpened
import com.music.spotui.ui.immersive.squared.model.toCatalogTrack
import com.music.spotui.ui.immersive.squared.model.toListeningEvent
import com.music.spotui.ui.immersive.squared.model.withLikedSecond
import com.music.spotui.ui.immersive.squared.model.withPinnedFirst
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SquaredMappingTest {
    private fun song(
        id: Int = 1, url: String = "u$id", trackId: String = "T$id", singer: String = "Ada, Bo",
        rowUid: String = "", artistIds: List<String> = listOf("A1", "A2"),
    ) = SongsModel(
        id = id, title = "Song $id", album = "Alb", singer = singer, coverUri = "c", url = url,
        spotifyTrackId = trackId, artistIds = artistIds, albumId = "AL", releaseDate = "2020-05-01", rowUid = rowUid,
    )

    private fun playlist(uri: String) = CatalogPlaylist(uri = uri, name = uri)

    @Test fun legacyUserPlaylistAddressParses() {
        val t = SquaredUris.parse("spotify:user:someone:playlist:PL9", "Mix")
        assertEquals(SquaredTargetKind.PLAYLIST, t.kind)
        assertEquals("PL9", t.id)
    }

    @Test fun addressesRoundTrip() {
        val album = SquaredUris.parse(SquaredUris.album("X1", "Name", "Art"), "Name", "Art")
        assertEquals(SquaredTargetKind.ALBUM, album.kind)
        assertEquals("X1", album.id)
        val byName = SquaredUris.parse(SquaredUris.album("", "Name", "Art"))
        assertEquals(SquaredTargetKind.ALBUM, byName.kind)
        assertEquals("Name", byName.name)
        assertEquals("Art", byName.artist)
        assertEquals("", byName.id)
        assertEquals(SquaredTargetKind.ARTIST, SquaredUris.parse(SquaredUris.artist("", "Ada")).kind)
        assertEquals("Ada", SquaredUris.parse(SquaredUris.artist("", "Ada")).name)
        assertEquals("P9", SquaredUris.parse(SquaredUris.playlist("P9")).id)
        assertEquals(SquaredTargetKind.LIKED, SquaredUris.parse(SquaredUris.playlist(SquaredUris.LIKED_ID)).kind)
        assertEquals("S1", SquaredUris.parse(SquaredUris.show("S1")).id)
        assertEquals(SquaredTargetKind.EPISODE, SquaredUris.parse(SquaredUris.episode("E1")).kind)
        assertEquals(SquaredTargetKind.UNKNOWN, SquaredUris.parse("nonsense").kind)
    }

    @Test fun ownEntriesAreKeptApart() {
        val target = SquaredUris.parse(SquaredUris.entry("local-files"), "Local")
        assertEquals(SquaredTargetKind.ENTRY, target.kind)
        assertEquals("local-files", target.id)
        assertTrue(SquaredUris.isOwnEntry("downloads"))
        assertTrue(SquaredUris.isOwnEntry("local-playlist:7"))
        assertEquals(false, SquaredUris.isOwnEntry("37i9dQZF1E4abc"))
    }

    @Test fun folderKeepsItsUri() {
        val t = SquaredUris.parse("spotify:user:me:folder:F1", "Folder")
        assertEquals(SquaredTargetKind.FOLDER, t.kind)
        assertEquals("spotify:user:me:folder:F1", t.uri)
    }

    @Test fun songBecomesTrackWithSplitArtists() {
        val t = song().toCatalogTrack()
        assertEquals("spotify:track:T1", t.uri)
        assertEquals(listOf("Ada", "Bo"), t.artists.map { it.name })
        assertEquals("spotify:artist:A2", t.artists[1].uri)
        assertEquals("spotify:album:AL", t.albumUri)
        assertEquals("2020", t.year)
    }

    @Test fun episodeBecomesEpisodeAddress() {
        val t = song(url = "episode:E5", trackId = "").toCatalogTrack()
        assertEquals("spotify:episode:E5", t.uri)
    }

    @Test fun trackIndexFindsSongsAndRows() {
        val a = song(1, rowUid = "r1")
        val b = song(2)
        val index = listOf(a, b).indexedByTrack()
        assertEquals(a, index["spotify:track:T1"])
        assertEquals(a, index["spotify:track:T1#r1"])
        assertEquals(b, index["spotify:track:T2"])
        assertNull(index["spotify:track:none"])
    }

    @Test fun pinnedListsComeFirstInPinOrder() {
        val list = listOf("a", "b", "c", "d").map(::playlist)
        assertEquals(listOf("c", "a", "b", "d"), list.withPinnedFirst(listOf("c", "a")).map { it.uri })
        assertEquals(list, list.withPinnedFirst(emptyList()))
    }

    @Test fun likedSongsGoesSecondOnlyWhenPresent() {
        val list = listOf("a", SquaredUris.LIKED, "b").map(::playlist)
        assertEquals(listOf(SquaredUris.LIKED, "a", "b"), list.withLikedSecond().map { it.uri })
        val none = listOf("a", "b").map(::playlist)
        assertEquals(none, none.withLikedSecond())
    }

    @Test fun recentlyOpenedOrderKeepsTheRest() {
        val list = listOf("a", "b", "c").map(::playlist)
        assertEquals(listOf("c", "a", "b"), list.sortedByRecentlyOpened(listOf("c")).map { it.uri })
        assertEquals(list, list.sortedByRecentlyOpened(emptyList()))
    }

    @Test fun chipsFollowTheRow() {
        assertEquals(TabContents.Chip.ARTISTS, TabContents.chipOf(HomeShelf("t", emptyList(), artist = "Ada")))
        assertEquals(TabContents.Chip.FOR_YOU, TabContents.chipOf(HomeShelf("t", emptyList(), gathered = true)))
        assertEquals(
            TabContents.Chip.FOR_YOU,
            TabContents.chipOf(HomeShelf("t", listOf(playlist("spotify:playlist:37i9dQZF1E3x")))),
        )
        assertEquals(TabContents.Chip.DISCOVER, TabContents.chipOf(HomeShelf("t", listOf(playlist("spotify:playlist:other")))))
    }

    @Test fun listenerCountsAreCompact() {
        assertNull(compactCount(null))
        assertNull(compactCount(0))
        assertEquals("999", compactCount(999))
        assertEquals("12K", compactCount(12_345))
        assertEquals("1.2M", compactCount(1_234_567))
        assertEquals("3M", compactCount(3_000_000))
        assertEquals("1.5B", compactCount(1_500_000_000))
    }

    private fun listen(at: Long, key: String, ms: Long = 60_000, artist: String = "Ada") = ListenHistory.Listen(
        atMs = at, key = key, title = "t$key", artist = artist, album = "al", cover = "", playedMs = ms,
        source = ListenHistory.Source.SPOTIFY,
    )

    @Test fun recentTracksAreNewestFirstAndDistinct() {
        val tracks: List<CatalogTrack> = recentTracks(
            listOf(listen(1, "a"), listen(3, "b"), listen(2, "a"), listen(4, "c")), limit = 3,
        )
        assertEquals(listOf("c", "b", "a"), tracks.map { it.uri })
    }

    @Test fun monthSummaryCountsOnlyThatMonth() {
        val zone = ZoneOffset.UTC
        fun at(month: Int, day: Int) = YearMonth.of(2026, month).atDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = listOf(
            listen(at(5, 2), "a", 120_000), listen(at(5, 9), "a", 60_000), listen(at(5, 20), "b", 60_000, artist = "Bo"),
            listen(at(6, 1), "c"),
        ).map { it.toListeningEvent() }
        val may = SquaredListening.summarize(events, YearMonth.of(2026, 5), zone)
        assertEquals(3, may.plays)
        assertEquals(4L, may.minutes)
        assertEquals("a", may.tracks.first().first.uri)
        assertEquals(2, may.tracks.first().second)
        assertEquals(listOf("Ada" to 2, "Bo" to 1), may.artists)
        assertEquals(YearMonth.of(2026, 5), SquaredListening.firstMonth(events, YearMonth.of(2026, 8), zone))
        assertEquals(YearMonth.of(2026, 8), SquaredListening.firstMonth(emptyList(), YearMonth.of(2026, 8), zone))
    }
}
