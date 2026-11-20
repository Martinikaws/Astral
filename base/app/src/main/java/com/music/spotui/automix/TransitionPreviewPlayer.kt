/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.myspoti.automix.CustomTransition
import app.myspoti.automix.MixPlan
import com.music.spotui.di.LocalPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dual-deck transition audition player for the Transition Editor.
 * Plays `[exitA - preRollSec .. exitA + overlapSec + postRollSec]` using [LocalPlayer]'s
 * media source stack and applies live envelope/EQ edits every 20 ms.
 */
object TransitionPreviewPlayer {
    data class PreviewState(
        val playing: Boolean = false,
        val preparing: Boolean = false,
        val progress: Float = 0f,        // 0f..1f across the full preview (preRoll + overlap + postRoll)
        val overlapProgress: Float = 0f, // 0f..1f inside the overlap window (<0 before, >1 after)
        val elapsedSec: Double = 0.0,
        val totalSec: Double = 0.0,
        val posASec: Double = 0.0,
        val posBSec: Double = 0.0,
    )

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private var deckA: ExoPlayer? = null
    private var deckB: ExoPlayer? = null
    private var eqA: DeckEq? = null
    private var eqB: DeckEq? = null

    private var activeSession: TransitionPairSession? = null
    private var activeTransition: CustomTransition? = null
    private var startASec: Double = 0.0
    private var actualPreRollSec: Double = 0.0
    private var overlapSec: Double = 1.0
    private var postRollSec: Double = 3.0
    private var totalSec: Double = 1.0
    private var speedB: Double = 1.0

    private var readyA = false
    private var readyB = false
    private var bStarted = false
    private var aFinished = false

    /** True while the preview decks exist (preparing or playing). */
    val isActive: Boolean get() = deckA != null || deckB != null

    fun play(
        session: TransitionPairSession,
        transition: CustomTransition,
        preRollSec: Double = 3.0,
        postRollSec: Double = 3.0,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { play(session, transition, preRollSec, postRollSec) }
            return
        }
        stop()
        if (runCatching { LocalPlayer.isPlaying() }.getOrDefault(false)) {
            runCatching { LocalPlayer.pause() }
        }

        val plan = CustomTransitionStore.toMixPlan(
            transition,
            session.analysisA,
            session.analysisB,
            session.autoPlan,
        )

        activeSession = session
        activeTransition = transition
        startASec = (transition.exitA - preRollSec.coerceAtLeast(0.0)).coerceAtLeast(0.0)
        actualPreRollSec = (transition.exitA - startASec).coerceAtLeast(0.0)
        overlapSec = transition.overlapSec.coerceAtLeast(0.5)
        this.postRollSec = postRollSec.coerceAtLeast(0.5)
        totalSec = (actualPreRollSec + overlapSec + this.postRollSec).coerceAtLeast(0.5)
        speedB = plan.speedB.coerceIn(0.5, 2.0)

        _state.value = PreviewState(
            playing = false,
            preparing = true,
            progress = 0f,
            overlapProgress = (-actualPreRollSec / overlapSec).toFloat(),
            elapsedSec = 0.0,
            totalSec = totalSec,
            posASec = startASec,
            posBSec = transition.entryB,
        )

        val (pA, _) = LocalPlayer.buildPreviewDeck()
        val (pB, _) = LocalPlayer.buildPreviewDeck()
        deckA = pA
        deckB = pB
        // Unplugging headphones pauses the decks (see onPlayWhenReadyChanged), never blasts the speaker.
        pA.setHandleAudioBecomingNoisy(true)
        pB.setHandleAudioBecomingNoisy(true)

        if (AutoMix.djEq) {
            eqA = DeckEq.attach(pA.audioSessionId)
            eqB = DeckEq.attach(pB.audioSessionId)?.also {
                if (transition.eqSwap) it.set(MixPlan.KILL, 0.0)
            }
        }

        pA.volume = transition.gainAAt(0f).coerceIn(0f, 1f)
        pB.volume = 0f
        pB.playbackParameters = PlaybackParameters(speedB.toFloat())

        pA.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (deckA !== pA || !AutoMix.djEq) return
                eqA?.release()
                eqA = DeckEq.attach(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (deckA !== pA) return
                if (playbackState == Player.STATE_READY && !readyA) {
                    readyA = true
                    tryStartDecks()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (deckA === pA && !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) stop()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (deckA === pA) stop()
            }
        })

        pB.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (deckB !== pB || !AutoMix.djEq) return
                eqB?.release()
                eqB = DeckEq.attach(audioSessionId)?.also {
                    if (activeTransition?.eqSwap == true && !bStarted) it.set(MixPlan.KILL, 0.0)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (deckB !== pB) return
                if (playbackState == Player.STATE_READY && !readyB) {
                    readyB = true
                    tryStartDecks()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (deckB === pB && !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) stop()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (deckB === pB) stop()
            }
        })

        pA.setMediaItem(MediaItem.fromUri(session.fromUri), (startASec * 1000.0).toLong().coerceAtLeast(0L))
        pB.setMediaItem(MediaItem.fromUri(session.toUri), (transition.entryB * 1000.0).toLong().coerceAtLeast(0L))
        pA.prepare()
        pB.prepare()
    }

    fun updateLive(transition: CustomTransition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { updateLive(transition) }
            return
        }
        activeTransition = transition
        if (_state.value.playing || _state.value.preparing) {
            overlapSec = transition.overlapSec.coerceAtLeast(0.5)
            totalSec = (actualPreRollSec + overlapSec + postRollSec).coerceAtLeast(0.5)
        }
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            _state.value = PreviewState()
            handler.post { stop() }
            return
        }
        handler.removeCallbacks(ticker)
        eqA?.release(); eqA = null
        eqB?.release(); eqB = null
        deckA?.let { runCatching { it.stop(); it.release() } }; deckA = null
        deckB?.let { runCatching { it.stop(); it.release() } }; deckB = null
        activeSession = null
        activeTransition = null
        readyA = false
        readyB = false
        bStarted = false
        aFinished = false
        if (_state.value != PreviewState()) {
            _state.value = PreviewState()
        }
    }

    private fun tryStartDecks() {
        if (!readyA || !readyB || !_state.value.preparing) return
        val pA = deckA ?: return
        val pB = deckB ?: return
        val tr = activeTransition ?: return

        if (actualPreRollSec <= 0.02) {
            bStarted = true
            pB.playbackParameters = PlaybackParameters(speedB.toFloat())
            pB.volume = tr.gainBAt(0f).coerceIn(0f, 1f)
            pB.play()
        }
        pA.volume = tr.gainAAt(0f).coerceIn(0f, 1f)
        pA.play()
        _state.value = _state.value.copy(playing = true, preparing = false)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            val pA = deckA ?: return
            val pB = deckB ?: return
            val tr = activeTransition ?: return

            val posA = pA.currentPosition / 1000.0
            val posB = pB.currentPosition / 1000.0

            var elapsed = (posA - startASec).coerceAtLeast(0.0)
            if (!aFinished && (elapsed >= actualPreRollSec + overlapSec || pA.playbackState == Player.STATE_ENDED)) {
                aFinished = true
            }
            if (aFinished) {
                val bAfterOverlap = (posB - (tr.entryB + overlapSec * speedB)) / speedB.coerceAtLeast(0.1)
                elapsed = actualPreRollSec + overlapSec + bAfterOverlap.coerceAtLeast(0.0)
            }

            val overlapProg = ((elapsed - actualPreRollSec) / overlapSec).toFloat()
            val totalProg = (elapsed / totalSec).toFloat().coerceIn(0f, 1f)

            when {
                overlapProg < 0f -> {
                    pA.volume = tr.gainAAt(0f).coerceIn(0f, 1f)
                    pB.volume = 0f
                    eqA?.neutral()
                    if (tr.eqSwap) eqB?.set(MixPlan.KILL, 0.0) else eqB?.neutral()
                }
                overlapProg <= 1f -> {
                    if (!bStarted) {
                        bStarted = true
                        pB.playbackParameters = PlaybackParameters(speedB.toFloat())
                        pB.play()
                    }
                    pA.volume = tr.gainAAt(overlapProg).coerceIn(0f, 1f)
                    pB.volume = tr.gainBAt(overlapProg).coerceIn(0f, 1f)
                    if (tr.eqSwap && AutoMix.djEq) {
                        val s = ((overlapProg - 0.35f) / 0.30f).coerceIn(0f, 1f)
                        val smooth = (s * s * (3f - 2f * s)).toDouble()
                        val highA = if (overlapProg > 0.7f) -12.0 * ((overlapProg - 0.7f) / 0.3f) else 0.0
                        eqA?.set(MixPlan.KILL * smooth, highA)
                        eqB?.set(MixPlan.KILL * (1.0 - smooth), 0.0)
                    } else {
                        eqA?.neutral()
                        eqB?.neutral()
                    }
                }
                else -> {
                    if (!bStarted) {
                        bStarted = true
                        pB.playbackParameters = PlaybackParameters(speedB.toFloat())
                        pB.play()
                    }
                    if (pA.isPlaying) pA.pause()
                    pA.volume = 0f
                    pB.volume = tr.gainBAt(1f).coerceIn(0f, 1f)
                    eqA?.neutral()
                    eqB?.neutral()
                }
            }

            _state.value = PreviewState(
                playing = true,
                preparing = false,
                progress = totalProg,
                overlapProgress = overlapProg,
                elapsedSec = elapsed,
                totalSec = totalSec,
                posASec = posA,
                posBSec = posB,
            )

            if (elapsed >= totalSec || pB.playbackState == Player.STATE_ENDED) {
                stop()
                return
            }
            handler.postDelayed(this, 20L)
        }
    }
}
