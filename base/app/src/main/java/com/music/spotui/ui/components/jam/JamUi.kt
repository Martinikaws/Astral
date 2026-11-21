package com.music.spotui.ui.components.jam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.myspoti.api.Jam
import app.myspoti.api.JamAvailability
import app.myspoti.api.JamMember
import app.myspoti.api.JamSession
import app.myspoti.engine.R
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.music.spotui.ui.components.LookSheet
import com.music.spotui.ui.components.ProfileAvatar
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppDestructive
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AstralMidnight
import com.music.spotui.ui.theme.AstralNavy
import com.music.spotui.ui.theme.StarryBlue
import com.music.spotui.ui.theme.tone
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val JamSheetBackground: Color get() = tone(0xFF181C26)
private val JamCardSurface: Color get() = tone(0xFF1B2234)
private val JamElevatedSurface: Color get() = tone(0xFF262A36)

/** Pure helpers for Jam state, member ordering, and permission checks. */
internal fun sortJamMembers(members: List<JamMember>): List<JamMember> =
    members.sortedByDescending { it.host }

internal fun isRestricted(availability: JamAvailability?): Boolean =
    availability == JamAvailability.RESTRICTED

internal fun canStartJam(availability: JamAvailability?): Boolean =
    !isRestricted(availability)

internal fun memberDisplayName(member: JamMember): String =
    member.name.ifBlank { member.userUri.substringAfterLast(':') }

internal fun resolveInviteLink(fetchedLink: String?, session: JamSession): String? =
    fetchedLink?.takeIf { it.isNotBlank() }
        ?: session.inviteLink?.takeIf { it.isNotBlank() }
        ?: session.joinToken?.takeIf { it.isNotBlank() }?.let { "https://open.spotify.com/socialsession/$it" }

/** QR code BitMatrix encoder for Jam invite links. */
internal object JamQr {
    fun encodeModules(text: String): BitMatrix {
        val payload = text.ifBlank { "https://open.spotify.com" }
        return QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0),
        )
    }
}

/** Jam invite codes: `open.spotify.com/socialsession/<token>`, `spotify:socialsession:<token>`, or a short link to it. */
internal object JamLinks {
    private val http = OkHttpClient.Builder().followRedirects(true).build()
    private val webTokenRegex = Regex("socialsession/([A-Za-z0-9_-]+)")
    private val uriTokenRegex = Regex("spotify:socialsession:([A-Za-z0-9_-]+)")
    private val embeddedUrlRegex = Regex("https://open\\.spotify\\.com/socialsession/[A-Za-z0-9_-]+")

    fun extractToken(url: String): String? {
        if (url.isBlank()) return null
        webTokenRegex.find(url)?.let { return it.groupValues[1] }
        uriTokenRegex.find(url)?.let { return it.groupValues[1] }
        return null
    }

    suspend fun token(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val url = if (trimmed.contains("spotify.link") || trimmed.contains("spoti.fi")) {
            resolve(trimmed) ?: trimmed
        } else {
            trimmed
        }
        return extractToken(url)
    }

    /** Follows a short link to its final URL after redirects. */
    private suspend fun resolve(link: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(
                Request.Builder()
                    .url(link)
                    .header("user-agent", "Mozilla/5.0")
                    .build(),
            ).execute().use { r ->
                val final = r.request.url.toString()
                if (final.contains("socialsession")) final
                else embeddedUrlRegex.find(r.body.string())?.value ?: final
            }
        }.getOrNull()
    }
}

/**
 * Joins the Jam of the invite [raw] (a link or a QR code's text) on the runtime scope, not the sheet's,
 * so closing the devices sheet does not cancel an in-flight join.
 */
internal fun joinJam(context: Context, raw: String, onDismiss: () -> Unit) {
    fun failed() = Toast.makeText(
        context.applicationContext,
        context.getString(R.string.jam_failed),
        Toast.LENGTH_SHORT,
    ).show()
    onDismiss()
    MyspotiRuntime.scope.launch {
        val token = JamLinks.token(raw)
            ?: return@launch withContext(Dispatchers.Main) { failed() }
        val error = runCatching { MyspotiRuntime.api.jam.join(token) }.exceptionOrNull()
        if (error != null && error !is CancellationException) {
            Logbook.log("jam: join failed: ${error.message}")
            withContext(Dispatchers.Main) { failed() }
        }
    }
}

/**
 * Astral-styled Jam card embedded inside [com.music.spotui.ui.components.DevicesSheet],
 * plus child sheets for managing Jam details and inviting friends via link or QR code.
 */
@Composable
fun JamSection(onDismiss: () -> Unit) {
    val jam = MyspotiRuntime.api.jam
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by jam.session.collectAsState()
    val availability by produceState<JamAvailability?>(null) {
        value = runCatching {
            jam.refresh()
            jam.availability()
        }.getOrDefault(JamAvailability.UNKNOWN)
    }

    var showDetailsSheet by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var cachedInviteLink by remember(session?.id) { mutableStateOf(session?.inviteLink) }
    var startingJam by remember { mutableStateOf(false) }

    fun showFailedToast() {
        Toast.makeText(context, context.getString(R.string.jam_failed), Toast.LENGTH_SHORT).show()
    }

    val activeSession = session
    if (activeSession != null && showDetailsSheet) {
        JamDetailsSheet(
            session = activeSession,
            jam = jam,
            inviteLink = cachedInviteLink,
            onInviteLinkLoaded = { cachedInviteLink = it },
            onOpenInvite = { showInviteSheet = true },
            onDismiss = { showDetailsSheet = false },
            onDismissParent = onDismiss,
        )
    }

    if (activeSession != null && showInviteSheet) {
        JamInviteSheet(
            session = activeSession,
            jam = jam,
            inviteLink = cachedInviteLink,
            onInviteLinkLoaded = { cachedInviteLink = it },
            onDismiss = { showInviteSheet = false },
        )
    }

    val cardShape = RoundedCornerShape(18.dp)
    val accent = AppAccent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        JamCardSurface,
                        AstralNavy.copy(alpha = 0.55f),
                        JamElevatedSurface,
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.22f), cardShape)
            .padding(16.dp),
    ) {
        if (activeSession == null) {
            InactiveJamCardContent(
                availability = availability,
                startingJam = startingJam,
                onStartJam = {
                    if (startingJam) return@InactiveJamCardContent
                    startingJam = true
                    scope.launch {
                        val started = runCatching { jam.start() }.getOrNull()
                        if (started == null) {
                            startingJam = false
                            showFailedToast()
                            return@launch
                        }
                        val link = runCatching { jam.inviteLink() }.getOrNull()
                        cachedInviteLink = resolveInviteLink(link, started)
                        startingJam = false
                        showDetailsSheet = true
                    }
                },
                onScanQr = {
                    GmsBarcodeScanning.getClient(context).startScan()
                        .addOnSuccessListener { code ->
                            val raw = code.rawValue.orEmpty()
                            joinJam(context, raw, onDismiss)
                        }
                        .addOnFailureListener { showFailedToast() }
                },
                onPasteLink = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val raw = clip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    joinJam(context, raw, onDismiss)
                },
            )
        } else {
            ActiveJamCardContent(
                session = activeSession,
                onOpenDetails = { showDetailsSheet = true },
                onOpenInvite = { showInviteSheet = true },
                onEndOrLeave = {
                    scope.launch {
                        runCatching {
                            if (activeSession.isHost) jam.end() else jam.leave()
                        }
                        showDetailsSheet = false
                        showInviteSheet = false
                        onDismiss()
                    }
                },
            )
        }
    }
}

@Composable
private fun InactiveJamCardContent(
    availability: JamAvailability?,
    startingJam: Boolean,
    onStartJam: () -> Unit,
    onScanQr: () -> Unit,
    onPasteLink: () -> Unit,
) {
    val accent = AppAccent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.jam_title),
                color = AppText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.jam_subtitle),
                color = AppMuted,
                fontSize = 13.sp,
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    if (isRestricted(availability)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JamElevatedSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AppMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.jam_restricted_notice),
                color = AppMuted,
                fontSize = 13.sp,
            )
        }
    } else if (canStartJam(availability)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable(enabled = !startingJam, onClick = onStartJam)
                .padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            if (startingJam) {
                CircularProgressIndicator(
                    color = AstralMidnight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.jam_starting),
                    color = AstralMidnight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = AstralMidnight,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.jam_start),
                    color = AstralMidnight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.jam_join_section),
        color = AppMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        JamActionPill(
            icon = Icons.Default.QrCodeScanner,
            label = stringResource(R.string.jam_scan),
            onClick = onScanQr,
            modifier = Modifier.weight(1f),
        )
        JamActionPill(
            icon = Icons.Default.ContentPaste,
            label = stringResource(R.string.jam_paste_link),
            onClick = onPasteLink,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActiveJamCardContent(
    session: JamSession,
    onOpenDetails: () -> Unit,
    onOpenInvite: () -> Unit,
    onEndOrLeave: () -> Unit,
) {
    val accent = AppAccent
    val sortedMembers = remember(session.members) { sortJamMembers(session.members) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.18f)),
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.jam_title),
                    color = AppText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (session.isHost) {
                    Spacer(Modifier.width(8.dp))
                    HostBadgePill()
                }
            }
            Text(
                text = stringResource(R.string.jam_members, session.members.size),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (sortedMembers.isNotEmpty()) {
            StackedMemberAvatars(members = sortedMembers, maxVisible = 4)
        }
    }

    if (!session.isHost) {
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JamElevatedSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                tint = StarryBlue,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (session.listensHere) R.string.jam_plays_here else R.string.jam_plays_on_host,
                ),
                color = AppMuted,
                fontSize = 12.sp,
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        JamActionPill(
            icon = Icons.Default.Groups,
            label = stringResource(R.string.jam_details_title),
            onClick = onOpenDetails,
            highlighted = true,
            modifier = Modifier.weight(1f),
        )
        JamActionPill(
            icon = Icons.Default.PersonAdd,
            label = stringResource(R.string.jam_invite_friends_title),
            onClick = onOpenInvite,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(8.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppDestructive.copy(alpha = 0.14f))
            .border(1.dp, AppDestructive.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
            .clickable(onClick = onEndOrLeave)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = stringResource(if (session.isHost) R.string.jam_end else R.string.jam_leave),
            color = AppDestructive,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun JamActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val accent = AppAccent
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (highlighted) accent.copy(alpha = 0.18f) else JamElevatedSurface
    val borderColor = if (highlighted) accent.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.08f)
    val contentColor = if (highlighted) accent else AppText

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) accent else StarryBlue,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StackedMemberAvatars(members: List<JamMember>, maxVisible: Int = 4) {
    val visible = members.take(maxVisible)
    Row(
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEach { member ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(JamCardSurface)
                    .padding(2.dp),
            ) {
                ProfileAvatar(
                    url = member.imageUrl,
                    name = memberDisplayName(member),
                    size = 28.dp,
                )
            }
        }
    }
}

@Composable
private fun HostBadgePill() {
    val accent = AppAccent
    Text(
        text = stringResource(R.string.jam_host_badge),
        color = accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Bottom sheet showing Jam details: playback route info for guests, "Invite others" button,
 * nearby Jam toggle slot, guest permission switch, member list with host badge and removal,
 * and End/Leave Jam action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamDetailsSheet(
    session: JamSession,
    jam: Jam,
    inviteLink: String?,
    onInviteLinkLoaded: (String) -> Unit,
    onOpenInvite: () -> Unit,
    onDismiss: () -> Unit,
    onDismissParent: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = AppAccent
    val sortedMembers = remember(session.members) { sortJamMembers(session.members) }
    var allowOthersControl by remember(session.id, session.queueOnly) {
        mutableStateOf(!session.queueOnly)
    }

    LaunchedEffect(session.id) {
        if (inviteLink.isNullOrBlank()) {
            val fetched = runCatching { jam.inviteLink() }.getOrNull()
            val resolved = resolveInviteLink(fetched, session)
            if (resolved != null) onInviteLinkLoaded(resolved)
        }
    }

    fun showFailedToast() {
        Toast.makeText(context, context.getString(R.string.jam_failed), Toast.LENGTH_SHORT).show()
    }

    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JamSheetBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.jam_details_title),
                        color = AppText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.jam_members, session.members.size),
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!session.isHost) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(JamCardSurface)
                        .border(1.dp, StarryBlue.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = StarryBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            if (session.listensHere) R.string.jam_plays_here else R.string.jam_plays_on_host,
                        ),
                        color = AppText.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // Invite others button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent)
                    .clickable(onClick = onOpenInvite)
                    .padding(vertical = 13.dp, horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = AstralMidnight,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.jam_invite_others),
                    color = AstralMidnight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // TODO: Wire com.music.spotui.connect.nearby.NearbyJamToggle(inviteLink) after nearby-jam branch merges.
            if (false) {
                NearbyJamToggleSlot(inviteLink = inviteLink)
            }

            Spacer(Modifier.height(18.dp))

            // Guest permissions section
            Text(
                text = stringResource(R.string.jam_guest_settings_title),
                color = AppMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JamCardSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.jam_let_others_change),
                        color = AppText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.jam_let_others_change_detail),
                        color = AppMuted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = allowOthersControl,
                    enabled = session.isHost,
                    onCheckedChange = { checked ->
                        if (!session.isHost) return@Switch
                        allowOthersControl = checked
                        scope.launch {
                            val result = runCatching {
                                jam.setParticipantVolumeControl(checked)
                                jam.refresh()
                            }
                            if (result.isFailure) {
                                allowOthersControl = !checked
                                showFailedToast()
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AstralMidnight,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = AppMuted,
                        uncheckedTrackColor = JamElevatedSurface,
                    ),
                )
            }

            Spacer(Modifier.height(18.dp))

            // Member list
            Text(
                text = stringResource(R.string.jam_members, sortedMembers.size),
                color = AppMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                sortedMembers.forEach { member ->
                    val displayName = memberDisplayName(member)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(JamCardSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        ProfileAvatar(
                            url = member.imageUrl,
                            name = displayName,
                            size = 42.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayName,
                                    color = AppText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (member.host) {
                                    Spacer(Modifier.width(8.dp))
                                    HostBadgePill()
                                }
                            }
                        }
                        if (session.isHost && !member.host) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val res = runCatching {
                                            jam.removeMember(member.userUri)
                                            jam.refresh()
                                        }
                                        if (res.isFailure) showFailedToast()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonRemove,
                                    contentDescription = stringResource(R.string.jam_remove_member),
                                    tint = AppDestructive,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // End Jam (host) or Leave Jam (guest)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppDestructive.copy(alpha = 0.16f))
                    .border(1.dp, AppDestructive.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable {
                        scope.launch {
                            runCatching { if (session.isHost) jam.end() else jam.leave() }
                            onDismiss()
                            onDismissParent()
                        }
                    }
                    .padding(vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(if (session.isHost) R.string.jam_end else R.string.jam_leave),
                    color = AppDestructive,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
private fun NearbyJamToggleSlot(inviteLink: String?) {
    // Reserved slot for com.music.spotui.connect.nearby.NearbyJamToggle(inviteLink)
}

/**
 * Bottom sheet for inviting friends to the active Jam via QR code (with full-screen zoom),
 * system share sheet, or clipboard copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamInviteSheet(
    session: JamSession,
    jam: Jam,
    inviteLink: String?,
    onInviteLinkLoaded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val accent = AppAccent
    var currentLink by remember(session.id, inviteLink) {
        mutableStateOf(resolveInviteLink(inviteLink, session))
    }
    var loadingLink by remember(session.id) { mutableStateOf(currentLink == null) }
    var fullScreenQr by remember { mutableStateOf(false) }

    LaunchedEffect(session.id) {
        if (currentLink.isNullOrBlank()) {
            loadingLink = true
            val fetched = runCatching { jam.inviteLink() }.getOrNull()
            val resolved = resolveInviteLink(fetched, session)
            currentLink = resolved
            if (resolved != null) onInviteLinkLoaded(resolved)
            loadingLink = false
        }
    }

    val linkToUse = currentLink
    val copiedToastText = stringResource(R.string.jam_link_copied)
    val failedToastText = stringResource(R.string.jam_failed)

    if (fullScreenQr && !linkToUse.isNullOrBlank()) {
        JamFullScreenQrDialog(
            url = linkToUse,
            onDismiss = { fullScreenQr = false },
        )
    }

    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JamSheetBackground,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.jam_invite_friends_title),
                color = AppText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.jam_invite_friends_subtitle),
                color = AppMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // QR Code Card
            val qrCardShape = RoundedCornerShape(20.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(qrCardShape)
                    .background(JamCardSurface)
                    .border(1.dp, accent.copy(alpha = 0.24f), qrCardShape)
                    .clickable(enabled = !linkToUse.isNullOrBlank()) { fullScreenQr = true }
                    .padding(20.dp),
            ) {
                when {
                    loadingLink -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(210.dp),
                        ) {
                            CircularProgressIndicator(
                                color = accent,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    !linkToUse.isNullOrBlank() -> {
                        JamQrCode(
                            url = linkToUse,
                            size = 210.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.jam_qr_tap_fullscreen),
                            color = AppMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(210.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.jam_failed),
                                color = AppMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Share link button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent)
                    .clickable(enabled = !linkToUse.isNullOrBlank()) {
                        val url = linkToUse ?: return@clickable
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, url)
                        context.startActivity(
                            Intent.createChooser(send, null)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = AstralMidnight,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.jam_share_link),
                    color = AstralMidnight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Copy link button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JamElevatedSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .clickable {
                        val url = linkToUse
                        if (url.isNullOrBlank()) {
                            Toast.makeText(context, failedToastText, Toast.LENGTH_SHORT).show()
                        } else {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Jam", url))
                            Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 14.dp, horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = AppText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.jam_copy_link),
                    color = AppText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Crisp high-contrast QR code rendered on a white quiet-zone canvas for reliable camera scanning. */
@Composable
fun JamQrCode(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(url) { JamQr.encodeModules(url) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val quiet = 3
            val cell = floor(this.size.minDimension / (matrix.width + 2 * quiet)).coerceAtLeast(1f)
            val origin = (this.size.minDimension - cell * matrix.width) / 2f
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(origin + x * cell, origin + y * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JamFullScreenQrDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val matrix = remember(url) { JamQr.encodeModules(url) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AstralMidnight, AstralNavy, JamSheetBackground),
                    ),
                )
                .clickable(onClick = onDismiss)
                .padding(24.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.beta_logs_close),
                    tint = AppText,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            ) {
                Text(
                    text = stringResource(R.string.jam_qr_fullscreen_title),
                    color = AppText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.jam_invite_friends_subtitle),
                    color = AppMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val quiet = 3
                        val cell = floor(this.size.minDimension / (matrix.width + 2 * quiet)).coerceAtLeast(1f)
                        val origin = (this.size.minDimension - cell * matrix.width) / 2f
                        for (y in 0 until matrix.height) {
                            for (x in 0 until matrix.width) {
                                if (matrix[x, y]) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(origin + x * cell, origin + y * cell),
                                        size = Size(cell, cell),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
