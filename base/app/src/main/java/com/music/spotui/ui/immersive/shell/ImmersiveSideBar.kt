package com.music.spotui.ui.immersive.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.data.preferences.ModulesPref
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.LocalLook
import com.music.spotui.ui.navigation.Routes

/** The sidebar's column: the panel, its margin included. */
val ImmersiveSideBarWidth = 96.dp

private val PanelMargin = 12.dp
private val PanelPadding = 6.dp
private val TabHeight = 64.dp
private val TabGap = 4.dp

/**
 * Large windows (tablets, DeX): the Immersive Looks' tabs in a floating glass panel down the left
 * side, in place of the phone tab bar; the mini player stays at the bottom of the page area
 * ([ImmersiveBottomChrome]). Same tabs and navigation as the bar. Hidden on the full-screen
 * routes (player, queue), like the bar.
 *
 * The panel is drawn from the Look's tokens ([Immersive.SideBarShape], [Immersive.Button],
 * [glass], the accent), so a Look restyles it through them, not here.
 */
@Composable
fun ImmersiveSideBar(navController: NavController, modifier: Modifier = Modifier) {
    val tabs = rememberTabNavigation(navController)
    if (tabs.fullScreen) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { ModulesPref.init(context) }
    val modulesEnabled by ModulesPref.enabled.collectAsState()
    val items = remember(modulesEnabled) {
        if (modulesEnabled) listOf(Routes.Home, Routes.Library, Routes.Modules, Routes.Search)
        else listOf(Routes.Home, Routes.Library, Routes.Search)
    }
    val shape = Immersive.SideBarShape
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(ImmersiveSideBarWidth)
            // The same veil the pages draw, so no bare backdrop strip shows beside them.
            .background(com.music.spotui.ui.navigation.pageBackgroundVeil(immersive = true))
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .padding(start = PanelMargin, top = PanelMargin, bottom = PanelMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .width(ImmersiveSideBarWidth - PanelMargin - 4.dp)
                // The mini player's frost. Its Liquid Glass refraction samples the page, which does
                // not extend under the sidebar, so the frost (no lens) is what the panel can match.
                .immersiveGlassSurface(shape, liquid = false)
                .clip(shape)
                .padding(PanelPadding)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TabGap),
        ) {
            items.forEach { item ->
                SideBarTab(item, selected = tabs.selectedTab == item.route, onClick = { tabs.onTabClick(item) })
            }
        }
    }
}

/** One tab: the glyph over its label, on the Look's selection fill while it is the current one. */
@Composable
private fun SideBarTab(item: Routes, selected: Boolean, onClick: () -> Unit) {
    val accent = Immersive.Accent
    val tint = if (selected) accent else Immersive.Text.copy(alpha = 0.65f)
    val shape = Immersive.Button
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    // Keyboard / mouse (DeX) / touch feedback: a fill on hover, a stronger one on press, a ring on focus.
    val feedback = when {
        pressed -> Immersive.Text.copy(alpha = 0.18f)
        hovered -> Immersive.Text.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(TabHeight)
            .clip(shape)
            .then(if (selected) Modifier.background(tabIndicatorColor(LocalLook.current, accent), shape) else Modifier)
            .background(feedback, shape)
            .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painter = painterResource(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            text = tabLabel(item),
            style = TabLabel,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
        )
    }
}
