package com.music.spotui.data.preferences

/*
 * The app's unified look (Astral Basic, Astral Interactive, Spotifusion 1:1, Glassify, LiquidMusic, Starry Night),
 * picked during onboarding or changed live in Settings → Look.
 */

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The six unified app looks. [CLASSIC] is the default fallback. */
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
        detail = "Pure black, iOS inset lists, bold titles, red accent & Liquid Refraction toggle.",
    ),
    STARRY_NIGHT(
        key = "starry_night",
        label = "Starry Night",
        detail = "Deep navy sky with twinkling stars, shooting stars & warm starlight gold.",
    );

    /** True for floating interactive shells ([IMMERSIVE], [GLASSIFY], [LIQUID_MUSIC], [STARRY_NIGHT]). */
    fun isImmersive(): Boolean = this == IMMERSIVE || this == GLASSIFY || this == LIQUID_MUSIC || this == STARRY_NIGHT

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
                "starry_night", "starrynight" -> STARRY_NIGHT
                else -> entries.firstOrNull {
                    it.name.equals(norm, ignoreCase = true) || it.key.equals(norm, ignoreCase = true)
                } ?: CLASSIC
            }
        }

        fun of(name: String?): Look = fromKey(name)
    }
}

/**
 * One-time upgrade from v1.0.1, where the visual settings lived in three places: [Look] (CLASSIC /
 * IMMERSIVE), `page_style` (a page style rendered independently of the look, default ORPHEON_GLASS) and
 * `astral_theme_mode` (GLASSIFY / LIQUID_MUSIC). Folds them into the 5-Look model so nobody loses their
 * appearance. Idempotent (flag [FLAG]) and a no-op for fresh installs. Runs on raw [SharedPreferences]
 * (no Android Context, no singletons) so it is unit-testable and safe to call from any pref initialiser.
 */
internal object LookMigration {
    const val FLAG = "look_migrated_v2"
    /** v1.0.3: Starry Night became a Look of its own, see [runStarryLook]. */
    const val FLAG_STARRY = "look_migrated_v3"
    private const val STARRY_ENABLED = "starry_night_enabled"
    private const val LOOK = "look"
    private const val LOOK_CHOSEN = "look_chosen"
    private const val PAGE_STYLE = "page_style"
    private const val PAGE_STYLE_OVERRIDE = "page_style_override"
    private const val THEME_MODE = "astral_theme_mode"

    fun run(c: Context) = run(c.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE))

    fun run(p: SharedPreferences) {
        runV2(p)
        runStarryLook(p)
    }

    /**
     * Starry Night used to be a switch (default on) over the Astral Interactive look. It is a Look now
     * and the switch is only an extra ("stars behind any Look", default off): a user who had the stars
     * on (never touched, or explicitly on) with Astral Interactive moves to [Look.STARRY_NIGHT]; the
     * switch is then cleared. Anyone else keeps their Look; the extra is kept on for an upgrader who never set the
     * switch (the stars were on by default behind every Look) and survives when it was explicitly on. Idempotent (flag [FLAG_STARRY]); fresh installs are untouched.
     */
    fun runStarryLook(p: SharedPreferences) {
        if (p.getBoolean(FLAG_STARRY, false)) return
        val e = p.edit().putBoolean(FLAG_STARRY, true)
        val existing = p.getBoolean(LOOK_CHOSEN, false) || p.contains(LOOK) || p.contains(PAGE_STYLE) || p.contains(THEME_MODE) ||
            p.contains("starry_night_shooting_stars")
        if (existing) {
            val starryOn = p.getBoolean(STARRY_ENABLED, true) // an unset switch was on
            val look = Look.fromKey(p.getString(LOOK, null))
            if (look == Look.IMMERSIVE && starryOn) {
                e.putString(LOOK, Look.STARRY_NIGHT.name).putBoolean(STARRY_ENABLED, false)
                if (p.contains(THEME_MODE)) e.putString(THEME_MODE, "STARRY_NIGHT")
            } else if (look != Look.IMMERSIVE && look != Look.STARRY_NIGHT && !p.contains(STARRY_ENABLED)) {
                // The stars were on by default behind every other Look too: the extra keeps them for upgraders.
                e.putBoolean(STARRY_ENABLED, true)
            }
        }
        e.apply()
    }

    private fun runV2(p: SharedPreferences) {
        if (p.getBoolean(FLAG, false)) return
        val e = p.edit().putBoolean(FLAG, true)
        val lookChosen = p.getBoolean(LOOK_CHOSEN, false) || p.contains(LOOK)
        val styleName = p.getString(PAGE_STYLE, null)
        val theme = p.getString(THEME_MODE, null)
        if (!lookChosen && styleName == null && theme == null) { e.apply(); return } // fresh install

        val look = Look.fromKey(p.getString(LOOK, null))
        val old = look == Look.CLASSIC || look == Look.IMMERSIVE
        val newLook = when {
            old && theme == "GLASSIFY" -> Look.GLASSIFY
            old && theme == "LIQUID_MUSIC" -> Look.LIQUID_MUSIC
            look == Look.CLASSIC && styleName == PageStyle.SPOTIFUSION.name -> Look.SPOTIFUSION
            else -> look
        }
        if (newLook != look) e.putString(LOOK, newLook.name)

        // v1.0.1 rendered pages purely from page_style, defaulting to ORPHEON_GLASS when never stored
        // (PageStyle.of), whatever the look was. So a chosen look without a stored style really showed
        // glass pages: keep that as an explicit override when it differs from what the look implies
        // (CLASSIC -> Basic now). Past the fresh-install return, v1.0.1 data exists, so what it rendered is preserved.
        val rendered = PageStyle.of(styleName)
        if (rendered != PageStyle.forLook(newLook)) {
            e.putString(PAGE_STYLE, rendered.name).putBoolean(PAGE_STYLE_OVERRIDE, true)
        } else {
            e.remove(PAGE_STYLE).putBoolean(PAGE_STYLE_OVERRIDE, false)
        }

        // Same mapping as AstralThemePref.syncFromLook; only the theme key is touched, Starry Night's own
        // enable state lives in StarryNightPref.
        val mode = when (newLook) {
            Look.GLASSIFY -> "GLASSIFY"
            Look.LIQUID_MUSIC -> "LIQUID_MUSIC"
            else -> "STARRY_NIGHT"
        }
        if (theme != null && theme != mode) e.putString(THEME_MODE, mode)
        e.apply()
    }
}

object LookPref {
    private const val PREF = "settings_prefs"
    private const val KEY = "look"
    private const val KEY_CHOSEN = "look_chosen"
    private const val KEY_OFFERED = "look_offered"

    /** The chosen look, as Compose state: everything reading it redraws when it changes. */
    var look by mutableStateOf(
        prefs(com.music.spotui.di.Graph.app).also { LookMigration.run(it) }.getString(KEY, null).let(Look::fromKey),
    )
        private set

    fun set(c: Context, value: Look) {
        look = value
        prefs(c).edit().putString(KEY, value.name).apply()
        PageStylePref.clearOverride(c)
        AstralThemePref.syncFromLook(c, value)
        StarryNightPref.onLookChanged(c, value)
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
