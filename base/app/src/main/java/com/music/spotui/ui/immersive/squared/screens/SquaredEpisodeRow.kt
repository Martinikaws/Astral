/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/library/EpisodeRow.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed; imports redirected to the squared packages. episodeLine, which Square
 * keeps at the end of its PlaylistScreen.kt, and formatTotal, are here beside the row that uses it.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.screens

import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.*
import com.music.spotui.ui.immersive.squared.components.*
import com.music.spotui.ui.immersive.squared.glass.*
import com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.*
import com.music.spotui.ui.immersive.squared.glass.shapes.*
import com.music.spotui.ui.immersive.squared.glass.morph.*
import com.music.spotui.ui.immersive.squared.player.*
import com.music.spotui.ui.immersive.squared.model.*
import com.music.spotui.ui.immersive.squared.screens.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.DotsThree
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play

/**
 * One episode, as a card rather than a song's row.
 *
 * An episode is chosen differently from a song: by what it is about, by when
 * it came out, and by whether it was already heard. So the card carries what a
 * song's row has no room for — a couple of lines of what the show says about
 * it, how far in the listener got, and a mark on the ones heard to the end —
 * and the play button is a target of its own, the way Spotify lays these out.
 */
@Composable
internal fun EpisodeRow(
    episode: CatalogTrack,
    isCurrent: Boolean,
    /** Whether the one that is current is playing right now. */
    playing: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    /** Off on a show's own page, where every card is the same show. */
    showName: Boolean = false,
    unavailable: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .pressable(onClick, shape = shape, pressedScale = 0.985f, onLongClick = onMenu)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .alpha(if (unavailable) 0.38f else if (episode.played && !isCurrent) 0.72f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquaredArtwork(
                url = episode.artworkUrl,
                title = episode.name,
                modifier = Modifier.size(60.dp),
                corner = 10.dp,
                decodeSize = 60.dp,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    episode.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showName && episode.artist.isNotBlank()) {
                    Text(
                        episode.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        if (episode.summary.isNotBlank()) {
            Text(
                episode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = dim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (episode.played) {
                Icon(
                    PhosphorIcons.Fill.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                episodeLine(episode, showName = false),
                style = MaterialTheme.typography.labelMedium,
                color = dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val started = !episode.played && episode.resumeMs > 0 && episode.durationMs > 0
            if (started) {
                Box(
                    Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(dim.copy(alpha = 0.25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((episode.resumeMs.toFloat() / episode.durationMs).coerceIn(0.03f, 1f))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .pressable(onMenu, shape = CircleShape, pressedScale = 0.9f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.Bold.DotsThree,
                    contentDescription = stringResource(R.string.squared_more),
                    tint = dim,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ink)
                    .pressable(onClick, shape = CircleShape, pressedScale = 0.9f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isCurrent && playing) PhosphorIcons.Fill.Pause else PhosphorIcons.Fill.Play,
                    contentDescription = stringResource(R.string.squared_play),
                    // Whatever reads on the ink it sits on, the page's own
                    // colour being anything the cover made it.
                    tint = if (ink.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * An episode's second line: the day it came out, then how long it is or how
 * much of it is left, or that it was heard. The show's name first where the
 * list is not the show's own.
 */
@Composable
internal fun episodeLine(track: CatalogTrack, showName: Boolean): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val day = track.releasedAt?.let { iso ->
        runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
    }?.let { millis ->
        val thisYear = java.time.Year.now().value.toString() == track.releasedAt.take(4)
        android.text.format.DateUtils.formatDateTime(
            context,
            millis,
            android.text.format.DateUtils.FORMAT_SHOW_DATE or
                android.text.format.DateUtils.FORMAT_ABBREV_MONTH or
                if (thisYear) android.text.format.DateUtils.FORMAT_NO_YEAR else android.text.format.DateUtils.FORMAT_SHOW_YEAR,
        )
    }
    val length = when {
        track.played -> stringResource(R.string.squared_episode_played)
        track.resumeMs > 0 && track.durationMs > track.resumeMs ->
            stringResource(R.string.squared_episode_left, formatTotal(track.durationMs - track.resumeMs))
        track.durationMs > 0 -> formatTotal(track.durationMs)
        else -> null
    }
    return listOfNotNull(track.artist.takeIf { showName && it.isNotBlank() }, day, length)
        .joinToString(" · ")
}

/** Total playlist length, in hours and minutes rather than a running clock. */
@Composable
internal fun formatTotal(ms: Long): String {
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) stringResource(R.string.squared_hours_minutes, hours, minutes)
    else stringResource(R.string.squared_minutes_only, minutes)
}
