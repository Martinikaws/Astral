/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/player/PlayerPanel.kt (EffectsPanel, PresetRow,
 * EffectSlider and the slider scales) at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed; the panel is fed by Astral's audio-effects engine (AudioEffectsPref)
 * instead of Square's player, and gains Astral's karaoke slider and presets that carry it; the
 * lyrics-delay slider and the sleep timer are not carried over; the reverb and karaoke follow the
 * finger (previewReverb, previewKaraoke) and every value is committed when the finger lifts; when
 * the song is not played by Astral's own player (AudioEffectsState.supported is false) the panel is
 * switched off and says why; the save-preset question is an AlertDialog (Square's NameDialog is not
 * ported); strings are Astral's effects_* resources.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myspoti.engine.R
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.X
import com.music.spotui.data.preferences.AudioEffectsModel
import com.music.spotui.data.preferences.AudioEffectsPref
import com.music.spotui.data.preferences.EffectPreset
import com.music.spotui.data.preferences.ORIGINAL_ID
import com.music.spotui.data.preferences.SLOWED_ID
import com.music.spotui.data.preferences.SPED_UP_ID
import com.music.spotui.di.AudioEffectsState
import com.music.spotui.ui.immersive.squared.components.SideScrollNoBack
import com.music.spotui.ui.immersive.squared.components.horizontalEdgeFade
import com.music.spotui.ui.immersive.squared.glass.LiquidSlider
import com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Tempo, pitch, reverb and karaoke, with the presets, driving [AudioEffectsPref].
 *
 * Speed and pitch are separate controls on purpose: resampling would move both at once, and the
 * whole point of asking for them separately is being able to slow a track down without dropping
 * it an octave.
 */
@Composable
internal fun EffectsPanel(backdrop: Backdrop) {
    val speed by AudioEffectsPref.speed.collectAsStateWithLifecycle()
    val pitch by AudioEffectsPref.pitch.collectAsStateWithLifecycle()
    val reverb by AudioEffectsPref.reverb.collectAsStateWithLifecycle()
    val karaoke by AudioEffectsPref.karaoke.collectAsStateWithLifecycle()
    val presets by AudioEffectsPref.presets.collectAsStateWithLifecycle()
    val supported by AudioEffectsState.supported.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .alpha(if (supported) 1f else 0.4f),
        ) {
            if (!supported) {
                Text(
                    stringResource(R.string.effects_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassInkDim,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            PresetRow(
                presets = presets,
                speed = speed,
                pitch = pitch,
                reverb = reverb,
                karaoke = karaoke,
                onApply = AudioEffectsPref::applyPreset,
                onDelete = AudioEffectsPref::deletePreset,
                onSaveCurrent = { naming = true },
            )

            EffectSlider(
                label = stringResource(R.string.effects_speed),
                value = speed,
                // Below half speed the stretcher smears badly and above double the
                // track stops being recognisable; both ends are past the useful part.
                scale = RatioScale,
                reading = { "%.2f×".format(it) },
                backdrop = backdrop,
                onCommit = AudioEffectsPref::setSpeed,
                onReset = { AudioEffectsPref.setSpeed(1f) },
            )

            val semitones = stringResource(R.string.effects_semitones)
            EffectSlider(
                label = stringResource(R.string.effects_pitch),
                value = pitch,
                scale = RatioScale,
                // Semitones read better than a ratio for pitch: "+3" is a musical
                // amount, "1.19×" is not.
                reading = { "%+.1f %s".format(AudioEffectsModel.semitones(it), semitones) },
                backdrop = backdrop,
                onCommit = AudioEffectsPref::setPitch,
                onReset = { AudioEffectsPref.setPitch(1f) },
            )

            val off = stringResource(R.string.effects_off)
            EffectSlider(
                label = stringResource(R.string.effects_reverb),
                value = reverb,
                scale = LinearScale,
                reading = { if (it <= 0f) off else "${(it * 100).roundToInt()}%" },
                backdrop = backdrop,
                onChange = AudioEffectsPref::previewReverb,
                onCommit = AudioEffectsPref::setReverb,
                onReset = { AudioEffectsPref.setReverb(0f) },
            )

            EffectSlider(
                label = stringResource(R.string.effects_karaoke),
                value = karaoke,
                scale = LinearScale,
                reading = { if (it <= 0f) off else "${(it * 100).roundToInt()}%" },
                backdrop = backdrop,
                onChange = AudioEffectsPref::previewKaraoke,
                onCommit = AudioEffectsPref::setKaraoke,
                onReset = { AudioEffectsPref.setKaraoke(0f) },
            )
            Text(
                stringResource(R.string.effects_karaoke_hint),
                style = MaterialTheme.typography.bodySmall,
                color = GlassInkDim,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                stringResource(R.string.effects_reset),
                style = MaterialTheme.typography.bodySmall,
                color = GlassInk,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { AudioEffectsPref.reset() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // Disabled: the panel is there to read, nothing under the finger answers.
        if (!supported) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }

    if (naming) {
        SavePresetDialog(
            onDismiss = { naming = false },
            onConfirm = {
                AudioEffectsPref.savePreset(it)
                naming = false
            },
        )
    }
}

@Composable
private fun presetName(preset: EffectPreset): String = when (preset.id) {
    ORIGINAL_ID -> stringResource(R.string.effects_preset_original)
    SLOWED_ID -> stringResource(R.string.effects_preset_slowed)
    SPED_UP_ID -> stringResource(R.string.effects_preset_sped_up)
    else -> preset.name
}

/**
 * The preset chips.
 *
 * A custom preset can be deleted from its own chip once it is the selected one, rather than
 * through a separate edit mode: there are only ever a handful of these.
 */
@Composable
private fun PresetRow(
    presets: List<EffectPreset>,
    speed: Float,
    pitch: Float,
    reverb: Float,
    karaoke: Float,
    onApply: (EffectPreset) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCurrent: () -> Unit,
) {
    Text(
        stringResource(R.string.effects_presets),
        style = MaterialTheme.typography.labelLarge,
        color = GlassInkDim,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val rowScroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .then(SideScrollNoBack)
            .horizontalEdgeFade(rowScroll)
            .horizontalScroll(rowScroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val selected = preset.matches(speed, pitch, reverb, karaoke)
            val name = presetName(preset)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.background,
                    )
                    .clickable { onApply(preset) }
                    .padding(start = 14.dp, end = if (selected && !preset.builtIn) 6.dp else 14.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = if (selected) GlassInk else GlassInkDim,
                )
                if (selected && !preset.builtIn) {
                    Icon(
                        PhosphorIcons.Regular.X,
                        contentDescription = stringResource(R.string.effects_delete_preset, name),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onDelete(preset.id) },
                    )
                }
            }
        }

        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onSaveCurrent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                PhosphorIcons.Regular.Plus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.effects_save),
                style = MaterialTheme.typography.bodySmall,
                color = GlassInk,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.effects_preset_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.effects_save_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text(stringResource(R.string.effects_preset_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { defaultName }) }) {
                Text(stringResource(R.string.effects_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/**
 * How a value is laid out along the track.
 *
 * Speed and pitch are ratios, and distance between ratios is multiplicative: half speed and
 * double speed are the same amount of change, one octave either way. Placing by log2 puts normal
 * in the middle, and it is also how the ear hears the difference.
 */
private interface SliderScale {
    val positions: ClosedFloatingPointRange<Float>
    fun position(value: Float): Float
    fun value(position: Float): Float
}

/** Symmetric around 1.0: half at one end, double at the other, normal in the middle. */
private object RatioScale : SliderScale {
    override val positions = -1f..1f
    override fun position(value: Float) = (ln(value.toDouble()) / ln(2.0)).toFloat()
    override fun value(position: Float) = 2f.pow(position)
}

/** For a plain amount, where the number already means what it says. */
private object LinearScale : SliderScale {
    override val positions = 0f..1f
    override fun position(value: Float) = value
    override fun value(position: Float) = position
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    scale: SliderScale,
    /** Given the value under the finger, so the number moves with the thumb. */
    reading: (Float) -> String,
    backdrop: Backdrop,
    /** Every value while the finger moves (the stage follows; nothing is written). */
    onChange: (Float) -> Unit = {},
    /** The last value, when the finger lifts: the engine is told once. */
    onCommit: (Float) -> Unit,
    onReset: () -> Unit,
) {
    // What the thumb shows while the engine has not been told yet: writing every frame starves
    // the audio output, so between the two the slider has to speak for itself.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: value
    LaunchedEffect(value) {
        if (dragged != null && kotlin.math.abs(value - dragged!!) < 0.0005f) dragged = null
    }

    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = GlassInkDim,
                modifier = Modifier.weight(1f),
            )
            Text(reading(shown), style = MaterialTheme.typography.titleMedium)
            // A one-tap way back to unmodified: dragging to exactly 1.0 is fiddly.
            Text(
                stringResource(R.string.effects_reset),
                style = MaterialTheme.typography.bodySmall,
                color = GlassInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        dragged = null
                        onReset()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        LiquidSlider(
            // The track is in position space: the curve lives only in these two conversions.
            value = { scale.position(shown) },
            onValueChange = { position ->
                val v = scale.value(position)
                dragged = v
                onChange(v)
            },
            onValueChangeFinished = {
                dragged?.let(onCommit)
                dragged = null
            },
            valueRange = scale.positions,
            visibilityThreshold = 0.001f,
            backdrop = backdrop,
            accentColor = MaterialTheme.colorScheme.primary,
            trackColor = GlassInk.copy(alpha = 0.22f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
