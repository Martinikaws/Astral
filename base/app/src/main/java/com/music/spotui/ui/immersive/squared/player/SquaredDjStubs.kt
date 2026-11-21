/*
 * Written for Astral, 2026-11. Square's DJ voice and mark (its Spotify DJ support) are not part of
 * the Squared look; these stand in so the ported player and mini player draw nothing for them.
 */
package com.music.spotui.ui.immersive.squared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

@Composable
internal fun DjVoice(modifier: Modifier = Modifier) = Unit

@Composable
internal fun DjMark(color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) = Unit

internal fun isDjQueue(contextUri: String?): Boolean = false
