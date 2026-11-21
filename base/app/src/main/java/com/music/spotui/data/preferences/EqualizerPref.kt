package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.spotui.di.OutputRoute
import com.music.spotui.di.EqBands
import com.music.spotui.di.EqTuning
import com.music.spotui.di.Equalizer
import com.music.spotui.di.Spatial

/** The equalizer's ready-made curves: ten band gains and the preamp that keeps them under full scale, in dB. */
enum class EqPreset(val gains: FloatArray, val preamp: Float) {
    FLAT(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0f),
    BASS_BOOST(floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), -5f),
    BASS_CUT(floatArrayOf(-6f, -5f, -4f, -2f, 0f, 0f, 0f, 0f, 0f, 0f), 0f),
    TREBLE_BOOST(floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f), -5f),
    TREBLE_CUT(floatArrayOf(0f, 0f, 0f, 0f, 0f, -1f, -2f, -4f, -5f, -6f), 0f),
    VOCAL(floatArrayOf(-2f, -2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f), -3f),
    ROCK(floatArrayOf(5f, 4f, 2f, -1f, -2f, -1f, 1f, 3f, 4f, 5f), -4f),
    POP(floatArrayOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f), -3f),
    JAZZ(floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f), -2f),
    CLASSICAL(floatArrayOf(3f, 2f, 1f, 0f, 0f, 0f, -1f, -1f, 1f, 2f), -2f),
    ELECTRONIC(floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f), -4f),
    HIP_HOP(floatArrayOf(5f, 4f, 2f, 3f, -1f, -1f, 1f, -1f, 2f, 3f), -4f),
    ACOUSTIC(floatArrayOf(4f, 4f, 3f, 1f, 2f, 1f, 3f, 3f, 3f, 2f), -4f),
    SPOKEN_WORD(floatArrayOf(-4f, -3f, 0f, 1f, 3f, 4f, 4f, 3f, 1f, -2f), -3f),
    /** The user's own curve (a band or the preamp moved by hand). */
    CUSTOM(FloatArray(10), 0f),
}

/**
 * The equalizer, speed and silence skipping of the local player (ExoPlayer: extensions' streams,
 * downloads, the phone's files). Spotify's own songs play in the Web Player, whose decoded sound
 * the app never gets: none of this applies to them, nor to a Cast device. Kept across launches.
 *
 * The curve is per output (see [OutputRoute]): the phone's speaker, a cable, each pair of Bluetooth
 * headphones. What is heard is the curve of the output in use, and it follows the output when it
 * changes. The curve Astral had before outputs were told apart (the legacy keys `enabled`,
 * `preset`, `gain0..9`, `preamp`) is the one every output starts from; an output is given its own
 * the first time it is edited, so the sound of nobody's phone changes on update. [enabled],
 * [preset], [gains] and [preamp] and their setters are those of the output in use; the setters
 * that take a key are for a page that edits an output that is not (the Squared look's).
 *
 * Speed: 0.5× to 2× for everything the local player plays, 1× by default (podcasts streamed by
 * Spotify keep their own speed, the podcast player's). Off 1×, Spotify does not follow the song
 * (see AccountSync): its page plays at 1× only.
 *
 * Spatial audio: the stereo widened by mid/side processing (see SpatialProcessor), off by default,
 * on its own switch (the equalizer may be off).
 */
object EqualizerPref {
    private const val PREF = "equalizer_prefs"
    private const val KEY_CURVES = "eq_curves"
    private const val KEY_NAMES = "eq_names"
    private const val KEY_USER_PRESETS = "eq_user_presets"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val p get() = prefs(com.music.spotui.di.Graph.app)

    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** Every output's curve: the legacy one for those without their own; swapped whole on an edit. */
    private var book by mutableStateOf(
        EqCurveBook(
            default = EqCurve(
                enabled = p.getBoolean("enabled", false),
                preset = runCatching { EqPreset.valueOf(p.getString("preset", null) ?: "FLAT") }.getOrDefault(EqPreset.FLAT),
                customGains = List(EqBands.COUNT) { p.getFloat("gain$it", 0f).coerceIn(EqBands.MIN_DB, EqBands.MAX_DB) },
                customPreamp = p.getFloat("preamp", 0f).coerceIn(EqBands.PREAMP_MIN_DB, EqBands.PREAMP_MAX_DB),
            ),
            overrides = EqCodec.decodeOverrides(p.getString(KEY_CURVES, null)),
        ),
    )

    /** What each output was called when it was last seen: for listing the ones not plugged in now. */
    var names by mutableStateOf(EqCodec.decodeNames(p.getString(KEY_NAMES, null)))
        private set

    /** The listener's own presets; the ready-made ones are [EqPreset]. */
    var userPresets by mutableStateOf(EqCodec.decodePresets(p.getString(KEY_USER_PRESETS, null)))
        private set

    /** The key of the output in use (Compose state, followed from [OutputRoute]). */
    var routeKey by mutableStateOf(OutputRoute.current.value.key)
        private set

    var speed by mutableStateOf(p.getFloat("speed", 1f).coerceIn(0.5f, 2f))
        private set
    var skipSilence by mutableStateOf(p.getBoolean("skip_silence", false))
        private set
    var spatial by mutableStateOf(p.getBoolean("spatial", false))
        private set
    var spatialWidth by mutableStateOf(p.getFloat("spatial_width", Spatial.DEFAULT_WIDTH).coerceIn(Spatial.MIN_WIDTH, Spatial.MAX_WIDTH))
        private set

    /** The curve of the output in use. */
    val curve: EqCurve get() = book.curveFor(routeKey)
    val enabled: Boolean get() = curve.enabled
    val preset: EqPreset get() = curve.preset

    /** The gains shown and heard: the preset's, or the user's own. */
    val gains: FloatArray get() = curve.gains.toFloatArray()
    val preamp: Float get() = curve.preamp

    /** The curve of the output [key]: its own, or the one every output starts from. */
    fun curveFor(key: String): EqCurve = book.curveFor(key)

    /** The outputs that have been seen or given a curve, the one in use first. */
    fun knownOutputs(): List<String> = (listOf(routeKey) + names.keys + book.keys).distinct()

    init {
        OutputRoute.start(com.music.spotui.di.Graph.app)
        OutputRoute.onChange { out -> noteRoute(out) }
        noteRoute(OutputRoute.current.value)
    }

    /** An output has come into use: remember what it is called, and follow it. */
    private fun noteRoute(out: OutputRoute.Output) {
        if (out.name.isNotEmpty() && names[out.key] != out.name) {
            names = names + (out.key to out.name)
            p.edit().putString(KEY_NAMES, EqCodec.encodeNames(names)).apply()
        }
        routeKey = out.key
        push()
    }

    // The output in use.
    fun setEnabled(c: Context, on: Boolean) = setEnabled(c, routeKey, on)

    fun setPreset(c: Context, value: EqPreset) = setPreset(c, routeKey, value)

    /**
     * A band moved by hand: the curve shown becomes the user's own, from where it was. Heard at
     * once; kept when [save] (a slider saves once, at the end of its drag, see [save]).
     */
    fun setGain(c: Context, band: Int, db: Float, save: Boolean = true) = setGain(c, routeKey, band, db, save)

    fun setPreamp(c: Context, db: Float, save: Boolean = true) = setPreamp(c, routeKey, db, save)

    // Any output.
    fun setEnabled(c: Context, key: String, on: Boolean) = edit(c, key, true) { it.copy(enabled = on) }

    fun setPreset(c: Context, key: String, value: EqPreset) = edit(c, key, true) { it.withPreset(value) }

    fun setGain(c: Context, key: String, band: Int, db: Float, save: Boolean) = edit(c, key, save) { it.withGain(band, db) }

    fun setPreamp(c: Context, key: String, db: Float, save: Boolean) = edit(c, key, save) { it.withPreamp(db) }

    /** A whole curve (a preset of the user's): it becomes this output's own. */
    fun applyCurve(c: Context, key: String, gains: List<Float>, preamp: Float) =
        edit(c, key, true) { it.withCurve(gains, preamp) }

    /** Flat, still on or off as it was. */
    fun reset(c: Context, key: String) = edit(c, key, true) { it.reset() }

    private fun edit(c: Context, key: String, save: Boolean, change: (EqCurve) -> EqCurve) {
        val next = book.edit(key, change)
        if (next === book) return
        book = next
        if (key == routeKey) push()
        if (save) save(c)
    }

    /** Keeps every output's own curve as it is now. */
    fun save(c: Context) {
        prefs(c).edit().putString(KEY_CURVES, EqCodec.encodeOverrides(book.overrides)).apply()
    }

    /** Keeps a curve under a name, for any output to start from. */
    fun savePreset(c: Context, name: String, gains: List<Float>, preamp: Float) {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
        userPresets = userPresets + EqUserPreset(java.util.UUID.randomUUID().toString(), clean, gains, preamp)
        prefs(c).edit().putString(KEY_USER_PRESETS, EqCodec.encodePresets(userPresets)).apply()
    }

    fun deletePreset(c: Context, id: String) {
        userPresets = userPresets.filterNot { it.id == id }
        prefs(c).edit().putString(KEY_USER_PRESETS, EqCodec.encodePresets(userPresets)).apply()
    }

    fun setSpeed(c: Context, value: Float) {
        speed = value.coerceIn(0.5f, 2f)
        prefs(c).edit().putFloat("speed", speed).apply()
        AudioEffectsPref.noteSpeed(speed)
        com.music.spotui.di.LocalPlayer.applyPlayback()
        // Spotify's page plays at 1× only: the listen sync stops off 1×, and starts again at 1×.
        com.music.spotui.di.AccountSync.listen.refresh()
    }

    fun setSkipSilence(c: Context, on: Boolean) {
        skipSilence = on
        prefs(c).edit().putBoolean("skip_silence", on).apply()
        com.music.spotui.di.LocalPlayer.applyPlayback()
    }

    fun setSpatial(c: Context, on: Boolean) {
        spatial = on
        prefs(c).edit().putBoolean("spatial", on).apply()
        push()
    }

    /** Heard at once, gliding; kept when [save] (a slider saves once, at the end of its drag). */
    fun setSpatialWidth(c: Context, value: Float, save: Boolean = true) {
        spatialWidth = value.coerceIn(Spatial.MIN_WIDTH, Spatial.MAX_WIDTH)
        push()
        if (save) prefs(c).edit().putFloat("spatial_width", spatialWidth).apply()
    }

    /** Hands the curve and the width to the players' audio stages (read on their audio threads). */
    private fun push() {
        val now = curve
        Equalizer.current = if (now.enabled) EqTuning(now.gains.toFloatArray(), now.preamp) else null
        Spatial.current = if (spatial) spatialWidth else null
        // The audio offload is allowed only with both doing nothing.
        com.music.spotui.di.LocalPlayer.applyOffload()
    }
}
