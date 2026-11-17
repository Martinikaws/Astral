package com.music.spotui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import app.myspoti.engine.R
import app.myspoti.host.android.PlaybackService
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Home screen widget (4x1): SpotiDuck-inspired adaptive dark-glass card with album art,
 * track title & artist, dynamic accent-tinted play/pause pill, and prev/next/like controls.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREVIOUS -> SongPlayer.previous()
            ACTION_TOGGLE -> if (SongPlayer.isPlaying()) SongPlayer.pause(by = "widget") else SongPlayer.play(by = "widget")
            ACTION_NEXT -> SongPlayer.next()
            ACTION_LIKE -> {
                val nextLiked = !shown.liked
                SongPlayer.setLiked(nextLiked)
                shown = shown.copy(liked = nextLiked)
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
                if (ids.isNotEmpty()) render(app, manager, ids)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_PREVIOUS = "app.astral.widget.PREVIOUS"
        private const val ACTION_TOGGLE = "app.astral.widget.TOGGLE"
        private const val ACTION_NEXT = "app.astral.widget.NEXT"
        private const val ACTION_LIKE = "app.astral.widget.LIKE"

        private const val IDLE_TITLE = "Astral"
        private const val DEFAULT_ACCENT = 0xFF7C6BFF.toInt()
        private const val DEFAULT_GLASS = 0xFF1A1C26.toInt()
        private const val COVER_PX = 160
        private const val COVER_RADIUS_PX = 24f

        private data class Shown(
            val title: String,
            val artist: String,
            val coverUrl: String?,
            val playing: Boolean,
            val liked: Boolean,
        )

        @Volatile private var shown = Shown("", "", null, false, false)
        @Volatile private var cover: Bitmap? = null
        @Volatile private var coverUrl: String? = null
        @Volatile private var accentColor: Int = DEFAULT_ACCENT
        @Volatile private var glassColor: Int = DEFAULT_GLASS

        private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val http by lazy { OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build() }

        /** The track on screen changed (or its play/like state): redraw the widgets, if any. */
        fun show(
            context: Context,
            title: String,
            artist: String,
            coverUrl: String?,
            playing: Boolean,
            liked: Boolean = PlaybackService.currentInfo()?.liked ?: false,
        ) {
            val next = Shown(title, artist, coverUrl, playing, liked)
            if (next == shown) return
            shown = next
            if (coverUrl == null) {
                cover = null
                this.coverUrl = null
                accentColor = DEFAULT_ACCENT
                glassColor = DEFAULT_GLASS
            }
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            render(app, manager, ids)
            if (coverUrl != null && coverUrl != this.coverUrl) io.launch {
                val raw = runCatching {
                    http.newCall(Request.Builder().url(coverUrl).build()).execute().use { r ->
                        BitmapFactory.decodeStream(r.body.byteStream())
                            ?.let { Bitmap.createScaledBitmap(it, COVER_PX, COVER_PX, true) }
                    }
                }.getOrNull() ?: return@launch
                if (shown.coverUrl != coverUrl) return@launch
                val rounded = roundedBitmap(raw, COVER_RADIUS_PX)
                val (accent, glass) = extractColors(raw)
                cover = rounded
                accentColor = accent
                glassColor = glass
                this@Companion.coverUrl = coverUrl
                render(app, manager, ids)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = shown
            val hasCover = coverUrl == s.coverUrl && cover != null
            val activeAccent = if (hasCover) accentColor else DEFAULT_ACCENT
            val activeGlass = if (hasCover) glassColor else DEFAULT_GLASS
            val pillIconColor = if (ColorUtils.calculateLuminance(activeAccent) > 0.45) 0xFF0E1015.toInt() else Color.WHITE

            val views = RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setImageViewBitmap(R.id.widget_bg, glassBackgroundBitmap(activeGlass))
                setTextViewText(R.id.widget_title, s.title.ifBlank { IDLE_TITLE })
                setTextViewText(R.id.widget_artist, s.artist)
                setViewVisibility(R.id.widget_artist, if (s.artist.isBlank()) View.GONE else View.VISIBLE)

                val bmp = cover.takeIf { coverUrl == s.coverUrl }
                if (bmp != null) {
                    setImageViewBitmap(R.id.widget_cover, bmp)
                } else {
                    setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
                }

                setImageViewBitmap(R.id.widget_play_pill, pillBitmap(activeAccent))
                setImageViewResource(
                    R.id.widget_play,
                    if (s.playing) app.myspoti.host.android.R.drawable.msp_ic_pause
                    else app.myspoti.host.android.R.drawable.msp_ic_play,
                )
                setInt(R.id.widget_play, "setColorFilter", pillIconColor)

                setImageViewResource(
                    R.id.widget_like,
                    if (s.liked) app.myspoti.host.android.R.drawable.msp_ic_liked
                    else app.myspoti.host.android.R.drawable.msp_ic_like,
                )
                setInt(
                    R.id.widget_like,
                    "setColorFilter",
                    if (s.liked) activeAccent else 0xB8FFFFFF.toInt(),
                )
                setInt(R.id.widget_previous, "setColorFilter", 0xE6FFFFFF.toInt())
                setInt(R.id.widget_next, "setColorFilter", 0xE6FFFFFF.toInt())

                setOnClickPendingIntent(R.id.widget_like, action(context, ACTION_LIKE, 0))
                setOnClickPendingIntent(R.id.widget_previous, action(context, ACTION_PREVIOUS, 1))
                setOnClickPendingIntent(R.id.widget_play, action(context, ACTION_TOGGLE, 2))
                setOnClickPendingIntent(R.id.widget_next, action(context, ACTION_NEXT, 3))
                val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (open != null) {
                    setOnClickPendingIntent(
                        R.id.widget_root,
                        PendingIntent.getActivity(context, 4, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                    )
                }
            }
            manager.updateAppWidget(ids, views)
        }

        private fun extractColors(bmp: Bitmap): Pair<Int, Int> {
            val palette = Palette.from(bmp).generate()
            val rawAccent = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_ACCENT
            // Boost saturation slightly if muted, keep bright enough to pop as a pill.
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(rawAccent, hsl)
            hsl[1] = hsl[1].coerceAtLeast(0.45f)
            hsl[2] = hsl[2].coerceIn(0.42f, 0.72f)
            val accent = ColorUtils.HSLToColor(hsl)

            val rawGlass = palette.darkVibrantSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_GLASS
            val glass = ColorUtils.blendARGB(0xFF101218.toInt(), rawGlass, 0.38f)
            return accent to glass
        }

        private fun roundedBitmap(src: Bitmap, radius: Float): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(src, 0f, 0f, paint)
            return out
        }

        private fun glassBackgroundBitmap(tint: Int): Bitmap {
            val w = 320
            val h = 84
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val start = ColorUtils.setAlphaComponent(tint, 232)
            val end = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tint, 0xFF0B0C10.toInt(), 0.65f), 238)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), start, end, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            // Subtle top glass highlight line
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x24FFFFFF
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), border)
            return bmp
        }

        private fun pillBitmap(color: Int): Bitmap {
            val w = 126
            val h = 102
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, paint)
            return bmp
        }

        private fun action(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, NowPlayingWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
