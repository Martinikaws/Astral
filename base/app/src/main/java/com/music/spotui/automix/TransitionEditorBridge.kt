/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import android.net.Uri
import app.myspoti.automix.CustomTransition
import app.myspoti.automix.MixPlan
import app.myspoti.automix.TrackAnalysis
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.data.preferences.TransitionEditorPref
import com.music.spotui.di.LocalPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class TransitionPairSession(
    val fromTrackId: String,
    val toTrackId: String,
    val fromUri: Uri,
    val toUri: Uri,
    val contextUri: String,
    val titleA: String,
    val artistA: String,
    val coverA: String?,
    val durationA: Double,
    val titleB: String,
    val artistB: String,
    val coverB: String?,
    val durationB: Double,
    val analysisA: TrackAnalysis?,
    val analysisB: TrackAnalysis?,
    val autoPlan: MixPlan?,
    val savedCustom: CustomTransition?,
    val initialTransition: CustomTransition,
    val ready: Boolean,
    val analyzing: Boolean,
    val unavailableReason: AutoMix.Reason?,
)

/**
 * Live bridge between [LocalPlayer] / [AutoMix] and the Transition Editor UI.
 */
object TransitionEditorBridge {
    private val _currentPair = MutableStateFlow<TransitionPairSession?>(null)

    /** Live session for the currently playing track -> next track in LocalPlayer (null when no next track). */
    val currentPair: StateFlow<TransitionPairSession?> = _currentPair.asStateFlow()

    private var lastFromTrack: LocalTrack? = null
    private var lastToTrack: LocalTrack? = null
    private var lastListUri: String? = null
    private var lastAnalysisA: TrackAnalysis? = null
    private var lastAnalysisB: TrackAnalysis? = null
    private var lastPlan: MixPlan? = null
    private var lastDurationAMs: Long = -1L
    private var lastStoreVersion: Int = -1
    private var lastReason: AutoMix.Reason? = null
    private var lastActive: Boolean = false

    /** Number of open editor screens; [refresh] does nothing while it is 0 (users without the editor pay nothing). */
    private val openCount = AtomicInteger(0)

    /** Planning and session building happen here, never on the main thread. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "transition-editor").apply { isDaemon = true } }

    /** Called when the editor screen opens; pair with [close] when it leaves. */
    fun openCurrentPair(): StateFlow<TransitionPairSession?> {
        openCount.incrementAndGet()
        refresh(force = true)
        val snap = runCatching { LocalPlayer.editorPairSnapshot() }.getOrNull()
        if (snap != null) {
            val ra = AutoMix.analysis(snap.fromTrack.uri)
            val rb = AutoMix.analysis(snap.toTrack.uri)
            if (ra == null || rb == null) {
                AutoMix.ensureAnalyzedForEditor(snap.fromTrack.uri, snap.toTrack.uri) {
                    refresh(force = true)
                }
            }
        }
        return currentPair
    }

    fun camelotOf(key: Int): String = TrackAnalysis.camelotOf(key)

    /** The editor screen left: stop refreshing and drop the cached session. */
    fun close() {
        if (openCount.updateAndGet { (it - 1).coerceAtLeast(0) } == 0) {
            worker.execute {
                if (openCount.get() == 0) {
                    clearCacheState()
                    _currentPair.value = null
                }
            }
        }
    }

    /** Cheap for everyone else: returns at once unless the editor is enabled and its screen is open. */
    fun refresh(force: Boolean = false) {
        if (openCount.get() <= 0 || !TransitionEditorPref.enabled) return
        val snap = runCatching { LocalPlayer.editorPairSnapshot() }.getOrNull()
        worker.execute { compute(snap, force) }
    }

    private fun compute(snap: LocalPlayer.EditorPairSnapshot?, force: Boolean) {
        if (openCount.get() <= 0) return
        if (snap == null) {
            if (_currentPair.value != null || lastFromTrack != null) {
                clearCacheState()
                _currentPair.value = null
            }
            return
        }
        val ra = AutoMix.analysis(snap.fromTrack.uri)
        val rb = AutoMix.analysis(snap.toTrack.uri)
        val storeVer = CustomTransitionStore.version.value
        val reason = runCatching { AutoMix.whyNot() }.getOrNull()
        val isActive = runCatching { LocalPlayer.active.value }.getOrDefault(false)

        if (!force &&
            _currentPair.value != null &&
            snap.fromTrack === lastFromTrack &&
            snap.toTrack === lastToTrack &&
            snap.listUri == lastListUri &&
            ra === lastAnalysisA &&
            rb === lastAnalysisB &&
            snap.currentPlan === lastPlan &&
            snap.durationAMs == lastDurationAMs &&
            storeVer == lastStoreVersion &&
            reason == lastReason &&
            isActive == lastActive
        ) {
            return
        }

        lastFromTrack = snap.fromTrack
        lastToTrack = snap.toTrack
        lastListUri = snap.listUri
        lastAnalysisA = ra
        lastAnalysisB = rb
        lastPlan = snap.currentPlan
        lastDurationAMs = snap.durationAMs
        lastStoreVersion = storeVer
        lastReason = reason
        lastActive = isActive

        val fromId = LocalPlayer.transitionTrackId(snap.fromTrack)
        val toId = LocalPlayer.transitionTrackId(snap.toTrack)
        val savedCustom = CustomTransitionStore.get(fromId, toId, snap.listUri)

        val durationA = ra?.duration
            ?: snap.durationAMs.takeIf { it > 0 }?.div(1000.0)
            ?: snap.fromTrack.durationMs.takeIf { it > 0 }?.div(1000.0)
            ?: 180.0
        val durationB = rb?.duration
            ?: snap.toTrack.durationMs.takeIf { it > 0 }?.div(1000.0)
            ?: 180.0

        val autoPlan = when {
            savedCustom == null && snap.currentPlan != null -> snap.currentPlan
            ra != null && rb != null -> runCatching { AutoMix.plan(ra, rb) }.getOrNull() ?: snap.currentPlan
            else -> snap.currentPlan
        }

        val initialTransition = savedCustom
            ?: CustomTransition.fromAutoPlan(fromId, toId, ra, rb, autoPlan)

        val ready = ra != null && rb != null
        val analyzing = !ready && isActive

        val coverA = snap.fromTrack.artUri.toString().takeIf { it.isNotBlank() }
        val coverB = snap.toTrack.artUri.toString().takeIf { it.isNotBlank() }

        _currentPair.value = TransitionPairSession(
            fromTrackId = fromId,
            toTrackId = toId,
            fromUri = snap.fromTrack.uri,
            toUri = snap.toTrack.uri,
            contextUri = snap.listUri,
            titleA = snap.fromTrack.title,
            artistA = snap.fromTrack.artist,
            coverA = coverA,
            durationA = durationA,
            titleB = snap.toTrack.title,
            artistB = snap.toTrack.artist,
            coverB = coverB,
            durationB = durationB,
            analysisA = ra,
            analysisB = rb,
            autoPlan = autoPlan,
            savedCustom = savedCustom,
            initialTransition = initialTransition,
            ready = ready,
            analyzing = analyzing,
            unavailableReason = reason,
        )
    }

    private fun clearCacheState() {
        lastFromTrack = null
        lastToTrack = null
        lastListUri = null
        lastAnalysisA = null
        lastAnalysisB = null
        lastPlan = null
        lastDurationAMs = -1L
        lastStoreVersion = -1
        lastReason = null
        lastActive = false
    }
}
