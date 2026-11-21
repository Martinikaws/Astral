package com.music.spotui.ui.immersive.squared

/**
 * Switches for the parts of Square's player that are not wired to Astral yet.
 *
 * [effects] shows the effects panel (tempo, pitch, reverb, karaoke) and the karaoke dial, which
 * drive Astral's audio-effects engine (AudioEffectsPref).
 */
object SquaredFeatures {
    const val effects: Boolean = true

    /** The credits panel; Astral has no credits source in this phase. */
    const val credits: Boolean = false
}
