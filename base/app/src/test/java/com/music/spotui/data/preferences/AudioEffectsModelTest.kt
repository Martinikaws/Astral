package com.music.spotui.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The effect settings and the presets: persisted, restored, never written over before they are read. */
class AudioEffectsModelTest {
    private class MapStorage(val map: MutableMap<String, Any> = HashMap()) : EffectsStorage {
        override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
        override fun getString(key: String) = map[key] as? String
        override fun putFloat(key: String, value: Float) { map[key] = value }
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class Recorder : EffectsSink {
        val speeds = ArrayList<Float>()
        var changes = 0
        override fun speed(value: Float) { speeds += value }
        override fun changed(speed: Float, pitch: Float, reverb: Float, karaoke: Float) { changes++ }
    }

    @Test fun defaultsAreTheOriginalSound() {
        val m = AudioEffectsModel(MapStorage(), 1f)
        assertTrue(m.isOriginal)
        assertEquals(listOf("original", "slowed", "sped_up"), m.presets.value.map { it.id })
    }

    @Test fun valuesAndPresetsSurviveARestart() {
        val storage = MapStorage()
        val first = AudioEffectsModel(storage, 1f)
        first.setPitch(0.9f); first.setReverb(0.4f); first.setKaraoke(0.75f)
        first.setSpeed(0.8f)
        val saved = first.savePreset("Night drive")
        val second = AudioEffectsModel(storage, 0.8f)
        assertEquals(0.9f, second.pitch.value, 0f)
        assertEquals(0.4f, second.reverb.value, 0f)
        assertEquals(0.75f, second.karaoke.value, 0f)
        assertEquals(0.8f, second.speed.value, 0f)
        val back = second.presets.value.single { !it.builtIn }
        assertEquals(saved, back)
        assertEquals("Night drive", back.name)
        assertEquals(0.8f, back.speed, 0f); assertEquals(0.9f, back.pitch, 0f)
        assertEquals(0.4f, back.reverb, 0f); assertEquals(0.75f, back.karaoke, 0f)
    }

    @Test fun deletingASavedPresetPersistsAndBuiltInsStay() {
        val storage = MapStorage()
        val m = AudioEffectsModel(storage, 1f)
        val a = m.savePreset("a"); val b = m.savePreset("b")
        m.deletePreset(a.id)
        m.deletePreset("slowed")
        assertEquals(listOf("original", "slowed", "sped_up", b.id), AudioEffectsModel(storage, 1f).presets.value.map { it.id })
    }

    @Test fun applyingAPresetSetsAllFour() {
        val rec = Recorder()
        val m = AudioEffectsModel(MapStorage(), 1f, rec)
        assertTrue(m.applyPreset("slowed"))
        assertEquals(0.85f, m.speed.value, 0f); assertEquals(0.92f, m.pitch.value, 0f)
        assertEquals(0.55f, m.reverb.value, 0f); assertEquals(0f, m.karaoke.value, 0f)
        assertEquals(listOf(0.85f), rec.speeds)
        assertEquals(1, rec.changes)
        assertFalse(m.applyPreset("nope"))
        assertTrue(m.presets.value.first { it.id == "slowed" }.matches(m.speed.value, m.pitch.value, m.reverb.value, m.karaoke.value))
    }

    @Test fun resetGoesBackToTheOriginalSound() {
        val m = AudioEffectsModel(MapStorage(), 1f)
        m.applyPreset("sped_up"); m.setKaraoke(1f)
        m.reset()
        assertTrue(m.isOriginal)
    }

    @Test fun valuesAreClampedAndSnapped() {
        val m = AudioEffectsModel(MapStorage(), 1f)
        m.setSpeed(9f); assertEquals(2f, m.speed.value, 0f)
        m.setSpeed(0.1f); assertEquals(0.5f, m.speed.value, 0f)
        m.setPitch(0.8499999f); assertEquals(0.85f, m.pitch.value, 0f)
        m.setReverb(-1f); assertEquals(0f, m.reverb.value, 0f)
        m.setKaraoke(Float.NaN); assertEquals(0f, m.karaoke.value, 0f)
    }

    @Test fun anUnchangedValueWritesNothingAndTellsNobody() {
        val rec = Recorder()
        val storage = MapStorage()
        val m = AudioEffectsModel(storage, 1f, rec)
        m.setReverb(0f); m.setSpeed(1f)
        assertEquals(0, rec.changes)
        assertTrue(storage.map.isEmpty())
    }

    @Test fun aBrokenSavedListIsDroppedNotFatal() {
        val storage = MapStorage(hashMapOf("presets" to "{not json"))
        val m = AudioEffectsModel(storage, 1f)
        assertEquals(3, m.presets.value.size)
        assertNotNull(m.savePreset("   "))
        assertEquals("Custom", m.presets.value.last().name)
    }

    @Test fun aMalformedPresetDoesNotDropTheOthers() {
        val good = """{"id":"a","name":"Keep","speed":0.9,"pitch":1.0}"""
        val bad = """{"id":"b","name":"Broken","speed":"fast","pitch":1.0}"""
        val good2 = """{"id":"c","name":"Also","speed":1.1,"pitch":1.0}"""
        val storage = MapStorage(hashMapOf("presets" to "[$good,$bad,$good2]"))
        val m = AudioEffectsModel(storage, 1f)
        assertEquals(listOf("original", "slowed", "sped_up", "a", "c"), m.presets.value.map { it.id })
    }

    @Test fun semitones() {
        assertEquals(12f, AudioEffectsModel.semitones(2f), 1e-4f)
        assertEquals(-12f, AudioEffectsModel.semitones(0.5f), 1e-4f)
        assertEquals(0f, AudioEffectsModel.semitones(1f), 1e-6f)
    }
}
