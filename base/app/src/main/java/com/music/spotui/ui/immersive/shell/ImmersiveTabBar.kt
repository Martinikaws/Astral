/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * app/src/main/java/com/music/bitchord/ui/components/GlassNavBar.kt at edc1e6f
 * (and app/src/main/java/com/music/bitchord/ui/components/FloatingBottomBar.kt). Modified for Myspoti, 2026-10.
 */
package com.music.spotui.ui.immersive.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import app.myspoti.engine.R
import com.music.spotui.data.preferences.AstralThemePref
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.ModulesPref
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.ImmersiveSans
import com.music.spotui.ui.look.LocalLook
import com.music.spotui.ui.look.rememberReduceMotion
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.TabReselect

/** The gap between the pill's glass edge and the tabs inside it. */
private val PILL_INSET = 6.dp

/**
 * How far the floating bars stand off the bottom edge: the system navigation bar's inset, but
 * never less than a gesture bar's. With the gesture hint switched off that inset is zero and the
 * bar would drop onto the very edge of the screen.
 */
internal val ImmersiveBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))

/** A gesture bar's inset. */
private val GESTURE_BAR_FLOOR = 15.dp

/**
 * Each tab's own vertical padding, the counterweight to [PILL_INSET]: the pill has no height of
 * its own, so the two numbers are a pair — change one and the bar's height moves unless the other
 * moves against it.
 */
private val TAB_VERTICAL_PADDING = 7.dp

/** The gap between a tab's glyph and its label. */
private val TAB_ICON_LABEL_GAP = 2.dp

/** How wide the floating bars are ever allowed to get: on a tablet they stop growing and centre. */
val ImmersiveBarMaxWidth = 440.dp

internal val TabLabel = TextStyle(
    fontFamily = ImmersiveSans,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.Medium,
)

/** The tab the user is in, how to switch tabs, and whether the bar hides (player, queue). */
internal class TabNavigation(
    val selectedTab: String,
    val onTabClick: (Routes) -> Unit,
    val fullScreen: Boolean,
)

/** The tab bar's navigation, shared by [TabBarContent] and the minimizing [ImmersiveBottomChrome]. */
@Composable
internal fun rememberTabNavigation(navController: NavController): TabNavigation {
    // Visibility follows the screen actually shown, not flags each screen sets.
    val navStack by navController.currentBackStackEntryAsState()
    val currentRoute = navStack?.destination?.route
    val fullScreen = currentRoute == Routes.Player.route ||
        currentRoute == Routes.Queue.route

    // The tab the user is in, even on one of its sub-pages (album, artist...). Kept whether or
    // not the bar shows, so it survives the bar being hidden on the player.
    var selectedTab by rememberSaveable { mutableStateOf(Routes.Home.route) }
    if (currentRoute in TabRoutes) selectedTab = currentRoute!!

    val onTabClick: (Routes) -> Unit = { item ->
        // Standard bottom-tab navigation: each tab keeps its state (no reload, scroll kept).
        if (item.route == selectedTab) {
            if (currentRoute == item.route) {
                // Already on the tab's own page: nothing to reload, only the re-tap shortcut.
                TabReselect.emit(item.route)
            } else if (!navController.popBackStack(item.route, inclusive = false)) {
                // On one of its sub-pages: back to its root page first.
                navController.navigate(item.route) { launchSingleTop = true }
            }
        } else {
            selectedTab = item.route
            navController.navigate(item.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                // Home always opens Home itself (its scroll kept), not the page last left
                // open from it (a playlist, an artist...).
                restoreState = item.route != Routes.Home.route
            }
        }
    }
    return TabNavigation(selectedTab, onTabClick, fullScreen)
}

// Friend activity is not a tab any more: it opens from Home's header, as in Spotify.
private val TabRoutes = listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route, Routes.Modules.route)

/** The tabs the bar shows. */
internal val TabItems = listOf(Routes.Home, Routes.Library, Routes.Modules, Routes.Search)

private val GlassifyTabBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.24f),
        Color.White.copy(alpha = 0.10f),
        Color.White.copy(alpha = 0.04f),
    ),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

/** The fill behind the selected tab, in the bar and the tablet sidebar. */
internal fun tabIndicatorColor(look: Look, accent: Color): Color = when (look) {
    Look.LIQUID_MUSIC -> accent.copy(alpha = 0.22f)
    // Apple's selected tab: a soft grey capsule behind the red glyph.
    Look.CUPERTINO, Look.SQUARED -> Color.White.copy(alpha = 0.14f)
    Look.GLASSIFY -> Color.White.copy(alpha = 0.16f)
    else -> Color.White.copy(alpha = 0.12f)
}

/** The tab bar itself (pill and Search circle), filling the width [modifier] leaves it. */
@Composable
internal fun TabBarContent(
    selectedTab: String,
    onTabClick: (Routes) -> Unit,
    modifier: Modifier,
) {
    // Held for the same reason the content lambda below is keyed: it is captured once per key.
    val currentOnTabClick by androidx.compose.runtime.rememberUpdatedState(onTabClick)
    val context = LocalContext.current
    LaunchedEffect(Unit) { ModulesPref.init(context) }
    val modulesEnabled by ModulesPref.enabled.collectAsState()
    val look = LocalLook.current
    val liquidRefraction by AstralThemePref.liquidGlassRefraction.collectAsState()
    val accent = Immersive.Accent
    val pillTabs = remember(modulesEnabled) {
        if (modulesEnabled) listOf(Routes.Home, Routes.Library, Routes.Modules)
        else listOf(Routes.Home, Routes.Library)
    }
    val pillShape = remember(look) {
        if (look == Look.LIQUID_MUSIC) RoundedCornerShape(20.dp) else RoundedCornerShape(percent = 50)
    }
    val standaloneShape = remember(look) {
        if (look == Look.LIQUID_MUSIC) RoundedCornerShape(20.dp) else CircleShape
    }
    val reduceMotion = rememberReduceMotion()

    // A factory, not a value: each surface gets its own glass modifier styled for the active Look.
    val glassSurface: @Composable () -> Modifier = remember(look, pillShape) {
        {
            when (look) {
                Look.GLASSIFY -> Modifier
                    .shadow(10.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                    .immersiveGlassSurface(pillShape)
                    .border(
                        width = 1.dp,
                        brush = GlassifyTabBorderBrush,
                        shape = pillShape,
                    )
                else -> Modifier.immersiveGlassSurface(pillShape)
            }
        }
    }
    val indicatorColor = tabIndicatorColor(look, accent)

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        FloatingTabBar(
            selectedTabKey = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            tabBarContentModifier = glassSurface,
            colors = FloatingTabBarDefaults.colors(
                backgroundColor = Color.Transparent,
                indicatorColor = indicatorColor,
            ),
            shapes = FloatingTabBarDefaults.shapes(
                tabBarShape = pillShape,
                tabShape = pillShape,
                standaloneTabShape = standaloneShape,
            ),
            sizes = FloatingTabBarDefaults.sizes(
                tabBarContentPadding = PaddingValues(PILL_INSET),
                tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
            ),
            reduceMotion = reduceMotion,
            contentKey = listOf(selectedTab, modulesEnabled, look, accent.value, liquidRefraction),
        ) {
            // Search is the odd one out: a circle/pill of its own beside the pill.
            pillTabs.forEach { item ->
                val tint = if (selectedTab == item.route) accent else Immersive.Text.copy(alpha = 0.65f)
                tab(
                    key = item.route,
                    icon = { TabIcon(item, tint) },
                    title = {
                        Text(
                            text = tabLabel(item),
                            style = TabLabel,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Three tabs in the pill: a long label (Bibliothèque, with large text) shrinks a little before it is cut,
                            // and keeps clear of the tab's rounded ends, which clip its first and last letters.
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
                            modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP).padding(horizontal = 6.dp),
                        )
                    },
                    onClick = { currentOnTabClick(item) },
                )
            }
            standaloneTab(
                key = Routes.Search.route,
                icon = {
                    TabIcon(
                        Routes.Search,
                        if (selectedTab == Routes.Search.route) accent else Immersive.Text.copy(alpha = 0.65f),
                    )
                },
                onClick = { currentOnTabClick(Routes.Search) },
            )
        }
    }
}

@Composable
internal fun tabLabel(item: Routes): String =
    if (item == Routes.Library && com.music.spotui.ui.immersive.cupertino.isCupertino()) stringResource(R.string.cupertino_library)
    else if (item.labelRes != 0) stringResource(item.labelRes) else item.label

@Composable
private fun TabIcon(item: Routes, tint: Color) {
    Icon(
        painter = painterResource(item.icon),
        // The label right below names the tab for TalkBack; the standalone one has none.
        contentDescription = if (item == Routes.Search) tabLabel(item) else null,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}
