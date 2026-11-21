package com.music.spotui.di

/** When the local player may hand its audio to the phone's DSP (see LocalPlayer.offload); pure, unit-tested. */
internal object LocalOffload {
    /**
     * Only when no stage of ours changes the samples, as Media3 leaves them out in offload:
     * the equalizer off or flat, no spatial width (null or 1), AutoMix and the crossfade off, the
     * speed and the pitch at 1, no silence skipping, no reverb and no karaoke.
     */
    fun allowed(
        eqFlat: Boolean, spatialWidth: Float?, autoMix: Boolean, crossfadeSeconds: Int, speed: Float, skipSilence: Boolean,
        pitch: Float = 1f, reverb: Float = 0f, karaoke: Float = 0f,
    ): Boolean =
        eqFlat && (spatialWidth == null || spatialWidth <= 1f) && !autoMix && crossfadeSeconds <= 0 && speed == 1f && !skipSilence &&
            pitch == 1f && reverb <= 0f && karaoke <= 0f
}
