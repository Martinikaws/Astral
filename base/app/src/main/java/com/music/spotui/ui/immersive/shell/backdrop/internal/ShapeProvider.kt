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
package com.music.spotui.ui.immersive.shell.backdrop.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Immutable
internal class ShapeProvider(val shapeBlock: () -> Shape) {

    private var _shape: Shape? = null
    private var _outline: Outline? = null
    private var _size: Size = Size.Unspecified
    private var _layoutDirection: LayoutDirection? = null
    private var _density: Float? = null

    val innerShape
        get() = shapeBlock()

    val shape: Shape = ProviderShape(this)

    private fun createCachedOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val shape = shapeBlock()
        if (_shape != shape) {
            _shape = shape
            _outline = null
        }
        if (_outline == null || _size != size || _layoutDirection != layoutDirection || _density != density.density) {
            _size = size
            _layoutDirection = layoutDirection
            _density = density.density
            _outline = shape.createOutline(size, layoutDirection, density)
        }

        return _outline!!
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShapeProvider) return false
        if (shapeBlock == other.shapeBlock) return true
        return innerShape == other.innerShape
    }

    override fun hashCode(): Int = innerShape.hashCode()

    private class ProviderShape(private val provider: ShapeProvider) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline = provider.createCachedOutline(size, layoutDirection, density)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProviderShape) return false
            return provider == other.provider
        }

        override fun hashCode(): Int = provider.hashCode()
    }
}
