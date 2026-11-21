/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/settings/EqualizerSettings.kt. Modified for Astral, 2026-11.
 *
 * Changes: the curve and its presets are Astral's (ten bands, +-12 dB, EqPreset) kept in
 * EqualizerPref, which keeps one curve per output (see EqCurveBook) instead of Square's single
 * store; the page edits the output in use by default and any output that has been seen can be
 * picked from the list at the foot; the band columns, the preamp and the preset chips are
 * drawn with the shared rows of SquaredSettingsParts; the labels are Astral's existing strings.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.BluetoothConnected
import com.adamglin.phosphoricons.regular.Headphones
import com.adamglin.phosphoricons.regular.Plugs
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.Trash
import com.music.spotui.data.preferences.EqPreset
import com.music.spotui.data.preferences.EqualizerPref
import com.music.spotui.di.OutputRoute
import com.music.spotui.di.EqBands
import com.music.spotui.ui.immersive.squared.Ink
import com.music.spotui.ui.immersive.squared.InkDim
import com.music.spotui.ui.screens.eqBandLabel
import com.music.spotui.ui.screens.eqPresetLabel
import kotlin.math.roundToInt

/**
 * The equalizer page: one curve for each output. What is edited is the output in use until another
 * is picked from the list at the foot; an output is given a curve of its own the first time it is
 * edited, and the output in use is always the one that is heard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SquaredEqualizerPage(contentPadding: PaddingValues, header: @Composable () -> Unit) {
    val context = LocalContext.current
    val inUse = EqualizerPref.routeKey
    var picked by remember { mutableStateOf<String?>(null) }
    // An output that is no longer listed cannot stay picked.
    val key = picked?.takeIf { it in EqualizerPref.knownOutputs() } ?: inUse
    val curve = EqualizerPref.curveFor(key)
    val gains = curve.gains
    var naming by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        header()

        Text(
            stringResource(R.string.equalizer_scope),
            style = MaterialTheme.typography.bodySmall,
            color = InkDim,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        SettingsSection(null) {
            SettingsSwitchRow(
                label = outputLabel(key),
                detail = stringResource(
                    if (key == inUse) R.string.squared_eq_output_in_use else R.string.squared_eq_output_not_in_use,
                ),
                icon = outputIcon(key),
                checked = curve.enabled,
                onChange = { EqualizerPref.setEnabled(context, key, it) },
            )
        }

        SettingsSection(stringResource(R.string.equalizer_bands)) {
            CurveEditor(
                gains = gains,
                enabled = curve.enabled,
                onGain = { band, db -> EqualizerPref.setGain(context, key, band, db, save = false) },
                onFinished = { EqualizerPref.save(context) },
            )
            SettingsDivider()
            SettingsSliderRow(
                label = stringResource(R.string.equalizer_preamp),
                value = curve.preamp,
                range = EqBands.PREAMP_MIN_DB..EqBands.PREAMP_MAX_DB,
                readout = stringResource(R.string.equalizer_db, signedDb(curve.preamp)),
                onFinished = { EqualizerPref.save(context) },
                onChange = { EqualizerPref.setPreamp(context, key, it, save = false) },
            )
            SettingsDivider()
            SettingsActionRow(
                label = stringResource(R.string.equalizer_reset),
                icon = PhosphorIcons.Regular.ArrowCounterClockwise,
                enabled = !curve.isFlat,
            ) { EqualizerPref.reset(context, key) }
        }

        SettingsSection(stringResource(R.string.equalizer_preset)) {
            FlowRow(
                Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPreset.entries.filter { it != EqPreset.CUSTOM }.forEach { preset ->
                    Chip(stringResource(eqPresetLabel(preset)), selected = curve.preset == preset) {
                        EqualizerPref.setPreset(context, key, preset)
                    }
                }
                EqualizerPref.userPresets.forEach { mine ->
                    Chip(
                        mine.name,
                        selected = curve.preset == EqPreset.CUSTOM && curve.matches(mine.gains),
                        trailing = {
                            Icon(
                                PhosphorIcons.Regular.Trash,
                                contentDescription = stringResource(R.string.squared_eq_delete_preset),
                                tint = InkDim,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { EqualizerPref.deletePreset(context, mine.id) },
                            )
                        },
                    ) { EqualizerPref.applyCurve(context, key, mine.gains, mine.preamp) }
                }
            }
            SettingsDivider()
            SettingsActionRow(
                label = stringResource(R.string.squared_eq_save_preset),
                enabled = !curve.isFlat,
            ) { naming = true }
        }

        val outputs = EqualizerPref.knownOutputs()
        if (outputs.size > 1) {
            SettingsSection(stringResource(R.string.squared_eq_outputs)) {
                outputs.forEachIndexed { index, out ->
                    if (index > 0) SettingsDivider()
                    SettingsChoiceRow(
                        label = outputLabel(out),
                        detail = stringResource(
                            if (out == inUse) R.string.squared_eq_output_in_use else R.string.squared_eq_output_not_in_use,
                        ),
                        icon = outputIcon(out),
                        selected = out == key,
                    ) { picked = out }
                }
            }
        }
    }

    if (naming) NameDialog(
        onDismiss = { naming = false },
        onSave = { name ->
            naming = false
            EqualizerPref.savePreset(context, name, gains, curve.preamp)
        },
    )
}

/** The name of an output: what it was called when last seen, or the kind of output it is. */
@Composable
internal fun outputLabel(key: String): String {
    EqualizerPref.names[key]?.takeIf { it.isNotBlank() }?.let { return it }
    return stringResource(
        when (OutputRoute.kindOf(key)) {
            OutputRoute.Kind.SPEAKER -> R.string.squared_eq_kind_speaker
            OutputRoute.Kind.WIRED -> R.string.squared_eq_kind_wired
            OutputRoute.Kind.BLUETOOTH -> R.string.squared_eq_kind_bluetooth
            OutputRoute.Kind.USB -> R.string.squared_eq_kind_usb
            OutputRoute.Kind.OTHER -> R.string.squared_eq_kind_other
        },
    )
}

private fun outputIcon(key: String) = when (OutputRoute.kindOf(key)) {
    OutputRoute.Kind.SPEAKER -> PhosphorIcons.Regular.SpeakerHigh
    OutputRoute.Kind.WIRED -> PhosphorIcons.Regular.Headphones
    OutputRoute.Kind.BLUETOOTH -> PhosphorIcons.Regular.BluetoothConnected
    OutputRoute.Kind.USB -> PhosphorIcons.Regular.Plugs
    OutputRoute.Kind.OTHER -> PhosphorIcons.Regular.Plugs
}

private fun signedDb(db: Float): String {
    val v = (db * 10).roundToInt() / 10f
    val text = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
    return if (v > 0f) "+$text" else text
}

@Composable
private fun Chip(label: String, selected: Boolean, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val accent = settingsAccent()
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.28f) else Ink.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink else InkDim)
        trailing?.invoke()
    }
}

/**
 * Ten columns, one for each band, each dragged up and down, with the curve they make drawn through
 * their knobs. Heard as it moves; kept when the finger lifts.
 */
@Composable
private fun CurveEditor(
    gains: List<Float>,
    enabled: Boolean,
    onGain: (Int, Float) -> Unit,
    onFinished: () -> Unit,
) {
    // The drag handlers outlive a recomposition; they must call the latest lambdas, which are
    // bound to the output being edited.
    val currentGain by rememberUpdatedState(onGain)
    val currentFinished by rememberUpdatedState(onFinished)
    val accent = settingsAccent()
    val alpha = if (enabled) 1f else 0.45f
    val ink = Ink
    Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            Row(Modifier.fillMaxSize()) {
                for (band in 0 until EqBands.COUNT) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .pointerInput(band) {
                                fun at(y: Float) = EqBands.MAX_DB - (y / size.height).coerceIn(0f, 1f) * (EqBands.MAX_DB - EqBands.MIN_DB)
                                detectDragGestures(
                                    onDragStart = { currentGain(band, snap(at(it.y))) },
                                    onDragEnd = { currentFinished() },
                                    onDragCancel = { currentFinished() },
                                ) { change, _ -> currentGain(band, snap(at(change.position.y))) }
                            },
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val colW = size.width / EqBands.COUNT
                val span = EqBands.MAX_DB - EqBands.MIN_DB
                fun y(db: Float) = (EqBands.MAX_DB - db) / span * size.height
                // The 0 dB line and a hairline under each band.
                drawLine(ink.copy(alpha = 0.25f), Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.5f)
                val points = gains.mapIndexed { i, g -> Offset(colW * (i + 0.5f), y(g)) }
                points.forEach { p ->
                    drawLine(ink.copy(alpha = 0.10f * alpha), Offset(p.x, 0f), Offset(p.x, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                // Catmull-Rom through the knobs, drawn as cubic segments.
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[(i - 1).coerceAtLeast(0)]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
                        cubicTo(
                            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                            p2.x, p2.y,
                        )
                    }
                }
                drawPath(path, accent.copy(alpha = alpha), style = Stroke(width = 5f, cap = StrokeCap.Round))
                points.forEach { p ->
                    drawCircle(Color.White.copy(alpha = alpha), radius = 11f, center = p)
                    drawCircle(accent.copy(alpha = alpha), radius = 6f, center = p)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (band in 0 until EqBands.COUNT) {
                Text(
                    eqBandLabel(band),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = InkDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Half a decibel is as fine as a finger can mean. */
private fun snap(db: Float): Float = (db * 2f).roundToInt() / 2f

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.squared_eq_save_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text(stringResource(R.string.squared_eq_preset_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.squared_eq_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
