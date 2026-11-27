/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album as OutlinedAlbum
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.InterpreterMode
import androidx.compose.material.icons.outlined.Speed as OutlinedSpeed
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.canvas.CANVAS_SONG_DEFAULT
import com.dezzy.app.feature.canvas.CanvasLoop
import com.dezzy.app.feature.canvas.CanvasResolver
import com.dezzy.app.feature.canvas.CanvasSongEnabledKey
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.ui.player.canvas.CanvasVideo
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.glass.DezzyIconButton
import com.dezzy.app.feature.connect.ConnectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.sin

// ── Barra de progreso ────────────────────────────────────────────────────────────────────────

/** Color de la pista sin reproducir que fija un estilo (null = el del propio slider). */
val LocalSliderTrackBase = compositionLocalOf<Color?> { null }

/** Pista redondeada de los sliders del reproductor: base y parte ya reproducida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    colors: SliderColors,
    trackHeight: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    val base = LocalSliderTrackBase.current ?: colors.inactiveTrackColor
    val range = sliderState.valueRange
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(trackHeight)) {
        val y = size.height / 2
        val stroke = size.height
        drawLine(base, Offset(stroke / 2, y), Offset(size.width - stroke / 2, y), stroke, StrokeCap.Round)
        if (fraction > 0f) {
            val end = (size.width * fraction).coerceAtLeast(stroke / 2)
            drawLine(colors.activeTrackColor, Offset(stroke / 2, y), Offset(end, y), stroke, StrokeCap.Round)
        }
    }
}

/**
 * Slider de reproducción con el estilo global (Ajustes › Apariencia): los 12 estilos de la app actual.
 * Las animaciones sólo corren mientras suena; [songKey] reinicia la animación al cambiar de canción.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledPlaybackSlider(
    sliderStyle: SliderStyle,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    isPlaying: Boolean,
    songKey: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val touching = dragged || pressed
    val base = LocalSliderTrackBase.current ?: activeColor.copy(alpha = 0.24f)
    val colors = SliderDefaults.colors(
        thumbColor = activeColor,
        activeTrackColor = activeColor,
        inactiveTrackColor = base,
    )
    val safe = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val thumbSize by animateFloatAsState(
        when (sliderStyle) {
            SliderStyle.Slim, SliderStyle.Thick, SliderStyle.Dots, SliderStyle.AudioWave ->
                if (touching) 14f else 0f
            SliderStyle.Circular -> if (touching) 22f else 18f
            SliderStyle.ShootingStar -> if (touching) 14f else 10f
            else -> if (touching) 16f else 12f
        },
        label = "thumb",
    )
    val animate = isPlaying && !touching
    Slider(
        value = safe,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = interaction,
        colors = colors,
        thumb = { SliderThumb(sliderStyle, thumbSize, activeColor) },
        track = { state ->
            when (sliderStyle) {
                SliderStyle.Standard -> PlayerSliderTrack(state, colors, trackHeight = 4.dp)
                SliderStyle.Slim -> PlayerSliderTrack(state, colors, trackHeight = if (touching) 4.dp else 2.dp)
                SliderStyle.Thick -> PlayerSliderTrack(state, colors, trackHeight = 10.dp)
                SliderStyle.Circular -> PlayerSliderTrack(state, colors, trackHeight = 4.dp)
                SliderStyle.Wavy -> WavyTrack(state, activeColor, base, animate = animate, songKey = songKey)
                else -> FancyTrack(sliderStyle, state, activeColor, base, animate, songKey)
            }
        },
        modifier = modifier.heightIn(min = 24.dp),
    )
}

/** Tirador de cada estilo. */
@Composable
private fun SliderThumb(style: SliderStyle, size: Float, color: Color) {
    if (size <= 0.5f) return
    when (style) {
        SliderStyle.Circular -> Box(
            Modifier.size(size.dp).clip(CircleShape).background(Color.White).padding(3.dp)
                .clip(CircleShape).background(color),
        )
        SliderStyle.Capsule -> Box(
            Modifier.size(width = (size * 0.5f).dp, height = (size * 1.7f).dp)
                .clip(RoundedCornerShape(50)).background(Color.White),
        )
        SliderStyle.Neon -> Canvas(Modifier.size((size * 2).dp)) {
            val r = size.dp.toPx() / 2
            drawCircle(color.copy(alpha = 0.25f), r * 2f)
            drawCircle(color.copy(alpha = 0.45f), r * 1.4f)
            drawCircle(Color.White, r * 0.8f)
        }
        SliderStyle.ShootingStar -> Canvas(Modifier.size((size * 2.4f).dp)) {
            val r = size.dp.toPx() / 2
            val c = center
            drawCircle(color.copy(alpha = 0.3f), r * 2.2f)
            val star = Path().apply {
                for (i in 0 until 10) {
                    val ang = -PI / 2 + i * PI / 5
                    val rad = if (i % 2 == 0) r * 1.5f else r * 0.6f
                    val x = c.x + (rad * cos(ang)).toFloat()
                    val y = c.y + (rad * sin(ang)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, Color.White)
        }
        else -> Box(Modifier.size(size.dp).clip(CircleShape).background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WavyTrack(state: SliderState, active: Color, base: Color, animate: Boolean, songKey: String) {
    val transition = rememberInfiniteTransition(label = "wave:$songKey")
    val phase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "phase",
    )
    val amplitude by animateFloatAsState(if (animate) 1f else 0f, label = "amp")
    val fraction = state.fraction()
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val stroke = 3.dp.toPx()
        val end = size.width * fraction
        drawLine(base, Offset(end, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (end <= 0f) return@Canvas
        val amp = 3.dp.toPx() * amplitude
        val wavelength = 28.dp.toPx()
        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x <= end) {
                lineTo(x, y + amp * sin((x / wavelength) * 2 * PI.toFloat() - phase))
                x += 2f
            }
        }
        drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun SliderState.fraction(): Float {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    return ((value - valueRange.start) / span).coerceIn(0f, 1f)
}

/** Pistas dibujadas: neón, cápsula, línea doble, puntos, onda de audio, estrella fugaz y aurora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FancyTrack(style: SliderStyle, state: SliderState, active: Color, base: Color, animate: Boolean, songKey: String) {
    val transition = rememberInfiniteTransition(label = "track:$songKey")
    val clock by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "clock",
    )
    val motion by animateFloatAsState(if (animate) 1f else 0f, label = "motion")
    val t = clock
    val fraction = state.fraction()
    val height = when (style) {
        SliderStyle.Capsule -> 14.dp
        SliderStyle.AudioWave -> 22.dp
        SliderStyle.Neon, SliderStyle.ShootingStar -> 14.dp
        else -> 10.dp
    }
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val y = size.height / 2
        val end = w * fraction
        when (style) {
            SliderStyle.Neon -> {
                val s = 3.dp.toPx()
                drawLine(base, Offset(s, y), Offset(w - s, y), s, StrokeCap.Round)
                if (end > 0f) {
                    val glow = 0.6f + 0.4f * motion * sin(t * 2 * PI.toFloat())
                    drawLine(active.copy(alpha = 0.18f * glow), Offset(s, y), Offset(end, y), s * 4f, StrokeCap.Round)
                    drawLine(active.copy(alpha = 0.35f * glow), Offset(s, y), Offset(end, y), s * 2.2f, StrokeCap.Round)
                    drawLine(active, Offset(s, y), Offset(end, y), s, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(s, y), Offset(end, y), s * 0.35f, StrokeCap.Round)
                }
            }
            SliderStyle.Capsule -> {
                val s = size.height
                drawLine(base, Offset(s / 2, y), Offset(w - s / 2, y), s, StrokeCap.Round)
                if (end > 0f) drawLine(active, Offset(s / 2, y), Offset(end.coerceAtLeast(s / 2), y), s, StrokeCap.Round)
            }
            SliderStyle.DoubleLine -> {
                val s = 2.dp.toPx()
                val gap = 3.dp.toPx()
                for (dy in listOf(-gap, gap)) {
                    drawLine(base, Offset(s, y + dy), Offset(w - s, y + dy), s, StrokeCap.Round)
                    if (end > 0f) drawLine(active, Offset(s, y + dy), Offset(end, y + dy), s, StrokeCap.Round)
                }
            }
            SliderStyle.Dots -> {
                val step = 9.dp.toPx()
                val r = 2.2.dp.toPx()
                var x = r
                var i = 0
                while (x <= w - r) {
                    val played = x <= end
                    val pulse = if (played && motion > 0f) 1f + 0.25f * motion * sin((t * 2 * PI.toFloat()) - i * 0.5f) else 1f
                    drawCircle(if (played) active else base, r * pulse, Offset(x, y))
                    x += step; i++
                }
            }
            SliderStyle.AudioWave -> {
                val bar = 3.dp.toPx()
                val step = 5.dp.toPx()
                var x = bar / 2
                var i = 0
                while (x <= w - bar / 2) {
                    val seed = ((sin(i * 12.9898f) * 43758.547f) % 1f + 1f) % 1f
                    val wave = 0.5f + 0.5f * sin(t * 2 * PI.toFloat() * 2 + i * 0.7f)
                    val hFrac = (0.3f + 0.7f * seed) * (1f - 0.45f * motion + 0.45f * motion * wave)
                    val h = (size.height * hFrac).coerceAtLeast(bar)
                    drawLine(
                        if (x <= end) active else base,
                        Offset(x, y - h / 2), Offset(x, y + h / 2), bar, StrokeCap.Round,
                    )
                    x += step; i++
                }
            }
            SliderStyle.ShootingStar -> {
                val s = 2.dp.toPx()
                drawLine(base, Offset(s, y), Offset(w - s, y), s, StrokeCap.Round)
                if (end > 0f) {
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, active.copy(alpha = 0.6f), Color.White), startX = 0f, endX = end),
                        Offset(s, y), Offset(end, y), s * 1.6f, StrokeCap.Round,
                    )
                    // Chispas que se quedan atrás de la estrella.
                    for (k in 1..5) {
                        val off = ((t + k * 0.19f) % 1f)
                        val sx = end - off * 60.dp.toPx()
                        if (sx > 0f) {
                            val sy = y + (if (k % 2 == 0) 1 else -1) * off * 4.dp.toPx()
                            drawCircle(Color.White.copy(alpha = (1f - off) * 0.8f * (0.3f + 0.7f * motion)), 1.4.dp.toPx() * (1f - off * 0.5f), Offset(sx, sy))
                        }
                    }
                }
            }
            SliderStyle.Aurora -> {
                val s = 6.dp.toPx()
                drawLine(base, Offset(s / 2, y), Offset(w - s / 2, y), s, StrokeCap.Round)
                if (end > 0f) {
                    val shift = t * w * motion
                    val aurora = Brush.linearGradient(
                        listOf(Color(0xFF00E5A0), Color(0xFF00B8FF), Color(0xFF8A4DFF), Color(0xFFFF4FD8), Color(0xFF00E5A0)),
                        start = Offset(-shift, 0f), end = Offset(w - shift, 0f),
                        tileMode = TileMode.Repeated,
                    )
                    drawLine(aurora, Offset(s / 2, y), Offset(end.coerceAtLeast(s / 2), y), s * 1.8f, StrokeCap.Round, alpha = 0.25f)
                    drawLine(aurora, Offset(s / 2, y), Offset(end.coerceAtLeast(s / 2), y), s, StrokeCap.Round)
                }
            }
            else -> {
                val s = 4.dp.toPx()
                drawLine(base, Offset(s / 2, y), Offset(w - s / 2, y), s, StrokeCap.Round)
                if (end > 0f) drawLine(active, Offset(s / 2, y), Offset(end, y), s, StrokeCap.Round)
            }
        }
    }
}

// ── Canvas de la canción (llega en la fase 7) ────────────────────────────────────────────────

/** Vídeo corto en bucle sobre la carátula (Spotify/Apple). */
class CanvasArtwork(
    val name: String? = null,
    val artist: String? = null,
    val animated: String? = null,
    val animatedVertical: String? = null,
    val videoUrl: String? = null,
    val videoUrlVertical: String? = null,
    val source: String? = null,
    /** Si el vídeo es un trozo de un videoclip: desde dónde se repite (ms). */
    val loopStartMs: Long = 0L,
    /** Duración del trozo que se repite (ms); 0 = el vídeo entero. */
    val loopLengthMs: Long = 0L,
) {
    /** (principal, respaldo) para el reproductor de canvas. */
    fun playbackUrls(): Pair<String?, String?> =
        (animatedVertical ?: videoUrlVertical) to (animated ?: videoUrl)

    companion object {
        const val SOURCE_SPOTIFY = "spotify"
        const val SOURCE_APPLE = "apple"
    }
}

/**
 * Canvas de la canción que suena: trozo de su videoclip en bucle. Respeta el ajuste «Canvas de la
 * canción» (apagado = null) y devuelve null mientras se busca o si la canción no tiene videoclip.
 */
@Composable
fun rememberPlaybackCanvas(
    mediaId: String?,
    title: String?,
    artist: String?,
    albumTitle: String?,
    durationMs: Long,
): CanvasArtwork? {
    val resolver: CanvasResolver = koinInject()
    val enabled by rememberPreference(CanvasSongEnabledKey, CANVAS_SONG_DEFAULT)
    var canvas by remember(mediaId, enabled) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(mediaId, title, enabled) {
        canvas = null
        if (!enabled || (mediaId.isNullOrBlank() && title.isNullOrBlank())) return@LaunchedEffect
        canvas = resolver.resolve(
            mediaId = mediaId,
            title = title,
            artist = artist,
            durationSec = durationMs.takeIf { it > 0 }?.let { (it / 1000).toInt() },
        )
    }
    return canvas
}

/** Reproduce el canvas en bucle, sin sonido; no pinta nada si no hay URL. */
@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val url = primaryUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl?.takeIf { it.isNotBlank() } ?: return
    val resolver: CanvasResolver = koinInject()
    // Sin trozo conocido se reproduce entero; no se pinta hasta saberlo para no reiniciar el vídeo.
    val loop by produceState<CanvasLoop?>(null, url) { value = resolver.loopFor(url) ?: CanvasLoop(0L, 0L) }
    val ready = loop ?: return
    CanvasVideo(
        url = url,
        loopStartMs = ready.startMs,
        loopLengthMs = ready.lengthMs,
        playing = isPlaying,
        modifier = modifier,
    )
}

// ── Letras (llegan en la fase 7) ─────────────────────────────────────────────────────────────

data class LyricsEntry(val time: Long, val text: String)

/** Lee letras LRC («[mm:ss.xx] texto»); sin marcas de tiempo devuelve cada línea con tiempo 0. */
fun parseLyrics(text: String): List<LyricsEntry> {
    val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    val out = mutableListOf<LyricsEntry>()
    text.lineSequence().forEach { raw ->
        val stamps = stamp.findAll(raw).toList()
        if (stamps.isEmpty()) {
            if (raw.isNotBlank() && !raw.trimStart().startsWith("[")) out += LyricsEntry(0, raw.trim())
            return@forEach
        }
        val body = raw.substring(stamps.last().range.last + 1).trim()
        stamps.forEach { m ->
            val (mm, ss, frac) = m.destructured
            val ms = frac.takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
            out += LyricsEntry(mm.toLong() * 60_000 + ss.toLong() * 1000 + ms, body)
        }
    }
    return out.sortedBy { it.time }
}

/** Panel de letras sincronizadas; hasta la fase 7 muestra un aviso. */
@Composable
fun Lyrics(
    lyricsSyncOffset: Int = 0,
    sliderPositionProvider: () -> Long? = { null },
    modifier: Modifier = Modifier,
    textSizeOverride: Float? = null,
) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            "Las letras llegan en la fase 7",
            color = LocalContentColor.current.copy(alpha = 0.7f),
            fontSize = (textSizeOverride ?: 16f).sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Créditos (llegan con SongDNA) ────────────────────────────────────────────────────────────

data class SongCredits(val writers: List<String>, val producers: List<String>, val source: String)

object SongCreditsResolver {
    /** Compositores y productores reales (MusicBrainz); null hasta la fase de SongDNA. */
    suspend fun resolve(mediaId: String, title: String, artist: String): SongCredits? = null
}

// ── Dispositivos (Dezzy Connect) ─────────────────────────────────────────────────────────────

enum class ConnectState { DISCONNECTED, CONNECTING, CONNECTED }

/** Estado de Dezzy Connect para los reproductores: conectado si hay algún Dezzy de tu grupo enlazado. */
class DesktopLink(service: ConnectService, scope: CoroutineScope) {
    val connectionState: StateFlow<ConnectState> = service.state
        .map { if (it.peers.isNotEmpty()) ConnectState.CONNECTED else ConnectState.DISCONNECTED }
        .stateIn(scope, SharingStarted.Eagerly, ConnectState.DISCONNECTED)
    val connectedDeviceName: StateFlow<String?> = service.state
        .map { it.peers.firstOrNull()?.name }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

@Composable
fun rememberDesktopLink(): DesktopLink {
    val service: ConnectService = koinInject()
    val scope = rememberCoroutineScope()
    return remember(service) { DesktopLink(service, scope) }
}

/** Nombre del Chromecast al que se transmite, o null (Chromecast llega más tarde). */
@Composable
fun rememberCastingRouteName(): String? = null

@Composable
fun rememberIsCasting(): Boolean = rememberCastingRouteName() != null

/**
 * Botón único de dispositivos de los reproductores: abre «Conectar a un dispositivo». Mientras se
 * transmite el icono cambia a «transmitiendo» y se pinta con [activeTint].
 */
@Composable
fun DevicesButton(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    activeTint: Color = tint,
    iconSize: Dp = 24.dp,
    onOpenListenTogether: (() -> Unit)? = null,
    /** Icono en reposo; el estilo Mínimo pasa su versión de trazo fino. */
    idleIcon: ImageVector = Icons.Rounded.Devices,
    /** Sin círculo de cristal (estilo Apple Music, como en la app actual). */
    plain: Boolean = false,
) {
    val casting = rememberIsCasting()
    var showSheet by remember { mutableStateOf(false) }
    PlayerIconButton(plain = plain, onClick = { showSheet = true }, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (casting) Icons.Rounded.CastConnected else idleIcon,
                contentDescription = "Conectar a un dispositivo",
                tint = if (casting) activeTint else tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
    if (showSheet) {
        ConnectDeviceBottomSheet(
            onDismissRequest = { showSheet = false },
            onOpenListenTogether = onOpenListenTogether?.let { open -> { showSheet = false; open() } },
        )
    }
}

// La hoja «Conectar a un dispositivo» (ConnectDeviceBottomSheet) vive en ConnectSheet.kt.

// ── Compartir ────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun ShareBottomSheet(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    shareLink: String,
    lyrics: String? = null,
    mediaId: String? = null,
    onDismiss: () -> Unit,
) {
    val share = rememberShareText()
    val clipboard = LocalClipboardManager.current
    val connection = LocalPlayerConnection.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(thumbnailUrl, Modifier.size(64.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            SheetRow(Icons.Rounded.Share, "Compartir", null, onClick = {
                share(listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(" — ") + "\n" + shareLink)
                onDismiss()
            })
            SheetRow(Icons.Rounded.ContentCopy, "Copiar enlace", null, onClick = {
                clipboard.setText(AnnotatedString(shareLink))
                connection?.toast?.invoke("Enlace copiado")
                onDismiss()
            })
        }
    }
}

// ── Añadir a una lista ───────────────────────────────────────────────────────────────────────

/** Elige (o crea) una lista local y le añade [track]; [onAddComplete] recibe los nombres. */
@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    track: Track,
    onDismiss: () -> Unit,
    onAddComplete: (playlistNames: List<String>) -> Unit = {},
) {
    if (!isVisible) return
    val repository: MusicRepository = koinInject()
    val scope = rememberCoroutineScope()
    val playlists by remember { repository.getPlaylists() }.collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a una lista") },
        text = {
            if (creating) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre de la lista") }, singleLine = true)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { SheetRow(Icons.Rounded.Add, "Nueva lista", null, onClick = { creating = true }) }
                    items(playlists, key = { it.id }) { p ->
                        SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, p.title, "${p.trackCount} canciones", onClick = {
                            scope.launch {
                                repository.saveTrack(track)
                                repository.addTrackToPlaylist(p.id, track)
                                onAddComplete(listOf(p.title))
                                onDismiss()
                            }
                        })
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        val p = repository.createPlaylist(name.trim())
                        repository.saveTrack(track)
                        repository.addTrackToPlaylist(p.id, track)
                        onAddComplete(listOf(p.title))
                        onDismiss()
                    }
                }) { Text("Crear") }
            }
        },
        dismissButton = { TextButton(onClick = { if (creating) creating = false else onDismiss() }) { Text("Cancelar") } },
    )
}

// ── Menús en hoja ────────────────────────────────────────────────────────────────────────────

/** Hoja de menú que abren los estilos con `show { … }` (menú del reproductor, detalles). */
class MenuState {
    var content by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null)
        private set
    fun show(content: @Composable ColumnScope.() -> Unit) { this.content = content }
    fun dismiss() { content = null }
}

val LocalMenuState = staticCompositionLocalOf { MenuState() }
val LocalBottomSheetPageState = staticCompositionLocalOf { MenuState() }

/** Abre el selector de estilo del reproductor (lo fija el enrutador). */
val LocalOpenPlayerStyleSelector = staticCompositionLocalOf<() -> Unit> { {} }

/** Abre el temporizador de apagado (lo fija el enrutador). */
val LocalOpenSleepTimer = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuHost(state: MenuState) {
    val content = state.content ?: return
    ModalBottomSheet(onDismissRequest = state::dismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

/** Menú «⋯» del reproductor. */
@Composable
fun ColumnScope.PlayerMenu(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val connection = LocalPlayerConnection.current ?: return
    val openSleep = LocalOpenSleepTimer.current
    val openVisualizer = LocalOpenVisualizer.current
    val openAod = LocalOpenAod.current
    val playback by connection.controller.state.collectAsState()
    val fx: com.dezzy.app.feature.audio.AudioEffects = koinInject()
    val fxState by fx.state.collectAsState()
    val downloads = koinInject<DownloadManager>()
    val dlStatus by downloads.status(mediaMetadata.id).collectAsState(null)
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var addToPlaylist by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var modDialog by remember { mutableStateOf(false) }
    var voiceDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val artistNames = mediaMetadata.artists.joinToString { it.name }
    val albumTitle = mediaMetadata.album?.title
    Column(Modifier.verticalScroll(rememberScrollState())) {
        // Cabecera
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(mediaMetadata.thumbnailUrl, Modifier.size(48.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mediaMetadata.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(artistNames.ifBlank { null }, albumTitle).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Chips
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuChip(Icons.Rounded.Shortcut, "Compartir") { share = true }
            MenuChip(Icons.Rounded.Mic, "Voz / Pista") { voiceDialog = true }
            MenuChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir") { addToPlaylist = true }
            if (dlStatus != null) {
                MenuChip(Icons.Rounded.Download, "Quitar descarga") {
                    downloads.remove(mediaMetadata.id)
                    connection.toast("Descarga quitada")
                    onDismiss()
                }
            } else {
                MenuChip(Icons.Rounded.Download, "Descargar") {
                    downloads.enqueue(listOf(mediaMetadata.track))
                    connection.toast("“${mediaMetadata.title}” a descargas")
                    onDismiss()
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            MenuGroupHeader("Ver y escuchar")
            MenuRow(Icons.Rounded.SlowMotionVideo, "Ver video") { connection.enableVideoMode(); onDismiss() }
            MenuRow(Icons.Rounded.AutoAwesome, "Visualizador", "El estilo se elige en Ajustes › Apariencia") {
                onDismiss(); openVisualizer()
            }
            MenuRow(Icons.Rounded.GraphicEq, "DJ Dezzy", "Pídele música escribiendo o con tu voz") {
                onDismiss(); navController.navigate("dj_dezzy")
            }
            MenuRow(Icons.Rounded.Sensors, "Empezar radio") { connection.playQueue(mediaMetadata.track); onDismiss() }

            MenuGroupHeader("Audio")
            MenuRow(Icons.Rounded.Mic, "Voz / Instrumental", vocalMixSubtitle(fxState.vocalMix)) { voiceDialog = true }
            MenuRow(Icons.Rounded.Equalizer, "Ecualizador") { onDismiss(); navController.navigate("settings/equalizer") }
            MenuRow(Icons.Outlined.OutlinedSpeed, "Velocidad y tono", "x${formatFactor(playback.speed)} • x${formatFactor(playback.pitch)}") {
                speedDialog = true
            }
            MenuRow(Icons.Outlined.DarkMode, "Temporizador") { onDismiss(); openSleep() }

            MenuGroupHeader("Ir a")
            mediaMetadata.artists.firstOrNull()?.let { artist ->
                MenuRow(Icons.Outlined.InterpreterMode, "Ver artista") { onDismiss(); navController.openArtist(artist) }
            }
            if (albumTitle != null) {
                MenuRow(Icons.Outlined.OutlinedAlbum, "Ver álbum") {
                    val albumId = mediaMetadata.album?.id
                    if (albumId != null) {
                        onDismiss(); navController.navigate("album/$albumId")
                    } else {
                        // Se busca «álbum artista» y se abre el primero; si no hay, abre Buscar con esa consulta.
                        val query = listOf(albumTitle, mediaMetadata.artists.firstOrNull()?.name).filterNotNull().joinToString(" ")
                        scope.launch {
                            val found = connection.client.search(query).getOrNull()?.albums?.firstOrNull()
                            navController.navigate(if (found != null) "album/${found.id}" else "search/$query")
                            onDismiss()
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            MenuRow(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                if (expanded) "Mostrar menos" else "Mostrar todo",
                "Marcación rápida, escuchar juntos, ajustes, detalles…",
            ) { expanded = !expanded }
            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text("Volumen", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${(playback.volume * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(value = playback.volume, onValueChange = { connection.controller.setVolume(it) })
                }
                MenuRow(Icons.Rounded.PushPin, "Pin to speed dial") {
                    // Aún no hay almacén de canciones fijadas en la «Marcación rápida» del Inicio.
                    connection.toast("Fijar en la marcación rápida llegará más adelante")
                    onDismiss()
                }
                MenuRow(Icons.Rounded.ContentCopy, "Copiar enlace") {
                    clipboard.setText(AnnotatedString("https://music.youtube.com/watch?v=${mediaMetadata.id}"))
                    connection.toast("Enlace copiado")
                    onDismiss()
                }
                MenuRow(Icons.Rounded.Groups, "Listen Together", "Escuchar juntos") {
                    onDismiss(); navController.navigate("settings/music_together")
                }
                MenuRow(Icons.Rounded.ScreenLockPortrait, "Pantalla siempre activa") {
                    onDismiss(); openAod()
                }
                MenuRow(Icons.Rounded.Info, "Detalles", onClick = onShowDetailsDialog)
                MenuRow(Icons.Rounded.Settings, "Ajustes del reproductor", "Reproductor y audio") {
                    onDismiss(); navController.navigate("settings/player")
                }
                MenuRow(Icons.Rounded.Palette, "Apariencia y visualizador") {
                    onDismiss(); navController.navigate("settings/appearance")
                }
                MenuRow(Icons.Rounded.Edit, "Personalizar canción") { modDialog = true }
            }
        }
    }
    if (voiceDialog) VocalMixDialog(onDismiss = { voiceDialog = false })
    if (speedDialog) SpeedPitchDialog(onDismiss = { speedDialog = false })
    if (modDialog) {
        // El diálogo trabaja con la canción ORIGINAL (la de la cola), no con la ya modificada.
        val original = connection.controller.state.value.queue.current?.track ?: mediaMetadata.track
        com.dezzy.app.ui.screens.personalize.SongModDialog(original, onDismiss = { modDialog = false; onDismiss() })
    }
    AddToPlaylistDialog(
        isVisible = addToPlaylist,
        track = mediaMetadata.track,
        onDismiss = { addToPlaylist = false },
        onAddComplete = { names -> connection.toast("Añadida a ${names.firstOrNull().orEmpty()}") },
    )
    if (share) {
        ShareBottomSheet(
            title = mediaMetadata.title,
            subtitle = mediaMetadata.artists.joinToString { it.name },
            thumbnailUrl = mediaMetadata.thumbnailUrl,
            shareLink = "https://music.youtube.com/watch?v=${mediaMetadata.id}",
            mediaId = mediaMetadata.id,
            onDismiss = { share = false; onDismiss() },
        )
    }
}

/** Detalles de la canción que suena. */
@Composable
fun ColumnScope.ShowMediaInfo(mediaId: String) {
    val connection = LocalPlayerConnection.current ?: return
    val meta by connection.mediaMetadata.collectAsState()
    val m = meta?.takeIf { it.id == mediaId } ?: return
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Detalles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        InfoLine("Título", m.title)
        InfoLine("Artistas", m.artists.joinToString { it.name })
        m.album?.let { InfoLine("Álbum", it.title) }
        InfoLine("ID", m.id)
        if (m.duration > 0) InfoLine("Duración", makeTimeString(m.duration * 1000L))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ── Temporizador ─────────────────────────────────────────────────────────────────────────────

/** Temporizador de apagado: minutos fijos o al terminar la canción. */
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val state by connection.controller.state.collectAsState()
    val active = state.sleepAtMillis != null || state.sleepAtEndOfSong
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador") },
        text = {
            Column {
                listOf(5, 10, 15, 30, 45, 60, 90).forEach { min ->
                    SheetRow(null, "$min minutos", null, onClick = { connection.controller.setSleepTimer(min); onDismiss() })
                }
                SheetRow(null, "Al terminar la canción", null, onClick = { connection.controller.setSleepAtEndOfSong(true); onDismiss() })
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = {
                    connection.controller.setSleepTimer(null)
                    connection.controller.setSleepAtEndOfSong(false)
                    onDismiss()
                }) { Text("Apagar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ── Fila común de las hojas ──────────────────────────────────────────────────────────────────

@Composable
private fun SheetRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) Icon(trailing, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.offset(x = (-4).dp))
    }
}

/** Botón de icono del reproductor: plano con [plain] o con el cristal del tema en otro caso. */
@Composable
fun PlayerIconButton(plain: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (plain) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, content = content)
    } else {
        DezzyIconButton(onClick = onClick, modifier = modifier, content = content)
    }
}
