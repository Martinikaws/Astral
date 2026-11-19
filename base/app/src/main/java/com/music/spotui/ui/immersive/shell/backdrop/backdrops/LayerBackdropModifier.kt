/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 * Myspoti copy taken from BitChord's vendored tree (github.com/kushagrasinghx/BitChord,
 * app/src/main/java/com/music/bitchord/ui/components/backdrop at edc1e6f), which adds
 * backdropScale and exportedBackdrop to drawBackdrop. Package renamed again for Myspoti.
 */
package com.music.spotui.ui.immersive.shell.backdrop.backdrops

import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.music.spotui.ui.immersive.shell.backdrop.internal.recordLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop !== backdrop) {
            node.switchBackdrop(backdrop)
        }
        node.syncSourceAlpha()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false

        return true
    }

    override fun hashCode(): Int {
        return backdrop.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode,
    LayoutModifierNode,
    PointerInputModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode,
    Modifier.Node() {

    private var hasPositioned = false
    private var lastPositionInWindow = Offset.Unspecified
    private var lastSize = IntSize.Zero

    private var pulseJob: Job? = null
    private var fastUntil = 0L
    private var settleUntil = 0L

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val p = measurable.measure(constraints)
        if (hasPositioned) {
            invalidateDraw()
        }
        return layout(p.width, p.height) { p.place(0, 0) }
    }

    // Seen first and never consumed: flings, horizontal LazyRow scrolls, ripples and taps
    // can update child visuals without invalidating the container's own draw pass, so run a
    // finite pulse that settles and stops completely at rest.
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass == PointerEventPass.Initial) {
            bumpPulse(fastMs = TOUCH_FAST_MS, settleMs = TOUCH_SETTLE_MS)
        }
    }

    override fun onCancelPointerInput() = Unit

    override fun ContentDrawScope.draw() {
        // Vendored change: record once and composite, rather than upstream's
        // draw-then-record.
        //
        // Upstream draws the content to the screen and then draws all of it a
        // second time into the layer, which puts the entire app through the draw
        // pass twice on every frame the glass is switched on — and this node
        // wraps the whole page, so "the entire app" is what that is. Recording
        // first and then blitting the finished layer to the screen produces the
        // same pixels off one pass.
        //
        // The layer is the node's own size, so this does clip anything a child
        // paints outside its bounds. Nothing here does — the pages are lists
        // inside a full-screen container — but a child that grew a shadow past
        // the edge would lose it, which upstream would not.
        // A degenerate size would record an empty layer, and unlike upstream there
        // is no direct draw left to fall back on — the page would simply vanish.
        if (size.width < 1f || size.height < 1f) {
            drawContent()
            return
        }
        // One recording per source, read in the draw phase so every frame of a transition redraws it.
        val layer = source.layer
        if (layer == null || layer.isReleased) {
            if (layer != null) app.myspoti.host.android.Logbook.log("glass: backdrop source drawn after release, drawing the page directly")
            return drawContent()
        }
        recordLayer(this@LayerBackdropNode, layer) { backdrop.onDraw(this@draw) }
        drawLayer(layer)
        // Event-driven invalidation: notify glass readers when this source actually redrew.
        (registeredIn ?: backdrop).notifyMoved()
    }

    private val source = LayerBackdrop.Source()
    private var registeredIn: LayerBackdrop? = null

    private fun bumpPulse(fastMs: Long, settleMs: Long) {
        if (!isAttached) return
        val now = SystemClock.uptimeMillis()
        if (fastMs > 0L) fastUntil = maxOf(fastUntil, now + fastMs)
        if (settleMs > 0L) settleUntil = maxOf(settleUntil, now + settleMs)
        ensurePulseRunning()
    }

    private fun ensurePulseRunning() {
        if (!isAttached || pulseJob?.isActive == true) return
        val launched = coroutineScope.launch {
            val me = coroutineContext[Job]
            try {
                while (isAttached) {
                    val now = SystemClock.uptimeMillis()
                    val interval = when {
                        now < fastUntil -> FAST_INTERVAL_MS
                        now < settleUntil -> SETTLE_INTERVAL_MS
                        else -> break
                    }
                    delay(interval)
                    if (!isAttached) break
                    withFrameNanos { }
                    if (!isAttached) break
                    (registeredIn ?: backdrop).notifyMoved()
                    invalidateDraw()
                }
            } finally {
                if (pulseJob === me) pulseJob = null
            }
        }
        if (launched.isActive) {
            pulseJob = launched
        }
    }

    fun switchBackdrop(next: LayerBackdrop) {
        if (backdrop === next) return
        if (isAttached) {
            registeredIn?.retire(source)
            registeredIn = next
            source.alpha = currentValueOf(LocalBackdropSourceAlpha)
            if (!next.sources.contains(source)) {
                next.sources.add(source)
            }
            val coords = source.coordinates
            if (coords != null && coords.isAttached) {
                lastPositionInWindow = coords.positionInWindow()
                lastSize = coords.size
                hasPositioned = true
                next.publish(source)
            } else {
                hasPositioned = false
                next.notifyMoved()
            }
        }
        backdrop = next
    }

    fun syncSourceAlpha() {
        if (isAttached) {
            source.alpha = currentValueOf(LocalBackdropSourceAlpha)
        }
    }

    override fun onAttach() {
        registeredIn = backdrop
        source.layer = requireGraphicsContext().createGraphicsLayer()
        source.alpha = currentValueOf(LocalBackdropSourceAlpha)
        if (!backdrop.sources.contains(source)) {
            backdrop.sources.add(source)
        }
        backdrop.notifyMoved()
        invalidateDraw()
        bumpPulse(fastMs = 0L, settleMs = INITIAL_SETTLE_MS)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            source.coordinates = coordinates
            // The backdrop may have been swapped by update(): follow it.
            if (registeredIn !== backdrop) {
                registeredIn?.retire(source)
                registeredIn = backdrop
                if (!backdrop.sources.contains(source)) {
                    backdrop.sources.add(source)
                }
                hasPositioned = false
            }
            val pos = coordinates.positionInWindow()
            val sz = coordinates.size
            if (!hasPositioned || pos != lastPositionInWindow || sz != lastSize) {
                hasPositioned = true
                lastPositionInWindow = pos
                lastSize = sz
                backdrop.publish(source)
            }
        }
    }

    override fun onDetach() {
        pulseJob?.cancel()
        pulseJob = null
        fastUntil = 0L
        settleUntil = 0L
        registeredIn?.retire(source)
        registeredIn = null
        source.coordinates = null
        hasPositioned = false
        lastPositionInWindow = Offset.Unspecified
        lastSize = IntSize.Zero
        source.layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        source.layer = null
    }
}

/** How long a newly attached page pulses slowly while initial covers and async rows settle. */
private const val INITIAL_SETTLE_MS = 1_200L

/** Interval between slow settle ticks (~8 ticks over [INITIAL_SETTLE_MS]). */
private const val SETTLE_INTERVAL_MS = 150L

/** High-rate pulse window after pointer activity (flings, horizontal rows, ripples). */
private const val TOUCH_FAST_MS = 1_200L

/** Total settle window after pointer activity before stopping completely at rest. */
private const val TOUCH_SETTLE_MS = 2_500L

/** Fast pulse interval during active touch or fling (~30 fps). */
private const val FAST_INTERVAL_MS = 32L
