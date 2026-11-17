package com.music.spotui.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

import com.music.spotui.data.preferences.AstralThemeMode
import com.music.spotui.data.preferences.AstralThemePref

/** The page: black with the AMOLED setting (an extension's look never undoes it), else a look's background or active Astral theme. */
val AppBackground: Color get() =
    if (app.myspoti.power.EnergySettings.oledBlack) Color.Black
    else com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.BACKGROUND)
        ?: when (AstralThemePref.themeMode.value) {
            AstralThemeMode.STARRY_NIGHT -> Color(0xB80A1422)
            AstralThemeMode.GLASSIFY -> GlassifySurface
            AstralThemeMode.LIQUID_MUSIC -> LiquidMusicBackground
        }
val GridBackground: Color get() =
    com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.SURFACE)
        ?: when (AstralThemePref.themeMode.value) {
            AstralThemeMode.STARRY_NIGHT -> StarryCard
            AstralThemeMode.GLASSIFY -> GlassifySurface
            AstralThemeMode.LIQUID_MUSIC -> LiquidMusicSurface
        }
/** The accent (StarryNight warm starlight by default, or the colour chosen in settings, DynamicColorPref, or an extension's look). */
val AppPalette: Color get() = AppAccent
val AppAccent: Color get() =
    com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.ACCENT)
        ?: when (AstralThemePref.themeMode.value) {
            AstralThemeMode.LIQUID_MUSIC ->
                com.music.spotui.data.preferences.DynamicColorPref.accent()
                    ?: com.music.spotui.data.api.SupportReminder.accentColor.takeUnless {
                        it == com.music.spotui.data.api.SupportReminder.Accent.GREEN.color
                    }
                    ?: AstralTrailMid
            AstralThemeMode.GLASSIFY ->
                com.music.spotui.data.preferences.DynamicColorPref.accent()
                    ?: StarryBlue
            AstralThemeMode.STARRY_NIGHT ->
                com.music.spotui.data.preferences.DynamicColorPref.accent()
                    ?: com.music.spotui.data.api.SupportReminder.accentColor.takeUnless {
                        it == com.music.spotui.data.api.SupportReminder.Accent.GREEN.color
                    }
                    ?: StarryAccent
        }

/** Glassify translucent frosted surface and crisp 1dp border. */
val GlassifySurface = Color(0xA6141C2B)
val GlassifyBorder = Color.White.copy(alpha = 0.18f)

/** LiquidMusic translucent liquid-glass surfaces. */
val LiquidMusicBackground = Color(0x990D1624)
val LiquidMusicSurface = Color(0xB3152238)
val LiquidMusicBorder = Color.White.copy(alpha = 0.22f)

/** Spicetify StarryNight `[Base]` palette. */
val StarrySkyTop = Color(0xFF000000)
val StarrySkyBottom = Color(0xFF142B44)
val StarryCard = Color(0xFF152238)
val StarryAccent = Color(0xFFFFF3C4)
val StarryBlue = Color(0xFF4687D6)
val StarrySubtext = Color(0xFFADB5BD)

/** Astral brand palette. */
val AstralMidnight = Color(0xFF060F2B)
val AstralNavy = Color(0xFF0B1B45)
val AstralTrailLight = Color(0xFFB5D4F4)
val AstralTrailMid = Color(0xFF85B7EB)
val AstralLavender = Color(0xFFCECBF6)

/** Primary text and icons. */
val AppText: Color = Color.White
/** Secondary text and icons. */
val AppMuted: Color = StarrySubtext
/** Destructive actions (delete, leave, block) and failures shown as text. */
val AppDestructive = Color(0xFFF15E6C)
/** What a tint is mixed with to make a header. */
val AppShade: Color = Color.Black

/** One of the palette's raised greys (#181818 ... #3E3E3E): "a bit raised from the page". */
fun tone(argb: Long): Color = Color(argb)

/** The cover's colour as a page header: darkened toward black, so the title stays readable. */
fun headerTint(tint: Color, amount: Float): Color = lerp(tint, Color.Black, amount)
