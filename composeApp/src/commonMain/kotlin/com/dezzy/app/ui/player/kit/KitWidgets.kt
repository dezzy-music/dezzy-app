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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Slider de reproducción con el estilo global (Ajustes › Reproductor): estándar, fino u ondulado.
 * La onda sólo se mueve mientras suena; [songKey] reinicia la animación al cambiar de canción.
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
            SliderStyle.Slim -> if (touching) 12f else 0f
            else -> if (touching) 16f else 12f
        },
        label = "thumb",
    )
    Slider(
        value = safe,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = interaction,
        colors = colors,
        thumb = {
            Box(Modifier.size(thumbSize.dp).clip(CircleShape).background(activeColor))
        },
        track = { state ->
            when (sliderStyle) {
                SliderStyle.Standard -> PlayerSliderTrack(state, colors, trackHeight = 4.dp)
                SliderStyle.Slim -> PlayerSliderTrack(state, colors, trackHeight = if (touching) 4.dp else 2.dp)
                SliderStyle.Wavy -> WavyTrack(state, activeColor, base, animate = isPlaying && !touching, songKey = songKey)
            }
        },
        modifier = modifier.heightIn(min = 24.dp),
    )
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
    val range = state.valueRange
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((state.value - range.start) / span).coerceIn(0f, 1f)
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

// ── Dispositivos (Dezzy Connect llega en la fase 9) ──────────────────────────────────────────

enum class ConnectState { DISCONNECTED, CONNECTING, CONNECTED }

/** Enlace con Dezzy Desktop; desconectado hasta la fase 9. */
class DesktopLink {
    val connectionState: StateFlow<ConnectState> = MutableStateFlow(ConnectState.DISCONNECTED).asStateFlow()
    val connectedDeviceName: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
}

@Composable
fun rememberDesktopLink(): DesktopLink = remember { DesktopLink() }

/** Nombre del Chromecast al que se transmite, o null (Cast llega en la fase 9). */
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
) {
    val casting = rememberIsCasting()
    var showSheet by remember { mutableStateOf(false) }
    DezzyIconButton(onClick = { showSheet = true }, modifier = modifier) {
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

/** Hoja «Conectar a un dispositivo». Por ahora sólo este teléfono y Escuchar juntos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectDeviceBottomSheet(
    onDismissRequest: () -> Unit,
    onOpenListenTogether: (() -> Unit)? = null,
) {
    val phone = rememberPhoneDeviceName()
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Conectar a un dispositivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            SheetRow(Icons.Rounded.Smartphone, phone, "Reproduciendo aquí", trailing = Icons.Rounded.Check)
            Text(
                "Tus otros Dezzy, Chromecast y Bluetooth llegan con Dezzy Connect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (onOpenListenTogether != null) {
                HorizontalDivider()
                SheetRow(Icons.Rounded.Groups, "Escuchar juntos", "Escucha en sincronía con tus amigos", onClick = onOpenListenTogether)
            }
        }
    }
}

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
    val openStyles = LocalOpenPlayerStyleSelector.current
    val openSleep = LocalOpenSleepTimer.current
    var addToPlaylist by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(mediaMetadata.thumbnailUrl, Modifier.size(48.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(mediaMetadata.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Column(Modifier.padding(horizontal = 20.dp)) {
        SheetRow(Icons.Rounded.Radio, "Iniciar radio", null, onClick = { connection.playQueue(mediaMetadata.track); onDismiss() })
        SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a una lista", null, onClick = { addToPlaylist = true })
        val downloads = koinInject<DownloadManager>()
        val dlStatus by downloads.status(mediaMetadata.id).collectAsState(null)
        if (dlStatus != null) {
            SheetRow(Icons.Rounded.Download, "Quitar descarga", null, onClick = {
                downloads.remove(mediaMetadata.id)
                connection.toast("Descarga quitada")
                onDismiss()
            })
        } else {
            SheetRow(Icons.Rounded.Download, "Descargar", null, onClick = {
                downloads.enqueue(listOf(mediaMetadata.track))
                connection.toast("“${mediaMetadata.title}” a descargas")
                onDismiss()
            })
        }
        mediaMetadata.artists.firstOrNull()?.let { artist ->
            SheetRow(Icons.Rounded.Person, "Ir al artista", artist.name, onClick = { onDismiss(); navController.openArtist(artist) })
        }
        SheetRow(Icons.Rounded.Share, "Compartir", null, onClick = { share = true })
        SheetRow(Icons.Rounded.Bedtime, "Temporizador", null, onClick = { onDismiss(); openSleep() })
        SheetRow(Icons.Rounded.Palette, "Estilo del reproductor", null, onClick = { onDismiss(); openStyles() })
        SheetRow(Icons.Rounded.Equalizer, "Ecualizador", null, onClick = { onDismiss(); connection.pending("Ecualizador", "4") })
        SheetRow(Icons.Rounded.Info, "Detalles", null, onClick = onShowDetailsDialog)
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
