// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * The Android Auto car app that draws the immersive player. Android Auto gives media apps no way to
 * draw, so this is a navigation-category car app: only those get a surface (see [CarSurfaceRenderer]).
 * The media browser of PlaybackService is a separate integration and stays as it was.
 */
class AstralCarAppService : CarAppService() {
    // The validator checks the signature of the host app (Android Auto, Android Automotive) against the
    // allow-list of known hosts, so only a genuine host can bind to this service. A debuggable build
    // accepts any host so the Desktop Head Unit, which is not on that list, works.
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = AstralCarSession()
}
