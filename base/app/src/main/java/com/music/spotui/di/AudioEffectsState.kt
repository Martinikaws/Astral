package com.music.spotui.di

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the effects (speed, pitch, reverb, karaoke; see AudioEffectsPref) apply to what plays now.
 *
 * They live in the local player's audio chain, so they apply to what [LocalPlayer] plays: the
 * extensions' streams (the usual source of a song), downloads and the phone's own files. They do
 * not apply to Spotify's own audio (the native engine or the Web Player, picked as the audio
 * source, and episodes of podcasts), nor to Spotify Connect and Cast devices: the app never gets
 * those samples.
 */
object AudioEffectsState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True while the song plays through [LocalPlayer]; the controls are disabled, with an explanation, otherwise. */
    val supported: StateFlow<Boolean> by lazy {
        snapshotFlow { LocalPlayer.active.value }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, LocalPlayer.active.value)
    }
}
