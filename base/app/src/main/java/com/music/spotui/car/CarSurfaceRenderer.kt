// SPDX-License-Identifier: GPL-3.0-or-later
// Astral's own code.
package com.music.spotui.car

import android.app.Application
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.music.spotui.di.Graph
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Draws the immersive player on the surface Android Auto gives a navigation app.
 *
 * The surface is the target of a [VirtualDisplay]; a [Presentation] on that display hosts a
 * [ComposeView], so the player is ordinary Compose. A presentation has no activity behind it, so the
 * view tree owners Compose looks for (lifecycle, saved state, view models, back dispatcher) are
 * supplied here. The surface is rebuilt whenever the host hands out a new one (a resize, split screen);
 * the visible and stable areas the host reports (what its own UI leaves free) are published as
 * [insets] and the content lays itself out inside them.
 */
class CarSurfaceRenderer(private val carContext: CarContext) : SurfaceCallback {
    /** What the host's own UI leaves free of the surface, as margins; the whole surface until it says. */
    var insets by mutableStateOf(CarInsetsPx.None)
        private set

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var visible: Rect? = null
    private var stable: Rect? = null

    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var owner: CarViewTreeOwner? = null

    // Survives the surface being rebuilt (a resize must not reload the lyrics), cleared with the session.
    private val viewModels = ViewModelStore()

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        releaseDisplay()
        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        if (width <= 0 || height <= 0) return
        surfaceWidth = width
        surfaceHeight = height
        updateInsets()
        try {
            val manager = carContext.getSystemService(DisplayManager::class.java)
            val virtual = manager.createVirtualDisplay(DISPLAY_NAME, width, height, surfaceContainer.dpi, surface, 0)
            display = virtual
            val viewOwner = CarViewTreeOwner(Graph.app, viewModels).also { owner = it }
            val shown = CarPresentation(carContext, virtual.display, viewOwner, this)
            presentation = shown
            shown.show()
            viewOwner.resume()
        } catch (e: Exception) {
            // A display the platform refuses: the template still works, the surface stays black.
            Log.w(TAG, "Could not show the player on the car surface", e)
            releaseDisplay()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        releaseDisplay()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        updateInsets()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        stable = Rect(stableArea)
        updateInsets()
    }

    /** The visible area decides the layout; the stable one stands in until the host has reported a visible one. */
    private fun updateInsets() {
        val area = visible?.takeUnless { it.isEmpty } ?: stable ?: return
        insets = carInsets(surfaceWidth, surfaceHeight, area.left, area.top, area.right, area.bottom)
    }

    private fun releaseDisplay() {
        val shown = presentation
        val viewOwner = owner
        presentation = null
        owner = null
        // The composition ends with the lifecycle, before the window it draws in goes away.
        viewOwner?.destroy()
        try { shown?.dismiss() } catch (_: Exception) {}
        display?.release()
        display = null
    }

    /** Ends the rendering for good (the session is over). */
    fun release() {
        releaseDisplay()
        viewModels.clear()
    }

    private class CarPresentation(
        outer: Context,
        display: android.view.Display,
        private val owner: CarViewTreeOwner,
        private val renderer: CarSurfaceRenderer,
    ) : Presentation(outer, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val window = window ?: return
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            // Compose finds its owners on the view tree: set before the view is attached.
            val decor = window.decorView
            decor.setViewTreeLifecycleOwner(owner)
            decor.setViewTreeSavedStateRegistryOwner(owner)
            decor.setViewTreeViewModelStoreOwner(owner)
            decor.setViewTreeOnBackPressedDispatcherOwner(owner)
            val compose = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setContent { CarPlayerContent(renderer.insets) }
            }
            setContentView(compose)
        }
    }

    /** The lifecycle, saved state, view models and back dispatcher an activity would give the composition. */
    private class CarViewTreeOwner(
        app: Application,
        private val store: ViewModelStore,
    ) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory, OnBackPressedDispatcherOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)

        fun resume() { registry.currentState = Lifecycle.State.RESUMED }

        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    private companion object {
        const val TAG = "AstralCar"
        const val DISPLAY_NAME = "AstralCarPlayer"
    }
}
