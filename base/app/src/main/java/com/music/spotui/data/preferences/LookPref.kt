package com.music.spotui.data.preferences

/*
 * The app's unified look (Astral Basic, Astral Interactive, Spotifusion 1:1, Glassify, LiquidMusic),
 * picked during onboarding or changed live in Settings → Look.
 */

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The five unified app looks. [CLASSIC] is the default fallback. */
enum class Look(
    val key: String,
    val label: String,
    val detail: String,
) {
    CLASSIC(
        key = "classic",
        label = "Astral Basic",
        detail = "Classic Material shell with dark surfaces and bottom bar.",
    ),
    IMMERSIVE(
        key = "immersive",
        label = "Astral Interactive",
        detail = "Floating glass shell with pill tab bar and album-art mesh.",
    ),
    SPOTIFUSION(
        key = "spotifusion",
        label = "Spotifusion 1:1",
        detail = "Full Spotify 1:1 overhaul: #121212 cards, #1DB954 green & classic nav.",
    ),
    GLASSIFY(
        key = "glassify",
        label = "Glassify",
        detail = "Translucent frosted glass with 1dp specular borders & ambient album backdrop.",
    ),
    LIQUID_MUSIC(
        key = "liquid_music",
        label = "LiquidMusic",
        detail = "iOS 20dp rounded glass panels, cover accent & Liquid Refraction toggle.",
    );

    /** True for floating interactive glass shells ([IMMERSIVE], [GLASSIFY], [LIQUID_MUSIC]). */
    fun isImmersive(): Boolean = this == IMMERSIVE || this == GLASSIFY || this == LIQUID_MUSIC

    companion object {
        fun fromKey(key: String?): Look {
            if (key.isNullOrBlank()) return CLASSIC
            val norm = key.trim().lowercase()
            return when (norm) {
                "classic", "astral_basic", "orpheon_basic" -> CLASSIC
                "immersive", "astral_interactive", "orpheon_glass" -> IMMERSIVE
                "spotifusion", "spotifusion_1_1" -> SPOTIFUSION
                "glassify" -> GLASSIFY
                "liquid_music", "liquidmusic" -> LIQUID_MUSIC
                else -> entries.firstOrNull {
                    it.name.equals(norm, ignoreCase = true) || it.key.equals(norm, ignoreCase = true)
                } ?: CLASSIC
            }
        }

        fun of(name: String?): Look = fromKey(name)
    }
}

object LookPref {
    private const val PREF = "settings_prefs"
    private const val KEY = "look"
    private const val KEY_CHOSEN = "look_chosen"
    private const val KEY_OFFERED = "look_offered"

    /** The chosen look, as Compose state: everything reading it redraws when it changes. */
    var look by mutableStateOf(Look.fromKey(prefs(com.music.spotui.di.Graph.app).getString(KEY, null)))
        private set

    fun set(c: Context, value: Look) {
        look = value
        prefs(c).edit().putString(KEY, value.name).apply()
        PageStylePref.clearOverride(c)
        AstralThemePref.syncFromLook(c, value)
    }

    /** The look step of the onboarding was seen (new installs and updates alike). */
    fun isChosen(c: Context): Boolean = prefs(c).getBoolean(KEY_CHOSEN, false)
    fun setChosen(c: Context) {
        prefs(c).edit().putBoolean(KEY_CHOSEN, true).putBoolean(KEY_OFFERED, true).apply()
        offered = true
    }

    /**
     * The new-look popup was shown to an existing user (or the onboarding look step covered it), as
     * Compose state. The activity kept its own copy, read once at its start: the look step of the
     * onboarding saved "offered" but the copy stayed false, and the popup came up as soon as the
     * onboarding ended, asking the look a second time.
     */
    var offered by mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY_OFFERED, false))
        private set
    fun isOffered(c: Context): Boolean = prefs(c).getBoolean(KEY_OFFERED, false)
    fun setOffered(c: Context) {
        prefs(c).edit().putBoolean(KEY_OFFERED, true).apply()
        offered = true
    }
    fun clearOffered(c: Context) {
        prefs(c).edit().remove(KEY_OFFERED).apply()
        offered = false
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
