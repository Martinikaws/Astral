package com.music.spotui.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The size-class thresholds that pick Immersive's chrome (sidebar, one row, stacked bars). */
class WindowSizeTest {
    @Test fun portraitPhoneKeepsBottomBars() {
        val w = WindowSize(400, 900)
        assertFalse(w.large)
        assertFalse(w.sideNavigation)
        assertFalse(w.chromeInOneRow)
    }

    @Test fun justUnder600WideIsNotLarge() {
        val w = WindowSize(599, 900)
        assertFalse(w.large)
        assertFalse(w.sideNavigation)
    }

    @Test fun under480TallIsNotLarge() {
        val w = WindowSize(600, 479)
        assertFalse(w.large)
        assertFalse(w.sideNavigation)
        assertTrue(w.shortAndWide)
    }

    @Test fun exactly600x480IsLarge() {
        val w = WindowSize(600, 480)
        assertTrue(w.large)
        assertTrue(w.sideNavigation)
        assertFalse(w.chromeInOneRow)
    }

    @Test fun phoneOnItsSideKeepsOneRowBar() {
        val w = WindowSize(850, 400)
        assertFalse(w.sideNavigation)
        assertTrue(w.chromeInOneRow)
    }

    @Test fun landscapeTabletUsesSidebar() {
        val w = WindowSize(1280, 800)
        assertTrue(w.sideNavigation)
        assertTrue(w.expanded)
        assertTrue(w.twoPane)
    }

    @Test fun sideNavigationFollowsLarge() {
        for ((w, h) in listOf(360 to 780, 599 to 900, 600 to 479, 600 to 480, 840 to 400, 1280 to 800)) {
            val s = WindowSize(w, h)
            assertEquals("$w x $h", s.large, s.sideNavigation)
        }
    }

    @Test fun phoneColumnsUnchanged() {
        assertEquals(2, WindowSize(360, 780).columns(2))
    }
}
