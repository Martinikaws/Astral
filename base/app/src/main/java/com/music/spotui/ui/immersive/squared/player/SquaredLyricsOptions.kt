/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/ui/player/LyricsOptions.kt at 139441f.
 * Modified for Astral, 2026-11.
 *
 * Changes: package renamed; Square's panel that surveys every lyrics source (LyricsLibrary) is
 * replaced by a row that shows the current source and opens Astral's own lyrics-source sheet,
 * plus the translation switch; the strings are Astral's squared_* resources.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.ui.immersive.squared.player

import com.music.spotui.ui.immersive.squared.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.Quotes
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.X
import app.myspoti.engine.R
import com.music.spotui.ui.immersive.squared.Lyrics
import com.music.spotui.ui.immersive.squared.glass.backdrop.Backdrop
import com.music.spotui.ui.immersive.squared.glass.pressable
import kotlinx.coroutines.launch

/**
 * The way to the other sources and styles: a round button in the column of
 * the lyrics' controls, above the translation, like them. It used to be a
 * labelled chip on its own in the other corner.
 */
@Composable
fun LyricsSourceChip(source: String?, backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = source?.takeIf { it.isNotBlank() } ?: stringResource(R.string.squared_lyrics_sources)
    GlassSurface(
        backdrop = backdrop,
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = modifier
            .size(44.dp)
            .pressable(onClick, shape = androidx.compose.foundation.shape.CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                PhosphorIcons.Regular.Quotes,
                // Says which source it is, for whoever cannot see the words.
                contentDescription = label,
                tint = GlassInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * "Lyrics from Spicy Lyrics · uploaded by …, made by …", each name a link to
 * the person's page, as the source asks; the names only when it gives them.
 */
@Composable
fun LyricsCreditLine(credit: com.music.spotui.ui.immersive.squared.LyricsCredit, modifier: Modifier = Modifier) {
    val from = stringResource(R.string.squared_lyrics_from, credit.provider)
    val uploadedBy = stringResource(R.string.squared_lyrics_uploaded_by)
    val madeBy = stringResource(R.string.squared_lyrics_made_by)
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(from)
        fun person(label: String, name: String?, url: String?) {
            if (name == null) return
            append(" · ")
            append(label)
            append(" ")
            if (url != null) {
                withLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url,
                        androidx.compose.ui.text.TextLinkStyles(
                            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold),
                        ),
                    ),
                ) { append(name) }
            } else {
                append(name)
            }
        }
        person(uploadedBy, credit.uploader, credit.uploaderUrl)
        person(madeBy, credit.maker, credit.makerUrl)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = GlassInkDim,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier,
    )
}
