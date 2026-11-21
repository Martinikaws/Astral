/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/player/PlayerPanel.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed; the audio-effects state (speed, pitch, reverb, karaoke, the web SDK
 * check) is Astral's AudioEffectsPref (the panel itself is in SquaredEffectsPanel.kt), the sleep
 * timer is not carried over, the Effects and Info tabs sit behind SquaredFeatures.effects and .credits, and the strings are Astral's squared_* resources.
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import com.music.spotui.ui.immersive.squared.components.horizontalEdgeFade
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.Lyrics
import com.music.spotui.ui.immersive.squared.SquaredFeatures
import androidx.compose.foundation.layout.RowScope
import com.music.spotui.ui.immersive.squared.glass.LiquidBottomTab
import com.music.spotui.ui.immersive.squared.glass.LiquidBottomTabs
import com.music.spotui.ui.immersive.squared.glass.LiquidSlider
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import com.adamglin.phosphoricons.regular.TextAa
import com.adamglin.phosphoricons.fill.SlidersHorizontal
import com.adamglin.phosphoricons.fill.TextAa
import com.adamglin.phosphoricons.fill.Queue
import com.adamglin.phosphoricons.fill.VinylRecord
import com.adamglin.phosphoricons.regular.Queue
import com.adamglin.phosphoricons.regular.VinylRecord
import com.adamglin.phosphoricons.fill.Info
import com.adamglin.phosphoricons.regular.Info
import com.adamglin.phosphoricons.regular.X
import com.adamglin.phosphoricons.regular.Sparkle
import com.music.spotui.ui.immersive.squared.glass.pressable

/** Which panel is open below the transport controls. */
enum class PlayerPanel {
    NONE,
    QUEUE,
    LYRICS,
    EFFECTS,

    /** Who made the track: performers, writers, producers, and the label. */
    INFO,

    /**
     * The Connect device list and the playlist picker.
     *
     * Both used to open as modals over the transport. The player already has one
     * place where a second thing is shown — the slot the cover lives in — and
     * two mechanisms for the same job meant the screen sometimes dimmed itself
     * and sometimes did not, depending on which button had been pressed.
     */
    DEVICES,
    ADD_TO_PLAYLIST,
}

@Composable
fun PlayerPanelSection(
    panel: PlayerPanel,
    onSelect: (PlayerPanel) -> Unit,
    queue: List<QueueEntry>,
    lyrics: Lyrics?,
    lyricsLoading: Boolean,
    positionMs: androidx.compose.runtime.State<Long>,
    onPlayQueueItem: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    /** The layer the panel refracts; see the liquid-glass note in PlayerScreen. */
    backdrop: com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop,
    modifier: Modifier = Modifier,
    /** False on a phone held sideways, where the cover is beside the views already. */
    showCover: Boolean = true,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The same segmented control the tab bar uses, because this is the same
        // kind of choice: three views of one screen, exactly one of them
        // showing. Three separate toggles said "three independent switches",
        // which is not what cover, lyrics and effects are.
        //
        // The queue in here too: it opens in the same middle of the screen as
        // the others, and a button for it beside the title was one more
        // control in a row that already had the radio and the heart.
        val effectsSpeed by com.music.spotui.data.preferences.AudioEffectsPref.speed.collectAsStateWithLifecycle()
        val effectsPitch by com.music.spotui.data.preferences.AudioEffectsPref.pitch.collectAsStateWithLifecycle()
        val effectsReverb by com.music.spotui.data.preferences.AudioEffectsPref.reverb.collectAsStateWithLifecycle()
        val effectsKaraoke by com.music.spotui.data.preferences.AudioEffectsPref.karaoke.collectAsStateWithLifecycle()
        val effectsSupported by com.music.spotui.di.AudioEffectsState.supported.collectAsStateWithLifecycle()
        val karaokeOn = effectsSupported && effectsKaraoke > 0f
        val effectsOn = effectsSupported && (effectsSpeed != 1f || effectsPitch != 1f || effectsReverb > 0f || karaokeOn)

        val views = remember(showCover) {
            listOfNotNull(
                PlayerPanel.NONE.takeIf { showCover },
                PlayerPanel.LYRICS,
                PlayerPanel.QUEUE,
                PlayerPanel.EFFECTS.takeIf { SquaredFeatures.effects },
                PlayerPanel.INFO.takeIf { SquaredFeatures.credits },
            )
        }
        val selected = views.indexOf(panel).coerceAtLeast(0)
        // Stable, or LiquidBottomTabs throws away the state it keys on this and
        // the indicator stops animating; see the note in SquareApp.
        val selectedState = rememberUpdatedState(selected)
        val selectedTabIndex = remember { { selectedState.value } }

        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { onSelect(views[it]) },
            backdrop = backdrop,
            tabsCount = views.size,
            accentColor = GlassInk,
            containerColor = LocalPlayerFilm.current,
            // Lit, whatever the phone's theme. The player's film is dark in
            // both, and the default follows the theme: on a light phone it
            // darkened a slot of an already dark bar and could hardly be seen.
            indicatorColor = GlassInk.copy(alpha = 0.18f),
            // Slimmer than the tab bar, and icon-only. This one sits under the
            // transport rather than at the edge of the window, so it has to
            // read as a smaller thing than the app's own navigation.
            height = 42.dp,
            // Under half the height, or the refraction from the two long edges
            // meets in the middle and draws a seam across the capsule.
            lensDepth = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .padding(top = 14.dp),
        ) {
            if (showCover) PanelTab(
                icon = PhosphorIcons.Regular.VinylRecord,
                activeIcon = PhosphorIcons.Fill.VinylRecord,
                label = stringResource(R.string.squared_cover),
                selected = selected == views.indexOf(PlayerPanel.NONE),
            ) { onSelect(PlayerPanel.NONE) }
            PanelTab(
                icon = PhosphorIcons.Regular.TextAa,
                activeIcon = PhosphorIcons.Fill.TextAa,
                label = stringResource(R.string.squared_lyrics),
                selected = selected == views.indexOf(PlayerPanel.LYRICS),
                // The karaoke lives in this view and keeps working with the
                // panel shut; see the same halo on the effects tab.
                marked = karaokeOn,
            ) { onSelect(PlayerPanel.LYRICS) }
            PanelTab(
                icon = PhosphorIcons.Regular.Queue,
                activeIcon = PhosphorIcons.Fill.Queue,
                label = stringResource(R.string.squared_queue),
                selected = selected == views.indexOf(PlayerPanel.QUEUE),
            ) { onSelect(PlayerPanel.QUEUE) }
            if (SquaredFeatures.effects) PanelTab(
                icon = PhosphorIcons.Regular.SlidersHorizontal,
                activeIcon = PhosphorIcons.Fill.SlidersHorizontal,
                label = stringResource(R.string.squared_effects),
                selected = selected == views.indexOf(PlayerPanel.EFFECTS),
                marked = effectsOn,
            ) { onSelect(PlayerPanel.EFFECTS) }
            if (SquaredFeatures.credits) PanelTab(
                icon = PhosphorIcons.Regular.Info,
                activeIcon = PhosphorIcons.Fill.Info,
                label = stringResource(R.string.squared_credits),
                selected = selected == views.indexOf(PlayerPanel.INFO),
            ) { onSelect(PlayerPanel.INFO) }
        }

    }
}

@Composable
private fun RowScope.PanelTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** The filled cut of the same glyph, for the view being shown. */
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    /**
     * A light on the tab: what is behind it is doing something right now.
     *
     * Used by the effects, which are the one view whose settings go on working
     * after it is closed — a song playing a third slower with the panel shut
     * looks, from this row, exactly like a song playing normally.
     */
    marked: Boolean = false,
    onClick: () -> Unit,
) {
    LiquidBottomTab(onClick = onClick) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
        if (marked) {
            // A halo rather than a badge stuck to the corner: the tabs are
            // small and round-ended, and a dot on the edge of one reads as
            // damage to the capsule.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(30.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(GlassInk.copy(alpha = 0.28f), androidx.compose.ui.graphics.Color.Transparent),
                        ),
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            )
        }
        Icon(
            // Filled rather than only brighter. The indicator behind the icon
            // moves, so at a glance the two states differed by a shade of grey
            // sliding around; a solid glyph says which view you are in without
            // being read against its neighbours.
            imageVector = if (selected) activeIcon else icon,
            contentDescription = label,
            tint = when {
                selected -> GlassInk
                marked -> GlassInk.copy(alpha = 0.85f)
                else -> GlassInkDim
            },
            modifier = Modifier.size(19.dp),
        )
        }
    }
}

/** One row of the upcoming-tracks list. */
data class QueueEntry(
    val index: Int,
    /** What the row is, so a list that loses one can animate the rest. */
    val uri: String,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
    /** Already heard: shown above the playing track, dimmed. */
    val played: Boolean = false,
    val recommended: Boolean = false,
    /** Put in the queue by hand, as "play next": the "Next in queue" section. */
    val queued: Boolean = false,
    /** The playlist or album it came from, for the "Next from" heading. */
    val contextLabel: String = "",
)

@Composable
internal fun EmptyPanel(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = GlassInkDim,
        )
    }
}
