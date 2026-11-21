package com.music.spotui.data.api

import android.os.SystemClock
import com.music.spotui.data.entity.Lyrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** The last lookup requested by the visible lyrics UI. Kept only for this app session. */
internal object LyricsLookupStatus {
    data class Attempt(val title: String, val provider: String?, val found: Boolean, val durationMs: Long)
    private val seq = AtomicLong()
    private val record = MutableStateFlow<Attempt?>(null)
    val last = record.asStateFlow()

    suspend fun observe(title: String, fetch: suspend () -> Lyrics?): Lyrics? {
        val request = seq.incrementAndGet()
        val start = SystemClock.elapsedRealtime()
        val lyrics = fetch()
        if (request == seq.get()) record.value = Attempt(title, lyrics?.provider, lyrics != null, SystemClock.elapsedRealtime() - start)
        return lyrics
    }
}
