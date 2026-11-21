package com.music.spotui.ui.immersive

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.ui.look.ImmersiveDisplay
import com.music.spotui.ui.look.ImmersiveSans
import com.music.spotui.ui.theme.AstralLavender
import com.music.spotui.ui.theme.AstralTrailLight
import com.music.spotui.ui.theme.CupertinoBackground
import com.music.spotui.ui.theme.CupertinoBorder
import com.music.spotui.ui.theme.CupertinoElevated
import com.music.spotui.ui.theme.CupertinoGlassSolid
import com.music.spotui.ui.theme.CupertinoGroup
import com.music.spotui.ui.theme.CupertinoSecondary
import com.music.spotui.ui.theme.CupertinoTertiary
import com.music.spotui.ui.theme.GlassifyBackground
import com.music.spotui.ui.theme.GlassifyBorder
import com.music.spotui.ui.theme.GlassifyGlassFill
import com.music.spotui.ui.theme.GlassifyTint
import com.music.spotui.ui.theme.LiquidMusicBackground
import com.music.spotui.ui.theme.LiquidMusicBorder
import com.music.spotui.ui.theme.LiquidMusicGroup
import com.music.spotui.ui.theme.LiquidMusicSurface
import com.music.spotui.ui.theme.SpotifusionBackground
import com.music.spotui.ui.theme.SpotifusionCard
import com.music.spotui.ui.theme.SpotifusionElevated
import com.music.spotui.ui.theme.StarryAccent
import com.music.spotui.ui.theme.StarryCard
import com.music.spotui.ui.theme.StarryCardOpaque
import com.music.spotui.ui.theme.StarryHairline
import com.music.spotui.ui.theme.StarrySubtext
import com.music.spotui.ui.theme.lookAccentOrNull
import com.music.spotui.ui.theme.starryNightRevision

/** How a Look draws the border of its surfaces: none, a flat 1dp hairline, or a Glassify specular gradient rim. */
enum class EdgeStyle { NONE, HAIRLINE, SPECULAR }

/** How a Look lays out the rows of a Settings-like page: separate rounded cards, or iOS inset grouped lists. */
enum class GroupStyle { CARD, INSET_LIST }

/**
 * Everything that makes one Look read differently from the others, in one place. The screens never
 * branch on [Look] themselves: they read these through [Immersive] (and [immersiveSurface]), so a new
 * Look, or a change to one, reaches every Immersive page at once.
 *
 * @param page opaque page colour (the page is transparent when the star backdrop is drawn).
 * @param surface the fill of a card, group or panel.
 * @param edge how that surface is bordered, in [edgeColor].
 * @param groupStyle cards or inset lists for the page groups.
 * @param cardRadius radius of cards and tiles; [groupRadius] of the page groups; [buttonRadius] of buttons.
 * @param divider / [dividerThickness] the line between the rows of a group.
 * @param switchTrackOff the off track of a switch; [switchThumbOn] the thumb while on.
 * @param sliderTrack the inactive part of a slider.
 * @param accentFollowsCover true when the accent is the playing cover's colour (else a fixed colour).
 * @param titleWeight / [titleSpacing] the character of the titles.
 * @param chromeRadius corner radius of the floating chrome: the mini player and the tablet sidebar.
 */
@Immutable
class LookTokens(
    val page: Color,
    val surface: Color,
    val edge: EdgeStyle,
    val edgeColor: Color,
    val groupStyle: GroupStyle,
    val cardRadius: Dp,
    val groupRadius: Dp,
    val buttonRadius: Dp,
    val divider: Color,
    val dividerThickness: Dp,
    val switchTrackOff: Color,
    val switchThumbOn: Color,
    val sliderTrack: Color,
    val accentFollowsCover: Boolean,
    val titleWeight: FontWeight,
    val titleSpacing: TextUnit,
    val largeTitleSize: TextUnit,
    val gutter: Dp,
    val chromeRadius: Dp = 28.dp,
)

private val ClassicTokens = LookTokens(
    page = Color(0xFF0A1422),
    surface = StarryCardOpaque,
    edge = EdgeStyle.NONE,
    edgeColor = Color.White.copy(alpha = 0.14f),
    groupStyle = GroupStyle.CARD,
    cardRadius = 14.dp,
    groupRadius = 14.dp,
    buttonRadius = 14.dp,
    divider = AstralTrailLight.copy(alpha = 0.14f),
    dividerThickness = 0.5.dp,
    switchTrackOff = Color.White.copy(alpha = 0.18f),
    switchThumbOn = Color.White,
    sliderTrack = Color.White.copy(alpha = 0.20f),
    accentFollowsCover = false,
    titleWeight = FontWeight.Bold,
    titleSpacing = 0.sp,
    largeTitleSize = 32.sp,
    gutter = 20.dp,
)

/** Astral Interactive: mesh / cover accent, 14dp cards, no borders. */
private val InteractiveTokens = ClassicTokens.copy(accentFollowsCover = true)

/** Spotifusion 1:1: flat Spotify dark, 8dp cards, pill buttons, no borders, no glass. */
private val SpotifusionTokens = ClassicTokens.copy(
    page = SpotifusionBackground,
    surface = SpotifusionCard,
    edge = EdgeStyle.NONE,
    cardRadius = 8.dp,
    groupRadius = 8.dp,
    buttonRadius = 500.dp,
    chromeRadius = 8.dp,
    divider = Color.White.copy(alpha = 0.07f),
    switchTrackOff = Color(0xFF535353),
    sliderTrack = Color(0xFF535353),
    gutter = 16.dp,
)

/** Glassify: frosted translucent glass everywhere, specular rim, cool blue tint. */
private val GlassifyTokens = ClassicTokens.copy(
    page = GlassifyBackground,
    surface = GlassifyTint,
    edge = EdgeStyle.SPECULAR,
    edgeColor = GlassifyBorder,
    cardRadius = 16.dp,
    groupRadius = 16.dp,
    buttonRadius = 16.dp,
    divider = Color.White.copy(alpha = 0.10f),
    switchTrackOff = Color.White.copy(alpha = 0.20f),
    sliderTrack = Color.White.copy(alpha = 0.22f),
    accentFollowsCover = true,
    titleWeight = FontWeight.SemiBold,
    titleSpacing = (-0.2).sp,
)

/** LiquidMusic: pure black, iOS inset grouped lists with hairlines, 20dp groups, large heavy titles. */
private val LiquidTokens = ClassicTokens.copy(
    page = LiquidMusicBackground,
    surface = LiquidMusicGroup,
    edge = EdgeStyle.NONE,
    edgeColor = LiquidMusicBorder,
    groupStyle = GroupStyle.INSET_LIST,
    cardRadius = 20.dp,
    groupRadius = 20.dp,
    buttonRadius = 20.dp,
    chromeRadius = 20.dp,
    divider = Color.White.copy(alpha = 0.12f),
    dividerThickness = 0.33.dp,
    switchTrackOff = Color(0xFF39393D),
    sliderTrack = Color(0xFF39393D),
    titleWeight = FontWeight.ExtraBold,
    titleSpacing = (-0.8).sp,
    largeTitleSize = 34.sp,
    gutter = 16.dp,
)

/**
 * Cupertino (Apple Music-style, dark): pure black, #1C1C1E groups with 10dp corners and 0.33dp hairlines,
 * pill buttons, capsule chrome, 34sp bold titles.
 */
private val CupertinoTokens = LiquidTokens.copy(
    page = CupertinoBackground,
    surface = CupertinoGroup,
    edgeColor = CupertinoBorder,
    cardRadius = 10.dp,
    groupRadius = 10.dp,
    buttonRadius = 500.dp,
    chromeRadius = 28.dp,
    divider = Color.White.copy(alpha = 0.15f),
    titleWeight = FontWeight.Bold,
    titleSpacing = (-0.4).sp,
)

/** Starry Night: translucent navy cards with a faint gold hairline, light airy titles, warm gold. */
private val StarryTokens = ClassicTokens.copy(
    surface = StarryCard,
    edge = EdgeStyle.HAIRLINE,
    edgeColor = StarryHairline,
    chromeRadius = 22.dp,
    divider = StarryAccent.copy(alpha = 0.10f),
    switchTrackOff = Color(0xFF2A3A55),
    switchThumbOn = Color(0xFF152238),
    sliderTrack = Color(0xFF2A3A55),
    titleWeight = FontWeight.Light,
    titleSpacing = 0.6.sp,
)

private fun LookTokens.copy(
    page: Color = this.page,
    surface: Color = this.surface,
    edge: EdgeStyle = this.edge,
    edgeColor: Color = this.edgeColor,
    groupStyle: GroupStyle = this.groupStyle,
    cardRadius: Dp = this.cardRadius,
    groupRadius: Dp = this.groupRadius,
    buttonRadius: Dp = this.buttonRadius,
    divider: Color = this.divider,
    dividerThickness: Dp = this.dividerThickness,
    switchTrackOff: Color = this.switchTrackOff,
    switchThumbOn: Color = this.switchThumbOn,
    sliderTrack: Color = this.sliderTrack,
    accentFollowsCover: Boolean = this.accentFollowsCover,
    titleWeight: FontWeight = this.titleWeight,
    titleSpacing: TextUnit = this.titleSpacing,
    largeTitleSize: TextUnit = this.largeTitleSize,
    gutter: Dp = this.gutter,
    chromeRadius: Dp = this.chromeRadius,
) = LookTokens(
    page, surface, edge, edgeColor, groupStyle, cardRadius, groupRadius, buttonRadius, divider, dividerThickness,
    switchTrackOff, switchThumbOn, sliderTrack, accentFollowsCover, titleWeight, titleSpacing, largeTitleSize, gutter, chromeRadius,
)

/** The tokens of [look]. */
fun lookTokens(look: Look): LookTokens = when (look) {
    Look.CLASSIC -> ClassicTokens
    Look.IMMERSIVE -> InteractiveTokens
    Look.SPOTIFUSION -> SpotifusionTokens
    Look.GLASSIFY -> GlassifyTokens
    Look.LIQUID_MUSIC -> LiquidTokens
    Look.STARRY_NIGHT -> StarryTokens
    Look.CUPERTINO, Look.SQUARED -> CupertinoTokens
}

/*
 * The Immersive looks' tokens, shared by every interactive screen so they read as one shell, each
 * Look with its own identity (see [LookTokens]): Interactive's cover accent, Spotifusion's flat green,
 * Glassify's specular frosted glass, LiquidMusic's black iOS lists, Starry Night's navy sky.
 */
object Immersive {
    /** The active Look's token set (reads [LookPref.look], so a screen using it follows a switch live). */
    val Tokens: LookTokens get() = lookTokens(LookPref.look)

    // Colours.
    /** Transparent while the star backdrop is drawn so the root StarryNightBackdrop shines through cleanly. */
    val Page: Color get() {
        @Suppress("UNUSED_EXPRESSION")
        starryNightRevision
        return if (app.myspoti.power.EnergySettings.oledBlack) Color.Black
        else com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.BACKGROUND)
            ?: if (StarryNightPref.enabled.value) Color.Transparent
            // Glassify's pages let the blurred cover ambient (drawn at the root) through.
            else if (LookPref.look == Look.GLASSIFY) Color.Transparent
            else Tokens.page
    }
    /**
     * An opaque fill for what floats over moving content (menus, dialogs, a dragged row): [Raised] where that
     * is opaque already, a solid cool dark for Glassify's 8% glass and Starry Night's translucent navy.
     */
    val Popup: Color get() = when (LookPref.look) {
        Look.GLASSIFY -> Color(0xFF1B2230)
        Look.STARRY_NIGHT -> StarryCardOpaque
        else -> Raised.copy(alpha = 1f)
    }
    /** The wash at the top of a detail page (album, playlist), from the cover's [tint] into the page. */
    fun headerWash(tint: Color): Color = when (LookPref.look) {
        Look.LIQUID_MUSIC -> androidx.compose.ui.graphics.lerp(Color.Black, tint, 0.20f)
        Look.CUPERTINO, Look.SQUARED -> androidx.compose.ui.graphics.lerp(Color.Black, tint, 0.30f)
        Look.GLASSIFY -> tint.copy(alpha = 0.38f)
        Look.STARRY_NIGHT -> Color(0xFF1B2D4A)
        else -> androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.3f)
    }
    /** The card & panel fill of the active Look (translucent only where the Look is glass or starlit). */
    val Raised: Color get() {
        @Suppress("UNUSED_EXPRESSION")
        starryNightRevision
        return com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.SURFACE)
            ?: if (StarryNightPref.enabled.value && LookPref.look.let { it == Look.CLASSIC || it == Look.IMMERSIVE }) StarryCard
            else Tokens.surface
    }
    /** The line between the rows of a group. */
    val Separator: Color get() = Tokens.divider
    val SeparatorThickness: Dp get() = Tokens.dividerThickness
    val Text = Color.White
    val TextSecondary: Color get() = if (LookPref.look == Look.CUPERTINO) CupertinoSecondary else StarrySubtext
    val TextTertiary: Color get() = if (LookPref.look == Look.CUPERTINO) CupertinoTertiary else AstralLavender.copy(alpha = 0.50f)
    /**
     * The Look's accent: Interactive and Glassify follow the cover (a chosen accent wins on Interactive),
     * Spotifusion is always #1DB954, LiquidMusic #FA2D48, Starry Night warm gold #FFF3C4.
     */
    val Accent: Color get() = com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.ACCENT)
        ?: lookAccentOrNull(LookPref.look)
        ?: (com.music.spotui.data.preferences.DynamicColorPref.accent()
            ?: com.music.spotui.data.api.SupportReminder.accentColor)
    val GlassFill: Color get() = when (LookPref.look) {
        Look.GLASSIFY -> GlassifyGlassFill
        Look.LIQUID_MUSIC -> LiquidMusicSurface
        Look.CUPERTINO, Look.SQUARED -> CupertinoGlassSolid
        Look.SPOTIFUSION -> SpotifusionCard
        Look.STARRY_NIGHT -> StarryCard
        Look.CLASSIC, Look.IMMERSIVE -> StarryCard
    }
    val GlassEdge: Color get() = when (LookPref.look) {
        Look.GLASSIFY -> GlassifyBorder
        Look.LIQUID_MUSIC -> LiquidMusicBorder
        Look.CUPERTINO, Look.SQUARED -> CupertinoBorder
        Look.SPOTIFUSION -> Color.White.copy(alpha = 0.10f)
        Look.STARRY_NIGHT -> StarryHairline
        Look.CLASSIC, Look.IMMERSIVE -> Color.White.copy(alpha = 0.14f)
    }
    /** A second, raised level of surface (a pressed card, a menu): the Look's raised grey on flat Looks. */
    val Elevated: Color get() = when (LookPref.look) {
        Look.SPOTIFUSION -> SpotifusionElevated
        Look.LIQUID_MUSIC -> Color(0xFF2C2C2E)
        Look.CUPERTINO, Look.SQUARED -> CupertinoElevated
        Look.GLASSIFY -> Color.White.copy(alpha = 0.14f)
        else -> Color(0xFF1E3250)
    }
    /** The red of a destructive swipe, menu entry or confirmation (Leave, Remove, an error). */
    val Destructive = Color(0xFFFF453A)

    // Shapes.
    val CoverSmall: Shape = RoundedCornerShape(6.dp)
    val CoverMedium: Shape = RoundedCornerShape(10.dp)
    val CoverLarge: Shape = RoundedCornerShape(14.dp)
    val Card: Shape get() = RoundedCornerShape(Tokens.cardRadius)
    /** The section card (or inset list) of a Settings-like page. */
    val Group: Shape get() = RoundedCornerShape(Tokens.groupRadius)
    val Button: Shape get() = RoundedCornerShape(Tokens.buttonRadius)
    /** The search field: Cupertino's 10 dp rounded rectangle, the button shape otherwise. */
    val Field: Shape get() = if (LookPref.look == Look.CUPERTINO) RoundedCornerShape(10.dp) else Button
    /** The floating mini player: a capsule, rounder or squarer with the Look. */
    val MiniPlayerShape: Shape get() = RoundedCornerShape(Tokens.chromeRadius)
    /** The tablet sidebar's panel (and what a new Look restyles it with): same corners as the mini player. */
    val SideBarShape: Shape get() = RoundedCornerShape(Tokens.chromeRadius)
    val GroupLayout: GroupStyle get() = Tokens.groupStyle

    // Type (Inter, or the phone's system font when chosen in the settings: AppFontPref).
    private class Type(
        val sans: androidx.compose.ui.text.font.FontFamily,
        val look: Look,
        display: androidx.compose.ui.text.font.FontFamily,
    ) {
        private val t = lookTokens(look)
        private val base = TextStyle(fontFamily = sans, color = Text)
        private val shown = TextStyle(fontFamily = display, color = Text)
        val largeTitle = shown.copy(
            fontSize = t.largeTitleSize, lineHeight = t.largeTitleSize * 1.19f, fontWeight = t.titleWeight, letterSpacing = t.titleSpacing,
        )
        val title = shown.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = t.titleWeight, letterSpacing = t.titleSpacing)
        val headline = shown.copy(
            fontSize = 20.sp, lineHeight = 25.sp,
            fontWeight = if (t.titleWeight == FontWeight.Light) FontWeight.Normal else FontWeight.SemiBold,
            letterSpacing = t.titleSpacing / 2,
        )
        // Cupertino reads as iOS text: 17 / 15 / 13 sp, the secondary text its system grey.
        private val ios = look == Look.CUPERTINO
        val body = base.copy(fontSize = if (ios) 17.sp else 16.sp, lineHeight = if (ios) 22.sp else 21.sp)
        val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
        val subhead = base.copy(
            fontSize = if (ios) 15.sp else 14.sp, lineHeight = if (ios) 20.sp else 19.sp,
            color = if (ios) CupertinoSecondary else TextSecondary,
        )
        val caption = base.copy(
            fontSize = if (ios) 13.sp else 12.sp, lineHeight = if (ios) 18.sp else 16.sp,
            color = if (ios) CupertinoSecondary else TextSecondary,
        )
        val lyric = shown.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    }
    // The styles of the font in use, made again only when the font or the Look changes. Reading the
    // font reads its setting: a screen drawn with these styles redraws when it changes.
    @Volatile private var type: Type? = null
    private fun type(): Type {
        val sans = ImmersiveSans
        val look = LookPref.look
        return type?.takeIf { it.sans === sans && it.look == look } ?: Type(sans, look, ImmersiveDisplay).also { type = it }
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
    val Gutter: Dp get() = Tokens.gutter
    val RowHeight = 60.dp
    /**
     * Room the floating chrome takes at the bottom of every page: the tab bar under the mini player
     * (phones), one row of both (a phone on its side) or the mini player alone (the tablet
     * sidebar holds the tabs). Follows the window, so pages pad themselves by what is really there.
     */
    val BottomChrome: Dp
        @Composable get() {
            val w = com.music.spotui.ui.components.windowSize()
            return when {
                // The mini player (56 dp + 2 dp) over the bar inset (a taskbar or 3-button bar is 48 dp+).
                w.sideNavigation -> 58.dp + com.music.spotui.ui.immersive.shell.ImmersiveBarInsets
                    .asPaddingValues().calculateBottomPadding() + 12.dp
                else -> 170.dp
            }
        }
}

/** The colours of a switch in the active Look: the accent track on, the Look's grey off. */
@Composable
fun immersiveSwitchColors(): SwitchColors {
    val t = Immersive.Tokens
    val accent = Immersive.Accent
    val thumbOn = if (accent.luminance() > 0.55f) Color(0xFF0A1422) else t.switchThumbOn
    return SwitchDefaults.colors(
        checkedThumbColor = thumbOn,
        checkedTrackColor = accent,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = t.switchTrackOff,
        uncheckedBorderColor = Color.Transparent,
        disabledCheckedThumbColor = thumbOn.copy(alpha = 0.7f),
        disabledCheckedTrackColor = accent.copy(alpha = 0.4f),
        disabledCheckedBorderColor = Color.Transparent,
        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
        disabledUncheckedTrackColor = t.switchTrackOff.copy(alpha = 0.5f),
        disabledUncheckedBorderColor = Color.Transparent,
    )
}

/** The colours of a slider in the active Look: the accent on the played part, the Look's grey after it. */
@Composable
fun immersiveSliderColors(): SliderColors {
    val t = Immersive.Tokens
    val accent = Immersive.Accent
    return SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent,
        inactiveTrackColor = t.sliderTrack,
    )
}

/**
 * A panel in the active Look's surface and border: the Look's [Immersive.Raised] fill, rimmed by a
 * Glassify specular gradient, a Starry Night gold hairline, or nothing (Interactive, Spotifusion,
 * LiquidMusic). Used by cards, groups and panels so they all follow the Look.
 */
fun Modifier.immersiveSurface(shape: Shape = Immersive.Card, fill: Color = Immersive.Raised): Modifier {
    val t = Immersive.Tokens
    val base = this.clip(shape).background(fill, shape)
    return when (t.edge) {
        EdgeStyle.NONE -> base
        EdgeStyle.HAIRLINE -> base.border(1.dp, t.edgeColor, shape)
        EdgeStyle.SPECULAR -> base.border(1.dp, specularBrush(), shape)
    }
}

/** The Glassify rim: bright at the top left, fading to almost nothing at the bottom right. */
private fun specularBrush() = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.24f),
        Color.White.copy(alpha = 0.10f),
        Color.White.copy(alpha = 0.04f),
    ),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

/** A translucent bar or capsule (tab bar, mini player, menus). */
fun Modifier.glass(shape: Shape = CircleShape): Modifier =
    when (LookPref.look) {
        Look.GLASSIFY -> this
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
            .background(GlassifyGlassFill, shape)
            .border(width = 1.dp, brush = specularBrush(), shape = shape)
        // Flat Spotify: a plain dark capsule, no rim.
        Look.SPOTIFUSION -> this.background(Immersive.GlassFill, shape)
        else -> this.background(Immersive.GlassFill, shape).border(1.dp, Immersive.GlassEdge, shape)
    }
