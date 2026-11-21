/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, by Lelonio,
 * app/src/main/java/dev/lelonio/square/playback/CentreExtractor.kt at 139441f (the idea and the
 * comments on it are Square's; see also AudioOutput.kt and AudioEffects.kt there).
 * Modified for Astral, 2026-11.
 *
 * Changes: the DUET-style time-frequency mask (4096-sample Hann frames at 75 % overlap, a soft
 * mask from the level and phase difference of the two channels, bass and air left alone) and its
 * radix-2 FFT are Square's; the frame engine now works on float samples and is a streaming queue
 * (write, then read what is finished) instead of rewriting a 16-bit buffer in place, it starts on
 * a few frames of silence and drops them again so the output is aligned with the input (no
 * 93 ms offset), the amount glides between frames, and it is wrapped as a media3 audio processor
 * in the local player's chain (16-bit and float stereo) that copies its input bit for bit while
 * the karaoke dial is at 0 and hands the held-back audio over unprocessed when it returns to 0.
 */
package com.music.spotui.di

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Takes the singing out of a finished stereo mix, one frequency at a time.
 *
 * In a two-channel recording each source has its own place on the stage, which shows up as a
 * difference in level and in phase between the channels. Cut the sound into short overlapping
 * frames and look at every frequency of every frame separately: each cell belongs almost entirely
 * to whichever source is loudest there. A lead voice sits dead centre (equal in both channels, no
 * delay), so the cells that look centred are turned down and everything to the sides is left
 * alone. Soft mask, not a binary one: it is a dial, not a switch.
 *
 * References (as in Square): Rickard, "The DUET blind source separation algorithm" (2007);
 * Yilmaz and Rickard, "Blind separation of speech mixtures via time-frequency masking" (2004).
 *
 * Not stem separation: nothing computed from a finished mix comes close to a recording that never
 * had the voice in it. A voice with reverb, a double-tracked or a wide-stereo voice is only
 * partly removed, and instruments panned dead centre (bass drum above 120 Hz, snare) are dimmed too.
 */
internal class CentreExtractor {
    private var rate = 0
    private var input = FloatArray(0)
    private var overlap = FloatArray(0)
    private var done = FloatArray(0)
    private var primed = 0
    private var pending = 0
    private var ready = 0
    /** Prime samples (interleaved) still to leave the finished queue. */
    private var skip = 0

    private val re = FloatArray(WINDOW)
    private val im = FloatArray(WINDOW)
    private val leftRe = FloatArray(WINDOW)
    private val leftIm = FloatArray(WINDOW)
    private val rightRe = FloatArray(WINDOW)
    private val rightIm = FloatArray(WINDOW)

    /** Hann; at three-quarters overlap it sums to a constant, so frames add back without breathing. */
    private val window = FloatArray(WINDOW) { i -> (0.5f - 0.5f * cos(2.0 * PI * i / WINDOW).toFloat()) }

    /** Frames of real input held and not yet given back (the pipeline's depth). */
    val heldFrames: Int get() = (pending - skip) / 2 + (ready) / 2

    /** Finished frames waiting to be read. */
    val readyFrames: Int get() = ready / 2

    /** Sets the sample rate and allocates the buffers (once per stream format, off the audio path); [reset] then only clears them. */
    fun configure(sampleRate: Int) {
        rate = sampleRate
        primed = (WINDOW - HOP) * 2
        input = FloatArray(primed + WINDOW * 8)
        overlap = FloatArray(input.size)
        done = FloatArray(WINDOW * 8)
        reset()
    }

    /** Starts over: a few frames of silence first, so the first real frame is in full overlap. Allocates nothing. */
    fun reset() {
        input.fill(0f)
        overlap.fill(0f)
        done.fill(0f)
        pending = primed
        ready = 0
        skip = primed
    }

    /** Takes [frames] interleaved stereo frames of [pcm] in and works through every whole frame now available. */
    fun write(pcm: FloatArray, frames: Int, amount: Float) {
        if (frames <= 0) return
        val samples = frames * 2
        ensure(samples)
        System.arraycopy(pcm, 0, input, pending, samples)
        pending += samples

        var consumed = 0
        while (pending - consumed >= WINDOW * 2) {
            transformFrame(consumed, amount)
            consumed += HOP * 2
        }
        if (consumed > 0) {
            // What the overlap has finished with moves to the queue waiting to be read.
            System.arraycopy(overlap, 0, done, ready, consumed)
            ready += consumed
            System.arraycopy(overlap, consumed, overlap, 0, overlap.size - consumed)
            java.util.Arrays.fill(overlap, overlap.size - consumed, overlap.size, 0f)
            System.arraycopy(input, consumed, input, 0, pending - consumed)
            pending -= consumed
            if (skip > 0) {
                val drop = min(skip, ready)
                System.arraycopy(done, drop, done, 0, ready - drop)
                ready -= drop
                skip -= drop
            }
        }
    }

    /** Copies the finished frames to [dst] (interleaved) and empties the queue; returns the number of frames. */
    fun readAll(dst: FloatArray, offsetSamples: Int = 0): Int {
        val n = ready
        System.arraycopy(done, 0, dst, offsetSamples, n)
        ready = 0
        return n / 2
    }

    /** The real input not yet finished, as it came (to hand over untouched); copied to [dst]. */
    fun readRaw(dst: FloatArray, offsetSamples: Int): Int {
        val from = skip
        val n = pending - from
        if (n > 0) System.arraycopy(input, from, dst, offsetSamples, n)
        return maxOf(n, 0) / 2
    }

    private fun ensure(extra: Int) {
        val wanted = pending + extra + WINDOW * 4
        if (input.size < wanted) input = input.copyOf(wanted)
        if (overlap.size < input.size) overlap = overlap.copyOf(input.size)
        if (done.size < ready + input.size) done = done.copyOf(ready + input.size)
    }

    /** One frame: window it, look at every bin, put it back. */
    private fun transformFrame(at: Int, amount: Float) {
        for (i in 0 until WINDOW) {
            val w = window[i]
            leftRe[i] = input[at + i * 2] * w
            leftIm[i] = 0f
            rightRe[i] = input[at + i * 2 + 1] * w
            rightIm[i] = 0f
        }
        fft(leftRe, leftIm, false)
        fft(rightRe, rightIm, false)

        val lowBin = (KEEP_BELOW_HZ * WINDOW / rate).toInt()
        val highBin = (KEEP_ABOVE_HZ * WINDOW / rate).toInt()

        for (bin in 0 until BINS) {
            val lr = leftRe[bin]
            val li = leftIm[bin]
            val rr = rightRe[bin]
            val ri = rightIm[bin]
            val gain = if (bin < lowBin || bin > highBin) 1f else centreGain(lr, li, rr, ri, amount)
            leftRe[bin] = lr * gain
            leftIm[bin] = li * gain
            rightRe[bin] = rr * gain
            rightIm[bin] = ri * gain
            // The upper half of the spectrum is the mirror of the lower one.
            if (bin in 1 until BINS - 1) {
                val mirror = WINDOW - bin
                leftRe[mirror] = leftRe[bin]
                leftIm[mirror] = -leftIm[bin]
                rightRe[mirror] = rightRe[bin]
                rightIm[mirror] = -rightIm[bin]
            }
        }

        fft(leftRe, leftIm, true)
        fft(rightRe, rightIm, true)

        for (i in 0 until WINDOW) {
            val w = window[i] * NORMALISE
            overlap[at + i * 2] += leftRe[i] * w
            overlap[at + i * 2 + 1] += rightRe[i] * w
        }
    }

    /**
     * How much of this cell to keep, from where it sits on the stage: how far apart the two
     * channels are in level and in phase (both DUET's). A cell in the middle scores near zero on
     * both; the further from the middle, the less of the dial applies to it.
     */
    private fun centreGain(lr: Float, li: Float, rr: Float, ri: Float, amount: Float): Float {
        val leftSize = hypot(lr, li)
        val rightSize = hypot(rr, ri)
        if (leftSize < TINY && rightSize < TINY) return 1f
        val level = abs(leftSize - rightSize) / (leftSize + rightSize)
        val crossReal = lr * rr + li * ri
        val crossImaginary = li * rr - lr * ri
        val phase = abs(atan2(crossImaginary, crossReal))
        val centred = exp(-(level * level) / (LEVEL_WIDTH * LEVEL_WIDTH) - (phase * phase) / (PHASE_WIDTH * PHASE_WIDTH))
        return 1f - amount * centred
    }

    /** Radix-2, in place, iterative (written out rather than pulled in: nothing else here needs a transform). */
    private fun fft(r: FloatArray, m: FloatArray, inverse: Boolean) {
        var j = 0
        for (i in 1 until WINDOW) {
            var bit = WINDOW shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                var t = r[i]; r[i] = r[j]; r[j] = t
                t = m[i]; m[i] = m[j]; m[j] = t
            }
        }
        var length = 2
        while (length <= WINDOW) {
            val half = length / 2
            val twRe = TWIDDLE_COS[Integer.numberOfTrailingZeros(length)]
            val twIm = TWIDDLE_SIN[Integer.numberOfTrailingZeros(length)]
            val sign = if (inverse) 1f else -1f
            var i = 0
            while (i < WINDOW) {
                for (k in 0 until half) {
                    val wr = twRe[k]
                    val wi = sign * twIm[k]
                    val ar = r[i + k]
                    val ai = m[i + k]
                    val br = r[i + k + half] * wr - m[i + k + half] * wi
                    val bi = r[i + k + half] * wi + m[i + k + half] * wr
                    r[i + k] = ar + br
                    m[i + k] = ai + bi
                    r[i + k + half] = ar - br
                    m[i + k + half] = ai - bi
                }
                i += length
            }
            length = length shl 1
        }
        if (inverse) {
            val s = 1f / WINDOW
            for (i in 0 until WINDOW) { r[i] *= s; m[i] *= s }
        }
    }

    companion object {
        /** About 93 ms at 44.1 kHz: long enough to tell a voice from a snare. */
        const val WINDOW = 4096
        const val HOP = WINDOW / 4
        const val BINS = WINDOW / 2 + 1

        /**
         * How near the middle a cell has to be before it counts as the voice, in DUET's two numbers
         * (the level difference and the phase difference allowed). Generous, and the mask falls away
         * smoothly: a voice is never mathematically centred once a room has been added to it.
         */
        const val LEVEL_WIDTH = 0.28f
        const val PHASE_WIDTH = 0.55f

        /** Below this the record keeps its bass and its kick, centred or not. */
        const val KEEP_BELOW_HZ = 120f
        /** And above this the air, where a voice has only its sibilance. */
        const val KEEP_ABOVE_HZ = 12_000f
        const val TINY = 1e-7f
        /** Hann at three-quarters overlap sums to 1.5; this takes that back out. */
        const val NORMALISE = 2f / 3f

        /** cos and sin of 2*pi*k/length for each stage (index log2(length)), k below length/2. */
        private val TWIDDLE_COS = Array(13) { s -> val len = 1 shl s; FloatArray(maxOf(len / 2, 1)) { k -> cos(2.0 * PI * k / len).toFloat() } }
        private val TWIDDLE_SIN = Array(13) { s -> val len = 1 shl s; FloatArray(maxOf(len / 2, 1)) { k -> sin(2.0 * PI * k / len).toFloat() } }
    }
}

/** What the local player's audio stages follow; read on the audio threads (see AudioEffectsPref). */
object AudioFx {
    /** Reverb amount 0..1. */
    @Volatile var reverb: Float = 0f
    /** Karaoke dial 0..1. */
    @Volatile var karaoke: Float = 0f
}

/**
 * Karaoke of the local player (extensions' streams, downloads, the phone's files): the singing
 * turned down by [CentreExtractor], a media3 audio processor in ExoPlayer's chain. Each player has
 * its own (the fading-out copy of a crossfade too), all following [source].
 *
 * At 0 it copies its input as it came, bit for bit (a lossless stream stays lossless). Above 0
 * the sound is held back by about 70 ms inside the processor while frames fill, aligned again on
 * the way out (the song's position is unaffected), and the end of a stream is flushed whole.
 * Stereo 16-bit and float PCM only; anything else passes untouched (the processor is inactive).
 */
@UnstableApi
class KaraokeProcessor(private val source: () -> Float = { AudioFx.karaoke }) : BaseAudioProcessor() {
    private val centre = CentreExtractor()
    private var float = false
    private var sampleRate = 0
    private var engaged = false
    /** The dial as processed: follows the target at a bounded pace, so a jump of the dial is a ramp. */
    private var amount = 0f
    private var scratch = FloatArray(0)
    private var chunk = FloatArray(0)
    private var silence = FloatArray(0)
    /** Grow-only: what the extractor hands back, before it is written out (nothing is allocated per buffer once it has grown). */
    private var data = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount != 2 || inputAudioFormat.sampleRate < 8_000)
            return AudioProcessor.AudioFormat.NOT_SET
        // The format is applied in onFlush: the old stream may still be drained after this call.
        return inputAudioFormat
    }

    override fun onFlush() {
        engaged = false; amount = 0f
        val format = inputAudioFormat
        if (format == AudioProcessor.AudioFormat.NOT_SET) return
        float = format.encoding == C.ENCODING_PCM_FLOAT
        if (format.sampleRate != sampleRate) {
            sampleRate = format.sampleRate
            centre.configure(sampleRate)
        }
        if (silence.size < CentreExtractor.WINDOW * 4) silence = FloatArray(CentreExtractor.WINDOW * 4)
    }

    override fun onReset() { engaged = false; amount = 0f; sampleRate = 0; scratch = FloatArray(0); chunk = FloatArray(0); silence = FloatArray(0); data = FloatArray(0) }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val target = source().coerceIn(0f, 1f)
        if (!engaged && target <= 0f) {
            // The exact copy: nothing heard is changed, nothing computed.
            val out = replaceOutputBuffer(inputBuffer.remaining())
            out.put(inputBuffer)
            out.flip()
            return
        }
        val frames = inputBuffer.remaining() / (if (float) 8 else 4)
        if (frames == 0) return
        if (!engaged) { centre.reset(); engaged = true; amount = 0f }
        if (scratch.size < frames * 2) scratch = FloatArray(frames * 2)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) scratch[i] = if (float) inputBuffer.float else inputBuffer.short * (1f / 32768f)

        // The dial moves at most STEP per hop of frames, so a jump of it is a ramp of about a tenth of a second.
        var at = 0
        while (at < frames) {
            val n = min(CentreExtractor.HOP, frames - at)
            amount += (target - amount).coerceIn(-STEP, STEP)
            if (chunk.size < n * 2) chunk = FloatArray(n * 2)
            System.arraycopy(scratch, at * 2, chunk, 0, n * 2)
            centre.write(chunk, n, amount)
            at += n
        }

        val handBack = target <= 0f && amount <= 0f
        val finished = centre.readyFrames
        val raw = if (handBack) centre.heldFrames - finished else 0
        val total = finished + maxOf(raw, 0)
        if (total == 0) return
        if (data.size < total * 2) data = FloatArray(total * 2)
        centre.readAll(data)
        if (handBack) {
            centre.readRaw(data, finished * 2)
            engaged = false
        }
        emit(data, total)
    }

    override fun onQueueEndOfStream() {
        if (!engaged) return
        val held = centre.heldFrames
        engaged = false
        if (held <= 0) return
        if (silence.size < CentreExtractor.WINDOW * 4) silence = FloatArray(CentreExtractor.WINDOW * 4)
        centre.write(silence, CentreExtractor.WINDOW * 2, amount)
        if (data.size < centre.readyFrames * 2) data = FloatArray(centre.readyFrames * 2)
        val got = centre.readAll(data)
        emit(data, min(got, held))
    }

    private fun emit(data: FloatArray, frames: Int) {
        val out = replaceOutputBuffer(frames * 2 * (if (float) 4 else 2))
        for (i in 0 until frames * 2) {
            if (float) out.putFloat(data[i])
            else out.putShort((data[i] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    private companion object {
        /** The most the dial moves per hop of 1024 frames (full range in about 7 hops, 0.17 s). */
        const val STEP = 0.15f
    }
}
