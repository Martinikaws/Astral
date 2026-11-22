// SPDX-License-Identifier: GPL-3.0-or-later
package com.music.spotui.spicy

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.myspoti.power.EnergySettings
import com.music.spotui.audio.TrackAudioSync
import com.music.spotui.data.preferences.SpicyRenderPref
import com.music.spotui.di.Graph
import com.music.spotui.di.OutputLag
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.delay

/** Opt-in beat feedback using Astral's existing analysis and the system's touch feedback setting. */
@Composable
internal fun MusicHaptics() {
    val options by SpicyRenderPref.state.collectAsState()
    if (!options.musicHaptics) return
    val lowPower by EnergySettings.state.collectAsState()
    val track = SongPlayer.nowPlaying.value.uri
    val playing = Graph.currentSongState.playingState.value
    if (lowPower.policy.saverEnabled || !playing || track.isBlank()) return

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    LaunchedEffect(lifecycle, view, track) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (lowPower.policy.allowBackgroundLookups) TrackAudioSync.requestAnalysis()
            var previousPulse: Float? = null
            var lastTickAt = -1_000L
            var previousPosition = -1L
            while (true) {
                val position = (SongPlayer.getCurrentPosition() - OutputLag.ms()).coerceAtLeast(0L)
                val pulse = TrackAudioSync.peekMotion(position)?.beatPulse
                val previous = previousPulse
                if (position - previousPosition !in 1..120) lastTickAt = position
                if (pulse != null && previous != null && pulse > 0.72f && pulse - previous > 0.16f &&
                    position - lastTickAt >= 150L && SongPlayer.isPlaying() && view.hasWindowFocus()
                ) {
                    val feedback = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                        else HapticFeedbackConstants.CLOCK_TICK
                    view.performHapticFeedback(feedback)
                    lastTickAt = position
                }
                previousPulse = pulse
                previousPosition = position
                delay(25)
            }
        }
    }
}
