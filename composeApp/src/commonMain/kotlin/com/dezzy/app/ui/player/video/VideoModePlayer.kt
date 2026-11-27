/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.video.VideoDownloadState
import com.dezzy.app.feature.video.VideoPipState
import com.dezzy.app.feature.video.VideoPlayerHandle
import com.dezzy.app.feature.video.VideoPlayerListener
import com.dezzy.app.feature.video.VideoResizeMode
import com.dezzy.app.feature.video.VideoResizeModeKey
import com.dezzy.app.ui.components.glass.DezzyIconButton as IconButton
import com.dezzy.app.ui.components.glass.DezzyTextButton as TextButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.player.kit.AddToPlaylistDialog
import com.dezzy.app.ui.player.kit.DevicesButton
import com.dezzy.app.ui.player.kit.KeepScreenOn
import com.dezzy.app.ui.player.kit.Player
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.SongVideoToggle
import com.dezzy.app.ui.player.kit.VideoFullscreenDialog
import com.dezzy.app.ui.player.kit.VideoSurface
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import kotlinx.coroutines.delay

/** Fondo del modo video: casi negro, para que el resplandor del video se note. */
private val VideoBackground = Color(0xFF0C0C0F)

/**
 * Capa a tamaño completo con el reproductor de video aislado (`videoPlayer` de MusicService)
 * cuando el modo video está activo, encima del estilo de reproductor que haya debajo: así el
 * modo video funciona con todos los skins sin pasar una superficie de video por cada uno.
 * También lleva el botón flotante para activarlo (abajo a la derecha) en los estilos que no
 * tienen su propia píldora Canción/Video.
 *
 * Dentro hay dos pantallas que comparten estado (recomendados, pantalla completa):
 * el modo video inmersivo y "Solo videos".
 */
@Composable
fun VideoModeOverlay(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    /** False on styles that surface their own toggle (the V1–V4 Canción/Video pill). */
    showFloatingToggle: Boolean = true,
    /** Baja el reproductor a mini-player (la flecha ⌄ de arriba a la izquierda). */
    onCollapse: () -> Unit = {},
    /** Abre la cola (la barra inferior "A continuación"). */
    onQueueClick: () -> Unit = {},
) {
    val videoModeActive by playerConnection.videoModeActive.collectAsState()
    val videoModeLoading by playerConnection.videoModeLoading.collectAsState()
    val videoPlayer by playerConnection.videoPlayerInstance.collectAsState()
    val videoModeError by playerConnection.videoModeError.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val inPip by VideoPipState.inPip.collectAsState()
    val downloadState by playerConnection.videoDownloadState.collectAsState()

    val scope = rememberCoroutineScope()
    val recommendations = remember { VideoRecommendations(scope, playerConnection.client) }
    var feedOpen by rememberSaveable { mutableStateOf(false) }
    var recoFilter by rememberSaveable { mutableStateOf(VideoRecoFilter.FOR_SONG) }

    // Al pasar de canción el servicio apaga el modo video un instante y lo vuelve a encender
    // (VideoKeepModeOnSongChangeKey): mientras carga el siguiente video la capa se queda
    // visible con un indicador, en vez de parpadear a la pantalla de audio y perder "Solo videos".
    val showScreen = videoModeActive && videoPlayer != null
    var keepShown by remember { mutableStateOf(false) }
    LaunchedEffect(showScreen, videoModeLoading) {
        if (showScreen) keepShown = true else if (!videoModeLoading) keepShown = false
    }
    val visible = showScreen || (keepShown && videoModeLoading)
    LaunchedEffect(visible) {
        if (!visible) feedOpen = false
    }
    // La pantalla no se apaga mientras el video suena (sí cuando está en pausa).
    KeepScreenOnWhilePlaying(if (visible) videoPlayer else null)

    val songId = mediaMetadata?.id
    val songTitle = mediaMetadata?.title.orEmpty()
    val songArtist = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty()
    LaunchedEffect(visible, songId, recoFilter) {
        if (visible && songId != null) {
            recommendations.load(songId, songTitle, songArtist, recoFilter)
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            val retry = {
                if (songId != null) recommendations.retry(songId, songTitle, songArtist, recoFilter)
            }
            if (feedOpen) {
                PlatformBackHandler(enabled = true) { feedOpen = false }
                VideoFeedScreen(
                    playerConnection = playerConnection,
                    videoPlayer = videoPlayer,
                    loading = videoModeLoading || !videoModeActive,
                    recommendations = recommendations,
                    filter = recoFilter,
                    artistName = songArtist,
                    onFilterChange = { recoFilter = it },
                    onRetry = retry,
                    onBack = { feedOpen = false },
                    onQueueClick = onQueueClick,
                )
            } else {
                VideoModeScreen(
                    playerConnection = playerConnection,
                    videoPlayer = videoPlayer,
                    loading = videoModeLoading || !videoModeActive,
                    recommendations = recommendations,
                    onRetry = retry,
                    onOpenFeed = { feedOpen = true },
                    onCollapse = onCollapse,
                    onQueueClick = onQueueClick,
                )
            }
        }

        if (!videoModeActive && !visible && showFloatingToggle) {
            Surface(
                onClick = { playerConnection.enableVideoMode() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (videoModeLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.SlowMotionVideo,
                            contentDescription = "Ver video musical",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (!inPip) {
            videoModeError?.let { message ->
                VideoModeErrorSnack(
                    message = message,
                    onRetry = { playerConnection.retryVideoMode() },
                    onDismiss = { playerConnection.clearVideoModeError() },
                )
            }
            if (visible) {
                VideoDownloadStatus(
                    state = downloadState,
                    onClear = { playerConnection.clearVideoDownloadResult() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 76.dp),
                )
            }
        }
    }
}

/** Mantiene la pantalla encendida sólo mientras [player] está reproduciendo. */
@Composable
private fun KeepScreenOnWhilePlaying(player: VideoPlayerHandle?) {
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        if (player == null) {
            playing = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : VideoPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        playing = player.isPlaying
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            playing = false
        }
    }
    KeepScreenOn(playing)
}

/** Estado de reproducción del reproductor de VIDEO (independiente del de la app). */
@Stable
internal class VideoPlaybackState {
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
}

@Composable
internal fun rememberVideoPlaybackState(player: VideoPlayerHandle?): VideoPlaybackState {
    val state = remember { VideoPlaybackState() }
    var ticking by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        fun sync(p: VideoPlayerHandle) {
            state.position = p.currentPosition
            state.duration = p.duration.takeIf { it > 0 } ?: 0L
        }
        val listener = object : VideoPlayerListener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                state.isPlaying = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.buffering = playbackState == Player.STATE_BUFFERING
                player?.let(::sync)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ticking = isPlaying
                player?.let(::sync)
            }

            // Saltos (doble toque, barra, notificación…) se reflejan aunque esté en pausa.
            override fun onPositionDiscontinuity() {
                player?.let(::sync)
            }
        }
        if (player != null) {
            state.isPlaying = player.playWhenReady
            state.buffering = player.playbackState == Player.STATE_BUFFERING
            ticking = player.isPlaying
            sync(player)
            player.addListener(listener)
        } else {
            ticking = false
        }
        onDispose { player?.removeListener(listener) }
    }
    // Sólo se sondea la posición mientras el video avanza: en pausa no gasta batería.
    LaunchedEffect(player, ticking) {
        val p = player ?: return@LaunchedEffect
        if (!ticking) return@LaunchedEffect
        while (true) {
            state.position = p.currentPosition
            state.duration = p.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }
    return state
}

/**
 * Modo video inmersivo: el video arriba con los controles encima (se ocultan a los 3 s; un
 * toque los muestra, doble toque salta ±10 s), un resplandor del color de la portada, y
 * debajo la píldora Canción/Video, título, acciones, "Recomendados" y la barra
 * "A continuación". Play/pausa y la barra actúan sobre el reproductor de VIDEO; anterior,
 * siguiente, aleatorio y repetir, sobre la cola real de la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoModeScreen(
    playerConnection: PlayerConnection,
    videoPlayer: VideoPlayerHandle?,
    loading: Boolean,
    recommendations: VideoRecommendations,
    onRetry: () -> Unit,
    onOpenFeed: () -> Unit,
    onCollapse: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val liked by playerConnection.currentSongLiked.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val videoModeActive by playerConnection.videoModeActive.collectAsState()
    var resizeModePref by rememberEnumPreference(VideoResizeModeKey, VideoResizeMode.FIT)

    val playback = rememberVideoPlaybackState(videoPlayer)
    val ambient = rememberAmbientColor(mediaMetadata?.thumbnailUrl)

    // Al cambiar de canción el servicio apaga el modo video; si el salto lo pidió el usuario
    // desde AQUÍ, se vuelve a encender para no echarlo a la pantalla de audio.
    var keepVideoOnTrackChange by remember { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata?.id, videoModeActive) {
        if (keepVideoOnTrackChange && !videoModeActive && mediaMetadata != null) {
            keepVideoOnTrackChange = false
            if (!playerConnection.videoModeLoading.value) playerConnection.enableVideoMode()
        }
    }

    // Título de la siguiente canción de la cola, para la barra "A continuación".
    val nextTitle = remember(mediaMetadata?.id, shuffleEnabled, repeatMode, queueWindows, currentWindowIndex) {
        runCatching {
            val size = queueWindows.size
            val index = when {
                size == 0 || currentWindowIndex < 0 -> -1
                repeatMode == Player.REPEAT_MODE_ONE -> currentWindowIndex
                currentWindowIndex + 1 < size -> currentWindowIndex + 1
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> -1
            }
            if (index < 0) null else queueWindows[index].mediaItem.metadata?.title
        }.getOrNull()
    }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    val inPip by VideoPipState.inPip.collectAsState()
    val qualityOptions by playerConnection.videoQualityOptions.collectAsState()
    val selectedHeight by playerConnection.videoSelectedHeight.collectAsState()
    val playingHeight by playerConnection.videoPlayingHeight.collectAsState()
    val qualityLabel = qualityLabel(selectedHeight, playingHeight)

    if (showComments) {
        val commentsId = mediaMetadata?.id
        if (commentsId != null) VideoCommentsSheet(commentsId) { showComments = false } else showComments = false
    }

    val togglePlay: () -> Unit = {
        videoPlayer?.let { it.playWhenReady = !it.playWhenReady }
    }
    val seekTo: (Long) -> Unit = { ms -> videoPlayer?.seekTo(ms.coerceAtLeast(0L)) }
    val previous: () -> Unit = {
        keepVideoOnTrackChange = true
        playerConnection.seekToPrevious()
    }
    val next: () -> Unit = {
        keepVideoOnTrackChange = true
        playerConnection.seekToNext()
    }
    val menuItems: @Composable ColumnScope.(close: () -> Unit) -> Unit = { close ->
        DropdownMenuItem(
            text = { Text(if (shuffleEnabled) "Aleatorio: activado" else "Aleatorio: desactivado") },
            leadingIcon = {
                Icon(if (shuffleEnabled) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle, null)
            },
            onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
        )
        DropdownMenuItem(
            text = {
                Text(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repetir: esta canción"
                        Player.REPEAT_MODE_ALL -> "Repetir: toda la cola"
                        else -> "Repetir: no"
                    },
                )
            },
            leadingIcon = {
                Icon(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                        Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                        else -> Icons.Rounded.Repeat
                    },
                    null,
                )
            },
            // El ciclo OFF → ALL → ONE → OFF lo hace el reproductor.
            onClick = { playerConnection.player.toggleRepeatMode() },
        )
        DropdownMenuItem(
            text = {
                Text(
                    when (resizeModePref) {
                        VideoResizeMode.FIT -> "Ajuste: encajar"
                        VideoResizeMode.CROP -> "Ajuste: recortar"
                        VideoResizeMode.FILL -> "Ajuste: estirar"
                    },
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Fullscreen, null) },
            onClick = {
                resizeModePref = when (resizeModePref) {
                    VideoResizeMode.FIT -> VideoResizeMode.CROP
                    VideoResizeMode.CROP -> VideoResizeMode.FILL
                    VideoResizeMode.FILL -> VideoResizeMode.FIT
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Calidad: $qualityLabel") },
            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
            onClick = { close(); showQuality = true },
        )
        DropdownMenuItem(
            text = { Text("Solo videos") },
            leadingIcon = { Icon(Icons.Rounded.SlowMotionVideo, null) },
            onClick = { close(); onOpenFeed() },
        )
        DropdownMenuItem(
            text = { Text("Descargar video") },
            leadingIcon = { Icon(Icons.Rounded.Download, null) },
            onClick = {
                close()
                playerConnection.downloadCurrentVideo()
            },
        )
        DropdownMenuItem(
            text = { Text("Cerrar video") },
            leadingIcon = { Icon(Icons.Rounded.Close, null) },
            onClick = { close(); playerConnection.disableVideoMode() },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VideoBackground),
    ) {
        // Resplandor ambiental con el color de la portada.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .background(
                    Brush.verticalGradient(
                        0f to ambient.copy(alpha = 0.55f),
                        0.55f to ambient.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            VideoStage(
                player = videoPlayer,
                // En PiP la superficie la pone la capa raíz de la app (una sola a la vez).
                surfaceActive = !fullscreen && !inPip,
                loading = loading,
                playback = playback,
                resizeMode = resizeModePref,
                fullscreen = false,
                title = null,
                qualityLabel = qualityLabel,
                onOpenQuality = { showQuality = true },
                onTogglePlay = togglePlay,
                onSeekTo = seekTo,
                onPrevious = previous,
                onNext = next,
                onCollapse = onCollapse,
                onToggleFullscreen = { fullscreen = true },
                menuItems = menuItems,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(16.dp))
                SongVideoToggle(
                    playerConnection = playerConnection,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = mediaMetadata?.title.orEmpty(),
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .basicMarquee(),
                )
                Text(
                    text = mediaMetadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoActionChip(
                        icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (liked) "Te gusta" else "Me gusta",
                        highlighted = liked,
                        onClick = { playerConnection.toggleLike() },
                    )
                    VideoActionChip(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = "Guardar",
                        onClick = { showPlaylistDialog = true },
                    )
                    VideoActionChip(
                        icon = Icons.Rounded.ChatBubble,
                        label = "Comentarios",
                        onClick = { showComments = true },
                    )
                    VideoActionChip(
                        icon = Icons.Rounded.Download,
                        label = null,
                        contentDescription = "Descargar video",
                        onClick = { playerConnection.downloadCurrentVideo() },
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recomendados",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenFeed) {
                        Text("Ver todos", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    }
                }
                RecommendedCarousel(
                    recommendations = recommendations,
                    onRetry = onRetry,
                    onPlay = { playRelatedVideo(playerConnection, it) },
                )
                Spacer(Modifier.height(16.dp))
            }

            UpNextBar(
                nextTitle = nextTitle,
                queueTitle = queueTitle,
                onClick = onQueueClick,
            )
        }
    }

    if (fullscreen && !inPip) {
        VideoFullscreenDialog(onDismiss = { fullscreen = false }) {
            VideoStage(
                player = videoPlayer,
                surfaceActive = true,
                loading = loading,
                playback = playback,
                resizeMode = resizeModePref,
                fullscreen = true,
                title = mediaMetadata?.title,
                qualityLabel = qualityLabel,
                onOpenQuality = { showQuality = true },
                onTogglePlay = togglePlay,
                onSeekTo = seekTo,
                onPrevious = previous,
                onNext = next,
                onCollapse = { fullscreen = false },
                onToggleFullscreen = { fullscreen = false },
                menuItems = menuItems,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ── Hojas y diálogos ─────────────────────────────────────────────────
    mediaMetadata?.let { metadata ->
        AddToPlaylistDialog(
            isVisible = showPlaylistDialog,
            track = metadata.track,
            onDismiss = { showPlaylistDialog = false },
        )
    }

    if (showQuality && !inPip) {
        VideoQualitySheet(
            options = qualityOptions,
            selectedHeight = selectedHeight,
            playingHeight = playingHeight,
            onSelect = { height ->
                playerConnection.setVideoQuality(height)
                showQuality = false
            },
            onDismiss = { showQuality = false },
        )
    }
}

/**
 * El video con sus controles superpuestos. Se usa en el modo video (16:9) y en la pantalla
 * completa (llena la capa girada).
 */
@Composable
internal fun VideoStage(
    player: VideoPlayerHandle?,
    surfaceActive: Boolean,
    loading: Boolean,
    playback: VideoPlaybackState,
    resizeMode: VideoResizeMode,
    fullscreen: Boolean,
    title: String?,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    menuItems: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    qualityLabel: String? = null,
    onOpenQuality: (() -> Unit)? = null,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var hideTick by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var seekHint by remember { mutableStateOf<Int?>(null) }
    var seekHintTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, playback.isPlaying, hideTick, menuOpen, seekPreview) {
        if (controlsVisible && playback.isPlaying && !menuOpen && seekPreview == null) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekHintTick) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }
    val poke = {
        controlsVisible = true
        hideTick++
    }
    val busy = loading || playback.buffering
    val currentSeekTo by rememberUpdatedState(onSeekTo)
    val seekBy: (Long) -> Unit = { delta ->
        player?.let { p ->
            val max = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            currentSeekTo((p.currentPosition + delta).coerceIn(0L, max))
            seekHint = if (delta > 0) 10 else -10
            seekHintTick++
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            // El doble toque tiene alternativa para TalkBack (y botones visibles abajo).
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Retroceder 10 segundos") { seekBy(-10_000L); true },
                    CustomAccessibilityAction("Avanzar 10 segundos") { seekBy(10_000L); true },
                )
            }
            .pointerInput(player) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        hideTick++
                    },
                    onDoubleTap = { offset ->
                        seekBy(if (offset.x > size.width / 2f) 10_000L else -10_000L)
                    },
                )
            },
    ) {
        VideoSurface(
            player = player,
            active = surfaceActive,
            resizeMode = resizeMode,
            modifier = Modifier.fillMaxSize(),
        )

        seekHint?.let { hint ->
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(if (hint > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 36.dp),
            ) {
                Text(
                    text = if (hint > 0) "+10 s" else "−10 s",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || busy,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (controlsVisible) 0.38f else 0f)),
            ) {
                if (!controlsVisible) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                } else {
                    // Arriba: bajar · (título en pantalla completa) · cast · menú
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onCollapse) {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = if (fullscreen) "Salir de pantalla completa" else "Minimizar",
                                tint = Color.White,
                            )
                        }
                        if (fullscreen && title != null) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (onOpenQuality != null) {
                            Surface(
                                onClick = { onOpenQuality(); poke() },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 6.dp)
                                    .semantics { contentDescription = "Calidad del video: ${qualityLabel.orEmpty()}" },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    if (qualityLabel != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = qualityLabel,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                        DevicesButton(tint = Color.White)
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = Color.White,
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                menuItems { menuOpen = false }
                            }
                        }
                    }

                    // Centro: −10 s · anterior · play/pausa · siguiente · +10 s
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 32.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { seekBy(-10_000L); poke() }) {
                            Icon(
                                imageVector = Icons.Rounded.Replay,
                                contentDescription = "Retroceder 10 segundos",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IconButton(onClick = { onPrevious(); poke() }) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(if (fullscreen) 64.dp else 56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(enabled = !busy) { onTogglePlay(); poke() }
                                .semantics { contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(
                                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onNext(); poke() }) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        IconButton(onClick = { seekBy(10_000L); poke() }) {
                            Icon(
                                imageVector = Icons.Rounded.FastForward,
                                contentDescription = "Avanzar 10 segundos",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    // Abajo: tiempo · pantalla completa, y la barra de progreso
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = if (fullscreen) 20.dp else 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${makeTimeString(seekPreview ?: playback.position)} / ${makeTimeString(playback.duration)}",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onToggleFullscreen) {
                                Icon(
                                    imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                    tint = Color.White,
                                )
                            }
                        }
                        VideoSeekBar(
                            position = seekPreview ?: playback.position,
                            duration = playback.duration,
                            onPreview = { seekPreview = it; poke() },
                            onCommit = { ms ->
                                onSeekTo(ms)
                                seekPreview = null
                                poke()
                            },
                        )
                    }
                }
            }
        }

        // Línea de progreso fina con los controles ocultos (no en pantalla completa).
        if (!controlsVisible && !fullscreen && playback.duration > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(fraction = (playback.position.toFloat() / playback.duration).coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(Color.White),
            )
        }
    }
}

/** Barra de progreso fina y arrastrable (la Slider de M3 es demasiado alta sobre el video). */
@Composable
private fun VideoSeekBar(
    position: Long,
    duration: Long,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                contentDescription = "Posición del video"
                stateDescription = "${makeTimeString(position)} de ${makeTimeString(duration)}"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (duration > 0) {
                    setProgress { target ->
                        commit((target.coerceIn(0f, 1f) * duration).toLong())
                        true
                    }
                }
            }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) commit((offset.x / size.width * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        preview((dragFraction * duration).toLong())
                    },
                    onDragEnd = {
                        if (dragFraction >= 0f) commit((dragFraction * duration).toLong())
                        dragFraction = -1f
                    },
                    onDragCancel = { dragFraction = -1f },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        preview((dragFraction * duration).toLong())
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val shown = if (dragFraction >= 0f) dragFraction else fraction
        val dragging = dragFraction >= 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(shown)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        val thumb = if (dragging) 16.dp else 12.dp
        Box(
            modifier = Modifier
                .offset(x = (maxWidth - thumb) * shown)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Carrusel de "Recomendados": tarjetas 16:9 de 200 dp. */
@Composable
private fun RecommendedCarousel(
    recommendations: VideoRecommendations,
    onRetry: () -> Unit,
    onPlay: (Track) -> Unit,
) {
    when {
        recommendations.loading -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            userScrollEnabled = false,
        ) {
            items(3) {
                Column(Modifier.width(200.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                }
            }
        }

        recommendations.items.isEmpty() -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (recommendations.failed) "No se pudieron cargar los videos." else "No encontramos videos para esta canción.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (recommendations.failed) {
                TextButton(onClick = onRetry) { Text("Reintentar", color = Color.White) }
            }
        }

        else -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(recommendations.items, key = { it.videoId ?: it.title }) { item ->
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlay(item) },
                ) {
                    VideoThumbnail(item = item, corner = 12.dp)
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = item.artists,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Miniatura 16:9 con las insignias OFICIAL y duración. */
@Composable
internal fun VideoThumbnail(
    item: Track,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = item.videoId?.let { videoThumb(it) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isOfficialVideo()) {
            VideoBadge(text = "OFICIAL", modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        item.durationSeconds?.takeIf { it > 0 }?.let { seconds ->
            VideoBadge(
                text = makeTimeString(seconds * 1000L),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

@Composable
private fun VideoBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.72f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Barra fija inferior: siguiente canción de la cola; abre la cola. */
@Composable
private fun UpNextBar(
    nextTitle: String?,
    queueTitle: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .heightIn(min = 60.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "A continuación",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Text(
                    text = nextTitle ?: queueTitle ?: "Cola de reproducción",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nextTitle != null && !queueTitle.isNullOrBlank()) {
                    Text(
                        text = queueTitle,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ExpandLess,
                contentDescription = "Abrir la cola",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun VideoActionChip(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    contentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (highlighted) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label == null) 12.dp else 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (label == null) contentDescription else null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                Text(text = label, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/** Color vivo (o dominante) de la portada, animado, para el resplandor del fondo. */
@Composable
private fun rememberAmbientColor(url: String?): Color {
    var target by remember { mutableStateOf(Color(0xFF3A3A48)) }
    LaunchedEffect(url) {
        val thumbnailUrl = url ?: return@LaunchedEffect
        val rgb = runCatching {
            val palette = loadArtPalette(thumbnailUrl, 16) ?: return@runCatching null
            palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb
        }.getOrNull()
        if (rgb != null) target = Color(rgb)
    }
    val animated by animateColorAsState(targetValue = target, animationSpec = tween(600), label = "videoAmbient")
    return animated
}

/** «Auto (720p)», «1080p»… para el botón de calidad. */
private fun qualityLabel(selected: Int?, playing: Int?): String = when {
    selected != null -> "${selected}p"
    playing != null && playing > 0 -> "Auto (${playing}p)"
    else -> "Auto"
}

/**
 * Hoja M3 para elegir la calidad del videoclip: «Automática» (adapta según la conexión,
 * hasta el máximo de Ajustes) o una altura fija.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoQualitySheet(
    options: List<Int>,
    selectedHeight: Int?,
    playingHeight: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .selectableGroup(),
        ) {
            Text(
                text = "Calidad del video",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            Text(
                text = "Una calidad más alta usa más datos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            QualityRow(
                label = "Automática",
                supporting = if (playingHeight != null && playingHeight > 0) {
                    "Ahora en ${playingHeight}p · se adapta a tu conexión"
                } else {
                    "Se adapta a tu conexión"
                },
                selected = selectedHeight == null,
                onClick = { onSelect(null) },
            )
            options.forEach { height ->
                QualityRow(
                    label = "${height}p",
                    supporting = when {
                        height >= 1080 -> "Full HD"
                        height >= 720 -> "HD"
                        height >= 480 -> "Estándar"
                        else -> "Ahorro de datos"
                    },
                    selected = selectedHeight == height,
                    onClick = { onSelect(height) },
                )
            }
            if (options.isEmpty()) {
                Text(
                    text = "Este video sólo tiene una calidad disponible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(supporting) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** Aviso del progreso / resultado de «Descargar video» (sustituye a los Toasts). */
@Composable
private fun VideoDownloadStatus(
    state: VideoDownloadState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) {
        if (state is VideoDownloadState.Done || state is VideoDownloadState.Failed) {
            delay(4000)
            onClear()
        }
    }
    AnimatedVisibility(
        visible = state !is VideoDownloadState.Idle,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(250)),
        modifier = modifier,
    ) {
        val failed = state is VideoDownloadState.Failed
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            val onColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                when (state) {
                    is VideoDownloadState.Running -> {
                        Text(
                            text = state.progress?.let { "Descargando video… ${(it * 100).toInt()} %" } ?: "Preparando descarga…",
                            color = onColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = state.title,
                            color = onColor.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        val progress = state.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is VideoDownloadState.Done -> Text(state.message, color = onColor, style = MaterialTheme.typography.bodyMedium)
                    is VideoDownloadState.Failed -> Text(state.message, color = onColor, style = MaterialTheme.typography.bodyMedium)
                    VideoDownloadState.Idle -> Unit
                }
            }
        }
    }
}

/** Salta la cola a [track] (radio) y deja pedida la re-activación del modo video. */
internal fun playRelatedVideo(
    playerConnection: PlayerConnection,
    track: Track,
) {
    playerConnection.setPendingVideoMode(track.videoId)
    playerConnection.playQueue(track)
}

@Composable
private fun VideoModeErrorSnack(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    // Tiempo suficiente para leerlo y pulsar «Reintentar»; TalkBack lo anuncia al aparecer.
    LaunchedEffect(message) {
        delay(8000)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 10.dp),
                )
                TextButton(onClick = { onDismiss(); onRetry() }) {
                    Text("Reintentar", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar aviso",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
