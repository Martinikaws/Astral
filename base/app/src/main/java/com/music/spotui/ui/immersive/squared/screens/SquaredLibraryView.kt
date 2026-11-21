/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/data/LibraryViewStore.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed and the class called SquaredLibraryView; it is reached through
 * [SquaredLibraryView.of] (Square holds one in its Application class) and keeps its choices in a
 * preferences file of its own ("squared_library_view").
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.screens

import android.content.Context

/**
 * How the listener left the library looking.
 *
 * A grid and a list are not two ways of showing the same thing to the same
 * person: whoever wants rows wants them every time, and having to say so again
 * on every visit is the app forgetting something it was told.
 */
class SquaredLibraryView private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** True for the grid, which is what a library opens as until told otherwise. */
    var grid: Boolean
        get() = prefs.getBoolean(KEY_GRID, true)
        set(value) {
            prefs.edit().putBoolean(KEY_GRID, value).apply()
        }

    /** The sort, by its own key; unknown values fall back to the default. */
    var order: String?
        get() = prefs.getString(KEY_ORDER, null)
        set(value) {
            prefs.edit().putString(KEY_ORDER, value).apply()
        }

    /** Whether the sort runs backwards; the direction is part of the sort. */
    var descending: Boolean
        get() = prefs.getBoolean(KEY_DESC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DESC, value).apply()
        }

    companion object {
        private const val FILE_NAME = "squared_library_view"
        private const val KEY_GRID = "grid"
        private const val KEY_ORDER = "order"
        private const val KEY_DESC = "descending"

        @Volatile private var instance: SquaredLibraryView? = null

        fun of(context: Context): SquaredLibraryView =
            instance ?: synchronized(this) {
                instance ?: SquaredLibraryView(context).also { instance = it }
            }
    }
}
