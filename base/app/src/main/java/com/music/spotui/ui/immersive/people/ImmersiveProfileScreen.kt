package com.music.spotui.ui.immersive.people

import com.music.spotui.ui.immersive.sheets.ImmersiveAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import com.music.spotui.ui.immersive.squared.LocalInkOverride
import com.music.spotui.ui.immersive.squared.SquaredArtwork
import com.music.spotui.data.api.ProfileCache
import com.music.spotui.ui.immersive.squared.components.ProfilePicture
import com.music.spotui.ui.immersive.squared.inkOn
import com.music.spotui.ui.immersive.squared.pageColorFor
import com.music.spotui.ui.immersive.squared.rememberArtworkColor
import com.music.spotui.ui.immersive.squared.screens.rememberSquaredContentPadding
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.myspoti.api.UserProfile
import app.myspoti.api.best
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.di.Haptics
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.components.ImmersiveButton
import com.music.spotui.ui.immersive.components.ImmersiveCover
import com.music.spotui.ui.immersive.components.ImmersiveListRow
import com.music.spotui.ui.immersive.components.ImmersiveMessageState
import com.music.spotui.ui.immersive.components.SectionHeader
import com.music.spotui.ui.immersive.components.immersiveClickable
import com.music.spotui.ui.immersive.components.immersiveSongListSkeleton
import com.music.spotui.ui.immersive.podcast.CoverGlow
import com.music.spotui.ui.immersive.podcast.ImmersiveOverscrollHost
import com.music.spotui.ui.immersive.podcast.rememberHeaderCollapse
import com.music.spotui.ui.immersive.podcast.rowPlacement
import com.music.spotui.ui.immersive.shell.ImmersiveTopBar
import com.music.spotui.ui.immersive.shell.immersiveHazeSource
import com.music.spotui.ui.immersive.shell.immersiveTopContentPadding
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.screens.followListRoute
import kotlinx.coroutines.launch

private val AvatarSize = 168.dp

/**
 * A user's profile in the Immersive look: a large round picture on a wash of its own colour, the
 * name, followers and following (links to their lists), a Follow button (not on your own profile)
 * and the public playlists as rows. Same data and actions as the Classic profile page.
 */
@Composable
fun ImmersiveProfileScreen(
    navController: NavController,
    userId: String,
    userName: String = "",
    squared: Boolean = false,
) {
    val uri = "spotify:user:$userId"
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var failed by remember(userId) { mutableStateOf(false) }
    var following by remember(userId) { mutableStateOf<Boolean?>(null) }
    var accountId by remember(userId) { mutableStateOf(ProfileCache.id) }
    val me = accountId == userId
    // Blocking, as on the Classic profile: null until the server has said.
    var blocked by remember(userId) { mutableStateOf<Boolean?>(null) }
    var blockAsk by remember(userId) { mutableStateOf(false) }
    var barMenu by remember(userId) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember(userId) { mutableStateOf(0) }
    LaunchedEffect(userId, attempt) {
        failed = false
        runCatching { MyspotiRuntime.api.users.profile(uri) }
            .onSuccess { profile = it }.onFailure { failed = true }
    }
    LaunchedEffect(userId, attempt) { accountId = ProfileCache.awaitId(context, 5_000L) }
    LaunchedEffect(userId, accountId, attempt) {
        if (accountId == null || me) return@LaunchedEffect
        coroutineScope {
            launch { following = runCatching { MyspotiRuntime.api.users.isFollowing(listOf(uri)).singleOrNull() }.getOrNull() }
            launch { blocked = runCatching { MyspotiRuntime.api.users.isBlocked(uri) }.getOrNull() }
        }
    }
    val p = profile
    val displayName = p?.name?.takeIf { it.isNotBlank() }
        ?: ProfileCache.name?.takeIf { me && it.isNotBlank() }
        ?: userName.ifBlank { userId }
    val listState = rememberLazyListState()
    val avatarUrl = p?.images?.best(300)?.url ?: ProfileCache.imageUrl?.takeIf { me }

    val starryEnabled by com.music.spotui.data.preferences.StarryNightPref.enabled.collectAsState()
    val pageBg = if (squared) {
        val accent by rememberArtworkColor(avatarUrl)
        pageColorFor(accent)
    } else if (starryEnabled && Immersive.Page == Color.Transparent) Color.Transparent else Immersive.Page
    val pageInk = if (squared) inkOn(pageBg) else Immersive.Text
    val bottomPadding = if (squared) rememberSquaredContentPadding().calculateBottomPadding() else Immersive.BottomChrome
    val titleThreshold = with(LocalDensity.current) { 168.dp.roundToPx() }
    val squaredTitleVisible by remember(listState, titleThreshold) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > titleThreshold }
    }

    ImmersiveOverscrollHost {
        Box(Modifier.fillMaxSize().then(if (pageBg == Color.Transparent) Modifier else Modifier.background(pageBg))) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = bottomPadding),
                modifier = Modifier.fillMaxSize().immersiveHazeSource(),
            ) {
                item(key = "header") {
                    Box(Modifier.fillMaxWidth()) {
                        if (!squared) CoverGlow(avatarUrl, height = 360.dp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(top = immersiveTopContentPadding() + 8.dp, bottom = 12.dp),
                        ) {
                            if (squared) {
                                CompositionLocalProvider(LocalInkOverride provides pageInk) {
                                    val size = 132.dp
                                    ProfilePicture(avatarUrl, displayName, size, Modifier.size(size), decodeSize = size)
                                }
                            } else {
                                ImmersiveCover(
                                    url = avatarUrl,
                                    modifier = Modifier.size(AvatarSize),
                                    shape = CircleShape,
                                    placeholderIcon = Icons.Rounded.Person,
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = displayName,
                                color = pageInk,
                                style = Immersive.LargeTitle,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = Immersive.Gutter),
                            )
                            if (p != null) {
                                Row(
                                    Modifier.padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    p.followers?.let {
                                        CountLink(pluralStringResource(R.plurals.profile_followers, it, it), if (squared) pageInk else Immersive.Accent) {
                                            navController.navigate(followListRoute(userId, followers = true))
                                        }
                                    }
                                    if (p.followers != null && p.following != null) Text("·", color = pageInk, style = Immersive.Subhead)
                                    p.following?.let {
                                        CountLink(stringResource(R.string.profile_following_count, it), if (squared) pageInk else Immersive.Accent) {
                                            navController.navigate(followListRoute(userId, followers = false))
                                        }
                                    }
                                }
                            }
                            // Your own profile leads to your listening stats and the extensions.
                            if (me) {
                                Spacer(Modifier.height(18.dp))
                                ImmersiveButton(
                                    text = stringResource(R.string.stats_listening_stats),
                                    modifier = Modifier.widthIn(min = 170.dp),
                                    onClick = { navController.navigate(com.music.spotui.ui.navigation.Routes.Recap.route) },
                                )
                                Spacer(Modifier.height(10.dp))
                                ImmersiveButton(
                                    text = stringResource(R.string.extensions_title),
                                    modifier = Modifier.widthIn(min = 170.dp),
                                    onClick = { navController.navigate(com.music.spotui.ui.navigation.Routes.Extensions.route) },
                                )
                            }
                            if (blocked == true) {
                                Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.profile_blocked), color = pageInk, style = Immersive.Subhead)
                            }
                            val f = following
                            if (!me && f != null && blocked != true) {
                                Spacer(Modifier.height(18.dp))
                                ImmersiveButton(
                                    text = stringResource(
                                        if (f) R.string.curation_button_following_state_text else R.string.curation_button_follow_state_text,
                                    ),
                                    icon = if (f) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                    filled = !f,
                                    modifier = Modifier.widthIn(min = 170.dp),
                                    onClick = {
                                        Haptics.confirm()
                                        following = !f
                                        scope.launch {
                                            runCatching {
                                                if (f) MyspotiRuntime.api.users.unfollow(listOf(uri)) else MyspotiRuntime.api.users.follow(listOf(uri))
                                            }.onFailure { following = f }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                when {
                    p == null && !failed -> immersiveSongListSkeleton(count = 4, keyPrefix = "skeleton:profile", alpha = 0.5f)
                    p == null -> item(key = "failed") {
                        if (squared) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(stringResource(R.string.page_load_failed), color = pageInk, style = Immersive.Body)
                                TextButton(onClick = { attempt++ }) {
                                    Text(stringResource(R.string.retry_button), color = pageInk)
                                }
                            }
                        } else {
                            ImmersiveMessageState(
                                message = stringResource(R.string.page_load_failed),
                                actionLabel = stringResource(R.string.retry_button),
                                onAction = { attempt++ },
                            )
                        }
                    }
                    p.publicPlaylists.isNotEmpty() -> {
                        item(key = "playlists-header") {
                            if (squared) Text(
                                stringResource(R.string.profile_public_playlists),
                                style = Immersive.Headline,
                                color = pageInk,
                                modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 12.dp),
                            ) else SectionHeader(stringResource(R.string.profile_public_playlists))
                        }
                        items(p.publicPlaylists.distinctBy { it.uri }, key = { it.uri }) { pl ->
                            val followers = pl.followers?.toInt()
                            val subtitle = followers?.let { pluralStringResource(R.plurals.profile_followers, it, it) }
                            val art = pl.images.best(160)?.url
                            val open = { navController.navigate(playlistRoute(pl.uri.substringAfterLast(':'), pl.name)) }
                            if (squared) SquaredProfilePlaylist(pl.name, subtitle, art, pageInk, open)
                            else ImmersiveListRow(
                                title = pl.name, subtitle = subtitle, imageUrl = art, round = false,
                                onClick = open, modifier = rowPlacement(),
                            )
                        }
                    }
                }
            }
            val actions: @Composable () -> Unit = {
                    val b = blocked
                    if (!me && b != null) Box {
                        com.music.spotui.ui.immersive.shell.ImmersiveBarAction(
                            icon = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.profile_more_options),
                            onClick = { barMenu = true },
                        )
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }, containerColor = Immersive.Popup) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (b) R.string.profile_unblock else R.string.profile_block), style = Immersive.Body) },
                                onClick = {
                                    barMenu = false
                                    if (b) scope.launch {
                                        blocked = false
                                        runCatching { MyspotiRuntime.api.users.unblockUser(uri) }.onFailure {
                                            blocked = true
                                            android.widget.Toast.makeText(context, R.string.profile_block_failed, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else blockAsk = true
                                },
                            )
                        }
                    }
            }
            if (squared) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.background(pageBg, CircleShape),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.accessibility_navigate_back),
                            tint = pageInk,
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (squaredTitleVisible) Text(
                            displayName,
                            modifier = Modifier.background(pageBg, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
                            color = pageInk,
                            style = Immersive.BodyStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    actions()
                }
            } else {
                ImmersiveTopBar(
                    title = displayName, collapse = rememberHeaderCollapse(listState),
                    onBack = { navController.navigateUp() }, actions = { actions() },
                )
            }
        }
    }
    if (blockAsk) ImmersiveAlertDialog(
        onDismissRequest = { blockAsk = false },
        title = { Text(stringResource(R.string.profile_block_title, displayName.ifEmpty { userId }), style = Immersive.Headline) },
        text = { Text(stringResource(R.string.profile_block_confirm, displayName.ifEmpty { userId }), style = Immersive.Subhead) },
        confirmButton = {
            TextButton(onClick = {
                blockAsk = false
                Haptics.confirm()
                scope.launch {
                    runCatching { MyspotiRuntime.api.users.blockUser(uri) }
                        .onSuccess { blocked = true; following = false }
                        .onFailure { android.widget.Toast.makeText(context, R.string.profile_block_failed, android.widget.Toast.LENGTH_SHORT).show() }
                }
            }) { Text(stringResource(R.string.profile_block), style = Immersive.BodyStrong.copy(color = Color(0xFFF15E6C))) }
        },
        dismissButton = {
            TextButton(onClick = { blockAsk = false }) { Text(stringResource(R.string.cancel), style = Immersive.BodyStrong.copy(color = Immersive.Accent)) }
        },
    )
}

/** "1,204 followers": an accent link to the list behind the number. */
@Composable
private fun CountLink(text: String, ink: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = Immersive.Subhead.copy(color = ink),
        modifier = Modifier
            .immersiveClickable(onClick = onClick, pressedScale = 0.95f)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
private fun SquaredProfilePlaylist(
    name: String,
    subtitle: String?,
    artwork: String?,
    ink: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = Immersive.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SquaredArtwork(artwork, name, Modifier.size(48.dp), corner = 9.dp, decodeSize = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(name, color = ink, style = Immersive.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = ink, style = Immersive.Caption) }
        }
    }
}
