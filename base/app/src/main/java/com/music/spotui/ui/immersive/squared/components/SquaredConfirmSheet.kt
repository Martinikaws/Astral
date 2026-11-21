/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/components/ConfirmSheet.kt at 139441f.
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

/**
 * One question with two answers: upstream's GlassActionSheet, the answer
 * over the cancel button, in red where it takes something away.
 */
@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Red rather than the ink, for an answer that takes something away. */
    destructive: Boolean = false,
) {
    GlassActionSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        message = message,
        cancelLabel = cancelLabel,
    ) {
        GlassActionSheetAction(
            label = confirmLabel,
            style = if (destructive) GlassActionSheetStyle.Destructive else GlassActionSheetStyle.Default,
            onClick = onConfirm,
        )
    }
}
