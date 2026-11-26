/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

/*
 * Dezzy Style: tornamesa de vinilo (inspirada en MDVinyl, superándola):
 *  - Tema y textura del vinilo DINÁMICOS desde la paleta de la carátula
 *    (además de los temas fijos y el shuffle por canción).
 *  - Gestos sobre el vinilo: tocar = play/pausa, deslizar horizontal =
 *    canción anterior/siguiente, mantener presionado = guardar el vinilo
 *    con su carátula como imagen en Imágenes/Dezzy Vinyl.
 *  - Velocidad de giro configurable. El disco y el brazo se animan con la
 *    reproducción; el splatter es determinista por canción.
 */

package com.dezzy.app.ui.player.styles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import com.dezzy.app.ui.components.glass.DezzyFilterChip as FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.dezzy.app.ui.components.glass.DezzyTextButton as TextButton
import com.dezzy.app.ui.components.glass.DezzyGlassSurfaceButton
import com.dezzy.app.ui.theme.isCustomStyledTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import coil3.compose.AsyncImage
import com.dezzy.app.ui.player.kit.SongVideoToggle
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.CanvasArtwork
import com.dezzy.app.ui.player.kit.DevicesButton
import com.dezzy.app.ui.player.kit.DezzyVinylSpeedKey
import com.dezzy.app.ui.player.kit.DezzyVinylStyleKey
import com.dezzy.app.ui.player.kit.DezzyVinylThemeKey
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.player.kit.isLandscape
import com.dezzy.app.ui.player.kit.loadArtPalette
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberPlaybackCanvas
import com.dezzy.app.ui.player.kit.rememberPreference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

// ── Temas de color fijos (como los de MDVinyl); "dynamic" se genera aparte ──

private data class VinylTheme(
    val key: String,
    val background: Color,
    val content: Color,
    val subtle: Color,
    val button: Color,
)

private val VinylThemes = listOf(
    VinylTheme("cream", Color(0xFFEFECE3), Color(0xFF141414), Color(0xFF8C8A82), Color(0xFFF8F6EF)),
    VinylTheme("orange", Color(0xFFF0A422), Color(0xFF1A1206), Color(0xFF6E5312), Color(0xFFF6BC55)),
    VinylTheme("gray", Color(0xFF555555), Color(0xFFF2F2F2), Color(0xFFBBBBBB), Color(0xFF676767)),
    VinylTheme("silver", Color(0xFFC9CBCD), Color(0xFF17181A), Color(0xFF5F6265), Color(0xFFDCDEE0)),
    VinylTheme("black", Color(0xFF151515), Color(0xFFF2F2F2), Color(0xFF9A9A9A), Color(0xFF242424)),
)

// ── Estilos de vinilo fijos; "dynamic" se tiñe con la carátula ──

private data class VinylStyleSpec(
    val key: String,
    val base: Color,
    val groove: Color,
    val splat: Color,
    val splatter: Boolean,
)

private val VinylStyles = listOf(
    VinylStyleSpec("classic", Color(0xFF17171A), Color(0xFFFFFFFF).copy(alpha = 0.06f), Color.Transparent, false),
    VinylStyleSpec("gray", Color(0xFF9B9B9B), Color(0xFF000000).copy(alpha = 0.09f), Color.Transparent, false),
    VinylStyleSpec("splatter_light", Color(0xFFE9EDE7), Color(0xFF000000).copy(alpha = 0.05f), Color(0xFF14140F), true),
    VinylStyleSpec("splatter_gray", Color(0xFF8E8E8E), Color(0xFF000000).copy(alpha = 0.07f), Color(0xFFF4F4F4), true),
)

private data class Splat(
    val angle: Float,
    val startFrac: Float,
    val lenFrac: Float,
    val widthPx: Float,
    val alpha: Float,
)

/** Splatter determinista por canción: la misma canción siempre "mancha" igual. */
private fun generateSplats(seed: Int, spec: VinylStyleSpec, count: Int = 150): List<Splat> {
    if (!spec.splatter) return emptyList()
    val rnd = Random(seed)
    return List(count) {
        Splat(
            angle = rnd.nextFloat() * 360f,
            startFrac = 0.30f + rnd.nextFloat() * 0.60f,
            lenFrac = 0.04f + rnd.nextFloat() * 0.28f,
            widthPx = 2f + rnd.nextFloat() * 9f,
            alpha = 0.5f + rnd.nextFloat() * 0.5f,
        )
    }
}

private val VinylSpeeds = listOf(
    "slow" to ("Lenta" to 0.55f),
    "normal" to ("Normal" to 1f),
    "fast" to ("Rápida" to 1.7f),
)

/** Veces que se ha usado el brazo; el aviso «Arrastra el brazo» se oculta tras 3. */
private val VinylArmHintCountKey = intPreferencesKey("dezzy_vinyl_arm_hint_count")

/** Indicador de RPM (boceto C) según la velocidad de giro elegida. */
private fun rpmLabel(speedKey: String) = when (speedKey) {
    "slow" -> "16"
    "fast" -> "45"
    else -> "33⅓"
}

// ── HSL a mano (sustituye a androidx.core.graphics.ColorUtils) ──

/** Color → [matiz 0..360, saturación 0..1, luminosidad 0..1]. */
private fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    if (delta == 0f) return floatArrayOf(0f, 0f, l)
    val s = delta / (1f - abs(2f * l - 1f))
    var h = when (max) {
        r -> ((g - b) / delta) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    if (h < 0f) h += 360f
    return floatArrayOf(h, s.coerceIn(0f, 1f), l)
}

/** [matiz 0..360, saturación 0..1, luminosidad 0..1] → Color opaco. */
private fun hslToColor(hsl: FloatArray): Color {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VinylPlayer(
    state: BottomSheetState,
    navController: NavController,
    positionState: State<Long>,
    durationState: State<Long>,
    sliderPosition: Long?,
    onSliderPositionChange: (Long?) -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,

    onSleepTimerClick: () -> Unit = {},
    onVisualizerClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    // Optimista: ver PlayerConnection.currentSongLiked.
    val currentSongLiked by playerConnection.currentSongLiked.collectAsState()

    val position by positionState
    val duration by durationState

    // ── El brazo (varita) ES la barra de reproducción: su ángulo mapea la
    //    posición y arrastrarlo hace seek en tiempo real (como MDVinyl) ──
    val armProgress = if (duration > 0L) {
        ((sliderPosition ?: position).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val armSeekHolder = remember { longArrayOf(-1L) }
    val onArmScrub: (Float) -> Unit = { p ->
        if (duration > 0L) {
            val pos = (p * duration).toLong()
            armSeekHolder[0] = pos
            onSliderPositionChange(pos)
        }
    }
    var armHintCount by rememberPreference(VinylArmHintCountKey, 0)
    val onArmScrubEnd: () -> Unit = {
        if (armSeekHolder[0] >= 0L) {
            playerConnection.player.seekTo(armSeekHolder[0])
            if (armHintCount < 3) armHintCount = armHintCount + 1
        }
        armSeekHolder[0] = -1L
        onSliderPositionChange(null)
    }

    var themeKey by rememberPreference(DezzyVinylThemeKey, "dynamic")
    var styleKey by rememberPreference(DezzyVinylStyleKey, "dynamic")
    var speedKey by rememberPreference(DezzyVinylSpeedKey, "normal")

    // ── Color semilla de la carátula (para tema y vinilo dinámicos) ──
    var coverColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val url = mediaMetadata?.thumbnailUrl ?: run { coverColor = null; return@LaunchedEffect }
        val palette = loadArtPalette(url, 12) ?: return@LaunchedEffect
        val seed = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
        coverColor = seed?.let { Color(it).copy(alpha = 1f) }
    }

    // Tema dinámico: matiz de la carátula en banda clara y suave.
    val dynamicTheme = remember(coverColor) {
        val hsl = colorToHsl(coverColor ?: Color(0xFFB9B4A6))
        val h = hsl[0]
        val s = minOf(hsl[1], 0.38f)
        fun tone(sat: Float, light: Float) = hslToColor(floatArrayOf(h, sat, light))
        VinylTheme(
            key = "dynamic",
            background = tone(s, 0.86f),
            content = tone(minOf(s + 0.10f, 0.5f), 0.13f),
            subtle = tone(s, 0.44f),
            button = tone(s * 0.8f, 0.94f),
        )
    }
    val theme = if (themeKey == "dynamic") {
        dynamicTheme
    } else {
        VinylThemes.firstOrNull { it.key == themeKey } ?: dynamicTheme
    }

    // Vinilo dinámico: mármol teñido con el color de la carátula (como MDVinyl).
    val dynamicSpec = remember(coverColor) {
        val hsl = colorToHsl(coverColor ?: Color(0xFF8FA3B8))
        val s = minOf(hsl[1], 0.48f)
        VinylStyleSpec(
            key = "dynamic",
            base = hslToColor(floatArrayOf(hsl[0], s, 0.78f)),
            groove = Color.Black.copy(alpha = 0.06f),
            splat = hslToColor(floatArrayOf(hsl[0], minOf(s + 0.18f, 0.62f), 0.38f)),
            splatter = true,
        )
    }
    val resolvedStyle = remember(styleKey, mediaMetadata?.id, dynamicSpec) {
        when (styleKey) {
            "dynamic" -> dynamicSpec
            "shuffle" -> VinylStyles[abs(mediaMetadata?.id.hashCode()) % VinylStyles.size]
            else -> VinylStyles.firstOrNull { it.key == styleKey } ?: dynamicSpec
        }
    }

    // Canvas de la canción para el botón de Video inteligente
    val canvasArtwork = rememberPlaybackCanvas(
        mediaId = mediaMetadata?.id,
        title = mediaMetadata?.title,
        artist = mediaMetadata?.artists?.firstOrNull()?.name,
        albumTitle = mediaMetadata?.album?.title,
        durationMs = duration,
    )

    // ── Rotación del disco (solo avanza mientras suena; velocidad configurable) ──
    val speedMultiplier = VinylSpeeds.firstOrNull { it.first == speedKey }?.second?.second ?: 1f
    var discRotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, speedMultiplier) {
        if (!isPlaying) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    discRotation =
                        (discRotation + (now - last) / 1_000_000_000f * 108f * speedMultiplier) % 360f
                }
                last = now
            }
        }
    }

    // ── Gestos MDVinyl sobre el vinilo ──
    // Guardar el vinilo en la galería es Android puro: llega más adelante.
    val onSaveVinyl: () -> Unit = {
        playerConnection.toast("Guardar el vinilo llega más adelante")
    }

    var showStyleSheet by remember { mutableStateOf(false) }
    val isLandscape = isLandscape()

    // Compartir directo (tarjeta Canción/Letra/Canvas)
    var showShareSheet by remember { mutableStateOf(false) }
    val shareLyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    if (showShareSheet) {
        ShareBottomSheet(
            title = mediaMetadata?.title ?: "",
            subtitle = mediaMetadata?.artists?.joinToString { it.name } ?: "",
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            shareLink = "https://music.youtube.com/watch?v=${mediaMetadata?.id ?: ""}",
            lyrics = shareLyricsEntity?.lyrics,
            mediaId = mediaMetadata?.id,
            onDismiss = { showShareSheet = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        theme.background,
                        androidx.compose.ui.graphics.lerp(theme.background, theme.subtle, 0.18f),
                    )
                )
            ),
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxSize(),
                ) {
                    // Maqueta MDVinyl horizontal: funda con la carátula y el
                    // disco asomando detrás, con su etiqueta impresa.
                    SleeveTurntableSection(
                        thumbnailUrl = mediaMetadata?.thumbnailUrl,
                        title = mediaMetadata?.title.orEmpty(),
                        artist = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                        seed = mediaMetadata?.id.hashCode(),
                        spec = resolvedStyle,
                        isPlaying = isPlaying,
                        rotation = { discRotation },
                        theme = theme,
                        armProgress = armProgress,
                        onArmScrub = onArmScrub,
                        onArmScrubEnd = onArmScrubEnd,
                        onTogglePlay = { playerConnection.player.togglePlayPause() },
                        onPrevious = { playerConnection.seekToPrevious() },
                        onNext = { playerConnection.seekToNext() },
                        onSaveVinyl = onSaveVinyl,
                        rpm = rpmLabel(speedKey),
                        showArmHint = armHintCount < 3,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Misma barra que en vertical: colapsar · canción/video · cola · ajustes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundIconButton(
                            icon = Icons.Rounded.ExpandMore,
                            theme = theme,
                            onClick = state::collapseSoft,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        SongVideoToggle(
                            playerConnection = playerConnection,
                            canvas = canvasArtwork,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        RoundIconButton(
                            icon = Icons.AutoMirrored.Rounded.List,
                            theme = theme,
                            onClick = onQueueClick,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        RoundIconButton(
                            icon = Icons.Rounded.Settings,
                            theme = theme,
                            onClick = { showStyleSheet = true },
                        )
                    }
                    InfoAndControls(
                        theme = theme,
                        title = mediaMetadata?.title.orEmpty(),
                        artist = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                        isPlaying = isPlaying,
                        liked = currentSongLiked,
                        position = position,
                        duration = duration,
                        sliderPosition = sliderPosition,
                        onTogglePlay = { playerConnection.player.togglePlayPause() },
                        onPrevious = { playerConnection.seekToPrevious() },
                        onNext = { playerConnection.seekToNext() },
                        onToggleLike = { playerConnection.toggleLike() },
                        onLyricsClick = onLyricsClick,
                        onMoreClick = onMenuClick,
                        onVisualizerClick = onVisualizerClick,
                        onOpenListenTogether = {
                            state.collapseSoft()
                            navController.navigate("settings/music_together")
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // ── Barra superior: colapsar · video inteligente · cola/ajustes ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(
                        icon = Icons.Rounded.ExpandMore,
                        theme = theme,
                        onClick = state::collapseSoft,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SongVideoToggle(
                        playerConnection = playerConnection,
                        canvas = canvasArtwork,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    RoundIconButton(
                        icon = Icons.AutoMirrored.Rounded.List,
                        theme = theme,
                        onClick = onQueueClick,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RoundIconButton(
                        icon = Icons.Rounded.Settings,
                        theme = theme,
                        onClick = { showStyleSheet = true },
                    )
                }

                // ── Tornamesa ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    TurntableSection(
                        thumbnailUrl = mediaMetadata?.thumbnailUrl,
                        seed = mediaMetadata?.id.hashCode(),
                        spec = resolvedStyle,
                        isPlaying = isPlaying,
                        rotation = { discRotation },
                        theme = theme,
                        armProgress = armProgress,
                        onArmScrub = onArmScrub,
                        onArmScrubEnd = onArmScrubEnd,
                        onTogglePlay = { playerConnection.player.togglePlayPause() },
                        onPrevious = { playerConnection.seekToPrevious() },
                        onNext = { playerConnection.seekToNext() },
                        onSaveVinyl = onSaveVinyl,
                        rpm = rpmLabel(speedKey),
                        showArmHint = armHintCount < 3,
                    )
                }

                InfoAndControls(
                    theme = theme,
                    title = mediaMetadata?.title.orEmpty(),
                    artist = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                    isPlaying = isPlaying,
                    liked = currentSongLiked,
                    position = position,
                    duration = duration,
                    sliderPosition = sliderPosition,
                    onTogglePlay = { playerConnection.player.togglePlayPause() },
                    onPrevious = { playerConnection.seekToPrevious() },
                    onNext = { playerConnection.seekToNext() },
                    onToggleLike = { playerConnection.toggleLike() },
                    onLyricsClick = onLyricsClick,
                    onMoreClick = onMenuClick,
                    onVisualizerClick = onVisualizerClick,
                    onOpenListenTogether = {
                        state.collapseSoft()
                        navController.navigate("settings/music_together")
                    },
                )
            }
        }

        // El overlay de video lo pone Player.kt encima de todos los estilos. Aquí había
        // un segundo overlay: dos PlayerView se peleaban por el mismo ExoPlayer y la
        // pantalla completa podía quedarse sin imagen o con la del overlay de debajo.
    }

    // ── Hoja de estilo: Theme + Vinyl + Velocidad ──
    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            containerColor = Color(0xFF17171A),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = "Theme",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Tema dinámico (de la carátula) primero
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        dynamicTheme.background,
                                        coverColor ?: dynamicTheme.button,
                                    ),
                                ),
                            )
                            .border(
                                width = if (themeKey == "dynamic") 3.dp else 1.dp,
                                color = if (themeKey == "dynamic") Color.White else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { themeKey = "dynamic" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Tema dinámico",
                            tint = dynamicTheme.content,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    VinylThemes.forEach { t ->
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(t.background)
                                .border(
                                    width = if (t.key == themeKey) 3.dp else 1.dp,
                                    color = if (t.key == themeKey) Color.White else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .clickable { themeKey = t.key },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(26.dp))
                Text(
                    text = "Vinyl",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Vinilo dinámico (teñido con la carátula) primero
                    MiniDiscPreview(
                        spec = dynamicSpec,
                        selected = styleKey == "dynamic",
                        modifier = Modifier
                            .size(54.dp)
                            .clickable { styleKey = "dynamic" },
                    )
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (styleKey == "shuffle") 3.dp else 1.5.dp,
                                color = if (styleKey == "shuffle") Color.White else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape,
                            )
                            .clickable { styleKey = "shuffle" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Vinilo aleatorio",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    VinylStyles.forEach { spec ->
                        MiniDiscPreview(
                            spec = spec,
                            selected = styleKey == spec.key,
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { styleKey = spec.key },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(26.dp))
                Text(
                    text = "Velocidad",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VinylSpeeds.forEach { (key, labelSpeed) ->
                        FilterChip(
                            selected = speedKey == key,
                            onClick = { speedKey = key },
                            label = { Text(labelSpeed.first) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Toca el vinilo para pausar · desliza para cambiar de canción · mantén presionado para guardarlo como imagen",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = {
                    showStyleSheet = false
                    onSleepTimerClick()
                }) {
                    Text("Temporizador de sueño", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ── Tornamesa: disco (con gestos) + brazo ────────────────────────────────────

@Composable
private fun TurntableSection(
    thumbnailUrl: String?,
    seed: Int,
    spec: VinylStyleSpec,
    isPlaying: Boolean,
    rotation: () -> Float,
    theme: VinylTheme,
    armProgress: Float,
    onArmScrub: (Float) -> Unit,
    onArmScrubEnd: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSaveVinyl: () -> Unit,
    rpm: String = "33⅓",
    showArmHint: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Los gestos van en este Box exterior (NO rota): dentro del disco
        // giratorio las coordenadas del drag quedarían rotadas.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .aspectRatio(1f)
                .align(Alignment.CenterStart)
                .offset(x = (-18).dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTogglePlay() },
                        onLongPress = { onSaveVinyl() },
                    )
                }
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            when {
                                dragTotal <= -threshold -> onNext()
                                dragTotal >= threshold -> onPrevious()
                            }
                        },
                    ) { change, dragAmount ->
                        dragTotal += dragAmount
                        change.consume()
                    }
                },
        ) {
            VinylDisc(
                thumbnailUrl = thumbnailUrl,
                seed = seed,
                spec = spec,
                rotation = rotation,
                modifier = Modifier.fillMaxSize(),
            )
            // Arco de progreso alrededor del disco (boceto A): no gira con él.
            DiscProgressArc(progress = armProgress, theme = theme)
        }
        ToneArm(
            isPlaying = isPlaying,
            progress = armProgress,
            onScrub = onArmScrub,
            onScrubEnd = onArmScrubEnd,
            armColor = theme.button,
            outline = theme.content.copy(alpha = 0.18f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp, top = 4.dp)
                .fillMaxWidth(0.30f)
                .aspectRatio(0.34f),
        )
        // Indicador de RPM a la izquierda (boceto C)
        Text(
            text = "$rpm RPM",
            color = theme.subtle,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 6.dp),
        )
        // Aviso de las primeras veces: el brazo es la barra de progreso
        if (showArmHint) {
            Text(
                text = "Arrastra el brazo para avanzar",
                color = theme.content.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.button.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Arco de progreso alrededor del disco (boceto A). */
@Composable
private fun DiscProgressArc(progress: Float, theme: VinylTheme, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = modifier) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = theme.content.copy(alpha = 0.10f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = theme.content.copy(alpha = 0.55f),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

// ── Tornamesa horizontal (réplica MDVinyl): funda + disco asomando + brazo ──
//
// La carátula del álbum va impresa en la FUNDA cuadrada; el disco sale por su
// derecha con una etiqueta de vinilo real (título/artista impresos, que giran
// con el disco). El brazo a la derecha es la barra de reproducción.

@Composable
private fun SleeveTurntableSection(
    thumbnailUrl: String?,
    title: String,
    artist: String,
    seed: Int,
    spec: VinylStyleSpec,
    isPlaying: Boolean,
    rotation: () -> Float,
    theme: VinylTheme,
    armProgress: Float,
    onArmScrub: (Float) -> Unit,
    onArmScrubEnd: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSaveVinyl: () -> Unit,
    rpm: String = "33⅓",
    showArmHint: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Limitado por alto Y por ancho: funda + disco asomando caben siempre
        // sin invadir la columna de controles (funda·(1 + 0.60·0.93) ≈ 1.56×).
        val sleeveX = 20.dp
        val sleeveSide = minOf(maxHeight * 0.86f, (maxWidth - sleeveX) / 1.56f)
        val discSize = sleeveSide * 0.93f
        // El disco queda medio metido en la funda, como recién sacado.
        val discX = sleeveX + sleeveSide * 0.60f
        val discTop = (maxHeight - discSize) / 2

        // Disco (detrás de la funda), con los gestos MDVinyl
        Box(
            modifier = Modifier
                .size(discSize)
                .offset(x = discX, y = discTop)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTogglePlay() },
                        onLongPress = { onSaveVinyl() },
                    )
                }
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            when {
                                dragTotal <= -threshold -> onNext()
                                dragTotal >= threshold -> onPrevious()
                            }
                        },
                    ) { change, dragAmount ->
                        dragTotal += dragAmount
                        change.consume()
                    }
                },
        ) {
            VinylDisc(
                thumbnailUrl = null,
                seed = seed,
                spec = spec,
                rotation = rotation,
                labelText = title to artist,
                labelBg = theme.button,
                labelFg = theme.content,
                modifier = Modifier.fillMaxSize(),
            )
            DiscProgressArc(progress = armProgress, theme = theme)
        }

        // Funda: la carátula del álbum como portada cuadrada con sombra
        Box(
            modifier = Modifier
                .size(sleeveSide)
                .align(Alignment.CenterStart)
                .offset(x = sleeveX)
                .shadow(14.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ToneArm(
            isPlaying = isPlaying,
            progress = armProgress,
            onScrub = onArmScrub,
            onScrubEnd = onArmScrubEnd,
            armColor = theme.button,
            outline = theme.content.copy(alpha = 0.18f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
                .fillMaxHeight(0.95f)
                .aspectRatio(0.34f),
        )
        // RPM bajo la funda y la pista del brazo, igual que en vertical (bocetos A y C)
        Text(
            text = "$rpm RPM",
            color = theme.subtle,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sleeveX, bottom = 4.dp),
        )
        if (showArmHint) {
            Text(
                text = "Arrastra el brazo para avanzar",
                color = theme.content.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.button.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun VinylDisc(
    thumbnailUrl: String?,
    seed: Int,
    spec: VinylStyleSpec,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
    labelText: Pair<String, String>? = null, // (título, artista): etiqueta impresa en vez de carátula
    labelBg: Color = Color.White,
    labelFg: Color = Color(0xFF141414),
) {
    val splats = remember(seed, spec.key, spec.base) { generateSplats(seed, spec) }

    Box(
        modifier = modifier.graphicsLayer { rotationZ = rotation() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val c = center

            drawCircle(spec.base, radius, c)
            // Sombreado radial sutil (relieve del vinilo)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f),
                    ),
                    center = c,
                    radius = radius,
                ),
                radius = radius,
                center = c,
            )
            // Surcos
            val labelR = radius * 0.30f
            val grooves = 30
            for (i in 0..grooves) {
                val r = labelR + (radius * 0.94f - labelR) * i / grooves.toFloat()
                drawCircle(spec.groove, r, c, style = Stroke(width = 1.2f))
            }
            // Splatter
            for (s in splats) {
                rotate(s.angle, c) {
                    val y0 = c.y - radius * s.startFrac
                    val y1 = c.y - radius * (s.startFrac + s.lenFrac).coerceAtMost(0.96f)
                    drawLine(
                        color = spec.splat.copy(alpha = s.alpha),
                        start = Offset(c.x, y0),
                        end = Offset(c.x, y1),
                        strokeWidth = s.widthPx,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // Puntos estroboscópicos del borde (boceto C)
            val strobeR = radius * 0.975f
            for (i in 0 until 72) {
                val a = i * 5.0 * PI / 180.0
                drawCircle(
                    color = Color.Black.copy(alpha = if (i % 2 == 0) 0.30f else 0.12f),
                    radius = radius * 0.008f + 0.6f,
                    center = Offset(
                        c.x + (strobeR * kotlin.math.cos(a)).toFloat(),
                        c.y + (strobeR * kotlin.math.sin(a)).toFloat(),
                    ),
                )
            }
            // Borde exterior
            drawCircle(Color.Black.copy(alpha = 0.25f), radius, c, style = Stroke(width = 2f))
        }

        // Etiqueta central: carátula (vertical) o etiqueta impresa de vinilo
        // real con título/artista (horizontal, donde la carátula va en la funda).
        Box(
            modifier = Modifier
                .fillMaxSize(0.42f)
                .clip(CircleShape)
                .background(labelBg),
            contentAlignment = Alignment.Center,
        ) {
            if (labelText == null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val (title, artist) = labelText
                val textMeasurer = rememberTextMeasurer()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    val titleStyle = TextStyle(
                        color = labelFg,
                        fontSize = (r * 0.22f).toSp(),
                        fontWeight = FontWeight.Bold,
                    )
                    val artistStyle = TextStyle(
                        color = labelFg.copy(alpha = 0.75f),
                        fontSize = (r * 0.16f).toSp(),
                    )
                    drawCenteredText(textMeasurer, title, titleStyle, r * 1.55f, cx, cy - r * 0.10f)
                    drawCenteredText(textMeasurer, artist, artistStyle, r * 1.45f, cx, cy + r * 0.34f)

                    // Texto curvo en el borde, como una etiqueta de vinilo real
                    val rimStyle = TextStyle(
                        color = labelFg.copy(alpha = 0.45f),
                        fontSize = (r * 0.095f).toSp(),
                        letterSpacing = 0.18.em,
                    )
                    val rimR = r * 0.82f
                    // Arco superior (legible, centrado a las 12 en punto)
                    drawArcText(textMeasurer, "DEZZY RECORDS", rimStyle, cx, cy, rimR, top = true)
                    // Arco inferior legible (centrado a las 6 en punto)
                    drawArcText(textMeasurer, "33⅓ RPM · STEREO", rimStyle, cx, cy, rimR, top = false)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.42f)
                .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        )
        // Agujero central
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(spec.base),
        )
    }
}

/** Texto en una línea, centrado en [cx], con su línea base en [baselineY] y elipsis al pasar [maxWidthPx]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCenteredText(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Float,
    cx: Float,
    baselineY: Float,
) {
    val layout = measurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidthPx.toInt().coerceAtLeast(0)),
    )
    drawText(
        layout,
        topLeft = Offset(cx - layout.size.width / 2f, baselineY - layout.firstBaseline),
    )
}

/**
 * Texto curvo sobre el aro de la etiqueta: cada letra se coloca y gira sobre la circunferencia.
 * [top] = arco superior (letras hacia fuera); si no, arco inferior legible (letras hacia dentro).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcText(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    cx: Float,
    cy: Float,
    rimR: Float,
    top: Boolean,
) {
    val glyphs = text.map { measurer.measure(it.toString(), style) }
    val total = glyphs.sumOf { it.size.width.toDouble() }.toFloat()
    var advance = 0f
    for (g in glyphs) {
        val w = g.size.width.toFloat()
        val mid = advance + w / 2f - total / 2f
        val deg = mid / rimR * 180f / PI.toFloat()
        val theta = if (top) -90f + deg else 90f - deg
        val rad = theta.toDouble() * PI / 180.0
        val px = cx + (rimR * kotlin.math.cos(rad)).toFloat()
        val py = cy + (rimR * kotlin.math.sin(rad)).toFloat()
        val rot = if (top) theta + 90f else theta - 90f
        rotate(rot, Offset(px, py)) {
            drawText(g, topLeft = Offset(px - w / 2f, py - g.firstBaseline))
        }
        advance += w
    }
}

@Composable
private fun ToneArm(
    isPlaying: Boolean,
    progress: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    armColor: Color,
    outline: Color,
    modifier: Modifier = Modifier,
) {
    // El brazo ES la barra de reproducción: a mayor progreso, más se adentra
    // la aguja hacia el centro del disco. Arrastrarlo verticalmente hace seek.
    val armMin = 8f   // inicio de la canción: aguja en el borde exterior
    val armMax = 30f  // final: aguja cerca de la etiqueta central
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val shown = (if (dragging) dragProgress else progress).coerceIn(0f, 1f)
    val target = armMin + (armMax - armMin) * shown
    // Animado en reproducción normal; instantáneo mientras arrastras (sigue el dedo).
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (isPlaying) 500 else 250),
        label = "toneArmAngle",
    )
    val angle = if (dragging) target else animated
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            // Recorrido del dedo para barrer toda la canción (≈1.5× el alto del
            // brazo): al ser el ÚNICO control de posición debe sentirse directo.
            val rangePx = size.height * 1.5f
            detectVerticalDragGestures(
                onDragStart = {
                    dragging = true
                    dragProgress = progress.coerceIn(0f, 1f)
                },
                onDragEnd = { dragging = false; onScrubEnd() },
                onDragCancel = { dragging = false; onScrubEnd() },
            ) { change, dy ->
                dragProgress = (dragProgress + dy / rangePx).coerceIn(0f, 1f)
                onScrub(dragProgress)
                change.consume()
            }
        },
    ) {
        val pivot = Offset(size.width * 0.52f, size.height * 0.14f)

        // Aro del pivote
        drawCircle(outline, radius = size.width * 0.46f, center = pivot, style = Stroke(width = 2f))

        // Sombra del brazo proyectada sobre el disco
        rotate(angle + 1.5f, Offset(pivot.x + size.width * 0.05f, pivot.y + size.height * 0.02f)) {
            drawLine(
                color = Color.Black.copy(alpha = 0.14f),
                start = Offset(pivot.x + size.width * 0.05f, pivot.y + size.height * 0.02f),
                end = Offset(pivot.x + size.width * 0.05f, size.height * 0.92f),
                strokeWidth = size.width * 0.07f,
                cap = StrokeCap.Round,
            )
        }
        rotate(angle, pivot) {
            val armEndY = size.height * 0.90f
            // Vástago plateado con brillo
            drawLine(
                color = Color(0xFF9EA3A8),
                start = pivot,
                end = Offset(pivot.x, armEndY),
                strokeWidth = size.width * 0.06f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(pivot.x - size.width * 0.012f, pivot.y + size.height * 0.02f),
                end = Offset(pivot.x - size.width * 0.012f, armEndY),
                strokeWidth = size.width * 0.018f,
                cap = StrokeCap.Round,
            )
            // Cabezal (contrapeso) en el pivote
            drawRoundRect(
                color = armColor,
                topLeft = Offset(pivot.x - size.width * 0.17f, pivot.y - size.height * 0.055f),
                size = Size(size.width * 0.34f, size.height * 0.115f),
                cornerRadius = CornerRadius(size.width * 0.055f),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.10f),
                topLeft = Offset(pivot.x - size.width * 0.17f, pivot.y - size.height * 0.055f),
                size = Size(size.width * 0.34f, size.height * 0.115f),
                cornerRadius = CornerRadius(size.width * 0.055f),
                style = Stroke(width = 1.5f),
            )
            // Cápsula en la punta
            drawRoundRect(
                color = armColor,
                topLeft = Offset(pivot.x - size.width * 0.115f, armEndY - size.height * 0.015f),
                size = Size(size.width * 0.23f, size.height * 0.075f),
                cornerRadius = CornerRadius(size.width * 0.045f),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.10f),
                topLeft = Offset(pivot.x - size.width * 0.115f, armEndY - size.height * 0.015f),
                size = Size(size.width * 0.23f, size.height * 0.075f),
                cornerRadius = CornerRadius(size.width * 0.045f),
                style = Stroke(width = 1.5f),
            )
        }
    }
}

// ── Info + controles ─────────────────────────────────────────────────────────

@Composable
private fun InfoAndControls(
    theme: VinylTheme,
    title: String,
    artist: String,
    isPlaying: Boolean,
    liked: Boolean,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onLyricsClick: () -> Unit,

    onMoreClick: () -> Unit = {},
    onVisualizerClick: () -> Unit = {},
    onOpenListenTogether: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Título y artista (alineados a la izquierda, como MDVinyl)
        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(
                text = title,
                color = theme.content,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                color = theme.subtle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // SIN barra deslizante: el brazo del vinilo ES el control de posición
        // (arrástralo sobre el disco). Solo se muestran los tiempos.
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${makeTimeString(sliderPosition ?: position)} / ${makeTimeString(duration)}",
            color = if (sliderPosition != null) theme.content else theme.subtle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(theme.button)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Botones pill "físicos": PLAY a la izquierda, prev/next a la derecha
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PillButton(
                onClick = onTogglePlay,
                fill = theme.button,
                width = 110.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = theme.content,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaying) "PAUSE" else "PLAY",
                        color = theme.content,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            PillButton(onClick = onPrevious, fill = theme.button, width = 96.dp) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = theme.content,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            PillButton(onClick = onNext, fill = theme.button, width = 96.dp) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = theme.content,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Fila inferior: me gusta · dispositivos · letras · más (visualizador y compartir viven en «Más»)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                theme = theme,
                onClick = onToggleLike,
                // ♥ relleno y en color cuando te gusta (boceto A)
                tint = if (liked) Color(0xFFE5484D) else null,
            )
            // Dispositivos en el centro
            PhysicalEdge(theme = theme, shape = CircleShape, width = 46.dp, height = 46.dp) {
                Surface(
                    shape = CircleShape,
                    color = theme.button,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        DevicesButton(
                            tint = theme.content,
                            iconSize = 22.dp,
                            onOpenListenTogether = onOpenListenTogether,
                        )
                    }
                }
            }
            RoundIconButton(
                icon = Icons.Rounded.Lyrics,
                theme = theme,
                onClick = onLyricsClick,
            )
            RoundIconButton(
                icon = Icons.Rounded.MoreHoriz,
                theme = theme,
                onClick = onMoreClick,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Piezas pequeñas ──────────────────────────────────────────────────────────

@Composable
private fun PillButton(
    onClick: () -> Unit,
    fill: Color,
    width: Dp,
    label: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isCustomStyledTheme()) {
            DezzyGlassSurfaceButton(
                onClick = onClick,
                shape = RoundedCornerShape(50),
                normalContainerColor = fill,
                normalContentColor = Color.White,
                prominent = true,
                glassTintColor = fill,
                modifier = Modifier.size(width, 44.dp),
            ) {}
        } else {
            PhysicalEdge(edge = fill, shape = RoundedCornerShape(50), width = width, height = 44.dp) {
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(50),
                    color = fill,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(width, 44.dp),
                ) {}
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        label()
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    theme: VinylTheme,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    if (isCustomStyledTheme()) {
        DezzyGlassSurfaceButton(
            onClick = onClick,
            shape = CircleShape,
            normalContainerColor = theme.button,
            normalContentColor = tint ?: theme.content,
            prominent = false,
            modifier = Modifier.size(46.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: theme.content,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        PhysicalEdge(theme = theme, shape = CircleShape, width = 46.dp, height = 46.dp) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = theme.button,
                shadowElevation = 6.dp,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint ?: theme.content,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * Canto de 3 dp bajo un botón (boceto C): una copia más oscura asoma por debajo
 * y el botón parece una tecla física del tocadiscos.
 */
@Composable
private fun PhysicalEdge(
    shape: androidx.compose.ui.graphics.Shape,
    width: Dp,
    height: Dp,
    theme: VinylTheme? = null,
    edge: Color = theme?.button ?: Color.LightGray,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.size(width, height + 3.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width, height)
                .clip(shape)
                .background(androidx.compose.ui.graphics.lerp(edge, Color.Black, 0.18f)),
        )
        Box(modifier = Modifier.align(Alignment.TopCenter)) { content() }
    }
}

@Composable
private fun MiniDiscPreview(
    spec: VinylStyleSpec,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val splats = remember(spec.key, spec.base) {
        generateSplats(spec.key.hashCode(), spec, count = 40)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val c = center
            drawCircle(spec.base, radius, c)
            drawCircle(spec.groove, radius * 0.7f, c, style = Stroke(width = 1f))
            drawCircle(spec.groove, radius * 0.5f, c, style = Stroke(width = 1f))
            for (s in splats) {
                rotate(s.angle, c) {
                    drawLine(
                        color = spec.splat.copy(alpha = s.alpha),
                        start = Offset(c.x, c.y - radius * s.startFrac),
                        end = Offset(c.x, c.y - radius * (s.startFrac + s.lenFrac).coerceAtMost(0.95f)),
                        strokeWidth = s.widthPx * 0.4f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.22f, c)
            drawCircle(spec.base, radius * 0.05f, c)
            if (selected) {
                drawCircle(Color.White, radius, c, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}
