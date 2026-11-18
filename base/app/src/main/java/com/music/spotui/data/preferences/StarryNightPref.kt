package com.music.spotui.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The star backdrop. [Look.STARRY_NIGHT] always has it; every other Look gets it only when the user
 * turned on the "stars behind any Look" extra ([extra], default off). [enabled] is the effective
 * state every screen reads: `extra || look == STARRY_NIGHT`.
 */
object StarryNightPref {
    private const val PREF = "settings_prefs"
    private const val KEY_ENABLED = "starry_night_enabled"
    private const val KEY_SHOOTING_STARS = "starry_night_shooting_stars"

    @Volatile
    private var initialized = false

    private val _extra = MutableStateFlow(false)
    /** The user's "stars behind any Look" extra (default off). */
    val extra: StateFlow<Boolean> = _extra.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    /** True while the star backdrop is drawn: the Starry Night Look, or the extra on any other Look. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _shootingStars = MutableStateFlow(true)
    val shootingStars: StateFlow<Boolean> = _shootingStars.asStateFlow()

    init {
        runCatching { init(com.music.spotui.di.Graph.app) }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val p = prefs(context)
            LookMigration.run(p)
            _extra.value = p.getBoolean(KEY_ENABLED, false)
            _enabled.value = _extra.value || Look.fromKey(p.getString("look", null)) == Look.STARRY_NIGHT
            _shootingStars.value = p.getBoolean(KEY_SHOOTING_STARS, true)
            initialized = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        init(context)
        _extra.value = value
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value || LookPref.look == Look.STARRY_NIGHT
    }

    /** The Look changed: the backdrop follows (always on for Starry Night, else the extra). */
    fun onLookChanged(context: Context, look: Look) {
        init(context)
        _enabled.value = _extra.value || look == Look.STARRY_NIGHT
    }

    fun setShootingStars(context: Context, value: Boolean) {
        init(context)
        _shootingStars.value = value
        prefs(context).edit().putBoolean(KEY_SHOOTING_STARS, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
