package com.music.spotui.flac

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.myspoti.extensions.ExtensionMediaHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import com.music.spotui.data.DataSaver
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.extensions.MediaHttp
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Audio URLs are resolved only for tracks chosen in the built-in sources. The player's
 * queue and the last-session file carry opaque ids, never server credentials.
 */
internal object FlacSources {
    const val SCHEME = "astral-flac"
    private const val RESULTS = 40
    private const val MAX_JSON_BYTES = 2 * 1024 * 1024
    private data class Stream(val url: HttpUrl, val ownServer: Boolean)
    private val addresses = object : LinkedHashMap<String, Stream>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stream>?) = size > 1000
    }
    // Only the server typed by the listener goes through this client, with standard TLS validation.
    // Public providers and third-party extensions still use the separately checked MediaHttp.
    private val ownClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor(DataSaver.interceptor).build()

    data class Track(val audio: LocalTrack, val page: String? = null)

    fun openStream(
        address: String,
        headers: Map<String, String>,
        fixed: Map<String, String>,
        timeout: Int,
    ): ExtensionMediaHttp.Opened {
        val uri = Uri.parse(address)
        val stream = synchronized(addresses) { addresses[uri.lastPathSegment] }
            ?: throw IOException("This source's playback session expired")
        return if (stream.ownServer) openOwnServer(stream.url, headers + fixed, timeout)
        else MediaHttp.open(stream.url.toString(), headers = headers, fixed = fixed, timeoutMs = timeout)
    }

    private fun openOwnServer(url: HttpUrl, headers: Map<String, String>, timeout: Int): ExtensionMediaHttp.Opened {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        val response = try {
            ownClient.newBuilder()
                .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                .build().newCall(request).execute()
        } catch (_: IOException) {
            throw IOException("Could not connect to the OpenSubsonic server")
        }
        return ExtensionMediaHttp.Opened(response, url, redirects = 0, elapsedMs = 0)
    }

    fun jamendo(clientId: String, query: String): List<Track> {
        val key = clientId.trim().takeIf { it.matches(Regex("[A-Za-z0-9_-]{4,100}")) }
            ?: throw IOException("Invalid Jamendo client ID")
        val url = "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", key)
            .addQueryParameter("format", "json")
            .addQueryParameter("audioformat", "flac")
            .addQueryParameter("limit", RESULTS.toString())
            .addQueryParameter("type", "single albumtrack")
            .addQueryParameter("search", query)
            .build()
        val body = json(url)
        if (body.optJSONObject("headers")?.optString("status") != "success") throw IOException("Jamendo request refused")
        return body.optJSONArray("results").objects().mapNotNull { result ->
            val id = result.optString("id").takeIf { it.matches(Regex("[0-9]+")) } ?: return@mapNotNull null
            val stream = https(result.optString("audio"))?.takeIf {
                (it.host == "jamendo.com" || it.host.endsWith(".jamendo.com")) &&
                    (it.queryParameter("format") == "flac" || it.encodedPath.endsWith(".flac", ignoreCase = true))
            } ?: return@mapNotNull null
            remote(
                "Jamendo", stream, result.optString("name"), result.optString("artist_name"),
                result.optString("album_name"), secondsToMs(result.optString("duration")), "https://www.jamendo.com/track/$id",
            )
        }.toList()
    }

    fun archive(input: String): List<Track> {
        val id = archiveId(input) ?: throw IOException("Invalid Archive recording URL")
        val metadataUrl = "https://archive.org/metadata/".toHttpUrl().newBuilder().addPathSegment(id).build()
        val body = json(metadataUrl)
        val metadata = body.optJSONObject("metadata") ?: return emptyList()
        if (restricted(body, "is_dark") || restricted(metadata, "access-restricted-item") ||
            restricted(metadata, "no-download") || metadata.optString("mediatype") != "etree"
        ) return emptyList()
        val artist = metadata.optJSONArray("creator")?.optString(0).orEmpty().ifBlank {
            metadata.optString("creator")
        }
        val album = metadata.optString("title")
        return body.optJSONArray("files").objects().mapNotNull { result ->
            val filename = result.optString("name")
            val parts = filename.split('/')
            if (!filename.endsWith(".flac", true) || '\\' in filename ||
                parts.any { it.isEmpty() || it == "." || it == ".." } || restricted(result, "private")
            ) return@mapNotNull null
            val stream = "https://archive.org/download/".toHttpUrl().newBuilder()
                .addPathSegment(id).apply { parts.forEach(::addPathSegment) }.build()
            remote(
                "Live Music Archive", stream, result.optString("title").ifBlank { parts.last().dropLast(5) },
                artist, album, secondsToMs(result.optString("length")), "https://archive.org/details/$id",
            )
        }.toList()
    }

    private fun archiveId(input: String): String? {
        val trimmed = input.trim()
        val id = if (trimmed.contains("://")) {
            val url = https(trimmed)?.takeIf { it.host == "archive.org" || it.host == "www.archive.org" }
                ?: return null
            url.pathSegments.takeIf { it.size in 2..3 && it[0] == "details" && (it.size == 2 || it[2].isEmpty()) }
                ?.get(1) ?: return null
        } else trimmed
        return id.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) }
    }

    fun navidrome(server: String, user: String, password: String, query: String): List<Track> {
        val base = https(server.trim())?.takeIf { it.query == null && it.fragment == null }
            ?: throw IOException("Enter an HTTPS server address")
        val name = user.trim().takeIf { it.isNotEmpty() } ?: throw IOException("Username required")
        if (password.isEmpty()) throw IOException("Password required")
        fun endpoint(path: String): HttpUrl.Builder {
            val salt = UUID.randomUUID().toString().replace("-", "")
            val token = MessageDigest.getInstance("MD5").digest((password + salt).encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
            return base.newBuilder().apply {
                if (base.encodedPath.endsWith('/')) removePathSegment(base.pathSize - 1)
            }.addPathSegment("rest").addPathSegment("$path.view")
                .addQueryParameter("u", name)
                .addQueryParameter("t", token)
                .addQueryParameter("s", salt)
                .addQueryParameter("v", "1.16.1")
                .addQueryParameter("c", "Astral")
                .addQueryParameter("f", "json")
        }
        val url = endpoint("search3").addQueryParameter("query", query)
            .addQueryParameter("artistCount", "0").addQueryParameter("albumCount", "0")
            .addQueryParameter("songCount", RESULTS.toString()).build()
        val response = json(url, ownServer = true).optJSONObject("subsonic-response")
        if (response?.optString("status") != "ok") throw IOException("Server could not authenticate this account")
        return response.optJSONObject("searchResult3")?.optJSONArray("song").objects().mapNotNull { result ->
            if (!result.optString("suffix").equals("flac", true) && result.optString("contentType") != "audio/flac") {
                return@mapNotNull null
            }
            val id = result.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = endpoint("stream").addQueryParameter("id", id).addQueryParameter("format", "raw").build()
            remote(
                "Navidrome", url, result.optString("title"), result.optString("artist"), result.optString("album"),
                secondsToMs(result.optString("duration")), null, ownServer = true,
            )
        }.toList()
    }

    fun bandcamp(context: Context, files: List<Uri>): List<Track> = files.distinct().mapNotNull { uri ->
        val resolver = context.contentResolver
        if (uri.scheme != "content") return@mapNotNull null
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: return@mapNotNull null
        if (!name.endsWith(".flac", ignoreCase = true)) return@mapNotNull null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            Track(LocalTrack(
                id = localId(uri.toString()), uri = uri, title = title.ifBlank { name.dropLast(5) },
                artist = artist, album = album, durationMs = duration, artUri = Uri.EMPTY, mimeType = "audio/flac",
            ))
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun remote(
        source: String, stream: HttpUrl, title: String, artist: String, album: String,
        duration: Long, page: String?, ownServer: Boolean = false,
    ): Track? {
        if (title.isBlank()) return null
        val key = UUID.randomUUID().toString()
        synchronized(addresses) { addresses[key] = Stream(stream, ownServer) }
        return Track(
            LocalTrack(
                id = localId(key), uri = Uri.Builder().scheme(SCHEME).authority("track").appendPath(key).build(),
                title = title, artist = artist, album = album.ifBlank { source }, durationMs = duration,
                artUri = Uri.EMPTY, mimeType = "audio/flac",
            ), page,
        )
    }

    private fun localId(value: String): Long =
        UUID.nameUUIDFromBytes(value.encodeToByteArray()).mostSignificantBits and Long.MAX_VALUE

    private fun https(value: String): HttpUrl? = value.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty()
    }

    private fun json(url: HttpUrl, ownServer: Boolean = false): JSONObject {
        DataSaver.check()
        val connection = if (ownServer) openOwnServer(url, emptyMap(), 15_000) else MediaHttp.open(url.toString())
        return connection.use { opened ->
            if (!opened.response.isSuccessful) throw IOException("Source request failed")
            val body = opened.response.body.source()
            if (body.request(MAX_JSON_BYTES.toLong() + 1L)) throw IOException("Source response was too large")
            JSONObject(body.readUtf8())
        }
    }

    private fun JSONArray?.objects(): Sequence<JSONObject> =
        (0 until (this?.length() ?: 0)).asSequence().mapNotNull { this?.optJSONObject(it) }

    private fun restricted(o: JSONObject, name: String): Boolean =
        o.optString(name).lowercase() in setOf("true", "1", "yes")

    private fun secondsToMs(value: String): Long =
        value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.times(1000)?.toLong() ?: 0
}
