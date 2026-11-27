/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.aod

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dezzy.app.feature.stats.StatsDates
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.player.kit.ArtPalette
import com.dezzy.app.ui.player.kit.KeepScreenOn
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.settings.prefs.AodButtonStyle
import com.dezzy.app.ui.screens.settings.prefs.AodButtonStyleKey
import com.dezzy.app.ui.screens.settings.prefs.AodCoverFractionKey
import com.dezzy.app.ui.screens.settings.prefs.AodCoverShape
import com.dezzy.app.ui.screens.settings.prefs.AodCoverShapeKey
import com.dezzy.app.ui.screens.settings.prefs.AodDimKey
import com.dezzy.app.ui.screens.settings.prefs.AodImmersiveKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowArtistKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowClockKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowControlsKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowProgressKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowTimeKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowTitleKey
import com.dezzy.app.ui.screens.settings.prefs.AodStyle
import com.dezzy.app.ui.screens.settings.prefs.AodStyleKey
import com.dezzy.app.ui.screens.settings.prefs.AodTextScaleKey
import com.dezzy.app.ui.screens.settings.prefs.AodTransitionMsKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Clock

private val AodGrey = Color(0x22FFFFFF)

/**
 * Pantalla siempre activa: fondo negro, reloj, portada, título, progreso y controles. Doble toque
 * o gesto atrás para cerrar; mantiene la pantalla encendida mientras está abierta.
 */
@Composable
fun AodScreen(playerConnection: PlayerConnection, onClose: () -> Unit) {
    val immersive by rememberPreference(AodImmersiveKey, false)
    val style by rememberEnumPreference(AodStyleKey, AodStyle.CLASSIC)
    val shape by rememberEnumPreference(AodCoverShapeKey, AodCoverShape.ROUNDED)
    val buttons by rememberEnumPreference(AodButtonStyleKey, AodButtonStyle.ROUNDED)
    val coverFraction by rememberPreference(AodCoverFractionKey, 0.65f)
    val textScale by rememberPreference(AodTextScaleKey, 1f)
    val dim by rememberPreference(AodDimKey, 0.55f)
    val transitionMs by rememberPreference(AodTransitionMsKey, 700)
    val showClock by rememberPreference(AodShowClockKey, true)
    val showTitle by rememberPreference(AodShowTitleKey, true)
    val showArtist by rememberPreference(AodShowArtistKey, true)
    val showTime by rememberPreference(AodShowTimeKey, true)
    val showProgress by rememberPreference(AodShowProgressKey, true)
    val showControls by rememberPreference(AodShowControlsKey, true)

    PlatformBackHandler(enabled = true) { onClose() }
    KeepScreenOn(true)
    ImmersiveSystemBars(immersive)

    val meta by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val controller = playerConnection.controller
    val position by remember(controller) { controller.state.map { it.positionMs }.distinctUntilChanged() }
        .collectAsState(controller.state.value.positionMs)
    val duration by remember(controller) { controller.state.map { it.durationMs }.distinctUntilChanged() }
        .collectAsState(controller.state.value.durationMs)

    // Reloj del sistema, cada 10 s.
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(10_000)
        }
    }

    var palette by remember { mutableStateOf<ArtPalette?>(null) }
    LaunchedEffect(meta?.thumbnailUrl, style) {
        palette = if (style == AodStyle.SPOTLIGHT) loadArtPalette(meta?.thumbnailUrl, 16) else null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onClose() }) },
    ) {
        Crossfade(targetState = meta?.id, animationSpec = tween(transitionMs), label = "aod-bg") {
            when (style) {
                AodStyle.AMBIENT -> Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = meta?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(40.dp),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
                }
                AodStyle.SPOTLIGHT -> {
                    val tone = palette?.let { Color(it.getDominantColor(0xFF303030.toInt()) or 0xFF000000.toInt()) }
                        ?: Color(0xFF303030)
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.radialGradient(listOf(tone, Color.Black)))
                            .background(Color.Black.copy(alpha = dim)),
                    )
                }
                else -> Unit
            }
        }

        val insets = if (immersive) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)
        Column(
            Modifier.fillMaxSize().then(insets).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showClock) {
                AodClock(nowMs, textScale, big = style == AodStyle.MINIMAL)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val coverWidth = maxWidth * coverFraction.coerceIn(0.4f, 1f)
                Crossfade(targetState = meta?.id, animationSpec = tween(transitionMs), label = "aod-content") {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (style != AodStyle.MINIMAL) {
                            AsyncImage(
                                model = meta?.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(coverWidth)
                                    .aspectRatio(1f)
                                    .clip(aodShape(shape)),
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        if (showTitle) {
                            Text(
                                meta?.title.orEmpty(),
                                style = MaterialTheme.typography.titleLarge.scaled(textScale),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (showArtist) {
                            Text(
                                meta?.artists?.joinToString(", ") { it.name }.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge.scaled(textScale),
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (showProgress) {
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = Color(0x33FFFFFF),
                )
            }
            if (showTime) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val small = MaterialTheme.typography.bodySmall.scaled(textScale)
                    Text(makeTimeString(position), style = small, color = Color.Gray)
                    Text(if (duration > 0) makeTimeString(duration) else "", style = small, color = Color.Gray)
                }
            }
            if (showControls) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AodButton(buttons, accentFill = false, size = 52.dp, onClick = { playerConnection.player.seekToPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(28.dp), tint = Color.White)
                    }
                    AodButton(buttons, accentFill = true, size = 68.dp, onClick = { playerConnection.player.togglePlayPause() }) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Pausar" else "Reproducir",
                            Modifier.size(38.dp),
                            tint = if (buttons == AodButtonStyle.ACCENT) Color.Black else Color.White,
                        )
                    }
                    AodButton(buttons, accentFill = false, size = 52.dp, onClick = { playerConnection.player.seekToNext() }) {
                        Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(28.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    if (fontSize == TextUnit.Unspecified) this else copy(fontSize = fontSize * scale, lineHeight = TextUnit.Unspecified)

@Composable
private fun AodClock(nowMs: Long, scale: Float, big: Boolean) {
    val offset = StatsDates.offsetMs(nowMs)
    val local = nowMs + offset
    val minutesOfDay = (local.mod(StatsDates.DAY_MS) / 60_000L).toInt()
    val time = "${(minutesOfDay / 60).toString().padStart(2, '0')}:${(minutesOfDay % 60).toString().padStart(2, '0')}"
    val day = StatsDates.localDay(nowMs, offset)
    val (_, month, dom) = StatsDates.civil(day)
    val dow = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")[StatsDates.dayOfWeek(day)]
    val date = "$dow, $dom ${StatsDates.MONTHS_SHORT[month - 1]}"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time,
            style = (if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium).scaled(scale),
            fontWeight = FontWeight.Light,
            color = Color.White,
        )
        Text(date, style = MaterialTheme.typography.bodyLarge.scaled(scale), color = Color.Gray)
    }
}

@Composable
private fun AodButton(
    style: AodButtonStyle,
    accentFill: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val (shape, fill) = when (style) {
        AodButtonStyle.ROUNDED -> CircleShape to AodGrey
        AodButtonStyle.SQUARE -> RoundedCornerShape(12.dp) to AodGrey
        AodButtonStyle.ACCENT -> CircleShape to (if (accentFill) Color.White else AodGrey)
    }
    Box(
        Modifier.size(size).clip(shape).background(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Forma recortable de la portada en la pantalla siempre activa. */
internal fun aodShape(shape: AodCoverShape): Shape = when (shape) {
    AodCoverShape.ROUNDED -> RoundedCornerShape(24.dp)
    AodCoverShape.CIRCLE -> CircleShape
    AodCoverShape.SQUIRCLE -> RoundedCornerShape(36)
    AodCoverShape.DIAMOND -> GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width / 2f, size.height)
        lineTo(0f, size.height / 2f)
        close()
    }
    AodCoverShape.HEXAGON -> GenericShape { size, _ ->
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 6) {
            val a = PI / 3 * i
            val x = c.x + c.x * cos(a).toFloat()
            val y = c.y + c.y * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    AodCoverShape.STAR -> GenericShape { size, _ ->
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 10) {
            val a = -PI / 2 + PI / 5 * i
            val k = if (i % 2 == 0) 1f else 0.45f
            val x = c.x + c.x * k * cos(a).toFloat()
            val y = c.y + c.y * k * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    AodCoverShape.ARCH -> GenericShape { size, _ ->
        val r = size.width / 2f
        moveTo(0f, size.height)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, size.width, size.width), 180f, 180f, false)
        lineTo(size.width, size.height)
        close()
    }
    AodCoverShape.FLOWER -> GenericShape { size, _ ->
        val c = Offset(size.width / 2f, size.height / 2f)
        val steps = 180
        for (i in 0..steps) {
            val a = 2 * PI * i / steps
            val k = 0.86f + 0.14f * sin(8 * a).toFloat()
            val x = c.x + c.x * k * cos(a).toFloat()
            val y = c.y + c.y * k * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
