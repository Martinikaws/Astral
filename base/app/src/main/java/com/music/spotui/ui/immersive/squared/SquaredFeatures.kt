package com.music.spotui.ui.immersive.squared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Switches for the parts of Square's player that are not wired to Astral yet.
 *
 * [effects] is the hook for the effects panel (tempo, pitch, reverb, karaoke): it stays false
 * until Astral's audio-effects engine can drive it, and nothing in the player reaches the panel
 * while it is.
 */
object SquaredFeatures {
    const val effects: Boolean = false

    /** The credits panel; Astral has no credits source in this phase. */
    const val credits: Boolean = false

    /** The karaoke amount, 0 while there are no effects. Square reads it from its audio engine. */
    val karaoke: StateFlow<Float> = MutableStateFlow(0f)

    @Suppress("UNUSED_PARAMETER")
    fun setKaraoke(amount: Float) = Unit
}
