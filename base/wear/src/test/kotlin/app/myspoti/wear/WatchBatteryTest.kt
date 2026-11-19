/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class WatchBatteryTest {
    @Test
    fun visualizerUsesReducedGridOctavesAndFramePacing() {
        assertEquals(24, GRID_SIZE)
        assertEquals(576, DOT_COUNT)
        assertEquals(2, OCTAVES)
        assertEquals(33_333_333L, frameIntervalNanos(powerSaveMode = false))
        assertEquals(66_666_666L, frameIntervalNanos(powerSaveMode = true))
    }

    @Test
    fun perlinFbmAndFieldSampleProduceFiniteDeterministicValues() {
        val sampleA = fieldSample(seed = 42, u = 0.25f, v = 0.75f, noiseOffset = 1.25f)
        val sampleARepeat = fieldSample(seed = 42, u = 0.25f, v = 0.75f, noiseOffset = 1.25f)
        val sampleB = fieldSample(seed = 42, u = 0.65f, v = 0.15f, noiseOffset = 1.25f)
        val single = perlin2(seed = 42, x = 1.5f, y = 2.5f, z = 0.75f)
        val fbm = fbm2(seed = 42, x = 1.5f, y = 2.5f, z = 0.75f)

        assertTrue(single.isFinite() && abs(single) <= 1.5f)
        assertTrue(fbm.isFinite() && abs(fbm) <= 1.5f)
        assertTrue(sampleA.isFinite())
        assertEquals(sampleA, sampleARepeat, 0f)
        assertNotEquals(sampleA, sampleB)
    }

    @Test
    fun hoistedSpherePointsMatchUnhoistedFieldSamplesAndStayFinite() {
        val seed = 1337
        val noiseOffset = 2.4f
        val amplitude = 0.45f
        val centerX = 120f
        val centerY = 120f
        val halfSize = 110f
        val points = FloatArray(DOT_COUNT * 2)

        val written = fillSpherePoints(
            seed = seed,
            noiseOffset = noiseOffset,
            amplitude = amplitude,
            centerX = centerX,
            centerY = centerY,
            halfSize = halfSize,
            outPoints = points,
        )
        assertEquals(DOT_COUNT * 2, written)

        val uSphereRadius = 0.675f + 0.225f * amplitude
        val uFeather = (amplitude + 3f) * (amplitude + 3f) * (45f / 1568f)
        val featherRadius = uSphereRadius - uFeather

        var idx = 0
        for (gy in 0 until GRID_SIZE) {
            val v = (gy + 0.5f) / GRID_SIZE
            for (gx in 0 until GRID_SIZE) {
                val u = (gx + 0.5f) / GRID_SIZE
                val noise = fieldSample(seed, u, v, noiseOffset)
                var dx = u * 0.9f - 0.45f + noise
                var dy = v * 0.9f - 0.45f + noise
                val dz = (noise + 0.5f * (0.32f * 0.9f)) * amplitude * 0.4f
                val rawDist = sqrt(dx * dx + dy * dy + dz * dz)
                if (rawDist > 1e-5f) {
                    val inv = 1f / rawDist
                    dx *= inv
                    dy *= inv
                    val clamped = min(uSphereRadius, rawDist)
                    dx *= clamped
                    dy *= clamped
                    val featherStrength = 1f - ((clamped - featherRadius) / uFeather).coerceIn(0f, 1f)
                    val scale = featherStrength * (uSphereRadius / clamped.coerceAtLeast(1e-5f) - 1f) + 1f
                    dx *= scale
                    dy *= scale
                }
                val expectedX = centerX + halfSize * dx
                val expectedY = centerY - halfSize * dy
                val actualX = points[idx++]
                val actualY = points[idx++]
                assertTrue(actualX.isFinite() && actualY.isFinite())
                assertEquals(expectedX, actualX, 1e-4f)
                assertEquals(expectedY, actualY, 1e-4f)
            }
        }
    }

    @Test
    fun pageLruCacheCapsAt16EntriesAndPromotesRecentAccess() {
        assertEquals(16, MAX_CACHED_PAGES)
        val cache = PageLruCache(maxEntries = MAX_CACHED_PAGES, freshMs = BROWSE_FRESH_MS)

        for (i in 0 until 16) {
            cache.put("p$i", "{\"id\":$i}", nowMs = 1_000L + i)
        }
        assertEquals(16, cache.size())

        // Touch p0 so p1 becomes the least recently used entry.
        assertEquals("{\"id\":0}", cache.get("p0"))
        cache.put("p16", "{\"id\":16}", nowMs = 2_000L)

        assertEquals(16, cache.size())
        assertTrue(cache.contains("p0"))
        assertFalse(cache.contains("p1"))
        assertTrue(cache.contains("p16"))

        for (i in 17 until 20) {
            cache.put("p$i", "{\"id\":$i}", nowMs = 2_000L + i)
        }
        assertEquals(16, cache.size())
        assertFalse(cache.contains("p2"))
        assertFalse(cache.contains("p3"))
        assertFalse(cache.contains("p4"))
        assertTrue(cache.contains("p19"))
    }

    @Test
    fun pageLruCacheTracks45SecondFreshnessWindow() {
        assertEquals(45_000L, BROWSE_FRESH_MS)
        val cache = PageLruCache(maxEntries = MAX_CACHED_PAGES, freshMs = BROWSE_FRESH_MS)

        // Disk-loaded entries have no session fetch timestamp and must not suppress initial refresh.
        cache.put("disk", "{\"items\":[]}", nowMs = null)
        assertFalse(cache.isFresh("disk", 10_000L))

        cache.put("playlists", "{\"items\":[1]}", nowMs = 10_000L)
        assertTrue(cache.isFresh("playlists", 10_000L))
        assertTrue(cache.isFresh("playlists", 54_999L))
        assertFalse(cache.isFresh("playlists", 55_000L))
        assertFalse(cache.isFresh("playlists", 80_000L))
    }

    @Test
    fun remoteStateHasNoByteArraysAndVizSnapshotUsesContentEquality() {
        val hasByteArrayField = RemoteState::class.java.declaredFields.any { it.type == ByteArray::class.java }
        assertFalse(hasByteArrayField)

        val a = VizSnapshot(key = "track:1", seed = 7, stepMs = 100, samples = byteArrayOf(1, 2, 3))
        val b = VizSnapshot(key = "track:1", seed = 7, stepMs = 100, samples = byteArrayOf(1, 2, 3))
        val c = VizSnapshot(key = "track:1", seed = 7, stepMs = 100, samples = byteArrayOf(1, 2, 4))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
