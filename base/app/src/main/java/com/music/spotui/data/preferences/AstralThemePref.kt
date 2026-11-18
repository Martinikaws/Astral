package com.music.spotui.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AstralThemeMode(val label: String, val detail: String) {
    STARRY_NIGHT("Starry Night", "StarryNight [Base] deep sky with warm starlight accents."),
    GLASSIFY("Glassify", "Translucent frosted glass surfaces with crisp 1dp borders."),
    LIQUID_MUSIC("LiquidMusic", "Dynamic album-art accents with liquid refraction or frosted blur."),
}

enum class SpicyThemePreset(
    val label: String,
    val accentHex: Long,
    val glowHex: Long,
    val bgTopHex: Long,
    val bgBottomHex: Long,
) {
    ASTRAL_DEFAULT("Astral Default", 0xFFFFF3C4, 0xFF85B7EB, 0xFF0A1422, 0xFF142B44),
    STARRY_GOLD("Starry Gold", 0xFFFFD166, 0xFFF4A261, 0xFF0D0B14, 0xFF261E10),
    NEON_ROSE("Neon Rose", 0xFFFF5C8A, 0xFFFF8FAB, 0xFF1A0814, 0xFF2D0F24),
    MIDNIGHT_CYAN("Midnight Cyan", 0xFF38BDF8, 0xFF22D3EE, 0xFF04141E, 0xFF0B2A3E),
    SUNSET_AMBER("Sunset Amber", 0xFFFF8C42, 0xFFFFB703, 0xFF1B0E07, 0xFF34180C),
    MONOCHROME_FROST("Monochrome Frost", 0xFFE2E8F0, 0xFF94A3B8, 0xFF0B0F14, 0xFF1B232E),
}

object AstralThemePref {
    private const val PREF = "settings_prefs"
    private const val KEY_THEME_MODE = "astral_theme_mode"
    private const val KEY_LIQUID_REFRACTION = "liquid_music_refraction"
    private const val KEY_SPICY_THEME = "spicy_theme_preset"

    @Volatile
    private var initialized = false

    private val _themeMode = MutableStateFlow(AstralThemeMode.STARRY_NIGHT)
    val themeMode: StateFlow<AstralThemeMode> = _themeMode.asStateFlow()

    private val _liquidGlassRefraction = MutableStateFlow(true)
    val liquidGlassRefraction: StateFlow<Boolean> = _liquidGlassRefraction.asStateFlow()

    private val _spicyTheme = MutableStateFlow(SpicyThemePreset.ASTRAL_DEFAULT)
    val spicyTheme: StateFlow<SpicyThemePreset> = _spicyTheme.asStateFlow()

    init {
        runCatching { init(com.music.spotui.di.Graph.app) }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val p = prefs(context)
            LookMigration.run(p)
            _themeMode.value = p.getString(KEY_THEME_MODE, null)
                ?.let { name -> AstralThemeMode.entries.firstOrNull { it.name == name } }
                ?: AstralThemeMode.STARRY_NIGHT
            _liquidGlassRefraction.value = p.getBoolean(KEY_LIQUID_REFRACTION, true)
            _spicyTheme.value = p.getString(KEY_SPICY_THEME, null)
                ?.let { name -> SpicyThemePreset.entries.firstOrNull { it.name == name } }
                ?: SpicyThemePreset.ASTRAL_DEFAULT
            initialized = true
        }
    }

    fun setThemeMode(context: Context, mode: AstralThemeMode) {
        init(context)
        _themeMode.value = mode
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /**
     * Keeps [themeMode] aligned when the unified [LookPref] changes. Starry Night is a Look of its own now
     * ([Look.STARRY_NIGHT]): the stars follow the Look through [StarryNightPref.onLookChanged], this key only
     * tells Glassify / LiquidMusic from everything else.
     */
    fun syncFromLook(context: Context, look: Look) {
        val mapped = when (look) {
            Look.GLASSIFY -> AstralThemeMode.GLASSIFY
            Look.LIQUID_MUSIC -> AstralThemeMode.LIQUID_MUSIC
            else -> AstralThemeMode.STARRY_NIGHT
        }
        setThemeMode(context, mapped)
    }

    fun setLiquidGlassRefraction(context: Context, enabled: Boolean) {
        init(context)
        _liquidGlassRefraction.value = enabled
        prefs(context).edit().putBoolean(KEY_LIQUID_REFRACTION, enabled).apply()
    }

    fun setSpicyTheme(context: Context, preset: SpicyThemePreset) {
        init(context)
        _spicyTheme.value = preset
        prefs(context).edit().putString(KEY_SPICY_THEME, preset.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
