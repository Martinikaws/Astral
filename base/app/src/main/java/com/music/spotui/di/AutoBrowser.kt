package com.music.spotui.di

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.host.android.PlaybackService
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.ContextPageModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.offline.LocalPlayPlan
import com.music.spotui.data.offline.OfflineCollections
import com.music.spotui.data.offline.OfflineStore
import com.music.spotui.data.offline.OfflineTrack
import app.myspoti.api.MediaKind
import app.myspoti.api.best
import app.myspoti.api.groupRecents
import com.music.spotui.data.DataSaver
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The hidden player did not come up in time (cold start, no network). The browse answer is
 * an empty folder then — MediaBrowserCompat has no error reply — and the log says why.
 */
class AudioNotReady(what: String) : IllegalStateException("player not ready: $what")

/**
 * Android Auto / media browser tree, built from the app's own data (not the web page).
 *
 * Root tabs (Auto allows four): Home (a quick-play group, recently played, then the home feed's
 * shelves, each a titled group), Library (Liked Songs, Playlists, Albums, Artists, "Add to playlist"),
 * Podcasts (New episodes, then the followed shows), Downloads (served from the files, no network
 * needed). Search answers songs, artists, albums, playlists, podcasts and episodes as titled groups.
 * The rows are [AutoNode]s ([AutoTree] builds them, testable); this object reads the data, keeps the
 * last answers ([remember]) and plays what is tapped. Tapping a song plays it in its context, the
 * way the album / playlist screens do: a downloaded song plays the local copy (see [playTrack]).
 * Lists of songs start with "Play", "Shuffle" and "Start radio".
 *
 * Media ids: "home", "library", "podcasts", "downloads" (tabs); "liked", "playlists", "albums", "artists",
 * "dsongs", "newep", "addto" (lists); "pl:<id>", "al:<id>", "ar:<id>|<name>", "dl:<list uri>", "sh:<show id>" (contexts);
 * "t|<context uri>|<play key>" (a song), "d|<list uri or empty>|<track id>" (a downloaded song),
 * "radio|<seed uri>|<fallback song uri>" (start a radio), "p|<context uri>|play/shuffle/go" (a whole list),
 * "target|<playlist id>|<name>" (the playlist the car's add button adds to: chosen only in Library > Add to playlist).
 */
object AutoBrowser {
    const val ROOT = "root"
    private const val TIMEOUT_MS = 15_000L
    /** Cold start: the car binds before the hidden Web Player is up; browse waits this long. */
    private const val READY_WAIT_MS = 25_000L

    // Contexts recently listed, so a tap plays with the right queue: the last few only, in the order used.
    private val pages: MutableMap<String, ContextPageModel> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ContextPageModel>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ContextPageModel>) = size > MAX_PAGES
        })
    private const val MAX_PAGES = 30
    /** An artist's top songs as listed, for "Play" / "Shuffle" on its page (context uri -> songs). */
    private val artistTop = java.util.concurrent.ConcurrentHashMap<String, List<SongsModel>>()
    /** How long a long list keeps coming in after its first page was shown ([contextPage]). */
    private const val FULL_LIST_MS = 120_000L
    private const val LIKED_URI = AutoIds.LIKED_URI
    /** [ContextPageModel.type] of a show's episodes listed here. */
    private const val SHOW = "show"

    // ── Kept folders (shown at once, read again behind) ──

    private class Entry(val nodes: List<AutoNode>, val at: Long)
    private val memory: MutableMap<String, Entry> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > MAX_KEPT
        })
    private const val MAX_KEPT = 40
    /** A folder read this recently is not read again (the car asks again right after a change notice). */
    private const val FRESH_MS = 15_000L
    /** The folders kept between runs: the ones of the first screens, small and the same for a while. */
    private val persisted = setOf("home", "playlists", "albums", "artists", "podcasts", "newep", "addto")
    private const val CACHE_PREFS = "auto_browse_cache"
    private fun cachePrefs() = Graph.app.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    private fun remember(parentId: String, nodes: List<AutoNode>) {
        if (nodes.isEmpty()) return
        memory[parentId] = Entry(nodes, android.os.SystemClock.elapsedRealtime())
        if (parentId in persisted) cachePrefs().edit().putString(parentId, AutoNodeJson.encode(nodes)).apply()
    }

    /** The folder as last listed, in this run or an earlier one; null when never. */
    fun cached(context: Context, parentId: String): List<MediaItem>? {
        if (parentId == ROOT || parentId in LOCAL_FOLDERS || parentId.startsWith("dl:")) return null
        val nodes = memory[parentId]?.nodes
            ?: parentId.takeIf { it in persisted }?.let { cachePrefs().getString(it, null) }?.let(AutoNodeJson::decode)?.takeIf { it.isNotEmpty() }
            ?: return null
        return nodes.map { it.toMediaItem(context.packageName) }
    }

    /** Answered from the files, no session needed: nothing is kept for them. */
    private val LOCAL_FOLDERS = setOf("library", "downloads", "dsongs")

    /** The account signs out: nothing it listed stays for the next one. */
    fun clearSession() {
        pages.clear(); artistTop.clear(); memory.clear()
        cachePrefs().edit().clear().apply()
        CarPlaylist.clear()
    }
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun strings(context: Context) = AutoStrings.from(context)
    private fun art(url: String?): String? = ArtworkProvider.uriFor(url)?.toString()
    private fun isDownloaded(song: SongsModel) =
        !song.isEpisode && OfflineStore.isDownloaded(song.spotifyTrackId.ifBlank { song.url.removePrefix("spotify:track:") })

    /**
     * Browse for [parentId]. The tabs, the Library list and the Downloads answer at once (no
     * session, no network); every other node needs the session, and waits for it instead of
     * returning an empty list, which Android Auto keeps as the folder's content until it
     * unsubscribes (issue #41, cold start).
     */
    suspend fun children(context: Context, parentId: String): List<MediaItem> =
        nodes(context, parentId).map { it.toMediaItem(context.packageName) }

    private suspend fun nodes(context: Context, parentId: String): List<AutoNode> {
        val s = strings(context)
        when {
            parentId == ROOT -> return AutoTree.rootTabs(s)
            parentId == "library" -> return AutoTree.libraryTab(s, CarPlaylist.name)
            parentId == "downloads" -> return downloadsTab(s)
            parentId == "dsongs" -> return downloadedSongs(s, "")
            parentId.startsWith("dl:") -> return downloadedSongs(s, parentId.removePrefix("dl:"))
        }
        memory[parentId]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.at < FRESH_MS }?.let { return it.nodes }
        // No connection: a downloaded list opens from its files, the web page is not needed.
        if (!OfflineStore.isOnline(context)) offlineContext(s, parentId)?.let { return it }
        if (!awaitPlayer()) throw AudioNotReady("browse $parentId")
        val nodes = when {
            parentId == "home" -> homeTab(s)
            parentId == "playlists" || parentId == "albums" -> {
                val wantPlaylists = parentId == "playlists"
                val entries = (firstResult { Graph.api.getLibrary() } ?: emptyList())
                    .filter { it.isPlaylist == wantPlaylists && it.spotifyId != com.music.spotui.data.api.Api.HomeCache.LIKED_SONGS_ID }
                entries.take(AutoTree.MAX_FOLDER).map { e ->
                    if (wantPlaylists) AutoTree.playlistEntry(e, ::art)
                    else AutoTree.albumNode(e.spotifyId, e.name, e.coverUri, e.subtitle, ::art)
                }
            }
            parentId == "artists" -> Graph.api.getFollowedArtists().filter { it.id.isNotBlank() }.take(AutoTree.MAX_FOLDER).map { AutoTree.artistNode(it, ::art) }
            parentId == "podcasts" -> podcastsTab(s)
            parentId == "newep" -> Graph.api.getNewEpisodes().take(AutoTree.MAX_SONGS).map { AutoTree.song("", it, null, false, ::art) }
            parentId == "addto" -> targetPicker(s)
            parentId.startsWith("sh:") -> showEpisodes(parentId.removePrefix("sh:"))
            parentId == "liked" || parentId.startsWith("pl:") || parentId.startsWith("al:") -> tracksOf(s, contextPage(parentId))
            parentId.startsWith("ar:") -> artistPage(s, parentId)
            else -> emptyList()
        }
        remember(parentId, nodes)
        return nodes
    }

    // ── Tabs ──

    /**
     * "New episodes" (the episodes not played yet of the followed shows), then the followed shows
     * (the phone's Library > Podcasts). Spotify not answering is an error, not an empty tab.
     */
    private suspend fun podcastsTab(s: AutoStrings): List<AutoNode> {
        val entries = Graph.api.followedShows().getOrElse { throw AudioNotReady("podcasts: ${it.message}") }
        val showPrefix = com.music.spotui.data.api.Api.SHOW_PREFIX
        return entries.take(AutoTree.MAX_FOLDER).mapNotNull { e ->
            when {
                e.spotifyId == com.music.spotui.data.api.Api.NEW_EPISODES_ID ->
                    AutoTree.browsable("newep", e.name, art(e.coverUri), e.subtitle, grid = false)
                e.spotifyId.startsWith(showPrefix) ->
                    AutoTree.browsable(AutoIds.show(e.spotifyId.removePrefix(showPrefix)), e.name, art(e.coverUri), e.subtitle, s.podcasts, grid = false)
                else -> null
            }
        }.let(AutoTree::contiguous)
    }

    /** A show's latest episodes (the first page, newest first); a tap plays the episode in its show, as the show screen does. */
    private suspend fun showEpisodes(showId: String): List<AutoNode> {
        val episodes = firstResult { Graph.api.getShowEpisodes(showId) }.orEmpty()
        if (episodes.isEmpty()) return emptyList()
        val page = ContextPageModel(uri = "spotify:show:$showId", type = SHOW, name = episodes.first().album,
            coverUri = episodes.first().coverUri, tracks = episodes.take(AutoTree.MAX_SONGS))
        pages[page.uri] = page
        return page.tracks.map { AutoTree.song(page.uri, it, null, false, ::art) }
    }

    /** A quick-play group, the account's recents, then the home feed's shelves (mixes, jump back in, …), each a titled group. */
    private suspend fun homeTab(s: AutoStrings): List<AutoNode> = kotlinx.coroutines.coroutineScope {
        val recents = async { runCatching { recentItems(s) }.getOrDefault(emptyList()) }
        val feed = async { runCatching { firstResult { Graph.api.getHomeFeed() } }.getOrNull() }
        val out = ArrayList<AutoNode>(AutoTree.quickPlay(s))
        out += recents.await()
        feed.await()?.sections?.take(AutoTree.HOME_SHELVES)?.forEach { section ->
            val title = section.title.ifBlank { s.home }
            section.items.mapNotNull { homeItem(it, title) }.take(AutoTree.HOME_PER_SHELF).let(out::addAll)
        }
        AutoTree.contiguous(out)
    }

    private fun homeItem(item: HomeItem, group: String): AutoNode? = when (item) {
        is HomeItem.Playlist -> AutoTree.browsable(
            if (item.id == com.music.spotui.data.api.Api.HomeCache.LIKED_SONGS_ID) "liked" else AutoIds.playlist(item.id),
            item.name, art(item.imageUrl), group = group,
        ).takeIf { item.id.isNotBlank() }
        is HomeItem.Album -> AutoTree.albumNode(item.id, item.name, item.imageUrl, item.artists, ::art, group).takeIf { item.id.isNotBlank() }
        is HomeItem.Artist -> AutoTree.browsable(AutoIds.artist(item.id, item.name), item.name, art(item.imageUrl), group = group).takeIf { item.id.isNotBlank() }
        else -> null
    }

    /** The account's recents (all devices): playlists, albums, artists and Liked Songs, as a group. */
    private suspend fun recentItems(s: AutoStrings): List<AutoNode> {
        val entries = withTimeoutOrNull(TIMEOUT_MS) { MyspotiRuntime.api.recents.recentEntries(0, 50).items } ?: return emptyList()
        val seen = HashSet<String>()
        return groupRecents(entries).flatMap { it.groups }.filter { it.music }.mapNotNull { g ->
            val c = g.context
            val id = c.uri.substringAfterLast(':')
            val mediaId = when {
                c.kind == MediaKind.PLAYLIST -> AutoIds.playlist(id)
                c.kind == MediaKind.ALBUM -> AutoIds.album(id)
                c.kind == MediaKind.ARTIST -> AutoIds.artist(id, c.title)
                c.kind == MediaKind.COLLECTION && c.uri == LIKED_URI -> "liked"
                else -> return@mapNotNull null
            }
            if (id.isBlank() || !seen.add(mediaId)) return@mapNotNull null
            AutoTree.browsable(mediaId, c.title, art(c.images.best(300)?.url), c.subtitle.orEmpty(), s.recent)
        }.take(AutoTree.HOME_RECENTS)
    }

    // ── Downloads (from the files, no session) ──

    private fun downloadsTab(s: AutoStrings): List<AutoNode> {
        val out = ArrayList<AutoNode>()
        val songs = OfflineStore.list()
        if (songs.isNotEmpty()) out += AutoNode(
            "dsongs", s.downloadedSongs, subtitle = songs.size.toString(), iconUri = downloadedCover(songs.first()),
            iconRes = "aa_ic_downloads".takeIf { downloadedCover(songs.first()) == null },
        )
        OfflineCollections.collections.values.sortedBy { it.name.lowercase() }.take(AutoTree.MAX_FOLDER).forEach { c ->
            val first = c.trackIds.firstNotNullOfOrNull { OfflineStore.tracks[it] }
            out += AutoTree.browsable("dl:${c.uri}", c.name, first?.let(::downloadedCover), group = s.downloadedLists)
        }
        return out
    }

    /** The downloaded songs of the list at [listUri] (all of them when empty), as local-play items, after Play / Shuffle. */
    private fun downloadedSongs(s: AutoStrings, listUri: String): List<AutoNode> {
        val songs = downloadedIds(listUri).take(AutoTree.MAX_SONGS).mapNotNull { OfflineStore.tracks[it] }.map { t ->
            AutoNode(AutoIds.downloaded(listUri, t.trackId), t.title, subtitle = t.artist, iconUri = downloadedCover(t),
                playable = true, download = AutoNode.DOWNLOAD_YES, group = s.songs)
        }
        if (songs.isEmpty()) return songs
        val name = OfflineCollections.collections[listUri]?.name ?: s.downloadedSongs
        return AutoTree.contextHeader(s, "dl:$listUri", name, null, "") + songs
    }

    /** Track ids of the downloaded songs of a list in its order, or of every download (newest first). */
    private fun downloadedIds(listUri: String): List<String> {
        if (listUri.isBlank()) return OfflineStore.list().map { it.trackId }
        val c = OfflineCollections.collections[listUri] ?: return emptyList()
        val ordered = OfflineCollections.offlinePage(listUri)?.tracks?.map { it.spotifyTrackId.ifBlank { it.url } }.orEmpty()
        return (ordered + c.trackIds).distinct().filter(OfflineStore::isDownloaded)
    }

    private fun downloadedCover(t: OfflineTrack): String? = ArtworkProvider.uriForDownloaded(t)?.toString()

    /** Offline, a downloaded list (or Liked Songs) opens from its stored page; null when it was never downloaded. */
    private fun offlineContext(s: AutoStrings, parentId: String): List<AutoNode>? {
        val uri = AutoIds.contextOfNode(parentId)?.takeIf { !parentId.startsWith("ar:") } ?: return null
        if (!OfflineCollections.isDownloaded(uri)) return null
        return downloadedSongs(s, uri)
    }

    // ── "Add to playlist" ──

    /** The playlists the car's "Add to playlist" button may add to: a tap chooses one (see [play]). */
    private suspend fun targetPicker(s: AutoStrings): List<AutoNode> {
        val current = CarPlaylist.id
        val list = withTimeoutOrNull(TIMEOUT_MS) { com.music.spotui.data.api.EditablePlaylists.loadWithCovers() }
            ?: com.music.spotui.data.api.EditablePlaylists.cachedWithCovers().orEmpty()
        return list.filter { !it.isFolder }.take(AutoTree.MAX_FOLDER).map { p ->
            AutoNode(AutoIds.target(p.id, p.name), p.name,
                subtitle = if (p.id == current) s.addToPlaylistCurrent else "", iconUri = art(p.imageUrl), playable = true)
        }
    }

    /** A playlist was chosen in the car as the one songs are added to. */
    private fun chooseTarget(id: String, name: String) {
        CarPlaylist.set(id, name)
        PlaybackService.notice(Graph.app.getString(R.string.aa_target_set, name))
    }

    private const val ADD_DEBOUNCE_MS = 2_000L
    private var lastAddedTrack = ""
    private var lastAddedPlaylist = ""
    private var lastAddAt = 0L

    /** The "Add to playlist" button: the song playing goes to the playlist chosen for the car; the car is told how it went. */
    fun addCurrentToPlaylist(trackId: String) {
        val app = Graph.app
        val playlist = CarPlaylist.id
        if (trackId.isBlank()) { PlaybackService.notice(app.getString(R.string.aa_add_failed)); return }
        if (playlist == null) { PlaybackService.notice(app.getString(R.string.aa_add_no_target)); return }
        // A double tap adds once: the second one inside the window is ignored.
        val now = android.os.SystemClock.elapsedRealtime()
        if (trackId == lastAddedTrack && playlist == lastAddedPlaylist && now - lastAddAt < ADD_DEBOUNCE_MS) return
        lastAddedTrack = trackId; lastAddedPlaylist = playlist; lastAddAt = now
        val name = CarPlaylist.name.orEmpty()
        com.music.spotui.data.api.SpotifySync.addTrackToPlaylist(app, playlist, trackId) { ok ->
            PlaybackService.notice(app.getString(if (ok) R.string.aa_added_to else R.string.aa_add_failed, name))
        }
    }

    // ── Search ──

    suspend fun search(query: String): List<MediaItem> {
        if (!awaitPlayer()) throw AudioNotReady("search")
        // The last answer: the music comes first, the podcasts and episodes in a second one.
        val r = lastResult { Graph.api.searchEverything(query) } ?: return emptyList()
        val context = Graph.app
        return AutoTree.search(strings(context), r, ::isDownloaded, ::art).map { it.toMediaItem(context.packageName) }
    }

    /** What a voice search asked, from the assistant's extras (artist, album, title, the kind of media). */
    fun voiceRequest(query: String, extras: Bundle?) = AutoVoice.Request(
        query = query,
        focus = AutoVoice.focusOf(extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_FOCUS)),
        artist = extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST).orEmpty(),
        album = extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_ALBUM).orEmpty(),
        title = extras?.getString(android.provider.MediaStore.EXTRA_MEDIA_TITLE).orEmpty(),
        playlist = extras?.getString("android.intent.extra.playlist").orEmpty(),
    )

    /**
     * Voice search ("play X on Astral"): the artist, album, playlist, show or song the words mean
     * ([AutoVoice.pick]), played from its first song; tells the car when nothing matched.
     */
    suspend fun playFromSearch(request: AutoVoice.Request): Boolean {
        // A cold start asks before the page is up: waiting gives the answer, returning at
        // once would tell the driver nothing matched.
        if (!awaitPlayer()) {
            android.util.Log.w("Myspoti", "voice search dropped: the player page is not ready")
            return false
        }
        val text = AutoVoice.searchText(request)
        val results = firstResult { Graph.api.searchEverything(text) }
        if (results == null) {
            // No answer (offline, timed out): not the same as nothing matching.
            PlaybackService.notice(Graph.app.getString(R.string.aa_unreachable))
            return false
        }
        val pick = AutoVoice.pick(request, results)
        when (pick) {
            is AutoVoice.Pick.Song -> play(AutoIds.song("", pick.song.url))
            is AutoVoice.Pick.Album -> playContext("spotify:album:${pick.album.spotifyId}", AutoIds.MODE_GO)
            is AutoVoice.Pick.Playlist -> playContext("spotify:playlist:${pick.playlist.spotifyId}", AutoIds.MODE_GO)
            is AutoVoice.Pick.Artist -> {
                val uri = "spotify:artist:${pick.artist.id}"
                firstResult { Graph.api.getArtistOverview(pick.artist.name, pick.artist.id) }?.let { o ->
                    artistTop[uri] = o.topTracks.map { it.song }
                }
                playContext(uri, AutoIds.MODE_GO)
            }
            is AutoVoice.Pick.Show -> {
                val episodes = showEpisodes(pick.show.id)
                episodes.firstOrNull()?.let { play(it.id) }
                if (episodes.isEmpty()) return noMatch(pick.show.name)
            }
            AutoVoice.Pick.None -> return noMatch(text)
        }
        return true
    }

    /** Voice search of plain words (the app's own search intent). */
    suspend fun playFromSearch(query: String): Boolean = playFromSearch(AutoVoice.Request(query))

    /** Nothing to play for [text] (words said or a title, never an id): tells the car. */
    private fun noMatch(text: String): Boolean {
        val app = Graph.app
        PlaybackService.notice(if (text.isBlank()) app.getString(R.string.aa_play_failed) else app.getString(R.string.aa_nothing_found, text))
        return false
    }

    /** [MyspotiRuntime.api.ready], waited for (bounded) — the page loads after the car connects. */
    private suspend fun awaitPlayer(): Boolean =
        MyspotiRuntime.api.ready.value ||
            withTimeoutOrNull(READY_WAIT_MS) { MyspotiRuntime.api.ready.first { it } } != null

    // ── Playing ──

    fun play(mediaId: String) {
        val parts = mediaId.split("|", limit = 3)
        if (parts.size < 3) return
        when (parts[0]) {
            "t" -> playTrack(parts[1].ifBlank { null }, parts[2])
            "d" -> playDownloaded(parts[1], parts[2])
            "radio" -> startRadio(parts[1], parts[2])
            "p" -> playContext(parts[1], parts[2])
            "target" -> chooseTarget(parts[1], parts[2])
        }
    }

    private fun onMain(block: () -> Unit) { android.os.Handler(android.os.Looper.getMainLooper()).post(block) }

    /**
     * "Play" / "Shuffle" / a voice request on a whole list: its first song, or a random one when
     * shuffling, in the list; "go" leaves shuffle as the user has it, the others set it.
     */
    private fun playContext(contextUri: String, mode: String) {
        if (contextUri.startsWith("dl:")) { playDownloadedList(contextUri.removePrefix("dl:"), mode); return }
        scope.launch {
            // A cached row can be tapped before the player page is up (cold start).
            if (!awaitPlayer()) { noMatch(""); return@launch }
            val urls = urlsOf(contextUri)
            if (urls.isNullOrEmpty()) { noMatch(""); return@launch }
            val key = if (mode == AutoIds.MODE_SHUFFLE) urls.random() else urls.first()
            onMain {
                if (mode != AutoIds.MODE_GO) {
                    val shuffle = mode == AutoIds.MODE_SHUFFLE
                    if (SongPlayer.isShuffleOn() != shuffle) SongPlayer.setShuffle(shuffle, haptic = false)
                }
                playTrack(contextUri, key)
            }
        }
    }

    /** The songs of a list as the car listed them (read again when the list is not at hand). */
    private suspend fun urlsOf(contextUri: String): List<String>? {
        pages[contextUri]?.tracks?.takeIf { it.isNotEmpty() }?.let { return it.map { s -> s.url } }
        artistTop[contextUri]?.takeIf { it.isNotEmpty() }?.let { return it.map { s -> s.url } }
        if (contextUri.startsWith("spotify:artist:")) {
            val id = contextUri.removePrefix("spotify:artist:")
            val top = firstResult { Graph.api.getArtistOverview("", id) }?.topTracks?.map { it.song }.orEmpty()
            artistTop[contextUri] = top
            return top.map { it.url }
        }
        val page = nodeOf(contextUri)?.let { contextPage(it) } ?: return null
        pages[page.uri] = page
        return page.tracks.map { it.url }
    }

    private fun playDownloadedList(listUri: String, mode: String) {
        val ids = downloadedIds(listUri)
        val id = (if (mode == AutoIds.MODE_SHUFFLE) ids.randomOrNull() else ids.firstOrNull()) ?: return
        playDownloaded(listUri, id, shuffle = if (mode == AutoIds.MODE_GO) null else mode == AutoIds.MODE_SHUFFLE)
    }

    private fun playTrack(contextUri: String?, key: String, reread: Boolean = true) {
        // A cached row can be tapped before the player page is up: wait for it, then play.
        if (!MyspotiRuntime.api.ready.value) {
            scope.launch { if (awaitPlayer()) onMain { playTrack(contextUri, key, reread) } else noMatch("") }
            return
        }
        val page = contextUri?.let { pages[it] }
        // A list the car showed before this process started (or one listed long ago): read again,
        // so the song plays on in its list and not alone, then its radio (BUG-15).
        val node = contextUri?.takeIf { page == null && reread }?.let(::nodeOf)
        if (node != null) {
            scope.launch {
                contextPage(node)?.let { pages[it.uri] = it }
                onMain { playTrack(contextUri, key, reread = false) }
            }
            return
        }
        val song = page?.tracks?.firstOrNull { it.url == key }
        // A downloaded song plays its file, not the stream (the phone's rule, see LocalPlayPlan):
        // offline or on the data saver the list's downloaded songs, online the whole list when it
        // is all downloaded; a lone downloaded song (search) always.
        val store = OfflineStore
        val id = song?.spotifyTrackId?.ifBlank { null } ?: key.removePrefix("spotify:track:")
        val ids = if (page != null) LocalPlayPlan.localIds(
            page.tracks.map { it.spotifyTrackId.ifBlank { it.url } }, id, page.complete,
            offline = !store.isOnline(Graph.app), dataSaver = DataSaver.active, isDownloaded = store::isDownloaded,
        ) else if (store.isDownloaded(id)) listOf(id) else null
        if (ids != null) {
            val local = ids.mapNotNull { store.tracks[it] }
            val at = local.indexOfFirst { it.trackId == id }
            if (at >= 0) {
                onMain { LocalPlayer.play(local.map(store::toLocalTrack), at, shuffle = Graph.currentSongState.shuffle.value, context = page?.name ?: local[at].title, contextUri = page?.uri.orEmpty()) }
                return
            }
        }
        if (page != null) {
            Graph.currentSongState.updateQueue(page.tracks)
            SongPlayer.registerContextName(page.uri, page.name)
            if (page.type == "liked") SongPlayer.registerLikedSongs(page.tracks.map { it.url })
        }
        // The list's songs: an audio-source extension then plays the list on from the song, as on
        // the phone, instead of the song alone and its radio. Spotify plays the context anyway.
        // A show's episodes play in the show, as on its screen: Spotify's queue, never the extensions'.
        SongPlayer.playSong(key, contextUri = contextUri, songs = page?.tracks?.takeIf { page.type != SHOW })
        song?.let { Graph.currentSongState.updateSongState(it.coverUri, it.title, it.singer, true, it.id, 0, it.album) }
    }

    /** The Downloads tab: always the files, from the tapped song on ([shuffle] null: as the user has it). */
    private fun playDownloaded(listUri: String, trackId: String, shuffle: Boolean? = null) {
        val store = OfflineStore
        val local = downloadedIds(listUri).mapNotNull { store.tracks[it] }
        val at = local.indexOfFirst { it.trackId == trackId }
        if (at < 0) return
        val name = OfflineCollections.collections[listUri]?.name ?: Graph.app.getString(R.string.aa_downloaded_songs)
        onMain { LocalPlayer.play(local.map(store::toLocalTrack), at, shuffle = shuffle ?: Graph.currentSongState.shuffle.value, context = name, contextUri = listUri) }
    }

    /**
     * "Start radio": Spotify's radio playlist for the song / artist / list (the phone's "Go to song
     * radio" opens the same playlist), played from its first song. A list that has no radio of its
     * own falls back to the radio of [fallbackSong].
     */
    private fun startRadio(seed: String, fallbackSong: String) {
        scope.launch {
            if (!awaitPlayer()) { PlaybackService.notice(Graph.app.getString(R.string.aa_radio_failed)); return@launch }
            val id = Graph.api.radioPlaylistId(seed)
                ?: fallbackSong.takeIf { it.startsWith("spotify:track:") }?.let { Graph.api.radioPlaylistId(it) }
            val page = id?.let { contextPage("pl:$it") }?.takeIf { it.tracks.isNotEmpty() }
            if (page == null) { PlaybackService.notice(Graph.app.getString(R.string.aa_radio_failed)); return@launch }
            pages[page.uri] = page
            play(AutoIds.song(page.uri, page.tracks.first().url))
        }
    }

    /** The player button "Start radio": the radio of the song playing. */
    fun startRadioOfCurrent(songUri: String) {
        if (!songUri.startsWith("spotify:track:")) { PlaybackService.notice(Graph.app.getString(R.string.aa_radio_failed)); return }
        startRadio(songUri, songUri)
    }

    // ── Items ──

    private fun tracksOf(s: AutoStrings, page: ContextPageModel?): List<AutoNode> {
        page ?: return emptyList()
        pages[page.uri] = page
        val first = page.tracks.firstOrNull() ?: return emptyList()
        // Liked Songs has no radio of its own: the first song's.
        val seed = if (page.type == "liked" || page.uri == LIKED_URI) first.url else page.uri
        return AutoTree.contextHeader(s, page.uri, page.name, seed, first.url) +
            page.tracks.take(AutoTree.MAX_SONGS).map { AutoTree.song(page.uri, it, s.songs, isDownloaded(it), ::art) }
    }

    private suspend fun artistPage(s: AutoStrings, parentId: String): List<AutoNode> {
        val (id, name) = parentId.removePrefix("ar:").split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val overview = firstResult { Graph.api.getArtistOverview(name, id) } ?: return emptyList()
        val top = overview.topTracks.map { it.song }
        artistTop["spotify:artist:$id"] = top
        return AutoTree.artistPage(s, id, name.ifBlank { overview.name }, top, overview.popularReleases, ::isDownloaded, ::art)
    }

    /** The last answer of [load] within [TIMEOUT_MS] (a flow that answers in steps); null when none came. */
    private suspend fun <T> lastResult(load: suspend () -> kotlinx.coroutines.flow.Flow<Response<T>>): T? {
        var last: T? = null
        withTimeoutOrNull(TIMEOUT_MS) { load().collect { if (it is Response.Success) last = it.data } }
        return last
    }

    /**
     * The songs of a list node ("liked", "pl:<id>", "al:<id>"): its first answer, to show at once.
     * A long playlist comes in steps; the rest keeps coming after, and the list kept for a tap
     * ([pages]) grows with it, so the song tapped queues the whole list, not its first 100 songs.
     */
    private suspend fun contextPage(node: String): ContextPageModel? {
        val load: suspend () -> kotlinx.coroutines.flow.Flow<Response<ContextPageModel>> = when {
            node == "liked" -> { { Graph.api.getLikedPage() } }
            node.startsWith("pl:") -> { { Graph.api.getPlaylistPage(node.removePrefix("pl:")) } }
            node.startsWith("al:") -> { { Graph.api.getAlbumPage(node.removePrefix("al:")) } }
            else -> return null
        }
        val first = kotlinx.coroutines.CompletableDeferred<ContextPageModel?>()
        scope.launch {
            withTimeoutOrNull(FULL_LIST_MS) {
                load().collect { r ->
                    when {
                        r is Response.Success && !first.isCompleted -> first.complete(r.data)
                        r is Response.Success -> pages[r.data.uri] = r.data
                        r is Response.Error && !first.isCompleted -> first.complete(null)
                    }
                }
            }
            first.complete(null)
        }
        return withTimeoutOrNull(TIMEOUT_MS) { first.await() }
    }

    /** The list node of a context uri a song's media id names, to read it again ([contextPage]). */
    internal fun nodeOf(contextUri: String): String? = when {
        contextUri == LIKED_URI -> "liked"
        contextUri.startsWith("spotify:playlist:") -> "pl:" + contextUri.removePrefix("spotify:playlist:")
        contextUri.startsWith("spotify:album:") -> "al:" + contextUri.removePrefix("spotify:album:")
        else -> null
    }

    private suspend fun <T> firstResult(load: suspend () -> kotlinx.coroutines.flow.Flow<Response<T>>): T? =
        withTimeoutOrNull(TIMEOUT_MS) {
            (load().first { it !is Response.Loading } as? Response.Success)?.data
        }
}

/**
 * Cover art for Android Auto, which only shows local (content://) artwork: serves an
 * image from Spotify's image CDN through Glide's disk cache. Other hosts are refused.
 */
class ArtworkProvider : android.content.ContentProvider() {
    companion object {
        private val AUTHORITY = app.myspoti.engine.BuildConfig.APPLICATION_ID + ".artwork"
        private val allowedHosts = setOf("i.scdn.co", "mosaic.scdn.co", "image-cdn-ak.spotifycdn.com", "image-cdn-fa.spotifycdn.com", "thisis-images.spotifycdn.com", "seeded-session-images.scdn.co", "pickasso.spotifycdn.com")

        /** A downloaded song's cover: its file (the car is offline), else the CDN image. */
        fun uriForDownloaded(t: com.music.spotui.data.offline.OfflineTrack): Uri? {
            if (t.cover.isNotBlank() && java.io.File(com.music.spotui.data.offline.OfflineStore.coverDir, t.cover).isFile)
                return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("cover").appendQueryParameter("f", t.cover).build()
            return uriFor(t.coverUrl)
        }

        fun uriFor(url: String?): Uri? {
            if (url.isNullOrBlank() || !allowed(url)) return null
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("img").appendQueryParameter("u", url).build()
        }

        /**
         * Only https URLs on the CDN hosts: a `file:` URL whose authority happens to be
         * an allowed host (`file://i.scdn.co/data/data/...`) must never reach Glide,
         * which would open the local path and hand this app's private files to any caller
         * (the provider is exported, for Android Auto).
         */
        private fun allowed(url: String): Boolean {
            val u = Uri.parse(url)
            return u.scheme == "https" && u.host in allowedHosts
        }
    }

    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor? {
        // A downloaded song's cover: only a file that a download of the store names, never a path.
        uri.getQueryParameter("f")?.let { name ->
            val file = com.music.spotui.data.offline.OfflineStore.tracks.values.firstOrNull { it.cover == name }
                ?.let(com.music.spotui.data.offline.OfflineStore::coverFile) ?: return null
            return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val url = uri.getQueryParameter("u") ?: return null
        if (!allowed(url)) return null
        // Bounded: a binder thread must not wait on the network forever.
        val file = runCatching {
            com.bumptech.glide.Glide.with(context!!).asFile().load(url).submit()
                .get(15, java.util.concurrent.TimeUnit.SECONDS)
        }.getOrNull() ?: return null
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun insert(uri: Uri, values: android.content.ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0
}
