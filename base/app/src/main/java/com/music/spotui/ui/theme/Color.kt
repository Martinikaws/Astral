package com.music.spotui.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette as AndroidPalette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.data.preferences.StarryNightPref

/** Bumped when [StarryNightPref.enabled] toggles so all Compose readers of [AppBackground] / [GridBackground] recompose. */
internal var starryNightRevision by mutableIntStateOf(0)
    private set

internal fun notifyStarryNightChanged() {
    starryNightRevision++
}

/** True when the root [com.music.spotui.ui.components.StarryNightBackdrop] is active and visible. */
internal val isStarryNightActive: Boolean
    get() {
        @Suppress("UNUSED_EXPRESSION")
        starryNightRevision
        return StarryNightPref.enabled.value &&
            !app.myspoti.power.EnergySettings.oledBlack &&
            com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.BACKGROUND) == null
    }

/** Contrast-adjusted dynamic album-art accent used by [Look.GLASSIFY] and [Look.LIQUID_MUSIC]. */
internal var dynamicCoverAccent by mutableStateOf<Color?>(null)

private val coverAccentCache = java.util.Collections.synchronizedMap(
    object : java.util.LinkedHashMap<String, Color>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 120
    },
)

/** Extracts a vibrant, dark-surface-legible cover accent for Glassify / LiquidMusic. */
internal fun extractContrastCoverAccent(context: Context, imageUrl: String, onExtracted: (Color?) -> Unit) {
    if (imageUrl.isBlank()) {
        onExtracted(null)
        return
    }
    coverAccentCache[imageUrl]?.let {
        onExtracted(it)
        return
    }
    runCatching {
        Glide.with(context.applicationContext)
            .asBitmap()
            .load(imageUrl)
            .override(com.music.spotui.di.COVER_DECODE_PX, com.music.spotui.di.COVER_DECODE_PX)
            .disallowHardwareConfig()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    AndroidPalette.from(resource).generate { palette ->
                        val rgb = palette?.let {
                            it.vibrantSwatch ?: it.lightVibrantSwatch ?: it.dominantSwatch ?: it.darkVibrantSwatch ?: it.mutedSwatch
                        }?.rgb
                        val adjusted = rgb?.let { adjustForDarkGlass(Color(it or (0xFF shl 24))) }
                        if (adjusted != null) coverAccentCache[imageUrl] = adjusted
                        onExtracted(adjusted)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) { onExtracted(null) }
            })
    }.onFailure { onExtracted(null) }
}

/** Ensures dynamic album-art accents maintain crisp contrast against dark glass panels. */
internal fun adjustForDarkGlass(color: Color): Color {
    var out = color
    var step = 0f
    while (out.luminance() < 0.28f && step < 0.65f) {
        step += 0.05f
        out = lerp(color, Color.White, step)
    }
    return out
}

/**
 * The page background: black with the AMOLED setting (an extension's look never undoes it),
 * extension override if set, [Color.Transparent] when Starry Night is active so the root
 * [com.music.spotui.ui.components.StarryNightBackdrop] shines through cleanly, else the active Look's base.
 */
val AppBackground: Color get() {
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

/** Non-transparent card / grid / surface color so cards & lists stay legible over Starry Night & ambient layers. */
val GridBackground: Color get() {
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

/** The accent for the active Look (or extension override). */
val AppPalette: Color get() = AppAccent
val AppAccent: Color get() =
    com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.ACCENT)
        ?: when (LookPref.look) {
            Look.SPOTIFUSION -> SpotifusionGreen
            Look.GLASSIFY -> dynamicCoverAccent ?: StarryBlue
            Look.LIQUID_MUSIC -> dynamicCoverAccent ?: LiquidMusicAccent
            Look.IMMERSIVE ->
                com.music.spotui.data.api.SupportReminder.run { if (immersiveAccentChosen) immersiveAccent.color else StarryAccent }
            Look.CLASSIC ->
                com.music.spotui.data.preferences.DynamicColorPref.accent()
                    ?: com.music.spotui.data.api.SupportReminder.accentColor.takeUnless {
                        it == com.music.spotui.data.api.SupportReminder.Accent.GREEN.color
                    }
                    ?: StarryAccent
        }

/** Spotifusion 1:1 Spotify palette. */
val SpotifusionBackground = Color(0xFF121212)
val SpotifusionCard = Color(0xFF1E1E1E)
val SpotifusionGreen = Color(0xFF1DB954)

/** Glassify translucent frosted surface (`spicetify-glassify`) and crisp 1dp specular border. */
val GlassifyBackground = Color(0xFF0E1118)
val GlassifyGlassFill = Color(0x28333338)
val GlassifySurface = Color(0x38282C38)
val GlassifyBorder = Color.White.copy(alpha = 0.24f)

/** LiquidMusic iOS-inspired liquid-glass surfaces and contrast accent. */
val LiquidMusicBackground = Color(0xFF0B0B0F)
val LiquidMusicSurface = Color(0x8C1C1C1E)
val LiquidMusicBorder = Color.White.copy(alpha = 0.22f)
val LiquidMusicAccent = Color(0xFFFA2D48)

/** Spicetify StarryNight `[Base]` palette. */
val StarrySkyTop = Color(0xFF000000)
val StarrySkyBottom = Color(0xFF142B44)
val StarryCard = Color(0xD9152238)
val StarryCardOpaque = Color(0xFF152238)
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
