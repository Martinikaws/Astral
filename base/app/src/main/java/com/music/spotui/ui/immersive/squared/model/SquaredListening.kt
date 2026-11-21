/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/data/ListeningHistory.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: only the shapes and the monthly summary are kept (ListeningEvent, MonthlyListening,
 * and summarize, now SquaredListening.summarize). Square records its own events and reconciles
 * them with Spotify's; here the events are read from Astral's ListenHistory, which already keeps
 * only plays that ran long enough, so every event counts as qualified and the month's minutes are
 * the time actually played. Square's sampler and merge logic are left out.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.model

import com.music.spotui.data.local.ListenHistory
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One song heard, with when and for how long. */
data class ListeningEvent(
    val id: String,
    val track: CatalogTrack,
    val startedAt: Long,
    val listenedMs: Long = 0,
    val qualified: Boolean = true,
)

/** What a month of listening comes to. */
data class MonthlyListening(
    val month: YearMonth,
    val plays: Int,
    val minutes: Long,
    val tracks: List<Pair<CatalogTrack, Int>>,
    val artists: List<Pair<String, Int>>,
)

/** Astral's play log as the events the recap page reads. */
fun ListenHistory.Listen.toListeningEvent(): ListeningEvent = ListeningEvent(
    id = "$atMs:$key",
    track = CatalogTrack(
        uri = key,
        name = title,
        artist = artist,
        artists = artists.filter { it.isNotBlank() }.map { CatalogArtist(it) },
        album = album,
        artworkUrl = cover.takeIf { it.isNotBlank() },
    ),
    startedAt = atMs,
    listenedMs = playedMs,
)

/** Pure aggregation of the listening log by month. */
object SquaredListening {
    /** The month the first event falls in, or [fallback] when there are none. */
    fun firstMonth(events: List<ListeningEvent>, fallback: YearMonth, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        events.minOfOrNull { it.startedAt }
            ?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }
            ?.coerceAtMost(fallback)
            ?: fallback

    fun summarize(events: List<ListeningEvent>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): MonthlyListening {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val counted = events.filter { it.qualified && it.startedAt in start until end }
        val tracks = counted.groupBy { it.track.uri }.values
            .map { it.last().track to it.size }
            .sortedWith(compareByDescending<Pair<CatalogTrack, Int>> { it.second }.thenBy { it.first.name })
        val artists = counted
            .flatMap { event ->
                event.track.artists.map { it.name }.ifEmpty { listOf(event.track.artist) }
                    .filter { it.isNotBlank() }.distinct()
            }
            .groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        val ms = counted.sumOf { it.listenedMs }
        return MonthlyListening(month, counted.size, ms / 60_000, tracks, artists)
    }
}
