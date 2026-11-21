package com.music.spotui.car

import org.junit.Assert.assertEquals
import org.junit.Test

class CarLayoutTest {
    @Test fun wideScreenWithRoomIsWide() {
        assertEquals(CarLayoutMode.WIDE, carLayoutMode(1100f, 450f))
        assertEquals(CarLayoutMode.WIDE, carLayoutMode(600f, 400f))
    }

    @Test fun nearSquareAreaStacks() {
        assertEquals(CarLayoutMode.STACKED, carLayoutMode(700f, 600f))
        assertEquals(CarLayoutMode.STACKED, carLayoutMode(500f, 500f))
    }

    @Test fun splitScreenColumnStacks() {
        assertEquals(CarLayoutMode.STACKED, carLayoutMode(380f, 640f))
        assertEquals(CarLayoutMode.STACKED, carLayoutMode(240f, 320f))
    }

    @Test fun smallAreaIsCompact() {
        assertEquals(CarLayoutMode.COMPACT, carLayoutMode(300f, 150f))
        // Wide but too short for a lyric list.
        assertEquals(CarLayoutMode.COMPACT, carLayoutMode(900f, 190f))
        // Narrow and short.
        assertEquals(CarLayoutMode.COMPACT, carLayoutMode(500f, 250f))
        assertEquals(CarLayoutMode.COMPACT, carLayoutMode(200f, 800f))
    }

    @Test fun unknownAreaIsCompact() {
        assertEquals(CarLayoutMode.COMPACT, carLayoutMode(0f, 0f))
        assertEquals(CarLayoutMode.COMPACT, carLayoutMode(-1f, 300f))
    }

    @Test fun insetsLeaveTheVisibleRectangle() {
        assertEquals(CarInsetsPx(100, 20, 0, 80), carInsets(1000, 500, 100, 20, 1000, 420))
    }

    @Test fun emptyVisibleRectangleMeansTheWholeSurface() {
        assertEquals(CarInsetsPx.None, carInsets(1000, 500, 0, 0, 0, 0))
        assertEquals(CarInsetsPx.None, carInsets(1000, 500, 600, 0, 200, 500))
    }

    @Test fun insetsAreClampedToTheSurface() {
        assertEquals(CarInsetsPx(0, 0, 0, 0), carInsets(1000, 500, -50, -10, 1200, 600))
    }

    @Test fun aspectExactlyAtThresholdIsWide() {
        // 750 / 600 is exactly 1.25.
        assertEquals(CarLayoutMode.WIDE, carLayoutMode(750f, 600f))
        assertEquals(CarLayoutMode.STACKED, carLayoutMode(749f, 600f))
    }

    @Test fun widthJustUnderWideMinimumIsNotWide() {
        assertEquals(CarLayoutMode.STACKED, carLayoutMode(599.9f, 400f))
        assertEquals(CarLayoutMode.WIDE, carLayoutMode(600f, 400f))
    }

    private val stableArea = CarAreaPx(0, 0, 900, 500)
    private val visibleArea = CarAreaPx(100, 0, 1000, 450)

    @Test fun contentUsesTheStableAreaWhenThereIsOne() {
        assertEquals(CarInsetsPx(0, 0, 100, 0), carContentInsets(1000, 500, stableArea, visibleArea))
    }

    @Test fun contentFallsBackToTheVisibleAreaThenTheWholeSurface() {
        assertEquals(CarInsetsPx(100, 0, 0, 50), carContentInsets(1000, 500, null, visibleArea))
        assertEquals(CarInsetsPx(100, 0, 0, 50), carContentInsets(1000, 500, CarAreaPx(0, 0, 0, 0), visibleArea))
        assertEquals(CarInsetsPx.None, carContentInsets(1000, 500, null, null))
        assertEquals(CarInsetsPx.None, carContentInsets(1000, 500, CarAreaPx(0, 0, 0, 0), CarAreaPx(5, 5, 5, 5)))
    }

    @Test fun activeLineIsTheLastOneStarted() {
        val times = listOf(1000L, 5000L, 9000L)
        assertEquals(-1, activeLyricLine(times, 500))
        assertEquals(0, activeLyricLine(times, 1000))
        assertEquals(1, activeLyricLine(times, 8999))
        assertEquals(2, activeLyricLine(times, 60_000))
    }

    @Test fun unsyncedLyricsHaveNoActiveLine() {
        assertEquals(-1, activeLyricLine(listOf(0L, 0L, 0L), 10_000))
        assertEquals(-1, activeLyricLine(emptyList(), 10_000))
    }
}
