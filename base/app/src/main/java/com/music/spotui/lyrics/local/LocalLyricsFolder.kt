package com.music.spotui.lyrics.local

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.music.spotui.data.api.LyricsApi
import com.music.spotui.data.api.LyricsMatch
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Stores a folder grant and indexes its LRC and TTML documents by filename. */
internal object LocalLyricsFolder {
    private const val MAX_FILES = 2_000
    private const val MAX_SIZE = 1024 * 1024
    private val context get() = Graph.app
    private val resolver get() = context.contentResolver
    private val prefs get() = context.getSharedPreferences("spicy_local_lyrics", 0)
    private val tree get() = prefs.getString("tree", null)?.let(Uri::parse)
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    val linked get() = runCatching { tree != null }.getOrDefault(false)
    val label: String? get() = tree?.let {
        runCatching { DocumentsContract.getTreeDocumentId(it).substringAfterLast(':').substringAfterLast('/') }.getOrNull()
    }
    const val PROVIDER = "Local lyrics"

    private data class File(val uri: Uri, val name: String)
    private data class Index(val tree: Uri, val files: List<File>)
    @Volatile private var index: Index? = null

    @Synchronized
    fun link(folder: Uri) {
        val old = tree
        resolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val scan = try {
            scan(folder)
        } catch (failure: Exception) {
            if (old != folder) release(folder)
            throw failure
        }
        prefs.edit().putString("tree", folder.toString()).apply()
        index = Index(folder, scan)
        if (old != null && old != folder) release(old)
        revision.value++
    }

    @Synchronized
    fun unlink() {
        tree?.let(::release)
        prefs.edit().remove("tree").apply()
        index = null
        revision.value++
    }

    @Synchronized
    fun rescan() {
        val folder = tree ?: return
        index = Index(folder, scan(folder))
        revision.value++
    }

    private fun release(uri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    @Synchronized
    private fun currentIndex(): Index? {
        val folder = tree ?: return null
        return index?.takeIf { it.tree == folder } ?: Index(folder, scan(folder)).also { index = it }
    }

    fun find(track: LyricsApi.Track): Lyrics? {
        val source = currentIndex() ?: return null
        val title = LyricsMatch.key(track.title)
        val artist = LyricsMatch.key(track.artist.substringBefore(','))
        if (title.isEmpty()) return null
        val matches = source.files.groupBy { match(it.name.substringBeforeLast('.'), title, artist) }
        val files = matches[2].orEmpty().ifEmpty { matches[1].orEmpty().takeIf { it.size == 1 }.orEmpty() }
        for (file in files.sortedByDescending { it.name.endsWith(".ttml", true) }) {
            val text = read(file.uri) ?: continue
            val lyrics = if (file.name.endsWith(".ttml", true)) LyricsApi.parseTtml(text)
                else LyricsApi.lrcLines(text).takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = true) }
            if (lyrics != null && !lyrics.isEmpty && tree == source.tree) return lyrics.copy(provider = PROVIDER)
        }
        return null
    }

    private fun match(filename: String, title: String, artist: String): Int {
        val name = LyricsMatch.key(filename)
        return when {
            artist.isNotEmpty() && (name == LyricsMatch.key("$artist $title") || name == LyricsMatch.key("$title $artist")) -> 2
            name == title -> 1
            else -> 0
        }
    }

    private fun scan(folder: Uri): List<File> {
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(folder)))
        val visited = HashSet<String>()
        val out = ArrayList<File>()
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        while (pending.isNotEmpty() && visited.size < MAX_FILES && out.size < MAX_FILES) {
            val dir = pending.removeFirst()
            if (!visited.add(dir)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, dir)
            resolver.query(children, columns, null, null, null)?.use { rows ->
                while (out.size < MAX_FILES && rows.moveToNext()) {
                    val id = rows.getString(0) ?: continue
                    val name = rows.getString(1).orEmpty()
                    if (rows.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (pending.size < MAX_FILES) pending += id
                    } else if (name.endsWith(".lrc", true) || name.endsWith(".ttml", true)) {
                        out += File(DocumentsContract.buildDocumentUriUsingTree(folder, id), name)
                    }
                }
            } ?: throw IOException("Could not read lyrics folder")
        }
        return out
    }

    private fun read(uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > MAX_SIZE) return null
                out.write(buffer, 0, count)
            }
            out.toByteArray().decodeToString().removePrefix("\uFEFF")
        }
    }.getOrNull()
}
