package com.music.spotui.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.music.spotui.data.api.LyricsLookupStatus
import com.music.spotui.data.preferences.LyricsTimingPref
import com.music.spotui.data.preferences.SpicyRenderPref
import com.music.spotui.lyrics.local.LocalLyricsFolder
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LyricsTimingControls(trackKey: String) {
    val offsets by LyricsTimingPref.state.collectAsState()
    val current = offsets[trackKey] ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.spicy_delay_value, current), color = AppText)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = current > -LyricsTimingPref.LIMIT, onClick = {
                LyricsTimingPref.set(trackKey, current - LyricsTimingPref.STEP)
            }) { Text(stringResource(R.string.spicy_delay_earlier)) }
            TextButton(enabled = current < LyricsTimingPref.LIMIT, onClick = {
                LyricsTimingPref.set(trackKey, current + LyricsTimingPref.STEP)
            }) { Text(stringResource(R.string.spicy_delay_later)) }
            TextButton(enabled = current != 0, onClick = { LyricsTimingPref.set(trackKey, 0) }) {
                Text(stringResource(R.string.spicy_delay_reset))
            }
        }
    }
}

@Composable
internal fun SpicyLyricsExtras() {
    val options by SpicyRenderPref.state.collectAsState()
    val status by LyricsLookupStatus.last.collectAsState()
    val revision by LocalLyricsFolder.changes.collectAsState()
    val folder = remember(revision) { LocalLyricsFolder.label }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var background by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun changeFolder(block: () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            failed = false
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { changeFolder { LocalLyricsFolder.link(it) } }
    }

    if (background) com.music.spotui.spicy.SpicyBackgroundSheet { background = false }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        TextButton(onClick = { background = true }) { Text(stringResource(R.string.spicy_renderer_backgrounds)) }
        val controls = listOf(
            Triple(R.string.spicy_bloom_renderer, options.bloom) { checked: Boolean ->
                SpicyRenderPref.update { it.copy(bloom = checked) }
            },
            Triple(R.string.spicy_reduce_effects, options.lowEffects) { checked: Boolean ->
                SpicyRenderPref.update { it.copy(lowEffects = checked) }
            },
            Triple(R.string.spicy_hold_background, options.stillBackground) { checked: Boolean ->
                SpicyRenderPref.update { it.copy(stillBackground = checked) }
            },
        )
        controls.forEach { (res, checked, toggle) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(res), Modifier.weight(1f), color = AppText)
                Switch(checked, toggle)
            }
        }
        Text(stringResource(R.string.spicy_local_lyrics), color = AppText)
        Text(folder ?: stringResource(R.string.spicy_local_folder_none), color = AppMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = !busy, onClick = { pick.launch(null) }) {
                Text(stringResource(R.string.spicy_local_folder_choose), color = AppAccent)
            }
            if (folder != null) {
                TextButton(enabled = !busy, onClick = { changeFolder(LocalLyricsFolder::rescan) }) {
                    Text(stringResource(R.string.spicy_local_folder_rescan), color = AppAccent)
                }
                TextButton(enabled = !busy, onClick = { changeFolder(LocalLyricsFolder::unlink) }) {
                    Text(stringResource(R.string.spicy_local_folder_unlink), color = AppAccent)
                }
            }
        }
        if (failed) Text(stringResource(R.string.spicy_local_folder_error), color = AppMuted)
        Text(stringResource(R.string.spicy_lookup_title), color = AppText)
        val attempt = status
        Text(
            if (attempt == null) stringResource(R.string.spicy_lookup_empty)
            else stringResource(R.string.spicy_lookup_result, attempt.title,
                attempt.provider ?: stringResource(if (attempt.found) R.string.spicy_lookup_unknown else R.string.spicy_lookup_no_result),
                attempt.durationMs),
            color = AppMuted,
        )
    }
}
