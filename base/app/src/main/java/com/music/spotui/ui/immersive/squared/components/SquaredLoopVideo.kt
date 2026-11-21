package com.music.spotui.ui.immersive.squared.components

import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import androidx.annotation.LayoutRes
import app.myspoti.engine.R
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.ui.PlayerView
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.delay

/** A muted visual loop owned by one TextureView. Both Squared video backdrops use this lifecycle. */
@OptIn(UnstableApi::class)
@Composable
internal fun SquaredLoopVideo(
    url: String,
    @LayoutRes layout: Int,
    playing: Boolean,
    modifier: Modifier = Modifier,
    onFrameVisible: (Boolean) -> Unit,
    sampleEveryMs: Long,
    sampleFrame: (suspend (TextureView) -> Unit)?,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(context, url, layout) {
        squaredLoopPlayer(context, url, portrait = layout == R.layout.squared_canvas_surface)
    }
    val owner = remember(player) { LoopSurface() }
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var rendered by remember(player) { mutableStateOf(false) }
    var moving by remember(player) { mutableStateOf(false) }
    var retryPending by remember(player) { mutableStateOf(false) }
    val showFrame by rememberUpdatedState(onFrameVisible)
    val sample by rememberUpdatedState(sampleFrame)

    DisposableEffect(player) {
        var retried = false
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                rendered = true
                showFrame(true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) { moving = isPlaying }

            override fun onPlayerError(error: PlaybackException) {
                rendered = false
                showFrame(false)
                Log.w("SquaredLoopVideo", "Visual loop failed: " + error.errorCodeName)
                if (!retried) {
                    retried = true
                    retryPending = true
                }
            }
        }
        player.addListener(listener)
        player.prepare()
        onDispose {
            player.removeListener(listener)
            // Dispose may run before AndroidView.onRelease. Clear the surface while the player is alive.
            owner.detach(player)
            player.release()
        }
    }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!started) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, started, playing) {
        player.playWhenReady = started && playing
    }
    LaunchedEffect(player, started, retryPending) {
        if (started && retryPending) {
            delay(650)
            retryPending = false
            player.prepare()
        }
    }

    val canSample = started && playing && rendered && moving && sampleFrame != null
    LaunchedEffect(player, canSample) {
        if (!canSample) return@LaunchedEffect
        while (player.isPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            val texture = owner.view?.videoSurfaceView as? TextureView
            if (texture != null && texture.isAvailable && texture.width > 0 && texture.height > 0) {
                sample?.invoke(texture)
            }
            delay(sampleEveryMs)
        }
    }

    key(player) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(layout, null) as PlayerView).also {
                    it.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    owner.view = it
                }
            },
            update = { if (it.player !== player) it.player = player },
            onRelease = { owner.detach(player) },
            modifier = modifier,
        )
    }
}

@OptIn(UnstableApi::class)
private class LoopSurface {
    var view: PlayerView? = null

    fun detach(player: ExoPlayer) {
        view?.let { if (it.player === player) it.player = null }
        view = null
    }
}

@OptIn(UnstableApi::class)
private fun squaredLoopPlayer(context: Context, url: String, portrait: Boolean): ExoPlayer {
    val app = context.applicationContext
    val source = CacheDataSource.Factory()
        .setCache(SongPlayer.mediaCache(app))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
    val renderers = if (Build.VERSION.SDK_INT >= 37 && Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
        SamsungLoopRenderers(app)
    } else DefaultRenderersFactory(app)
    return ExoPlayer.Builder(app, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(source))
        .setDetachSurfaceTimeoutMs(350L)
        .setReleaseTimeoutMs(350L)
        .build()
        .apply {
            setAudioAttributes(AudioAttributes.DEFAULT, false)
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setMaxVideoSize(if (portrait) 720 else 1280, if (portrait) 1280 else 720)
                .build()
            setMediaItem(MediaItem.fromUri(url))
        }
}

@OptIn(UnstableApi::class)
private class SamsungLoopRenderers(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        out.add(object : MediaCodecVideoRenderer(
            context, codecAdapterFactory, mediaCodecSelector, allowedVideoJoiningTimeMs,
            enableDecoderFallback, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
        ) {
            // The SM-G780G's Android 17 codec threw from native_detachOutputSurface.
            // Retire the codec when the view loses its output instead of switching its surface.
            override fun shouldUseDetachedSurface(codecInfo: MediaCodecInfo) = false
            override fun shouldUsePlaceholderSurface(codecInfo: MediaCodecInfo) = false
        })
    }
}
