package com.music.spotui.data.api

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyLyricsCacheTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun legacyCacheCleanupKeepsAudioAndCurrentLyrics() {
        fun file(path: String) = File(files.root, path).apply { parentFile?.mkdirs(); writeText("{}") }
        val legacy = listOf(
            file("lyrics_cache1/song.json"),
            file("lyrics_cache2/song.json"),
            file("offline/lyrics/song.json.tmp"),
            file("offline-accounts/other/lyrics/song.json.old"),
        )
        val kept = listOf(
            file("offline/audio/song"),
            file("offline/lyrics-v2/song.json"),
            file("lyrics_cache3/song.json"),
            file("offline/lyrics/personal.lrc"),
        )

        LegacyLyricsCache.discardGeneratedLyrics(files.root)
        LegacyLyricsCache.discardGeneratedLyrics(files.root)

        assertTrue(legacy.none { it.exists() })
        assertTrue(kept.all { it.isFile })
    }
}
