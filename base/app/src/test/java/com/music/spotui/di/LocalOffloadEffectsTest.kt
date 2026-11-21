package com.music.spotui.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pitch, the reverb and the karaoke keep the audio out of the phone's DSP too (it skips our stages). */
class LocalOffloadEffectsTest {
    private fun allowed(pitch: Float = 1f, reverb: Float = 0f, karaoke: Float = 0f) =
        LocalOffload.allowed(true, null, false, 0, 1f, false, pitch, reverb, karaoke)

    @Test fun offloadedWithNoEffects() = assertTrue(allowed())

    @Test fun anyEffectKeepsTheAudioHere() {
        assertFalse(allowed(pitch = 1.1f))
        assertFalse(allowed(reverb = 0.2f))
        assertFalse(allowed(karaoke = 0.5f))
    }
}
