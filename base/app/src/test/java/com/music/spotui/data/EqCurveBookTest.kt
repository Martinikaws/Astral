package com.music.spotui.data

import com.music.spotui.data.preferences.EqCodec
import com.music.spotui.data.preferences.EqCurve
import com.music.spotui.data.preferences.EqCurveBook
import com.music.spotui.data.preferences.EqPreset
import com.music.spotui.data.preferences.EqUserPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EqCurveBookTest {
    private val legacy = EqCurve(enabled = true, preset = EqPreset.ROCK)

    @Test fun anOutputWithNoCurveOfItsOwnFollowsTheLegacyOne() {
        val book = EqCurveBook(legacy)
        assertEquals(legacy, book.curveFor("speaker"))
        assertEquals(legacy, book.curveFor("bt:Buds"))
        assertFalse(book.hasOwn("bt:Buds"))
    }

    @Test fun editingOneOutputLeavesTheOthersAlone() {
        val book = EqCurveBook(legacy).edit("bt:Buds") { it.withGain(0, 9f) }
        assertEquals(EqPreset.CUSTOM, book.curveFor("bt:Buds").preset)
        assertEquals(9f, book.curveFor("bt:Buds").gains[0], 0f)
        // The custom curve starts from where the preset was: the other bands are Rock's.
        assertEquals(EqPreset.ROCK.gains[3], book.curveFor("bt:Buds").gains[3], 0f)
        assertEquals(legacy, book.curveFor("speaker"))
        assertEquals(legacy, book.default)
    }

    @Test fun anEditThatChangesNothingKeepsTheBook() {
        val book = EqCurveBook(legacy)
        assertSame(book, book.edit("speaker") { it })
    }

    @Test fun forgettingAnOutputPutsItBackOnTheLegacyCurve() {
        val book = EqCurveBook(legacy).edit("wired") { it.copy(enabled = false) }.forget("wired")
        assertEquals(legacy, book.curveFor("wired"))
    }

    @Test fun gainsAreKeptWithinTheRange() {
        val c = EqCurve().withGain(2, 40f).withPreamp(-40f)
        assertEquals(12f, c.gains[2], 0f)
        assertEquals(-12f, c.preamp, 0f)
        assertEquals(c, c.withGain(99, 5f))
    }

    @Test fun resetKeepsItOnOrOff() {
        val c = EqCurve(enabled = true).withGain(1, 4f).reset()
        assertTrue(c.enabled)
        assertTrue(c.isFlat)
    }

    @Test fun aPresetSoundsLikeItself() {
        val c = EqCurve(enabled = true, preset = EqPreset.JAZZ)
        assertTrue(c.matches(EqPreset.JAZZ.gains.toList()))
        assertFalse(c.matches(EqPreset.POP.gains.toList()))
    }

    @Test fun overridesSurviveTheirStorageFormat() {
        val book = EqCurveBook(legacy)
            .edit("bt:Ada & Bo's\tbuds") { it.withGain(4, -3.5f).copy(enabled = true) }
            .edit("speaker") { it.withPreset(EqPreset.VOCAL).copy(enabled = true) }
        val back = EqCodec.decodeOverrides(EqCodec.encodeOverrides(book.overrides))
        assertEquals(book.overrides, back)
    }

    @Test fun storedLinesThatDoNotReadAreSkipped() {
        val text = "speaker\t1\tFLAT\t0.0\t" + List(10) { "0.0" }.joinToString(",") + "\n" +
            "broken\n" +
            "usb\t1\tNOT_A_PRESET\t0.0\t" + List(10) { "0.0" }.joinToString(",") + "\n" +
            "wired\t1\tCUSTOM\t0.0\t1,2,3\n" +
            "type:3\t1\tCUSTOM\tabc\t" + List(10) { "0" }.joinToString(",")
        assertEquals(setOf("speaker"), EqCodec.decodeOverrides(text).keys)
        assertEquals(emptyMap<String, EqCurve>(), EqCodec.decodeOverrides(null))
    }

    @Test fun namesAndPresetsSurviveTheirStorageFormat() {
        val names = mapOf("bt:Buds, 2" to "Buds, 2", "usb:DAC" to "DAC")
        assertEquals(names, EqCodec.decodeNames(EqCodec.encodeNames(names)))
        val presets = listOf(EqUserPreset("a-1", "Late night\tmix", List(10) { it - 5f }, -2.5f))
        assertEquals(presets, EqCodec.decodePresets(EqCodec.encodePresets(presets)))
    }

    @Test fun customCurveFromAPresetIsClampedAndPadded() {
        val c = EqCurve().withCurve(listOf(30f, -30f), 20f)
        assertEquals(10, c.customGains.size)
        assertEquals(12f, c.customGains[0], 0f)
        assertEquals(-12f, c.customGains[1], 0f)
        assertEquals(0f, c.customGains[9], 0f)
        assertEquals(12f, c.preamp, 0f)
        assertNotEquals(EqPreset.FLAT, c.preset)
    }
}
