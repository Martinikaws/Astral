/*
 * SPDX-License-Identifier: GPL-3.0-only
 * EngineCard and SetupAurora adapted from Square 2.11.0-beta1 (Lelonio/Square, soloist-android),
 * app/src/main/java/dev/lelonio/square/ui/onboarding/{OnboardingScreen,SetupPieces}.kt.
 * Copyright (C) Lelonio. Modified for Astral: accessible selection and Astral theme.
 */
package com.music.spotui.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.Circle
import com.music.spotui.data.preferences.Look
import com.music.spotui.ui.components.SpotifyReceiver
import com.music.spotui.ui.components.ReceiverSetupDialog
import com.music.spotui.ui.immersive.squared.Ink
import com.music.spotui.ui.immersive.squared.InkDim
import com.music.spotui.ui.immersive.squared.SquaredTheme
import com.music.spotui.ui.look.LookDemo
import com.music.spotui.ui.look.PhoneFrame
import com.music.spotui.ui.look.rememberStillClock

@Composable
internal fun FirstLookScreen(onChoose: (Look) -> Unit) {
    val clock = rememberStillClock(0)
    SquaredTheme(seed = null, darkTheme = true) {
        SetupLayout {
            Text(stringResource(R.string.first_look_title), style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((look, label) in listOf(Look.CLASSIC to R.string.first_look_astral, Look.SQUARED to R.string.first_look_square)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                            .background(Ink.copy(alpha = 0.08f))
                            .clickable(role = Role.Button) { onChoose(look) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PhoneFrame(Modifier.fillMaxWidth().height(230.dp)) { LookDemo(look, clock, still = true) }
                        Text(
                            stringResource(label),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SquarePlaybackSetup(
    spotifySignIn: () -> Unit,
    musicAccount: () -> Unit,
    back: () -> Unit,
) {
    var receiver by rememberSaveable { mutableStateOf<SpotifyReceiver?>(null) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = back)

    SquaredTheme(seed = null, darkTheme = true) {
        SetupLayout(aurora = true) {
            Text(stringResource(R.string.square_setup_title), style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (option in SpotifyReceiver.entries) {
                    EngineCard(
                        stringResource(option.title),
                        stringResource(option.summary),
                        selected = receiver == option,
                        onClick = { receiver = option },
                    )
                }
                EngineCard(
                    stringResource(R.string.setup_account_title),
                    stringResource(R.string.square_setup_music_account),
                    selected = receiver == null,
                    onClick = { receiver = null },
                )
            }
            Button(onClick = { if (receiver == null) musicAccount() else showGuide = true }) {
                Text(stringResource(R.string.setup_continue))
            }
            TextButton(onClick = back) { Text(stringResource(R.string.first_look_title)) }
        }
        if (showGuide) receiver?.let { option ->
            ReceiverSetupDialog(
                option,
                onDismiss = { showGuide = false },
                nextLabel = R.string.square_setup_spotify_sign_in,
                onNext = {
                    showGuide = false
                    spotifySignIn()
                },
            )
        }
    }
}

@Composable
private fun SetupLayout(aurora: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (aurora) SetupAurora(Modifier.matchParentSize())
        Column(
            Modifier.systemBarsPadding().widthIn(max = 540.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun EngineCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.copy(alpha = 0.08f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        androidx.compose.material3.Icon(
            if (selected) PhosphorIcons.Regular.CheckCircle else PhosphorIcons.Regular.Circle,
            contentDescription = null,
            tint = if (selected) Ink else InkDim,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(24.dp),
        )
    }
}

@Composable
private fun SetupAurora(modifier: Modifier) {
    val time = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val reduceMotion = com.music.spotui.ui.look.rememberReduceMotion()
    if (!reduceMotion) androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    val ground = MaterialTheme.colorScheme.background
    androidx.compose.foundation.Canvas(modifier) {
        val t = time.floatValue
        val w = size.width
        val h = size.height
        AuroraBlobs.forEach { blob ->
            val x = w * (blob.x + 0.18f * kotlin.math.sin(t * blob.speed + blob.phase))
            val y = h * (blob.y + 0.12f * kotlin.math.cos(t * blob.speed * 0.8f + blob.phase * 1.3f))
            val radius = maxOf(w, h) * blob.size
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to blob.color.copy(alpha = 0.62f),
                    0.55f to blob.color.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                    radius = radius,
                ),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(x, y),
            )
        }
        // Darker towards the foot, where the buttons are.
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Transparent,
                0.7f to ground.copy(alpha = 0.25f),
                1f to ground.copy(alpha = 0.6f),
            ),
        )
    }
}

private data class AuroraBlob(
    val color: Color,
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val phase: Float,
)

private val AuroraBlobs = listOf(
    AuroraBlob(Color(0xFF3F46E0), 0.15f, 0.18f, 0.55f, 0.11f, 0f),
    AuroraBlob(Color(0xFF13B89F), 0.85f, 0.32f, 0.5f, 0.09f, 1.7f),
    AuroraBlob(Color(0xFF9147D9), 0.3f, 0.72f, 0.55f, 0.07f, 3.1f),
    AuroraBlob(Color(0xFF2A86E8), 0.8f, 0.88f, 0.45f, 0.1f, 4.6f),
)
