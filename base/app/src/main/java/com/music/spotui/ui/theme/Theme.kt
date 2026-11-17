package com.music.spotui.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Astral's dark navy palette for Material components (switches, sliders, dialogs,
 * spinners, text fields, menus): Astral blue/lavender accents on deep navy surfaces
 * with no tonal tint on elevated containers.
 */
private fun astralColorScheme(
    accent: Color,
    mode: com.music.spotui.data.preferences.AstralThemeMode,
): ColorScheme {
    val cardSurface = when (mode) {
        com.music.spotui.data.preferences.AstralThemeMode.STARRY_NIGHT -> StarryCard
        com.music.spotui.data.preferences.AstralThemeMode.GLASSIFY -> GlassifySurface
        com.music.spotui.data.preferences.AstralThemeMode.LIQUID_MUSIC -> LiquidMusicSurface
    }
    val borderOutline = when (mode) {
        com.music.spotui.data.preferences.AstralThemeMode.STARRY_NIGHT -> Color(0xFF3A5270)
        com.music.spotui.data.preferences.AstralThemeMode.GLASSIFY -> GlassifyBorder
        com.music.spotui.data.preferences.AstralThemeMode.LIQUID_MUSIC -> LiquidMusicBorder
    }
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0A1422),
        primaryContainer = accent,
        onPrimaryContainer = Color(0xFF0A1422),
        secondary = StarryBlue,
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
        onSurfaceVariant = AppMuted,
        surfaceTint = Color.Transparent,
        surfaceDim = AppBackground,
        surfaceBright = Color(0xFF1E3250),
        surfaceContainerLowest = AppBackground,
        surfaceContainerLow = Color(0xFF101C2E),
        surfaceContainer = cardSurface,
        surfaceContainerHigh = Color(0xFF182840),
        surfaceContainerHighest = Color(0xFF1E3250),
        inverseSurface = Color.White,
        inverseOnSurface = Color(0xFF0A1422),
        inversePrimary = accent,
        outline = borderOutline,
        outlineVariant = Color(0xFF243752),
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
    val themeMode by com.music.spotui.data.preferences.AstralThemePref.themeMode.collectAsStateWithLifecycle()
    val policy = energy.policy
    androidx.compose.runtime.LaunchedEffect(policy.videoMaxHeight, policy.allowBackgroundLookups) {
        com.music.spotui.video.MusicVideoController.onEnergyPolicyChanged()
    }
    // A new wallpaper recreates the activity: its accent is read again (Material You, see DynamicColorPref).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    androidx.compose.runtime.remember(configuration) { com.music.spotui.data.preferences.DynamicColorPref.refresh() }
    // Dark only: a saved choice from the withdrawn light palette is ignored.
    val visualScheme: ColorScheme = astralColorScheme(AppAccent, themeMode)
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
            // Behind the first frame and under overscroll: opaque night-sky colour (window background must be opaque).
            val windowBg = if (app.myspoti.power.EnergySettings.oledBlack) Color.Black
                else AppBackground.copy(alpha = 1f)
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
