// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.spicy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.myspoti.engine.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.data.preferences.SpicyBackgroundPref
import com.music.spotui.data.preferences.SpicyBackgroundSettings
import com.music.spotui.data.preferences.SpicyBackgroundType

/** The small round button of the lyrics view's top bar that opens [SpicyBackgroundSheet]. */
@Composable
fun SpicyBackgroundButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val bgDescription = stringResource(R.string.spicy_bg_button)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = bgDescription },
    ) {
        Icon(Icons.Rounded.Palette, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Picks the Spicy Lyrics renderer's background preset and how it moves, and switches the renderer
 * itself off (back to Astral's own lyrics). Every change is stored at once by [SpicyBackgroundPref].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpicyBackgroundSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by SpicyBackgroundPref.state.collectAsState()
    fun change(f: (SpicyBackgroundSettings) -> SpicyBackgroundSettings) = SpicyBackgroundPref.update(context, f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF17171B),
        contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.spicy_bg_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            SheetSwitch(
                stringResource(R.string.spicy_renderer),
                stringResource(R.string.spicy_renderer_detail),
                settings.renderer,
            ) { on -> change { it.copy(renderer = on) } }
            if (settings.renderer) {
                Text(stringResource(R.string.spicy_preset), fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpicyBackgroundType.entries.forEach { type ->
                        val selected = type == settings.type
                        Text(
                            type.label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.Black else Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
                                .clickable { change { it.copy(type = type) } }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                if (settings.type.blurs) {
                    val unit = if (settings.type == SpicyBackgroundType.DEFAULT) "%" else "dp"
                    // Held in the slider while it is dragged, stored once when the finger lifts.
                    var dragging by remember(settings.type) { mutableStateOf<Float?>(null) }
                    val shownBlur = dragging ?: settings.blur.toFloat()
                    Text(stringResource(R.string.spicy_blur, shownBlur.toInt(), unit), fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    Slider(
                        value = shownBlur,
                        onValueChange = { v -> dragging = v },
                        onValueChangeFinished = {
                            dragging?.let { v -> change { it.withBlur(v.toInt()) } }
                            dragging = null
                        },
                        valueRange = 0f..settings.maxBlur.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                    )
                }
                if (settings.type == SpicyBackgroundType.CUSTOM) {
                    Text(stringResource(R.string.spicy_color), fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CUSTOM_COLORS.forEach { argb ->
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        if (argb == settings.customColor) 2.dp else 1.dp,
                                        Color.White.copy(alpha = if (argb == settings.customColor) 1f else 0.25f),
                                        CircleShape,
                                    )
                                    .clickable { change { it.copy(customColor = argb) } },
                            )
                        }
                    }
                }
                if (settings.type.moving) {
                    SheetSwitch(stringResource(R.string.spicy_still), stringResource(R.string.spicy_still_detail), settings.still) { on -> change { it.copy(still = on) } }
                }
                if (settings.type == SpicyBackgroundType.DEFAULT) {
                    SheetSwitch(
                        stringResource(R.string.spicy_beat),
                        stringResource(R.string.spicy_beat_detail),
                        settings.beatReactive,
                    ) { on -> change { it.copy(beatReactive = on) } }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SheetSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White),
        )
    }
}

private val CUSTOM_COLORS = listOf(
    0xFF1B1B2F, 0xFF0B3D4F, 0xFF1F4D2B, 0xFF5A1F2B, 0xFF4A2A6B, 0xFF6B3A12, 0xFF2B2B2B, 0xFF000000,
).map { it.toInt() }
