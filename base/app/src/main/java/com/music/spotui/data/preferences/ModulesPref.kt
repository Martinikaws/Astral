package com.music.spotui.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls whether the full-screen **Modules** tab (BackMusic + Visualizer) is shown in navigation.
 */
object ModulesPref {
    private const val PREFS = "settings_prefs"
    private const val KEY_ENABLED = "modules_tab_enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
            initialized = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        init(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }
}
