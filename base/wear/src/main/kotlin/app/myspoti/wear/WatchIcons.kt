// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
// The paths below are Google's Material Icons ("queue_music", "shuffle", "repeat", "repeat_one",
// "smartphone", "watch", "computer", "speaker", "tv", "devices", "favorite", "favorite_border",
// "explicit", "replay_10"/"forward_10" arc with 15s badge, "home", "library_music", "mic",
// "file_download", "music_note", "album", "person", "playlist_play", "radio", "check"),
// Apache-2.0.
package app.myspoti.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WatchColorScheme = ColorScheme(
    primary = Color(0xFF1ED760),
    onPrimary = Color.Black,
    background = Color.Black,
    surfaceContainer = Color(0xFF14161A),
)

@Composable
internal fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatchColorScheme,
        content = content,
    )
}

/**
 * Vector icons drawn directly here: `androidx.compose.material:material-icons-extended`
 * would add ~60 MB to the watch APK.
 */
internal object WatchIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

    private const val FIFTEEN_DIGITS =
        "M10.9,15.8H9.8v-3.8L8.8,12.4v-0.9l2.0,-0.7h0.1V15.8z" +
            "M15.1,10.8h-3.2v2.7h2.2v1.3h-2.2v1.0h3.3v-3.2h-2.3v-0.8h2.2V10.8z"

    val QueueMusic: ImageVector by lazy {
        icon("QueueMusic", "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3V16zM17,6v8.18C16.69,14.07 16.35,14 16,14c-1.66,0 -3,1.34 -3,3s1.34,3 3,3s3,-1.34 3,-3V8h3V6H17z")
    }

    val Shuffle: ImageVector by lazy {
        icon("Shuffle", "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5L20,4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20L20,20v-5.5l-2.04,2.04 -3.13,-3.13z")
    }

    val Repeat: ImageVector by lazy {
        icon("Repeat", "M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z")
    }

    val RepeatOne: ImageVector by lazy {
        icon("RepeatOne", "M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15L13,9h-1l-2,1v1h1.5v4L13,15z")
    }

    val Phone: ImageVector by lazy {
        icon("Phone", "M17,1.01L7,1C5.9,1 5,1.9 5,3v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3C19,1.9 18.1,1.01 17,1.01zM17,19H7V5h10V19z")
    }

    val Watch: ImageVector by lazy {
        icon("Watch", "M20,12c0,-2.54 -1.19,-4.81 -3.04,-6.27L16,0H8L7.05,5.73C5.19,7.19 4,9.45 4,12s1.19,4.81 3.05,6.27L8,24h8l0.96,-5.73C18.81,16.81 20,14.54 20,12zM6,12c0,-3.31 2.69,-6 6,-6s6,2.69 6,6 -2.69,6 -6,6 -6,-2.69 -6,-6z")
    }

    val Computer: ImageVector by lazy {
        icon("Computer", "M20,18c1.1,0 1.99,-0.9 1.99,-2L22,6c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2H0v2h24v-2h-4zM4,6h16v10H4V6z")
    }

    val Speaker: ImageVector by lazy {
        icon("Speaker", "M17,2H7c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,1.99 2,1.99L17,22c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,4c1.1,0 2,0.9 2,2s-0.9,2 -2,2c-1.11,0 -2,-0.9 -2,-2s0.89,-2 2,-2zM12,20c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,12c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z")
    }

    val Tv: ImageVector by lazy {
        icon("Tv", "M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z")
    }

    val Devices: ImageVector by lazy {
        icon("Devices", "M4,6h18V4H4c-1.1,0 -2,0.9 -2,2v11H0v3h14v-3H4V6zM23,8h-6c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1V9c0,-0.55 -0.45,-1 -1,-1zM22,17h-4v-7h4v7z")
    }

    val HeartFilled: ImageVector by lazy {
        icon("HeartFilled", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    }

    val HeartOutline: ImageVector by lazy {
        icon("HeartOutline", "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 -0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z")
    }

    val Explicit: ImageVector by lazy {
        icon("Explicit", "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM15,9h-4v2h4v2h-4v2h4v2H9V7h6v2z")
    }

    val SkipBack15: ImageVector by lazy {
        icon("SkipBack15", "M11.99,5V1l-5,5 5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6h-2c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z$FIFTEEN_DIGITS")
    }

    val SkipForward15: ImageVector by lazy {
        icon("SkipForward15", "M18,13c0,3.31 -2.69,6 -6,6s-6,-2.69 -6,-6s2.69,-6 6,-6v4l5,-5l-5,-5v4c-4.42,0 -8,3.58 -8,8c0,4.42 3.58,8 8,8c4.42,0 8,-3.58 8,-8H18z$FIFTEEN_DIGITS")
    }

    val Home: ImageVector by lazy {
        icon("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    }

    val Library: ImageVector by lazy {
        icon("Library", "M20,2L8,2c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM18,7h-3v5.5c0,1.38 -1.12,2.5 -2.5,2.5S10,13.88 10,12.5s1.12,-2.5 2.5,-2.5c0.57,0 1.08,0.19 1.5,0.51L14,5h4v2zM4,6L2,6v14c0,1.1 0.9,2 2,2h14v-2L4,20L4,6z")
    }

    val Podcasts: ImageVector by lazy {
        icon("Podcasts", "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z")
    }

    val Downloads: ImageVector by lazy {
        icon("Downloads", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote", "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z")
    }

    val Album: ImageVector by lazy {
        icon("Album", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,16c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4 4,1.79 4,4 -1.79,4 -4,4zM12,11c-0.55,0 -1,0.45 -1,1s0.45,1 1,1 1,-0.45 1,-1 -0.45,-1 -1,-1z")
    }

    val Artist: ImageVector by lazy {
        icon("Artist", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    }

    val Playlist: ImageVector by lazy {
        icon("Playlist", "M3,10h11v2H3V10zM3,6h11v2H3V6zM3,14h7v2H3V14zM16,13v8l6,-4L16,13z")
    }

    val Radio: ImageVector by lazy {
        icon("Radio", "M3.24,6.15C2.51,6.43 2,7.17 2,8v12c0,1.1 0.89,2 2,2h16c1.11,0 2,-0.9 2,-2L22,8c0,-1.1 -0.89,-2 -2,-2L8.3,6l8.26,-3.34L15.88,1 3.24,6.15zM7,20c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM20,12h-2v-2h-2v2L4,12L4,8h16v4z")
    }

    val Check: ImageVector by lazy {
        icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    }
}
