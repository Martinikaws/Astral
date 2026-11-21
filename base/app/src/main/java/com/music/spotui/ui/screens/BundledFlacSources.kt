package com.music.spotui.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.di.LocalPlayer
import com.music.spotui.flac.FlacSources
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal const val FLAC_SOURCE_ROUTE = "bundled-flac"

private enum class BundledSource(val label: String, val description: Int, val website: String, val link: Int) {
    JAMENDO("Jamendo", R.string.flac_jamendo_description, "https://devportal.jamendo.com/", R.string.flac_jamendo_signup),
    NAVIDROME("Navidrome / OpenSubsonic", R.string.flac_navidrome_description, "https://www.navidrome.org/docs/installation/", R.string.flac_navidrome_setup),
    ARCHIVE("Live Music Archive", R.string.flac_archive_description, "https://archive.org/details/etree", R.string.flac_archive_browse),
    BANDCAMP("Bandcamp", R.string.flac_bandcamp_description, "https://bandcamp.com/fans", R.string.flac_bandcamp_signup),
    MASSIVE("MassiveMusic / 7digital", R.string.flac_massive_description, "https://docs.massivemusic.com/reference/introduction", R.string.flac_massive_access),
}

@Composable
internal fun BundledFlacSources(navController: NavController) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.flac_bundled_title), color = AppText, fontWeight = FontWeight.Bold)
        BundledSource.entries.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { navController.navigate("$FLAC_SOURCE_ROUTE/${source.name.lowercase()}") }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(source.label, color = AppText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.flac_source_open), color = AppAccent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun BundledFlacSourceScreen(navController: NavController, id: String) {
    val source = BundledSource.entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: return
    val uriHandler = LocalUriHandler.current
    Page(null, navController, title = source.label) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(source.description), color = AppMuted)
            TextButton(onClick = { runCatching { uriHandler.openUri(source.website) } }) {
                Text(stringResource(source.link), color = AppAccent)
            }
            when (source) {
                BundledSource.BANDCAMP -> BandcampFiles()
                BundledSource.MASSIVE -> Unit
                else -> FlacSearch(source)
            }
        }
    }
}

@Composable
private fun FlacSearch(source: BundledSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("bundled_flac", 0) }
    var client by remember { mutableStateOf(if (source == BundledSource.JAMENDO) prefs.getString("jamendo_client", "").orEmpty() else "") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<FlacSources.Track>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    if (source == BundledSource.JAMENDO) {
        OutlinedTextField(
            value = client, onValueChange = { client = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.flac_jamendo_client)) },
            visualTransformation = PasswordVisualTransformation(),
        )
    }
    if (source == BundledSource.NAVIDROME) {
        OutlinedTextField(
            value = server, onValueChange = { server = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.flac_server)) },
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.flac_username)) },
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.flac_password)) },
            visualTransformation = PasswordVisualTransformation(),
        )
    }
    val archive = source == BundledSource.ARCHIVE
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(if (archive) R.string.flac_archive_input else R.string.flac_search_input)) },
    )
    val ready = query.isNotBlank() && when (source) {
        BundledSource.JAMENDO -> client.isNotBlank()
        BundledSource.NAVIDROME -> server.isNotBlank() && user.isNotBlank() && password.isNotEmpty()
        else -> true
    }
    TextButton(enabled = ready && !loading, onClick = {
        val request = query.trim()
        val providerKey = client
        val host = server
        val username = user
        val pass = password
        loading = true
        failed = false
        tracks = null
        scope.launch {
            try {
                tracks = withContext(Dispatchers.IO) {
                    when (source) {
                        BundledSource.JAMENDO -> FlacSources.jamendo(providerKey, request)
                        BundledSource.NAVIDROME -> FlacSources.navidrome(host, username, pass, request)
                        BundledSource.ARCHIVE -> FlacSources.archive(request)
                        else -> emptyList()
                    }
                }
                if (source == BundledSource.JAMENDO) prefs.edit().putString("jamendo_client", providerKey.trim()).apply()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }
    }) {
        Text(stringResource(if (archive) R.string.flac_archive_load else R.string.flac_search), color = if (ready && !loading) AppAccent else AppMuted)
    }
    if (loading) CircularProgressIndicator()
    if (failed) Text(stringResource(R.string.flac_source_error), color = AppMuted)
    tracks?.let { FlacTrackList(it, source.label) }
}

@Composable
private fun BandcampFiles() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("bundled_flac", 0) }
    var tracks by remember { mutableStateOf<List<FlacSources.Track>?>(null) }
    suspend fun read(files: List<Uri>) = withContext(Dispatchers.IO) { FlacSources.bandcamp(context, files) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val saved = runCatching { JSONArray(prefs.getString("bandcamp_files", "[]")) }.getOrElse { JSONArray() }
        tracks = read((0 until saved.length()).mapNotNull { i -> saved.optString(i).takeIf(String::isNotBlank)?.let(Uri::parse) })
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        if (selected.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = read(selected)
            val all = (tracks.orEmpty() + added).distinctBy { it.audio.uri }
            val saved = JSONArray().apply { all.forEach { put(it.audio.uri.toString()) } }
            prefs.edit().putString("bandcamp_files", saved.toString()).apply()
            tracks = all
        }
    }
    TextButton(enabled = tracks != null, onClick = { picker.launch(arrayOf("audio/*", "application/octet-stream")) }) {
        Text(stringResource(R.string.flac_bandcamp_import), color = AppAccent)
    }
    tracks?.let { FlacTrackList(it, "Bandcamp") }
}

@Composable
private fun FlacTrackList(tracks: List<FlacSources.Track>, source: String) {
    val uriHandler = LocalUriHandler.current
    if (tracks.isEmpty()) Text(stringResource(R.string.flac_source_empty), color = AppMuted)
    tracks.forEachIndexed { i, result ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.fillMaxWidth().clickable { LocalPlayer.play(tracks.map { it.audio }, i, context = source) }.padding(vertical = 4.dp)) {
                Text(result.audio.title, color = AppText, fontWeight = FontWeight.Medium)
                Text(result.audio.artist.ifBlank { source }, color = AppMuted, fontSize = 13.sp)
            }
            result.page?.let { page ->
                TextButton(onClick = { runCatching { uriHandler.openUri(page) } }) {
                    Text(stringResource(R.string.flac_view_source, source), color = AppAccent, fontSize = 12.sp)
                }
            }
        }
    }
}
