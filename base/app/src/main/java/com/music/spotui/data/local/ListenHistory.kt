package com.music.spotui.data.local

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Every listen on this phone, whatever the source (Spotify, downloads, local files), for the
 * in-app recap. Stays on the phone; nothing is reported to Spotify or anyone else.
 * A listen counts after 30 s, like most services.
 */
object ListenHistory {
    enum class Source { SPOTIFY, DOWNLOAD, LOCAL }

    data class Listen(
        val atMs: Long,
        val key: String,
        val title: String,
        val artist: String,
        val album: String,
        val cover: String,
        val playedMs: Long,
        val source: Source,
        /** Each credited artist on its own, for the top artists; just the line's first one for older listens. */
        val artists: List<String> = listOf(artist.substringBefore(",").trim()),
    )

    /**
     * The artists of a song credited as [line] ("A, B") with [ids] artist ids: split only when the
     * names and the ids agree in number, so a name with a comma in it ("Tyler, The Creator", one id)
     * stays whole.
     */
    fun artistsOf(line: String, ids: List<String>): List<String> {
        val names = line.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        return if (ids.size > 1 && names.size == ids.size) names else listOf(line.trim()).filter { it.isNotEmpty() }
    }

    const val MIN_PLAYED_MS = 30_000L
    private var file: File? = null
    private var statsFile: File? = null
    private val lock = Any()

    fun init(context: Context) {
        file = File(context.filesDir, "listen_history.jsonl")
        statsFile = File(context.filesDir, "listen_stats.json")
    }

    fun record(listen: Listen) {
        if (listen.playedMs < MIN_PLAYED_MS || listen.title.isBlank()) return
        val f = file ?: return
        val line = JSONObject()
            .put("at", listen.atMs).put("key", listen.key).put("title", listen.title).put("artist", listen.artist)
            .put("album", listen.album).put("cover", listen.cover).put("ms", listen.playedMs).put("src", listen.source.name)
            .put("artists", org.json.JSONArray(listen.artists))
            .toString()
        synchronized(lock) {
            f.appendText(line + "\n")
            loaded?.let { loaded = it + listen }
            val store = statsLoaded ?: loadOrBuildStatsLocked()
            store.addListen(listen)
            saveStatsLocked(store)
        }
    }

    /** The history as last read, kept while the file only grows by [record]: not read again for every screen. */
    private var loaded: List<Listen>? = null

    fun all(): List<Listen> = synchronized(lock) {
        loaded?.let { return it }
        val f = file?.takeIf { it.exists() } ?: return emptyList()
        val list = f.readLines().mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                val artist = o.optString("artist")
                val artists = o.optJSONArray("artists")?.let { a -> List(a.length()) { a.getString(it) } }?.filter { it.isNotBlank() }
                Listen(o.getLong("at"), o.getString("key"), o.getString("title"), artist, o.optString("album"),
                    o.optString("cover"), o.getLong("ms"), Source.valueOf(o.optString("src", "SPOTIFY")),
                    artists?.ifEmpty { null } ?: listOf(artist.substringBefore(",").trim()))
            }.getOrNull()
        }
        loaded = list
        list
    }

    fun clear() = synchronized(lock) {
        loaded = null
        statsLoaded = null
        file?.delete()
        statsFile?.delete()
        owner()?.delete()
    }

    /*
     * The history belongs to the account it was heard on, as the downloads do (OfflineAccounts): on an
     * account switch it is put aside under that account's key (`listen-history/<key>.jsonl`) and the
     * next account's comes back. A history from before has no owner and goes to the first account read.
     */
    private fun owner(): File? = file?.let { File(it.parentFile, "listen_history.owner") }
    private fun parked(key: String): File? = file?.let { File(it.parentFile, "listen-history/$key.jsonl") }
    private fun ownerKey(): String? = owner()?.takeIf { it.isFile }?.let { runCatching { it.readText().trim() }.getOrNull() }?.ifBlank { null }

    /** [from] is put after what [to] already holds (a copy left by an earlier move that went wrong), then removed. */
    private fun moveInto(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (!to.exists() && from.renameTo(to)) return
        to.appendText(from.readText())
        from.delete()
    }

    /** Leaving the account [key] (its stamp wins): its history goes aside, the next account starts with none. */
    fun park(key: String?) = synchronized(lock) {
        val f = file ?: return@synchronized
        val owner = ownerKey() ?: key ?: "unclaimed-${System.currentTimeMillis()}"
        runCatching { if (f.exists()) moveInto(f, parked(owner)!!) }
        owner()?.delete()
        statsFile?.delete()
        loaded = null
        statsLoaded = null
    }

    /** The account [key] is signed in: its own history is the one shown; one with no owner yet becomes its own. */
    fun claim(key: String) = synchronized(lock) {
        val f = file ?: return@synchronized
        val owner = ownerKey()
        if (owner == key) return@synchronized
        runCatching {
            if (owner != null && f.exists()) moveInto(f, parked(owner)!!)
            parked(key)?.takeIf { it.exists() }?.let { moveInto(it, f) }
            owner()?.writeText(key)
        }
        statsFile?.delete()
        loaded = null
        statsLoaded = null
    }

    /** The account [key] is removed from the phone: its history put aside goes with it. */
    fun forget(key: String) = synchronized(lock) { parked(key)?.delete(); Unit }

    data class Ranked(val name: String, val subtitle: String, val cover: String, val count: Int, val minutes: Long)

    data class Recap(
        val listens: Int,
        val minutes: Long,
        val bySource: Map<Source, Long>,
        val topTracks: List<Ranked>,
        val topArtists: List<Ranked>,
        val topAlbums: List<Ranked>,
    )

    /** One calendar week of listening: its first day (local midnight, epoch ms) and the minutes played in it. */
    data class Week(val startMs: Long, val minutes: Long)

    /** Minutes per week over the last [weeks] calendar weeks, oldest first, the current week last. */
    fun weekly(weeks: Int = 8, nowMs: Long = System.currentTimeMillis()): List<Week> = weekly(all(), weeks, nowMs)

    /**
     * Groups [listens] by calendar week (weeks start on the locale's first day, in [zone]); the
     * current, unfinished week is last. Weeks without listens count 0.
     */
    fun weekly(
        listens: List<Listen>,
        weeks: Int,
        nowMs: Long,
        zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        firstDayOfWeek: Int = java.util.Calendar.getInstance().firstDayOfWeek,
    ): List<Week> {
        val cal = java.util.Calendar.getInstance(zone).apply {
            this.firstDayOfWeek = firstDayOfWeek
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            while (get(java.util.Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        // Week starts, newest first; calendar arithmetic keeps daylight-saving weeks right.
        val starts = (0 until weeks).map { i ->
            if (i > 0) cal.add(java.util.Calendar.WEEK_OF_YEAR, -1)
            cal.timeInMillis
        }.reversed()
        val ends = starts.drop(1) + Long.MAX_VALUE
        return starts.indices.map { i ->
            val ms = listens.filter { it.atMs >= starts[i] && it.atMs < ends[i] && it.atMs <= nowMs }.sumOf { it.playedMs }
            Week(starts[i], ms / 60_000)
        }
    }

    /** Every credited artist of a listen counts it, not only the first one on the line. */
    internal fun rankArtists(list: List<Listen>): List<Ranked> = list
        .flatMap { l -> l.artists.filter { it.isNotBlank() }.map { it to l } }
        .groupBy({ it.first }, { it.second })
        .map { (name, l) -> Ranked(name, "", l.first().cover, l.size, l.sumOf { it.playedMs } / 60_000) }
        .sortedWith(compareByDescending<Ranked> { it.count }.thenByDescending { it.minutes })
        .take(10)

    /** Totals and top lists for listens since [sinceMs]. */
    fun recap(sinceMs: Long): Recap {
        val list = all().filter { it.atMs >= sinceMs }
        fun rank(group: (Listen) -> String, subtitle: (Listen) -> String) = list.groupBy(group)
            .filterKeys { it.isNotBlank() }
            .map { (name, l) -> Ranked(name, subtitle(l.first()), l.first().cover, l.size, l.sumOf { it.playedMs } / 60_000) }
            .sortedWith(compareByDescending<Ranked> { it.count }.thenByDescending { it.minutes })
            .take(10)
        return Recap(
            listens = list.size,
            minutes = list.sumOf { it.playedMs } / 60_000,
            bySource = list.groupBy { it.source }.mapValues { (_, l) -> l.sumOf { it.playedMs } / 60_000 },
            topTracks = rank({ it.title + "\u0000" + it.artist }, { it.artist }).map { it.copy(name = it.name.substringBefore('\u0000')) },
            topArtists = rankArtists(list),
            topAlbums = rank({ it.album }, { it.artist }),
        )
    }

    // ── Persistent daily / monthly / per-track & artist stats ─────────────────────────────

    data class DayStat(val date: String, val label: String, val minutes: Long, val seconds: Long)

    data class MonthlyRecap(
        val month: String,
        val monthLabel: String,
        val totalMinutes: Long,
        val listens: Int,
        val streakDays: Int,
        val topTrack: Ranked?,
        val topArtist: Ranked?,
    )

    data class TimeListenedSummary(
        val todayMinutes: Long,
        val todaySeconds: Long,
        val totalMinutes: Long,
        val last7Days: List<DayStat>,
        val streakDays: Int,
        val monthlyRecap: MonthlyRecap,
    )

    private class AggItem(
        val name: String,
        val subtitle: String,
        var cover: String,
        var count: Int = 0,
        var playedMs: Long = 0L,
    )

    private class StatsStore(
        val dailySeconds: MutableMap<String, Long> = HashMap(),
        val monthlySeconds: MutableMap<String, Long> = HashMap(),
        val tracks: MutableMap<String, AggItem> = HashMap(),
        val artists: MutableMap<String, AggItem> = HashMap(),
        var totalMs: Long = 0L,
        var listenCount: Int = 0,
    ) {
        fun addListen(listen: Listen) {
            val sec = (listen.playedMs / 1_000L).coerceAtLeast(0L)
            val date = dateKey(listen.atMs)
            val month = date.substring(0, 7)
            dailySeconds[date] = (dailySeconds[date] ?: 0L) + sec
            monthlySeconds[month] = (monthlySeconds[month] ?: 0L) + sec
            totalMs += listen.playedMs
            listenCount += 1

            val trackKey = "${listen.title}\u0000${listen.artist}"
            val t = tracks.getOrPut(trackKey) { AggItem(listen.title, listen.artist, listen.cover) }
            t.count += 1
            t.playedMs += listen.playedMs
            if (t.cover.isBlank() && listen.cover.isNotBlank()) t.cover = listen.cover

            for (aName in listen.artists) {
                if (aName.isBlank()) continue
                val a = artists.getOrPut(aName) { AggItem(aName, "", listen.cover) }
                a.count += 1
                a.playedMs += listen.playedMs
                if (a.cover.isBlank() && listen.cover.isNotBlank()) a.cover = listen.cover
            }
        }
    }

    private var statsLoaded: StatsStore? = null

    private fun dateKey(atMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(atMs))

    private fun loadOrBuildStatsLocked(): StatsStore {
        statsLoaded?.let { return it }
        val sf = statsFile
        if (sf != null && sf.exists()) {
            val parsed = runCatching { parseStatsJson(sf.readText()) }.getOrNull()
            if (parsed != null) {
                statsLoaded = parsed
                return parsed
            }
        }
        val rebuilt = StatsStore()
        val history = all()
        history.forEach { rebuilt.addListen(it) }
        statsLoaded = rebuilt
        if (sf != null && history.isNotEmpty()) saveStatsLocked(rebuilt)
        return rebuilt
    }

    private fun saveStatsLocked(store: StatsStore) {
        val sf = statsFile ?: return
        runCatching {
            val root = JSONObject()
            root.put("totalMs", store.totalMs)
            root.put("listenCount", store.listenCount)
            root.put("daily", JSONObject(store.dailySeconds as Map<*, *>))
            root.put("monthly", JSONObject(store.monthlySeconds as Map<*, *>))

            val tracksObj = JSONObject()
            store.tracks.forEach { (k, v) ->
                tracksObj.put(k, JSONObject()
                    .put("name", v.name).put("sub", v.subtitle).put("cover", v.cover)
                    .put("count", v.count).put("ms", v.playedMs))
            }
            root.put("tracks", tracksObj)

            val artistsObj = JSONObject()
            store.artists.forEach { (k, v) ->
                artistsObj.put(k, JSONObject()
                    .put("name", v.name).put("cover", v.cover)
                    .put("count", v.count).put("ms", v.playedMs))
            }
            root.put("artists", artistsObj)
            sf.writeText(root.toString())
        }
    }

    private fun parseStatsJson(text: String): StatsStore {
        val root = JSONObject(text)
        val store = StatsStore(
            totalMs = root.optLong("totalMs", 0L),
            listenCount = root.optInt("listenCount", 0),
        )
        root.optJSONObject("daily")?.let { d ->
            d.keys().forEach { k -> store.dailySeconds[k] = d.optLong(k, 0L) }
        }
        root.optJSONObject("monthly")?.let { m ->
            m.keys().forEach { k -> store.monthlySeconds[k] = m.optLong(k, 0L) }
        }
        root.optJSONObject("tracks")?.let { t ->
            t.keys().forEach { k ->
                val o = t.getJSONObject(k)
                store.tracks[k] = AggItem(
                    o.optString("name"), o.optString("sub"), o.optString("cover"),
                    o.optInt("count"), o.optLong("ms"),
                )
            }
        }
        root.optJSONObject("artists")?.let { a ->
            a.keys().forEach { k ->
                val o = a.getJSONObject(k)
                store.artists[k] = AggItem(
                    o.optString("name"), "", o.optString("cover"),
                    o.optInt("count"), o.optLong("ms"),
                )
            }
        }
        return store
    }

    /** Returns total minutes listened on Astral across all recorded listens. */
    fun totalMinutesListened(): Long = synchronized(lock) {
        loadOrBuildStatsLocked().totalMs / 60_000L
    }

    /** Returns seconds listened on a specific date (`YYYY-MM-DD`). */
    fun dailySeconds(date: String): Long = synchronized(lock) {
        loadOrBuildStatsLocked().dailySeconds[date] ?: 0L
    }

    /** Returns minutes listened in a specific month (`YYYY-MM`). */
    fun monthlyMinutes(month: String): Long = synchronized(lock) {
        (loadOrBuildStatsLocked().monthlySeconds[month] ?: 0L) / 60L
    }

    /** Computes the current consecutive-day listening streak ending today (or yesterday). */
    fun listeningStreak(nowMs: Long = System.currentTimeMillis()): Int {
        val daily = synchronized(lock) { loadOrBuildStatsLocked().dailySeconds.toMap() }
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        val todayKey = dateKey(cal.timeInMillis)
        if ((daily[todayKey] ?: 0L) <= 0L) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        var streak = 0
        while (true) {
            val key = dateKey(cal.timeInMillis)
            if ((daily[key] ?: 0L) <= 0L) break
            streak += 1
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    /** Builds the complete Time Listened & Monthly Recap summary for the UI. */
    fun timeListenedSummary(nowMs: Long = System.currentTimeMillis()): TimeListenedSummary {
        val store = synchronized(lock) { loadOrBuildStatsLocked() }
        val dailyMap = synchronized(lock) { store.dailySeconds.toMap() }
        val monthlyMap = synchronized(lock) { store.monthlySeconds.toMap() }
        val totalMin = synchronized(lock) { store.totalMs / 60_000L }

        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val todayKey = dateKey(nowMs)
        val todaySec = dailyMap[todayKey] ?: 0L

        // Last 7 days (oldest first, today last)
        val days = ArrayList<DayStat>(7)
        for (i in 6 downTo 0) {
            cal.timeInMillis = nowMs
            cal.add(Calendar.DAY_OF_MONTH, -i)
            val key = dateKey(cal.timeInMillis)
            val sec = dailyMap[key] ?: 0L
            days += DayStat(
                date = key,
                label = dayFmt.format(cal.time),
                minutes = sec / 60L,
                seconds = sec,
            )
        }

        // Current month recap
        cal.timeInMillis = nowMs
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStartMs = cal.timeInMillis
        val monthKey = todayKey.substring(0, 7)
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        val monthRecap = recap(monthStartMs)
        val monthMinutes = maxOf(monthRecap.minutes, (monthlyMap[monthKey] ?: 0L) / 60L)
        val streak = listeningStreak(nowMs)

        return TimeListenedSummary(
            todayMinutes = todaySec / 60L,
            todaySeconds = todaySec,
            totalMinutes = totalMin,
            last7Days = days,
            streakDays = streak,
            monthlyRecap = MonthlyRecap(
                month = monthKey,
                monthLabel = monthLabel,
                totalMinutes = monthMinutes,
                listens = monthRecap.listens,
                streakDays = streak,
                topTrack = monthRecap.topTracks.firstOrNull(),
                topArtist = monthRecap.topArtists.firstOrNull(),
            ),
        )
    }
}
