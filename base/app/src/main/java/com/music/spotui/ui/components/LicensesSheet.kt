package com.music.spotui.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone

private const val LICENSES_DIR = "licenses"
private val REPO_URL get() = "https://github.com/" + app.myspoti.engine.BuildConfig.UPDATE_REPO.ifBlank { "Martinikaws/Astral" }

/** The licence files shipped in the APK's assets, known ones first in a fixed order. */
private fun licenseFiles(context: Context): List<String> {
    val found = runCatching { context.assets.list(LICENSES_DIR)?.toList() }.getOrNull().orEmpty()
    val order = listOf("LICENSE.txt", "THIRD_PARTY_NOTICES.md", "AGPL-3.0.txt", "LGPL-3.0.txt", "Apache-2.0.txt")
    return found.sortedWith(compareBy({ f -> order.indexOf(f).let { i -> if (i < 0) order.size else i } }, { it }))
}

@Composable
private fun licenseTitle(file: String): String = when (file) {
    "LICENSE.txt" -> stringResource(R.string.licenses_name_license)
    "THIRD_PARTY_NOTICES.md" -> stringResource(R.string.licenses_name_third_party)
    "AGPL-3.0.txt" -> stringResource(R.string.licenses_name_agpl)
    "LGPL-3.0.txt" -> stringResource(R.string.licenses_name_lgpl)
    "Apache-2.0.txt" -> stringResource(R.string.licenses_name_apache)
    "Inter-OFL.txt" -> stringResource(R.string.licenses_name_inter)
    "Manrope-OFL.txt" -> stringResource(R.string.licenses_name_manrope)
    else -> file
}

/** "Open-source licences": the files under assets/licenses, one tap to read each as plain text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files = remember { licenseFiles(context) }
    var open by remember { mutableStateOf<String?>(null) }
    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tone(0xFF282828),
    ) {
        val current = open
        if (current != null) {
            BackHandler { open = null }
            // null while loading; an empty list when the file could not be read.
            val paragraphs by produceState<List<String>?>(null, current) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        context.assets.open("$LICENSES_DIR/$current").bufferedReader().use { it.readText() }
                    }.getOrNull()?.replace("\r\n", "\n")?.split(Regex("\n\\s*\n"))?.filter { it.isNotBlank() }.orEmpty()
                }
            }
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.licenses_back), color = AppAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { open = null }
                        .wrapContentHeight(Alignment.CenterVertically))
                Text(licenseTitle(current), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
                val list = paragraphs
                when {
                    list == null -> Text(stringResource(R.string.licenses_loading), color = AppMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp))
                    list.isEmpty() -> Text(stringResource(R.string.licenses_load_failed), color = AppMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp))
                    else -> LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(list) { p ->
                            Text(p.trimEnd(), color = AppMuted, fontSize = 12.sp, lineHeight = 17.sp,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                }
            }
        } else {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                Text(stringResource(R.string.licenses_title), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() })
                if (files.isEmpty()) {
                    Text(stringResource(R.string.licenses_empty), color = AppMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    Text(stringResource(R.string.licenses_open_github), color = AppAccent, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { openRepo(context) }
                            .padding(horizontal = 20.dp, vertical = 12.dp))
                } else for (f in files) {
                    Text(licenseTitle(f), color = AppText, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = f }
                            .padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        }
    }
}

private fun openRepo(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
