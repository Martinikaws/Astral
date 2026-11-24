package com.music.spotui.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

internal object LegacyLyricsCache {
    private val generatedFile = Regex("[A-Za-z0-9]{1,64}\\.json(?:\\.(?:tmp|old))?")

    fun cleanup(files: File, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { discardGeneratedLyrics(files) }
    }

    /** Old files may contain Spicy backing vocals labelled with a different provider. */
    internal fun discardGeneratedLyrics(files: File) {
        val live = File(files, "offline/lyrics")
        val parked = File(files, "offline-accounts").listFiles().orEmpty()
            .filter { it.isDirectory }.map { File(it, "lyrics") }
        val caches = listOf(File(files, "lyrics_cache1"), File(files, "lyrics_cache2"))
        for (folder in caches + live + parked) {
            folder.listFiles().orEmpty().filter { it.isFile && generatedFile.matches(it.name) }
                .forEach { it.delete() }
        }
    }
}
