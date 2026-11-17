package com.music.spotui.data.cache

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CachedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val fileName: String,
    val bytes: Long,
    val cachedAt: Long,
    val lastPlayedAt: Long = cachedAt,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", trackId)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("coverUrl", coverUrl)
        .put("durationMs", durationMs)
        .put("file", fileName)
        .put("bytes", bytes)
        .put("cachedAt", cachedAt)
        .put("lastPlayedAt", lastPlayedAt)

    companion object {
        fun fromJson(o: JSONObject): CachedTrack = CachedTrack(
            trackId = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            coverUrl = o.optString("coverUrl"),
            durationMs = o.optLong("durationMs", 0L),
            fileName = o.getString("file"),
            bytes = o.optLong("bytes"),
            cachedAt = o.optLong("cachedAt"),
            lastPlayedAt = o.optLong("lastPlayedAt", o.optLong("cachedAt")),
        )
    }
}

/**
 * Manages locally pre-cached playback tracks in `context.cacheDir/playback_cache/`.
 */
object PlaybackCacheStore {
    private const val TAG = "PlaybackCache"
    private const val COOLDOWN_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private lateinit var cacheDir: File
    private lateinit var indexFile: File

    val cachedTracks: SnapshotStateMap<String, CachedTrack> = mutableStateMapOf()
    private val pendingPurges = ConcurrentHashMap<String, Job>()

    private inline fun <T> withStateMutation(block: () -> T): T = synchronized(lock) {
        Snapshot.withMutableSnapshot(block)
    }

    fun init(context: Context) {
        synchronized(lock) {
            if (::cacheDir.isInitialized) return
            cacheDir = File(context.cacheDir, "playback_cache")
            cacheDir.mkdirs()
            indexFile = File(cacheDir, "index.json")
        }
        loadIndex()
    }

    private fun ensureInit() {
        synchronized(lock) {
            if (!::cacheDir.isInitialized) {
                cacheDir = File(Graph.app.cacheDir, "playback_cache")
                cacheDir.mkdirs()
                indexFile = File(cacheDir, "index.json")
            } else {
                return
            }
        }
        loadIndex()
    }

    private fun loadIndex() {
        withStateMutation { cachedTracks.clear() }
        if (!indexFile.exists()) return
        runCatching {
            val json = JSONArray(indexFile.readText())
            withStateMutation {
                for (i in 0 until json.length()) {
                    val t = CachedTrack.fromJson(json.getJSONObject(i))
                    val audioFile = File(cacheDir, t.fileName)
                    if (audioFile.isFile) {
                        cachedTracks[t.trackId] = t
                    }
                }
            }
        }.onFailure {
            android.util.Log.w(TAG, "failed to read playback cache index: ${it.message}")
        }
    }

    private fun saveIndex() {
        ensureInit()
        runCatching {
            val snapshot = synchronized(lock) { cachedTracks.values.toList() }
            val json = JSONArray()
            snapshot.forEach { json.put(it.toJson()) }
            indexFile.writeText(json.toString())
        }
    }

    fun localIdOf(trackId: String): Long = ("cache_" + trackId).hashCode().toLong()

    fun byLocalId(localId: Long): CachedTrack? = synchronized(lock) {
        cachedTracks.values.firstOrNull { localIdOf(it.trackId) == localId }
    }

    fun isCached(trackId: String): Boolean {
        if (trackId.isBlank()) return false
        ensureInit()
        val t = synchronized(lock) { cachedTracks[trackId] } ?: return false
        return File(cacheDir, t.fileName).isFile
    }

    fun getAudioFile(trackId: String): File? {
        ensureInit()
        val t = synchronized(lock) { cachedTracks[trackId] } ?: return null
        val f = File(cacheDir, t.fileName)
        return if (f.isFile) f else null
    }

    fun createCacheFile(trackId: String, ext: String = "m4a"): File {
        ensureInit()
        return File(cacheDir, "${trackId}.$ext")
    }

    fun toLocalTrack(c: CachedTrack): LocalTrack {
        ensureInit()
        val mime = if (c.fileName.endsWith(".flac", ignoreCase = true)) "audio/flac" else null
        return LocalTrack(
            id = localIdOf(c.trackId),
            uri = Uri.fromFile(File(cacheDir, c.fileName)),
            title = c.title.ifBlank { "Preloaded Track" },
            artist = c.artist,
            album = c.album,
            durationMs = c.durationMs,
            artUri = if (c.coverUrl.isNotBlank()) Uri.parse(c.coverUrl) else Uri.EMPTY,
            mimeType = mime,
        )
    }

    fun toSong(c: CachedTrack): com.music.spotui.data.entity.SongsModel =
        com.music.spotui.data.entity.SongsModel(
            id = ("track:" + c.trackId).hashCode() and 0x7fffffff,
            title = c.title.ifBlank { "Cached Track" },
            album = c.album,
            singer = c.artist,
            coverUri = c.coverUrl,
            url = com.music.spotui.di.SongPlayer.buildSpotifyPlayQuery(c.trackId, c.title, c.artist),
            spotifyTrackId = c.trackId,
            durationMs = c.durationMs.toInt(),
        )

    fun addTrack(t: CachedTrack) {
        ensureInit()
        pendingPurges.remove(t.trackId)?.cancel()
        withStateMutation { cachedTracks[t.trackId] = t }
        saveIndex()
    }

    fun totalBytes(): Long = synchronized(lock) { cachedTracks.values.sumOf { it.bytes } }

    fun scheduleTrackPurge(trackId: String, delayMs: Long = COOLDOWN_MS) {
        if (synchronized(lock) { !cachedTracks.containsKey(trackId) }) return
        pendingPurges.remove(trackId)?.cancel()
        lateinit var job: Job
        job = scope.launch {
            delay(delayMs)
            if (pendingPurges.remove(trackId, job)) {
                removeTrack(trackId)
            }
        }
        pendingPurges[trackId] = job
    }

    fun cancelPendingPurge(trackId: String) {
        pendingPurges.remove(trackId)?.cancel()
    }

    fun removeTrack(trackId: String) {
        ensureInit()
        val t = withStateMutation { cachedTracks.remove(trackId) } ?: return
        File(cacheDir, t.fileName).delete()
        saveIndex()
    }

    fun enforceCap(maxCapBytes: Long) {
        if (maxCapBytes <= 0) return
        var currentSize = totalBytes()
        if (currentSize <= maxCapBytes) return

        val sorted = synchronized(lock) { cachedTracks.values.sortedBy { it.lastPlayedAt } }
        for (t in sorted) {
            if (currentSize <= maxCapBytes) break
            removeTrack(t.trackId)
            currentSize -= t.bytes
        }
    }

    fun clearAll() {
        ensureInit()
        pendingPurges.values.forEach { it.cancel() }
        pendingPurges.clear()
        withStateMutation { cachedTracks.clear() }
        cacheDir.listFiles()?.forEach { it.delete() }
        saveIndex()
    }
}
