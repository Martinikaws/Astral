/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.data.preferences

import android.content.SharedPreferences
import com.music.spotui.di.Graph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The v1.0.1 -> 5-Look upgrade keeps the user's look, page style and theme mode. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LookMigrationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var p: SharedPreferences

    @Before fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        p = app.getSharedPreferences("settings_prefs", 0)
        p.edit().clear().commit()
    }

    private fun v101(look: String? = "CLASSIC", style: String? = null, theme: String? = null) {
        p.edit().apply {
            putBoolean("look_chosen", true)
            look?.let { putString("look", it) }
            style?.let { putString("page_style", it) }
            theme?.let { putString("astral_theme_mode", it) }
        }.commit()
    }

    private fun look() = Look.fromKey(p.getString("look", null))
    private fun pages() =
        if (p.getBoolean("page_style_override", false)) PageStyle.of(p.getString("page_style", null))
        else PageStyle.forLook(look())

    @Test fun `fresh install is untouched`() {
        LookMigration.run(p)
        assertNull(p.getString("look", null))
        assertNull(p.getString("page_style", null))
        assertNull(p.getString("astral_theme_mode", null))
        assertFalse(p.getBoolean("page_style_override", false))
        assertTrue(p.getBoolean(LookMigration.FLAG, false))
    }

    @Test fun `classic with Spotifusion pages becomes the Spotifusion look`() {
        v101(style = "SPOTIFUSION")
        LookMigration.run(p)
        assertEquals(Look.SPOTIFUSION, look())
        assertEquals(PageStyle.SPOTIFUSION, pages())
        assertFalse(p.getBoolean("page_style_override", false))
    }

    @Test fun `Glassify theme becomes the Glassify look`() {
        v101(look = "IMMERSIVE", style = "ORPHEON_GLASS", theme = "GLASSIFY")
        LookMigration.run(p)
        assertEquals(Look.GLASSIFY, look())
        assertEquals("GLASSIFY", p.getString("astral_theme_mode", null))
        assertEquals(PageStyle.ORPHEON_GLASS, pages())
    }

    @Test fun `LiquidMusic theme becomes the LiquidMusic look`() {
        v101(look = "CLASSIC", style = "ORPHEON_GLASS", theme = "LIQUID_MUSIC")
        LookMigration.run(p)
        assertEquals(Look.LIQUID_MUSIC, look())
        assertEquals("LIQUID_MUSIC", p.getString("astral_theme_mode", null))
        assertEquals(PageStyle.ORPHEON_GLASS, pages())
    }

    @Test fun `classic with glass pages keeps glass pages`() {
        v101(style = "ORPHEON_GLASS", theme = "STARRY_NIGHT")
        LookMigration.run(p)
        assertEquals(Look.CLASSIC, look())
        assertTrue(p.getBoolean("page_style_override", false))
        assertEquals(PageStyle.ORPHEON_GLASS, pages())
        assertEquals("STARRY_NIGHT", p.getString("astral_theme_mode", null))
    }

    @Test fun `classic that never stored a page style keeps the v1_0_1 default glass pages`() {
        v101()
        LookMigration.run(p)
        assertEquals(PageStyle.ORPHEON_GLASS, pages())
    }

    @Test fun `classic with basic pages stays basic without override`() {
        v101(style = "ORPHEON_BASIC")
        LookMigration.run(p)
        assertEquals(PageStyle.ORPHEON_BASIC, pages())
        assertFalse(p.getBoolean("page_style_override", false))
    }

    @Test fun `migration runs once`() {
        v101(style = "SPOTIFUSION")
        LookMigration.run(p)
        p.edit().putString("look", "CLASSIC").commit() // a later user choice must survive
        LookMigration.run(p)
        assertEquals(Look.CLASSIC, look())
    }

    @Test fun `fromKey accepts old and alias strings`() {
        assertEquals(Look.CLASSIC, Look.fromKey(null))
        assertEquals(Look.CLASSIC, Look.fromKey("  "))
        assertEquals(Look.CLASSIC, Look.fromKey("garbage"))
        assertEquals(Look.CLASSIC, Look.fromKey("CLASSIC"))
        assertEquals(Look.CLASSIC, Look.fromKey("orpheon_basic"))
        assertEquals(Look.IMMERSIVE, Look.fromKey("IMMERSIVE"))
        assertEquals(Look.IMMERSIVE, Look.fromKey("orpheon_glass"))
        assertEquals(Look.SPOTIFUSION, Look.fromKey("spotifusion_1_1"))
        assertEquals(Look.GLASSIFY, Look.fromKey("Glassify"))
        assertEquals(Look.LIQUID_MUSIC, Look.fromKey("LIQUID_MUSIC"))
        assertEquals(Look.LIQUID_MUSIC, Look.fromKey("liquidmusic"))
    }
}
