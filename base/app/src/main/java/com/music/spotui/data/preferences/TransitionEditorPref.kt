package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.myspoti.host.android.Logbook
import app.myspoti.spotify.SpotifyPlaylists
import app.myspoti.spotify.bridge.LogMask
import com.music.spotui.di.Graph
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Preferences and debug dump hooks for the experimental DJ Transition Editor.
 */
object TransitionEditorPref {
    private const val PREFS = "myspoti_prefs"
    private const val KEY_ENABLED = "transition_editor_enabled"
    private const val KEY_DUMP_MIXED_PLAYLISTS = "debug_dump_mixed_playlists"

    private fun prefs(context: Context = Graph.app) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled by mutableStateOf(runCatching { prefs().getBoolean(KEY_ENABLED, false) }.getOrDefault(false))
        private set

    private const val MAX_DUMP_FILES = 50

    /** Debug builds only: a stored "on" in a release build is ignored. */
    var dumpMixedPlaylists by mutableStateOf(
        app.myspoti.engine.BuildConfig.DEBUG &&
            runCatching { prefs().getBoolean(KEY_DUMP_MIXED_PLAYLISTS, false) }.getOrDefault(false),
    )
        private set

    init {
        applyRecorder()
        if (enabled) com.music.spotui.automix.CustomTransitionStore.warmUp()
    }

    /** The recorder is set only while the dump is on, and cleared otherwise. */
    private fun applyRecorder() {
        SpotifyPlaylists.rawPlaylistRecorder =
            if (dumpMixedPlaylists) { uri, op, rawJson -> maybeDumpPlaylist(uri, op, rawJson) } else null
    }

    /** Re-arms the recorder after a restart when the dump was left on (does nothing otherwise). */
    fun restoreRecorder() = applyRecorder()

    @JvmName("updateEnabled")
    fun setEnabled(value: Boolean) {
        enabled = value
        runCatching { prefs().edit().putBoolean(KEY_ENABLED, value).apply() }
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        if (value) com.music.spotui.automix.CustomTransitionStore.warmUp()
        runCatching { prefs(context).edit().putBoolean(KEY_ENABLED, value).apply() }
    }

    @JvmName("updateDumpMixedPlaylists")
    fun setDumpMixedPlaylists(value: Boolean) {
        dumpMixedPlaylists = value
        runCatching { prefs().edit().putBoolean(KEY_DUMP_MIXED_PLAYLISTS, value).apply() }
        applyRecorder()
    }

    fun setDumpMixedPlaylists(context: Context, value: Boolean) {
        dumpMixedPlaylists = value
        runCatching { prefs(context).edit().putBoolean(KEY_DUMP_MIXED_PLAYLISTS, value).apply() }
        applyRecorder()
    }

    fun dumpDir(context: Context = Graph.app): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "mixed-playlists")
    }

    /** Keeps only the newest [MAX_DUMP_FILES] dumps in [dir]. */
    internal fun pruneDumps(dir: File, keep: Int = MAX_DUMP_FILES) {
        val files = dir.listFiles { f -> f.isFile } ?: return
        if (files.size <= keep) return
        files.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .drop(keep)
            .forEach { runCatching { it.delete() } }
    }

    /**
     * When [dumpMixedPlaylists] is enabled, redacts secrets with [LogMask.redact] and writes
     * the raw Spotify playlist GraphQL response to `<externalFilesDir or filesDir>/mixed-playlists/`.
     */
    fun maybeDumpPlaylist(uri: String, operation: String, jsonText: String): File? {
        if (!dumpMixedPlaylists) return null
        return runCatching {
            val dir = dumpDir()
            if (!dir.exists()) dir.mkdirs()
            val id = uri.substringAfterLast(':').replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "unknown" }
            val opSafe = operation.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "op" }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val file = File(dir, "playlist-${id}-${opSafe}-${timestamp}.json")
            val redacted = LogMask.redact(jsonText)
            file.writeText(redacted)
            pruneDumps(dir)
            Logbook.log("transition-debug: dumped $operation for $uri to ${file.absolutePath} (${redacted.length} chars)")
            file
        }.onFailure { e ->
            Logbook.log("transition-debug: failed to dump $operation for $uri: ${e.javaClass.simpleName} ${e.message}")
        }.getOrNull()
    }
}
