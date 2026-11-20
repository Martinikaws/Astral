// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.engine.R
import app.myspoti.host.android.Covers
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.spicy.LocalSpicyBackdropSlot
import com.music.spotui.spicy.SpicyBackdropSlot
import com.music.spotui.spicy.SpicyBackdropSlotLayer
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.lyrics.ImmersiveLyricsPanel
import com.music.spotui.ui.look.MeshGradientBackground
import com.music.spotui.ui.look.rememberArtworkColors
import com.music.spotui.ui.screens.rememberLyricsState
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay

/**
 * The immersive player as the car shows it: the cover's mesh backdrop over the whole surface, and inside
 * the area the host leaves free ([insets]) a layout picked by [carLayoutMode]. The lyrics scroll to the
 * sung line by themselves; nothing here takes touch.
 */
@Composable
fun CarPlayerContent(insets: CarInsetsPx) {
    val song = Graph.currentSongState
    val title = song.title.value
    val artist = song.singer.value
    val coverUrl = Covers.large(song.coverUri.value)
    val playing = song.playingState.value
    val palette = rememberArtworkColors(coverUrl.takeIf { it.isNotBlank() })
    val slot = remember { SpicyBackdropSlot() }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MeshGradientBackground(palette = palette, modifier = Modifier.fillMaxSize(), trackKey = coverUrl, active = playing)
        // The Spicy lyrics background is published by the lyrics panel and drawn here, under all content.
        SpicyBackdropSlotLayer(slot, Modifier.fillMaxSize())
        val start = with(density) { insets.left.toDp() }
        val top = with(density) { insets.top.toDp() }
        val end = with(density) { insets.right.toDp() }
        val bottom = with(density) { insets.bottom.toDp() }
        CompositionLocalProvider(LocalSpicyBackdropSlot provides slot) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(start = start, top = top, end = end, bottom = bottom).padding(16.dp)) {
                if (title.isBlank()) {
                    Text(
                        text = stringResource(R.string.aa_car_nothing_playing),
                        style = Immersive.Headline,
                        color = Immersive.TextSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else when (carLayoutMode(maxWidth.value, maxHeight.value)) {
                    CarLayoutMode.WIDE -> WideLayout(title, artist, coverUrl, maxWidth, maxHeight)
                    CarLayoutMode.STACKED -> StackedLayout(title, artist, coverUrl, maxWidth)
                    CarLayoutMode.COMPACT -> CompactLayout(title, artist, coverUrl, maxHeight)
                }
            }
        }
    }
}

/** Cover, credits and progress on the left, the lyrics on the right. */
@Composable
private fun WideLayout(title: String, artist: String, coverUrl: String, width: Dp, height: Dp) {
    val lyrics = CarUiState.lyricsOn
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        val column = if (lyrics) width * 0.42f else width.coerceAtMost(520.dp)
        Column(
            Modifier.width(column).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cover = minOf(column, height - CREDITS_HEIGHT).coerceAtLeast(72.dp)
            Cover(coverUrl, Modifier.size(cover), RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            Credits(title, artist, Modifier.width(cover), center = false)
            Spacer(Modifier.height(10.dp))
            Progress(Modifier.width(cover))
        }
        if (lyrics) ImmersiveLyricsPanel(
            Modifier.weight(1f).fillMaxHeight(),
            PaddingValues(vertical = 8.dp),
            showControls = false,
        )
    }
}

/** Cover and credits as a header, the lyrics under it. */
@Composable
private fun StackedLayout(title: String, artist: String, coverUrl: String, width: Dp) {
    val lyrics = CarUiState.lyricsOn
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val cover = (width * 0.28f).coerceIn(64.dp, 120.dp)
            Cover(coverUrl, Modifier.size(cover), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Credits(title, artist, Modifier.fillMaxWidth(), center = false)
                Progress(Modifier.fillMaxWidth())
            }
        }
        if (lyrics) ImmersiveLyricsPanel(
            Modifier.weight(1f).fillMaxWidth(),
            PaddingValues(top = 12.dp, bottom = 8.dp),
            showControls = false,
        )
    }
}

/** Cover, credits and the line being sung, nothing else. */
@Composable
private fun CompactLayout(title: String, artist: String, coverUrl: String, height: Dp) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Cover(coverUrl, Modifier.size(height.coerceIn(48.dp, 112.dp)), RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Credits(title, artist, Modifier.fillMaxWidth(), center = false)
            if (CarUiState.lyricsOn) {
                val line = currentLyricLine(title, artist)
                if (line.isNotBlank()) Text(
                    text = line,
                    style = Immersive.BodyStrong,
                    color = Immersive.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The text of the lyric line being sung, from the same lyrics source the player's panel reads. */
@Composable
private fun currentLyricLine(title: String, artist: String): String {
    val vm: LyricsViewModel = viewModel()
    val state = rememberLyricsState(vm, title, artist)
    val lyrics: Lyrics? = (state as? LyricsViewModel.State.Loaded)?.lyrics
    val line by produceState(initialValue = "", lyrics) {
        val lines = lyrics?.takeIf { it.synced }?.lines.orEmpty()
        val times = lines.map { it.timeMs }
        while (true) {
            val i = activeLyricLine(times, SongPlayer.getCurrentPosition())
            value = lines.getOrNull(i)?.text.orEmpty().trim().takeIf { it != "♪" }.orEmpty()
            delay(LINE_POLL_MS)
        }
    }
    return line
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Cover(url: String, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    GlideImage(
        model = url.takeIf { it.isNotBlank() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = placeholder(R.drawable.placeholder),
        failure = placeholder(R.drawable.placeholder),
        modifier = modifier.clip(shape),
        requestBuilderTransform = { it.override(COVER_DECODE_PX) },
    )
}

@Composable
private fun Credits(title: String, artist: String, modifier: Modifier, center: Boolean) {
    val align = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title, style = Immersive.Title, color = Immersive.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = artist, style = Immersive.Subhead, color = Immersive.TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A thin progress line and the two times, following the playhead a few times a second. */
@Composable
private fun Progress(modifier: Modifier) {
    val clock: State<Pair<Long, Long>> = produceState(0L to 0L) {
        while (true) {
            value = SongPlayer.getCurrentPosition().coerceAtLeast(0L) to SongPlayer.getDuration().coerceAtLeast(0L)
            delay(PROGRESS_POLL_MS)
        }
    }
    val (position, duration) = clock.value
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f))
                .drawBehind {
                    drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
                },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clockText(position), style = Immersive.Caption, color = Immersive.TextSecondary)
            Text(clockText(duration), style = Immersive.Caption, color = Immersive.TextSecondary)
        }
    }
}

private fun clockText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** The room the credits and the progress line take under the cover of the wide layout. */
private val CREDITS_HEIGHT = 130.dp
private const val COVER_DECODE_PX = 640
private const val LINE_POLL_MS = 200L
private const val PROGRESS_POLL_MS = 250L
