/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/PlayerLyrics.kt at edc1e6f
 * (SweptLyricLine, its drawing helpers and the constants they use) and
 * sharedUi/src/androidMain/kotlin/com/music/bitchord/ui/player/PlayerPlatformUi.android.kt
 * (clipShiftedDown). Modified for Myspoti, 2026-10.
 *
 * Changes: public so the Immersive screens can draw it; drawn from SweptLine (see
 * SweptLyricModel.kt); the translation particles are left out.
 */
package com.music.spotui.ui.look

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Returns true if most of the strongly directional characters in [text] are right-to-left. */
private fun isRtl(text: String): Boolean {
    var rtlCount = 0
    var ltrCount = 0
    for (ch in text) {
        val code = ch.code
        // Hebrew presentation forms
        if (code in 0xFB1D..0xFB4F) return true
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtlCount++
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltrCount++
        }
    }
    return rtlCount > ltrCount
}

/** Whether this text should align to the right end of its box. */
private fun alignsRight(text: String, alignEnd: Boolean, laneLocked: Boolean): Boolean =
    if (laneLocked) alignEnd else isRtl(text)

/** How far back the part of the playing line that hasn't been sung yet is held. */
const val UNSUNG_ALPHA = 0.38f

/** How bright words/lines that have already been sung settle. */
const val SUNG_ALPHA = 0.78f

/**
 * The bloom behind the line being sung, at its very strongest.
 */
const val GLOW_ALPHA = 0.78f

/**
 * How far the bloom spreads off a letter.
 */
private val GLOW_RADIUS = 10.dp

/**
 * Room reserved inside each copy of a line for the halo to spread into.
 *
 * A blur is computed on its layer's own bitmap, so a halo with nowhere to go
 * inside those bounds is a halo with a hard edge. Every copy carries the same
 * inset so they still lay out identically, and the list gives the width back
 * by taking it off its own padding and row spacing.
 */
val GLOW_ROOM = 12.dp

/** Where a lyric line is in its own animation; see [SweptLyricLine]. */
private const val PHASE_BEFORE = 0
private const val PHASE_MOVING = 1
private const val PHASE_AFTER = 2

/**
 * How far the sweep's leading edge fades out instead of ending on a cut.
 *
 * A hard boundary is legible as a boundary: the eye reads a bar travelling
 * across the words rather than the words themselves lighting up as they are
 * sung. Feathering it over roughly a character and a half is what turns the
 * cut back into a wavefront.
 */
private val WIPE_FEATHER = 32.dp

/**
 * How far the word being sung lifts off the line.
 */
private val WORD_RISE = 3.dp

/** Subtle kinetic scale-up on ordinary active words. */
private const val ACTIVE_WORD_SCALE = 1.035f

/** Peak swell on emphasized/held words (> 600 ms). */
private const val HELD_WORD_SCALE = 1.06f

/** Duration threshold above which a sung word earns held-word swell & bloom. */
private const val HELD_WORD_THRESHOLD_MS = 600L

/** Duration over which a lifted word settles smoothly back onto the baseline (fits within [SweptLine.animatesUntilMs]). */
private const val WORD_SETTLE_MS = 650f

/** Line-synced vocal sweep ceiling when a huge gap precedes the next line. */
private const val LINE_SWEEP_GAP_THRESHOLD_MS = 7_500L

/**
 * How much further up a row is opened when it holds a word being animated
 * letter by letter, in multiples of [WORD_RISE]: a letter that swells has to be
 * given the room above the line it grew out of.
 */
private const val GROW_HEADROOM = 3f

/**
 * How the stack falls away either side of the line being sung, indexed by
 * distance from it. Neighboring lines fade smoothly from 0.42f down to 0.22f
 * so focus locks onto the active lyric cleanly.
 */
val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.42f, 0.34f, 0.27f, 0.22f)
val LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.4.dp, 2.3.dp, 3.1.dp, 3.8.dp)

/** The playing line sits at 1; the rest scale down subtly with distance. */
const val INACTIVE_SCALE = 0.94f

/** Progressive scale for a line [distance] steps away from the active line. */
fun scaleForDistance(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> 0.96f
    else -> INACTIVE_SCALE
}

/**
 * The curve every handover runs on: away quickly, in slowly and softly. One
 * curve for the lot — dimming, blurring, scaling and the scroll — so a line
 * handing over reads as a single movement rather than four.
 */
val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
const val LYRIC_SETTLE_MS = 400

/** Critically-damped spring unit response for [t] in 0..1 (zero overshoot, silky landing). */
private fun criticallyDamped(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return ((1f - (1f + 6f * u) * exp(-6f * u)) / 0.98265f).coerceIn(0f, 1f)
}

/** Smoothly eased 0..1 curve for line-synced progressive sweep pacing. */
private fun smoothstep(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

/**
 * Critically-damped spring lift (0..1) for [word] at [positionMs].
 * Rises swiftly as the word begins and settles gently back over [WORD_SETTLE_MS].
 */
private fun springWordLift(word: SweptWord, positionMs: Long): Float {
    if (positionMs <= word.startMs || positionMs >= word.endMs + WORD_SETTLE_MS.toLong()) return 0f
    val duration = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
    val riseWindow = duration.coerceIn(220f, 520f)
    val rise = criticallyDamped((positionMs - word.startMs) / riseWindow)
    val settle = 1f - criticallyDamped((positionMs - word.endMs) / WORD_SETTLE_MS)
    val heldRamp = ((duration - 140f) / 460f).coerceIn(0f, 1f)
    val amplitude = 0.38f + 0.62f * smoothstep(heldRamp)
    return amplitude * minOf(rise, settle)
}

/** Kinetic scale for [word] given its current [lift], peaking at [HELD_WORD_SCALE] on held notes > 600 ms. */
private fun wordScaleFor(word: SweptWord, lift: Float): Float {
    if (lift <= 0.001f) return 1f
    val held = word.endMs - word.startMs
    val peak = if (held > HELD_WORD_THRESHOLD_MS) {
        val t = ((held - HELD_WORD_THRESHOLD_MS).toFloat() / 420f).coerceIn(0f, 1f)
        1.045f + (HELD_WORD_SCALE - 1.045f) * smoothstep(t)
    } else {
        val t = (held.toFloat() / HELD_WORD_THRESHOLD_MS).coerceIn(0f, 1f)
        1.018f + (ACTIVE_WORD_SCALE - 1.018f) * smoothstep(t)
    }
    return 1f + (peak - 1f) * lift
}

/**
 * Progressive illumination fraction (0..1) for a line-synced lyric without word timings
 * across its duration (`nextLineStartMs - lineStartMs`).
 */
private fun lineSyncedProgress(line: SweptLine, positionMs: Long): Float {
    val rawSpan = (line.endMs - line.timeMs).coerceAtLeast(1L)
    val sweepSpan = if (rawSpan > LINE_SWEEP_GAP_THRESHOLD_MS) {
        minOf(rawSpan, maxOf(3_600L, line.text.length * 100L))
    } else {
        rawSpan
    }
    val linear = ((positionMs - line.timeMs).toFloat() / sweepSpan.coerceAtLeast(1L)).coerceIn(0f, 1f)
    return (0.84f * linear + 0.16f * smoothstep(linear)).coerceIn(0f, 1f)
}

/** Clips to the box, then draws [block] moved down by [dy]. */
private fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}

/**
 * A lyric line with the sung part of it lit, the rest dimmed, and the boundary
 * travelling across the words in time with the vocal.
 *
 * Two copies of the same text stacked: a dim one and a bright one clipped to
 * whatever has been sung. Same string, same style, same constraints, so the
 * two lay out identically and the bright copy lands exactly on top of the dim
 * one. The alternative — colouring an AnnotatedString word by word — can only
 * change a whole word at a time, which turns the sweep into a flicker.
 *
 * The clip is recomputed in the draw phase, so a frame costs one clip and one
 * redraw of already-measured text.
 *
 * [glowAlpha] adds the bloom: a third copy, blurred, behind the other two
 * and clipped to the letters of whatever word is being held. Blurring *after*
 * the clip rather than before is what makes the halo bleed out past the letter
 * it belongs to, which is the part that reads as light coming off a carried
 * note rather than a drop shadow sitting under the line.
 */
@Composable
fun SweptLyricLine(
    line: SweptLine,
    clock: MutableLongState,
    style: TextStyle,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = 0f,
    glowRadius: Dp = GLOW_RADIUS,
    glowRoom: Dp = 0.dp,
    feather: Boolean = false,
    rise: Boolean = true,
    alignEnd: Boolean = false,
    laneLocked: Boolean = false,
    /** What the words are set in. */
    color: Color = Color.White,
    /** Tint of the starlight/accent bloom behind active words. */
    glowColor: Color = color,
    /** Optional layout callback for tap-to-seek and annotations. */
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    // Filled in and read back a letter at a time inside the draw lambdas, and
    // shared by all three copies of the line — they draw one after another on
    // the same thread, so there is only ever one letter in hand. Held here
    // rather than allocated per frame: a held word is seven letters at the
    // outside, but this runs on every frame of every line that has one.
    val growth = remember { CharGrowth() }

    // Where this line is in its own animation — not started, moving, or done —
    // which changes three times a line rather than sixty times a second. The
    // draw passes below read the clock only while it says moving; on either
    // side they draw a position that looks identical to any other there (see
    // [SweptLine.animatesFromMs]), so a line nobody is singing never redraws.
    val phase = remember(line, clock) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = clock.longValue
            when {
                now < line.animatesFromMs -> PHASE_BEFORE
                now > line.animatesUntilMs -> PHASE_AFTER
                else -> PHASE_MOVING
            }
        }
    }
    val drawnAt: () -> Long = {
        when (phase.value) {
            PHASE_MOVING -> clock.longValue
            PHASE_BEFORE -> line.animatesFromMs - 1
            else -> line.animatesUntilMs + 1
        }
    }

    val room = if (glowRoom > 0.dp) Modifier.padding(glowRoom) else Modifier

    val riseAgainst: (Modifier) -> Modifier = { inner ->
        if (!rise) {
            inner
        } else {
            Modifier
                .drawWithContent {
                    val measured = layout
                    if (measured == null || line.words.isEmpty()) {
                        drawContent()
                    } else {
                        riseWith(
                            layout = measured,
                            line = line,
                            positionMs = drawnAt(),
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
                        )
                    }
                }
                .then(inner)
        }
    }

    val sweep = Modifier.drawWithContent {
        val position = drawnAt()
        when {
            position >= line.endMs -> drawContent()
            position <= line.timeMs -> Unit
            else -> layout?.let {
                val revealed = if (line.words.isNotEmpty()) {
                    line.revealedChars(position)
                } else if (line.endMs > line.timeMs) {
                    lineSyncedProgress(line, position) * line.text.length
                } else {
                    line.revealedChars(position)
                }
                sweepTo(it, revealed, line.sweepSpans, feather)
            }
        }
    }

    val auraModifier = if (glowAlpha > 0.01f) {
        Modifier.drawBehind {
            val measured = layout ?: return@drawBehind
            drawAmbientBloom(
                layout = measured,
                line = line,
                positionMs = drawnAt(),
                inset = glowRoom.toPx(),
                peak = WORD_RISE.toPx(),
                glowColor = glowColor,
                glowAlpha = glowAlpha,
                blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        }
    } else Modifier

    val right = alignsRight(line.text, alignEnd, laneLocked)
    Box(
        modifier.then(auraModifier),
        contentAlignment = if (right) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft,
    ) {
        Text(
            text = line.text,
            style = style,
            color = color.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = {
                layout = it
                onTextLayout?.invoke(it)
            },
            modifier = riseAgainst(room),
        )
        if (glowAlpha > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                text = line.text,
                style = style,
                color = glowColor,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    // Offscreen wraps glowGrown + room so DstIn erases only inside this layer
                    // and clip/text coordinates both share the same [glowRoom] inset.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val measured = layout ?: return@drawWithContent
                        glowGrown(
                            layout = measured,
                            line = line,
                            positionMs = drawnAt(),
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
                        )
                    }
                    .then(room),
            )
        }
        Text(
            text = line.text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = riseAgainst(
                Modifier
                    .graphicsLayer {
                        compositingStrategy = if (feather) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .then(room)
                    .then(sweep),
            ),
        )
    }
}

/**
 * Draws a soft radial starlight/accent bloom behind active or held words (and traveling along
 * active line-synced phrases). Acts as a subtle atmospheric aura on API 31+ and as a crisp,
 * ghost-free fallback glow on Android versions where [Modifier.blur] is unsupported.
 */
private fun DrawScope.drawAmbientBloom(
    layout: TextLayoutResult,
    line: SweptLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    glowColor: Color,
    glowAlpha: Float,
    blurSupported: Boolean,
) {
    if (positionMs < line.timeMs || positionMs > line.animatesUntilMs) return
    val textLen = layout.layoutInput.text.length
    if (textLen <= 0 || layout.lineCount <= 0) return
    val baseStrength = if (blurSupported) 0.18f else 0.34f

    if (line.words.isEmpty()) {
        if (positionMs !in line.timeMs..line.endMs) return
        val through = lineSyncedProgress(line, positionMs)
        val charIdx = (through * (textLen - 1)).toInt().coerceIn(0, textLen - 1)
        val vl = layout.getLineForOffset(charIdx)
        val left = layout.getLineLeft(vl) + inset
        val right = layout.getLineRight(vl) + inset
        val cx = left + (right - left) * ((through * layout.lineCount) - vl).coerceIn(0f, 1f)
        val cy = (layout.getLineTop(vl) + layout.getLineBottom(vl)) / 2f + inset
        val radius = maxOf((layout.getLineBottom(vl) - layout.getLineTop(vl)) * 1.45f, 36.dp.toPx())
        val pulse = sin(through * PI).toFloat().coerceIn(0f, 1f)
        val alpha = (glowAlpha * baseStrength * (0.4f + 0.6f * pulse)).coerceIn(0f, 1f)
        if (alpha > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        return
    }

    for (i in line.words.indices) {
        val w = line.words[i]
        val lift = springWordLift(w, positionMs)
        if (lift <= 0.02f) continue
        val heldBonus = if (w.endMs - w.startMs > HELD_WORD_THRESHOLD_MS) 1.35f else 0.72f
        val span = line.wordSpans.getOrNull(i) ?: continue
        val first = span.first.coerceIn(0, textLen - 1)
        val last = span.last.coerceIn(first, textLen - 1)
        val vl = layout.getLineForOffset(first)
        val from = layout.xOn(first, vl, inset)
        val to = layout.xOn(minOf(last + 1, layout.getLineEnd(vl, visibleEnd = true)), vl, inset)
        if (to <= from) continue
        val cy = (layout.getLineTop(vl) + layout.getLineBottom(vl)) / 2f + inset - lift * peak
        val cx = (from + to) / 2f
        val radius = maxOf((to - from) * 0.85f, (layout.getLineBottom(vl) - layout.getLineTop(vl)) * 1.15f)
        val alpha = (glowAlpha * baseStrength * heldBonus * lift).coerceIn(0f, 0.6f)
        if (alpha > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}

/**
 * Draws this text clipped to the letters of the words being held, each at its
 * own brightness — the light the singing is actually giving off, rather than a
 * band of it dragged along behind the highlight.
 */
private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: SweptLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    val length = layout.layoutInput.text.length
    if (length <= 0) return

    // Line-synced lyrics: bloom tracks the progressive illumination wavefront smoothly.
    if (line.words.isEmpty()) {
        if (positionMs in line.timeMs..line.endMs) {
            val through = lineSyncedProgress(line, positionMs)
            val revealed = through * line.text.length
            val waveAlpha = (0.18f + 0.34f * sin(through * PI).toFloat()).coerceIn(0f, 1f)
            sweepTo(layout, revealed, emptyList(), feather = true, inset = inset)
            drawRect(color = Color.White.copy(alpha = waveAlpha), blendMode = BlendMode.DstIn)
        }
        return
    }

    val growingIndices = if (line.isGrowing(positionMs)) {
        line.growingWords.filter { positionMs in it.startMs..it.restsAtMs }.map { it.index }.toSet()
    } else emptySet()

    for (i in line.words.indices) {
        if (i in growingIndices) continue
        val w = line.words[i]
        val lift = springWordLift(w, positionMs)
        if (positionMs < w.startMs && lift <= 0.01f) continue
        if (positionMs > w.endMs && lift <= 0.01f) continue
        val span = line.wordSpans.getOrNull(i) ?: continue
        val first = span.first.coerceIn(0, length - 1)
        val last = span.last.coerceIn(first, length - 1)
        val startLine = layout.getLineForOffset(first)
        val endLine = layout.getLineForOffset(last)
        val wordScale = wordScaleFor(w, lift)
        val dy = -lift * peak
        val isHeld = w.endMs - w.startMs > HELD_WORD_THRESHOLD_MS
        val wordDur = (w.endMs - w.startMs).coerceAtLeast(1L).toFloat()
        val wordProg = ((positionMs - w.startMs) / wordDur).coerceIn(0f, 1f)
        val heldPeak = if (isHeld) 0.42f + 0.56f * sin(wordProg * PI).toFloat().coerceAtLeast(0f) else 0.36f
        val liftGlow = (heldPeak * (0.45f + 0.55f * lift)).coerceIn(0f, 1f)
        for (vl in startLine..endLine) {
            val lStart = maxOf(first, layout.getLineStart(vl))
            val lEnd = minOf(last + 1, layout.getLineEnd(vl, visibleEnd = true))
            if (lStart >= lEnd) continue
            val from = layout.xOn(lStart, vl, inset)
            val to = layout.xOn(lEnd, vl, inset)
            if (to <= from) continue
            val top = layout.getLineTop(vl) + inset
            val bottom = layout.getLineBottom(vl) + inset
            val overhang = (to - from) * (wordScale - 1f) / 2f
            clipRect(
                left = from - overhang,
                top = top - peak * GROW_HEADROOM,
                right = to + overhang,
                bottom = bottom,
            ) {
                translate(top = dy) {
                    scale(wordScale, wordScale, Offset((from + to) / 2f, (top + bottom) / 2f)) {
                        this@glowGrown.drawContent()
                    }
                }
                drawRect(color = Color.White.copy(alpha = liftGlow), blendMode = BlendMode.DstIn)
            }
        }
    }
    if (growingIndices.isEmpty()) return
    val em = layout.layoutInput.style.fontSize.toPx()
    for (word in line.growingWords) {
        if (positionMs < word.startMs || positionMs > word.restsAtMs) continue
        val span = line.wordSpans[word.index]
        val fall = if (positionMs <= word.endMs) 1f else {
            1f - criticallyDamped(((positionMs - word.endMs) / WORD_SETTLE_MS).coerceIn(0f, 1f))
        }
        for (char in span.first..minOf(span.last, length - 1)) {
            word.sampleInto(char - span.first, positionMs, growth)
            if (growth.bloom <= 0.01f) continue
            val visualLine = layout.getLineForOffset(char)
            val from = layout.xOn(char, visualLine, inset)
            val to = layout.xOn(char + 1, visualLine, inset)
            if (to <= from) continue
            val dx = growth.shift * em
            val dy = -growth.rise * peak * fall
            val rowTop = layout.getLineTop(visualLine) + inset
            val bottom = layout.getLineBottom(visualLine) + inset
            val overhang = (to - from) * (growth.scale - 1f) / 2f
            clipRect(
                left = from - overhang + dx,
                top = rowTop - peak * GROW_HEADROOM,
                right = to + overhang + dx,
                bottom = bottom,
            ) {
                translate(left = dx, top = dy) {
                    scale(
                        growth.scale,
                        growth.scale,
                        Offset((from + to) / 2f, (rowTop + bottom) / 2f),
                    ) {
                        this@glowGrown.drawContent()
                    }
                }
                drawRect(
                    color = Color.White.copy(alpha = growth.bloom),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

/**
 * Redraws this row with the word being sung lifted off the line, and the ones
 * behind it settling back down.
 */
private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: SweptLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isLifted(positionMs)) {
        drawContent()
        return
    }
    val em = layout.layoutInput.style.fontSize.toPx()
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        var at = lineStart
        var edge = layout.getLineLeft(visualLine) + inset
        for (index in line.words.indices) {
            val span = line.wordSpans[index]
            val start = maxOf(span.first, lineStart)
            val end = minOf(span.last + 1, lineEnd)
            if (start >= end) continue
            val held = line.growingAt(index)?.takeIf { positionMs in it.startMs..it.restsAtMs }
            val word = line.words[index]
            val lift = springWordLift(word, positionMs)
            if (held == null && lift <= 0.01f) continue
            val from = layout.xOn(start, visualLine, inset)
            val to = layout.xOn(end, visualLine, inset)
            if (to <= from) continue
            if (start > at) sliceRisen(edge, top, from, bottom, 0f)
            if (held != null) {
                growEach(
                    layout, held, line, positionMs, visualLine,
                    start, end, top, bottom, inset, peak, em, growth,
                )
            } else {
                val wordScale = wordScaleFor(word, lift)
                val overhang = (to - from) * (wordScale - 1f) / 2f
                clipShiftedDown(
                    left = from - overhang,
                    top = top - peak,
                    right = to + overhang,
                    bottom = bottom,
                    dy = -lift * peak,
                ) {
                    scale(wordScale, wordScale, Offset((from + to) / 2f, (top + bottom) / 2f)) {
                        this@riseWith.drawContent()
                    }
                }
            }
            at = end
            edge = to
        }
        if (at < lineEnd) {
            sliceRisen(edge, top, layout.getLineRight(visualLine) + inset, bottom, 0f)
        }
    }
}

@Suppress("LongParameterList")
private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    word: GrowingWord,
    line: SweptLine,
    positionMs: Long,
    visualLine: Int,
    start: Int,
    end: Int,
    top: Float,
    bottom: Float,
    inset: Float,
    peak: Float,
    em: Float,
    growth: CharGrowth,
) {
    val fall = if (positionMs <= word.endMs) 1f else {
        1f - criticallyDamped(((positionMs - word.endMs) / WORD_SETTLE_MS).coerceIn(0f, 1f))
    }
    val first = line.wordSpans[word.index].first
    val ceiling = top - peak * GROW_HEADROOM
    val middle = (top + bottom) / 2f
    for (char in start until end) {
        word.sampleInto(char - first, positionMs, growth)
        val from = layout.xOn(char, visualLine, inset)
        val to = layout.xOn(char + 1, visualLine, inset)
        if (to <= from) continue
        val dx = growth.shift * em
        val dy = -growth.rise * peak * fall
        val overhang = (to - from) * (growth.scale - 1f) / 2f
        clipShiftedDown(
            left = from - overhang + dx,
            top = ceiling,
            right = to + overhang + dx,
            bottom = bottom,
            dy = dy,
        ) {
            translate(left = dx) {
                scale(growth.scale, growth.scale, Offset((from + to) / 2f, middle)) {
                    this@growEach.drawContent()
                }
            }
        }
    }
}

/**
 * Where an offset sits horizontally *on the row it was cut out of*.
 *
 * [TextLayoutResult.getHorizontalPosition] answers for the row the offset
 * itself belongs to — and the offset one past the last character of a wrapped
 * row belongs to the next row, so asking where a word that runs up to a wrap
 * *ends* gives a position at the far left, one row down. A slice cut between
 * there and the word's start is empty, and the walk then treats the row as
 * finished: everything from that word to the end of the row is never drawn.
 *
 * Whole rows disappeared that way, and Japanese lines disappeared most, because
 * word spans there are often whole phrases and reach a wrap on their own where
 * an English word rarely does.
 */
/**
 * Where a character offset sits across a visual line, in pixels.
 *
 * For RTL paragraphs the first character of a visual line is at the right
 * edge and the last is at the left — this function respects that so callers
 * (horizontalAt, sweepTo) get correct clip boundaries regardless of text
 * direction.  Interior offsets delegate to Compose's own cursor-position
 * calculation which already handles direction; only the two boundary cases
 * need special handling because getLineLeft / getLineRight return physical
 * bounding-box edges (always left ≤ right), not paragraph-direction edges.
 */
private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float): Float {
    val lineStart = getLineStart(visualLine)
    val lineEndVisible = getLineEnd(visualLine, visibleEnd = true)
    val lineLeft = getLineLeft(visualLine) + inset
    val lineRight = getLineRight(visualLine) + inset

    val rtl = getParagraphDirection(lineStart.coerceIn(0, (layoutInput.text.length - 1).coerceAtLeast(0))) ==
              androidx.compose.ui.text.style.ResolvedTextDirection.Rtl

    return when {
        offset <= lineStart -> if (rtl) lineRight else lineLeft
        offset >= lineEndVisible -> if (rtl) lineLeft else lineRight
        // The inset belongs on every answer, not just the two edges: the rows
        // are drawn [inset] in from the layout's origin, and an interior cut
        // without it lands that far left of the glyph boundary — the lifted
        // and swelling slices then sliced letters in half.
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset)
            .coerceIn(lineLeft, lineRight)
    }
}

/** One piece of a line, clipped to its own width and drawn at its own height. */
private fun ContentDrawScope.sliceRisen(
    from: Float,
    top: Float,
    to: Float,
    bottom: Float,
    dy: Float,
) {
    if (to <= from) return
    clipShiftedDown(left = from, top = top, right = to, bottom = bottom, dy = dy) {
        this@sliceRisen.drawContent()
    }
}

/**
 * Where a fractional character index sits across a visual line, in pixels.
 *
 * Inside a timed piece — a syllable, or a word nobody split — that sits whole
 * on this row, the index is read as a share of the piece and laid across its
 * width. Read letter by letter instead, the edge crossed a "w" in the same time
 * as an "i": it sped up and slowed down inside every word, a wobble that a held
 * note made plain. am-lyrics wipes each syllable across its box at a constant
 * rate, and this is the same thing.
 */
private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    visualLine: Int,
    spans: List<IntRange>,
): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEndVisible = layout.getLineEnd(visualLine, visibleEnd = true)
    // Use the *all* end (including trailing whitespace/punctuation) for the
    // boundary check — visibleEnd can exclude chars that sit at the physical
    // edge of an RTL line and would cause xOn to resolve to the next visual
    // line instead of the current one's far edge.
    val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)
    val word = spans.firstOrNull { chars >= it.first && chars < it.last + 1 }
    if (word != null && word.first >= lineStart && word.last + 1 <= lineEndVisible) {
        val from = layout.xOn(word.first, visualLine, 0f)
        val to = layout.xOn(word.last + 1, visualLine, 0f)
        return from + (to - from) * (chars - word.first) / (word.last + 1 - word.first)
    }
    // A word broken over a wrap, or the space between two words: letter by
    // letter, which is all a single space or a half-word needs.
    val index = chars.toInt().coerceIn(lineStart, lineEndAll)
    // Row-aware at both ends: on the last character of a wrapped row the next
    // position belongs to the row below, and read straight it puts the edge
    // back at the left margin — the highlight jumped backwards a letter before
    // every wrap.  Guard against that by checking whether index+1 would land
    // on the next visual line; if so, clamp to this line's far edge instead.
    val here = layout.xOn(index, visualLine, 0f)
    val rawNext = index + 1
    val textLen = layout.layoutInput.text.length
    if (textLen <= 0) return here
    val spills: Boolean = layout.getLineForOffset(rawNext.coerceIn(0, textLen)) != visualLine
    val next: Float = if (spills) {
        // Spilled to the next visual line: use this line's far edge as the
        // interpolation target.  For RTL that is getLineLeft; for LTR it is
        // getLineRight.  The fallback below (xOn with coerceAtMost(lineEnd))
        // would return the wrong side for RTL.
        if (rawNext > lineEndVisible) {
            // At or past the visible end: use the all-end edge.
            val rtl = layout.getParagraphDirection(
                visualLine.coerceAtMost(lineEndAll).coerceIn(0, textLen - 1)
            ) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
            val farLeft = layout.getLineLeft(visualLine)
            val farRight = layout.getLineRight(visualLine)
            // For RTL, the "far" edge in character order is getLineLeft;
            // for LTR it is getLineRight.  We detect by comparing the two:
            if (rtl) farLeft else farRight
        } else {
            layout.xOn(rawNext.coerceAtMost(lineEndVisible), visualLine, 0f)
        }
    } else {
        layout.xOn(rawNext, visualLine, 0f)
    }
    return here + (next - here) * (chars - index)
}

/**
 * Draws this text clipped to its first [revealedChars] characters.
 *
 * Wrapped lines are handled a visual line at a time: the ones already passed
 * are drawn whole, the one holding the boundary is cut at it, and the rest are
 * left to the dim copy. Within a word the cut moves across the word's width
 * at a steady rate — see [horizontalAt] — rather than jumping a letter at a
 * time. [spans] are the line's timed pieces, [SweptLine.sweepSpans]; empty, the
 * cut goes letter by letter.
 *
 * The boundary itself is then feathered over the [WIPE_FEATHER] ahead of it
 * rather than left as the cut, which needs the caller to give this an offscreen layer to erase
 * into — see [SweptLyricLine]. Only the line actually being sung carries one;
 * everywhere else the boundary is at one end of the text or the other and
 * there is nothing to soften.
 */
private fun ContentDrawScope.sweepTo(
    layout: TextLayoutResult,
    revealedChars: Float,
    spans: List<IntRange>,
    feather: Boolean,
    inset: Float = 0f,
) {
    if (revealedChars <= 0f) return
    val textLen = layout.layoutInput.text.length
    if (textLen <= 0) return
    if (revealedChars >= textLen) {
        drawContent()
        return
    }
    val offset = layout.getLineStart(0).coerceIn(0, textLen - 1)
    val rtl = layout.getParagraphDirection(offset) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl

    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEndAll = layout.getLineEnd(visualLine, visibleEnd = false)

        if (revealedChars <= lineStart) return

        val cut = revealedChars < lineEndAll
        val charPos = inset + if (cut) {
            horizontalAt(layout, revealedChars, visualLine, spans)
        } else {
            if (rtl) layout.getLineLeft(visualLine) else layout.getLineRight(visualLine)
        }

        val lineLeft = layout.getLineLeft(visualLine) + inset
        val lineRight = layout.getLineRight(visualLine) + inset

        val safeCharPos = charPos.coerceIn(lineLeft, lineRight)

        val featherPx = if (feather && cut) {
            minOf(
                WIPE_FEATHER.toPx(),
                if (rtl) lineRight - safeCharPos else safeCharPos - lineLeft,
            )
        } else {
            0f
        }
        val lead = if (rtl) {
            (safeCharPos - featherPx).coerceAtLeast(lineLeft)
        } else {
            (safeCharPos + featherPx).coerceAtMost(lineRight)
        }

        val (clipLeft, clipRight) = if (rtl) {
            lead to lineRight
        } else {
            lineLeft to lead
        }

        if (clipLeft >= clipRight) continue

        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        clipRect(
            left = clipLeft,
            top = top,
            right = clipRight,
            bottom = bottom,
        ) {
            this@sweepTo.drawContent()
        }

        if (featherPx < 0.5f) continue

        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = safeCharPos,
                    endX = if (rtl) safeCharPos - featherPx else safeCharPos + featherPx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}
