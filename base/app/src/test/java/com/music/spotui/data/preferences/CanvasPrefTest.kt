/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.di.Graph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Canvas background toggle and blur percentage: defaults, clamping, and persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CanvasPrefTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    @Before fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
    }

    @After fun tearDown() {
        CanvasPref.setBackground(app, true)
        CanvasPref.setBlur(app, 0)
        prefs.edit().clear().commit()
    }

    @Test fun defaultsAreBackgroundOnAndZeroBlur() {
        assertTrue(CanvasPref.background)
        assertEquals(0, CanvasPref.blur)
    }

    @Test fun setBackgroundUpdatesStateAndPersists() {
        CanvasPref.setBackground(app, false)
        assertFalse(CanvasPref.background)
        assertFalse(prefs.getBoolean("canvas_background", true))

        CanvasPref.setBackground(app, true)
        assertTrue(CanvasPref.background)
        assertTrue(prefs.getBoolean("canvas_background", false))
    }

    @Test fun setBlurClampsAndPersists() {
        CanvasPref.setBlur(app, -10)
        assertEquals(0, CanvasPref.blur)
        assertEquals(0, prefs.getInt("canvas_blur", -1))

        CanvasPref.setBlur(app, 150)
        assertEquals(100, CanvasPref.blur)
        assertEquals(100, prefs.getInt("canvas_blur", -1))

        CanvasPref.setBlur(app, 42)
        assertEquals(42, CanvasPref.blur)
        assertEquals(42, prefs.getInt("canvas_blur", -1))
    }
}
