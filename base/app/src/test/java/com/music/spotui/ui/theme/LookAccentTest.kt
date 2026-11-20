/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.music.spotui.data.api.SupportReminder
import com.music.spotui.data.api.SupportReminder.Accent
import com.music.spotui.data.preferences.Look
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

/** Covers dynamic cover accent contrast adjustment and per-Look accent selection rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LookAccentTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        resetSupportReminder()
    }

    @After fun tearDown() {
        resetSupportReminder()
    }

    private fun resetSupportReminder() {
        app.getSharedPreferences("support_reminder", Context.MODE_PRIVATE).edit().clear().commit()
        SupportReminder.init(app)
    }

    @Test fun `adjustForDarkGlass lifts dark cover colors`() {
        val dark = Color(0xFF1B3A5C)
        assertTrue(dark.luminance() < 0.28f)
        val adjusted = adjustForDarkGlass(dark)
        assertTrue("Expected luminance >= 0.28f, was ${adjusted.luminance()}", adjusted.luminance() >= 0.28f)
        assertTrue("Expected luminance <= 0.72f, was ${adjusted.luminance()}", adjusted.luminance() <= 0.72f)
    }

    @Test fun `adjustForDarkGlass clamps overly bright cover colors`() {
        val bright = Color(0xFFFFF8E7)
        assertTrue(bright.luminance() > 0.72f)
        val adjusted = adjustForDarkGlass(bright)
        assertTrue("Expected luminance <= 0.72f, was ${adjusted.luminance()}", adjusted.luminance() <= 0.72f)
        assertTrue("Expected luminance >= 0.28f, was ${adjusted.luminance()}", adjusted.luminance() >= 0.28f)
    }

    @Test fun `adjustForDarkGlass leaves mid-luminance colors unchanged`() {
        val midGreen = Color(0xFF1ED760)
        val midBlue = Color(0xFF509BF5)
        assertTrue(midGreen.luminance() in 0.28f..0.72f)
        assertTrue(midBlue.luminance() in 0.28f..0.72f)
        assertEquals(midGreen, adjustForDarkGlass(midGreen))
        assertEquals(midBlue, adjustForDarkGlass(midBlue))
    }

    @Test fun `honoursChosenAccent excludes fixed-green Spotifusion and cover-driven Glassify`() {
        assertTrue(SupportReminder.honoursChosenAccent(Look.CLASSIC))
        assertTrue(SupportReminder.honoursChosenAccent(Look.IMMERSIVE))
        assertTrue(SupportReminder.honoursChosenAccent(Look.LIQUID_MUSIC))
        assertTrue(SupportReminder.honoursChosenAccent(Look.STARRY_NIGHT))
        assertFalse(SupportReminder.honoursChosenAccent(Look.SPOTIFUSION))
        assertFalse(SupportReminder.honoursChosenAccent(Look.GLASSIFY))
    }

    @Test fun `Cupertino defaults to its red and honours a chosen accent`() {
        assertTrue(SupportReminder.honoursChosenAccent(Look.CUPERTINO))
        assertTrue(SupportReminder.hasLookDefaultAccent(Look.CUPERTINO))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.CUPERTINO))
        assertEquals(CupertinoAccent, lookAccentOrNull(Look.CUPERTINO))
        SupportReminder.setAccent(app, Look.CUPERTINO, Accent.BLUE)
        assertEquals(Accent.BLUE.color, lookAccentOrNull(Look.CUPERTINO))
        SupportReminder.clearAccent(app)
        assertEquals(CupertinoAccent, lookAccentOrNull(Look.CUPERTINO))
    }

    @Test fun `hasLookDefaultAccent is true only for LiquidMusic, Starry Night and Cupertino`() {
        assertTrue(SupportReminder.hasLookDefaultAccent(Look.LIQUID_MUSIC))
        assertTrue(SupportReminder.hasLookDefaultAccent(Look.STARRY_NIGHT))
        assertTrue(SupportReminder.hasLookDefaultAccent(Look.CUPERTINO))
        assertFalse(SupportReminder.hasLookDefaultAccent(Look.CLASSIC))
        assertFalse(SupportReminder.hasLookDefaultAccent(Look.IMMERSIVE))
        assertFalse(SupportReminder.hasLookDefaultAccent(Look.SPOTIFUSION))
        assertFalse(SupportReminder.hasLookDefaultAccent(Look.GLASSIFY))
    }

    @Test fun `usesLookDefaultAccent reflects setAccent and clearAccent`() {
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.LIQUID_MUSIC))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.STARRY_NIGHT))
        assertFalse(SupportReminder.usesLookDefaultAccent(Look.IMMERSIVE))
        assertFalse(SupportReminder.usesLookDefaultAccent(Look.CLASSIC))

        // Setting a classic accent does not override an interactive look's default accent.
        SupportReminder.setAccent(app, Look.CLASSIC, Accent.PURPLE)
        assertEquals(Accent.PURPLE, SupportReminder.accentOf(Look.CLASSIC))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.LIQUID_MUSIC))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.STARRY_NIGHT))

        // Choosing an accent on an interactive look overrides the look default until cleared.
        SupportReminder.setAccent(app, Look.STARRY_NIGHT, Accent.BLUE)
        assertTrue(SupportReminder.immersiveAccentChosen)
        assertEquals(Accent.BLUE, SupportReminder.accentOf(Look.STARRY_NIGHT))
        assertEquals(Accent.BLUE, SupportReminder.accentOf(Look.LIQUID_MUSIC))
        assertEquals(Accent.BLUE.color, lookAccentOrNull(Look.STARRY_NIGHT))
        assertEquals(Accent.BLUE.color, lookAccentOrNull(Look.LIQUID_MUSIC))
        assertFalse(SupportReminder.usesLookDefaultAccent(Look.STARRY_NIGHT))
        assertFalse(SupportReminder.usesLookDefaultAccent(Look.LIQUID_MUSIC))

        SupportReminder.clearAccent(app)
        assertFalse(SupportReminder.immersiveAccentChosen)
        assertEquals(Accent.GREEN, SupportReminder.accentOf(Look.IMMERSIVE))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.STARRY_NIGHT))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.LIQUID_MUSIC))
        assertEquals(StarryAccent, lookAccentOrNull(Look.STARRY_NIGHT))
        assertEquals(LiquidMusicAccent, lookAccentOrNull(Look.LIQUID_MUSIC))

        // Re-initializing from SharedPreferences preserves the cleared state and classic choice.
        SupportReminder.init(app)
        assertFalse(SupportReminder.immersiveAccentChosen)
        assertEquals(Accent.GREEN, SupportReminder.accentOf(Look.IMMERSIVE))
        assertTrue(SupportReminder.usesLookDefaultAccent(Look.STARRY_NIGHT))
        assertEquals(Accent.PURPLE, SupportReminder.accentOf(Look.CLASSIC))
    }
}
