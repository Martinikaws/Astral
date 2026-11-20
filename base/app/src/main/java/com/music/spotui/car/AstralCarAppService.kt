// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * The Android Auto car app that draws the immersive player. Android Auto gives media apps no way to
 * draw, so this is a navigation-category car app: only those get a surface (see [CarSurfaceRenderer]).
 * The media browser of PlaybackService is a separate integration and stays as it was.
 */
class AstralCarAppService : CarAppService() {
    // Astral is sideloaded, never on Google Play, so the host cannot be checked against Play's list of
    // allowed hosts: any host is accepted. It is only given the player's state and the control buttons,
    // which any media controller of the device can read and press anyway.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = AstralCarSession()
}
