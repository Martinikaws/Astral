package com.music.spotui.ui.immersive.cupertino

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.data.local.LibraryChip
import com.music.spotui.data.preferences.Look
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.LocalLook
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.theme.CupertinoTertiary

/**
 * What the Cupertino Look's sidebar and the Library page tell each other: the library kind the
 * page shows ([libraryKind], written by the page) and one the sidebar asks for ([requestedKind],
 * read and cleared by the page, which may not be composed yet when the row is tapped).
 */
object CupertinoNav {
    var libraryKind by mutableStateOf(LibraryChip.ALL)
    var requestedKind by mutableStateOf<LibraryChip?>(null)

    /** Opens the Library tab filtered to [kind] ([LibraryChip.ALL]: the whole library). */
    fun openLibrary(kind: LibraryChip, openTab: (Routes) -> Unit) {
        requestedKind = kind
        openTab(Routes.Library)
    }
}

/** True while the Cupertino Look is active. */
@Composable
fun isCupertino(): Boolean = LocalLook.current == Look.CUPERTINO

/** A hairline along the bottom edge, starting [startInset] in (the inset-after-the-icon of iOS lists). */
@Composable
fun Modifier.cupertinoHairline(startInset: Dp = 0.dp): Modifier {
    val color = Immersive.Separator
    val thickness = Immersive.SeparatorThickness
    return drawBehind {
        val t = thickness.toPx().coerceAtLeast(1f)
        val x = startInset.toPx()
        drawRect(color, topLeft = Offset(x, size.height - t), size = Size((size.width - x).coerceAtLeast(0f), t))
    }
}

/**
 * A section heading as in Apple Music: 22sp bold, with a gray chevron after it when it opens
 * something ([onClick]).
 */
@Composable
fun CupertinoSectionHeader(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String = "",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = stringResource(R.string.cupertino_see_all), role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Immersive.Gutter, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = Immersive.Title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onClick != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = CupertinoTertiary,
                    modifier = Modifier.padding(start = 2.dp, top = 3.dp).size(24.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        if (subtitle.isNotBlank()) {
            Text(text = subtitle, style = Immersive.Subhead, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * An iOS segmented control: a dark track with the chosen segment raised on it, scrolling sideways
 * when the segments do not fit. Selecting the chosen one again does nothing.
 */
@Composable
fun CupertinoSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Immersive.Gutter, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Immersive.Raised)
            .horizontalScroll(rememberScrollState())
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Immersive.Elevated else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = label,
                    style = Immersive.Subhead.copy(
                        color = Immersive.Text,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The red circular play button of the artist header: a white glyph on the accent. */
@Composable
fun CupertinoPlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Immersive.Accent)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.immersive_detail_pause else R.string.immersive_shell_play),
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** "‹ Library": the red back affordance above a kind's large title. */
@Composable
fun CupertinoBackRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Immersive.Gutter - 8.dp, end = Immersive.Gutter, top = 4.dp, bottom = 0.dp)
            .height(36.dp),
    ) {
        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Immersive.Accent, modifier = Modifier.size(28.dp))
        Text(text = label, style = Immersive.Body.copy(color = Immersive.Accent), maxLines = 1)
    }
}

/** "⇅ Recently added": the kind pages' sort control, opening the sort sheet. */
@Composable
fun CupertinoSortRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.cupertino_sort), role = Role.Button, onClick = onClick)
            .padding(horizontal = Immersive.Gutter, vertical = 8.dp),
    ) {
        Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = Immersive.Accent, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = Immersive.Subhead.copy(color = Immersive.Accent, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private val RowIconSize = 26.dp
private val RowIconGap = 14.dp

/**
 * The Library page's kind list, as in Apple Music: a red glyph, the kind in 20sp and a gray
 * chevron per row, hairlines inset past the glyph. Each row is an existing filter of the page
 * ([onKind]) or, for Downloaded, the existing page ([onDownloads]).
 */
@Composable
fun CupertinoLibraryKinds(
    onKind: (LibraryChip) -> Unit,
    onDownloads: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LibraryKinds.forEach { (kind, label, icon) ->
            KindRow(stringResource(label), icon) { onKind(kind) }
        }
        KindRow(stringResource(R.string.collection_downloaded), Icons.Rounded.Download, onClick = onDownloads)
    }
}

@Composable
private fun KindRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .height(52.dp)
            .cupertinoHairline(startInset = Immersive.Gutter + RowIconSize + RowIconGap)
            .padding(horizontal = Immersive.Gutter),
    ) {
        Icon(icon, contentDescription = null, tint = Immersive.Accent, modifier = Modifier.size(RowIconSize))
        Spacer(Modifier.width(RowIconGap))
        Text(
            text = label,
            style = Immersive.Body.copy(fontSize = 20.sp, lineHeight = 26.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = CupertinoTertiary)
        }
    }
}
