package com.music.spotui.ui.immersive.cupertino

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.di.Palette
import com.music.spotui.ui.components.WindowSize
import com.music.spotui.ui.components.onHomeItemClick
import com.music.spotui.ui.components.prefetchHomeItem
import com.music.spotui.ui.components.pressHint
import com.music.spotui.ui.components.windowSize
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.components.ImmersiveCover
import com.music.spotui.ui.immersive.components.ImmersivePlayingBars
import com.music.spotui.ui.immersive.components.ImmersiveShelfRow
import com.music.spotui.ui.immersive.components.immersiveClickable
import com.music.spotui.ui.theme.CupertinoElevated

/**
 * Cupertino Home's lead shelf, "Top Picks for You": large cards about three quarters of the page wide
 * (the next one peeking in), a small grey caption above each, the artwork filling the top and a solid
 * block at the bottom, in a colour taken from the artwork, holding the title and the subtitle.
 */
@Composable
internal fun CupertinoTopPicks(
    navController: NavController,
    section: HomeSection,
    keys: List<String>,
    subtitleOf: @Composable (HomeItem) -> String,
    isPlaying: (HomeItem) -> Boolean = { false },
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        CupertinoSectionHeader(stringResource(R.string.cupertino_top_picks), onClick = null)
        BoxWithConstraints {
            val max = if (windowSize().large) WindowSize.LargeHeroCardMaxWidth else 320.dp
            val cardWidth = minOf(maxWidth * 0.75f, max)
            val caption = section.title.ifBlank { stringResource(R.string.cupertino_made_for_you) }
            ImmersiveShelfRow {
                items(count = section.items.size, key = { keys[it] }, contentType = { section.items[it]::class }) { i ->
                    val item = section.items[i]
                    val hint = remember(item) { { prefetchHomeItem(item) } }
                    val onClick = remember(navController, item) { { onHomeItemClick(navController, item) } }
                    TopPickCard(item, caption, subtitleOf(item), isPlaying(item), onClick, Modifier.width(cardWidth).pressHint(hint))
                }
            }
        }
    }
}

@Composable
private fun TopPickCard(item: HomeItem, caption: String, subtitle: String, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val url = item.imageUrl.orEmpty()
    var block by remember(url) { mutableStateOf(Palette.cachedFirst(url) ?: CupertinoElevated) }
    LaunchedEffect(url) {
        if (url.isNotBlank()) Palette().extractFirstColorFromImageUrl(context, url) { block = it }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier) {
        Text(
            text = caption,
            style = Immersive.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth().immersiveClickable(onClick = onClick, pressedScale = 0.98f).clip(shape).background(block)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                ImmersiveCover(url, Modifier.fillMaxSize(), shape = RectangleShape, border = false)
                // The playing context: bars over the artwork and the title in the accent, as the other Looks' cards.
                if (playing) ImmersivePlayingBars(Modifier.align(Alignment.Center))
            }
            // Fills the rest of the 4:5 card: the artwork is the square, this the quarter under it.
            Column(Modifier.fillMaxWidth().aspectRatio(4f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = item.name,
                    style = Immersive.Body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = if (playing) Immersive.Accent else Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = Immersive.Caption.copy(color = Color.White.copy(alpha = 0.72f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
