/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import app.myspoti.automix.CustomTransition
import app.myspoti.automix.EnvelopePoint
import app.myspoti.automix.TransitionPreset
import app.myspoti.automix.segue.SegueDeck
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomTransitionStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        CustomTransitionStore.clearForTest()
        CustomTransitionStore.replaceStorageFileForTest(tempFolder.newFile("custom_transitions.json").apply { delete() })
    }

    @After
    fun tearDown() {
        CustomTransitionStore.clearForTest()
    }

    @Test
    fun saveAndRetrieveGlobalAndContextOverride() {
        val globalTransition = CustomTransition(
            fromTrackId = "trackA",
            toTrackId = "trackB",
            exitA = 140.0,
            entryB = 12.0,
            overlapBars = 8,
            overlapSec = 16.0,
            preset = TransitionPreset.FADE,
            outgoingCurve = listOf(EnvelopePoint(0f, 1f), EnvelopePoint(1f, 0f)),
            incomingCurve = listOf(EnvelopePoint(0f, 0f), EnvelopePoint(1f, 1f)),
            eqSwap = true,
            updatedAtMs = 1000L,
        )
        val playlistTransition = globalTransition.copy(
            exitA = 152.5,
            entryB = 4.0,
            overlapBars = 4,
            overlapSec = 8.0,
            preset = TransitionPreset.RISE,
            eqSwap = false,
            updatedAtMs = 2000L,
        )

        assertFalse(CustomTransitionStore.hasCustom("trackA", "trackB"))
        assertEquals(0, CustomTransitionStore.version.value)

        CustomTransitionStore.save(globalTransition)
        assertEquals(1, CustomTransitionStore.version.value)
        assertTrue(CustomTransitionStore.hasCustom("trackA", "trackB"))
        assertTrue(CustomTransitionStore.hasCustom("trackA", "trackB", "spotify:playlist:other"))
        assertEquals(140.0, CustomTransitionStore.get("trackA", "trackB")!!.exitA, 1e-6)
        assertEquals(140.0, CustomTransitionStore.get("trackA", "trackB", "spotify:playlist:other")!!.exitA, 1e-6)

        CustomTransitionStore.save(playlistTransition, contextUri = "spotify:playlist:mix1")
        assertEquals(2, CustomTransitionStore.version.value)

        val inPlaylist = CustomTransitionStore.get("trackA", "trackB", "spotify:playlist:mix1")
        assertNotNull(inPlaylist)
        assertEquals(152.5, inPlaylist!!.exitA, 1e-6)
        assertEquals(TransitionPreset.RISE, inPlaylist.preset)
        assertFalse(inPlaylist.eqSwap)

        val fallbackOther = CustomTransitionStore.get("trackA", "trackB", "spotify:playlist:other")
        assertNotNull(fallbackOther)
        assertEquals(140.0, fallbackOther!!.exitA, 1e-6)
        assertEquals(TransitionPreset.FADE, fallbackOther.preset)
    }

    @Test
    fun removeClearsTransitionAndBumpsVersion() {
        val tr = CustomTransition(
            fromTrackId = "song1",
            toTrackId = "song2",
            exitA = 100.0,
            entryB = 5.0,
            overlapBars = 8,
            overlapSec = 15.0,
            preset = TransitionPreset.AUTO,
        )
        CustomTransitionStore.save(tr, contextUri = "spotify:playlist:p1")
        assertTrue(CustomTransitionStore.hasCustom("song1", "song2", "spotify:playlist:p1"))
        val vBefore = CustomTransitionStore.version.value

        CustomTransitionStore.remove("song1", "song2", "spotify:playlist:p1")
        assertEquals(vBefore + 1, CustomTransitionStore.version.value)
        assertFalse(CustomTransitionStore.hasCustom("song1", "song2", "spotify:playlist:p1"))
        assertNull(CustomTransitionStore.get("song1", "song2", "spotify:playlist:p1"))
    }

    @Test
    fun diskPersistenceRoundTripPreservesAllFields() {
        val diskFile = tempFolder.newFile("persisted_transitions.json").apply { delete() }
        CustomTransitionStore.replaceStorageFileForTest(diskFile)

        val custom = CustomTransition(
            fromTrackId = "id_alpha",
            toTrackId = "id_beta",
            exitA = 184.25,
            entryB = 16.5,
            overlapBars = 12,
            overlapSec = 24.0,
            preset = TransitionPreset.CUSTOM,
            outgoingCurve = listOf(
                EnvelopePoint(0.0f, 1.0f),
                EnvelopePoint(0.4f, 0.85f),
                EnvelopePoint(1.0f, 0.0f),
            ),
            incomingCurve = listOf(
                EnvelopePoint(0.0f, 0.0f),
                EnvelopePoint(0.25f, 0.7f),
                EnvelopePoint(1.0f, 1.0f),
            ),
            eqSwap = false,
            updatedAtMs = 987654321L,
        )

        CustomTransitionStore.save(custom, contextUri = "spotify:album:alb1")
        assertTrue(diskFile.exists())
        assertTrue(diskFile.length() > 0)

        // Evict in-memory map and reload from diskFile
        CustomTransitionStore.replaceStorageFileForTest(diskFile)
        val loaded = CustomTransitionStore.get("id_alpha", "id_beta", "spotify:album:alb1")
        assertNotNull(loaded)
        loaded!!
        assertEquals("id_alpha", loaded.fromTrackId)
        assertEquals("id_beta", loaded.toTrackId)
        assertEquals(184.25, loaded.exitA, 1e-6)
        assertEquals(16.5, loaded.entryB, 1e-6)
        assertEquals(12, loaded.overlapBars)
        assertEquals(24.0, loaded.overlapSec, 1e-6)
        assertEquals(TransitionPreset.CUSTOM, loaded.preset)
        assertFalse(loaded.eqSwap)
        assertEquals(987654321L, loaded.updatedAtMs)
        assertEquals(3, loaded.outgoingCurve.size)
        assertEquals(0.4f, loaded.outgoingCurve[1].t, 1e-4f)
        assertEquals(0.85f, loaded.outgoingCurve[1].gain, 1e-4f)
        assertEquals(3, loaded.incomingCurve.size)
        assertEquals(0.25f, loaded.incomingCurve[1].t, 1e-4f)
        assertEquals(0.7f, loaded.incomingCurve[1].gain, 1e-4f)
    }

    @Test
    fun toMixPlanProducesValidPlayableSeguePlan() {
        val (outCurve, inCurve) = CustomTransition.defaultCurvesFor(TransitionPreset.RISE)
        val custom = CustomTransition(
            fromTrackId = "track1",
            toTrackId = "track2",
            exitA = 160.0,
            entryB = 8.0,
            overlapBars = 8,
            overlapSec = 16.0,
            preset = TransitionPreset.RISE,
            outgoingCurve = outCurve,
            incomingCurve = inCurve,
            eqSwap = true,
        )
        val plan = CustomTransitionStore.toMixPlan(custom, analysisA = null, analysisB = null)
        assertEquals(160.0, plan.exitA, 1e-3)
        assertEquals(8.0, plan.entryB, 1e-3)
        assertEquals(16.0, plan.length, 1e-3)
        assertNotNull(plan.segue)
        assertTrue(SegueDeck.playable(plan.segue!!))
    }
}
