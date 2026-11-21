/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/player/PlayerState.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed; PlaybackState keeps Square's fields but artists are SquaredArtist;
 * rememberPlaybackState, rememberQueue and RemotePlayback.asPlaybackState (they read media3 and
 * Spotify Connect) are not carried over, SquaredAdapters.kt builds the same models from Astral's
 * player.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.player

import com.music.spotui.ui.immersive.squared.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.Player

/**
 * Everything the player UI draws **except** the playback position.
 *
 * Position is deliberately kept out: it changes several times a second, and
 * anything that reads it recomposes at that rate. Keeping it in this class would
 * drag the whole screen — including a hundred-row list — along with the seek
 * bar, which is enough jank to make the audio stutter. See [rememberPositionMs].
 */
data class PlaybackState(
    val hasItem: Boolean = false,
    /** Spotify URI of the current track, for highlighting it in lists. */
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    /**
     * The record it is from, where the queue knows it.
     *
     * Only the player uses it, and only to ask the other catalogue for that
     * record's own artwork — the tall picture and the moving cover a song
     * inherits from its album; see AppleCatalog.
     */
    val album: String = "",
    /** And its address, where the queue knows it; see EXTRA_ALBUM_URI. */
    val albumUri: String? = null,
    val artworkUrl: String? = null,
    /** Whether the source rates this track as explicit; see EXTRA_EXPLICIT. */
    val explicit: Boolean = false,
    val isPlaying: Boolean = false,
    /**
     * What the listener asked for, which is not always what is happening.
     *
     * A track being fetched is not playing yet, and the play button read that
     * as "still paused": every tap during a load meant play again, so the load
     * could not be called off and the button that says pause never appeared.
     * The glyph and the tap both follow this; [isPlaying] stays the fact, for
     * the things that follow the sound rather than the intent.
     */
    val wantsPlay: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val smartShuffle: Boolean = false,
    val smartRecommended: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Tempo, 1.0 being the track as recorded. */
    val speed: Float = 1f,
    /** Pitch, independent of [speed]. */
    val pitch: Float = 1f,
    /**
     * Where this queue came from, ready to read: "Playlist · Estate 2025".
     *
     * Carried on the media item rather than derived here, because by the time
     * the player sees a track the only thing left of the tap that started it is
     * a URI, and a URI does not say whether it was reached from a playlist, an
     * album, an artist or a search.
     */
    val source: String = "",
    /**
     * Where the line under the title leads, when it leads anywhere.
     *
     * Both are null on a queue that came from somewhere with no page of its
     * own: a handful of search results, a track handed over by another device.
     * The labels are then plain text, which is what they have always been.
     */
    val artistUri: String? = null,
    /**
     * Each credited artist separately, so a line naming two of them opens the
     * one that was pressed. Empty when the source gave only the joined names.
     */
    val artists: List<SquaredArtist> = emptyList(),
    val contextUri: String? = null,
)

/** One credited artist and the address of their page. */
data class SquaredArtist(val name: String, val uri: String)
