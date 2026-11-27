/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dezzy.app.feature.personalize.WallpaperState
import com.dezzy.app.feature.personalize.WallpaperStore
import org.koin.compose.koinInject

/**
 * Fondo de la app con imagen o vídeo (uno para vertical y otro para horizontal, según la forma del
 * espacio que ocupa). Se pone DETRÁS del contenido, ocupando toda la pantalla; si no hay fondo
 * configurado no pinta nada. Sobre la imagen va el oscurecido que el usuario eligió.
 *
 * Para que se vea, las superficies de la app que lo tapen (fondo del `Scaffold`/`Surface` raíz) deben ser
 * transparentes cuando [rememberHasAppWallpaper] devuelva true.
 */
@Composable
fun AppWallpaper(modifier: Modifier = Modifier) {
    val store: WallpaperStore = koinInject()
    val state by remember(store) { store.state }.collectAsState(initial = WallpaperState())
    if (state.isEmpty) return
    val density = LocalDensity.current
    val platformContext = coil3.compose.LocalPlatformContext.current
    BoxWithConstraints(modifier) {
        val slot = state.slot(landscape = maxWidth > maxHeight) ?: return@BoxWithConstraints
        var imageLuminance by remember(slot.uri) { mutableStateOf<Float?>(null) }
        if (slot.isVideo) {
            WallpaperVideo(slot.uri, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = remember(slot.uri, platformContext) {
                    coil3.request.ImageRequest.Builder(platformContext).data(slot.uri).readableBitmap().build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { st -> imageLuminance = averageLuminance(st.painter, density) },
            )
        }
        val dim = state.dimPercent / 100f
        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
        // Velo inteligente: con tema claro sobre una imagen oscura (o tema oscuro sobre una clara), se
        // aclara u oscurece la imagen lo justo para que el texto del tema se lea. Vídeo: se supone oscuro.
        // Si la imagen no se pudo medir se supone oscura (lo habitual en fondos), para no dejar el texto sin contraste.
        val lum = (if (slot.isVideo) 0.3f else imageLuminance ?: UNKNOWN_LUMINANCE) * (1f - dim)
        val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val veil = if (lightTheme) {
            ((LIGHT_TARGET - lum) / (1f - lum)).coerceIn(0f, MAX_VEIL)
        } else {
            (1f - DARK_TARGET / lum.coerceAtLeast(0.01f)).coerceIn(0f, MAX_VEIL)
        }
        if (veil > 0.01f) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = veil)))
        }
    }
}

/** Luminosidad de fondo que se busca con tema claro (mínima) y con tema oscuro (máxima). */
private const val LIGHT_TARGET = 0.62f
private const val DARK_TARGET = 0.28f
private const val MAX_VEIL = 0.8f
private const val UNKNOWN_LUMINANCE = 0.3f

/** Luminosidad media (0..1) de una imagen, pintándola en miniatura. null si no se puede leer. */
private fun averageLuminance(painter: Painter, density: androidx.compose.ui.unit.Density): Float? = runCatching {
    val side = 24
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
        with(painter) { draw(size) }
    }
    val pixels = IntArray(side * side)
    bitmap.readPixels(pixels)
    pixels.map { c ->
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        0.2126f * r + 0.7152f * g + 0.0722f * b
    }.average().toFloat()
}.getOrNull()

/** true si hay algún fondo configurado (para que el llamador deje transparentes las superficies de debajo). */
@Composable
fun rememberHasAppWallpaper(): Boolean {
    val store: WallpaperStore = koinInject()
    val state by remember(store) { store.state }.collectAsState(initial = WallpaperState())
    return !state.isEmpty
}
