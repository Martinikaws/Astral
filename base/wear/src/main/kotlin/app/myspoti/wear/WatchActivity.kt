package app.myspoti.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * The Wear OS Phone Remote for Astral on the phone (phone remote only; standalone watch playback
 * is not used). Auto-launches on `com.google.android.wearable.action.MEDIA_CONTROLS` when
 * playback starts on the phone.
 */
class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            WatchTheme {
                RemoteApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_MEDIA_CONTROLS) RemoteLink.send("watch_open")
    }

    private companion object {
        const val ACTION_MEDIA_CONTROLS = "com.google.android.wearable.action.MEDIA_CONTROLS"
    }
}
