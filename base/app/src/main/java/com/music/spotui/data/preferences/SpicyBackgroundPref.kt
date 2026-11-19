package com.music.spotui.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The lyrics background presets of the Spicy Lyrics renderer (see `com.music.spotui.spicy`). */
enum class SpicyBackgroundType(val key: String, val label: String) {
    /** The moving warp of the cover (the Kawarp renderer; the rotating circles below Android 13). */
    DEFAULT("default", "Default"),
    LEGACY("legacy", "Legacy"),
    /** The artist's header picture, or the cover when the artist has none. */
    AUTO("auto", "Auto"),
    ARTIST_HEADER("artist_header", "Artist Header"),
    COVER_ART("cover_art", "Cover Art"),
    /** The cover's main colour, shaded dark enough for white lyrics. */
    COLOR("color", "Color"),
    CUSTOM("custom", "Custom Color");

    /** A still picture, which the blur setting softens. */
    val image get() = this == AUTO || this == ARTIST_HEADER || this == COVER_ART
    val usesArtistHeader get() = this == AUTO || this == ARTIST_HEADER
    /** Moves on its own (and can be held still). */
    val moving get() = this == DEFAULT || this == LEGACY
    /** Has an image to blur. */
    val blurs get() = this == DEFAULT || this == LEGACY || image

    companion object {
        fun fromKey(key: String?): SpicyBackgroundType = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** What the sheet edits: one value per setting, so a change is one copy. */
data class SpicyBackgroundSettings(
    val renderer: Boolean = true,
    val type: SpicyBackgroundType = SpicyBackgroundType.DEFAULT,
    /** Default's blur is a strength in percent (60 is the reference's); the others' is in dp. */
    val dynamicBlur: Int = DEFAULT_DYNAMIC_BLUR,
    val imageBlur: Int = DEFAULT_IMAGE_BLUR,
    /** Hold the moving presets still. */
    val still: Boolean = false,
    /** The moving presets follow the song's loudness and beat, where Astral knows them. */
    val beatReactive: Boolean = false,
    /** [SpicyBackgroundType.CUSTOM]'s colour, ARGB. */
    val customColor: Int = DEFAULT_CUSTOM_COLOR,
) {
    /** The blur of the current preset, in its own unit. */
    val blur: Int get() = if (type == SpicyBackgroundType.DEFAULT) dynamicBlur else imageBlur

    val maxBlur: Int get() = if (type == SpicyBackgroundType.DEFAULT) MAX_DYNAMIC_BLUR else MAX_IMAGE_BLUR

    fun withBlur(value: Int): SpicyBackgroundSettings =
        if (type == SpicyBackgroundType.DEFAULT) copy(dynamicBlur = value.coerceIn(0, MAX_DYNAMIC_BLUR))
        else copy(imageBlur = value.coerceIn(0, MAX_IMAGE_BLUR))

    companion object {
        const val DEFAULT_DYNAMIC_BLUR = 60
        const val MAX_DYNAMIC_BLUR = 100
        const val DEFAULT_IMAGE_BLUR = 20
        const val MAX_IMAGE_BLUR = 67
        const val DEFAULT_CUSTOM_COLOR = 0xFF1B1B2F.toInt()
    }
}

/**
 * The Spicy Lyrics renderer's switch and its background: which preset sits behind the lyrics, its
 * blur, holding it still and following the beat. [state] is what the lyrics views read.
 */
object SpicyBackgroundPref {
    private const val PREF = "settings_prefs"
    private const val KEY_RENDERER = "spicy_renderer"
    private const val KEY_TYPE = "spicy_background_type"
    private const val KEY_DYNAMIC_BLUR = "spicy_background_dynamic_blur"
    private const val KEY_IMAGE_BLUR = "spicy_background_image_blur"
    private const val KEY_STILL = "spicy_background_still"
    private const val KEY_BEAT = "spicy_background_beat"
    private const val KEY_COLOR = "spicy_background_color"

    @Volatile
    private var initialized = false

    private val _state = MutableStateFlow(SpicyBackgroundSettings())
    val state: StateFlow<SpicyBackgroundSettings> = _state.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            _state.value = read(prefs(context))
            initialized = true
        }
    }

    /** Applies [change] to the settings and stores the result. */
    fun update(context: Context, change: (SpicyBackgroundSettings) -> SpicyBackgroundSettings) {
        init(context)
        val next = change(_state.value)
        _state.value = next
        prefs(context).edit()
            .putBoolean(KEY_RENDERER, next.renderer)
            .putString(KEY_TYPE, next.type.key)
            .putInt(KEY_DYNAMIC_BLUR, next.dynamicBlur)
            .putInt(KEY_IMAGE_BLUR, next.imageBlur)
            .putBoolean(KEY_STILL, next.still)
            .putBoolean(KEY_BEAT, next.beatReactive)
            .putInt(KEY_COLOR, next.customColor)
            .apply()
    }

    internal fun read(p: android.content.SharedPreferences): SpicyBackgroundSettings {
        val d = SpicyBackgroundSettings()
        // A value of another type under the same key (a bad restore) falls back to the default.
        fun bool(key: String, def: Boolean) = runCatching { p.getBoolean(key, def) }.getOrDefault(def)
        fun int(key: String, def: Int) = runCatching { p.getInt(key, def) }.getOrDefault(def)
        return SpicyBackgroundSettings(
            renderer = bool(KEY_RENDERER, d.renderer),
            type = SpicyBackgroundType.fromKey(runCatching { p.getString(KEY_TYPE, null) }.getOrNull()),
            dynamicBlur = int(KEY_DYNAMIC_BLUR, d.dynamicBlur).coerceIn(0, SpicyBackgroundSettings.MAX_DYNAMIC_BLUR),
            imageBlur = int(KEY_IMAGE_BLUR, d.imageBlur).coerceIn(0, SpicyBackgroundSettings.MAX_IMAGE_BLUR),
            still = bool(KEY_STILL, d.still),
            beatReactive = bool(KEY_BEAT, d.beatReactive),
            customColor = int(KEY_COLOR, d.customColor) or 0xFF000000.toInt(),
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
