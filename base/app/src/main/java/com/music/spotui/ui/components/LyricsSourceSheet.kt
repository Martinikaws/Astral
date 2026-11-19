package com.music.spotui.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.engine.R
import com.music.spotui.data.api.LyricsApi
import com.music.spotui.data.preferences.LyricsSourcePref
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone
import com.music.spotui.ui.viewmodel.LyricsViewModel

/**
 * "Try another source": the providers that can give this song's lyrics, the one shown marked,
 * the word-timed ones named, the ones set aside for now dimmed. A tap asks that one (the lyrics
 * shown stay until it answers); the sheet closes on lyrics, and stays with a toast when the
 * provider had none. Shared by both looks, like the other pickers behind the song options.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LyricsSourceSheet(onDismiss: () -> Unit, vm: LyricsViewModel = viewModel()) {
    val context = LocalContext.current
    val track = remember { SongPlayer.currentLyricsTrack() }
    if (track == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val state by vm.state.collectAsState()
    val trying by vm.trying.collectAsState()
    val current = (state as? LyricsViewModel.State.Loaded)?.lyrics?.let { LyricsApi.sourceOf(it) }
    // Bumped when a source is switched on or off: the list is read again.
    var changes by remember { mutableStateOf(0) }
    val sources = remember(track, changes) { vm.sources(track) }
    // The source whose one-time notice (who is asked, what is sent) is showing, before it is first switched on.
    var notice by remember { mutableStateOf<LyricsApi.Source?>(null) }
    fun switchSource(s: LyricsApi.Source) {
        if (!s.enabled && s.id in LyricsSourcePref.OPT_IN && !LyricsSourcePref.noticeSeen(context, s.id)) { notice = s; return }
        LyricsSourcePref.set(context, s.id, !s.enabled)
        changes++
    }

    // The answer to a try: new lyrics close the sheet, a provider with nothing says so.
    var asked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(trying) {
        val id = trying
        if (id != null) { asked = id; return@LaunchedEffect }
        val was = asked ?: return@LaunchedEffect
        asked = null
        if (vm.triedNothing.value == was) {
            val name = sources.firstOrNull { it.id == was }?.name ?: was
            Toast.makeText(context, context.getString(R.string.lyrics_source_nothing, name), Toast.LENGTH_SHORT).show()
        } else onDismiss()
    }

    LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.lyrics_other_source), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp, 8.dp),
            )
            sources.forEach { s ->
                val here = s.id == current
                val detail = listOfNotNull(
                    if (here) stringResource(R.string.lyrics_source_shown) else null,
                    if (s.words) stringResource(R.string.lyrics_source_words) else null,
                    if (s.setAside != null && s.enabled) stringResource(R.string.lyrics_source_aside) else null,
                    if (!s.enabled) stringResource(R.string.lyrics_source_off) else null,
                ).joinToString(" · ")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Set aside: dimmed, still tappable (asking by hand ignores the break).
                        .alpha(if (s.setAside != null || !s.enabled) 0.45f else 1f)
                        // A source that is off is switched on by a tap (after its notice), not asked.
                        .clickable(enabled = trying == null) { if (s.enabled) vm.loadFrom(track, s.id) else switchSource(s) }
                        .padding(20.dp, 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = AppText, fontSize = 16.sp)
                        if (detail.isNotEmpty()) Text(detail, color = AppMuted, fontSize = 12.sp)
                    }
                    if (trying == s.id) CircularProgressIndicator(color = AppText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else if (here) Icon(Icons.Default.Check, contentDescription = null, tint = AppText)
                    if (s.toggle) Switch(checked = s.enabled, onCheckedChange = { switchSource(s) })
                }
            }
        }
    }

    notice?.let { s ->
        AlertDialog(
            onDismissRequest = { notice = null },
            containerColor = tone(0xFF282828),
            title = { Text(s.name, color = AppText) },
            text = { Text(stringResource(sourceNotice(s.id)), color = AppText) },
            confirmButton = {
                TextButton(onClick = {
                    LyricsSourcePref.markNoticeSeen(context, s.id)
                    LyricsSourcePref.set(context, s.id, true)
                    changes++
                    notice = null
                }) { Text(stringResource(R.string.lyrics_source_turn_on), color = AppText) }
            },
            dismissButton = { TextButton(onClick = { notice = null }) { Text(stringResource(R.string.lyrics_source_not_now), color = AppMuted) } },
        )
    }
}

/** The one-time notice of a source that is off until the user turns it on: who is asked and what is sent. */
private fun sourceNotice(id: String): Int = when (id) {
    "qq" -> R.string.lyrics_notice_qq
    "kuwo" -> R.string.lyrics_notice_kuwo
    "rmm" -> R.string.lyrics_notice_rmm
    else -> R.string.lyrics_notice_generic
}
