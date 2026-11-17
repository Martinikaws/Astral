package com.music.spotui.data.preferences

/*
 * Lossless (SpotiFLAC) download and streaming preferences: availability and quality.
 */

import android.content.Context

private const val PREF = "LosslessPref"
private const val KEY_ENABLED = "enabled"

/** Lossless FLAC resolution enabled. */
fun isLosslessEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

fun setLosslessEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
