/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/glass/backdrop/backdrops/BackdropStaticRegion.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed to com.music.spotui.ui.immersive.squared.glass (and R to Astral's);
 * otherwise verbatim.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
/*
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.music.spotui.ui.immersive.squared.glass.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize

/**
 * Declares this composable's own bounds as provably static for the current
 * frame: its drawn pixels do not change, so a glass surface sampling [backdrop]
 * whose capture rect falls entirely inside a declared static region can reuse
 * its last capture instead of re-recording on a [backdrop] content-version bump
 * caused by unrelated content elsewhere in the same source subtree (see
 * [LayerBackdrop.isFullyStatic] and its use in `DrawBackdropNode`).
 *
 * Fails safe: an undeclared region, or one only partially covered, is always
 * treated as changed — today's existing behaviour. A missed declaration costs a
 * missed optimization, never a stale pixel. Re-declares every frame this draws;
 * [LayerBackdropNode.draw] clears all declarations at the start of every
 * generation, so a region that stops being drawn here also stops being trusted.
 *
 * Only meaningful when nothing else paints over this composable's bounds within
 * the same backdrop subtree for the current frame — e.g. a full-bleed background
 * image with no scrolling content currently drawn on top of it in that screen
 * area. Wrapping something that shares its rect with changing content elsewhere
 * silently defeats the guarantee this makes to sampling glass surfaces.
 */
fun Modifier.backdropStaticRegion(backdrop: LayerBackdrop): Modifier =
    this then BackdropStaticRegionElement(backdrop)

private class BackdropStaticRegionElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<BackdropStaticRegionNode>() {

    override fun create(): BackdropStaticRegionNode = BackdropStaticRegionNode(backdrop)

    override fun update(node: BackdropStaticRegionNode) {
        node.backdrop = backdrop
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "backdropStaticRegion"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackdropStaticRegionElement) return false
        return backdrop == other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class BackdropStaticRegionNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var coordinates: LayoutCoordinates? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates.takeIf { it.isAttached }
    }

    override fun ContentDrawScope.draw() {
        val self = coordinates
        val source = backdrop.layerCoordinates
        if (self != null && source != null) {
            try {
                val topLeft = source.localPositionOf(self)
                backdrop.staticRegions.add(Rect(topLeft, self.size.toSize()))
            } catch (_: Exception) {
                // Position unavailable this frame: simply don't declare. The
                // fail-safe default (always record) applies as if undeclared.
            }
        }
        drawContent()
    }

    override fun onDetach() {
        coordinates = null
    }
}
