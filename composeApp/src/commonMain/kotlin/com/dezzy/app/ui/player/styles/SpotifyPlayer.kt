/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.Track
import com.dezzy.app.ui.components.glass.DezzyGlassSurfaceButton
import com.dezzy.app.ui.components.glass.DezzyIconButton as IconButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton as OutlinedButton
import com.dezzy.app.ui.player.kit.AddToPlaylistDialog
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.ConnectDeviceBottomSheet
import com.dezzy.app.ui.player.kit.ConnectState
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.Player
import com.dezzy.app.ui.player.kit.PlayerSliderTrack
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.player.kit.SliderStyle
import com.dezzy.app.ui.player.kit.SliderStyleKey
import com.dezzy.app.ui.player.kit.SongCredits
import com.dezzy.app.ui.player.kit.SongCreditsResolver
import com.dezzy.app.ui.player.kit.StyledPlaybackSlider
import com.dezzy.app.ui.player.kit.CanvasArtworkPlayer
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.parseLyrics
import com.dezzy.app.ui.player.kit.rememberCastingRouteName
import com.dezzy.app.ui.player.kit.rememberDesktopLink
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPhoneDeviceName
import com.dezzy.app.ui.player.kit.rememberPlaybackCanvas
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyCardColor = Color(0xFF1F1F1F)

/** Color de las tarjetas del player Spotify, derivado de la paleta de la portada. */
private val LocalSpotifyCardColor = androidx.compose.runtime.compositionLocalOf { SpotifyCardColor }

/** RGB (ARGB Int) a HSL: [0] tono 0..360, [1] saturación 0..1, [2] luminosidad 0..1. */
private fun colorToHsl(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var h: Float
    val s: Float
    val l = (max + min) / 2f
    if (max == min) {
        h = 0f
        s = 0f
    } else {
        h = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        s = delta / (1f - abs(2f * l - 1f))
    }
    h = (h * 60f) % 360f
    if (h < 0f) h += 360f
    return floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

/** HSL a color opaco. */
private fun hslToColor(hsl: FloatArray): Color {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    val c = (1f - abs(2f * l - 1f)) * s
    val m = l - 0.5f * c
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val (r0, g0, b0) = when (h.toInt() / 60) {
        0 -> Triple(c + m, x + m, m)
        1 -> Triple(x + m, c + m, m)
        2 -> Triple(m, c + m, x + m)
        3 -> Triple(m, x + m, c + m)
        4 -> Triple(x + m, m, c + m)
        else -> Triple(c + m, m, x + m)
    }
    return Color(
        red = (r0.coerceIn(0f, 1f) * 255f).roundToInt() / 255f,
        green = (g0.coerceIn(0f, 1f) * 255f).roundToInt() / 255f,
        blue = (b0.coerceIn(0f, 1f) * 255f).roundToInt() / 255f,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpotifyPlayer(
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
    val currentSongLiked = currentSong?.song?.liked == true

    val position by positionState
    val duration by durationState
    val currentPos = sliderPosition ?: position

    val scrollState = rememberScrollState()

    val isLocalSong = mediaMetadata?.id?.startsWith("local_") == true
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val linerManager = rememberDesktopLink()
    val linerConnState by linerManager.connectionState.collectAsState()
    val linerDeviceName by linerManager.connectedDeviceName.collectAsState()
    val castingRouteName = rememberCastingRouteName()
    val phoneDeviceName = rememberPhoneDeviceName()
    val isConnectedToDesktop = linerConnState == ConnectState.CONNECTED || castingRouteName != null
    val activeDeviceName = if (castingRouteName != null) {
        castingRouteName
    } else if (linerConnState == ConnectState.CONNECTED) {
        linerDeviceName ?: "Liner Desktop"
    } else {
        phoneDeviceName
    }
    var showDevicePicker by remember { mutableStateOf(false) }

    val pageCount = queueWindows.size.coerceAtLeast(1)
    val artworkPagerState = rememberPagerState(
        initialPage = if (currentWindowIndex in 0 until pageCount) currentWindowIndex else 0,
        pageCount = { pageCount }
    )

    LaunchedEffect(currentWindowIndex, queueWindows.size) {
        if (currentWindowIndex in 0 until queueWindows.size &&
            artworkPagerState.currentPage != currentWindowIndex &&
            !artworkPagerState.isScrollInProgress
        ) {
            artworkPagerState.scrollToPage(currentWindowIndex)
        }
    }

    LaunchedEffect(artworkPagerState, queueWindows) {
        snapshotFlow { artworkPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != currentWindowIndex && page in 0 until queueWindows.size) {
                    playerConnection.controller.playAt(page)
                }
            }
    }

    // ---- Synced lyrics (live line + preview) ----
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyricsText = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }
    val lyricEntries = remember(lyricsText) {
        if (lyricsText != null && lyricsText != LYRICS_NOT_FOUND) {
            runCatching { parseLyrics(lyricsText) }.getOrNull()
        } else null
    }
    val isSynced = remember(lyricsText) {
        lyricsText != null && lyricsText != LYRICS_NOT_FOUND && lyricsText.startsWith("[")
    }
    val currentLineIndex = if (isSynced && lyricEntries != null && lyricEntries.isNotEmpty()) {
        lyricEntries.indexOfLast { it.time <= currentPos }.coerceAtLeast(0)
    } else -1
    val liveLyricLine = lyricEntries?.getOrNull(currentLineIndex)?.text?.takeIf { it.isNotBlank() }

    // ---- Artist page (About / Explore / Credits) ----
    val rawArtistId = mediaMetadata?.artists?.firstOrNull { it.id != null }?.id
    val primaryArtistName = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty()
    var resolvedArtistId by remember(rawArtistId, primaryArtistName) { mutableStateOf(rawArtistId) }
    val primaryArtistId = resolvedArtistId ?: rawArtistId
    var artistPage by remember(primaryArtistId, primaryArtistName) { mutableStateOf<Artist?>(null) }
    LaunchedEffect(rawArtistId, primaryArtistName) {
        val idToTry = rawArtistId ?: if (primaryArtistName.isNotBlank()) {
            runCatching {
                playerConnection.client.findArtistId(primaryArtistName).getOrNull()
            }.getOrNull()
        } else null

        resolvedArtistId = idToTry
        if (idToTry != null) {
            playerConnection.client.getArtist(idToTry).onSuccess { page -> artistPage = page }
        }
    }

    // ---- Créditos REALES (MusicBrainz: compositores y productores) ----
    var songCredits by remember(mediaMetadata?.id) { mutableStateOf<SongCredits?>(null) }
    LaunchedEffect(mediaMetadata?.id) {
        val meta = mediaMetadata ?: return@LaunchedEffect
        if (meta.id.startsWith("local_")) return@LaunchedEffect
        songCredits = SongCreditsResolver.resolve(
            mediaId = meta.id,
            title = meta.title,
            artist = meta.artists.firstOrNull()?.name.orEmpty(),
        )
    }


    // Canvas de la canción: decide si aplicamos el degradado VIVO de Spotify (solo
    // cuando NO hay canvas; las que tienen canvas se dejan exactamente como estaban).
    val canvasArtwork = rememberPlaybackCanvas(
        mediaId = mediaMetadata?.id,
        title = mediaMetadata?.title,
        artist = mediaMetadata?.artists?.firstOrNull()?.name,
        albumTitle = mediaMetadata?.album?.title,
        durationMs = duration,
    )
    val (canvasPrimary, canvasFallback) = canvasArtwork?.playbackUrls() ?: (null to null)
    val canvasAvailable = canvasPrimary != null || canvasFallback != null
    // «Silenciar canvas» (boceto B): se vuelve a la carátula hasta que se reactive.
    var canvasMuted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val hasCanvas = canvasAvailable && !canvasMuted
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // ---- Colores dinámicos de la carátula (como Spotify): fondo con la paleta y
    //      tarjetas de un tono distinto pero relacionado ----
    var targetDominantColor by remember { mutableStateOf(Color(0xFF242424)) }
    // Fondo inferior (al hacer scroll) y color de las tarjetas, ambos derivados
    // de la paleta pero más oscuros/desaturados; las tarjetas van un poco más
    // claras que el fondo para destacar sobre él.
    var targetBgBottomColor by remember { mutableStateOf(Color.Black) }
    var targetCardColor by remember { mutableStateOf(Color(0xFF1F1F1F)) }
    // Tarjeta de letras: tono más vibrante/claro derivado del álbum (como Spotify).
    var targetLyricsCardColor by remember { mutableStateOf(Color(0xFF2A2A2A)) }

    val dominantColor by animateColorAsState(
        targetValue = targetDominantColor,
        animationSpec = tween(durationMillis = 500),
        label = "dominantColor"
    )
    val bgBottomColor by animateColorAsState(
        targetValue = targetBgBottomColor,
        animationSpec = tween(durationMillis = 500),
        label = "bgBottomColor"
    )
    val cardColor by animateColorAsState(
        targetValue = targetCardColor,
        animationSpec = tween(durationMillis = 500),
        label = "cardColor"
    )
    val lyricsCardColor by animateColorAsState(
        targetValue = targetLyricsCardColor,
        animationSpec = tween(durationMillis = 500),
        label = "lyricsCardColor"
    )

    val fallbackColor = Color(0xFF242424).toArgb()
    LaunchedEffect(mediaMetadata?.id, hasCanvas) {
        val thumbnailUrl = mediaMetadata?.thumbnailUrl ?: return@LaunchedEffect
        val palette = loadArtPalette(thumbnailUrl, 16)
        if (palette != null) {
            val topBg: Color
            val bottomBg: Color
            val card: Color
            val lyricsCard: Color
            if (hasCanvas) {
                // CON canvas: se deja EXACTAMENTE como estaba (dominante, apagado).
                val dominant = palette.getDominantColor(fallbackColor)
                val hsl = colorToHsl(dominant)
                topBg = hslToColor(floatArrayOf(hsl[0], (hsl[1] * 0.85f).coerceIn(0.15f, 0.6f), 0.30f))
                bottomBg = Color.Black
                card = hslToColor(floatArrayOf(hsl[0], (hsl[1] * 0.45f).coerceIn(0.06f, 0.30f), 0.155f))
                lyricsCard = hslToColor(floatArrayOf(hsl[0], (hsl[1] * 0.65f).coerceIn(0.12f, 0.50f), 0.20f))
            } else {
                // SIN canvas: degradado VIVO como Spotify (color vibrante de la portada).
                val base = palette.dominantSwatch?.rgb
                    ?: palette.vibrantSwatch?.rgb
                    ?: fallbackColor
                val hsl = colorToHsl(base)
                // Ajuste de saturación/luminancia para evitar fondos sobreexpuestos
                hsl[1] = hsl[1].coerceAtMost(0.6f)
                hsl[2] = hsl[2].coerceIn(0.1f, 0.4f)
                val srcSat = hsl[1]
                topBg = hslToColor(hsl)
                // El degradado acaba en negro puro: las tarjetas se ven sobre #000.
                bottomBg = Color.Black
                card = hslToColor(
                    floatArrayOf(hsl[0], if (srcSat < 0.12f) srcSat else (srcSat * 0.45f).coerceIn(0.06f, 0.35f), 0.16f)
                )
                // Lyrics card: más saturada y luminosa que la card genérica (como Spotify).
                lyricsCard = hslToColor(
                    floatArrayOf(hsl[0], if (srcSat < 0.12f) srcSat else (srcSat * 0.7f).coerceIn(0.18f, 0.55f), 0.22f)
                )
            }
            targetDominantColor = topBg
            targetBgBottomColor = bottomBg
            targetCardColor = card
            targetLyricsCardColor = lyricsCard
        }
    }
    var showShareSheet by remember { mutableStateOf(false) }

    // Global slider style (canvas ya calculado arriba).
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)

    val windowSize = LocalWindowInfo.current.containerSize

    // El bloqueo de orientación vertical (Activity.requestedOrientation) es de Android y no se porta
    // aquí; el reproductor se pinta siempre en vertical.

    val isLandscape = false

    // Show the compact sticky header once the user scrolls past the controls.
    val showMiniHeader by remember { derivedStateOf { scrollState.value > 700 } }

    // Follow the app's selected font (Type.kt applies FontFamilyKey to the Typography) so this
    // player matches the rest of the app instead of the platform default.
    val appFontFamily = MaterialTheme.typography.bodyLarge.fontFamily
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = appFontFamily),
        LocalSpotifyCardColor provides cardColor,
    ) {
    val screenHeightDp = with(LocalDensity.current) { windowSize.height.toDp() }
    var artworkDragTotal by remember { mutableStateOf(0f) }

    // El canvas de fondo se DESVANECE al hacer scroll (como Spotify): al bajar,
    // deja ver el fondo con la paleta y las tarjetas.
    val canvasFadePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (screenHeightDp * 0.5f).toPx()
    }
    val canvasAlpha by remember(canvasFadePx) {
        derivedStateOf { (1f - scrollState.value / canvasFadePx).coerceIn(0f, 1f) }
    }
    val swipeToChangeSong = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { artworkDragTotal = 0f },
            onDragEnd = {
                when {
                    artworkDragTotal < -120f -> playerConnection.seekToNext()
                    artworkDragTotal > 120f -> playerConnection.seekToPrevious()
                }
                artworkDragTotal = 0f
            },
            onHorizontalDrag = { _, dragAmount -> artworkDragTotal += dragAmount },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to dominantColor,
                    0.62f to androidx.compose.ui.graphics.lerp(dominantColor, bgBottomColor, 0.7f),
                    0.86f to bgBottomColor,
                )
            )
    ) {
        // ---------- Blur sutil de la carátula (como Spotify real) ----------
        // Capa nebulosa de la portada difuminada debajo del degradado, solo
        // cuando NO hay canvas. Da profundidad visual al fondo.
        if (!hasCanvas && mediaMetadata?.thumbnailUrl != null) {
            AsyncImage(
                model = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .graphicsLayer { alpha = 0.15f }
            )
        }
        // ---------- Canvas a PANTALLA COMPLETA (como Spotify real) ----------
        // El video del canvas cubre toda la pantalla detrás de los controles, en
        // vez de quedar encajado en el recuadro de la carátula. Un scrim arriba y
        // abajo mantiene legibles la cabecera y los controles. Se desvanece al
        // hacer scroll (canvasAlpha) para dejar ver el fondo con la paleta.
        if (hasCanvas && !isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = canvasAlpha }
                    .then(swipeToChangeSong)
            ) {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimary,
                    fallbackUrl = canvasFallback,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.28f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.88f),
                            )
                        )
                )
            }
        }
        // Al bajar, todo lo que rodea a las tarjetas pasa a negro puro (las tarjetas
        // conservan su color; la cabecera fija mantiene el de la carátula).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - canvasAlpha }
                .background(Color.Black)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- Header ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.collapseSoft() }) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Cerrar",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val albumTitle = mediaMetadata?.album?.title
                    val source = queueTitle
                    val sourceLabel = when {
                        source == null || source == albumTitle -> if (albumTitle != null) "ÁLBUM" else "REPRODUCIENDO DESDE"
                        source.contains("radio", ignoreCase = true) -> "RADIO"
                        else -> "PLAYLIST"
                    }
                    Text(
                        text = sourceLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = queueTitle ?: (mediaMetadata?.album?.title ?: ""),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Más",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---------- Artwork/Controls Layout ----------
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Artwork / Canvas (fits screen height)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.88f)
                                .aspectRatio(1f)
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(8.dp), clip = false)
                                .clip(RoundedCornerShape(8.dp))
                                .then(swipeToChangeSong)
                        ) {
                            AsyncImage(
                                model = mediaMetadata?.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (hasCanvas) {
                                CanvasArtworkPlayer(
                                    primaryUrl = canvasPrimary,
                                    fallbackUrl = canvasFallback,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    // Right Column: Info + Controls + Actions
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Title + saved check + artist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mediaMetadata?.title ?: "",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        (resolvedArtistId ?: primaryArtistName.takeIf { it.isNotBlank() })?.let {
                                            state.collapseSoft(); navController.navigate("artist/$it")
                                        }
                                    }
                                )
                            }
                            AddToPlaylistButton { showAddToPlaylist = true }
                            SavedCheck(currentSongLiked) { playerConnection.toggleLike() }
                        }

                        if (liveLyricLine != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = liveLyricLine,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onLyricsClick() }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Slider
                        SpotifySeekBar(
                            position = currentPos,
                            duration = duration,
                            onSliderPositionChange = onSliderPositionChange,
                            onSeek = { playerConnection.player.seekTo(it) },
                            sliderStyle = sliderStyle,
                            isPlaying = isPlaying,
                            songKey = mediaMetadata?.id ?: "",
                        )

                        Spacer(Modifier.height(8.dp))

                        // Transport Controls
                        SpotifyControls(
                            isPlaying = isPlaying,
                            shuffleModeEnabled = shuffleModeEnabled,
                            repeatMode = repeatMode,
                            onShuffle = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                            onPrevious = { playerConnection.seekToPrevious() },
                            onTogglePlay = { playerConnection.player.togglePlayPause() },
                            onNext = { playerConnection.seekToNext() },
                            onRepeat = { playerConnection.player.toggleRepeatMode() }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Bottom actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            run {
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
                                        tint = if (videoModeActive) Color(0xFF1DB954) else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onSleepTimerClick) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = "Temporizador",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(onClick = onQueueClick) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = "Cola",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ---------- Artwork with HorizontalPager swipe-to-skip ----------
                if (hasCanvas && !isLandscape) {
                    Spacer(Modifier.height(screenHeightDp * 0.40f))
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (queueWindows.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(12.dp), clip = false)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = mediaMetadata?.thumbnailUrl,
                                    contentDescription = mediaMetadata?.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            HorizontalPager(
                                state = artworkPagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val itemMeta = queueWindows.getOrNull(page)?.mediaItem?.metadata ?: mediaMetadata
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp)
                                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(12.dp), clip = false)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = itemMeta?.thumbnailUrl ?: mediaMetadata?.thumbnailUrl,
                                        contentDescription = itemMeta?.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ---------- Title + saved check + artist ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Con canvas no hay carátula grande: mini carátula junto al título (boceto B).
                    if (hasCanvas) {
                        AsyncImage(
                            model = mediaMetadata?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata?.title ?: "",
                            color = Color.White,
                            fontSize = if (hasCanvas) 22.sp else 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                (resolvedArtistId ?: primaryArtistName.takeIf { it.isNotBlank() })?.let {
                                    state.collapseSoft(); navController.navigate("artist/$it")
                                }
                            }
                        )
                    }
                    AddToPlaylistButton { showAddToPlaylist = true }
                    SavedCheck(currentSongLiked) { playerConnection.toggleLike() }
                }

                // Letra en vivo bajo el artista (boceto A): una línea, toca para abrir la letra.
                if (liveLyricLine != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "♪ $liveLyricLine",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLyricsClick() }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ---------- Slider + times ----------
                SpotifySeekBar(
                    position = currentPos,
                    duration = duration,
                    onSliderPositionChange = onSliderPositionChange,
                    onSeek = { playerConnection.player.seekTo(it) },
                    sliderStyle = sliderStyle,
                    isPlaying = isPlaying,
                    songKey = mediaMetadata?.id ?: "",
                )

                Spacer(Modifier.height(12.dp))

                // ---------- Controls ----------
                SpotifyControls(
                    isPlaying = isPlaying,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    onShuffle = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                    onPrevious = { playerConnection.seekToPrevious() },
                    onTogglePlay = { playerConnection.player.togglePlayPause() },
                    onNext = { playerConnection.seekToNext() },
                    onRepeat = { playerConnection.player.toggleRepeatMode() }
                )

                Spacer(Modifier.height(16.dp))

                // ---------- Bottom actions: Spotify Connect active device + queue ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Spotify Connect device indicator & picker
                    Surface(
                        onClick = { showDevicePicker = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isConnectedToDesktop) SpotifyGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        contentColor = if (isConnectedToDesktop) SpotifyGreen else Color.White
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (castingRouteName != null) Icons.Rounded.CastConnected else Icons.Rounded.Devices,
                                contentDescription = "Conectar a un dispositivo",
                                tint = if (isConnectedToDesktop) SpotifyGreen else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = activeDeviceName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canvasAvailable) {
                            IconButton(onClick = { canvasMuted = !canvasMuted }) {
                                Icon(
                                    imageVector = if (canvasMuted) Icons.Rounded.SlowMotionVideo else Icons.AutoMirrored.Rounded.VolumeOff,
                                    contentDescription = if (canvasMuted) "Mostrar el canvas" else "Silenciar el canvas",
                                    tint = if (canvasMuted) SpotifyGreen else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        run {
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
                                    tint = if (videoModeActive) SpotifyGreen else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(onClick = onSleepTimerClick) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = "Temporizador",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onQueueClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Cola",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // ---------- Cards ----------
            queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata?.let { next ->
                NextInQueueCard(
                    title = next.title,
                    artist = next.artists.joinToString { it.name },
                    thumbnailUrl = next.thumbnailUrl,
                    cardColor = cardColor,
                    onOpenQueue = onQueueClick,
                )
                Spacer(Modifier.height(16.dp))
            }
            if (isLocalSong) {
                LocalSongCard()
            } else {
                // Lyrics preview
                if (lyricEntries != null && lyricEntries.any { it.text.isNotBlank() }) {
                    LyricsPreviewCard(
                        entries = lyricEntries.map { it.text },
                        synced = isSynced,
                        currentLineIndex = currentLineIndex,
                        lyricsCardColor = lyricsCardColor,
                        onShowLyrics = { onLyricsClick() },
                        onShareLyrics = { showShareSheet = true },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // Créditos y SongDNA en una sola tarjeta (boceto C).
                val creditArtists = mediaMetadata?.artists?.filter { it.name.isNotBlank() }.orEmpty()
                if (creditArtists.isNotEmpty() || songCredits != null) {
                    CreditsDnaCard(
                        songTitle = mediaMetadata?.title.orEmpty(),
                        artists = creditArtists.map { it.name to it.id },
                        credits = songCredits,
                        cardColor = cardColor,
                        onExplore = primaryArtistId?.let { id ->
                            {
                                state.collapseSoft()
                                navController.navigate("songdna/$id")
                            }
                        },
                        onArtistClick = { id ->
                            id?.let { state.collapseSoft(); navController.navigate("artist/$it") }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // About the artist
                artistPage?.let { artist ->
                    AboutArtistCard(
                        artist = artist,
                        scrollState = scrollState,
                        onOpenArtist = {
                            state.collapseSoft()
                            navController.navigate("artist/${artist.id}")
                        }
                    )
                    Spacer(Modifier.height(16.dp))

                    // Explore
                    val exploreSection = artist.shelves.firstOrNull { it.items.isNotEmpty() }
                    if (exploreSection != null) {
                        ExploreCard(
                            title = "Explora ${artist.name}",
                            items = exploreSection.items,
                            currentId = mediaMetadata?.id,
                            onItemClick = { item ->
                                when (item.type) {
                                    MusicItemType.SONG, MusicItemType.VIDEO -> playerConnection.playQueue(
                                        Track(
                                            videoId = item.id,
                                            title = item.title,
                                            artists = item.subtitle,
                                            thumbnail = item.thumbnail,
                                        )
                                    )
                                    MusicItemType.ALBUM -> {
                                        state.collapseSoft(); navController.navigate("album/${item.id}")
                                    }
                                    MusicItemType.ARTIST -> {
                                        state.collapseSoft(); navController.navigate("artist/${item.id}")
                                    }
                                    MusicItemType.PLAYLIST -> {
                                        state.collapseSoft(); navController.navigate("online_playlist/${item.id}")
                                    }
                                    MusicItemType.OTHER -> Unit
                                }
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

            }

            Spacer(Modifier.height(48.dp))
        }

        // ---------- Sticky mini-header ----------
        AnimatedVisibility(
            visible = showMiniHeader,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dominantColor.copy(alpha = 0.98f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini artwork (like real Spotify sticky header)
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaMetadata?.title ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SavedCheck(currentSongLiked) { playerConnection.toggleLike() }
                IconButton(onClick = { playerConnection.player.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            // Progreso de la canción pegado al borde inferior de la cabecera.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(
                        if (duration > 0L) (currentPos.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    )
                    .height(2.dp)
                    .background(Color.White)
            )
            }
        }
    }

    // ---------- Share card (Spotify-style) ----------
    if (showShareSheet) {
        ShareBottomSheet(
            title = mediaMetadata?.title ?: "",
            subtitle = mediaMetadata?.artists?.joinToString { it.name } ?: "",
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            shareLink = "https://music.youtube.com/watch?v=${mediaMetadata?.id ?: ""}",
            lyrics = playerConnection.currentLyrics.collectAsState(initial = null).value?.lyrics,
            mediaId = mediaMetadata?.id,
            onDismiss = { showShareSheet = false }
        )
    }

    mediaMetadata?.let { meta ->
        AddToPlaylistDialog(
            isVisible = showAddToPlaylist,
            track = meta.track,
            onDismiss = { showAddToPlaylist = false },
            onAddComplete = { playlistNames ->
                val message = if (playlistNames.size == 1) {
                    "Añadida a ${playlistNames.first()}"
                } else {
                    "Añadida a ${playlistNames.size} listas"
                }
                playerConnection.toast(message)
            },
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

/** «Siguiente en la cola» (boceto C): la próxima canción; toca para abrir la cola. */
@Composable
private fun NextInQueueCard(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    cardColor: Color,
    onOpenQueue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .clickable(onClick = onOpenQueue)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Siguiente en la cola",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Abrir cola",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SavedCheck(liked: Boolean, onClick: () -> Unit) {
    if (liked) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SpotifyGreen)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Guardado",
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Rounded.FavoriteBorder,
                contentDescription = "Guardar",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifySeekBar(
    position: Long,
    duration: Long,
    onSliderPositionChange: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    sliderStyle: SliderStyle = SliderStyle.Standard,
    isPlaying: Boolean = false,
    songKey: String = "",
) {
    // Spotify's bar: a thin (4dp) rounded track with a small round thumb.
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        if (sliderStyle == SliderStyle.Standard) {
            // Barra fina auténtica de Spotify cuando el estilo global es el estándar.
            Slider(
                value = position.toFloat(),
                valueRange = 0f..maxOf(duration.toFloat(), 1f),
                onValueChange = { onSliderPositionChange(it.toLong()) },
                onValueChangeFinished = {
                    onSeek(position)
                    onSliderPositionChange(null)
                },
                colors = sliderColors,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = sliderColors,
                        trackHeight = 4.dp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            StyledPlaybackSlider(
                sliderStyle = sliderStyle,
                value = position.toFloat(),
                valueRange = 0f..maxOf(duration.toFloat(), 1f),
                onValueChange = { onSliderPositionChange(it.toLong()) },
                onValueChangeFinished = {
                    onSeek(position)
                    onSliderPositionChange(null)
                },
                activeColor = Color.White,
                isPlaying = isPlaying,
                songKey = songKey,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(makeTimeString(position), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Text(makeTimeString(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpotifyControls(
    isPlaying: Boolean,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (shuffleModeEnabled) SpotifyGreen else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (shuffleModeEnabled) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(SpotifyGreen))
            } else {
                Spacer(Modifier.size(4.dp))
            }
        }
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        DezzyGlassSurfaceButton(
            onClick = onTogglePlay,
            shape = CircleShape,
            normalContainerColor = Color.White,
            normalContentColor = Color.Black,
            prominent = true,
            glassTintColor = SpotifyGreen,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Reproducir/Pausar",
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        // Repeat: off → all → one, tinted green while active (faithful to Spotify).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onRepeat) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    contentDescription = "Repetir",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (repeatMode != Player.REPEAT_MODE_OFF) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(SpotifyGreen))
            } else {
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun LyricsPreviewCard(
    entries: List<String>,
    synced: Boolean,
    currentLineIndex: Int,
    lyricsCardColor: Color,
    onShowLyrics: () -> Unit,
    onShareLyrics: () -> Unit = {},
) {
    // Pick a small window of lines: around the current line if synced, else the first lines.
    val nonBlank = entries.withIndex().filter { it.value.isNotBlank() }
    val window = if (synced && currentLineIndex >= 0) {
        nonBlank.filter { it.index in currentLineIndex..(currentLineIndex + 4) }
            .ifEmpty { nonBlank.take(5) }
    } else {
        nonBlank.take(5)
    }.map { it.value }

    // Graduated opacities: active line = full white, fading for subsequent lines.
    val lineAlphas = floatArrayOf(1.0f, 0.7f, 0.5f, 0.35f, 0.25f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(lyricsCardColor)
            .clickable(onClick = onShowLyrics)
            .padding(20.dp)
    ) {
        // Header: title + share/expand icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Letras",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onShareLyrics,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Compartir letras",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Crossfade: las líneas se desvanecen suavemente al cambiar (como Spotify).
        AnimatedContent(
            targetState = window,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "lyricsCrossfade"
        ) { currentWindow ->
            Column {
                currentWindow.forEachIndexed { i, line ->
                    val alpha = lineAlphas.getOrElse(i) { 0.2f }
                    Text(
                        text = line,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = 22.sp,
                        fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.clickable(onClick = onShowLyrics)
        ) {
            Text(
                text = "Mostrar la letra",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AboutArtistCard(artist: Artist, scrollState: androidx.compose.foundation.ScrollState, onOpenArtist: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val listeners = artist.listeners
    val description = artist.description

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF181818))
            .clickable(onClick = onOpenArtist)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    // Parallax: la imagen se desplaza a 30% de la velocidad del scroll
                    .graphicsLayer { translationY = scrollState.value * 0.3f }
            )
            // Scrim for text legibility over the hero image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                            )
                        )
                    )
            )
            Text(
                text = "Acerca del artista",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artist.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onOpenArtist,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Seguir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (listeners != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listeners,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!expanded) {
                    Text(
                        text = "ver más",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { expanded = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreCard(
    title: String,
    items: List<MusicItem>,
    currentId: String?,
    onItemClick: (MusicItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalSpotifyCardColor.current)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items.take(12)) { item ->
                Box(
                    modifier = Modifier
                        .size(width = 130.dp, height = 190.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .clickable { onItemClick(item) }
                ) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditRow(
    name: String,
    role: String,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = role,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

// ── Créditos y SongDNA ───────────────────────────────────────────────────────

/** ⊕ junto al check: añade la canción a una playlist. */
@Composable
private fun AddToPlaylistButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.AddCircle,
            contentDescription = "Añadir a una playlist",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * «Créditos y SongDNA» (boceto C): las personas detrás de la canción en una línea,
 * «Explorar» abre SongDNA y «Ver créditos» despliega intérpretes, compositores y productores.
 */
@Composable
private fun CreditsDnaCard(
    songTitle: String,
    artists: List<Pair<String, String?>>,
    credits: SongCredits?,
    cardColor: Color,
    onExplore: (() -> Unit)?,
    onArtistClick: (String?) -> Unit,
) {
    var expanded by remember(songTitle) { mutableStateOf(false) }
    val artistNames = artists.map { it.first.lowercase() }.toSet()
    val writers = credits?.writers?.filter { it.lowercase() !in artistNames || credits.producers.isEmpty() }.orEmpty()
    val producers = credits?.producers.orEmpty()
    val people = (artists.map { it.first } + writers + producers).distinctBy { it.lowercase() }
    val summary = when {
        people.size <= 2 -> people.joinToString(", ")
        else -> "${people.take(2).joinToString(", ")} y ${people.size - 2} más"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Créditos y SongDNA", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = songTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onExplore != null) {
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onExplore,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Explorar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            artists.forEachIndexed { index, (name, id) ->
                CreditRow(
                    name = name,
                    role = if (index == 0) "Artista principal" else "Artista",
                    clickable = id != null,
                    onClick = { onArtistClick(id) },
                )
            }
            writers.forEach { CreditRow(name = it, role = "Compositor", clickable = false, onClick = {}) }
            producers.forEach { CreditRow(name = it, role = "Productor", clickable = false, onClick = {}) }
            if (credits != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Fuente: ${credits.source}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (expanded) "Ocultar créditos" else "Ver todos los créditos",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
        )
    }
}


@Composable
private fun LocalSongCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Música Local", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Esta pista se reproduce desde tu almacenamiento local.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}
