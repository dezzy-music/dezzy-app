/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dezzy.app.ui.screens.settings.prefs.CropThumbnails11Key
import com.dezzy.app.ui.screens.settings.prefs.HidePlayerThumbnailKey
import com.dezzy.app.ui.screens.settings.prefs.PlayerButtonColorsKey
import com.dezzy.app.ui.screens.settings.prefs.PlayerCornerRadiusKey
import com.dezzy.app.ui.screens.settings.prefs.PlayerSwipeToChangeKey
import com.dezzy.app.ui.screens.settings.prefs.ShowCommentsButtonKey
import com.dezzy.app.ui.screens.settings.prefs.SpinningThumbnailKey

/** Ajustes de Apariencia › Reproductor ya leídos, para que los estilos los apliquen igual. */
class PlayerAppearance(
    val hideThumbnail: Boolean,
    val spinning: Boolean,
    val cornerRadius: Dp,
    val swipeToChange: Boolean,
    val cropThumbnails: Boolean,
    /** "DEFAULT", "DYNAMIC" o "WHITE". */
    val buttonColors: String,
    val showComments: Boolean,
) {
    /** Forma de la portada grande: círculo si gira, si no esquinas con el radio elegido. */
    val coverShape: Shape get() = if (spinning) CircleShape else RoundedCornerShape(cornerRadius)
}

@Composable
fun rememberPlayerAppearance(): PlayerAppearance {
    val hide by rememberPreference(HidePlayerThumbnailKey, false)
    val spin by rememberPreference(SpinningThumbnailKey, false)
    val radius by rememberPreference(PlayerCornerRadiusKey, 16f)
    val swipe by rememberPreference(PlayerSwipeToChangeKey, true)
    val crop by rememberPreference(CropThumbnails11Key, false)
    val buttons by rememberPreference(PlayerButtonColorsKey, "DEFAULT")
    val comments by rememberPreference(ShowCommentsButtonKey, true)
    return PlayerAppearance(hide, spin, radius.coerceIn(0f, 64f).dp, swipe, crop, buttons, comments)
}

/** Color dominante de la carátula (aclarado para que se vea sobre fondos oscuros), o null si no hay. */
@Composable
fun rememberCoverAccent(url: String?): Color? {
    val accent by produceState<Color?>(null, url) {
        value = loadArtPalette(url, 16)?.let {
            val rgb = it.getVibrantColor(it.getDominantColor(0xFFFFFFFF.toInt())) or 0xFF000000.toInt()
            lerp(Color(rgb), Color.White, 0.3f)
        }
    }
    return accent
}

/** Color de los botones de control: DYNAMIC = color de la carátula, WHITE = blanco, DEFAULT = [default]. */
fun PlayerAppearance.buttonTint(default: Color, accent: Color?): Color = when (buttonColors) {
    "WHITE" -> Color.White
    "DYNAMIC" -> accent ?: default
    else -> default
}

/** Deslizar a los lados sobre la portada cambia de canción (umbral de ~80 dp). */
fun Modifier.playerSwipeToChange(connection: PlayerConnection?, enabled: Boolean): Modifier =
    if (!enabled || connection == null) this else pointerInput(connection) {
        val threshold = 80.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -threshold -> connection.seekToNext()
                    total >= threshold -> connection.seekToPrevious()
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
        ) { change, dragAmount ->
            change.consume()
            total += dragAmount
        }
    }

/**
 * Portada grande del reproductor con los ajustes de Apariencia: esquinas, giro, ocultar (icono en
 * su lugar), recorte 1:1 y deslizar para cambiar de canción. El llamador da el tamaño/aspecto.
 */
@Composable
fun PlayerCover(
    url: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    scale: Float = 1f,
    elevation: Dp = 0.dp,
) {
    val look = rememberPlayerAppearance()
    val connection = LocalPlayerConnection.current
    val shape = look.coverShape

    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(look.spinning, isPlaying) {
        if (!look.spinning) { angle = 0f; return@LaunchedEffect }
        if (!isPlaying) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) angle = (angle + (now - last) / 1_000_000_000f * 18f) % 360f // 20 s por vuelta
                last = now
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = angle
            }
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape)
            .playerSwipeToChange(connection, look.swipeToChange),
        contentAlignment = Alignment.Center,
    ) {
        if (look.hideThumbnail) {
            // Sin logo propio en recursos: icono de música sobre el color de superficie.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                Icon(
                    Icons.Rounded.MusicNote, null,
                    Modifier.fillMaxSize(0.4f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Las miniaturas de vídeo (ytimg, 16:9) sólo se recortan a 1:1 con el ajuste activo.
            val isVideoThumb = url?.contains("ytimg.com") == true
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = if (look.cropThumbnails || !isVideoThumb) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
