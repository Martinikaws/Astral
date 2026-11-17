package com.music.spotui.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StarryNightPref {
    private const val PREF = "settings_prefs"
    private const val KEY_ENABLED = "starry_night_enabled"
    private const val KEY_SHOOTING_STARS = "starry_night_shooting_stars"

    @Volatile
    private var initialized = false

    private val _enabled = MutableStateFlow(true)
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
            _enabled.value = p.getBoolean(KEY_ENABLED, true)
            _shootingStars.value = p.getBoolean(KEY_SHOOTING_STARS, true)
            initialized = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        init(context)
        _enabled.value = value
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setShootingStars(context: Context, value: Boolean) {
        init(context)
        _shootingStars.value = value
        prefs(context).edit().putBoolean(KEY_SHOOTING_STARS, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
