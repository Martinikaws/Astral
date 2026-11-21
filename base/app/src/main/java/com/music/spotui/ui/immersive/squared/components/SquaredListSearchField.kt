/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/ListSearchField.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed, imports redirected; otherwise verbatim.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.components

import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.*
import com.music.spotui.ui.immersive.squared.components.*
import com.music.spotui.ui.immersive.squared.glass.*
import com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.*
import com.music.spotui.ui.immersive.squared.glass.shapes.*
import com.music.spotui.ui.immersive.squared.glass.morph.*
import com.music.spotui.ui.immersive.squared.player.*
import com.music.spotui.ui.immersive.squared.model.*
import com.music.spotui.ui.immersive.squared.screens.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The field that filters a list: the library's own lists, and the songs
 * inside a record. Upstream's GlassSearchBar, as every search field in the
 * app is; see GlassSearchBar.
 */
@Composable
fun ListSearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.squared_search_in_tracks),
    /** What the glass refracts; the app's own page where none is given. */
    backdrop: Backdrop? = null,
    height: Dp = 44.dp,
    /** Focus on arrival, for the field that opens out of a button. */
    autoFocus: Boolean = false,
    /** How present the contents are, read on every frame. */
    contentAlpha: () -> Float = { 1f },
) {
    GlassSearchBar(
        query = query,
        onQuery = onQuery,
        modifier = modifier,
        placeholder = placeholder,
        backdrop = backdrop,
        height = height,
        autoFocus = autoFocus,
        contentAlpha = contentAlpha,
    )
}
