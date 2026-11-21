/*
 * Written for Astral, 2026-11, for the Squared look. Not part of Square: the pure parts of
 * SquaredAdapters.kt (state mapping, queue index mapping, data rules), kept free of Compose and
 * of the player singleton so they can be unit-tested.
 */
package com.music.spotui.ui.immersive.squared

import androidx.media3.common.Player
import com.music.spotui.ui.immersive.player.ImmersivePlayerState
import com.music.spotui.ui.immersive.player.ImmersiveRepeat
import com.music.spotui.ui.immersive.squared.player.PlaybackState
import com.music.spotui.ui.immersive.squared.player.SquaredArtist

/** Astral's player state in the shape Square's player screens draw. */
internal fun ImmersivePlayerState.toPlaybackState(uri: String, contextUri: String?): PlaybackState {
    val artists = artist.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return PlaybackState(
        hasItem = title.isNotBlank() || uri.isNotBlank(),
        mediaId = uri.ifBlank { null },
        title = title,
        artist = artist,
        album = album,
        artworkUrl = coverUrl.ifBlank { null },
        explicit = explicit,
        isPlaying = playing,
        wantsPlay = playing || buffering,
        isBuffering = buffering,
        durationMs = durationMs,
        hasNext = true,
        hasPrevious = true,
        shuffleEnabled = shuffle,
        smartShuffle = smart,
        // Square counts media3's modes: 0 off, 1 one, 2 all.
        repeatMode = when (repeatMode) {
            ImmersiveRepeat.ALL -> Player.REPEAT_MODE_ALL
            ImmersiveRepeat.ONE, ImmersiveRepeat.ONCE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        },
        source = contextName,
        artists = artists.map { SquaredArtist(it, "") },
        contextUri = contextUri?.takeIf { it.isNotBlank() },
    )
}

/** Square's queue rows count the playing track as row 0, so row n is `items[n - 1]`. */
internal fun <T> queueItemAt(items: List<T>, index: Int): T? = items.getOrNull(index - 1)

/** A drag in the queue list: [moved] goes next to [target], after it when dragged down. */
internal class QueueMove<T>(val moved: T, val target: T, val after: Boolean)

internal fun <T> planQueueMove(items: List<T>, from: Int, to: Int): QueueMove<T>? {
    val moved = queueItemAt(items, from) ?: return null
    val target = queueItemAt(items, to) ?: return null
    return QueueMove(moved, target, after = to > from)
}

/** How often the position is read: fast only while the player is open and the song plays. */
internal fun positionPollMs(sheetOpen: Boolean, playing: Boolean): Long = when {
    !sheetOpen -> 250L
    playing -> 50L
    else -> 400L
}

/** Whether the Canvas and the catalogue's moving cover may play, under the data rules. */
internal fun motionAllowed(
    canvasSetting: Boolean,
    energyAllowsCanvas: Boolean,
    dataSaverActive: Boolean,
    metered: Boolean,
    canvasWifiOnly: Boolean,
): Boolean = canvasSetting && energyAllowsCanvas && !dataSaverActive && !(metered && canvasWifiOnly)
