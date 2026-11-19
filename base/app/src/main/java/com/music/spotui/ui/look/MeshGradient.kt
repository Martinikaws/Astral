/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/MeshGradient.kt at edc1e6f.
 * Modified for Myspoti, 2026-10.
 *
 * Changes: the artwork is loaded with Glide and quantised with androidx Palette, as the rest
 * of Myspoti does (di/Palette); "reduce animation" is the system's animator scale; the motion
 * artwork frame, the watch export and BitChord's own welcome palettes are left out.
 */
package com.music.spotui.ui.look

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import app.myspoti.power.EnergySettings
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.music.spotui.di.COVER_DECODE_PX
import com.music.spotui.ui.components.FrameCadence
import com.music.spotui.ui.components.createOwnedScaledCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val FallbackColors = listOf(
    Color(0xFF3A1C71),
    Color(0xFFD76D77),
    Color(0xFF2B5876),
    Color(0xFFFFAF7B),
)

/** Where the four blobs sit, and how fast each orbits (irrational multiples, so they never line up). */
private val MeshAnchors = listOf(
    Offset(0.20f, 0.25f),
    Offset(0.80f, 0.20f),
    Offset(0.75f, 0.80f),
    Offset(0.25f, 0.75f),
)
private val MeshSpeeds = listOf(1f, -0.7f, 0.85f, -1.15f)

/** The scrim that keeps white text legible over bright art. */
private val MeshScrim = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.38f)),
)

/** The four mesh colours, wrapped so the backdrop can skip recomposition. */
@Immutable
data class MeshPalette(val colors: List<Color>)

/** [from] blended into [to] by [fraction], colour by colour (a crossfade between two songs). */
fun lerp(from: MeshPalette, to: MeshPalette, fraction: Float): MeshPalette {
    fun four(p: MeshPalette) = (p.colors.ifEmpty { FallbackColors } + FallbackColors).take(4)
    return MeshPalette(four(from).zip(four(to)) { a, b -> androidx.compose.ui.graphics.lerp(a, b, fraction) })
}

/**
 * The Immersive player's backdrop: four luminous colour blobs sampled from
 * the album art, drawn as soft radial gradients and blurred into a mesh.
 * Colour changes on track skip crossfade over ~1.4s instead of snapping.
 *
 * The blobs drift when there is a reason to — the player opening, or
 * [trackKey] changing — and then come to rest. Orbiting forever would mean
 * re-blurring a full-screen layer at display refresh rate for as long as the
 * player is up: the most expensive thing on screen, for motion that reads as
 * ambient at best. The settled frame looks the same; only the battery drain is gone.
 */
@Composable
fun MeshGradientBackground(
    palette: MeshPalette,
    modifier: Modifier = Modifier,
    trackKey: Any? = null,
    driftMillis: Int = 8_000,
    /**
     * Keep the blobs orbiting instead of letting them settle. Off wherever the
     * mesh fills a screen, for the reason in the note above.
     */
    continuous: Boolean = false,
    /**
     * How far the blobs feather out. Used to size the soft Gaussian-like radial falloff
     * without running a full-screen RenderEffect blur.
     */
    blurRadius: Dp = 64.dp,
    /**
     * Off for a surface that should read as a still image: colours snap
     * straight to target instead of crossfading, and the blobs never drift
     * on a [trackKey] change, only settling once on first composition.
     */
    animated: Boolean = true,
    /**
     * How long a palette change takes to crossfade. A track skip wants it
     * brisk; a surface that changes colour on a timer wants it long enough
     * that one fade runs into the next and the colour never sits still.
     */
    colorFadeMillis: Int = 1400,
    /**
     * Gates the drift clock (pass false when the host surface is collapsed or off-screen so
     * zero frame ticks are scheduled).
     */
    active: Boolean = true,
) {
    val reduceAnimation = rememberReduceMotion()
    val energy by EnergySettings.state.collectAsStateWithLifecycle()
    val saver = energy.policy.saverEnabled
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val tuned = remember(palette) {
        (palette.colors.ifEmpty { FallbackColors } + FallbackColors)
            .take(4)
            .map { it.tuned() }
    }

    // Kept as State<Color> and read only inside Canvas draw so crossfades never recompose this tree.
    val colorSpec: AnimationSpec<Color> =
        if (reduceAnimation || !animated || saver) snap() else tween(colorFadeMillis)
    val colorStates = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "meshColor$index")
    }
    val baseColorState = animateColorAsState(tuned.first().dimmed(), colorSpec, label = "meshBase")

    val canAnimate = active && animated && !reduceAnimation && !saver
    val phaseState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(trackKey, canAnimate, continuous, driftMillis, animated, reduceAnimation, lifecycle) {
        if (!animated || reduceAnimation) {
            phaseState.floatValue = 0f
            return@LaunchedEffect
        }
        if (!canAnimate) return@LaunchedEffect
        val targetFps = minOf(24, energy.policy.progressFps.coerceIn(1, 30))
        val periodMs = 1000L / targetFps
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val cadence = FrameCadence(targetFps)
            if (continuous) {
                var prevNanos = -1L
                val radPerNano = ((2.0 * PI) / (driftMillis.coerceAtLeast(1) * 4L * 1_000_000.0)).toFloat()
                while (isActive) {
                    val nowNanos = withFrameNanos { it }
                    if (prevNanos < 0L) {
                        prevNanos = nowNanos
                    } else if (cadence.shouldTick(nowNanos)) {
                        val dtNanos = (nowNanos - prevNanos).coerceIn(0L, 250_000_000L)
                        prevNanos = nowNanos
                        phaseState.floatValue =
                            (phaseState.floatValue + dtNanos * radPerNano) % (2.0 * PI).toFloat()
                    }
                    delay((periodMs - 4L).coerceAtLeast(1L))
                }
            } else {
                val startPhase = phaseState.floatValue
                val startNanos = withFrameNanos { it }
                val durNanos = (driftMillis.coerceAtLeast(1) * 1_000_000f)
                while (isActive) {
                    val nowNanos = withFrameNanos { it }
                    val frac = ((nowNanos - startNanos) / durNanos).coerceIn(0f, 1f)
                    if (frac >= 1f) {
                        phaseState.floatValue = startPhase + DRIFT_RADIANS
                        break
                    }
                    if (cadence.shouldTick(nowNanos)) {
                        phaseState.floatValue =
                            startPhase + DRIFT_RADIANS * FastOutSlowInEasing.transform(frac)
                    }
                    delay((periodMs - 4L).coerceAtLeast(1L))
                }
            }
        }
    }

    val blobBrushes = remember { Array(4) { CachedMeshBlobBrush() } }
    val spreadFactor = remember(blurRadius) {
        0.62f + (blurRadius.value / 64f).coerceIn(0f, 1.5f) * 0.16f
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        drawRect(color = baseColorState.value)

        val drift = phaseState.floatValue
        val radius = size.maxDimension * spreadFactor

        for (index in 0 until 4) {
            val color = colorStates[index].value
            val anchor = MeshAnchors[index]
            val cx = (anchor.x + 0.16f * cos(drift * MeshSpeeds[index] + index * 1.7f)) * size.width
            val cy = (anchor.y + 0.16f * sin(drift * MeshSpeeds[index] * 0.9f + index * 2.3f)) * size.height
            val brush = blobBrushes[index].get(color, radius)
            withTransform({ translate(left = cx, top = cy) }) {
                drawCircle(brush = brush, radius = radius, center = Offset.Zero)
            }
        }

        // Gentle scrim so white text stays legible over bright art.
        drawRect(brush = MeshScrim)
    }
}

private class CachedMeshBlobBrush {
    private var lastColor: Color = Color.Unspecified
    private var lastRadius: Float = -1f
    private var cached: Brush? = null

    fun get(color: Color, radius: Float): Brush {
        val current = cached
        if (current != null && lastColor == color && lastRadius == radius) return current
        lastColor = color
        lastRadius = radius
        val created = Brush.radialGradient(
            0.00f to color.copy(alpha = 0.85f),
            0.25f to color.copy(alpha = 0.72f),
            0.52f to color.copy(alpha = 0.44f),
            0.78f to color.copy(alpha = 0.16f),
            1.00f to Color.Transparent,
            center = Offset.Zero,
            radius = radius.coerceAtLeast(1f),
        )
        cached = created
        return created
    }
}

/**
 * How long each palette in a [cyclingMeshPalette] is held, which is also how
 * long the fade into it takes — pass it as `colorFadeMillis` so one crossfade
 * runs straight into the next and the colour is never at rest.
 */
const val MESH_CYCLE_MILLIS = 3_600

/**
 * Steps through [palettes] for as long as the caller is composed, one every
 * [stepMillis]. With animations off every step would be a snap, so there it
 * stays on the first palette instead.
 */
@Composable
fun cyclingMeshPalette(palettes: List<MeshPalette>, stepMillis: Int = MESH_CYCLE_MILLIS): MeshPalette {
    val reduceAnimation = rememberReduceMotion()
    val energy by EnergySettings.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduceAnimation, energy.policy.saverEnabled, stepMillis, lifecycle) {
        if (reduceAnimation || energy.policy.saverEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(stepMillis.toLong())
                step++
            }
        }
    }
    return palettes[step % palettes.size]
}

/**
 * Loads the artwork with Glide (software bitmap, thumbnail-sized) and pulls a
 * 4-colour palette out of it. Recomputes when [imageUrl] changes.
 */
@Composable
fun rememberArtworkColors(imageUrl: String?): MeshPalette {
    val context = LocalContext.current
    // Not reset when the url changes: the last cover's colours stay until the new ones are known,
    // so a song change goes straight from one palette to the next, not through the stock purple.
    var palette by remember { mutableStateOf(MeshPalette(FallbackColors)) }

    val scope = rememberCoroutineScope()
    DisposableEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) palette = MeshPalette(FallbackColors)
        var job: Job? = null
        val target = if (imageUrl.isNullOrBlank()) null else loadArtwork(
            context = context,
            imageUrl = imageUrl,
            onLoaded = { bitmap ->
                // Copy into a newly owned PALETTE_PX software bitmap before moving off main thread:
                // Glide owns `bitmap` and may recycle it if cleared.
                val owned = createOwnedScaledCopy(bitmap, PALETTE_PX, PALETTE_PX)
                job?.cancel()
                job = scope.launch(Dispatchers.Default) {
                    val colors = artworkMeshColors(owned)
                    owned.recycle()
                    withContext(Dispatchers.Main) {
                        if (isActive) palette = MeshPalette(colors)
                    }
                }
            },
            onFailed = {
                job?.cancel()
                palette = MeshPalette(FallbackColors)
            },
        )
        onDispose { job?.cancel(); target?.let { Glide.with(context.applicationContext).clear(it) } }
    }
    return palette
}

private fun loadArtwork(
    context: Context,
    imageUrl: String,
    onLoaded: (Bitmap) -> Unit,
    onFailed: () -> Unit,
): CustomTarget<Bitmap> =
    Glide.with(context.applicationContext)
        .asBitmap()
        .load(imageUrl)
        .override(COVER_DECODE_PX, COVER_DECODE_PX) // shares software cache key with CoverGlow / Palette
        .disallowHardwareConfig() // Palette needs pixel access
        .into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) = onLoaded(resource)
            override fun onLoadCleared(placeholder: Drawable?) = Unit
            override fun onLoadFailed(errorDrawable: Drawable?) = onFailed()
        })

/**
 * How far the blobs travel in one settle. A shade under half a turn: enough
 * that the backdrop visibly reacts to a track change, short of a full orbit
 * that would land the blobs back where they started.
 */
/** The side of the copy the colours are taken from (Palette scales to about this anyway). */
private const val PALETTE_PX = 112

private const val DRIFT_RADIANS = (PI * 0.45f).toFloat()

/**
 * Four mesh colours drawn from the artwork.
 *
 * The named swatches — vibrant, muted and friends — are a convenience over the
 * full set, and on dark or desaturated sleeves every vibrant slot comes back
 * null. Topping the rest up from [FallbackColors] is what left those covers
 * sitting under the stock purple. So the whole swatch list is read instead, and
 * any shortfall is derived from the art's own colours rather than borrowed.
 */
fun artworkMeshColors(bitmap: Bitmap): List<Color> {
    fun swatchesOf(clearFilters: Boolean): List<Color> =
        Palette.from(bitmap).maximumColorCount(24)
            .apply { if (clearFilters) clearFilters() }
            .generate()
            .swatches
            .sortedByDescending { it.population }
            .map { Color(it.rgb) }

    val found = swatchesOf(clearFilters = false).ifEmpty {
        // The default filter discards near-black and near-white, which on a
        // monochrome sleeve can be everything there is.
        swatchesOf(clearFilters = true)
    }

    val distinct = found.distinctEnough()
    return when {
        distinct.isEmpty() -> FallbackColors
        distinct.size >= 4 -> distinct.take(4)
        else -> distinct.expandedToFour()
    }
}

/** Drop near-duplicates, so the four blobs don't collapse into one wash. */
private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

/** Fill the empty slots off the art itself, fanning hue and lightness out. */
private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray =
    FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

/** Boost saturation and clamp lightness so any artwork yields a rich, non-muddy mesh. */
private fun Color.tuned(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
