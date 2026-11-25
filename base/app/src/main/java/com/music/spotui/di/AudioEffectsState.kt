package com.music.spotui.di

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.audio.AudioProcessor
import com.music.spotui.data.preferences.AudioEffectsPref
import com.soloist.android.runtime.SoloistAudioEffects
import com.soloist.android.runtime.SoloistPcmEffects
import com.soloist.android.service.SoloistRuntime
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the effects (speed, pitch, reverb, karaoke; see AudioEffectsPref) apply to what plays now.
 *
 * The effects run in Astral's local player and its built-in Soloist output.
 */
object AudioEffectsState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun install(context: Context) {
        val speed = AudioEffectsPref.speed
        val pitch = AudioEffectsPref.pitch
        if (!SoloistRuntime.isAvailable(context)) return
        SoloistRuntime.get(context).setAudioEffects(
            SoloistAudioEffects(
                rate = { speed.value to pitch.value },
                create = { sampleRate, channels, encoding ->
                    val format = AudioProcessor.AudioFormat(sampleRate, channels, encoding)
                    val stages = listOf(KaraokeProcessor(), ReverbProcessor()).filter {
                        it.configure(format) != AudioProcessor.AudioFormat.NOT_SET
                    }
                    if (stages.isEmpty()) null else object : SoloistPcmEffects {
                        init { reset() }

                        override fun process(input: ByteBuffer): ByteBuffer {
                            var output = input
                            for (stage in stages) {
                                stage.queueInput(output)
                                output = stage.output
                            }
                            return output
                        }

                        override fun reset() = stages.forEach { it.flush() }
                    }
                },
            ),
        )
    }

    val supported: StateFlow<Boolean> by lazy {
        if (!SoloistRuntime.isAvailable(Graph.app)) {
            return@lazy snapshotFlow { LocalPlayer.active.value }.stateIn(scope, SharingStarted.Eagerly, LocalPlayer.active.value)
        }
        val soloist = SoloistRuntime.get(Graph.app)
        combine(snapshotFlow { LocalPlayer.active.value }, soloist.playbackState, soloist.audioStats) { local, playback, audio ->
            local || (playback.connected && playback.active && audio.effectsAvailable)
        }.stateIn(scope, SharingStarted.Eagerly, LocalPlayer.active.value)
    }
}
