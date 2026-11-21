package com.music.spotui.connect.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Place in the host's Jam controls. Advertising belongs to the Jam, even after these controls close. */
@Composable
fun NearbyJamToggle(inviteLink: String?) {
    if (!MyspotiRuntime.isStarted) return
    val session by MyspotiRuntime.api.jam.session.collectAsStateWithLifecycle()
    val host = session?.takeIf { it.isHost } ?: return
    val link = inviteLink ?: host.joinToken
    val radio = rememberNearbyRadio(host = true)
    val advertising by NearbyJam.advertising.collectAsStateWithLifecycle()
    val failed by NearbyJam.advertiseFailed.collectAsStateWithLifecycle()
    var permissionSession by remember { mutableStateOf<String?>(null) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        radio.refresh()
        if (permissionSession == host.id && radio.granted && NearbyJam.bluetoothEnabled.value && link != null) {
            NearbyJam.advertise(link)
        }
        permissionSession = null
    }
    if (!radio.ready) return

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nearby_jam_toggle), Modifier.weight(1f))
            Switch(
                checked = advertising,
                enabled = radio.supported && link != null && (!radio.granted || radio.bluetoothOn),
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        NearbyJam.stopAdvertising()
                    } else if (!radio.granted) {
                        permissionSession = host.id
                        permissionRequest.launch(nearbyPermissions(host = true))
                    } else if (link != null) {
                        NearbyJam.advertise(link)
                    }
                },
            )
        }
        when {
            !radio.supported -> Text(stringResource(R.string.nearby_jam_unsupported))
            radio.granted && !radio.bluetoothOn -> TurnOnBluetooth(radio.refresh)
            failed -> Text(stringResource(R.string.nearby_jam_advertising_failed))
        }
    }
}

/** Compose only while the player is open. Closing or backgrounding the player stops scanning. */
@Composable
fun NearbyJamPrompt() {
    if (!MyspotiRuntime.isStarted) return
    val session by MyspotiRuntime.api.jam.session.collectAsStateWithLifecycle()
    val radio = rememberNearbyRadio(host = false)
    val hosts by NearbyJam.found.collectAsStateWithLifecycle()
    val scanFailed by NearbyJam.scanFailed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(emptySet<String>()) }
    var joining by remember { mutableStateOf<NearbyHost?>(null) }
    var failed by remember { mutableStateOf(false) }
    var scanAttempt by remember { mutableStateOf(0) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        radio.refresh()
    }

    if (!radio.ready || !radio.supported || session != null) return
    LifecycleResumeEffect(radio.granted, radio.bluetoothOn, scanAttempt) {
        if (radio.granted && radio.bluetoothOn) NearbyJam.startScanning()
        onPauseOrDispose { NearbyJam.stopScanning() }
    }
    when {
        !radio.granted -> TextButton(onClick = { permissionRequest.launch(nearbyPermissions(host = false)) }) {
            Text(stringResource(R.string.nearby_jam_find))
        }
        !radio.bluetoothOn -> TurnOnBluetooth(radio.refresh)
        scanFailed -> TextButton(onClick = { scanAttempt++ }) {
            Text(stringResource(R.string.nearby_jam_retry_scan))
        }
    }

    val candidate = joining ?: hosts.firstOrNull { it.token !in dismissed } ?: return
    val title = candidate.name?.let { stringResource(R.string.nearby_jam_named_prompt, it) }
        ?: stringResource(R.string.nearby_jam_unnamed_prompt)
    val dismiss = {
        if (joining == null) {
            dismissed = dismissed + candidate.token
            failed = false
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = if (failed) ({ Text(stringResource(R.string.nearby_jam_join_failed)) }) else null,
        confirmButton = {
            TextButton(
                enabled = joining == null,
                onClick = {
                    joining = candidate
                    failed = false
                    scope.launch {
                        try {
                            MyspotiRuntime.api.jam.join(candidate.token)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            joining = null
                        }
                    }
                },
            ) { Text(stringResource(R.string.nearby_jam_join)) }
        },
        dismissButton = {
            TextButton(enabled = joining == null, onClick = dismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private data class NearbyRadio(
    val ready: Boolean,
    val granted: Boolean,
    val supported: Boolean,
    val bluetoothOn: Boolean,
    val refresh: () -> Unit,
)

@Composable
private fun rememberNearbyRadio(host: Boolean): NearbyRadio {
    val context = LocalContext.current
    var ready by remember(context) { mutableStateOf(false) }
    var granted by remember(context, host) { mutableStateOf(context.hasNearbyPermissions(host)) }
    val enabled by NearbyJam.bluetoothEnabled.collectAsStateWithLifecycle()
    val refresh = {
        granted = context.hasNearbyPermissions(host)
        NearbyJam.refreshBluetooth()
    }
    LaunchedEffect(context) {
        NearbyJam.initialize(context)
        ready = true
    }
    LifecycleResumeEffect(context, host) {
        refresh()
        onPauseOrDispose {}
    }
    return NearbyRadio(ready, granted, NearbyJam.supported, enabled, refresh)
}

@Composable
@SuppressLint("MissingPermission") // Shown only after the role's permission check.
private fun TurnOnBluetooth(refresh: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    TextButton(onClick = { launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
        Text(stringResource(R.string.nearby_jam_turn_on_bluetooth))
    }
}
