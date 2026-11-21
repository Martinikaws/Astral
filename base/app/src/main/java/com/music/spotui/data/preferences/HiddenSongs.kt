package com.music.spotui.data.preferences

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

/** A song marked "Not interested": enough to list it in Settings and show it again. */
data class HiddenSong(
    val id: String,
    val title: String,
    val singer: String,
    val coverUri: String,
    val snoozedUntil: Long = 0L,
) {
    fun activeAt(now: Long) = snoozedUntil == 0L || now < snoozedUntil
}

/**
 * "Not interested", as YouTube Music's dislike: a list that stays on this phone (nothing is sent to
 * Spotify). A hidden song is skipped when a list, a radio or the queue comes to it, and is dimmed
 * in song lists; playing it by hand still plays it. Newest first.
 */
object HiddenSongs {
    private const val PREF = "HiddenSongs"
    private const val KEY = "songs"

    private val app get() = com.music.spotui.di.Graph.app
    private val handler = Handler(Looper.getMainLooper())
    private var entries by mutableStateOf(load(app).associateBy { it.id })
    val songs: List<HiddenSong> get() {
        val now = System.currentTimeMillis()
        return entries.values.filter { it.activeAt(now) }
    }
    private val expire = Runnable { set(app, songs) }

    init { scheduleExpiry() }

    /** The song [justHidden] is offered to be taken back (the Undo snackbar of the app's root). */
    var justHidden by mutableStateOf<HiddenSong?>(null)

    fun isHidden(trackId: String): Boolean = entries[trackId]?.activeAt(System.currentTimeMillis()) == true

    fun hide(context: Context, song: SongsModel, snooze: Boolean): Boolean {
        if (song.spotifyTrackId.isBlank() || isHidden(song.spotifyTrackId)) return false
        val until = if (snooze) System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30) else 0L
        val h = HiddenSong(song.spotifyTrackId, song.title, song.singer, song.coverUri, until)
        set(context, listOf(h) + songs)
        justHidden = h
        return true
    }

    fun show(context: Context, trackId: String) = set(context, songs.filterNot { it.id == trackId })

    private fun set(context: Context, list: List<HiddenSong>) {
        entries = list.associateBy { it.id }
        scheduleExpiry()
        val arr = JSONArray().also { a ->
            list.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("singer", it.singer).put("cover", it.coverUri).put("until", it.snoozedUntil)) }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun scheduleExpiry() {
        handler.removeCallbacks(expire)
        val next = entries.values.map { it.snoozedUntil }.filter { it > 0L }.minOrNull() ?: return
        handler.postDelayed(expire, (next - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun load(context: Context): List<HiddenSong> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> HiddenSong(o.getString("id"), o.optString("title"), o.optString("singer"), o.optString("cover"), o.optLong("until")) } }
        }.getOrDefault(emptyList())
    }
}
