// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the car player remembers for the life of the process. */
object CarUiState {
    /** Lyrics shown (always, driving or not); the action strip's last button flips it. */
    var lyricsOn by mutableStateOf(true)
}
