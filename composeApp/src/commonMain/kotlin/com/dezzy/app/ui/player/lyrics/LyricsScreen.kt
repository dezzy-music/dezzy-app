/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dezzy.app.feature.lyrics.LyricsManager
import androidx.compose.material3.IconButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.player.canvas.AiCanvasScreen
import com.dezzy.app.ui.player.canvas.LyricsShareSheet
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dezzy.app.ui.player.kit.ArtPalette
import com.dezzy.app.ui.player.kit.LyricsBackground
import com.dezzy.app.ui.player.kit.LyricsBackgroundKey
import com.dezzy.app.ui.player.kit.LyricsBgBlurKey
import com.dezzy.app.ui.player.kit.LyricsBgDimKey
import com.dezzy.app.ui.player.kit.LyricsBgImageUriKey
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.player.kit.Player
import com.dezzy.app.ui.player.kit.PlayerBackgroundLayer
import com.dezzy.app.ui.player.kit.PlayerBackgroundStyle
import com.dezzy.app.ui.player.kit.PlayerDesignStyle
import com.dezzy.app.ui.player.kit.PlayerDesignStyleKey
import com.dezzy.app.ui.player.kit.PlayerBackgroundStyleKey
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.SliderStyle
import com.dezzy.app.ui.player.kit.SliderStyleKey
import com.dezzy.app.ui.player.kit.StyledPlaybackSlider
import com.dezzy.app.ui.player.kit.isLandscape
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * Letras a pantalla completa: cabecera con cerrar / canvas IA / más, la letra sincronizada
 * (con traducción y compartir) y los mandos del reproductor debajo.
 */
@Composable
fun LyricsScreen(playerConnection: PlayerConnection, onClose: () -> Unit) {
    val manager: LyricsManager = koinInject()
    val meta by playerConnection.mediaMetadata.collectAsState()
    val bgStyle by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val designStyle by rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V1)
    // «Fondo del reproductor» sólo aplica con el estilo Clásico.
    val customPlayerBg = designStyle == PlayerDesignStyle.V1 && bgStyle != PlayerBackgroundStyle.DEFAULT
    var showCanvas by remember { mutableStateOf(false) }
    var shareLines by remember { mutableStateOf<List<String>?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    PlatformBackHandler(enabled = true) { if (showCanvas) showCanvas = false else onClose() }

    // Fondo propio de las letras (independiente del reproductor).
    val lyricsBg by rememberEnumPreference(LyricsBackgroundKey, LyricsBackground.SAME_AS_PLAYER)
    val bgImageUri by rememberPreference(LyricsBgImageUriKey, "")
    val bgBlur by rememberPreference(LyricsBgBlurKey, 0f)
    val bgDim by rememberPreference(LyricsBgDimKey, 0.4f)
    val useImage = lyricsBg == LyricsBackground.IMAGE && bgImageUri.isNotBlank()

    // Ajustes › Apariencia › Letras: barra de estado oculta y fondo con la portada desenfocada.
    val hideStatusBar by rememberPreference(com.dezzy.app.ui.screens.settings.prefs.LyricsHideStatusBarKey, false)
    val standardBlur by rememberPreference(com.dezzy.app.ui.screens.settings.prefs.LyricsStandardBlurKey, false)
    HideStatusBarEffect(hideStatusBar)

    // Degradado con los colores de la carátula, salvo en el estilo por defecto.
    val followsPlayer = lyricsBg == LyricsBackground.SAME_AS_PLAYER
    // Como en la app original: siempre degradado con los colores de la carátula.
    val plain = lyricsBg == LyricsBackground.IMAGE && !useImage
    var palette by remember { mutableStateOf<ArtPalette?>(null) }
    LaunchedEffect(meta?.thumbnailUrl, plain, useImage) {
        palette = if (plain || useImage) null else loadArtPalette(meta?.thumbnailUrl, 16)
    }
    val surface = MaterialTheme.colorScheme.surface
    val background = if (plain || useImage) {
        Brush.verticalGradient(listOf(surface, surface))
    } else {
        val top = palette?.let { Color(it.getDominantColor(0xFF303030.toInt()) or 0xFF000000.toInt()) } ?: Color(0xFF303030)
        if (lyricsBg == LyricsBackground.SOLID) {
            val solid = lerp(top, Color.Black, 0.35f)
            Brush.verticalGradient(listOf(solid, solid))
        } else {
            // Diagonal entre el color dominante y el vivo de la carátula (como la app actual).
            val other = palette?.let {
                val v = it.getLightVibrantColor(it.getMutedColor(it.getVibrantColor(0xFF5A5A20.toInt())))
                Color(v or 0xFF000000.toInt())
            } ?: lerp(top, Color.Black, 0.5f)
            Brush.linearGradient(
                listOf(lerp(top, Color.Black, 0.1f), lerp(top, other, 0.45f), lerp(other, Color.Black, 0.35f)),
            )
        }
    }
    val textColor = if (plain) MaterialTheme.colorScheme.onBackground else Color.White
    val landscape = isLandscape()

    Box(
        Modifier
            .fillMaxSize()
            .then(if (customPlayerBg) Modifier.background(Color.Black) else Modifier.background(background))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (customPlayerBg) {
            PlayerBackgroundLayer(meta?.thumbnailUrl, emptyList())
        } else if (standardBlur && !useImage && meta?.thumbnailUrl != null) {
            // Portada desenfocada a pantalla completa detrás de la letra.
            AsyncImage(
                model = meta?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        if (useImage && !customPlayerBg) {
            AsyncImage(
                model = bgImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(bgBlur.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgDim)))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            LyricsHeader(
                title = meta?.title.orEmpty(),
                color = textColor,
                onClose = onClose,
                onCanvas = { showCanvas = true },
                onRetry = { manager.retry() },
                onMenu = { showMenu = true },
            )
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                    LyricsContent(
                        playerConnection = playerConnection,
                        modifier = Modifier.weight(1.4f).fillMaxSize().padding(horizontal = 20.dp),
                        contentColor = textColor, textSizeOverride = null, showChips = false,
                        onShare = { shareLines = it },
                    )
                    Column(
                        Modifier.weight(1f).fillMaxSize().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlayerControls(playerConnection, textColor)
                    }
                }
            } else {
                LyricsContent(
                    playerConnection = playerConnection,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    contentColor = textColor, textSizeOverride = null, showChips = false,
                    onShare = { shareLines = it },
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp)) {
                    PlayerControls(playerConnection, textColor)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (showCanvas) {
            Box(Modifier.fillMaxSize().zIndex(2f)) { AiCanvasScreen(onClose = { showCanvas = false }) }
        }
        if (showMenu) {
            LyricsMenuSheet(
                title = meta?.title.orEmpty(),
                artist = meta?.artists?.joinToString { it.name }.orEmpty(),
                manager = manager,
                onDismiss = { showMenu = false },
            )
        }
        shareLines?.let { lines ->
            meta?.track?.let { track ->
                LyricsShareSheet(track = track, lines = lines, onDismiss = { shareLines = null })
            }
        }
    }
}

@Composable
private fun LyricsHeader(
    title: String,
    color: Color,
    onClose: () -> Unit,
    onCanvas: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).zIndex(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(32.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ExpandMore, "Cerrar", Modifier.size(24.dp), tint = color)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Reproduciendo ahora", style = MaterialTheme.typography.titleMedium, color = color)
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clickable(onClick = onCanvas), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, "Canvas IA", Modifier.size(20.dp), tint = color)
            }
            Spacer(Modifier.size(4.dp))
            Box(Modifier.size(32.dp).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, "Más opciones", Modifier.size(20.dp), tint = color)
            }
        }
    }
}

/** Barra de progreso con tiempos, botones de reproducción y volumen. */
@Composable
private fun PlayerControls(pc: PlayerConnection, color: Color) {
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val controller = pc.controller
    val position by remember(controller) { controller.state.map { it.positionMs }.distinctUntilChanged() }
        .collectAsState(controller.state.value.positionMs)
    val duration by remember(controller) { controller.state.map { it.durationMs }.distinctUntilChanged() }
        .collectAsState(controller.state.value.durationMs)
    var drag by remember { mutableStateOf<Long?>(null) }
    val playbackState by pc.playbackState.collectAsState()
    val isPlaying by pc.isPlaying.collectAsState()
    val repeatMode by pc.repeatMode.collectAsState()
    val shuffle by pc.shuffleModeEnabled.collectAsState()
    val meta by pc.mediaMetadata.collectAsState()
    val volume by pc.service.playerVolume.collectAsState()
    val isLoading = playbackState == Player.STATE_BUFFERING || drag != null

    StyledPlaybackSlider(
        sliderStyle = sliderStyle,
        value = (drag ?: position).toFloat(),
        valueRange = 0f..(if (duration > 0) duration.toFloat() else 0f),
        onValueChange = { drag = it.toLong() },
        onValueChangeFinished = {
            drag?.let { pc.player.seekTo(it) }
            drag = null
        },
        activeColor = color,
        isPlaying = isPlaying,
        songKey = meta?.id.orEmpty(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(makeTimeString(drag ?: position), style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f))
        Text(
            if (duration > 0) makeTimeString(duration) else "",
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.7f),
        )
    }

    Spacer(Modifier.height(24.dp))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { pc.player.toggleRepeatMode() }, modifier = Modifier.size(40.dp)) {
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> "Repetir desactivado"
                    Player.REPEAT_MODE_ALL -> "Repetir todo"
                    else -> "Repetir una"
                },
                tint = if (repeatMode == Player.REPEAT_MODE_OFF) color.copy(alpha = 0.4f) else color,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = { pc.player.seekToPrevious() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.FastRewind, "Anterior", Modifier.size(24.dp), tint = color)
        }
        IconButton(onClick = { pc.player.togglePlayPause() }, modifier = Modifier.size(56.dp)) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(36.dp), color = color, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pausar" else "Reproducir",
                    Modifier.size(36.dp),
                    tint = color,
                )
            }
        }
        IconButton(onClick = { pc.player.seekToNext() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.FastForward, "Siguiente", Modifier.size(24.dp), tint = color)
        }
        IconButton(onClick = { pc.player.shuffleModeEnabled = !shuffle }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.Shuffle, "Aleatorio", Modifier.size(20.dp),
                tint = if (shuffle) color else color.copy(alpha = 0.4f),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeOff, "Volumen mínimo", Modifier.size(20.dp), tint = color)
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = { pc.service.playerVolume.value = it },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.24f),
            ),
            modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 16.dp),
        )
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Volumen máximo", Modifier.size(20.dp), tint = color)
    }
}
