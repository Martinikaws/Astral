package com.music.spotui.data.api

import android.os.SystemClock
import com.music.spotui.data.entity.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** The last lookup requested by the visible lyrics UI. Kept only for this app session. */
internal object LyricsLookupStatus {
    data class Attempt(
        val title: String,
        val provider: String?,
        val found: Boolean,
        val durationMs: Long,
        val failed: Boolean,
    )
    private val seq = AtomicLong()
    private val record = MutableStateFlow<Attempt?>(null)
    val last = record.asStateFlow()

    suspend fun observe(title: String, fetch: suspend () -> Lyrics?): Lyrics? {
        val request = seq.incrementAndGet()
        val start = SystemClock.elapsedRealtime()
        fun recordAttempt(lyrics: Lyrics?, failed: Boolean) {
            if (request == seq.get()) {
                record.value = Attempt(
                    title, lyrics?.provider, lyrics != null && !lyrics.isEmpty,
                    SystemClock.elapsedRealtime() - start, failed,
                )
            }
        }
        return try {
            fetch().also { recordAttempt(it, failed = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordAttempt(null, failed = true)
            null
        }
    }
}
