// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.spicy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.music.spotui.data.preferences.SpicyBackgroundSettings

/** What [SpicyBackgroundLayer] needs, published by a lyrics panel for its host to draw. */
class SpicyBackdropRequest(
    val coverUri: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: () -> Long,
    val settings: SpicyBackgroundSettings,
    val reduceMotion: Boolean,
)

/** Lets a host draw the Spicy background full screen, behind its content, for a panel it contains. */
class SpicyBackdropSlot {
    var request by mutableStateOf<SpicyBackdropRequest?>(null)
}

val LocalSpicyBackdropSlot = staticCompositionLocalOf<SpicyBackdropSlot?> { null }

/** Draws the slot's request, if any. One engine stays alive across requests so covers crossfade inside it. */
@Composable
fun SpicyBackdropSlotLayer(slot: SpicyBackdropSlot, modifier: Modifier = Modifier) {
    val r = slot.request ?: return
    val position by androidx.compose.runtime.rememberUpdatedState(r.positionMs)
    SpicyBackgroundLayer(
        coverUri = r.coverUri,
        artist = r.artist,
        playing = r.playing,
        positionMs = { position() },
        settings = r.settings,
        modifier = modifier.fillMaxSize(),
        reduceMotion = r.reduceMotion,
    )
}
