package com.music.spotui.ui.immersive.lyrics

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.spicy.SpicyLyricsLayer
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.rememberReduceMotion

/**
 * The Immersive panel's lyrics, drawn by the ported Spicy Lyrics renderer (see `com.music.spotui.spicy`)
 * in place of [LyricsList]. The position is the panel's own [rememberLyricsPlayhead] (the same one
 * the classic list reads), so a mix, a seek and a pause behave as they do there; a tap on a line
 * seeks to it the way the list does.
 */
@Composable
internal fun SpicyImmersiveLyrics(
    lyrics: Lyrics,
    trackKey: String,
    contentPadding: PaddingValues,
    romanizing: Boolean,
    modifier: Modifier = Modifier,
) {
    val playing = Graph.currentSongState.playingState.value
    // A source that calls itself synced with no real time on any line is static, as the list reads it.
    val isSynced = remember(lyrics) { lyrics.synced && lyrics.lines.any { it.timeMs > 0L } }
    val effective = remember(lyrics, isSynced) { if (lyrics.synced == isSynced) lyrics else lyrics.copy(synced = isSynced) }
    val head = rememberLyricsPlayhead(trackKey, playing && isSynced, LYRICS_WORD_FPS)
    val clock = head.clock
    val songDurationMs = SongPlayer.getDuration().coerceAtLeast(0L)
    SpicyLyricsLayer(
        lyrics = effective,
        documentKey = trackKey,
        currentTimeMs = { clock.longValue },
        playing = playing && isSynced,
        onSeek = { timeMs ->
            head.announceJump()
            SongPlayer.logLyricJump(timeMs)
            SongPlayer.seekTo(timeMs)
        },
        modifier = modifier.padding(contentPadding).padding(horizontal = Immersive.Gutter),
        romanize = romanizing,
        songDurationMs = songDurationMs,
        reduceMotion = rememberReduceMotion(),
        noticeColor = Immersive.TextSecondary,
    )
}
