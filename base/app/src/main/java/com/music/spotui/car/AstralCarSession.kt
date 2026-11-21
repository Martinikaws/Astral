// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** One connection to a car: the template screen and the surface the player is drawn on. */
class AstralCarSession : Session() {
    private var renderer: CarSurfaceRenderer? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                val r = CarSurfaceRenderer(carContext).also { renderer = it }
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(r)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                renderer?.release()
                renderer = null
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen = CarPlayerScreen(carContext)
}
