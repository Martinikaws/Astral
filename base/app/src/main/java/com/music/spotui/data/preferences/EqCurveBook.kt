/*
 * Astral's own code, written for Astral in 2026-11; no Square source is copied or adapted here.
 * Only the idea of a curve for each output (the phone's speaker, a cable, one pair of Bluetooth
 * headphones) comes from Square (https://github.com/Lelonio/Square, playback/Equalizer.kt).
 *
 * How it differs from Square: Square keeps JSON in its own file and starts an output it has not seen flat and off;
 * here the curve Astral already had (the legacy keys of EqualizerPref) is the one every output
 * starts from, so nobody's sound changes on update, and an output is given a curve of its own the
 * first time it is edited. The storage is a small line format of our own, not JSON, so it can be
 * read and written (and tested) without Android. Presets are Astral's (EqPreset) plus the
 * listener's own.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.data.preferences

import com.music.spotui.di.EqBands
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * One output's curve: on or off, and either one of the ready-made [preset]s or the user's own
 * ([EqPreset.CUSTOM], from [customGains] and [customPreamp]). Immutable: an edit makes a new one.
 */
data class EqCurve(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val customGains: List<Float> = FLAT_GAINS,
    val customPreamp: Float = 0f,
) {
    /** The ten band gains heard, in dB: the preset's, or the user's own. */
    val gains: List<Float>
        get() = if (preset == EqPreset.CUSTOM) customGains else preset.gains.toList()

    /** The preamp heard, in dB. */
    val preamp: Float get() = if (preset == EqPreset.CUSTOM) customPreamp else preset.preamp

    /** Nothing to hear: every band and the preamp at zero. */
    val isFlat: Boolean get() = preamp == 0f && gains.all { it == 0f }

    /** A band moved by hand: the curve becomes the user's own, from where it was. */
    fun withGain(band: Int, db: Float): EqCurve {
        if (band !in 0 until EqBands.COUNT) return this
        val g = gains.toMutableList()
        g[band] = db.coerceIn(EqBands.MIN_DB, EqBands.MAX_DB)
        return copy(preset = EqPreset.CUSTOM, customGains = g, customPreamp = preamp)
    }

    fun withPreamp(db: Float): EqCurve =
        copy(preset = EqPreset.CUSTOM, customGains = gains, customPreamp = db.coerceIn(EqBands.PREAMP_MIN_DB, EqBands.PREAMP_MAX_DB))

    /** A ready-made curve; [EqPreset.CUSTOM] keeps the user's own as it was. */
    fun withPreset(value: EqPreset): EqCurve = copy(preset = value)

    /** A whole curve, from a preset of the user's: it becomes their own. */
    fun withCurve(newGains: List<Float>, newPreamp: Float): EqCurve = copy(
        preset = EqPreset.CUSTOM,
        customGains = List(EqBands.COUNT) { newGains.getOrElse(it) { 0f }.coerceIn(EqBands.MIN_DB, EqBands.MAX_DB) },
        customPreamp = newPreamp.coerceIn(EqBands.PREAMP_MIN_DB, EqBands.PREAMP_MAX_DB),
    )

    /** Flat, keeping whether it is on. */
    fun reset(): EqCurve = EqCurve(enabled = enabled)

    /** Whether the bands sound like [other] (the preamp is not compared). */
    fun matches(other: List<Float>): Boolean =
        gains.size == other.size && gains.indices.all { kotlin.math.abs(gains[it] - other[it]) < 0.05f }

    companion object {
        val FLAT_GAINS: List<Float> = List(EqBands.COUNT) { 0f }
    }
}

/** A curve the listener named and kept. */
data class EqUserPreset(val id: String, val name: String, val gains: List<Float>, val preamp: Float)

/**
 * Every output's curve: [default] for the outputs that have none of their own, and [overrides] by
 * [OutputRoute.Output.key][com.music.spotui.di.OutputRoute.Output.key] for the ones that do.
 * Immutable; an edit returns the new book.
 */
class EqCurveBook(val default: EqCurve = EqCurve(), val overrides: Map<String, EqCurve> = emptyMap()) {

    /** The curve of the output [key]: its own, or the one every output starts from. */
    fun curveFor(key: String): EqCurve = overrides[key] ?: default

    /** Whether [key] has been given a curve of its own. */
    fun hasOwn(key: String): Boolean = key in overrides

    /** [change] applied to the curve of [key], which then has its own from here on. */
    fun edit(key: String, change: (EqCurve) -> EqCurve): EqCurveBook {
        val before = curveFor(key)
        val after = change(before)
        if (after == before) return this
        return EqCurveBook(default, overrides + (key to after))
    }

    /** Takes [key]'s own curve away: it follows [default] again. */
    fun forget(key: String): EqCurveBook = if (key in overrides) EqCurveBook(default, overrides - key) else this

    /** Every key that has a curve of its own. */
    val keys: Set<String> get() = overrides.keys
}

/**
 * The line formats the equaliser keeps: one record to a line, fields separated by tabs, text
 * fields percent-encoded. Anything that does not read back is skipped, never thrown.
 */
object EqCodec {
    private const val SEP = '\t'

    fun encodeOverrides(map: Map<String, EqCurve>): String = map.entries.joinToString("\n") { (key, c) ->
        listOf(enc(key), if (c.enabled) "1" else "0", c.preset.name, c.customPreamp.toString(), c.customGains.joinToString(","))
            .joinToString(SEP.toString())
    }

    fun decodeOverrides(text: String?): Map<String, EqCurve> {
        val out = LinkedHashMap<String, EqCurve>()
        for (line in text.orEmpty().lines()) {
            val f = line.split(SEP)
            if (f.size < 5) continue
            val key = dec(f[0]).takeIf { it.isNotEmpty() } ?: continue
            val preset = runCatching { EqPreset.valueOf(f[2]) }.getOrNull() ?: continue
            val preamp = f[3].toFloatOrNull() ?: continue
            val gains = gains(f[4]) ?: continue
            out[key] = EqCurve(enabled = f[1] == "1", preset = preset, customGains = gains, customPreamp = preamp)
        }
        return out
    }

    fun encodeNames(map: Map<String, String>): String =
        map.entries.joinToString("\n") { (k, v) -> enc(k) + SEP + enc(v) }

    fun decodeNames(text: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.orEmpty().lines()) {
            val f = line.split(SEP)
            if (f.size < 2) continue
            val key = dec(f[0]).takeIf { it.isNotEmpty() } ?: continue
            out[key] = dec(f[1])
        }
        return out
    }

    fun encodePresets(list: List<EqUserPreset>): String = list.joinToString("\n") { p ->
        listOf(enc(p.id), enc(p.name), p.preamp.toString(), p.gains.joinToString(",")).joinToString(SEP.toString())
    }

    fun decodePresets(text: String?): List<EqUserPreset> {
        val out = ArrayList<EqUserPreset>()
        for (line in text.orEmpty().lines()) {
            val f = line.split(SEP)
            if (f.size < 4) continue
            val id = dec(f[0]).takeIf { it.isNotEmpty() } ?: continue
            val preamp = f[2].toFloatOrNull() ?: continue
            val gains = gains(f[3]) ?: continue
            out += EqUserPreset(id, dec(f[1]), gains, preamp)
        }
        return out
    }

    /** Ten numbers, or null: a curve saved with another number of bands is not read. */
    private fun gains(text: String): List<Float>? {
        val parts = text.split(',').map { it.toFloatOrNull() ?: return null }
        return parts.takeIf { it.size == EqBands.COUNT }?.map { it.coerceIn(EqBands.MIN_DB, EqBands.MAX_DB) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault("")
}
