/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt and GlassSettings.kt
 * (the rows, the folding groups, the plain slider and switch). Modified for Astral, 2026-11.
 *
 * Changes: the row shapes both of Square's files shared are one file here, public to this package
 * rather than copied; the accent follows the glass store of this app; the group list is Astral's
 * (sound, listening, lyrics, covers, downloads, cleanup, language, appearance, system); the
 * back button and the tiles are the same shapes with Astral's strings.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.settings

import androidx.annotation.StringRes
import app.myspoti.engine.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Broom
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.FilmStrip
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.MicrophoneStage
import com.adamglin.phosphoricons.regular.Palette
import com.adamglin.phosphoricons.regular.Playlist
import com.adamglin.phosphoricons.regular.Translate
import com.adamglin.phosphoricons.regular.Waveform
import com.music.spotui.ui.immersive.squared.Ink
import com.music.spotui.ui.immersive.squared.InkDim
import com.music.spotui.ui.immersive.squared.glass.GlassEffectConfig
import com.music.spotui.ui.immersive.squared.glass.shapes.ContinuousCapsule
import com.music.spotui.ui.immersive.squared.softShadow

/** Where a row's words begin, icon and its gap included. */
internal val ROW_TEXT_START = 60.dp

/**
 * What the settings are marked in: the glass's colour where it has been given one, and the look's
 * own accent otherwise. Somebody who has just made every pane of the app cyan does not want the
 * page that did it marked in green.
 */
internal val LocalSettingsAccent = compositionLocalOf<Color?> { null }

@Composable
internal fun settingsAccent(): Color = LocalSettingsAccent.current ?: MaterialTheme.colorScheme.primary

/** The colour [LocalSettingsAccent] carries, worked out from the glass and the theme once for a page. */
@Composable
internal fun rememberSettingsAccent(config: GlassEffectConfig): Color {
    val accent = MaterialTheme.colorScheme.primary
    if (config.tintHue < 0f) return accent
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val strength = config.tintStrength.coerceIn(0f, 1f)
    val saturation = (hsv[1] + (ACCENT_SATURATION - hsv[1]) * strength).coerceIn(0f, 1f)
    return Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(config.tintHue, saturation, hsv[2].coerceAtLeast(ACCENT_MIN_VALUE)),
        ),
    )
}

/** How coloured a tinted mark goes, and how dark it is allowed to be. */
private const val ACCENT_SATURATION = 0.7f
private const val ACCENT_MIN_VALUE = 0.75f

/** A titled card of rows. The title is left off when a group of the same name has said it. */
@Composable
internal fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    val group = LocalGroupTitle.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        if (title != null && !title.equals(group, ignoreCase = true)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = InkDim,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Ink.copy(alpha = 0.07f)),
            content = { content() },
        )
    }
}

/** Indented to where the words start, not to the card's edge: a line under the icons cuts their column in half. */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(color = Ink.copy(alpha = 0.06f), modifier = Modifier.padding(start = ROW_TEXT_START, end = 18.dp))
}

/** The mark at the head of a row: a glyph in a tinted tile gives each row something to recognise it by. */
@Composable
internal fun RowIcon(icon: ImageVector) {
    val accent = settingsAccent()
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
    }
}

/** A row of a list where one is picked, with a tick on the one that is. */
@Composable
internal fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    detail: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { RowIcon(it) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) Ink else InkDim)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = InkDim)
        }
        if (selected) {
            Icon(
                PhosphorIcons.Regular.Check,
                contentDescription = null,
                tint = settingsAccent(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A label, an optional line under it, and a switch. */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    detail: String? = null,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { RowIcon(it) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = InkDim)
        }
        PlainToggle(checked = checked, onChange = onChange)
    }
}

/** A row that does something when tapped; [destructive] draws it in the error colour. */
@Composable
internal fun SettingsActionRow(
    label: String,
    detail: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { RowIcon(it) }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else Ink,
            )
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = InkDim)
        }
    }
}

/** A label with what it is now, on the right. */
@Composable
internal fun SettingsInfoRow(label: String, value: String, icon: ImageVector? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { RowIcon(it) }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = InkDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A label, what the slider is at, and the slider. */
@Composable
internal fun SettingsSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    icon: ImageVector? = null,
    onFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon?.let { RowIcon(it) }
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(readout, style = MaterialTheme.typography.bodyMedium, color = InkDim)
        }
        // Drawn rather than made of glass: a settings list is a dozen controls at once, and the
        // glass ones each record a layer of the screen. One surface in the preview shows what the
        // numbers do.
        PlainSlider(value = value, range = range, onChange = onChange, onFinished = onFinished, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
    }
}

/** A slider that costs nothing to draw: a track, a fill and a knob. */
@Composable
internal fun PlainSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onFinished: (() -> Unit)? = null,
) {
    val accent = settingsAccent()
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    BoxWithConstraints(modifier.fillMaxWidth().height(28.dp)) {
        val width = constraints.maxWidth.toFloat()
        val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
        val pick = { x: Float -> onChange(range.start + (x / width).coerceIn(0f, 1f) * span) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.CenterStart)
                .clip(ContinuousCapsule())
                .background(Ink.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .align(Alignment.CenterStart)
                .clip(ContinuousCapsule())
                .background(accent),
        )
        Box(
            Modifier
                .offset(x = with(LocalDensity.current) { (width * fraction).toDp() } - 11.dp)
                .size(22.dp)
                .align(Alignment.CenterStart)
                .softShadow(ContinuousCapsule(), elevation = 6.dp)
                .clip(ContinuousCapsule())
                .background(Color.White),
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(width, range) { detectTapGestures { pick(it.x); onFinished?.invoke() } }
                .pointerInput(width, range) {
                    detectHorizontalDragGestures(onDragEnd = { onFinished?.invoke() }) { change, _ -> pick(change.position.x) }
                },
        )
    }
}

/** The same trade as [PlainSlider], for a switch. */
@Composable
internal fun PlainToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = settingsAccent()
    val knob by animateDpAsState(if (checked) 22.dp else 2.dp, label = "toggleKnob")
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(ContinuousCapsule())
            .background(if (checked) accent else Ink.copy(alpha = 0.22f))
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .softShadow(ContinuousCapsule(), elevation = 4.dp)
                .clip(ContinuousCapsule())
                .background(Color.White),
        )
    }
}

/**
 * The groups a settings page is folded into, each under a heading that opens it: a page reads as a
 * short list of what is on it rather than a long run of sections.
 */
internal enum class SettingsGroup(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
) {
    SOUND(R.string.squared_group_sound, R.string.squared_group_sound_summary, PhosphorIcons.Regular.Waveform),
    LISTENING(R.string.squared_group_listening, R.string.squared_group_listening_summary, PhosphorIcons.Regular.Playlist),
    LYRICS(R.string.squared_group_lyrics, R.string.squared_group_lyrics_summary, PhosphorIcons.Regular.MicrophoneStage),
    COVERS(R.string.squared_group_covers, R.string.squared_group_covers_summary, PhosphorIcons.Regular.FilmStrip),
    DOWNLOADS(R.string.squared_group_downloads, R.string.squared_group_downloads_summary, PhosphorIcons.Regular.DownloadSimple),
    CLEANUP(R.string.squared_group_cleanup, R.string.squared_group_cleanup_summary, PhosphorIcons.Regular.Broom),
    LANGUAGE(R.string.squared_group_language, R.string.squared_group_language_summary, PhosphorIcons.Regular.Translate),
    APPEARANCE(R.string.squared_group_appearance, R.string.squared_group_appearance_summary, PhosphorIcons.Regular.Palette),
    SYSTEM(R.string.squared_group_system, R.string.squared_group_system_summary, PhosphorIcons.Regular.GearSix),
}

/** The group a section is drawn in, whose heading it then does not repeat; see [SettingsSection]. */
internal val LocalGroupTitle = compositionLocalOf<String?> { null }

private val GROUP_MARGIN = 10.dp
private val GROUP_CORNER = 28.dp
private const val GROUP_MOTION_MS = 220

/** The ground of a group's panel: under the sections' own cards, which stand out on it. */
@Composable
private fun groupPanel(): Color = Ink.copy(alpha = 0.045f)

/** A group's heading, and the top of the panel its sections sit in when it is open. */
internal fun LazyListScope.groupHeading(group: SettingsGroup, open: Boolean, onToggle: () -> Unit) =
    item("group-${group.name}") {
        val turn by animateFloatAsState(if (open) 180f else 0f, tween(GROUP_MOTION_MS), label = "groupCaret")
        val lower by animateDpAsState(if (open) 0.dp else GROUP_CORNER, tween(GROUP_MOTION_MS), label = "groupCorner")
        val gap by animateDpAsState(if (open) 0.dp else 6.dp, tween(GROUP_MOTION_MS), label = "groupGap")
        Row(
            Modifier
                .animateItem(fadeInSpec = tween(GROUP_MOTION_MS), fadeOutSpec = tween(150))
                .fillMaxWidth()
                .padding(start = GROUP_MARGIN, end = GROUP_MARGIN, top = 6.dp, bottom = gap)
                .clip(RoundedCornerShape(GROUP_CORNER, GROUP_CORNER, lower, lower))
                .background(groupPanel())
                .clickable(onClick = onToggle)
                .padding(start = 18.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupMark(group.icon)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(group.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(group.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                PhosphorIcons.Regular.CaretDown,
                contentDescription = null,
                tint = InkDim,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            )
        }
    }

/** The foot of an open group's panel, under its last section. */
internal fun LazyListScope.groupEnd(group: SettingsGroup) =
    item("group-end-${group.name}") {
        Box(
            Modifier
                .animateItem(fadeInSpec = tween(GROUP_MOTION_MS), fadeOutSpec = tween(150))
                .fillMaxWidth()
                .padding(start = GROUP_MARGIN, end = GROUP_MARGIN, bottom = 6.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(0.dp, 0.dp, GROUP_CORNER, GROUP_CORNER))
                .background(groupPanel()),
        )
    }

/** One of a group's sections, fading in as the group opens and out as it closes. */
internal fun LazyListScope.groupItem(key: String, group: SettingsGroup, content: @Composable () -> Unit) =
    item(key) {
        val title = stringResource(group.title)
        // A column, not a box: some items are more than one section, and a box drew them on top of
        // each other.
        Column(
            Modifier
                .animateItem(fadeInSpec = tween(GROUP_MOTION_MS), fadeOutSpec = tween(150))
                .padding(horizontal = GROUP_MARGIN)
                .background(groupPanel()),
        ) {
            CompositionLocalProvider(LocalGroupTitle provides title) { content() }
        }
    }

/** A group's mark: larger than a setting's and filled with the accent, the one place the colour is spent. */
@Composable
private fun GroupMark(icon: ImageVector) {
    val accent = settingsAccent()
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accent.copy(alpha = 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}
