package com.music.spotui.ui.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.ui.look.ImmersiveDisplay
import com.music.spotui.ui.look.ImmersiveSans
import com.music.spotui.ui.theme.AstralLavender
import com.music.spotui.ui.theme.AstralTrailLight
import com.music.spotui.ui.theme.GlassifyBackground
import com.music.spotui.ui.theme.GlassifyBorder
import com.music.spotui.ui.theme.GlassifyGlassFill
import com.music.spotui.ui.theme.GlassifySurface
import com.music.spotui.ui.theme.LiquidMusicAccent
import com.music.spotui.ui.theme.LiquidMusicBackground
import com.music.spotui.ui.theme.LiquidMusicBorder
import com.music.spotui.ui.theme.LiquidMusicSurface
import com.music.spotui.ui.theme.SpotifusionBackground
import com.music.spotui.ui.theme.SpotifusionCard
import com.music.spotui.ui.theme.SpotifusionGreen
import com.music.spotui.ui.theme.StarryAccent
import com.music.spotui.ui.theme.StarryBlue
import com.music.spotui.ui.theme.StarryCard
import com.music.spotui.ui.theme.StarryCardOpaque
import com.music.spotui.ui.theme.StarrySubtext
import com.music.spotui.ui.theme.dynamicCoverAccent
import com.music.spotui.ui.theme.starryNightRevision

/*
 * The Immersive / Interactive looks' tokens, shared by every interactive screen so they read as
 * one shell: StarryNight transparency when enabled, Glassify specular frosted glass, or
 * LiquidMusic 20dp iOS refraction panels.
 */
object Immersive {
    // Colours.
    /** Transparent when Starry Night is active so the root StarryNightBackdrop shines through cleanly. */
    val Page: Color get() {
        @Suppress("UNUSED_EXPRESSION")
        starryNightRevision
        return if (app.myspoti.power.EnergySettings.oledBlack) Color.Black
        else com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.BACKGROUND)
            ?: if (StarryNightPref.enabled.value) Color.Transparent
            else when (LookPref.look) {
                Look.CLASSIC, Look.IMMERSIVE -> Color(0xFF0A1422)
                Look.SPOTIFUSION -> SpotifusionBackground
                Look.GLASSIFY -> GlassifyBackground
                Look.LIQUID_MUSIC -> LiquidMusicBackground
            }
    }
    /** Non-transparent card & panel fill so text stays legible over Starry Night and ambient layers. */
    val Raised: Color get() {
        @Suppress("UNUSED_EXPRESSION")
        starryNightRevision
        return com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.SURFACE)
            ?: when (LookPref.look) {
                Look.SPOTIFUSION -> SpotifusionCard
                Look.GLASSIFY -> GlassifySurface
                Look.LIQUID_MUSIC -> LiquidMusicSurface
                Look.CLASSIC, Look.IMMERSIVE -> if (StarryNightPref.enabled.value) StarryCard else StarryCardOpaque
            }
    }
    val Separator = AstralTrailLight.copy(alpha = 0.14f)
    val Text = Color.White
    val TextSecondary = StarrySubtext
    val TextTertiary = AstralLavender.copy(alpha = 0.50f)
    /** Look-aware accent (StarryNight warm starlight, Glassify cover accent / sky-blue, LiquidMusic cover accent / #FA2D48). */
    val Accent: Color get() = com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.ACCENT)
        ?: when (LookPref.look) {
            Look.SPOTIFUSION -> SpotifusionGreen
            Look.GLASSIFY -> dynamicCoverAccent
                ?: com.music.spotui.data.api.SupportReminder.run { if (immersiveAccentChosen) immersiveAccent.color else StarryBlue }
            Look.LIQUID_MUSIC -> dynamicCoverAccent
                ?: com.music.spotui.data.api.SupportReminder.run { if (immersiveAccentChosen) immersiveAccent.color else LiquidMusicAccent }
            Look.IMMERSIVE ->
                com.music.spotui.data.api.SupportReminder.run { if (immersiveAccentChosen) immersiveAccent.color else StarryAccent }
            Look.CLASSIC ->
                com.music.spotui.data.preferences.DynamicColorPref.accent()
                    ?: com.music.spotui.data.api.SupportReminder.accentColor
        }
    val GlassFill: Color get() = when (LookPref.look) {
        Look.GLASSIFY -> GlassifyGlassFill
        Look.LIQUID_MUSIC -> LiquidMusicSurface
        Look.SPOTIFUSION -> SpotifusionCard
        Look.CLASSIC, Look.IMMERSIVE -> StarryCard
    }
    val GlassEdge: Color get() = when (LookPref.look) {
        Look.GLASSIFY -> GlassifyBorder
        Look.LIQUID_MUSIC -> LiquidMusicBorder
        Look.SPOTIFUSION -> Color.White.copy(alpha = 0.10f)
        Look.CLASSIC, Look.IMMERSIVE -> Color.White.copy(alpha = 0.14f)
    }
    /** The red of a destructive swipe, menu entry or confirmation (Leave, Remove, an error). */
    val Destructive = Color(0xFFFF453A)

    // Shapes.
    val CoverSmall: Shape = RoundedCornerShape(6.dp)
    val CoverMedium: Shape = RoundedCornerShape(10.dp)
    val CoverLarge: Shape = RoundedCornerShape(14.dp)
    val Card: Shape get() = when (LookPref.look) {
        Look.LIQUID_MUSIC -> RoundedCornerShape(20.dp)
        Look.GLASSIFY -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(14.dp)
    }
    val Button: Shape get() = if (LookPref.look == Look.LIQUID_MUSIC) RoundedCornerShape(20.dp) else RoundedCornerShape(14.dp)

    // Type (Inter, or the phone's system font when chosen in the settings: AppFontPref).
    private class Type(val sans: androidx.compose.ui.text.font.FontFamily, display: androidx.compose.ui.text.font.FontFamily) {
        private val base = TextStyle(fontFamily = sans, color = Text)
        private val shown = TextStyle(fontFamily = display, color = Text)
        val largeTitle = shown.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        val title = shown.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        val headline = shown.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        val body = base.copy(fontSize = 16.sp, lineHeight = 21.sp)
        val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
        val subhead = base.copy(fontSize = 14.sp, lineHeight = 19.sp, color = TextSecondary)
        val caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
        val lyric = shown.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    }
    // The styles of the font in use, made again only when the font changes. Reading the font reads
    // its setting: a screen drawn with these styles redraws when it changes.
    @Volatile private var type: Type? = null
    private fun type(): Type {
        val sans = ImmersiveSans
        return type?.takeIf { it.sans === sans } ?: Type(sans, ImmersiveDisplay).also { type = it }
    }
    val LargeTitle: TextStyle get() = type().largeTitle
    val Title: TextStyle get() = type().title
    val Headline: TextStyle get() = type().headline
    val Body: TextStyle get() = type().body
    val BodyStrong: TextStyle get() = type().bodyStrong
    val Subhead: TextStyle get() = type().subhead
    val Caption: TextStyle get() = type().caption
    val Lyric: TextStyle get() = type().lyric

    // Spacing.
    val Gutter = 20.dp
    val RowHeight = 60.dp
    /** Room the floating tab bar and mini player take at the bottom of every page. */
    val BottomChrome = 170.dp
}

/** A translucent bar or capsule (tab bar, mini player, menus). */
fun Modifier.glass(shape: Shape = CircleShape): Modifier =
    if (LookPref.look == Look.GLASSIFY) {
        this
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
            .background(GlassifyGlassFill, shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.04f),
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                shape = shape,
            )
    } else {
        this.background(Immersive.GlassFill, shape).border(1.dp, Immersive.GlassEdge, shape)
    }
