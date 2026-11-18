package com.music.spotui.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref

/**
 * Astral's dark palette for Material components (switches, sliders, dialogs, spinners,
 * text fields, menus): adapts cleanly across all 5 Looks ([Look]) while keeping cards,
 * menus, dialogs, and bottom sheets non-transparent and legible over Starry Night.
 */
private fun astralColorScheme(
    accent: Color,
    look: Look,
    starryOn: Boolean,
): ColorScheme {
    val cardSurface = when (look) {
        Look.SPOTIFUSION -> SpotifusionCard
        Look.GLASSIFY -> GlassifySurface
        Look.LIQUID_MUSIC -> LiquidMusicSurface
        Look.CLASSIC, Look.IMMERSIVE -> if (starryOn) StarryCard else StarryCardOpaque
    }
    val lowSurface = when (look) {
        Look.SPOTIFUSION -> Color(0xFF181818)
        Look.GLASSIFY -> Color(0xBF181C26)
        Look.LIQUID_MUSIC -> Color(0xC718181B)
        Look.CLASSIC, Look.IMMERSIVE -> if (starryOn) Color(0xD9101C2E) else Color(0xFF101C2E)
    }
    val elevatedSurface = when (look) {
        Look.SPOTIFUSION -> Color(0xFF282828)
        Look.GLASSIFY -> Color(0xD91E222D)
        Look.LIQUID_MUSIC -> Color(0xE01C1C1E)
        Look.CLASSIC, Look.IMMERSIVE -> if (starryOn) Color(0xEB182840) else Color(0xFF1E3250)
    }
    val borderOutline = when (look) {
        Look.SPOTIFUSION -> Color(0xFF3E3E3E)
        Look.GLASSIFY -> GlassifyBorder
        Look.LIQUID_MUSIC -> LiquidMusicBorder
        Look.CLASSIC, Look.IMMERSIVE -> Color(0xFF3A5270)
    }
    val onAccent = when {
        look == Look.SPOTIFUSION -> Color.Black
        accent.luminance() > 0.45f -> Color(0xFF0A1422)
        else -> Color.White
    }
    val secondary = if (look == Look.SPOTIFUSION) Color(0xFFB3B3B3) else StarryBlue
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = cardSurface,
        onSecondaryContainer = Color.White,
        tertiary = AstralTrailLight,
        onTertiary = Color(0xFF0A1422),
        background = AppBackground,
        onBackground = Color.White,
        surface = AppBackground,
        onSurface = Color.White,
        surfaceVariant = cardSurface,
        onSurfaceVariant = if (look == Look.SPOTIFUSION) Color(0xFFB3B3B3) else AppMuted,
        surfaceTint = Color.Transparent,
        surfaceDim = AppBackground,
        surfaceBright = elevatedSurface,
        surfaceContainerLowest = AppBackground,
        surfaceContainerLow = lowSurface,
        surfaceContainer = cardSurface,
        surfaceContainerHigh = elevatedSurface,
        surfaceContainerHighest = elevatedSurface,
        inverseSurface = Color.White,
        inverseOnSurface = Color(0xFF0A1422),
        inversePrimary = accent,
        outline = borderOutline,
        outlineVariant = if (look == Look.SPOTIFUSION) Color(0xFF282828) else Color(0xFF243752),
        error = Color(0xFFF3727F),
        onError = Color.Black,
        scrim = Color(0xFF000000),
    )
}

@Composable
fun SpotuiTheme(
    content: @Composable () -> Unit
) {
    val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
    val starryOn by com.music.spotui.data.preferences.StarryNightPref.enabled.collectAsStateWithLifecycle()
    val look = LookPref.look
    LaunchedEffect(starryOn) { notifyStarryNightChanged() }
    val policy = energy.policy
    LaunchedEffect(policy.videoMaxHeight, policy.allowBackgroundLookups) {
        com.music.spotui.video.MusicVideoController.onEnergyPolicyChanged()
    }
    // A new wallpaper recreates the activity: its accent is read again (Material You, see DynamicColorPref).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    androidx.compose.runtime.remember(configuration) { com.music.spotui.data.preferences.DynamicColorPref.refresh() }
    // Dark only: a saved choice from the withdrawn light palette is ignored.
    val visualScheme: ColorScheme = astralColorScheme(AppAccent, look, starryOn)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.attributes = window.attributes.apply { preferredRefreshRate = policy.displayRefreshCap }
            window.statusBarColor = Color.Transparent.toArgb()
            // White icons on the dark palette.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            // B19: the navigation bar was left to the system — a white band under the always-dark
            // UI (reported in the modal sheets, where the bar was opaque) with dark icons on it.
            window.navigationBarColor = Color.Transparent.toArgb()
            // Without this Android lays its own translucent scrim on a transparent bar (API 29+),
            // which showed as a differently coloured strip under the full-screen Canvas.
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            // Behind the first frame and under overscroll: opaque colour (window background must be opaque).
            val windowBg = when {
                app.myspoti.power.EnergySettings.oledBlack -> Color.Black
                starryOn -> StarrySkyTop
                look == Look.SPOTIFUSION -> SpotifusionBackground
                else -> AppBackground.takeIf { it != Color.Transparent }?.copy(alpha = 1f) ?: StarrySkyTop
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(windowBg.toArgb()))
        }
    }

    MaterialTheme(
        colorScheme = visualScheme,
        typography = Typography,
    ) {
        // Bare Text() uses LocalTextStyle, which MaterialTheme does NOT derive
        // from the typography — so provide Montserrat as the global default so
        // every screen picks it up without touching each Text call.
        // An extension's look may make the text a little smaller or larger (ui.styles.text.scale).
        val density = androidx.compose.ui.platform.LocalDensity.current
        val scale = com.music.spotui.extensions.ExtensionLook.textScale
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides
                if (scale == 1f) density else androidx.compose.ui.unit.Density(density.density, density.fontScale * scale),
        ) {
            androidx.compose.material3.ProvideTextStyle(
                value = androidx.compose.material3.LocalTextStyle.current.copy(
                    fontFamily = Montserrat,
                    letterSpacing = (-0.2).sp,
                ),
                content = content,
            )
        }
    }
}
