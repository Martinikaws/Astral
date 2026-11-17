package com.music.spotui.ui.screens

/*
 * Ported from Spotui's SpotiflacVerifyScreen (https://github.com/Spotui/Spotui), GPL-3.0,
 * and adapted to Astral.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SpotiFLAC verification, the way SpotiFLAC Mobile's extensions do it: bootstrap -> the
 * service's own hosted challenge page (the user solves the Turnstile themselves)
 * -> redirect with a grant -> exchange the grant for a signing session.
 */
@Composable
fun SpotiflacScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableStateOf(0) }
    fun missing() = listOf(com.music.spotui.lossless.ZarzSession.Ext.TIDAL)
        .firstOrNull { com.music.spotui.data.preferences.getSpotiflacSession(context, it.appVersion) == null }
    var ext by remember { mutableStateOf(missing()) }
    var verified by remember { mutableStateOf(ext == null) }
    var message by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    val leave: () -> Unit = onClose

    val installId = remember { com.music.spotui.lossless.ZarzSession.installId(context) }
    var state by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    fun finish(grant: String) = scope.launch {
        message = "Creating lossless session..."
        val current = ext ?: return@launch
        val ok = withContext(Dispatchers.IO) {
            runCatching { com.music.spotui.lossless.ZarzSession.exchange(context, current, installId, grant) }
                .onFailure { android.util.Log.w("Spotiflac", "exchange: ${it.message}") }.isSuccess
        }
        if (ok) {
            ext = missing()
            if (ext == null) {
                verified = true
                done = true
                android.widget.Toast.makeText(context, "Lossless server verified", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            message = "Failed to create session. Please try again."
        }
    }

    LaunchedEffect(attempt, ext) {
        val current = ext
        if (verified || current == null) return@LaunchedEffect
        message = ""
        val boot = withContext(Dispatchers.IO) {
            runCatching { com.music.spotui.lossless.ZarzSession.bootstrap(context, current, installId) }
                .onFailure { android.util.Log.w("Spotiflac", "bootstrap: ${it.message}") }.getOrNull()
        }
        if (boot == null) {
            message = "Could not start verification. Check your connection and retry."
            return@LaunchedEffect
        }
        if (boot.challengeUrl == null) {
            ext = missing()
            if (ext == null) { verified = true; done = true }
            return@LaunchedEffect
        }
        message = "Complete the verification in the browser tab to enable Hi-Res FLAC."
        state = boot.state
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(boot.challengeUrl))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        delay(180_000)
        if (!done && !verified) message = "Verification timed out. Tap Retry to try again."
    }

    LaunchedEffect(Unit) {
        com.music.spotui.lossless.ZarzSession.grants.collect { link ->
            if (link == null) return@collect
            com.music.spotui.lossless.ZarzSession.grants.value = null
            val back = com.music.spotui.lossless.ZarzSession.parseGrant(link)
            if (back == null || state.isBlank() || back.second != state) {
                message = "Verification response was rejected. Tap Retry."
            } else finish(back.first)
        }
    }

    if (verified) {
        SpotiflacStatusScreen(leave)
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (message.isNotEmpty()) {
            Text(
                message,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().height(56.dp).background(Color.Black),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { attempt++ }) { Text("Retry", color = Color.White) }
            TextButton(onClick = leave) { Text("Skip", color = Color.White) }
        }

        Box(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(40.dp).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Lossless Verification",
                color = Color(0xFF00C7B7),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

/** The status page when a session exists: state, expiry, forget. */
@Composable
private fun SpotiflacStatusScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Lossless HiFi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Your lossless FLAC download server session is active.", color = Color(0xFFB3B3B3), fontSize = 14.sp)
        for (e in com.music.spotui.lossless.ZarzSession.Ext.entries) {
            val session = com.music.spotui.data.preferences.getSpotiflacSession(context, e.appVersion) ?: continue
            Text(
                e.name.lowercase().replaceFirstChar { it.uppercase() } + " — Expires " +
                    session.expiresAt.take(19).replace('T', ' '),
                color = Color(0xFF00C7B7), fontSize = 13.sp,
            )
        }
        Button(
            onClick = {
                com.music.spotui.data.preferences.clearSpotiflacSessions(context)
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E), contentColor = Color.White),
            shape = RoundedCornerShape(24.dp),
        ) { Text("Forget session") }
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}
