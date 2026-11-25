/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.widget.Toast
import app.myspoti.engine.R
import com.soloist.android.runtime.DaemonState
import com.soloist.android.service.SoloistRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class SpotifyReceiver(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @StringRes val instructions: Int,
    val guide: String,
) {
    LIBRESPOT(
        R.string.librespot_playback, R.string.librespot_device_subtitle, R.string.librespot_setup,
        "https://github.com/librespot-org/librespot/wiki",
    ),
    SOLOIST(
        R.string.soloist_playback, R.string.soloist_device_subtitle, R.string.soloist_setup,
        "https://developer.spotify.com/documentation/soloist/tutorials/getting-started",
    ),
}

@Composable
internal fun ReceiverSetupDialog(
    receiver: SpotifyReceiver,
    onDismiss: () -> Unit,
    @StringRes nextLabel: Int,
    onNext: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val squarePackage = "dev.lelonio.square"
    fun openSquare() {
        val launch = context.packageManager.getLaunchIntentForPackage(squarePackage)
        if (launch == null) {
            Toast.makeText(context, R.string.square_receiver_missing, Toast.LENGTH_LONG).show()
            return
        }
        try {
            context.startActivity(launch)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.square_receiver_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.square_receiver_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    fun open(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: IllegalArgumentException) {
            android.widget.Toast.makeText(context, R.string.receiver_browser_unavailable, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (receiver == SpotifyReceiver.SOLOIST) {
        SoloistSetupDialog(onDismiss, nextLabel, onNext, ::open)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(receiver.title)) },
        text = {
            Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(stringResource(receiver.instructions))
                if (context.packageManager.getLaunchIntentForPackage(squarePackage) != null) {
                    Text(stringResource(R.string.square_receiver_help))
                    TextButton(onClick = ::openSquare) { Text(stringResource(R.string.square_receiver_open)) }
                }
                TextButton(onClick = { open(receiver.guide) }) {
                    Text(stringResource(R.string.soloist_instructions))
                }
            }
        },
        confirmButton = { TextButton(onClick = onNext) { Text(stringResource(nextLabel)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun SoloistSetupDialog(
    onDismiss: () -> Unit,
    @StringRes nextLabel: Int,
    onNext: () -> Unit,
    open: (String) -> Unit,
) {
    val context = LocalContext.current
    val available = remember(context) {
        java.io.File(context.applicationInfo.nativeLibraryDir, "libsoloist.so").isFile
    }
    if (!available) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.soloist_playback)) },
            text = { Text(stringResource(R.string.soloist_unavailable)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        )
        return
    }
    val controller = remember(context) { SoloistRuntime.get(context) }
    LaunchedEffect(controller) { controller.startSavedReceiver() }
    val state by controller.daemonState.collectAsState()
    val hasKey by controller.hasApiKey.collectAsState()
    val paired by controller.hasCredentials.collectAsState()
    val scope = rememberCoroutineScope()
    // Keep the key out of saved instance state, including during configuration changes.
    var key by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val editing = !hasKey || replacing
    val idle = state is DaemonState.Stopped || state is DaemonState.Error
    fun startReceiver() {
        try {
            controller.startDaemon(pairMode = !paired)
        } catch (_: IllegalStateException) {
            failed = true
        } catch (_: SecurityException) {
            failed = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.soloist_playback)) },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editing && idle) {
                    Text(stringResource(R.string.soloist_setup))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; failed = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.soloist_key_label)) },
                        singleLine = true,
                        enabled = !saving,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    TextButton(onClick = { open("https://developer.spotify.com/dashboard/soloist") }) {
                        Text(stringResource(R.string.soloist_key_sign_in))
                    }
                } else {
                    Text(stringResource(when {
                        state is DaemonState.Starting -> R.string.soloist_starting
                        state is DaemonState.Running && !paired -> R.string.soloist_pair_instruction
                        state is DaemonState.Running -> R.string.soloist_running
                        paired -> R.string.soloist_paired
                        else -> R.string.soloist_key_ready
                    }))
                    if (idle && hasKey) {
                        TextButton(onClick = { replacing = true; failed = false }) {
                            Text(stringResource(R.string.soloist_change_key))
                        }
                    }
                    if (state is DaemonState.Running) {
                        TextButton(onClick = controller::stopFromSetup) {
                            Text(stringResource(R.string.soloist_stop))
                        }
                    }
                }
                if (failed || state is DaemonState.Error) {
                    Text(stringResource(if (editing) R.string.soloist_key_error else R.string.soloist_start_error),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                editing && idle -> Button(enabled = key.isNotBlank() && !saving, onClick = {
                    saving = true
                    failed = false
                    scope.launch {
                        try {
                            if (controller.setupApiKey(key.trim()).success) {
                                key = ""
                                replacing = false
                                startReceiver()
                            } else failed = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(stringResource(R.string.soloist_save_start)) }
                idle -> Button(onClick = { failed = false; startReceiver() }) {
                    Text(stringResource(if (paired) R.string.soloist_start else R.string.soloist_pair))
                }
                state is DaemonState.Running && !paired -> Button(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("spotify:home"))
                        .setPackage("com.spotify.music")
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.soloist_spotify_missing, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.soloist_spotify_missing, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.soloist_open_spotify)) }
                else -> Button(onClick = onNext, enabled = paired && state is DaemonState.Running) {
                    Text(stringResource(nextLabel))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun ReceiverSourceRows(row: @Composable (String, String, () -> Unit) -> Unit) {
    var guide by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf<SpotifyReceiver?>(null)
    }
    var showDevices by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    for (receiver in SpotifyReceiver.entries) {
        row(stringResource(receiver.title), stringResource(receiver.summary)) { guide = receiver }
    }
    guide?.let { receiver ->
        ReceiverSetupDialog(
            receiver,
            onDismiss = { guide = null },
            nextLabel = R.string.listen_on,
            onNext = { guide = null; showDevices = true },
        )
    }
    if (showDevices) DevicesSheet(onDismiss = { showDevices = false })
}
