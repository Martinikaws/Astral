/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/Coroutines.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed to com.music.spotui.ui.immersive.squared.glass (and R to Astral's);
 * otherwise verbatim.
 *
 * Astral's and Square's changes are under the GNU General Public License, version 3
 * (base/licenses/GPL-3.0.txt). The original work's own notice follows and stays in force for
 * the code it covers.
 */
// Replaces the Backdrop catalog's `expect suspend fun awaitFrame()`, which only
// compiles in a multiplatform project. This app is Android-only, so it calls the
// coroutines-android implementation the catalog's Android source set uses.
//
// From https://github.com/Kyant0/AndroidLiquidGlass (Apache-2.0);
// see LICENSE-backdrop.txt.

package com.music.spotui.ui.immersive.squared.glass

/** Suspends until the next frame is drawn. */
suspend fun awaitFrame() = kotlinx.coroutines.android.awaitFrame()
