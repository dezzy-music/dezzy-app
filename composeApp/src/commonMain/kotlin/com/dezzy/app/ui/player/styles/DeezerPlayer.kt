/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.styles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.*
import com.dezzy.app.ui.components.glass.DezzyIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.CanvasArtworkPlayer
import com.dezzy.app.ui.player.kit.ConnectDeviceBottomSheet
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.Player
import com.dezzy.app.ui.player.kit.PlayerSliderTrack
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.player.kit.SliderStyle
import com.dezzy.app.ui.player.kit.SliderStyleKey
import com.dezzy.app.ui.player.kit.StyledPlaybackSlider
import com.dezzy.app.ui.player.kit.isLandscape
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPlaybackCanvas
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deezer-style "Now Playing", faithful to the real app:
 *
 *  - FLAT pastel background: the cover's hue clamped to a light, soft band (like the real
 *    Deezer app — always bright, always black content on top). By design it is NOT wired to
 *    [PlayerBackgroundStyleKey] — the background cannot be personalized.
 *  - Cover carousel: the previous/next queue items peek at both edges; swiping changes track.
 *  - "HQ" badge + white "Letras" pill overlaid on the artwork.
 *  - share / add / heart row, times above a thin slider, centered title + "Artist - Album".
 *  - Controls: repeat · previous · plain play/pause · next · shuffle.
 *  - Bottom: output device (speaker) · "Iniciar mix de la canción" pill · queue.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeezerPlayer(
    state: BottomSheetState,
    navController: NavController,
    positionState: State<Long>,
    durationState: State<Long>,
    sliderPosition: Long?,
    onSliderPositionChange: (Long?) -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,

    onSleepTimerClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onVisualizerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    // Optimista: ver PlayerConnection.currentSongLiked.
    val currentSongLiked by playerConnection.currentSongLiked.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)

    val position by positionState
    val duration by durationState
    val currentPos = sliderPosition ?: position

    var showShareSheet by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }

    // (Dezzy 3.x bloqueaba aquí la orientación en vertical con Activity.requestedOrientation;
    // en KMP no hay equivalente común, así que no se fuerza la orientación.)

    // Global slider style + canvas support (shared pipeline with DezzyPlayer/Thumbnail).
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val canvasArtwork = rememberPlaybackCanvas(
        mediaId = mediaMetadata?.id,
        title = mediaMetadata?.title,
        artist = mediaMetadata?.artists?.firstOrNull()?.name,
        albumTitle = mediaMetadata?.album?.title,
        durationMs = duration,
    )

    // ---- Fondo plano fijo: tinte pastel claro derivado de la portada ----
    // Deezer no pinta el color dominante tal cual: conserva el matiz (hue) de la
    // portada pero lo lleva a una banda clara y suave (pastel), de modo que el
    // fondo siempre queda luminoso y el contenido negro es legible sobre
    // cualquier carátula. Clampeamos en HSL; las portadas grises/monocromas
    // quedan neutras en vez de recibir un tinte inventado.
    val defaultBg = Color(0xFFE9E7EE)
    var rawBg by remember { mutableStateOf(defaultBg) }
    LaunchedEffect(mediaMetadata?.id) {
        val url = mediaMetadata?.thumbnailUrl ?: return@LaunchedEffect
        val palette = loadArtPalette(url, 12) ?: return@LaunchedEffect
        val seed = palette.lightVibrantSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: palette.getDominantColor(defaultBg.toArgb())
        val hsl = colorToHsl(seed)
        if (hsl[1] < 0.08f) {
            hsl[1] = 0.04f
        } else {
            hsl[1] = hsl[1].coerceIn(0.30f, 0.50f)
        }
        hsl[2] = hsl[2].coerceIn(0.70f, 0.82f)
        rawBg = hslToColor(hsl)
    }
    val bgColor by animateColorAsState(rawBg, tween(600), label = "deezerBg")
    // Con la banda pastel el fondo siempre es claro → contenido negro, como Deezer.
    val onColor = if (bgColor.luminance() > 0.5f) Color.Black else Color.White
    // Variante oscura del mismo matiz para la vista de letras a pantalla completa,
    // donde el texto es blanco y un fondo pastel no funcionaría.
    val lyricsBg = remember(rawBg) {
        val hsl = colorToHsl(rawBg.toArgb())
        hsl[1] = (hsl[1] * 0.9f).coerceAtMost(0.5f)
        hsl[2] = 0.16f
        hslToColor(hsl)
    }

    // ---- Cover carousel synced with the queue ----
    val pagerState = rememberPagerState(
        initialPage = currentWindowIndex.coerceAtLeast(0),
        pageCount = { queueWindows.size.coerceAtLeast(1) }
    )
    // Follow the player: when the track changes (next/prev/auto-advance), scroll the pager.
    LaunchedEffect(currentWindowIndex, queueWindows.size) {
        if (currentWindowIndex in queueWindows.indices &&
            pagerState.currentPage != currentWindowIndex &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.animateScrollToPage(currentWindowIndex)
        }
    }
    // Follow the user: when a swipe settles on a neighbor, play that queue item.
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled in queueWindows.indices && settled != currentWindowIndex) {
            playerConnection.controller.playAt(settled)
            playerConnection.player.playWhenReady = true
        }
    }

    // Sigue la fuente global elegida en la app (p. ej. Dancing Script): el estilo
    // Deezer ya no fija la sans del sistema.
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current
    ) {
        val landscape = isLandscape()

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(bgColor)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- Top bar ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.collapseSoft() }) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Cerrar",
                        tint = onColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Playlist",
                        color = onColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = queueTitle ?: (mediaMetadata?.album?.title ?: ""),
                        color = onColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Más",
                            tint = onColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Cover carousel (scaled to screen height)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            pageSpacing = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val window = queueWindows.getOrNull(page)
                            val art = window?.mediaItem?.metadata?.thumbnailUrl
                                ?: mediaMetadata?.thumbnailUrl
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.85f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.15f))
                            ) {
                                AsyncImage(
                                    model = art,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (page == pagerState.currentPage && canvasArtwork != null) {
                                    val (canvasPrimary, canvasFallback) = canvasArtwork.playbackUrls()
                                    if (canvasPrimary != null || canvasFallback != null) {
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
                    }

                    // Right Column: Info, Slider, Controls & Actions
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = mediaMetadata?.title ?: "",
                            color = onColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${mediaMetadata?.artists?.joinToString { it.name } ?: ""} — ${mediaMetadata?.album?.title ?: ""}",
                            color = onColor.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))

                        // Share / Video / Heart
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val videoModeActive by playerConnection.videoModeActive.collectAsState()
                                val videoModeLoading by playerConnection.videoModeLoading.collectAsState()
                                IconButton(
                                    onClick = {
                                        if (videoModeActive) {
                                            playerConnection.disableVideoMode()
                                        } else if (!videoModeLoading) {
                                            playerConnection.enableVideoMode()
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SlowMotionVideo,
                                        contentDescription = "Cambiar a video",
                                        tint = if (videoModeActive) MaterialTheme.colorScheme.primary else onColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                IconButton(onClick = { playerConnection.toggleLike() }) {
                                    Icon(
                                        imageVector = if (currentSongLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Me gusta",
                                        tint = onColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Times & Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(makeTimeString(currentPos), color = onColor, fontSize = 13.sp)
                            Text(makeTimeString(duration), color = onColor, fontSize = 13.sp)
                        }
                        val sliderColors = SliderDefaults.colors(
                            thumbColor = onColor,
                            activeTrackColor = onColor,
                            inactiveTrackColor = onColor.copy(alpha = 0.25f)
                        )
                        val pendingSeekPos = remember { longArrayOf(-1L) }
                        if (sliderStyle == SliderStyle.Standard) {
                            Slider(
                                value = currentPos.toFloat(),
                                valueRange = 0f..maxOf(duration.toFloat(), 1f),
                                onValueChange = {
                                    pendingSeekPos[0] = it.toLong()
                                    onSliderPositionChange(it.toLong())
                                },
                                onValueChangeFinished = {
                                    if (pendingSeekPos[0] >= 0L) {
                                        playerConnection.player.seekTo(pendingSeekPos[0])
                                    }
                                    pendingSeekPos[0] = -1L
                                    onSliderPositionChange(null)
                                },
                                colors = sliderColors,
                                thumb = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(onColor)
                                    )
                                },
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = sliderColors,
                                        trackHeight = 3.dp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            StyledPlaybackSlider(
                                sliderStyle = sliderStyle,
                                value = currentPos.toFloat(),
                                valueRange = 0f..maxOf(duration.toFloat(), 1f),
                                onValueChange = {
                                    pendingSeekPos[0] = it.toLong()
                                    onSliderPositionChange(it.toLong())
                                },
                                onValueChangeFinished = {
                                    if (pendingSeekPos[0] >= 0L) {
                                        playerConnection.player.seekTo(pendingSeekPos[0])
                                    }
                                    pendingSeekPos[0] = -1L
                                    onSliderPositionChange(null)
                                },
                                activeColor = onColor,
                                isPlaying = isPlaying,
                                songKey = mediaMetadata?.id ?: "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled }) {
                                Icon(
                                    imageVector = if (shuffleModeEnabled) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle,
                                    contentDescription = "Aleatorio",
                                    tint = onColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = { playerConnection.seekToPrevious() }) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Anterior",
                                    tint = onColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(onColor)
                                    .clickable { playerConnection.player.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pausa",
                                    tint = bgColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(onClick = { playerConnection.seekToNext() }) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = onColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(onClick = { playerConnection.player.toggleRepeatMode() }) {
                                Icon(
                                    imageVector = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                                        Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                                        else -> Icons.Rounded.Repeat
                                    },
                                    contentDescription = "Repetir",
                                    tint = onColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ---------- Cover carousel (neighbors peek at both edges) ----------
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 34.dp),
                        pageSpacing = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val window = queueWindows.getOrNull(page)
                        val art = window?.mediaItem?.metadata?.thumbnailUrl
                            ?: mediaMetadata?.thumbnailUrl
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.15f))
                        ) {
                            AsyncImage(
                                model = art,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Animated canvas over the current cover (cropped to the square slot).
                            if (page == pagerState.currentPage && canvasArtwork != null) {
                                val (canvasPrimary, canvasFallback) = canvasArtwork.playbackUrls()
                                if (canvasPrimary != null || canvasFallback != null) {
                                    CanvasArtworkPlayer(
                                        primaryUrl = canvasPrimary,
                                        fallbackUrl = canvasFallback,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            if (page == pagerState.currentPage) {
                                // "HQ" quality badge (bottom-left), like Deezer.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF3A3A3A).copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "HQ",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // White "Letras" pill (bottom-right), like Deezer.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .clickable { onLyricsClick() }
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = "Letras",
                                        color = Color.Black,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- share · ⊕ · heart ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Cambiar a modo video: el antiguo "+" duplicaba el menú de los tres puntos.
                    val videoModeActive by playerConnection.videoModeActive.collectAsState()
                    val videoModeLoading by playerConnection.videoModeLoading.collectAsState()
                    IconButton(
                        onClick = {
                            if (videoModeActive) {
                                playerConnection.disableVideoMode()
                            } else if (!videoModeLoading) {
                                playerConnection.enableVideoMode()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SlowMotionVideo,
                            contentDescription = "Cambiar a video",
                            tint = if (videoModeActive) MaterialTheme.colorScheme.primary else onColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { playerConnection.toggleLike() }) {
                        Icon(
                            imageVector = if (currentSongLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Me gusta",
                            tint = onColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // ---------- Times above the thin slider ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(makeTimeString(currentPos), color = onColor, fontSize = 15.sp)
                Text(makeTimeString(duration), color = onColor, fontSize = 15.sp)
            }
            val sliderColors = SliderDefaults.colors(
                thumbColor = onColor,
                activeTrackColor = onColor,
                inactiveTrackColor = onColor.copy(alpha = 0.25f)
            )
            // Tap-to-seek: en un toque rápido, onValueChangeFinished puede correr
            // ANTES de la recomposición que actualiza currentPos, así que el seek
            // usaba el valor viejo (no hacía nada). El destino se guarda fuera del
            // estado de Compose para que el toque en cualquier punto de la barra
            // salte directo ahí.
            val pendingSeekPos = remember { longArrayOf(-1L) }
            if (sliderStyle == SliderStyle.Standard) {
                // Deezer-authentic thin bar when the global style is the default.
                Slider(
                    value = currentPos.toFloat(),
                    valueRange = 0f..maxOf(duration.toFloat(), 1f),
                    onValueChange = {
                        pendingSeekPos[0] = it.toLong()
                        onSliderPositionChange(it.toLong())
                    },
                    onValueChangeFinished = {
                        if (pendingSeekPos[0] >= 0L) {
                            playerConnection.player.seekTo(pendingSeekPos[0])
                        }
                        pendingSeekPos[0] = -1L
                        onSliderPositionChange(null)
                    },
                    colors = sliderColors,
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(onColor)
                        )
                    },
                    track = { sliderState ->
                        PlayerSliderTrack(
                            sliderState = sliderState,
                            colors = sliderColors,
                            trackHeight = 3.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                )
            } else {
                StyledPlaybackSlider(
                    sliderStyle = sliderStyle,
                    value = currentPos.toFloat(),
                    valueRange = 0f..maxOf(duration.toFloat(), 1f),
                    onValueChange = {
                        pendingSeekPos[0] = it.toLong()
                        onSliderPositionChange(it.toLong())
                    },
                    onValueChangeFinished = {
                        if (pendingSeekPos[0] >= 0L) {
                            playerConnection.player.seekTo(pendingSeekPos[0])
                        }
                        pendingSeekPos[0] = -1L
                        onSliderPositionChange(null)
                    },
                    activeColor = onColor,
                    isPlaying = isPlaying,
                    songKey = mediaMetadata?.id ?: "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ---------- Title + "Artist - Album", centered ----------
            Text(
                text = mediaMetadata?.title ?: "",
                color = onColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(6.dp))
            val artistLine = buildString {
                append(mediaMetadata?.artists?.joinToString { it.name } ?: "")
                mediaMetadata?.album?.title?.takeIf { it.isNotBlank() }?.let { album ->
                    if (isNotEmpty()) append(" - ")
                    append(album)
                }
            }
            Text(
                text = artistLine,
                color = onColor.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable {
                        mediaMetadata?.artists?.firstOrNull { it.id != null }?.id?.let {
                            state.collapseSoft(); navController.navigate("artist/$it")
                        }
                    }
            )

            Spacer(Modifier.height(20.dp))

            // ---------- Controls: repeat · prev · play (plain) · next · shuffle ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { playerConnection.player.toggleRepeatMode() }) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        contentDescription = "Repetir",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) onColor
                               else onColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = { playerConnection.seekToPrevious() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = onColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
                // Plain oversized glyph — Deezer has no circle behind play/pause.
                IconButton(
                    onClick = { playerConnection.player.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir/Pausar",
                        tint = onColor,
                        modifier = Modifier.size(52.dp)
                    )
                }
                IconButton(onClick = { playerConnection.seekToNext() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = onColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = {
                    playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (shuffleModeEnabled) onColor else onColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---------- Bottom: speaker · "Iniciar mix" pill · queue ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Output device — Spotify Connect / Liner / Cast picker under speaker glyph
                IconButton(onClick = { showDevicePicker = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Speaker,
                        contentDescription = "Dispositivos conectados",
                        tint = onColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = onColor.copy(alpha = 0.10f),
                    modifier = Modifier.clickable {
                        mediaMetadata?.let {
                            playerConnection.playQueue(it.track)
                        }
                    }
                ) {
                    Text(
                        text = "Iniciar mix de la canción",
                        color = onColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp)
                    )
                }
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.List,
                        contentDescription = "Cola",
                        tint = onColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (showShareSheet) {
            ShareBottomSheet(
                title = mediaMetadata?.title ?: "",
                subtitle = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                thumbnailUrl = mediaMetadata?.thumbnailUrl,
                shareLink = "https://music.youtube.com/watch?v=${mediaMetadata?.id ?: ""}",
                lyrics = lyricsEntity?.lyrics,
                mediaId = mediaMetadata?.id,
                onDismiss = { showShareSheet = false }
            )
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
}

// ── HSL a mano (equivalente a ColorUtils.colorToHSL / HSLToColor de Android) ─────────────────────

/** ARGB → [matiz 0..360, saturación 0..1, luminosidad 0..1]. */
private fun colorToHsl(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val l = (maxC + minC) / 2f
    val h: Float
    val s: Float
    if (delta == 0f) {
        h = 0f
        s = 0f
    } else {
        h = when (maxC) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        s = delta / (1f - abs(2f * l - 1f))
    }
    var hue = (h * 60f) % 360f
    if (hue < 0f) hue += 360f
    return floatArrayOf(hue, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/** [matiz 0..360, saturación 0..1, luminosidad 0..1] → Color opaco. */
private fun hslToColor(hsl: FloatArray): Color {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    val c = (1f - abs(2f * l - 1f)) * s
    val m = l - c / 2f
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val (r1, g1, b1) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
    )
}
