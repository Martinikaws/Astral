package com.music.spotui.di

/** Soloist runs inside this app: when it is the active Connect device, its own report of playing beats the account's cluster state, which lags it. */
internal object SoloistRemote {
    fun plays(activeId: String?, ownId: String, connected: Boolean, active: Boolean, paused: Boolean): Boolean =
        activeId != null && ownId.isNotBlank() && activeId == ownId && connected && active && !paused
}
