/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.canvas.CanvasResolver
import com.dezzy.app.source.spotify.SpotifyFeatures
import com.dezzy.app.ui.player.canvas.CanvasExportRequest
import com.dezzy.app.ui.player.canvas.CanvasVideo
import com.dezzy.app.ui.player.canvas.ShareTarget
import com.dezzy.app.ui.player.canvas.canvasExportSupported
import com.dezzy.app.ui.player.canvas.encodePng
import com.dezzy.app.ui.player.canvas.exportCanvasVideo
import com.dezzy.app.ui.player.canvas.saveImage
import com.dezzy.app.ui.player.canvas.sharePngTo
import com.dezzy.app.ui.player.canvas.shareVideoTo
import com.dezzy.app.ui.player.kit.CanvasArtwork
import com.dezzy.app.ui.player.kit.loadArtPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/** Qué se comparte: cambia las pestañas, la tarjeta y los botones de la hoja. */
enum class ShareKind { Song, Album, Playlist, Artist }

/** Fondo elegido para la tarjeta: estilo Polaroid, o un color de la carátula sólido o en degradado. */
private sealed interface CardStyle {
    data object Polaroid : CardStyle
    data class Fill(val color: Color, val gradient: Boolean) : CardStyle
}

private enum class Tab { Main, Alt }

private val SheetBg = Color(0xFF1E1E1E)
private val ChipBg = Color(0xFF2C2C2C)
private val InnerCard = Color(0xFF0C0C0C)
private val Muted = Color(0xFFA9A9A9)
private val Paper = Color(0xFFF4EFE4)
private val PaperLine = Color(0xFFDCD6C9)
private val PaperMargin = Color(0xFFE8A0A0)
private val InvalidFileChars = Regex("[\\\\/:*?\"<>|]")
private val Durations = listOf(15, 30, 60)

/**
 * Hoja de Compartir con tarjeta: canción (pestañas Canción y Canvas, estilo Polaroid), álbum o lista
 * (en forma de lista o sólo la portada) y artista (foto redonda). La tarjeta usa los colores de la
 * carátula y se guarda o comparte como imagen; el Canvas se comparte como vídeo con el trozo de la
 * canción que se elija.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DezzyShareSheet(
    kind: ShareKind,
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    link: String,
    mediaId: String? = null,
    durationSec: Int? = null,
    tracks: List<Track> = emptyList(),
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(Tab.Main) }

    // Colores de la carátula, cada uno en sólido y en degradado color→negro.
    var colors by remember(thumbnailUrl) { mutableStateOf(listOf<Color>()) }
    LaunchedEffect(thumbnailUrl) {
        val p = loadArtPalette(thumbnailUrl, 12)
        colors = buildList {
            listOfNotNull(p?.darkVibrantSwatch, p?.dominantSwatch, p?.vibrantSwatch, p?.darkMutedSwatch, p?.mutedSwatch, p?.lightVibrantSwatch)
                .forEach { add(Color(it.rgb or 0xFF000000.toInt())) }
            add(Color(0xFF2B2B2B)); add(Color(0xFF121212))
        }.distinct()
    }
    val fills = remember(colors) { colors.flatMap { listOf(CardStyle.Fill(it, false), CardStyle.Fill(it, true)) } }
    var picked by remember(thumbnailUrl) { mutableStateOf<CardStyle?>(null) }
    val polaroidAllowed = kind == ShareKind.Song && tab == Tab.Main
    val styles: List<CardStyle> = if (polaroidAllowed) listOf(CardStyle.Polaroid) + fills else fills
    val style = picked?.takeIf { it in styles } ?: fills.firstOrNull() ?: CardStyle.Fill(Color(0xFF2B2B2B), false)
    val fill = style as? CardStyle.Fill ?: fills.firstOrNull() ?: CardStyle.Fill(Color(0xFF2B2B2B), false)

    // Canvas (sólo canciones): se busca al abrir la pestaña.
    val canvasTab = kind == ShareKind.Song && canvasExportSupported
    var canvas by remember(mediaId) { mutableStateOf<CanvasArtwork?>(null) }
    var canvasLoaded by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId, tab) {
        if (canvasTab && tab == Tab.Alt && !canvasLoaded) {
            canvas = runCatching {
                KoinPlatform.getKoin().getOrNull<CanvasResolver>()
                    ?.resolve(mediaId, title, subtitle, durationSec)
            }.getOrNull()
            canvasLoaded = true
        }
    }
    var durationS by remember { mutableStateOf(30) }
    var audioStartMs by remember { mutableLongStateOf(0L) }
    var branded by remember { mutableStateOf(true) }

    val baseName = remember(title) { title.replace(InvalidFileChars, "").ifBlank { "Dezzy" } }
    val shareText = remember(title, subtitle, link) {
        listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(" · ") + "\n" + link
    }
    val isCanvas = canvasTab && tab == Tab.Alt

    /** Captura la tarjeta tal como se ve y se la pasa a [action] en PNG. */
    fun capture(action: suspend (ByteArray) -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap()
                val png = withContext(Dispatchers.Default) { encodePng(bitmap) }
                if (png.isEmpty()) message = "No se pudo crear la imagen" else action(png)
            } catch (e: Exception) {
                message = "No se pudo crear la imagen"
            } finally {
                busy = false
            }
        }
    }

    /** Crea el vídeo del Canvas (se guarda en la galería) y se lo pasa a [action]. */
    fun exportVideo(action: (String) -> Unit) {
        val art = canvas ?: return
        val url = art.playbackUrls().let { it.first ?: it.second } ?: return
        if (busy) return
        busy = true
        message = "Creando vídeo…"
        scope.launch {
            val uri = try {
                exportCanvasVideo(
                    CanvasExportRequest(
                        videoUrl = url,
                        loopStartMs = art.loopStartMs,
                        loopLengthMs = art.loopLengthMs,
                        videoId = mediaId,
                        audioStartMs = audioStartMs,
                        durationMs = durationS * 1000L,
                        branded = branded,
                        frameColor = fill.color.toArgb(),
                        gradient = fill.gradient,
                        title = title,
                        artist = subtitle,
                        fileName = "$baseName - Canvas.mp4",
                    ),
                )
            } catch (e: Exception) {
                null
            }
            busy = false
            if (uri == null) message = "No se pudo crear el vídeo" else { message = null; action(uri) }
        }
    }

    fun copy(text: String, what: String) {
        clipboard.setText(AnnotatedString(text))
        message = "$what copiado"
    }

    fun copySpotify() {
        if (busy) return
        busy = true
        scope.launch {
            val query = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(" ")
            val found = runCatching {
                KoinPlatform.getKoin().getOrNull<SpotifyFeatures>()?.searchTracks(query, 1)?.firstOrNull()
            }.getOrNull()
            busy = false
            copy(
                found?.let { "https://open.spotify.com/track/${it.id}" } ?: "https://open.spotify.com/search/${urlEncode(query)}",
                "Enlace de Spotify",
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Tarjeta ──
            Box(Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                if (isCanvas) {
                    CanvasCard(canvas, canvasLoaded, branded, fill, title, subtitle)
                } else {
                    CapturedCard(layer) {
                        when {
                            style is CardStyle.Polaroid -> PolaroidCard(thumbnailUrl, title, subtitle)
                            kind == ShareKind.Song -> SongCard(fill, thumbnailUrl, title, subtitle)
                            kind == ShareKind.Artist -> CoverCard(fill, thumbnailUrl, title, "Artista en Dezzy", round = true)
                            tab == Tab.Alt -> CoverCard(fill, thumbnailUrl, title, collectionLabel(kind), round = false)
                            else -> ListCard(fill, thumbnailUrl, title, collectionLabel(kind), tracks)
                        }
                    }
                }
            }

            // ── Pestañas ──
            val tabs = when (kind) {
                ShareKind.Song -> if (canvasTab) listOf("Canción", "Canvas") else emptyList()
                ShareKind.Album -> listOf("Álbum", "Portada del álbum")
                ShareKind.Playlist -> listOf("Playlist", "Portada de la playlist")
                ShareKind.Artist -> emptyList()
            }
            if (tabs.isNotEmpty()) {
                Segmented(tabs, if (tab == Tab.Main) 0 else 1) { tab = if (it == 0) Tab.Main else Tab.Alt; message = null }
            }

            // ── Opciones del Canvas ──
            if (isCanvas && canvas != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Duración:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Durations.forEach { s ->
                            Chip(if (s == 30) "${s}s (Stories)" else "${s}s", s == durationS) { durationS = s }
                        }
                    }
                    val songMs = ((durationSec ?: 0).takeIf { it > 0 } ?: 240) * 1000L
                    val maxStart = (songMs - durationS * 1000L).coerceAtLeast(0L)
                    if (audioStartMs > maxStart) audioStartMs = maxStart
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Segmento de audio: ${clock(audioStartMs)} – ${clock(audioStartMs + durationS * 1000L)}",
                        color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                    Slider(
                        value = if (maxStart == 0L) 0f else audioStartMs.toFloat() / maxStart,
                        onValueChange = { audioStartMs = (it * maxStart).toLong() / 1000L * 1000L },
                        enabled = maxStart > 0L,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = ChipBg),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Con marca completa", branded, Modifier.weight(1f)) { branded = true }
                        Pill("Puro Canvas (sin marco)", !branded, Modifier.weight(1f)) { branded = false }
                    }
                }
            }

            // ── Colores ──
            if (!isCanvas || (canvas != null && branded)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    (if (isCanvas) fills else styles).forEach { s ->
                        val selected = s == style || (isCanvas && s == fill)
                        val bg = when (s) {
                            CardStyle.Polaroid -> Modifier.background(Paper)
                            is CardStyle.Fill -> if (s.gradient) Modifier.background(Brush.verticalGradient(listOf(s.color, Color.Black))) else Modifier.background(s.color)
                        }
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).then(bg)
                                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable { picked = s },
                        )
                    }
                }
                Text(
                    if (style is CardStyle.Polaroid && !isCanvas) "Estilo Polaroid" else "Toca un color para personalizar la tarjeta",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                )
            }

            // ── Botones ──
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val canAct = !busy && (!isCanvas || canvas != null)
                ActionButton(Icons.Rounded.Download, "Guardar", canAct) {
                    if (isCanvas) exportVideo { message = "Vídeo guardado en la galería" }
                    else capture { png -> message = if (saveImage(png, "$baseName.png")) "Imagen guardada en la galería" else "No se pudo guardar la imagen" }
                }
                ActionButton(Icons.Rounded.Link, "Copiar YT", !busy) { copy(link, "Enlace") }
                if (kind == ShareKind.Song) {
                    ActionButton(Icons.Rounded.Link, "Spotify", !busy) { copySpotify() }
                }
                ActionButton(Icons.Rounded.Camera, "Historias", canAct) {
                    if (isCanvas) exportVideo { shareVideoTo(it, shareText, ShareTarget.Stories) }
                    else capture { png -> sharePngTo(png, "$baseName.png", shareText, ShareTarget.Stories) }
                }
                if (kind != ShareKind.Song) {
                    ActionButton(Icons.AutoMirrored.Rounded.Send, "WhatsApp", canAct) {
                        capture { png -> sharePngTo(png, "$baseName.png", shareText, ShareTarget.WhatsApp) }
                    }
                }
                ActionButton(Icons.Rounded.MoreHoriz, "Más", canAct) {
                    if (isCanvas) exportVideo { shareVideoTo(it, shareText, ShareTarget.Chooser) }
                    else capture { png -> sharePngTo(png, "$baseName.png", shareText, ShareTarget.Chooser) }
                }
            }
            Text(
                message ?: " ",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun collectionLabel(kind: ShareKind) = when (kind) {
    ShareKind.Album -> "Álbum en Dezzy"
    ShareKind.Playlist -> "Playlist en Dezzy"
    ShareKind.Artist -> "Artista en Dezzy"
    ShareKind.Song -> "Canción en Dezzy"
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun urlEncode(text: String): String = buildString {
    text.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 0x80 || ch in "-_.~") append(ch)
        else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

private fun Fill.fillModifier(): Modifier = if (gradient) {
    Modifier.background(Brush.verticalGradient(listOf(color, Color.Black)))
} else {
    Modifier.background(color)
}

private typealias Fill = CardStyle.Fill

/** Caja cuya imagen se graba en [layer] para poder guardarla o compartirla. */
@Composable
private fun CapturedCard(layer: GraphicsLayer, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.widthIn(max = 320.dp).fillMaxWidth().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
        content = content,
    )
}

@Composable
private fun SongCard(fill: Fill, cover: String?, title: String, subtitle: String) {
    val text = if (fill.gradient || fill.color.luminance() <= 0.5f) Color.White else Color.Black
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(fill.fillModifier()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(cover, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(12.dp))
        Text(title, color = text, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(subtitle, color = text.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PolaroidCard(cover: String?, title: String, subtitle: String) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(16.dp)).background(Paper),
        contentAlignment = Alignment.Center,
    ) {
        // Papel de cuaderno: renglones y margen rojo.
        Canvas(Modifier.matchParentSize()) {
            val step = 18.dp.toPx()
            var y = step
            while (y < size.height) {
                drawLine(PaperLine, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                y += step
            }
            val x = 22.dp.toPx()
            drawLine(PaperMargin, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.5.dp.toPx())
        }
        Column(
            Modifier.fillMaxWidth(0.72f).rotate(-4f).background(Color.White).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 16.dp),
        ) {
            AsyncImage(cover, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color(0xFF111111), fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFF666666), fontFamily = FontFamily.Cursive, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Portada (de álbum, lista o artista) en una tarjeta negra dentro del marco de color. */
@Composable
private fun CoverCard(fill: Fill, cover: String?, title: String, label: String, round: Boolean) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(fill.fillModifier()).padding(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(InnerCard).padding(14.dp)) {
            AsyncImage(
                cover, null,
                Modifier.fillMaxWidth().padding(horizontal = if (round) 16.dp else 0.dp).aspectRatio(1f)
                    .clip(if (round) CircleShape else RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(label, color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("★ Dezzy", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

/** Álbum o lista en forma de lista: cabecera y las primeras canciones. */
@Composable
private fun ListCard(fill: Fill, cover: String?, title: String, label: String, tracks: List<Track>) {
    val text = if (fill.gradient || fill.color.luminance() <= 0.5f) Color.White else Color.Black
    val sub = text.copy(alpha = 0.7f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(fill.fillModifier()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(cover, null, Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = text, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(label, color = sub, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        tracks.take(4).forEach { t ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(t.thumbnail ?: cover, null, Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artists, color = sub, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("★ Dezzy", color = text, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

/** Vista previa del vídeo del Canvas, con marco o sin él. */
@Composable
private fun CanvasCard(canvas: CanvasArtwork?, loaded: Boolean, branded: Boolean, fill: Fill, title: String, subtitle: String) {
    val url = canvas?.playbackUrls()?.let { it.first ?: it.second }
    if (url == null) {
        Box(
            Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(ChipBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (loaded) "Esta canción no tiene Canvas" else "Buscando el Canvas…",
                color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            )
        }
        return
    }
    if (!branded) {
        Box(Modifier.width(220.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            CanvasVideo(url, canvas.loopStartMs, canvas.loopLengthMs, true, Modifier.fillMaxSize())
        }
        return
    }
    Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(fill.fillModifier()).padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(InnerCard).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                CanvasVideo(url, canvas.loopStartMs, canvas.loopLengthMs, true, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text("★ Dezzy", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)).background(ChipBg).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color(0xFF111111) else Color(0xFFDDDDDD), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else ChipBg).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (on) Color(0xFF111111) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun Pill(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp))
            .then(if (on) Modifier.background(Color.White) else Modifier.border(1.dp, Color(0xFF666666), RoundedCornerShape(18.dp)))
            .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Color(0xFF111111) else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(ChipBg), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFDDDDDD), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
    }
}
