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
        assertEquals(Look.CUPERTINO, Look.fromKey("cupertino"))
        assertEquals(Look.CUPERTINO, Look.fromKey("CUPERTINO"))
        assertEquals(Look.CUPERTINO, Look.fromKey("apple_music_style"))
    }

    @Test fun `Cupertino is an immersive look with its own key`() {
        assertTrue(Look.CUPERTINO.isImmersive())
        assertEquals("cupertino", Look.CUPERTINO.key)
        assertEquals(PageStyle.ORPHEON_GLASS, PageStyle.forLook(Look.CUPERTINO))
    }

    // ---- v1.0.3: Starry Night becomes a Look of its own ----

    @Test fun `Interactive with the never-touched Starry Night default moves to the Starry Night look`() {
        v101(look = "IMMERSIVE", style = "ORPHEON_GLASS", theme = "STARRY_NIGHT")
        LookMigration.run(p)
        assertEquals(Look.STARRY_NIGHT, look())
        assertEquals(PageStyle.ORPHEON_GLASS, pages())
        assertFalse(p.getBoolean("starry_night_enabled", true)) // the extra starts off
        assertTrue(p.getBoolean(LookMigration.FLAG_STARRY, false))
    }

    @Test fun `Interactive with Starry Night explicitly on moves to the Starry Night look`() {
        v101(look = "IMMERSIVE")
        p.edit().putBoolean("starry_night_enabled", true).commit()
        LookMigration.run(p)
        assertEquals(Look.STARRY_NIGHT, look())
        assertFalse(p.getBoolean("starry_night_enabled", true))
    }

    @Test fun `Interactive with Starry Night off stays Interactive`() {
        v101(look = "IMMERSIVE")
        p.edit().putBoolean("starry_night_enabled", false).commit()
        LookMigration.run(p)
        assertEquals(Look.IMMERSIVE, look())
        assertFalse(p.getBoolean("starry_night_enabled", true))
    }

    @Test fun `other looks keep their look and an explicit stars extra`() {
        v101(look = "CLASSIC", style = "ORPHEON_BASIC")
        p.edit().putBoolean("starry_night_enabled", true).commit()
        LookMigration.run(p)
        assertEquals(Look.CLASSIC, look())
        assertTrue(p.getBoolean("starry_night_enabled", false))
    }

    @Test fun `Glassify and Spotifusion are not turned into Starry Night`() {
        v101(look = "IMMERSIVE", theme = "GLASSIFY")
        LookMigration.run(p)
        assertEquals(Look.GLASSIFY, look())
    }

    @Test fun `Starry Night migration runs once`() {
        v101(look = "IMMERSIVE")
        LookMigration.run(p)
        assertEquals(Look.STARRY_NIGHT, look())
        p.edit().putString("look", "IMMERSIVE").commit() // a later user choice must survive
        LookMigration.run(p)
        assertEquals(Look.IMMERSIVE, look())
    }

    @Test fun `already migrated v2 data still gets the Starry Night step`() {
        v101(look = "IMMERSIVE")
        p.edit().putBoolean(LookMigration.FLAG, true).commit() // upgraded straight from 1.0.2
        LookMigration.run(p)
        assertEquals(Look.STARRY_NIGHT, look())
    }

    @Test fun `fresh install gets no Starry Night look`() {
        LookMigration.run(p)
        assertNull(p.getString("look", null))
        assertTrue(p.getBoolean(LookMigration.FLAG_STARRY, false))
    }

    @Test fun `Starry Night look is immersive and parsed from its key`() {
        assertTrue(Look.STARRY_NIGHT.isImmersive())
        assertEquals(Look.STARRY_NIGHT, Look.fromKey("starry_night"))
        assertEquals(Look.STARRY_NIGHT, Look.fromKey("STARRY_NIGHT"))
        assertEquals("Starry Night", Look.STARRY_NIGHT.label)
        assertEquals(PageStyle.ORPHEON_GLASS, PageStyle.forLook(Look.STARRY_NIGHT))
    }

    @Test fun `upgrader on another look who never set the stars switch keeps the stars`() {
        for (l in listOf("CLASSIC", "SPOTIFUSION", "GLASSIFY", "LIQUID_MUSIC")) {
            p.edit().clear().commit()
            v101(look = l)
            LookMigration.run(p)
            assertEquals(Look.fromKey(l), look())
            assertTrue("$l keeps its stars", p.getBoolean("starry_night_enabled", false))
        }
    }

    @Test fun `upgrader with the stars switch explicitly off keeps it off`() {
        v101(look = "CLASSIC")
        p.edit().putBoolean("starry_night_enabled", false).commit()
        LookMigration.run(p)
        assertFalse(p.getBoolean("starry_night_enabled", true))
    }

    @Test fun `stars keep-on step runs once`() {
        v101(look = "CLASSIC")
        LookMigration.run(p)
        p.edit().putBoolean("starry_night_enabled", false).commit() // the user turns them off later
        LookMigration.run(p)
        assertFalse(p.getBoolean("starry_night_enabled", true))
    }

    @Test fun `fresh install keeps the stars extra unset (off by default)`() {
        LookMigration.run(p)
        assertFalse(p.contains("starry_night_enabled"))
    }

    @Test fun `Interactive with the stars switch off does not get the extra turned on`() {
        v101(look = "IMMERSIVE")
        p.edit().putBoolean("starry_night_enabled", false).commit()
        LookMigration.run(p)
        assertFalse(p.getBoolean("starry_night_enabled", true))
    }

    @Test fun `the shooting-stars key alone marks an existing install`() {
        p.edit().putBoolean("starry_night_shooting_stars", true).commit()
        assertTrue(LookMigration.isExistingInstall(p))
        LookMigration.run(p)
        assertTrue(p.getBoolean("starry_night_enabled", false))
    }

    @Test fun `a fresh install is not detected as existing`() {
        assertFalse(LookMigration.isExistingInstall(p))
    }

    @Test fun `upgrader with only look_chosen reads as Classic and gets the stars`() {
        p.edit().putBoolean("look_chosen", true).commit()
        LookMigration.run(p)
        assertEquals(Look.CLASSIC, look())
        assertTrue(p.getBoolean("starry_night_enabled", false))
    }

    @Test fun `v1_0_1 Interactive with the Glassify theme ends on Glassify with the stars on`() {
        v101(look = "IMMERSIVE", theme = "GLASSIFY")
        LookMigration.run(p)
        assertEquals(Look.GLASSIFY, look())
        assertTrue(p.getBoolean("starry_night_enabled", false))
    }

    @Test fun `a stored Starry Night look gets no extra`() {
        p.edit().putBoolean("look_chosen", true).putString("look", "STARRY_NIGHT").commit()
        LookMigration.run(p)
        assertEquals(Look.STARRY_NIGHT, look())
        assertFalse(p.contains("starry_night_enabled"))
    }
}
