package com.music.spotui.di

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** The karaoke and the reverb stages of the local player (see KaraokeProcessor, ReverbProcessor). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioEffectsProcessorsTest {
    private val rate = 44_100

    private fun configured(p: AudioProcessor, channels: Int = 2, encoding: Int = C.ENCODING_PCM_16BIT): AudioProcessor {
        p.configure(AudioProcessor.AudioFormat(rate, channels, encoding))
        p.flush()
        return p
    }

    /** Through the processor in buffers of [chunk] frames, then the end of the stream; interleaved samples back. */
    private fun run(p: AudioProcessor, input: ShortArray, channels: Int = 2, chunk: Int = 3000, whileRunning: ((Int) -> Unit)? = null): ShortArray {
        val out = ArrayList<Short>(input.size)
        fun drain() { val o = p.output; o.order(ByteOrder.nativeOrder()); while (o.hasRemaining()) out += o.short }
        var at = 0
        while (at < input.size) {
            whileRunning?.invoke(at / channels)
            val n = minOf(chunk * channels, input.size - at)
            val buf = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            for (i in 0 until n) buf.putShort(input[at + i])
            buf.flip()
            p.queueInput(buf)
            assertFalse("all input consumed", buf.hasRemaining())
            drain()
            at += n
        }
        p.queueEndOfStream()
        drain()
        assertTrue("ended", p.isEnded)
        return out.toShortArray()
    }

    private fun noise(n: Int, seed: Long = 7): ShortArray { val r = java.util.Random(seed); return ShortArray(n) { (r.nextInt(65536) - 32768).toShort() } }

    private fun stereoSine(frames: Int, hzLeft: Double, hzRight: Double, amplitude: Double = 0.5, rightSign: Double = 1.0): ShortArray {
        val s = ShortArray(frames * 2)
        for (i in 0 until frames) {
            s[i * 2] = (amplitude * 32767 * sin(2 * PI * hzLeft * i / rate)).toInt().toShort()
            s[i * 2 + 1] = (rightSign * amplitude * 32767 * sin(2 * PI * hzRight * i / rate)).toInt().toShort()
        }
        return s
    }

    private fun rms(s: ShortArray, fromFrame: Int, toFrame: Int): Double {
        var sum = 0.0
        for (i in fromFrame * 2 until toFrame * 2) sum += s[i].toDouble() * s[i]
        return sqrt(sum / ((toFrame - fromFrame) * 2))
    }

    // ---- karaoke ----

    @Test fun karaokeAtZeroIsAnExactCopy() {
        val input = noise(2 * 20_000)
        val out = run(configured(KaraokeProcessor { 0f }), input)
        assertArrayEquals(input, out)
    }

    @Test fun karaokeAtZeroIsAnExactCopyOfFloats() {
        val p = configured(KaraokeProcessor { 0f }, encoding = C.ENCODING_PCM_FLOAT)
        val input = ByteBuffer.allocateDirect(4 * 2 * 5000).order(ByteOrder.nativeOrder())
        val r = java.util.Random(3)
        repeat(2 * 5000) { input.putFloat(r.nextFloat() * 2 - 1) }
        input.flip()
        val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        p.queueInput(input)
        val o = p.output
        val got = ByteArray(o.remaining()).also { o.get(it) }
        assertArrayEquals(copy, got)
    }

    @Test fun karaokeTakesTheCentreOut() {
        val frames = 6 * rate
        // The same 1 kHz tone in both channels: dead centre, in the band the voice lives in.
        val input = stereoSine(frames, 1000.0, 1000.0)
        val out = run(configured(KaraokeProcessor { 1f }), input)
        assertEquals("as many frames out as in", input.size, out.size)
        val before = rms(input, rate, 5 * rate)
        val after = rms(out, rate, 5 * rate)
        assertTrue("centre is down by 30 dB or more (was $before, now $after)", after < before * 0.03)
    }

    @Test fun karaokeLeavesTheSidesAlone() {
        val frames = 6 * rate
        // Opposite phase in the two channels: nothing centred, everything on the sides.
        val input = stereoSine(frames, 1000.0, 1000.0, rightSign = -1.0)
        val out = run(configured(KaraokeProcessor { 1f }), input)
        assertEquals(input.size, out.size)
        val before = rms(input, rate, 5 * rate)
        val after = rms(out, rate, 5 * rate)
        assertTrue("sides kept (was $before, now $after)", after > before * 0.9)
    }

    @Test fun karaokeKeepsTheBass() {
        val frames = 6 * rate
        val input = stereoSine(frames, 60.0, 60.0)
        val out = run(configured(KaraokeProcessor { 1f }), input)
        val before = rms(input, rate, 5 * rate)
        val after = rms(out, rate, 5 * rate)
        assertTrue("bass below 120 Hz kept (was $before, now $after)", after > before * 0.9)
    }

    @Test fun karaokeIsAlignedWithItsInput() {
        // A click far to the side (left only) must come out where it went in.
        val frames = 4 * rate
        val input = ShortArray(frames * 2)
        val at = 2 * rate + 123
        input[at * 2] = 20_000
        val out = run(configured(KaraokeProcessor { 1f }), input)
        assertEquals(input.size, out.size)
        var peak = 0
        for (i in 0 until frames) if (kotlin.math.abs(out[i * 2].toInt()) > kotlin.math.abs(out[peak * 2].toInt())) peak = i
        assertTrue("the click is at $peak, not at $at", kotlin.math.abs(peak - at) <= 2)
    }

    @Test fun karaokeBackToZeroHandsTheAudioOverWithNothingLost() {
        val frames = 8 * rate
        val input = noise(frames * 2, seed = 11)
        var dial = 1f
        val p = configured(KaraokeProcessor { dial })
        // On for the first 2 s, then back to 0.
        val out = run(p, input) { frame -> if (frame >= 2 * rate) dial = 0f }
        assertEquals("no frame lost or repeated", input.size, out.size)
        // Well after the ramp the stream is the input again, bit for bit.
        val from = 4 * rate * 2
        assertArrayEquals(input.copyOfRange(from, input.size), out.copyOfRange(from, out.size))
    }

    @Test fun karaokeEngagedMidStreamKeepsTheLengthAndTheAlignment() {
        val frames = 6 * rate
        val input = ShortArray(frames * 2)
        // Noise for the first 2 s (dial at 0: copied as it came), then silence with one click far to the side.
        System.arraycopy(noise(2 * rate * 2, seed = 21), 0, input, 0, 2 * rate * 2)
        val at = 4 * rate + 321
        input[at * 2] = 20_000
        var dial = 0f
        // The dial goes 0 -> 1 from the first buffer at or after 2 s.
        val out = run(configured(KaraokeProcessor { dial }), input) { frame -> if (frame >= 2 * rate) dial = 1f }
        assertEquals("no frame lost or repeated", input.size, out.size)
        val untouched = 2 * rate * 2
        assertArrayEquals("exact copy until the dial moved", input.copyOfRange(0, untouched), out.copyOfRange(0, untouched))
        var peak = 3 * rate
        for (i in 3 * rate until frames) if (kotlin.math.abs(out[i * 2].toInt()) > kotlin.math.abs(out[peak * 2].toInt())) peak = i
        assertTrue("the click is at $peak, not at $at", kotlin.math.abs(peak - at) <= 2)
    }

    @Test fun karaokeIsInactiveForMono() {
        val p = KaraokeProcessor { 1f }
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, p.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT)))
    }

    // ---- reverb ----

    @Test fun reverbAtZeroIsAnExactCopy() {
        val input = noise(2 * 20_000, seed = 5)
        assertArrayEquals(input, run(configured(ReverbProcessor { 0f }), input))
        val mono = noise(20_000, seed = 6)
        assertArrayEquals(mono, run(configured(ReverbProcessor { 0f }, channels = 1), mono, channels = 1))
    }

    @Test fun reverbAddsATailAfterTheSoundEnds() {
        val frames = 3 * rate
        val input = ShortArray(frames * 2)
        // A burst of noise at the start, then silence.
        val burst = noise(2 * rate / 5, seed = 9)
        System.arraycopy(burst, 0, input, 0, burst.size)
        val out = run(configured(ReverbProcessor { 0.6f }), input)
        assertEquals(input.size, out.size)
        assertTrue("silence in is not silence out", rms(out, rate, 2 * rate) > 50.0)
        // The dry part stays under the tail.
        assertTrue(rms(out, 0, rate / 5) > rms(input, 0, rate / 5) * 0.9)
    }

    @Test fun reverbTailDiesAway() {
        val frames = 12 * rate
        val input = ShortArray(frames * 2)
        System.arraycopy(noise(rate, seed = 2), 0, input, 0, rate)
        val out = run(configured(ReverbProcessor { 0.3f }), input)
        assertTrue(rms(out, 10 * rate, 12 * rate) < rms(out, rate, 2 * rate) * 0.1)
    }

    @Test fun reverbBackToZeroIsBitExactOnceTheTailHasFaded() {
        val frames = 8 * rate
        val input = noise(frames * 2, seed = 4)
        var dial = 0.7f
        val out = run(configured(ReverbProcessor { dial }), input) { frame -> if (frame >= rate) dial = 0f }
        assertEquals(input.size, out.size)
        val from = 5 * rate * 2
        assertArrayEquals(input.copyOfRange(from, input.size), out.copyOfRange(from, out.size))
    }

    @Test fun reverbKeepsTheLengthAtFullScale() {
        val input = ShortArray(2 * rate * 2) { if ((it / 2) % 100 < 50) 32767 else -32768 }
        val out = run(configured(ReverbProcessor { 1f }), input)
        assertEquals(input.size, out.size)
    }
}
