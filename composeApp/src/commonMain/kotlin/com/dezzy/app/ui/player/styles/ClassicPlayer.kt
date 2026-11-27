/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.styles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import com.dezzy.app.ui.player.kit.playerSwipeToChange
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.ui.components.glass.DezzyGlassSurfaceButton
import com.dezzy.app.ui.components.glass.DezzyIconButton as IconButton
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.CanvasArtworkPlayer
import com.dezzy.app.ui.player.kit.ConnectDeviceBottomSheet
import com.dezzy.app.ui.player.kit.ConnectState
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.Player
import com.dezzy.app.ui.player.kit.isLandscape
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberDesktopLink
import com.dezzy.app.ui.player.kit.rememberPlaybackCanvas
import kotlin.math.PI
import kotlin.math.sin

/**
 * Reproductor «Clásico» (fase 2 del rediseño, Boceto A: reproductor M3 enfocado).
 *
 * Una sola pantalla limpia con roles Material 3 (se adapta solo al tema claro y
 * oscuro): barra superior con contraer / origen / Más, carátula grande con
 * canvas, título con «Me gusta», barra de progreso, controles con Play/Pausa de
 * 80 dp y cuatro acciones con etiqueta (Letras, Dispositivos, Temporizador y
 * Cola). El resto de opciones vive en la hoja «Más», ordenada por grupos, que
 * al final abre el menú completo de siempre para no perder ninguna función.
 *
 * El fondo es un degradado suave del color de la carátula hacia `surface`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicPlayer(
    state: BottomSheetState,
    navController: NavController,
    positionState: State<Long>,
    durationState: State<Long>,
    sliderPosition: Long?,
    onSliderPositionChange: (Long?) -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onMenuClick: () -> Unit,
    onVisualizerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val currentSongLiked = currentSong?.song?.liked == true

    val position by positionState
    val duration by durationState
    val currentPos = sliderPosition ?: position

    val colorScheme = MaterialTheme.colorScheme
    val isLandscape = isLandscape()

    // ---- Dispositivo activo (Liner Connect) ----
    val linerManager = rememberDesktopLink()
    val linerConnState by linerManager.connectionState.collectAsState()
    val isConnectedToDesktop = linerConnState == ConnectState.CONNECTED
    var showDevicePicker by remember { mutableStateOf(false) }

    // ---- Canvas de la canción sobre la carátula ----
    val canvasArtwork = rememberPlaybackCanvas(
        mediaId = mediaMetadata?.id,
        title = mediaMetadata?.title,
        artist = mediaMetadata?.artists?.firstOrNull()?.name,
        albumTitle = mediaMetadata?.album?.title,
        durationMs = duration,
    )
    val (canvasPrimary, canvasFallback) = canvasArtwork?.playbackUrls() ?: (null to null)
    val hasCanvas = canvasPrimary != null || canvasFallback != null

    // ---- Color de la carátula para el degradado del fondo ----
    var targetAccent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(mediaMetadata?.id) {
        val thumbnailUrl = mediaMetadata?.thumbnailUrl ?: return@LaunchedEffect
        val palette = loadArtPalette(thumbnailUrl, 16) ?: return@LaunchedEffect
        val rgb = palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb ?: return@LaunchedEffect
        targetAccent = Color(rgb)
    }
    val accent by animateColorAsState(
        targetValue = targetAccent ?: colorScheme.primary,
        animationSpec = tween(600),
        label = "classicAccent",
    )
    val isDarkSurface = colorScheme.surface.luminance() < 0.5f
    val topTint = if (isDarkSurface) 0.30f else 0.34f
    val background = Brush.verticalGradient(
        0f to lerp(colorScheme.surface, accent, topTint),
        0.45f to lerp(colorScheme.surface, accent, topTint * 0.35f),
        0.8f to colorScheme.surface,
    )

    // ---- Bloques reutilizados en vertical y horizontal ----
    val topBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { state.collapseSoft() }) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = "Contraer reproductor",
                    tint = colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "REPRODUCIENDO DESDE",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = queueTitle ?: "Tu música",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            // DJ Dezzy llega en la fase 10: la hoja se sustituye por la ruta «dj_dezzy».
            IconButton(onClick = {
                state.collapseSoft()
                navController.navigate("dj_dezzy")
            }) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "DJ Dezzy",
                    tint = colorScheme.onSurface,
                )
            }
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Más opciones",
                    tint = colorScheme.onSurface,
                )
            }
        }
    }

    val look = com.dezzy.app.ui.player.kit.rememberPlayerAppearance()
    val cover: @Composable (Modifier) -> Unit = { coverModifier ->
        Box(modifier = coverModifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .shadow(12.dp, look.coverShape)
                    .clip(look.coverShape)
                    .background(colorScheme.surfaceContainerHighest)
                    // Deslizar la carátula a los lados cambia de canción (si el ajuste está activo).
                    .playerSwipeToChange(playerConnection, look.swipeToChange),
            ) {
                if (look.hideThumbnail) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote, null,
                            Modifier.fillMaxSize(0.4f),
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                } else AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = mediaMetadata?.title?.let { "Carátula de $it" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (hasCanvas && !look.hideThumbnail) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasPrimary,
                        fallbackUrl = canvasFallback,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    val controls: @Composable ColumnScope.() -> Unit = {
        // Título + artista + «Me gusta».
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata?.title ?: "",
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        enabled = mediaMetadata?.artists?.firstOrNull()?.id != null,
                        role = Role.Button,
                    ) {
                        mediaMetadata?.artists?.firstOrNull()?.id?.let {
                            state.collapseSoft()
                            navController.navigate("artist/$it")
                        }
                    },
                )
            }
            IconButton(onClick = { playerConnection.toggleLike() }) {
                Icon(
                    imageVector = if (currentSongLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (currentSongLiked) "Quitar de Me gusta" else "Me gusta",
                    tint = if (currentSongLiked) colorScheme.primary else colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Barra de progreso dinámica: toma el color de la carátula y, mientras suena,
        // la parte recorrida ondula suavemente (se aplana al pausar).
        val sliderMax = maxOf(duration.toFloat(), 1f)
        val activeTrack = remember(accent, isDarkSurface) {
            when {
                isDarkSurface && accent.luminance() < 0.22f -> lerp(accent, Color.White, 0.45f)
                !isDarkSurface && accent.luminance() > 0.55f -> lerp(accent, Color.Black, 0.4f)
                else -> accent
            }
        }
        val inactiveTrack = lerp(colorScheme.surfaceContainerHighest, activeTrack, 0.18f)
        val waveAmplitude by animateFloatAsState(
            targetValue = if (isPlaying && sliderPosition == null) 1f else 0f,
            animationSpec = tween(450),
            label = "classicWaveAmp",
        )
        val wavePhase by rememberInfiniteTransition(label = "classicWave").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "classicWavePhase",
        )
        Slider(
            value = currentPos.toFloat().coerceIn(0f, sliderMax),
            valueRange = 0f..sliderMax,
            onValueChange = { onSliderPositionChange(it.toLong()) },
            onValueChangeFinished = {
                playerConnection.player.seekTo(currentPos)
                onSliderPositionChange(null)
            },
            colors = SliderDefaults.colors(
                thumbColor = activeTrack,
                activeTrackColor = activeTrack,
                inactiveTrackColor = inactiveTrack,
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(activeTrack),
                )
            },
            track = { sliderState ->
                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val fraction = if (range > 0f) {
                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    val stroke = 4.dp.toPx()
                    val midY = size.height / 2f
                    val splitX = size.width * fraction
                    // Resto de la canción: línea recta.
                    if (splitX < size.width) {
                        drawLine(
                            color = inactiveTrack,
                            start = Offset(splitX, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                    // Parte recorrida: onda con el color de la carátula.
                    if (splitX > 0f) {
                        val amp = 3.dp.toPx() * waveAmplitude
                        if (amp < 0.5f) {
                            drawLine(
                                color = activeTrack,
                                start = Offset(0f, midY),
                                end = Offset(splitX, midY),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        } else {
                            val wavelength = 28.dp.toPx()
                            val path = Path()
                            var x = 0f
                            path.moveTo(0f, midY + amp * sin(wavePhase))
                            while (x < splitX) {
                                x = minOf(x + 2f, splitX)
                                path.lineTo(x, midY + amp * sin(x / wavelength * 2f * PI.toFloat() + wavePhase))
                            }
                            drawPath(
                                path = path,
                                color = activeTrack,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(28.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(makeTimeString(currentPos), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
            Text(makeTimeString(duration), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        // Controles: aleatorio, anterior, Play/Pausa (80 dp), siguiente, repetir.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled }) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = if (shuffleModeEnabled) "Aleatorio activado" else "Aleatorio desactivado",
                    tint = if (shuffleModeEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { playerConnection.seekToPrevious() },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }
            // Reproduciendo: cuadrado redondeado; en pausa: círculo.
            val playCorner by animateDpAsState(
                targetValue = if (isPlaying) 28.dp else 40.dp,
                label = "classicPlayCorner",
            )
            DezzyGlassSurfaceButton(
                onClick = { playerConnection.player.togglePlayPause() },
                shape = RoundedCornerShape(playCorner),
                normalContainerColor = colorScheme.primary,
                normalContentColor = colorScheme.onPrimary,
                prominent = true,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(
                onClick = { playerConnection.seekToNext() },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { playerConnection.player.toggleRepeatMode() }) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repetir una"
                        Player.REPEAT_MODE_ALL -> "Repetir todo"
                        else -> "Repetir desactivado"
                    },
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) colorScheme.primary else colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Cuatro acciones con etiqueta.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ClassicActionButton(
                icon = Icons.Rounded.Lyrics,
                label = "Letras",
                onClick = onLyricsClick,
                modifier = Modifier.weight(1f),
            )
            ClassicActionButton(
                icon = Icons.Rounded.Speaker,
                label = "Dispositivos",
                active = isConnectedToDesktop,
                onClick = { showDevicePicker = true },
                modifier = Modifier.weight(1f),
            )
            ClassicActionButton(
                icon = Icons.Rounded.Bedtime,
                label = if (sleepTimerEnabled && sleepTimerTimeLeft > 0) makeTimeString(sleepTimerTimeLeft) else "Temporizador",
                active = sleepTimerEnabled,
                onClick = onSleepTimerClick,
                modifier = Modifier.weight(1f),
            )
            ClassicActionButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Cola",
                onClick = onQueueClick,
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cover(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    topBar()
                    controls()
                    Spacer(Modifier.height(16.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topBar()
                // La carátula ocupa el espacio libre y se encoge en pantallas
                // pequeñas en lugar de empujar los controles fuera.
                cover(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
                controls()
            }
        }
    }

    if (showDevicePicker) {
        ConnectDeviceBottomSheet(
            onDismissRequest = { showDevicePicker = false },
            onOpenListenTogether = {
                showDevicePicker = false
                state.collapseSoft()
                navController.navigate("settings/music_together")
            },
        )
    }
}

/** Acción inferior con icono en pastilla (indicador `secondaryContainer` si está activa) y etiqueta de 12 sp. */
@Composable
private fun ClassicActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(CircleShape)
                .background(if (active) colorScheme.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (active) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
