/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt. Modified for Astral, 2026-11.
 *
 * Adapted to Astral's accounts, playback preferences and sources.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.UserCircle
import com.adamglin.phosphoricons.regular.Info
import com.adamglin.phosphoricons.regular.CaretLeft
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.Palette
import com.adamglin.phosphoricons.regular.PlayCircle
import com.adamglin.phosphoricons.regular.Scroll
import com.adamglin.phosphoricons.regular.MicrophoneStage
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import com.adamglin.phosphoricons.regular.Trash
import com.music.spotui.data.AppStorage
import com.music.spotui.data.offline.OfflineDownloader
import com.music.spotui.data.offline.OfflineStore
import com.music.spotui.data.preferences.AppShufflePref
import com.music.spotui.data.preferences.AutoplayPref
import com.music.spotui.data.preferences.CrossfadePref
import com.music.spotui.data.preferences.DownloadQuality
import com.music.spotui.data.preferences.EqualizerPref
import com.music.spotui.data.preferences.LyricsOfflinePref
import com.music.spotui.data.preferences.StreamingQuality
import com.music.spotui.data.preferences.TransitionPref
import com.music.spotui.data.preferences.getDownloadQuality
import com.music.spotui.data.preferences.getStreamingQuality
import com.music.spotui.data.preferences.setDownloadQuality
import com.music.spotui.data.preferences.setStreamingQuality
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.pages.ImmersiveSettingsScreen
import com.music.spotui.ui.immersive.pages.SettingsPage
import com.music.spotui.ui.immersive.squared.Ink
import com.music.spotui.ui.immersive.squared.InkDim
import com.music.spotui.ui.immersive.squared.SquaredTheme
import com.music.spotui.ui.screens.cacheSizeLabel
import com.music.spotui.ui.screens.eqPresetLabel
import app.myspoti.extensions.Action
import com.music.spotui.extensions.Extensions
import com.music.spotui.ui.components.ReceiverSourceRows
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.screens.MetadataOrderList
import com.music.spotui.ui.screens.SourceLook
import com.music.spotui.ui.screens.SourceOrderList
import com.music.spotui.ui.screens.installedRoute
import kotlinx.coroutines.launch

/** The pages of the Squared look's settings; the index is the first. */
private enum class Page { Index, Account, About, Playback, Sources, Equalizer, Glass, Downloads, App, Library, Lyrics, Data }

/** Settings tabs for the Squared look. */
@Composable
fun SquaredSettingsScreen(navController: NavController) {
    var page by rememberSaveable { mutableStateOf(Page.Index) }
    var astralTab by rememberSaveable { mutableStateOf(false) }
    // Registered before the inner screen's own handler, which therefore pops its pages first.
    BackHandler(enabled = page != Page.Index) { page = Page.Index }
    BackHandler(enabled = page == Page.Index) { navController.popBackStack() }

    val store = SquaredGlassStore.of(LocalContext.current)
    val config by store.config.collectAsStateWithLifecycle()
    val sharedPage = when (page) {
        Page.About -> SettingsPage.App
        Page.App -> SettingsPage.Display
        Page.Library -> SettingsPage.Library
        Page.Lyrics -> SettingsPage.Lyrics
        Page.Data -> SettingsPage.Data
        else -> null
    }
    if (sharedPage != null) {
        ImmersiveSettingsScreen(navController, initialPage = sharedPage, onBack = { page = Page.Index })
        return
    }
    SquaredTheme(seed = null, darkTheme = true) {
        val accent = rememberSettingsAccent(config)
        CompositionLocalProvider(LocalSettingsAccent provides accent) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val padding = PaddingValues(bottom = Immersive.BottomChrome)
                // Back from a page is the index; back from the index leaves Settings.
                val back: () -> Unit = { if (page == Page.Index) navController.popBackStack() else page = Page.Index }
                val header: @Composable (Int) -> Unit = { title -> PageHeader(stringResource(title), back) }
                when (page) {
                    Page.Index -> Index(
                        padding = padding,
                        header = { header(R.string.squared_settings_title) },
                        open = { page = it },
                        astral = astralTab,
                        onTab = { astralTab = it },
                    )
                    Page.Account -> com.music.spotui.ui.immersive.pages.AccountsPage(back)
                    Page.Playback -> PlaybackPage(padding) { header(R.string.settings_section_playback) }
                    Page.Equalizer -> SquaredEqualizerPage(padding) { header(R.string.equalizer_title) }
                    Page.Glass -> SquaredGlassPage(store, padding) { header(R.string.squared_page_glass) }
                    Page.Downloads -> DownloadsPage(padding) { header(R.string.settings_section_downloads) }
                    Page.Sources -> SourcesPage(padding, navController) { header(R.string.settings_sources_tab) }
                    Page.About, Page.App, Page.Library, Page.Lyrics, Page.Data -> Unit
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(Ink.copy(alpha = 0.10f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Regular.CaretLeft,
                contentDescription = stringResource(R.string.squared_back),
                tint = Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- index

@Composable
private fun Index(
    padding: PaddingValues,
    header: @Composable () -> Unit,
    open: (Page) -> Unit,
    astral: Boolean,
    onTab: (Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { header() }
        item {
            TabRow(selectedTabIndex = if (astral) 1 else 0) {
                Tab(selected = !astral, onClick = { onTab(false) }, text = { Text(stringResource(R.string.first_look_square)) })
                Tab(selected = astral, onClick = { onTab(true) }, text = { Text(stringResource(R.string.squared_tile_astral)) })
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!astral) {
                    Tile(MenuTile(Page.Account, PhosphorIcons.Regular.UserCircle, R.string.settings_account_provider_title,
                        R.string.settings_account_provider_detail), Modifier.fillMaxWidth(), open)
                }
                val pages = if (astral) listOf(
                    MenuTile(Page.Sources, PhosphorIcons.Regular.DownloadSimple, R.string.settings_sources_tab, R.string.settings_sources_detail),
                    MenuTile(Page.Library, PhosphorIcons.Regular.Scroll, R.string.settings_section_library),
                    MenuTile(Page.Lyrics, PhosphorIcons.Regular.MicrophoneStage, R.string.settings_section_lyrics),
                    MenuTile(Page.Data, PhosphorIcons.Regular.GearSix, R.string.settings_section_data),
                ) else listOf(
                    MenuTile(Page.Playback, PhosphorIcons.Regular.PlayCircle, R.string.settings_section_playback),
                    MenuTile(Page.Equalizer, PhosphorIcons.Regular.SlidersHorizontal, R.string.equalizer_title, R.string.squared_tile_equalizer_note),
                    MenuTile(Page.Glass, PhosphorIcons.Regular.Palette, R.string.squared_page_glass, R.string.squared_tile_glass_note),
                    MenuTile(Page.Downloads, PhosphorIcons.Regular.DownloadSimple, R.string.settings_section_downloads, R.string.squared_tile_downloads_note),
                    MenuTile(Page.App, PhosphorIcons.Regular.GearSix, R.string.settings_section_display),
                    MenuTile(Page.About, PhosphorIcons.Regular.Info, R.string.settings_tab_about),
                )
                pages.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { tile -> Tile(tile, Modifier.weight(1f), open) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class MenuTile(val page: Page, val icon: ImageVector, val title: Int, val note: Int? = null)

// Adapted from Lelonio/Square's settings index and Tile (GPL-3.0).
@Composable
private fun Tile(tile: MenuTile, modifier: Modifier, open: (Page) -> Unit) {
    Column(
        modifier.heightIn(min = 132.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink.copy(alpha = 0.07f))
            .clickable { open(tile.page) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RowIcon(tile.icon)
        Text(stringResource(tile.title), style = MaterialTheme.typography.titleMedium, color = Ink)
        tile.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = InkDim) }
    }
}

// ---------------------------------------------------------------- shared

/** A page of folding groups: the heading of each opens its sections. */
@Composable
private fun GroupedPage(
    padding: PaddingValues,
    header: @Composable () -> Unit,
    content: LazyListScope.(isOpen: (SettingsGroup) -> Boolean, toggle: (SettingsGroup) -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { header() }
        content(
            { it.name in open },
            { g -> open = if (g.name in open) open - g.name else open + g.name },
        )
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
    }
}

private fun LazyListScope.settingsGroup(
    group: SettingsGroup,
    isOpen: (SettingsGroup) -> Boolean,
    toggle: (SettingsGroup) -> Unit,
    body: LazyListScope.() -> Unit,
) {
    val open = isOpen(group)
    groupHeading(group, open) { toggle(group) }
    if (open) {
        body()
        groupEnd(group)
    }
}

// ---------------------------------------------------------------- sources

@Composable
private fun SourcesPage(padding: PaddingValues, navController: NavController, header: @Composable () -> Unit) {
    val registry by Extensions.registry.collectAsStateWithLifecycle()
    val look = SourceLook(Ink, InkDim, settingsAccent(), MaterialTheme.typography.bodyLarge, MaterialTheme.typography.bodySmall)
    val openExtension: (String) -> Unit = { navController.navigate(installedRoute(it)) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { header() }
        item {
            SettingsSection(stringResource(R.string.listen_on)) {
                ReceiverSourceRows { title, detail, onClick ->
                    SettingsActionRow(title, detail = detail, onClick = onClick)
                }
            }
        }
        for (action in listOf(Action.AUDIO_SOURCE, Action.DOWNLOAD_SOURCE)) {
            if (Extensions.sources(action).isEmpty()) continue
            val audio = action == Action.AUDIO_SOURCE
            item(action.key) {
                SettingsSection(stringResource(if (audio) R.string.sources_audio_title else R.string.sources_download_title)) {
                    SourceOrderList(action, withAccount = audio, look = look, onOpen = openExtension)
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.metadata_title)) {
                MetadataOrderList(look)
            }
        }
        item {
            SettingsSection(title = null) {
                com.music.spotui.ui.screens.BundledFlacSources(navController)
            }
        }
        item {
            SettingsSection(stringResource(R.string.extensions_title)) {
                registry.extensions.forEach { extension ->
                    val settings = Extensions.manifest(extension.id)?.settings.orEmpty()
                    if (settings.isNotEmpty()) SettingsActionRow(extension.name, detail = settings.joinToString { it.label }) {
                        openExtension(extension.id)
                    }
                }
                SettingsActionRow(stringResource(R.string.extensions_title), detail = stringResource(R.string.extensions_row_detail)) {
                    navController.navigate(Routes.Extensions.route)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- playback

private val StreamingChoices = listOf(
    Triple(StreamingQuality.AUTO, R.string.quality_streaming_auto, R.string.quality_streaming_auto_detail),
    Triple(StreamingQuality.LOW, R.string.quality_streaming_low, R.string.quality_streaming_low_detail),
    Triple(StreamingQuality.NORMAL, R.string.quality_streaming_normal, R.string.quality_streaming_normal_detail),
    Triple(StreamingQuality.HIGH, R.string.quality_streaming_high, R.string.quality_streaming_high_detail),
    Triple(StreamingQuality.VERY_HIGH, R.string.quality_streaming_very_high, R.string.quality_streaming_very_high_detail),
)

private val DownloadChoices = listOf(
    Triple(DownloadQuality.OFF, R.string.quality_download_off, R.string.quality_download_off_detail),
    Triple(DownloadQuality.SMALL, R.string.quality_download_small, R.string.quality_download_small_detail),
    Triple(DownloadQuality.CD, R.string.quality_download_cd, R.string.quality_download_cd_detail),
    Triple(DownloadQuality.HI_RES, R.string.quality_download_hires, R.string.quality_download_hires_detail),
    Triple(DownloadQuality.ASK, R.string.quality_download_ask, R.string.quality_download_ask_detail),
)

@Composable
private fun PlaybackPage(padding: PaddingValues, header: @Composable () -> Unit) {
    val ctx = LocalContext.current
    GroupedPage(padding, header) { isOpen, toggle ->
        settingsGroup(SettingsGroup.SOUND, isOpen, toggle) {
            groupItem("quality-wifi", SettingsGroup.SOUND) { StreamingSection(cellular = false) }
            groupItem("quality-cell", SettingsGroup.SOUND) { StreamingSection(cellular = true) }
            groupItem("transitions", SettingsGroup.SOUND) {
                val mode = TransitionPref.mode
                SettingsSection(stringResource(R.string.settings_transitions_title)) {
                    listOf(
                        Triple(TransitionPref.Mode.OFF, R.string.settings_crossfade_off, R.string.settings_transition_off_detail),
                        Triple(TransitionPref.Mode.CROSSFADE, R.string.settings_crossfade_title, R.string.settings_transition_crossfade_detail),
                        Triple(TransitionPref.Mode.AUTOMIX, R.string.settings_automix_title, R.string.settings_transition_automix_detail),
                    ).forEachIndexed { i, (m, title, detail) ->
                        if (i > 0) SettingsDivider()
                        SettingsChoiceRow(stringResource(title), selected = mode == m, detail = stringResource(detail)) {
                            TransitionPref.set(ctx, m)
                        }
                    }
                    if (mode == TransitionPref.Mode.CROSSFADE) {
                        SettingsDivider()
                        // Shown from here while the finger moves; the player is handed the length once, on release.
                        var dragged by remember(CrossfadePref.seconds) {
                            mutableFloatStateOf(CrossfadePref.seconds.coerceIn(1, CrossfadePref.MAX).toFloat())
                        }
                        SettingsSliderRow(
                            label = stringResource(R.string.settings_crossfade_length),
                            value = dragged,
                            range = 1f..CrossfadePref.MAX.toFloat(),
                            readout = stringResource(R.string.settings_transition_crossfade_value, dragged.roundToInt()),
                            onFinished = { CrossfadePref.set(ctx, dragged.roundToInt().coerceAtLeast(1)) },
                            onChange = { dragged = it },
                        )
                    }
                }
            }
            groupItem("speed", SettingsGroup.SOUND) {
                SettingsSection(stringResource(R.string.equalizer_section_playback)) {
                    EqualizerPref.SPEEDS.forEachIndexed { i, s ->
                        if (i > 0) SettingsDivider()
                        SettingsChoiceRow(
                            label = com.music.spotui.ui.screens.speedText(s),
                            selected = EqualizerPref.speed == s,
                            detail = if (i == 0) stringResource(R.string.equalizer_speed) else null,
                        ) { EqualizerPref.setSpeed(ctx, s) }
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.equalizer_skip_silence),
                        detail = stringResource(R.string.equalizer_skip_silence_detail),
                        checked = EqualizerPref.skipSilence,
                        onChange = { EqualizerPref.setSkipSilence(ctx, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.spatial_enable),
                        detail = stringResource(R.string.spatial_detail),
                        checked = EqualizerPref.spatial,
                        onChange = { EqualizerPref.setSpatial(ctx, it) },
                    )
                    if (EqualizerPref.enabled) {
                        SettingsDivider()
                        SettingsInfoRow(stringResource(R.string.equalizer_title), stringResource(eqPresetLabel(EqualizerPref.preset)))
                    }
                }
            }
        }

        settingsGroup(SettingsGroup.LISTENING, isOpen, toggle) {
            groupItem("listening", SettingsGroup.LISTENING) {
                var keepPlaying by remember { mutableStateOf(app.myspoti.host.android.PlaybackService.keepsPlayingOnClose(ctx)) }
                var headsetSkip by remember { mutableStateOf(app.myspoti.host.android.PlaybackService.skipsOnEpisodes(ctx)) }
                SettingsSection(null) {
                    SettingsSwitchRow(
                        stringResource(R.string.settings_autoplay_title), AutoplayPref.on,
                        detail = stringResource(R.string.settings_autoplay_detail),
                        onChange = { AutoplayPref.set(ctx, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        stringResource(R.string.myspoti_shuffle_title), AppShufflePref.on,
                        detail = stringResource(R.string.myspoti_shuffle_detail),
                        onChange = { AppShufflePref.set(ctx, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        stringResource(R.string.settings_keep_playing_title), keepPlaying,
                        detail = stringResource(R.string.settings_keep_playing_detail),
                        onChange = { keepPlaying = it; app.myspoti.host.android.PlaybackService.setKeepsPlayingOnClose(ctx, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        stringResource(R.string.settings_headset_skip_title), headsetSkip,
                        detail = stringResource(R.string.settings_headset_skip_detail),
                        onChange = { headsetSkip = it; app.myspoti.host.android.PlaybackService.setSkipsOnEpisodes(ctx, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingSection(cellular: Boolean) {
    val ctx = LocalContext.current
    var current by remember(cellular) { mutableStateOf(getStreamingQuality(ctx, cellular)) }
    val premium by app.myspoti.spotify.AccountPlan.premium.collectAsStateWithLifecycle()
    val choices = StreamingChoices.filter { it.first != StreamingQuality.VERY_HIGH || premium == true || current == StreamingQuality.VERY_HIGH }
    SettingsSection(stringResource(if (cellular) R.string.quality_streaming_cell_title else R.string.quality_streaming_wifi_title)) {
        choices.forEachIndexed { i, (value, title, detail) ->
            if (i > 0) SettingsDivider()
            SettingsChoiceRow(stringResource(title), selected = current == value, detail = stringResource(detail)) {
                current = value
                setStreamingQuality(ctx, value, cellular)
            }
        }
    }
}

// ---------------------------------------------------------------- downloads

@Composable
private fun DownloadsPage(padding: PaddingValues, header: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val usage by produceState<AppStorage.Usage?>(null, refresh) { value = AppStorage.usage(ctx) }
    val size = android.text.format.Formatter.formatShortFileSize(ctx, OfflineStore.totalBytes())

    GroupedPage(padding, header) { isOpen, toggle ->
        settingsGroup(SettingsGroup.DOWNLOADS, isOpen, toggle) {
            groupItem("download-quality", SettingsGroup.DOWNLOADS) {
                var current by remember { mutableStateOf(getDownloadQuality(ctx)) }
                SettingsSection(stringResource(R.string.quality_download_title)) {
                    DownloadChoices.forEachIndexed { i, (value, title, detail) ->
                        if (i > 0) SettingsDivider()
                        SettingsChoiceRow(stringResource(title), selected = current == value, detail = stringResource(detail)) {
                            current = value
                            setDownloadQuality(ctx, value)
                        }
                    }
                }
            }
            groupItem("download-lyrics", SettingsGroup.DOWNLOADS) {
                SettingsSection(null) {
                    SettingsSwitchRow(
                        stringResource(R.string.settings_download_lyrics_title), LyricsOfflinePref.on,
                        detail = stringResource(R.string.settings_download_lyrics_detail),
                        onChange = { LyricsOfflinePref.set(ctx, it) },
                    )
                }
            }
        }
        settingsGroup(SettingsGroup.CLEANUP, isOpen, toggle) {
            groupItem("cache-size", SettingsGroup.CLEANUP) {
                val opened = AppStorage.mediaCacheMbOpen
                SettingsSection(stringResource(R.string.settings_storage_media_cache)) {
                    AppStorage.MEDIA_CACHE_CHOICES.forEachIndexed { i, mb ->
                        if (i > 0) SettingsDivider()
                        SettingsChoiceRow(cacheSizeLabel(mb), selected = AppStorage.mediaCacheMb == mb) { AppStorage.chooseMediaCacheMb(mb) }
                    }
                    SettingsDivider()
                    Text(
                        stringResource(
                            if (opened != null && opened != AppStorage.mediaCacheMb) R.string.settings_storage_media_cache_restart
                            else R.string.settings_storage_media_cache_detail,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            groupItem("clear", SettingsGroup.CLEANUP) {
                SettingsSection(null) {
                    SettingsActionRow(
                        label = stringResource(R.string.settings_storage_clear_cache),
                        detail = usage?.let {
                            stringResource(R.string.settings_storage_clear_cache_detail, android.text.format.Formatter.formatShortFileSize(ctx, it.caches))
                        },
                        enabled = !clearing,
                    ) {
                        clearing = true
                        scope.launch {
                            AppStorage.clearCaches(ctx)
                            clearing = false
                            refresh++
                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.settings_storage_cleared), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (OfflineStore.tracks.isNotEmpty()) {
                        SettingsDivider()
                        SettingsActionRow(
                            label = stringResource(R.string.settings_item_remove_downloads_title),
                            detail = stringResource(R.string.downloads_size, size),
                            icon = PhosphorIcons.Regular.Trash,
                            destructive = true,
                        ) { ask = true }
                    }
                }
            }
        }
    }

    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(stringResource(R.string.settings_storage_remove_downloads_dialog_title)) },
        text = { Text(stringResource(R.string.playlist_entity_remove_download_dialog_description)) },
        confirmButton = {
            TextButton(onClick = { ask = false; OfflineDownloader.removeAll() }) {
                Text(stringResource(R.string.playlist_entity_remove_download_dialog_positive_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
