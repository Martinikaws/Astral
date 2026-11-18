package com.music.spotui.ui.screens

import com.music.spotui.ui.components.SongQueue
import com.music.spotui.ui.components.animatedRow
import com.music.spotui.ui.immersive.lyrics.QueueDragMath
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppText

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import app.myspoti.engine.R
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.look.isSpotifusionLook
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.theme.GridBackground
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val CLASSIC_HEADING_NEXT = "heading-next"
private const val CLASSIC_AUTOPLAY = "autoplay"

private data class ClassicQueueRow(
    val key: String,
    val item: SongPlayer.QueueItem,
    val queued: Boolean,
)

/**
 * The "play queue" — the track that's playing now plus everything coming up
 * (Spotify's Queue view). Tapping an upcoming track jumps straight to it;
 * dragging the handle reorders it; swiping left removes it; swiping right or
 * tapping the quick-add icon queues a track.
 */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(navController: NavController) {
    // Spotify's real queue, pushed by the engine.
    val q by SongPlayer.engineQueue
    val context = LocalContext.current
    val removed = remember { mutableStateListOf<String>() }
    LaunchedEffect(q) { removed.clear() }

    val rows = remember(q, removed.toList()) {
        val all = q.queued.map { it to true } + q.nextUp.map { it to false }
        val keys = queueKeys(all.map { it.first })
        all.mapIndexedNotNull { i, (item, queued) ->
            val k = keys[i]
            if (k in removed) null else ClassicQueueRow(k, item, queued)
        }
    }
    val baseOrder = remember(rows) {
        val next = rows.filter { !it.queued }.map { it.key }
        rows.filter { it.queued }.map { it.key } + (if (next.isEmpty()) emptyList() else listOf(CLASSIC_HEADING_NEXT)) + next
    }
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragStart by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragRows by remember { mutableStateOf<Map<String, ClassicQueueRow>>(emptyMap()) }
    val order = remember(baseOrder, dragOrder) { dragOrder ?: baseOrder }
    val byKey = remember(rows, dragOrder, dragRows) { if (dragOrder != null) dragRows else rows.associateBy { it.key } }
    val queuedKeys = remember(order) { QueueDragMath.queuedPart(order, CLASSIC_HEADING_NEXT) }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val cur = dragOrder ?: return@rememberReorderableLazyListState
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        val row = dragRows[fromKey] ?: return@rememberReorderableLazyListState
        val block: (String) -> Int = { k -> if (dragRows[k]?.item?.provider == CLASSIC_AUTOPLAY) 1 else 0 }
        val next = QueueDragMath.swap(
            cur, fromKey, toKey, CLASSIC_HEADING_NEXT,
            canLeaveQueue = row.item.provider == SongPlayer.LOCAL,
            block = block,
        ) ?: return@rememberReorderableLazyListState
        dragOrder = next
        Haptics.tick()
    }

    val onDragStarted: (String) -> Unit = { k ->
        dragStart = order
        dragRows = byKey
        dragOrder = order
        Haptics.click()
    }
    val onDragStopped: (String) -> Unit = stop@{ k ->
        val after = dragOrder ?: return@stop
        val start = dragStart
        val known = dragRows
        dragOrder = null
        val row = known[k] ?: return@stop
        val block: (String) -> Int = { key -> if (known[key]?.item?.provider == CLASSIC_AUTOPLAY) 1 else 0 }
        val move = QueueDragMath.moveFor(start, after, k, CLASSIC_HEADING_NEXT, block)
        when (move) {
            QueueDragMath.Move.None -> return@stop
            is QueueDragMath.Move.IntoQueue -> {
                val target = move.target?.let { known[it]?.item }
                if (target == null) SongPlayer.moveToQueueFront(row.item)
                else SongPlayer.moveIntoQueue(row.item, target, move.after)
            }
            is QueueDragMath.Move.Beside -> {
                val target = known[move.target]?.item ?: return@stop
                SongPlayer.reorder(row.item, target, move.after)
            }
        }
        val queuedNew = QueueDragMath.queuedPart(after, CLASSIC_HEADING_NEXT).mapNotNull { known[it]?.item }
        val nextNew = QueueDragMath.nextPart(after, CLASSIC_HEADING_NEXT).mapNotNull { known[it]?.item }
        SongPlayer.showQueueOrder(queuedNew, nextNew)
    }

    val starryEnabled by StarryNightPref.enabled.collectAsState()
    val pageBg = if (starryEnabled && AppBackground == Color.Transparent) Color.Transparent else AppBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (pageBg == Color.Transparent) Modifier else Modifier.background(pageBg))
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.bottom_actions_close),
                tint = AppText,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navController.navigateUp() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.action_queue_label), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            q.current?.let {
                item(key = "now") {
                    QueueHeader(stringResource(R.string.default_notification_channel_name))
                    QueueRow(song = it, highlight = true, onClick = {})
                }
            }
            if (queuedKeys.isNotEmpty()) {
                item(key = "heading-queued") { QueueHeader(stringResource(R.string.next_in_queue), animatedRow()) }
            }
            items(order, key = { it }) { k ->
                if (k == CLASSIC_HEADING_NEXT) {
                    ReorderableItem(reorder, key = k, enabled = false) {
                        QueueHeader(stringResource(R.string.next_up), animatedRow())
                    }
                } else {
                    val row = byKey[k] ?: return@items
                    ReorderableItem(reorder, key = k, enabled = false) { isDragging ->
                        val item = row.item
                        val canQueue = SongQueue.canQueue(item.song)
                        SwipeQueueItem(
                            item = item,
                            canQueue = canQueue,
                            gesturesEnabled = !isDragging,
                            onRemove = {
                                removed += k
                                SongPlayer.removeFromQueue(item)
                            },
                            onQueue = {
                                Haptics.click()
                                SongQueue.add(context, item.song)
                            },
                        ) { isSwiping ->
                            QueueRow(
                                song = item.song,
                                highlight = false,
                                isDragging = isDragging,
                                isSwiping = isSwiping,
                                onClick = { SongPlayer.skipTo(item) },
                                onQuickQueue = if (!row.queued && canQueue) {
                                    {
                                        Haptics.click()
                                        SongQueue.add(context, item.song)
                                    }
                                } else null,
                                handleModifier = Modifier.draggableHandle(
                                    onDragStarted = { onDragStarted(k) },
                                    onDragStopped = { onDragStopped(k) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One key per row: the song's uri, with its occurrence after the first ("uri~2"), never twice the same. */
private fun queueKeys(rows: List<SongPlayer.QueueItem>): List<String> {
    val seen = HashMap<String, Int>()
    return rows.map { item ->
        val count = seen.merge(item.uri, 1, Int::plus)!!
        if (count == 1) item.uri else "${item.uri}~$count"
    }
}

@Composable
private fun QueueHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = AppText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(16.dp, 18.dp, 16.dp, 6.dp)
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeQueueItem(
    item: SongPlayer.QueueItem,
    canQueue: Boolean,
    gesturesEnabled: Boolean,
    onRemove: () -> Unit,
    onQueue: () -> Unit,
    content: @Composable (isSwiping: Boolean) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onRemove()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (canQueue) onQueue()
                    false
                }
                else -> false
            }
        }
    )
    val isSwiping = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
    val actionInk = if (AppAccent.luminance() > 0.45f || isSpotifusionLook()) Color.Black else Color.White
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = canQueue,
        gesturesEnabled = gesturesEnabled,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppAccent)
                            .padding(horizontal = 24.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_to_queue_action_content_description),
                            tint = actionInk,
                        )
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF7A1F1F))
                            .padding(horizontal = 24.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description),
                            tint = AppText,
                        )
                    }
                }
                else -> {}
            }
        },
        content = { content(isSwiping) }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueRow(
    song: com.music.spotui.data.entity.SongsModel,
    highlight: Boolean,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    isSwiping: Boolean = false,
    onQuickQueue: (() -> Unit)? = null,
    handleModifier: Modifier? = null,
) {
    val rowBg = when {
        isDragging -> if (isSpotifusionLook()) com.music.spotui.ui.theme.headerTint(Color(0xFF1E1E24), 0f) else GridBackground
        isSwiping && AppBackground == Color.Transparent -> Color(0xE60A1422)
        else -> AppBackground
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowBg == Color.Transparent) Modifier else Modifier.background(rowBg))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(16.dp, 8.dp)
    ) {
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            model = song.coverUri,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = ""
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (highlight) Color(AppPalette.toArgb()) else AppText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.singer,
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onQuickQueue != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = stringResource(R.string.add_to_queue_action_content_description),
                tint = Color.Gray,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onQuickQueue)
                    .padding(7.dp),
            )
        }
        if (handleModifier != null) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.immersive_queue_reorder),
                tint = Color.Gray,
                modifier = handleModifier.size(36.dp).padding(6.dp),
            )
        }
    }
}
