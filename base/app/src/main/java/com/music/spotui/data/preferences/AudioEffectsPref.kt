/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, by Lelonio,
 * app/src/main/java/dev/lelonio/square/playback/AudioEffects.kt and EffectPresets.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: the persisted effect settings (reverb, karaoke, speed and pitch survive a restart), the
 * load-before-any-write rule, the built-in presets (Original, Slowed + Reverb, Sped Up) and the
 * saved-presets list are Square's; here the state is a plain model over a small key-value store
 * (unit-tested on the JVM), the karaoke dial is part of a preset, the speed is the app's own
 * speed setting (EqualizerPref) so there is one speed, presets are stored as JSON with
 * kotlinx.serialization in the app's preferences, and the player stages are fed through [AudioFx].
 */
package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.di.AudioFx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** A named speed / pitch / reverb / karaoke combination. [builtIn] ones cannot be deleted; their name comes from the app's strings. */
@Serializable
data class EffectPreset(
    val id: String,
    val name: String,
    val speed: Float,
    val pitch: Float,
    val reverb: Float = 0f,
    val karaoke: Float = 0f,
    val builtIn: Boolean = false,
) {
    /** True when the playback now sounds like this preset (sliders give 0.8499999; compared with a tolerance). */
    fun matches(speed: Float, pitch: Float, reverb: Float, karaoke: Float): Boolean =
        abs(this.speed - speed) < PRESET_TOLERANCE && abs(this.pitch - pitch) < PRESET_TOLERANCE &&
            abs(this.reverb - reverb) < PRESET_TOLERANCE && abs(this.karaoke - karaoke) < PRESET_TOLERANCE
}

private const val PRESET_TOLERANCE = 0.005f

/** The presets everyone gets (Square's three: named as these edits are labelled). */
val BuiltInEffectPresets: List<EffectPreset> = listOf(
    EffectPreset(id = ORIGINAL_ID, name = "Original", speed = 1f, pitch = 1f, reverb = 0f, builtIn = true),
    EffectPreset(id = SLOWED_ID, name = "Slowed + Reverb", speed = 0.85f, pitch = 0.92f, reverb = 0.55f, builtIn = true),
    EffectPreset(id = SPED_UP_ID, name = "Sped Up", speed = 1.25f, pitch = 1.25f, reverb = 0f, builtIn = true),
)

const val ORIGINAL_ID = "original"
const val SLOWED_ID = "slowed"
const val SPED_UP_ID = "sped_up"

/** Where the model keeps its values (SharedPreferences in the app, a map in the tests). */
interface EffectsStorage {
    fun getFloat(key: String, default: Float): Float
    fun getString(key: String): String?
    fun putFloat(key: String, value: Float)
    fun putString(key: String, value: String)
}

/** What the model tells the rest of the app when the sound changes (the app's wiring, or nothing in tests). */
interface EffectsSink {
    /** The speed (the app's own speed setting). */
    fun speed(value: Float) {}
    /** Something heard changed: the players' stages and the audio offload follow. */
    fun changed(speed: Float, pitch: Float, reverb: Float, karaoke: Float) {}
}

/**
 * The four live effects of the local player and the presets, as flows. Loaded from [storage] when
 * built, before anything can write (a write before the load would put the defaults over what was
 * saved). Setters clamp, snap to 0.01 and write at once.
 */
class AudioEffectsModel(
    private val storage: EffectsStorage,
    initialSpeed: Float,
    private val sink: EffectsSink = object : EffectsSink {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(EffectPreset.serializer())

    private val _speed = MutableStateFlow(snap(initialSpeed, SPEED_MIN, SPEED_MAX))
    private val _pitch = MutableStateFlow(snap(storage.getFloat(KEY_PITCH, 1f), PITCH_MIN, PITCH_MAX))
    private val _reverb = MutableStateFlow(snap(storage.getFloat(KEY_REVERB, 0f), 0f, 1f))
    private val _karaoke = MutableStateFlow(snap(storage.getFloat(KEY_KARAOKE, 0f), 0f, 1f))
    private val custom = MutableStateFlow(loadCustom())
    private val _presets = MutableStateFlow(BuiltInEffectPresets + custom.value)

    /** Playback speed, 0.5 to 2 (the app's speed setting). */
    val speed: StateFlow<Float> = _speed.asStateFlow()
    /** Pitch factor, 0.5 to 2 (see [semitones]). */
    val pitch: StateFlow<Float> = _pitch.asStateFlow()
    /** Reverb amount, 0 (dry) to 1 (large hall). */
    val reverb: StateFlow<Float> = _reverb.asStateFlow()
    /** Karaoke dial, 0 (as recorded) to 1 (the centre of the stage taken out as far as this goes). */
    val karaoke: StateFlow<Float> = _karaoke.asStateFlow()
    /** The built-in presets, then the user's own in the order they were saved. */
    val presets: StateFlow<List<EffectPreset>> = _presets.asStateFlow()

    /** Every value is at its default. */
    val isOriginal: Boolean get() = _speed.value == 1f && _pitch.value == 1f && _reverb.value == 0f && _karaoke.value == 0f

    fun setSpeed(value: Float) { set(speed = snap(value, SPEED_MIN, SPEED_MAX)) }
    fun setPitch(value: Float) { set(pitch = snap(value, PITCH_MIN, PITCH_MAX)) }
    fun setReverb(value: Float) { set(reverb = snap(value, 0f, 1f)) }
    fun setKaraoke(value: Float) { set(karaoke = snap(value, 0f, 1f)) }

    /** The speed changed elsewhere (the equalizer screen): follow it without writing it back. */
    fun noteSpeed(value: Float) { _speed.value = snap(value, SPEED_MIN, SPEED_MAX) }

    fun applyPreset(preset: EffectPreset) = set(
        speed = snap(preset.speed, SPEED_MIN, SPEED_MAX), pitch = snap(preset.pitch, PITCH_MIN, PITCH_MAX),
        reverb = snap(preset.reverb, 0f, 1f), karaoke = snap(preset.karaoke, 0f, 1f),
    )

    fun applyPreset(id: String): Boolean = _presets.value.firstOrNull { it.id == id }?.also(::applyPreset) != null

    /** Keeps the values now as a preset named [name] (blank: "Custom"); returns it. */
    fun savePreset(name: String): EffectPreset {
        val preset = EffectPreset(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Custom" }.take(40),
            speed = _speed.value, pitch = _pitch.value, reverb = _reverb.value, karaoke = _karaoke.value,
        )
        custom.value = custom.value + preset
        persistPresets()
        return preset
    }

    /** Deletes a saved preset (a built-in one stays). */
    fun deletePreset(id: String) {
        if (custom.value.none { it.id == id }) return
        custom.value = custom.value.filterNot { it.id == id }
        persistPresets()
    }

    /** Back to the original sound: speed 1, pitch 1, no reverb, no karaoke. */
    fun reset() = set(speed = 1f, pitch = 1f, reverb = 0f, karaoke = 0f)

    private fun set(speed: Float = _speed.value, pitch: Float = _pitch.value, reverb: Float = _reverb.value, karaoke: Float = _karaoke.value) {
        val speedChanged = speed != _speed.value
        val pitchChanged = pitch != _pitch.value
        val reverbChanged = reverb != _reverb.value
        val karaokeChanged = karaoke != _karaoke.value
        if (!speedChanged && !pitchChanged && !reverbChanged && !karaokeChanged) return
        _speed.value = speed; _pitch.value = pitch; _reverb.value = reverb; _karaoke.value = karaoke
        if (pitchChanged) storage.putFloat(KEY_PITCH, pitch)
        if (reverbChanged) storage.putFloat(KEY_REVERB, reverb)
        if (karaokeChanged) storage.putFloat(KEY_KARAOKE, karaoke)
        if (speedChanged) sink.speed(speed)
        sink.changed(speed, pitch, reverb, karaoke)
    }

    private fun persistPresets() {
        _presets.value = BuiltInEffectPresets + custom.value
        storage.putString(KEY_PRESETS, json.encodeToString(listSerializer, custom.value))
    }

    private fun loadCustom(): List<EffectPreset> {
        val raw = storage.getString(KEY_PRESETS) ?: return emptyList()
        // Entry by entry: one malformed preset is dropped, not the whole list; a list that is not an array at all is dropped
        // rather than failing the player (a convenience, not data to fail over).
        val entries = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
        return entries.mapNotNull { runCatching { json.decodeFromJsonElement(EffectPreset.serializer(), it) }.getOrNull() }
            .filter { !it.builtIn && it.id.isNotBlank() }
    }

    companion object {
        const val SPEED_MIN = 0.5f
        const val SPEED_MAX = 2f
        const val PITCH_MIN = 0.5f
        const val PITCH_MAX = 2f
        internal const val KEY_PITCH = "pitch"
        internal const val KEY_REVERB = "reverb"
        internal const val KEY_KARAOKE = "karaoke"
        internal const val KEY_PRESETS = "presets"

        /** The pitch factor as semitones (12 per octave): 2 is +12, 0.5 is -12. */
        fun semitones(pitch: Float): Float = (12.0 * ln(pitch.toDouble()) / ln(2.0)).toFloat()

        private fun snap(v: Float, min: Float, max: Float): Float =
            ((if (v.isNaN()) min else v.coerceIn(min, max)) * 100f).roundToInt() / 100f
    }
}

/**
 * The effects of the app's own playback (extensions' streams, downloads, the phone's files): speed,
 * pitch, reverb, karaoke and the presets. Kept across launches; loaded once from [Graph.init]
 * before anything can write. Spotify's own player (the native engine, the Web Player, Connect) and
 * a Cast device do not take them: see [com.music.spotui.di.AudioEffectsState.supported].
 */
object AudioEffectsPref {
    private const val PREF = "audio_effects"

    private val model: AudioEffectsModel by lazy {
        val app = com.music.spotui.di.Graph.app
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        AudioEffectsModel(
            storage = object : EffectsStorage {
                override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)
                override fun getString(key: String): String? = prefs.getString(key, null)
                override fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
                override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
            },
            initialSpeed = EqualizerPref.speed,
            sink = object : EffectsSink {
                override fun speed(value: Float) = EqualizerPref.setSpeed(app, value)
                override fun changed(speed: Float, pitch: Float, reverb: Float, karaoke: Float) = push(speed, pitch, reverb, karaoke)
            },
        ).also { push(it.speed.value, it.pitch.value, it.reverb.value, it.karaoke.value, applyNow = false) }
    }

    /**
     * Reads the saved reverb and karaoke and hands them to the players' stages (called once from
     * [com.music.spotui.di.Graph.init], before anything can write; idempotent). Only the preferences
     * file is read here: the model itself is built at its first use.
     */
    fun load() {
        val prefs = com.music.spotui.di.Graph.app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        AudioFx.reverb = prefs.getFloat(AudioEffectsModel.KEY_REVERB, 0f).coerceIn(0f, 1f)
        AudioFx.karaoke = prefs.getFloat(AudioEffectsModel.KEY_KARAOKE, 0f).coerceIn(0f, 1f)
    }

    val speed: StateFlow<Float> get() = model.speed
    val pitch: StateFlow<Float> get() = model.pitch
    val reverb: StateFlow<Float> get() = model.reverb
    val karaoke: StateFlow<Float> get() = model.karaoke
    val presets: StateFlow<List<EffectPreset>> get() = model.presets

    fun setSpeed(value: Float) = model.setSpeed(value)
    fun setPitch(value: Float) = model.setPitch(value)
    fun setReverb(value: Float) { model.setReverb(value); AudioFx.reverb = model.reverb.value }
    fun setKaraoke(value: Float) {
        model.setKaraoke(value)
        previewKaraoke(model.karaoke.value)
    }

    /** While a slider is dragged: the stage follows at once, nothing is written or applied (the release calls [setReverb]). */
    fun previewReverb(value: Float) { AudioFx.reverb = value.coerceIn(0f, 1f) }
    fun previewKaraoke(value: Float) {
        val bounded = value.coerceIn(0f, 1f)
        val changesOffload = (AudioFx.karaoke == 0f) != (bounded == 0f)
        AudioFx.karaoke = bounded
        if (changesOffload) com.music.spotui.di.LocalPlayer.applyOffload()
    }
    fun applyPreset(preset: EffectPreset) = model.applyPreset(preset)
    fun applyPreset(id: String): Boolean = model.applyPreset(id)
    fun savePreset(name: String): EffectPreset = model.savePreset(name)
    fun deletePreset(id: String) = model.deletePreset(id)
    fun reset() = model.reset()

    /** The speed changed through the equalizer screen: the flow follows. */
    internal fun noteSpeed(value: Float) { model.noteSpeed(value) }

    /** The pitch the local player plays at (1 = unchanged); read on the main thread when building playback parameters. */
    fun currentPitch(): Float = model.pitch.value

    /** The pitch the players were last given (a change of it, like a change of speed, is a new set of playback parameters). */
    private var pushedPitch = 1f

    private fun push(speed: Float, pitch: Float, reverb: Float, karaoke: Float, applyNow: Boolean = true) {
        AudioFx.reverb = reverb
        AudioFx.karaoke = karaoke
        if (!applyNow) { pushedPitch = pitch; return }
        // The speed went through EqualizerPref, which applies it; a new pitch is the player's parameter too.
        if (pitch != pushedPitch) { pushedPitch = pitch; com.music.spotui.di.LocalPlayer.applyPlayback() }
        // The reverb and the karaoke are stages of the audio chain, which the audio offload skips: it gives way to them.
        com.music.spotui.di.LocalPlayer.applyOffload()
    }
}
