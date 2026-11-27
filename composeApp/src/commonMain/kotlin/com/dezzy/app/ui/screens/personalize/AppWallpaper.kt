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
    BoxWithConstraints(modifier) {
        val slot = state.slot(landscape = maxWidth > maxHeight) ?: return@BoxWithConstraints
        if (slot.isVideo) {
            WallpaperVideo(slot.uri, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = slot.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state.dimPercent > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.dimPercent / 100f)))
        }
    }
}

/** true si hay algún fondo configurado (para que el llamador deje transparentes las superficies de debajo). */
@Composable
fun rememberHasAppWallpaper(): Boolean {
    val store: WallpaperStore = koinInject()
    val state by remember(store) { store.state }.collectAsState(initial = WallpaperState())
    return !state.isEmpty
}
