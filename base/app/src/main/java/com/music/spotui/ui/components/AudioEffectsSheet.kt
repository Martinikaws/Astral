package com.music.spotui.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.data.preferences.AudioEffectsModel
import com.music.spotui.data.preferences.AudioEffectsPref
import com.music.spotui.data.preferences.EffectPreset
import com.music.spotui.data.preferences.ORIGINAL_ID
import com.music.spotui.data.preferences.SLOWED_ID
import com.music.spotui.data.preferences.SPED_UP_ID
import com.music.spotui.di.AudioEffectsState
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The effects of the app's own playback (see [AudioEffectsPref]): speed, pitch, reverb and the
 * karaoke dial, presets (the built-in ones and the user's own, saved from the sliders), and a
 * reset. One sheet for every look ([LookSheet]). Disabled, with the reason, while the song plays
 * through Spotify's own player ([AudioEffectsState.supported]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioEffectsSheet(onDismiss: () -> Unit) {
    val supported by AudioEffectsState.supported.collectAsState()
    val speed by AudioEffectsPref.speed.collectAsState()
    val pitch by AudioEffectsPref.pitch.collectAsState()
    val reverb by AudioEffectsPref.reverb.collectAsState()
    val karaoke by AudioEffectsPref.karaoke.collectAsState()
    val presets by AudioEffectsPref.presets.collectAsState()
    var saving by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.effects_preset_default_name)
    val semitonesUnit = stringResource(R.string.effects_semitones)
    val off = stringResource(R.string.effects_off)

    LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.effects_title), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            if (!supported) {
                Text(stringResource(R.string.effects_unsupported), color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Text(stringResource(R.string.effects_presets), color = AppMuted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                presets.forEach { preset ->
                    val selected = preset.matches(speed, pitch, reverb, karaoke)
                    FilterChip(
                        selected = selected,
                        enabled = supported,
                        onClick = { AudioEffectsPref.applyPreset(preset) },
                        label = { Text(presetLabel(preset), maxLines = 1) },
                        trailingIcon = if (preset.builtIn) null else ({
                            val remove = stringResource(R.string.effects_delete_preset, preset.name)
                            Text(
                                "✕", fontSize = 12.sp,
                                modifier = Modifier
                                    .semantics { contentDescription = remove }
                                    .clickable(enabled = supported) { AudioEffectsPref.deletePreset(preset.id) }
                                    .padding(horizontal = 4.dp),
                            )
                        }),
                    )
                }
            }

            EffectSlider(
                label = stringResource(R.string.effects_speed), value = speed, range = AudioEffectsModel.SPEED_MIN..AudioEffectsModel.SPEED_MAX,
                format = { String.format(Locale.getDefault(), "%.2f×", it) }, enabled = supported,
                onChange = AudioEffectsPref::setSpeed,
            )
            EffectSlider(
                label = stringResource(R.string.effects_pitch), value = pitch, range = AudioEffectsModel.PITCH_MIN..AudioEffectsModel.PITCH_MAX,
                format = { String.format(Locale.getDefault(), "%.2f× · %+.1f %s", it, AudioEffectsModel.semitones(it), semitonesUnit) },
                enabled = supported,
                onChange = AudioEffectsPref::setPitch,
            )
            EffectSlider(
                label = stringResource(R.string.effects_reverb), value = reverb, range = 0f..1f,
                format = { if (it <= 0f) off else String.format(Locale.getDefault(), "%d%%", (it * 100).roundToInt()) },
                enabled = supported,
                onChange = AudioEffectsPref::setReverb, onPreview = AudioEffectsPref::previewReverb,
            )
            EffectSlider(
                label = stringResource(R.string.effects_karaoke), value = karaoke, range = 0f..1f,
                format = { if (it <= 0f) off else String.format(Locale.getDefault(), "%d%%", (it * 100).roundToInt()) },
                enabled = supported,
                onChange = AudioEffectsPref::setKaraoke, onPreview = AudioEffectsPref::previewKaraoke,
            )
            Text(stringResource(R.string.effects_karaoke_hint), color = AppMuted, fontSize = 12.sp)

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { saving = true }, enabled = supported) { Text(stringResource(R.string.effects_save_preset), color = AppAccent) }
                TextButton(onClick = AudioEffectsPref::reset, enabled = supported) { Text(stringResource(R.string.effects_reset), color = AppAccent) }
            }
        }
    }

    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(R.string.effects_save_preset)) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.effects_preset_name)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { AudioEffectsPref.savePreset(name.ifBlank { defaultName }); saving = false }) { Text(stringResource(R.string.effects_save)) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** The preset's name: the built-in ones come from the app's strings (translated), the user's own as typed. */
@Composable
private fun presetLabel(preset: EffectPreset): String = when (preset.id) {
    ORIGINAL_ID -> stringResource(R.string.effects_preset_original)
    SLOWED_ID -> stringResource(R.string.effects_preset_slowed)
    SPED_UP_ID -> stringResource(R.string.effects_preset_sped_up)
    else -> preset.name
}

/**
 * A labelled slider with its value shown. While dragged it follows the finger on its own;
 * [onChange] gets the value only at the end of the drag (it is written to the preferences and
 * applied to the players, which should not happen sixty times a second); [onPreview], when given,
 * follows the finger meanwhile (the reverb and karaoke stages read a live value).
 */
@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onPreview: ((Float) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val current = if (dragging) local else value
    // Dismissed mid-drag: commit what was previewed, so the live effect and the saved value agree.
    DisposableEffect(Unit) { onDispose { if (dragging) onChange(local) } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = if (enabled) AppText else AppMuted, fontSize = 16.sp)
            Text(format(current), color = AppMuted, fontSize = 14.sp)
        }
        Slider(
            value = current.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                dragging = true; local = it
                onPreview?.invoke(it)
            },
            onValueChangeFinished = { onChange(local); dragging = false },
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = AppAccent, activeTrackColor = AppAccent),
            modifier = Modifier.semantics { contentDescription = "$label ${format(current)}" },
        )
    }
}
