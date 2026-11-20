// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.myspoti.engine.R
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The template over the surface: a navigation template whose action strip carries the controls
 * (previous, play/pause, next, lyrics on/off). The player itself is drawn on the surface.
 */
class CarPlayerScreen(carContext: CarContext) : Screen(carContext) {
    init {
        // The play glyph follows the playback. The state is Compose state, polled rather than observed so
        // that nothing depends on a Compose host being alive while the surface is not.
        lifecycleScope.launch {
            // Only while the screen is on show; the template is rebuilt when it starts again.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var shown = Graph.currentSongState.playingState.value
                while (true) {
                    delay(POLL_MS)
                    val now = Graph.currentSongState.playingState.value
                    if (now != shown) {
                        shown = now
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val playing = Graph.currentSongState.playingState.value
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.car_ic_previous) { SongPlayer.previous(by = "car") })
            .addAction(
                action(if (playing) R.drawable.car_ic_pause else R.drawable.car_ic_play) {
                    if (Graph.currentSongState.playingState.value) SongPlayer.pause(by = "car") else SongPlayer.play(by = "car")
                    invalidate()
                },
            )
            .addAction(action(R.drawable.car_ic_next) { SongPlayer.next(by = "car") })
            .addAction(
                action(if (CarUiState.lyricsOn) R.drawable.car_ic_lyrics_on else R.drawable.car_ic_lyrics_off) {
                    CarUiState.lyricsOn = !CarUiState.lyricsOn
                    invalidate()
                },
            )
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setBackgroundColor(CarColor.createCustom(0xFF000000.toInt(), 0xFF000000.toInt()))
            .build()
    }

    private fun action(icon: Int, onClick: () -> Unit): Action = Action.Builder()
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).setTint(CarColor.DEFAULT).build())
        .setOnClickListener(onClick)
        .build()

    private companion object {
        const val POLL_MS = 400L
    }
}
