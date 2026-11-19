/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.wear

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.exp

/**
 * Renders the watch remote's player, library, and idle screens across round (192dp, 227dp)
 * and square (200dp) Wear OS sizes.
 * Run with `./gradlew :wear:testDebugUnitTest --tests '*WatchScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class WatchScreenshotTest(private val size: String) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun player() = capture("player") {
        RemotePlayer(
            state = RemoteState(
                hasSession = true,
                title = "Breathe Deeper (Extended Mix)",
                artist = "Tame Impala, Lil Yachty",
                album = "The Slow Rush",
                contextTitle = "Late Night Psych",
                playing = true,
                explicit = true,
                liked = true,
                shuffle = true,
                positionMs = 95_000,
                durationMs = 250_000,
                atElapsedMs = SystemClock.elapsedRealtime(),
                deviceName = "Living Room Speaker",
                deviceType = "speaker",
                color = 0xFF6B233D.toInt(),
                accentColor = 0xFF1ED760.toInt(),
            ),
            connected = true,
            onDevices = {},
            cover = sampleBlurredCover(),
        )
    }

    @Test
    fun library() = capture("library") {
        val samplePages = mapOf(
            "playlists" to RemotePage(
                items = listOf(
                    RemoteItem("pl:1", "Discover Weekly", "Made for you", browsable = true, playable = false),
                    RemoteItem("pl:2", "Running mix with a long name that does not fit", "48 songs", browsable = true, playable = false),
                    RemoteItem("t|pl:3|1", "Let It Happen", "Tame Impala", browsable = false, playable = true),
                    RemoteItem("pl:4", "Chill evenings", "", browsable = true, playable = false),
                ),
                error = null,
            ),
        )
        RemoteLibrary(
            onPlayed = {},
            pages = samplePages,
            initialOpened = listOf("playlists" to "Playlists"),
        )
    }

    @Test
    fun libraryRoot() = capture("library-root") {
        RemoteLibrary(
            onPlayed = {},
            pages = emptyMap(),
        )
    }

    @Test
    fun idlePlayer() = capture("idle-player") {
        RemotePlayer(
            state = RemoteState(),
            connected = false,
            onDevices = {},
        )
    }

    @Test
    fun visualizer() = capture("visualizer") {
        val samples = ByteArray(100) { i ->
            val pulse = if (i % 10 < 3) 225 else 110 + (i % 7) * 14
            pulse.coerceIn(0, 255).toByte()
        }
        VisualizerScreen(
            state = RemoteState(
                hasSession = true,
                title = "Breathe Deeper (Extended Mix)",
                artist = "Tame Impala, Lil Yachty",
                playing = false,
                positionMs = 4_200L,
                durationMs = 250_000L,
                color = 0xFF6B233D.toInt(),
                accentColor = 0xFF1ED760.toInt(),
            ),
            viz = VizSnapshot(
                key = "sample",
                seed = 1731690000,
                stepMs = 100,
                samples = samples,
            ),
            cover = sampleBlurredCover(),
        )
    }

    private fun sampleBlurredCover(): ImageBitmap {
        val size = 120
        val pixels = IntArray(size * size)
        val denom = (size - 1).toFloat()
        for (y in 0 until size) {
            val v = y / denom
            for (x in 0 until size) {
                val u = x / denom
                val dMagenta = (u - 0.34f) * (u - 0.34f) + (v - 0.32f) * (v - 0.32f)
                val dAmber = (u - 0.74f) * (u - 0.74f) + (v - 0.28f) * (v - 0.28f)
                val dIndigo = (u - 0.56f) * (u - 0.56f) + (v - 0.76f) * (v - 0.76f)
                val wMagenta = exp(-dMagenta * 5.5f)
                val wAmber = exp(-dAmber * 6.5f)
                val wIndigo = exp(-dIndigo * 4.5f)
                val r = (24f + wMagenta * 188f + wAmber * 212f + wIndigo * 48f).toInt().coerceIn(0, 255)
                val g = (16f + wMagenta * 42f + wAmber * 132f + wIndigo * 30f).toInt().coerceIn(0, 255)
                val b = (46f + wMagenta * 82f + wAmber * 26f + wIndigo * 116f).toInt().coerceIn(0, 255)
                pixels[y * size + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            WatchTheme {
                Box(Modifier.background(Color.Black)) {
                    content()
                }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/watch-$name-$size.png")
    }
}

@Config(sdk = [35], qualifiers = "w192dp-h192dp-round-watch-xhdpi", application = Application::class)
class WatchScreenshotTestRound192 : WatchScreenshotTest("round192")

@Config(sdk = [35], qualifiers = "w227dp-h227dp-round-watch-xhdpi", application = Application::class)
class WatchScreenshotTestRound227 : WatchScreenshotTest("round227")

@Config(sdk = [35], qualifiers = "w200dp-h200dp-notround-watch-xhdpi", application = Application::class)
class WatchScreenshotTestSquare200 : WatchScreenshotTest("square200")
