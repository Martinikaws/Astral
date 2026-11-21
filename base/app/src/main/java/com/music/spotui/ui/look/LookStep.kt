package com.music.spotui.ui.look

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import com.music.spotui.ui.components.WindowSize
import com.music.spotui.ui.components.windowSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.data.preferences.StarryNightPref
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppSans
import com.music.spotui.ui.theme.CupertinoAccent
import com.music.spotui.ui.theme.SquaredAccent
import com.music.spotui.ui.theme.CupertinoBackground
import com.music.spotui.ui.theme.CupertinoBorder
import com.music.spotui.ui.theme.CupertinoGlass
import com.music.spotui.ui.theme.CupertinoGroup
import com.music.spotui.ui.theme.GlassifyBackground
import com.music.spotui.ui.theme.GlassifyBorder
import com.music.spotui.ui.theme.GlassifyGlassFill
import com.music.spotui.ui.theme.LiquidMusicAccent
import com.music.spotui.ui.theme.LiquidMusicBackground
import com.music.spotui.ui.theme.LiquidMusicBorder
import com.music.spotui.ui.theme.LiquidMusicSurface
import com.music.spotui.ui.theme.SpotifusionBackground
import com.music.spotui.ui.theme.SpotifusionGreen
import com.music.spotui.ui.theme.StarryAccent
import com.music.spotui.ui.theme.StarryBlue
import kotlin.math.abs

/** Set when the step is the "new look" popup shown to existing users: its own title, buttons and note. */
class LookOffer(val onKeep: () -> Unit)

/**
 * Onboarding / Settings Look chooser: all the unified Looks ([Look.entries]) over a live preview
 * that plays the demo song in the chosen look. Saved immediately when tapped or swiped.
 */
@Composable
fun LookStep(onNext: () -> Unit, offer: LookOffer? = null) {
    val context = LocalContext.current
    val look = LocalLook.current
    val immersive = look.isImmersive()
    val starryEnabled by StarryNightPref.enabled.collectAsState()
    val starryActive = starryEnabled &&
        !app.myspoti.power.EnergySettings.oledBlack &&
        com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.BACKGROUND) == null
    val reduceMotion = rememberReduceMotion()
    var paused by remember { mutableStateOf(false) }
    val clock = rememberDemoClock(running = !paused && !reduceMotion)
    val choose: (Look) -> Unit = { if (it != look) LookPref.set(context, it) }
    val family = if (immersive) ImmersiveSans else AppSans
    val text = TextStyle(fontFamily = family, color = Color.White)
    val dim = Color.White.copy(alpha = if (immersive) 0.72f else 1f)
    val subtle = if (immersive) Color.White.copy(alpha = 0.7f) else Color(0xFFB3B3B3)
    val buttonColor = when (look) {
        Look.SPOTIFUSION -> SpotifusionGreen
        Look.LIQUID_MUSIC -> LiquidMusicAccent
        Look.CUPERTINO -> CupertinoAccent
        Look.SQUARED -> SquaredAccent
        Look.GLASSIFY -> StarryBlue
        Look.IMMERSIVE -> Color.White
        Look.STARRY_NIGHT -> StarryAccent
        Look.CLASSIC -> AppAccent
    }
    val buttonInk = if (buttonColor.luminance() > 0.45f) Color.Black else Color.White

    Box(Modifier.fillMaxSize()) {
        Crossfade(look to starryActive, animationSpec = tween(350), label = "lookBackdrop") { (activeLook, starry) ->
            when {
                starry -> {
                    val accentWash = activeLook.swatchColor()
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.radialGradient(
                                colors = listOf(accentWash.copy(alpha = 0.14f), Color.Transparent),
                            ),
                        ),
                    )
                }
                activeLook == Look.IMMERSIVE ->
                    MeshGradientBackground(cyclingMeshPalette(LookMeshPalettes), colorFadeMillis = MESH_CYCLE_MILLIS)
                activeLook == Look.GLASSIFY -> Box(
                    Modifier.fillMaxSize().background(GlassifyBackground).background(
                        Brush.radialGradient(colors = listOf(StarryBlue.copy(alpha = 0.28f), Color.Transparent)),
                    ),
                )
                activeLook == Look.LIQUID_MUSIC -> Box(
                    Modifier.fillMaxSize().background(LiquidMusicBackground).background(
                        Brush.radialGradient(colors = listOf(LiquidMusicAccent.copy(alpha = 0.26f), Color.Transparent)),
                    ),
                )
                activeLook == Look.CUPERTINO -> Box(
                    Modifier.fillMaxSize().background(CupertinoBackground).background(
                        Brush.radialGradient(colors = listOf(CupertinoAccent.copy(alpha = 0.20f), Color.Transparent)),
                    ),
                )
                activeLook == Look.SQUARED -> Box(
                    Modifier.fillMaxSize().background(CupertinoBackground).background(
                        Brush.radialGradient(colors = listOf(SquaredAccent.copy(alpha = 0.20f), Color.Transparent)),
                    ),
                )
                activeLook == Look.SPOTIFUSION -> Box(Modifier.fillMaxSize().background(SpotifusionBackground))
                else -> Box(Modifier.fillMaxSize().background(AppBackground))
            }
        }
        // Large windows: the content is a centred column, and a wide landscape window puts the
        // preview beside the picker (stacked, the preview was squeezed between the two).
        val window = windowSize()
        // A TV keeps its phone-style layout (D-pad navigation order).
        val tablet = window.large && !com.music.spotui.ui.tv.Tv.isTv(androidx.compose.ui.platform.LocalContext.current)
        val sideBySide = tablet && window.widthDp > window.heightDp
        val title: @Composable () -> Unit = {
            Text(
                stringResource(if (offer != null) R.string.look_offer_title else R.string.setup_look_title),
                style = text,
                fontSize = 26.sp,
                fontWeight = if (immersive) FontWeight.SemiBold else FontWeight.Bold,
            )
            Text(stringResource(R.string.setup_look_subtitle), style = text, color = subtle, fontSize = 14.sp)
        }
        val preview: @Composable (Modifier) -> Unit = { previewModifier ->
            Box(previewModifier) {
                val description = stringResource(R.string.setup_look_preview, look.label)
                if (reduceMotion) {
                    LookStills(look, subtle, Modifier.fillMaxSize().semantics { contentDescription = description })
                } else {
                    PhoneFrame(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                        Crossfade(look, animationSpec = tween(350), label = "lookPreview") {
                            LookStepDemo(it, clock, still = false)
                        }
                    }
                }
                // Above the preview, so nothing in it reacts: a tap pauses or resumes, a swipe
                // cycles through all the Looks.
                Box(Modifier.matchParentSize().pointerInput(look) {
                    val swipe = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var dx = 0f
                        var moved = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dx = change.position.x - down.position.x
                            if (abs(dx) > viewConfiguration.touchSlop || abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) moved = true
                            change.consume()
                        }
                        val all = Look.entries
                        val idx = all.indexOf(look).coerceAtLeast(0)
                        when {
                            !moved -> paused = !paused
                            dx < -swipe -> choose(all[(idx + 1) % all.size])
                            dx > swipe -> choose(all[(idx - 1 + all.size) % all.size])
                        }
                    }
                })
            }
        }
        val caption: @Composable () -> Unit = {
            Text(
                "${look.label} — ${look.detail}",
                style = text,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(if (reduceMotion) R.string.setup_look_still_hint else R.string.setup_look_tap_hint),
                style = text, color = dim.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val actions: @Composable () -> Unit = {
            Button(
                onClick = onNext,
                shape = when (look) {
                    Look.LIQUID_MUSIC -> RoundedCornerShape(20.dp)
                    Look.CUPERTINO, Look.SQUARED -> RoundedCornerShape(50)
                    Look.IMMERSIVE, Look.GLASSIFY -> RoundedCornerShape(14.dp)
                    else -> ButtonDefaults.shape
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = buttonInk,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    stringResource(if (offer != null) R.string.look_offer_try else R.string.setup_continue),
                    fontFamily = family,
                    fontSize = 16.sp,
                    fontWeight = if (immersive) FontWeight.SemiBold else FontWeight.Bold,
                )
            }
            if (offer != null) {
                TextButton(onClick = offer.onKeep, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.look_offer_keep), color = Color.White, fontFamily = family, fontSize = 15.sp)
                }
                Text(
                    stringResource(R.string.look_offer_note),
                    style = text,
                    color = dim.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (sideBySide) {
                Row(Modifier.fillMaxHeight().widthIn(max = WindowSize.MaxPageWidth), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        title()
                        LookSegments(look, family, choose, vertical = true, modifier = Modifier.weight(1f))
                        actions()
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        preview(Modifier.fillMaxWidth().weight(1f))
                        caption()
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxHeight().then(if (tablet) Modifier.widthIn(max = WindowSize.LookStepColumnWidth) else Modifier.fillMaxWidth()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    title()
                    LookSegments(look, family, choose)
                    preview(Modifier.fillMaxWidth().weight(1f))
                    caption()
                    actions()
                }
            }
        }
    }
}

private fun Look.swatchColor(): Color = when (this) {
    Look.CLASSIC -> StarryAccent
    Look.IMMERSIVE -> Color.White
    Look.SPOTIFUSION -> SpotifusionGreen
    Look.GLASSIFY -> StarryBlue
    Look.LIQUID_MUSIC -> LiquidMusicAccent
    Look.CUPERTINO -> CupertinoAccent
    Look.SQUARED -> SquaredAccent
    Look.STARRY_NIGHT -> StarryAccent
}

/** Preview inside [PhoneFrame], adapting [LookDemo] with visual cues for every Look. */
@Composable
private fun LookStepDemo(look: Look, clock: DemoClock, still: Boolean) {
    Box(Modifier.fillMaxSize()) {
        LookDemo(look, clock, still = still)
        when (look) {
            Look.STARRY_NIGHT -> Unit
            Look.SPOTIFUSION -> {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SpotifusionGreen)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text("SPOTIFUSION 1:1", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Look.GLASSIFY -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(StarryBlue.copy(alpha = 0.22f), Color.Transparent),
                            ),
                        )
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                colors = listOf(GlassifyBorder, Color.White.copy(alpha = 0.06f)),
                                start = Offset.Zero,
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                            ),
                            RoundedCornerShape(44.dp),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(GlassifyGlassFill)
                        .border(1.dp, GlassifyBorder, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text("GLASSIFY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Look.LIQUID_MUSIC -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, LiquidMusicBorder, RoundedCornerShape(44.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LiquidMusicSurface)
                        .border(1.dp, LiquidMusicAccent.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text("LIQUIDMUSIC", color = LiquidMusicAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Look.CUPERTINO -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, CupertinoBorder, RoundedCornerShape(44.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CupertinoGlass)
                        .border(1.dp, CupertinoBorder, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text("CUPERTINO", color = CupertinoAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Look.SQUARED -> {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text("SQUARED", color = SquaredAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Look.CLASSIC, Look.IMMERSIVE -> Unit
        }
    }
}

/** Horizontally scrollable Look card picker so every Look has room for its title and summary. */
@Composable
private fun LookSegments(
    look: Look,
    family: androidx.compose.ui.text.font.FontFamily,
    choose: (Look) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val all = Look.entries
    val listState = rememberLazyListState()
    LaunchedEffect(look) {
        val idx = all.indexOf(look).coerceAtLeast(0)
        runCatching { listState.animateScrollToItem(idx) }
    }
    val picker: LazyListScope.() -> Unit = {
        itemsIndexed(all) { _, option ->
            val selected = option == look
            val swatch = option.swatchColor()
            val cardShape = when (option) {
                Look.LIQUID_MUSIC -> RoundedCornerShape(20.dp)
                Look.CUPERTINO -> RoundedCornerShape(14.dp)
                Look.GLASSIFY -> RoundedCornerShape(16.dp)
                Look.IMMERSIVE -> RoundedCornerShape(14.dp)
                else -> RoundedCornerShape(12.dp)
            }
            val fill by animateColorAsState(
                when {
                    selected && option == Look.SPOTIFUSION -> SpotifusionGreen
                    selected && option == Look.IMMERSIVE -> Color.White.copy(alpha = 0.92f)
                    selected && option == Look.GLASSIFY -> GlassifyGlassFill
                    selected && option == Look.LIQUID_MUSIC -> LiquidMusicSurface
                    option == Look.CUPERTINO -> CupertinoGroup
                    selected -> AppAccent
                    option == Look.GLASSIFY -> GlassifyGlassFill
                    option == Look.LIQUID_MUSIC -> LiquidMusicSurface.copy(alpha = 0.65f)
                    else -> Color(0xCC182234)
                },
                label = "lookCardFill",
            )
            val ink = when {
                selected && (option == Look.SPOTIFUSION || option == Look.IMMERSIVE || option == Look.CLASSIC) ->
                    if (fill.luminance() > 0.45f) Color.Black else Color.White
                else -> Color.White
            }
            val borderColor = when {
                selected && option == Look.LIQUID_MUSIC -> LiquidMusicAccent
                selected && option == Look.CUPERTINO -> CupertinoAccent
                selected && option == Look.GLASSIFY -> GlassifyBorder
                selected -> swatch
                else -> Color.White.copy(alpha = 0.12f)
            }
            Column(
                Modifier
                    .then(if (vertical) Modifier.fillMaxWidth() else Modifier.width(164.dp))
                    .clip(cardShape)
                    .background(fill, cardShape)
                    .border(if (selected) 1.5.dp else 1.dp, borderColor, cardShape)
                    .clickable { choose(option) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected && ink == Color.Black) Color.Black else swatch))
                    Text(
                        option.label,
                        color = ink,
                        fontFamily = family,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    option.detail,
                    color = ink.copy(alpha = 0.76f),
                    fontFamily = family,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (vertical) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth(), content = picker)
    } else {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = modifier.fillMaxWidth(),
            content = picker,
        )
    }
}

/** "Remove animations": three still frames of the loop instead of the loop. */
@Composable
private fun LookStills(look: Look, caption: Color, modifier: Modifier) {
    val captions = listOf(R.string.setup_look_still_player, R.string.setup_look_still_paused, R.string.setup_look_still_lyrics)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DemoScript.stills.forEachIndexed { index, ms ->
            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneFrame(Modifier.fillMaxWidth().weight(1f)) { LookStepDemo(look, rememberStillClock(ms), still = true) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(captions[index]), color = caption, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
