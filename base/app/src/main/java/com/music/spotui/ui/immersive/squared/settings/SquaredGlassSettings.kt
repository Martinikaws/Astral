/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/settings/GlassSettings.kt. Modified for Astral, 2026-11.
 *
 * Changes: the glass is clear here, so the blur ("Frost") slider, the frost switch and the
 * "iOS 27" switch are gone and the refraction switch is a plain one; the preview bar has this
 * look's three tabs (Home, Search, Library) rather than Square's five; the settings go to
 * SquaredGlassStore, which Astral's Squared shell reads; the rows are the shared ones of
 * SquaredSettingsParts; Square's "glass unsupported" note is replaced by one that says what
 * phones below Android 13 get (a tinted film, nothing sampled).
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myspoti.engine.R
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.House
import com.adamglin.phosphoricons.fill.MagnifyingGlass
import com.adamglin.phosphoricons.fill.MusicNotes
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.DeviceMobile
import com.adamglin.phosphoricons.regular.Drop
import com.adamglin.phosphoricons.regular.Highlighter
import com.adamglin.phosphoricons.regular.MagicWand
import com.adamglin.phosphoricons.regular.MusicNotes
import com.adamglin.phosphoricons.regular.Palette
import com.adamglin.phosphoricons.regular.Rows
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.Stack
import com.adamglin.phosphoricons.regular.Sun
import com.adamglin.phosphoricons.regular.Swatches
import com.adamglin.phosphoricons.regular.Waves
import com.music.spotui.ui.immersive.squared.Ink
import com.music.spotui.ui.immersive.squared.InkDim
import com.music.spotui.ui.immersive.squared.glass.GlassEffectConfig
import com.music.spotui.ui.immersive.squared.glass.GlassStyle
import com.music.spotui.ui.immersive.squared.glass.LENS_MAX_DP
import com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.layerBackdrop
import com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops.rememberLayerBackdrop
import com.music.spotui.ui.immersive.squared.glass.liquidGlass
import com.music.spotui.ui.immersive.squared.glass.shapes.ContinuousCapsule
import kotlin.math.roundToInt

/**
 * The glass page: the preview above, pinned, and its settings under it.
 *
 * The preview stays on screen because the numbers being moved are for what it shows. The settings
 * are in the material's own groups: almost nobody wants to choose a refraction amount, and what
 * the person who does came for is usually one group of it. Everything is live, and the glass is
 * clear: nothing here frosts or blurs what is behind it.
 */
@Composable
fun SquaredGlassPage(store: SquaredGlassStore, contentPadding: PaddingValues, header: @Composable () -> Unit) {
    val profile by store.profile.collectAsStateWithLifecycle()
    val config by store.config.collectAsStateWithLifecycle()
    val barFolds by store.barFolds.collectAsStateWithLifecycle()
    val liquid = config.style != GlassStyle.TRANSPARENT

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        header()

        // Below Android 13 the refraction shader cannot run: the glass is the tinted film the
        // fastest profile picks, and the lens settings would move nothing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Text(
                stringResource(R.string.squared_glass_film_only),
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        GlassPreview(config = config)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            item("material") {
                SettingsSection(stringResource(R.string.squared_glass_group_material)) {
                    SquaredGlassProfile.entries.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        SettingsChoiceRow(
                            label = stringResource(entry.label),
                            detail = stringResource(entry.note),
                            selected = entry == profile,
                        ) { store.setProfile(entry) }
                    }
                    if (liquid) {
                        SettingsDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.squared_glass_refraction),
                            detail = stringResource(R.string.squared_glass_refraction_note),
                            icon = PhosphorIcons.Regular.Sparkle,
                            checked = config.refraction,
                            onChange = store::setRefraction,
                        )
                    }
                    SettingsDivider()
                    SettingsSliderRow(
                        label = stringResource(R.string.squared_glass_vibrancy),
                        icon = PhosphorIcons.Regular.Drop,
                        value = config.vibrancy,
                        range = 0f..2f,
                        readout = "${(config.vibrancy * 50).roundToInt()}%",
                        onFinished = store::commit,
                        onChange = store::setVibrancy,
                    )
                }
            }

            item("colour") {
                SettingsSection(stringResource(R.string.squared_glass_group_colour)) {
                    TintRow(
                        hue = config.tintHue,
                        strength = config.tintStrength,
                        fromSystem = store.tintFromSystem,
                        onHue = store::setTintHue,
                        onSystem = store::setTintFromSystem,
                        onStrength = store::setTintStrength,
                        onStrengthFinished = store::commit,
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = stringResource(R.string.squared_glass_film),
                        icon = PhosphorIcons.Regular.Swatches,
                        value = config.surfaceOpacity,
                        range = 0f..1f,
                        readout = "${(config.surfaceOpacity * 100).roundToInt()}%",
                        onFinished = store::commit,
                        onChange = store::setSurfaceOpacity,
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = stringResource(R.string.squared_glass_indicator),
                        icon = PhosphorIcons.Regular.Highlighter,
                        value = config.puckOpacity,
                        range = 0f..1f,
                        readout = "${(config.puckOpacity * 100).roundToInt()}%",
                        onFinished = store::commit,
                        onChange = store::setPuckOpacity,
                    )
                }
            }

            if (liquid) item("edges") {
                SettingsSection(stringResource(R.string.squared_glass_group_edges)) {
                    SettingsSliderRow(
                        label = stringResource(R.string.squared_glass_rim),
                        icon = PhosphorIcons.Regular.Sparkle,
                        value = config.highlightOpacity,
                        range = 0f..1f,
                        readout = "${(config.highlightOpacity * 100).roundToInt()}%",
                        onFinished = store::commit,
                        onChange = store::setHighlightOpacity,
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = stringResource(R.string.squared_glass_refraction_depth),
                        icon = PhosphorIcons.Regular.Waves,
                        value = config.lensHeight,
                        range = 0f..1f,
                        readout = "${(config.lensHeight * LENS_MAX_DP).roundToInt()} dp",
                        onFinished = store::commit,
                        onChange = store::setLensHeight,
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = stringResource(R.string.squared_glass_refraction_amount),
                        icon = PhosphorIcons.Regular.MagicWand,
                        value = config.lensAmount,
                        range = 0f..1f,
                        readout = "${(config.lensAmount * LENS_MAX_DP).roundToInt()} dp",
                        onFinished = store::commit,
                        onChange = store::setLensAmount,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.squared_glass_dispersion),
                        icon = PhosphorIcons.Regular.Sun,
                        checked = config.chromaticAberration,
                        onChange = store::setChromaticAberration,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.squared_glass_depth),
                        icon = PhosphorIcons.Regular.Stack,
                        checked = config.depthEffect,
                        onChange = store::setDepthEffect,
                    )
                }
            }

            // Where the glass is allowed to be: the blunt instrument, kept last. The player's is by
            // far the largest surface in the app, so turning it off saves more than any slider.
            item("where") {
                SettingsSection(stringResource(R.string.squared_glass_group_where)) {
                    SettingsSwitchRow(
                        label = stringResource(R.string.squared_glass_where_bar),
                        icon = PhosphorIcons.Regular.Rows,
                        checked = config.navBarEnabled,
                        onChange = store::setNavBarEnabled,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.squared_glass_where_mini_player),
                        icon = PhosphorIcons.Regular.MusicNotes,
                        checked = config.miniPlayerEnabled,
                        onChange = store::setMiniPlayerEnabled,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.squared_glass_where_player),
                        icon = PhosphorIcons.Regular.DeviceMobile,
                        checked = config.playerEnabled,
                        onChange = store::setPlayerEnabled,
                    )
                    SettingsDivider()
                    // The fold is the app's most expensive animation: a shared element travelling,
                    // a gooey layer, and glass whose bounds change on every frame of it.
                    SettingsSwitchRow(
                        label = stringResource(R.string.squared_glass_bar_folds),
                        detail = stringResource(R.string.squared_glass_bar_folds_note),
                        icon = PhosphorIcons.Regular.Rows,
                        checked = barFolds,
                        onChange = store::setBarFolds,
                    )
                }
            }

            item("reset") {
                SettingsSection(null) {
                    SettingsActionRow(
                        label = stringResource(R.string.squared_glass_reset),
                        icon = PhosphorIcons.Regular.ArrowCounterClockwise,
                        enabled = store.customised,
                    ) { store.reset() }
                }
            }
        }
    }
}

/**
 * A bar that does nothing, so you can see what you are doing: the same shapes made of the same
 * material (the capsule, the selected tab's wash, the playing pill above it), with nothing behind
 * them but a band of colour and hard edges of its own, and no behaviour at all.
 */
@Composable
private fun GlassPreview(config: GlassEffectConfig) {
    val barConfig = if (config.navBarEnabled) config else config.copy(style = GlassStyle.TRANSPARENT)
    // Its own backdrop is the whole reason the preview works: glass over an even surface is an
    // even surface, and refraction and dispersion only show against edges and colour. The band is
    // recorded into a layer the shapes below sample instead of the page.
    val stage = rememberLayerBackdrop()
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .layerBackdrop(stage)
                .background(Brush.linearGradient(listOf(accent, Color(0xFFFF7A45), Color(0xFF2ED3C6), Color(0xFF1B1B1B)))),
        ) {
            // Hard edges, because a gradient alone bends invisibly: what shows the lens is a
            // straight line going crooked as it passes the rim.
            Column(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(6) { index ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.94f else 0.7f)
                            .height(5.dp)
                            .background(if (index % 2 == 0) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f)),
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The playing pill, at the size the real one is.
            Row(
                Modifier
                    .fillMaxWidth(0.86f)
                    .height(46.dp)
                    .liquidGlass(
                        config = if (config.miniPlayerEnabled) config else config.copy(style = GlassStyle.TRANSPARENT),
                        shape = ContinuousCapsule(),
                        highlightAlpha = 0.3f,
                        ownBackdrop = stage,
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Ink.copy(alpha = 0.22f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(7.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.55f)))
                    Box(Modifier.fillMaxWidth(0.35f).height(6.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.28f)))
                }
                Icon(PhosphorIcons.Fill.Play, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            }

            // The bar as it is: three places across one capsule, the first lit.
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(0.86f)
                    .liquidGlass(
                        config = barConfig,
                        shape = ContinuousCapsule(),
                        highlightAlpha = 0.3f,
                        ownBackdrop = stage,
                    )
                    .padding(vertical = 5.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewTab(PhosphorIcons.Fill.House, R.string.squared_tab_home, selected = true, config = barConfig)
                PreviewTab(PhosphorIcons.Fill.MagnifyingGlass, R.string.squared_tab_search, selected = false, config = barConfig)
                PreviewTab(PhosphorIcons.Fill.MusicNotes, R.string.squared_tab_library, selected = false, config = barConfig)
            }
        }
    }
}

/** One tab of the dummy bar, with the selection wash the real puck draws. */
@Composable
private fun RowScope.PreviewTab(icon: ImageVector, label: Int, selected: Boolean, config: GlassEffectConfig) {
    // The shell's puck is a dark wash at the listener's opacity.
    val wash = Color(0.131f, 0.131f, 0.131f)
    Box(
        Modifier
            .weight(1f, fill = true)
            .height(44.dp)
            .then(
                if (selected) {
                    Modifier.clip(ContinuousCapsule()).background(wash.copy(alpha = config.puckOpacity.coerceIn(0f, 1f)))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Icon(icon, contentDescription = null, tint = if (selected) Ink else InkDim, modifier = Modifier.size(19.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Ink else InkDim,
                maxLines = 1,
            )
        }
    }
}

/**
 * The glass's own colour, picked off a strip of every hue there is: any colour fits in the height
 * of one row, where a palette would hold eight and still answer "not that one". The word on the
 * right is the way back to no colour, and the strength under it only appears once there is a
 * colour to weaken.
 */
@Composable
private fun TintRow(
    hue: Float,
    strength: Float,
    fromSystem: Boolean,
    onHue: (Float?) -> Unit,
    onSystem: () -> Unit,
    onStrength: (Float) -> Unit,
    onStrengthFinished: () -> Unit,
) {
    val on = hue >= 0f
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RowIcon(PhosphorIcons.Regular.Palette)
            Text(stringResource(R.string.squared_glass_tint), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (on) R.string.squared_glass_tint_clear else R.string.squared_glass_tint_none),
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) settingsAccent() else InkDim,
                modifier = Modifier
                    .clip(ContinuousCapsule())
                    .clickable(enabled = on) { onHue(null) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // What the strip shows while a finger is on it. Every write rebuilds the material and
        // redraws every pane of glass on the page, and a drag reports a position per frame: the
        // thumb follows the finger from here, and the setting is written at most every
        // [TINT_WRITE_MS] and again when the finger lifts.
        var dragHue by remember { mutableStateOf<Float?>(null) }
        var lastWrite by remember { mutableLongStateOf(0L) }
        val shown = dragHue ?: hue
        val hues = remember {
            List(HUE_STOPS) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 360f / (HUE_STOPS - 1), 0.8f, 1f))) }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
                .height(30.dp),
        ) {
            val width = constraints.maxWidth.toFloat()
            val pick = { x: Float, settled: Boolean ->
                val picked = (x / width).coerceIn(0f, 1f) * 360f
                dragHue = picked
                val now = android.os.SystemClock.uptimeMillis()
                if (settled || now - lastWrite >= TINT_WRITE_MS) {
                    lastWrite = now
                    onHue(picked)
                }
                if (settled) dragHue = null
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .align(Alignment.CenterStart)
                    .clip(ContinuousCapsule())
                    .background(Brush.horizontalGradient(hues))
                    .pointerInput(width) { detectTapGestures { pick(it.x, true) } }
                    .pointerInput(width) {
                        detectHorizontalDragGestures(
                            onDragEnd = { dragHue?.let { pick(width * (it / 360f), true) } },
                            onDragCancel = { dragHue = null },
                        ) { change, _ -> pick(change.position.x, false) }
                    },
            )
            if (on || dragHue != null) {
                val x = with(LocalDensity.current) { (width * (shown / 360f)).toDp() }
                Box(
                    Modifier
                        .offset(x = x - 11.dp)
                        .size(22.dp)
                        .clip(ContinuousCapsule())
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(ContinuousCapsule())
                        .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(shown, 0.8f, 1f))))
                        .align(Alignment.CenterStart),
                )
            }
        }
        // The handful worth a tap, and the phone's own accent beside them: the strip has every
        // colour, but nobody drags to find plain red.
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val system = Color(LocalContext.current.getColor(android.R.color.system_accent1_400))
                Swatch(colour = system, selected = fromSystem, onClick = onSystem)
            }
            PRESET_HUES.forEach { preset ->
                Swatch(
                    colour = Color(android.graphics.Color.HSVToColor(floatArrayOf(preset, 0.8f, 1f))),
                    selected = !fromSystem && on && kotlin.math.abs(hue - preset) < 1f,
                    onClick = { onHue(preset) },
                )
            }
        }
        if (on) {
            Column(Modifier.padding(top = 10.dp)) {
                Row {
                    Text(stringResource(R.string.squared_glass_tint_strength), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${(strength * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = InkDim)
                }
                PlainSlider(value = strength, range = 0f..1f, onChange = onStrength, onFinished = onStrengthFinished, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** One colour to tap, lit while it is the one in use. */
@Composable
private fun Swatch(colour: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(ContinuousCapsule())
            .background(if (selected) Ink else Color.Transparent)
            .padding(if (selected) 3.dp else 0.dp)
            .clip(ContinuousCapsule())
            .background(colour)
            .clickable(onClick = onClick),
    )
}

/**
 * How often a drag along the strip is allowed to write the setting: every write rebuilds the
 * material, and this page is made of it. At sixteen writes a second the main thread never caught
 * up in Square. The thumb follows the finger at full speed from state that costs nothing.
 */
private const val TINT_WRITE_MS = 250L

/** Stops in the hue strip: enough that the gradient has no visible banding. */
private const val HUE_STOPS = 13

/** Red, orange, yellow, green, cyan, blue, violet, magenta. */
private val PRESET_HUES = listOf(0f, 32f, 52f, 130f, 186f, 220f, 268f, 310f)
