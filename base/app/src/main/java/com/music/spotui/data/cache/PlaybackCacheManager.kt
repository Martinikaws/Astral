package com.music.spotui.data.cache

import android.content.Context
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.offline.OfflineStore
import com.music.spotui.data.preferences.isHiRes
import com.music.spotui.data.preferences.isLosslessEnabled
import com.music.spotui.di.Graph
import com.music.spotui.lossless.DashFlacRemux
import com.music.spotui.lossless.LosslessSource
import com.music.spotui.video.RangeAudioDownload
import com.music.spotui.video.YouTubeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles proactive preloading of upcoming queue tracks into local cache (preferring
 * lossless FLAC via [LosslessSource] when enabled, falling back to YouTube audio),
 * and manages cache storage thresholds and post-play purging.
 */
object PlaybackCacheManager {
    private const val TAG = "PlaybackCache"
    private const val PREF = "cache_settings_prefs"
    private const val KEY_PRELOADING_ENABLED = "preloading_enabled"
    private const val KEY_PRELOAD_COUNT = "preload_count"
    private const val KEY_CACHE_CAP_MB = "cache_cap_mb"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var preloadJob: kotlinx.coroutines.Job? = null

    private fun preloadingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_PRELOADING_ENABLED, true)

    private fun preloadCount(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_PRELOAD_COUNT, 2).coerceIn(1, 10)

    private fun cacheCapMb(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_CACHE_CAP_MB, 1000)

    fun preloadTracks(songs: List<SongsModel>, context: Context = Graph.app) {
        if (!preloadingEnabled(context)) return
        if (com.music.spotui.data.DataSaver.active || !OfflineStore.isOnline(context)) return

        val capMb = cacheCapMb(context)
        val maxCapBytes = if (capMb > 0) capMb * 1024L * 1024L else 0L

        val count = preloadCount(context)
        val toPreload = songs.take(count).filter { song ->
            val id = song.spotifyTrackId
            id.isNotBlank() && !OfflineStore.isDownloaded(id) && !PlaybackCacheStore.isCached(id)
        }

        if (toPreload.isEmpty()) return

        preloadJob?.cancel()
        preloadJob = scope.launch {
            kotlinx.coroutines.delay(3_000)
            if (maxCapBytes > 0) {
                PlaybackCacheStore.enforceCap(maxCapBytes)
            }
            for (song in toPreload) {
                val id = song.spotifyTrackId
                if (id.isBlank() || OfflineStore.isDownloaded(id) || PlaybackCacheStore.isCached(id)) continue
                if (!inFlight.add(id)) continue
                try {
                    val audioFile = fetchToCache(context, song)
                    if (audioFile != null && audioFile.isFile && audioFile.length() > 10_000L) {
                        PlaybackCacheStore.addTrack(
                            CachedTrack(
                                trackId = id,
                                title = song.title,
                                artist = song.singer,
                                album = song.album,
                                coverUrl = song.coverUri,
                                durationMs = song.durationMs.toLong(),
                                fileName = audioFile.name,
                                bytes = audioFile.length(),
                                cachedAt = System.currentTimeMillis(),
                            )
                        )
                        if (maxCapBytes > 0) {
                            PlaybackCacheStore.enforceCap(maxCapBytes)
                        }
                    } else {
                        audioFile?.delete()
                    }
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "Failed to preload $id (${song.title}): ${t.message}")
                } finally {
                    inFlight.remove(id)
                }
            }
        }
    }

    private suspend fun fetchToCache(context: Context, song: SongsModel): File? {
        val id = song.spotifyTrackId
        if (isLosslessEnabled(context)) {
            val info = LosslessSource.TrackInfo(
                title = song.title,
                artist = song.singer,
                durationSec = (song.durationMs / 1000).coerceAtLeast(0),
            )
            val res = runCatching {
                LosslessSource.resolve(context, id, info, preferHiRes = isHiRes(context), silent = true)
            }.getOrNull()
            if (res is LosslessSource.Success) {
                val ext = if (res.track.aac) "m4a" else "flac"
                val out = PlaybackCacheStore.createCacheFile(id, ext)
                val ok = if (res.track.dashBase64.isNotBlank()) {
                    DashFlacRemux.download("data:application/dash+xml;base64,${res.track.dashBase64}", out)
                } else if (res.track.url.isNotBlank()) {
                    downloadHttpFile(res.track.url, out)
                } else {
                    false
                }
                if (ok && out.isFile && out.length() > 10_000L) return out
                out.delete()
            }
        }

        val hit = YouTubeSource.findSong(song.title, song.singer, song.durationMs.toLong())
            ?: YouTubeSource.findAudio(song.title, song.singer, song.durationMs.toLong())
            ?: return null
        val info = runCatching { YouTubeSource.info(hit.url) }.getOrNull() ?: return null
        val stream = info.audioStreams
            .filter { it.format == MediaFormat.M4A || it.format == MediaFormat.WEBMA_OPUS }
            .sortedWith(compareBy({ if (it.format == MediaFormat.M4A) 0 else 1 }, { if (it.averageBitrate > 0) it.averageBitrate else Int.MAX_VALUE }))
            .firstOrNull() ?: return null
        val out = PlaybackCacheStore.createCacheFile(id, if (stream.format == MediaFormat.M4A) "m4a" else "webm")
        val ok = runCatching {
            RangeAudioDownload.fetch(YouTubeSource.http, stream.content, out, emptyMap())
            out.isFile && out.length() > 10_000L
        }.getOrDefault(false)
        if (!ok) {
            out.delete()
            return null
        }
        return out
    }

    private fun downloadHttpFile(url: String, out: File): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SpotiFLAC")
        }
        try {
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.use { input ->
                out.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            true
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    fun onTrackPlayed(trackId: String) {
        if (trackId.isBlank()) return
        PlaybackCacheStore.scheduleTrackPurge(trackId)
    }

    fun onTrackReplayed(trackId: String) {
        if (trackId.isBlank()) return
        PlaybackCacheStore.cancelPendingPurge(trackId)
    }
}
