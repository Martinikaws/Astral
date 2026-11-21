/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/data/GlassStore.kt. Modified for Astral, 2026-11.
 *
 * Changes: a process singleton like SquaredPrefs instead of a member of Square's application
 * class; the glass is clear here, so the blur radius, the frost switch and the "iOS 27" switch are
 * gone (the refraction switch stays, as a plain one); the Balanced material is the one the Squared
 * shell was already wearing, so the look does not change for anyone who never opens the page; the
 * profile's blurred film is not a thing, and the lowest-cost profile is a tinted film only; the
 * selection puck's wash is the shell's dark one, with the listener's opacity on it.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.glass.GlassEffectConfig
import com.music.spotui.ui.immersive.squared.glass.GlassStyle
import com.music.spotui.ui.immersive.squared.glass.NO_TINT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the glass costs, as three answers rather than eight sliders.
 *
 * Every glass surface records the screen behind it and runs the refraction shader over the
 * recording: that is the look, and it is the most expensive thing the interface does. Whether it is
 * worth it depends on the phone, so the profile is the setting most people should ever touch and
 * the numbers under it are for whoever wants them. The cheapest is first, because someone opening
 * the page is usually there because something felt slow.
 */
enum class SquaredGlassProfile(val key: String, @StringRes val label: Int, @StringRes val note: Int) {
    /** No capture at all: a tinted film over the content and nothing sampled. */
    Performance("performance", R.string.squared_glass_performance, R.string.squared_glass_performance_note),

    /** The look the Squared shell was designed against. */
    Balanced("balanced", R.string.squared_glass_balanced, R.string.squared_glass_balanced_note),

    /** Stronger refraction and the dispersion at the edges: the first to cost frames. */
    Quality("quality", R.string.squared_glass_quality, R.string.squared_glass_quality_note);

    /** The material this profile means, before any of the sliders. */
    fun config(): GlassEffectConfig = when (this) {
        Performance -> BalancedConfig.copy(style = GlassStyle.TRANSPARENT, surfaceOpacity = 0.55f)
        Balanced -> BalancedConfig
        Quality -> BalancedConfig.copy(
            vibrancy = 1.4f,
            lensHeight = 0.7f,
            lensAmount = 0.8f,
            chromaticAberration = true,
        )
    }
}

/**
 * The material the Squared shell has always worn: the library's measured refraction, a thin white
 * film, a deeper and stronger lens than the library's default. The selection puck is a dark wash
 * at the opacity [PUCK_DEFAULT].
 */
val BalancedConfig: GlassEffectConfig = GlassEffectConfig().copy(
    lensHeight = 0.60f,
    lensAmount = 0.69f,
    surfaceTintColor = Color.White,
    surfaceOpacity = 0.15f,
    puckOpacity = PUCK_DEFAULT,
)

/** The selection puck's opacity as the shell has always had it. */
const val PUCK_DEFAULT = 0.535f

/**
 * The glass, as the listener has it: a profile, plus whatever they have moved since choosing it.
 *
 * Overrides are stored apart from the profile rather than flattened into it, so choosing a profile
 * again is a way back: it clears them. Read by the Squared shell, which lays it over every pane.
 */
class SquaredGlassStore private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Slider values that are on screen but not yet in the preferences. A drag reports a value per
     * frame; the config follows it from memory and [commit] writes the last one once.
     */
    private val pending = HashMap<String, Float>()

    private val _profile = MutableStateFlow(
        SquaredGlassProfile.entries.firstOrNull { it.key == prefs.getString(KEY_PROFILE, null) } ?: defaultProfile(),
    )
    val profile: StateFlow<SquaredGlassProfile> = _profile.asStateFlow()

    private val _config = MutableStateFlow(read())
    val config: StateFlow<GlassEffectConfig> = _config.asStateFlow()

    private val _barFolds = MutableStateFlow(prefs.getBoolean(KEY_BAR_FOLDS, true))

    /** Whether the bottom bar folds away as the page scrolls: the app's most expensive animation. */
    val barFolds: StateFlow<Boolean> = _barFolds.asStateFlow()

    fun setProfile(profile: SquaredGlassProfile) {
        if (profile == _profile.value) return
        _profile.value = profile
        // The profile is the answer: choosing one drops the tuning that sat on the last. The bar's
        // fold is not tuning of the glass, and stays as it was.
        pending.clear()
        val folds = prefs.getBoolean(KEY_BAR_FOLDS, true)
        prefs.edit().clear().putString(KEY_PROFILE, profile.key).putBoolean(KEY_BAR_FOLDS, folds).apply()
        _config.value = read()
    }

    fun setVibrancy(value: Float) = putFloat(KEY_VIBRANCY, value)
    fun setLensHeight(value: Float) = putFloat(KEY_LENS_HEIGHT, value)
    fun setLensAmount(value: Float) = putFloat(KEY_LENS_AMOUNT, value)
    fun setSurfaceOpacity(value: Float) = putFloat(KEY_FILM, value)
    fun setHighlightOpacity(value: Float) = putFloat(KEY_RIM, value)
    fun setPuckOpacity(value: Float) = putFloat(KEY_PUCK, value)
    fun setTintStrength(value: Float) = putFloat(KEY_TINT_STRENGTH, value)
    fun setChromaticAberration(value: Boolean) = putBoolean(KEY_DISPERSION, value)
    fun setDepthEffect(value: Boolean) = putBoolean(KEY_DEPTH, value)
    fun setRefraction(value: Boolean) = putBoolean(KEY_REFRACTION, value)
    fun setPlayerEnabled(value: Boolean) = putBoolean(KEY_PLAYER, value)
    fun setMiniPlayerEnabled(value: Boolean) = putBoolean(KEY_MINI_PLAYER, value)
    fun setNavBarEnabled(value: Boolean) = putBoolean(KEY_NAV_BAR, value)

    /** A hue of the listener's choosing for the glass; null puts it back to none. */
    fun setTintHue(hue: Float?) {
        val edit = prefs.edit().remove(KEY_TINT_SYSTEM)
        if (hue == null) edit.remove(KEY_TINT_HUE) else edit.putFloat(KEY_TINT_HUE, hue)
        edit.apply()
        _config.value = read()
    }

    /**
     * The phone's own accent, as the glass's colour: kept as "whatever the system says", so a
     * wallpaper that changes the accent changes the glass with it at the next start.
     */
    fun setTintFromSystem() {
        prefs.edit().putBoolean(KEY_TINT_SYSTEM, true).remove(KEY_TINT_HUE).apply()
        _config.value = read()
    }

    /** Whether the glass is following the phone's accent; see [setTintFromSystem]. */
    val tintFromSystem: Boolean get() = prefs.getBoolean(KEY_TINT_SYSTEM, false)

    fun setBarFolds(value: Boolean) {
        prefs.edit().putBoolean(KEY_BAR_FOLDS, value).apply()
        _barFolds.value = value
    }

    /** Back to the profile as it comes, with nothing moved. */
    fun reset() {
        pending.clear()
        val folds = prefs.getBoolean(KEY_BAR_FOLDS, true)
        prefs.edit().clear().putString(KEY_PROFILE, _profile.value.key).putBoolean(KEY_BAR_FOLDS, folds).apply()
        _config.value = read()
    }

    /** True while any of the numbers differ from the chosen profile's own. */
    val customised: Boolean
        get() = pending.isNotEmpty() || prefs.all.keys.any { it != KEY_PROFILE && it != KEY_BAR_FOLDS }

    /** Writes what a slider drag left in memory; call when the finger lifts. */
    fun commit() {
        if (pending.isEmpty()) return
        val edit = prefs.edit()
        pending.forEach { (k, v) -> edit.putFloat(k, v) }
        pending.clear()
        edit.apply()
    }

    private fun float(key: String, default: Float): Float = pending[key] ?: prefs.getFloat(key, default)

    private fun putFloat(key: String, value: Float) {
        pending[key] = value
        _config.value = read()
    }

    private fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        _config.value = read()
    }

    /**
     * The profile's material with the stored overrides laid over it. Read in full on every change
     * rather than patched: the profile can change underneath, and a patched copy would carry the
     * old one's numbers.
     */
    private fun read(): GlassEffectConfig {
        val base = _profile.value.config()
        return base.copy(
            vibrancy = float(KEY_VIBRANCY, base.vibrancy),
            lensHeight = float(KEY_LENS_HEIGHT, base.lensHeight),
            lensAmount = float(KEY_LENS_AMOUNT, base.lensAmount),
            surfaceOpacity = float(KEY_FILM, base.surfaceOpacity),
            highlightOpacity = float(KEY_RIM, base.highlightOpacity),
            chromaticAberration = prefs.getBoolean(KEY_DISPERSION, base.chromaticAberration),
            depthEffect = prefs.getBoolean(KEY_DEPTH, base.depthEffect),
            refraction = prefs.getBoolean(KEY_REFRACTION, base.refraction),
            playerEnabled = prefs.getBoolean(KEY_PLAYER, base.playerEnabled),
            miniPlayerEnabled = prefs.getBoolean(KEY_MINI_PLAYER, base.miniPlayerEnabled),
            navBarEnabled = prefs.getBoolean(KEY_NAV_BAR, base.navBarEnabled),
            puckOpacity = float(KEY_PUCK, base.puckOpacity),
            tintHue = if (prefs.getBoolean(KEY_TINT_SYSTEM, false)) systemHue() ?: NO_TINT else prefs.getFloat(KEY_TINT_HUE, NO_TINT),
            tintStrength = float(KEY_TINT_STRENGTH, DEFAULT_TINT_STRENGTH),
        )
    }

    /** The accent's hue, or null where the phone has no accent to give. */
    private fun systemHue(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(context.getColor(android.R.color.system_accent1_400), hsv)
            hsv[0]
        }.getOrNull()
    }

    /** The look as it was for everyone, but the lowest-cost material on a phone that says it is short of memory. */
    private fun defaultProfile(): SquaredGlassProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return if (am?.isLowRamDevice == true) SquaredGlassProfile.Performance else SquaredGlassProfile.Balanced
    }

    companion object {
        private const val FILE_NAME = "squared_glass"
        private const val KEY_PROFILE = "profile"
        private const val KEY_VIBRANCY = "vibrancy"
        private const val KEY_LENS_HEIGHT = "lens_height"
        private const val KEY_LENS_AMOUNT = "lens_amount"
        private const val KEY_FILM = "film"
        private const val KEY_RIM = "rim"
        private const val KEY_DISPERSION = "dispersion"
        private const val KEY_DEPTH = "depth"
        private const val KEY_REFRACTION = "refraction"
        private const val KEY_PLAYER = "player"
        private const val KEY_MINI_PLAYER = "mini_player"
        private const val KEY_NAV_BAR = "nav_bar"
        private const val KEY_BAR_FOLDS = "bar_folds"
        private const val KEY_PUCK = "puck"
        private const val KEY_TINT_HUE = "tint_hue"
        private const val KEY_TINT_SYSTEM = "tint_system"
        private const val KEY_TINT_STRENGTH = "tint_strength"

        /** Enough colour to see at a glance, not enough to read as a coloured slab. */
        private const val DEFAULT_TINT_STRENGTH = 0.6f

        @Volatile
        private var instance: SquaredGlassStore? = null

        fun of(context: Context): SquaredGlassStore = instance ?: synchronized(this) {
            instance ?: SquaredGlassStore(context.applicationContext).also { instance = it }
        }
    }
}
