/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dezzy.app.feature.personalize.WallpaperFiles
import com.dezzy.app.feature.personalize.WallpaperSlot
import com.dezzy.app.feature.personalize.WallpaperState
import com.dezzy.app.feature.personalize.WallpaperStore
import com.dezzy.app.feature.personalize.wallpaperFiles
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.SettingsDivider
import com.dezzy.app.ui.screens.settings.SettingsGroup
import com.dezzy.app.ui.screens.settings.SettingsPage
import com.dezzy.app.ui.screens.settings.SettingsRow
import com.dezzy.app.ui.screens.settings.SettingsSliderRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Ajustes › Apariencia › Fondo de pantalla: imagen o vídeo de la galería (o de un pin de Pinterest),
 * uno para vertical y otro para horizontal, y cuánto se oscurece para que se lea el contenido.
 */
@Composable
fun WallpaperSettingsScreen(ctx: ScreenContext) {
    val store: WallpaperStore = koinInject()
    val files: WallpaperFiles = remember { wallpaperFiles() }
    val scope = rememberCoroutineScope()
    val state by remember(store) { store.state }.collectAsState(initial = WallpaperState())

    // Orientación a la que va lo que se elija en el selector (false = vertical).
    var pickLandscape by remember { mutableStateOf(false) }
    val pick = rememberMediaPicker { uri, mime ->
        val target = pickLandscape
        scope.launch {
            val isVideo = mime?.startsWith("video/") == true
            val imported = files.importPicked(uri, extensionForMime(mime, isVideo))
            if (imported == null) {
                ctx.toast("No se pudo usar ese archivo")
                return@launch
            }
            val isLandscape = files.isLandscape(imported, isVideo)
            store.set(target, imported, isVideo)
            ctx.toast(
                when {
                    isLandscape && !target -> "Es horizontal: en vertical se verá recortado"
                    !isLandscape && target -> "Es vertical: en horizontal se verá recortado"
                    else -> "Fondo cambiado"
                },
            )
        }
    }

    SettingsPage(title = "Fondo de pantalla", onBack = { ctx.navigator.back() }) {
        item(key = "portrait") {
            OrientationGroup(
                label = "Vertical",
                slot = state.portrait,
                icon = Icons.Rounded.StayCurrentPortrait,
                onPick = { kind -> pickLandscape = false; pick(kind) },
                onClear = { scope.launch { store.clear(landscape = false) } },
            )
        }
        item(key = "landscape") {
            OrientationGroup(
                label = "Horizontal (al girar)",
                slot = state.landscape,
                icon = Icons.Rounded.StayCurrentLandscape,
                onPick = { kind -> pickLandscape = true; pick(kind) },
                onClear = { scope.launch { store.clear(landscape = true) } },
            )
        }
        item(key = "dim") {
            SettingsGroup(label = "Legibilidad") {
                SettingsSliderRow(
                    title = "Oscurecer el fondo",
                    icon = Icons.Rounded.BrightnessMedium,
                    value = state.dimPercent.toFloat(),
                    valueRange = 0f..WallpaperState.MAX_DIM.toFloat(),
                    steps = WallpaperState.MAX_DIM / 5 - 1,
                    valueLabel = { "${it.toInt()} %" },
                    onValueChangeFinished = { scope.launch { store.setDim(it.toInt()) } },
                )
            }
        }
        item(key = "pinterest") {
            SettingsGroup(label = "Pinterest") {
                PinterestWallpaperField(
                    onImage = { uri, landscape -> scope.launch { applyPin(store, uri, isVideo = false, landscape = landscape) } },
                    onVideo = { uri, landscape -> scope.launch { applyPin(store, uri, isVideo = true, landscape = landscape) } },
                )
            }
        }
    }
}

/** Un pin vertical va al fondo vertical; uno horizontal, sólo al horizontal y deja libre el vertical. */
private suspend fun applyPin(store: WallpaperStore, uri: String, isVideo: Boolean, landscape: Boolean) {
    if (landscape) {
        store.set(landscape = true, uri = uri, isVideo = isVideo)
        store.clear(landscape = false)
    } else {
        store.set(landscape = false, uri = uri, isVideo = isVideo)
    }
}

@Composable
private fun OrientationGroup(
    label: String,
    slot: WallpaperSlot?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPick: (MediaKind) -> Unit,
    onClear: () -> Unit,
) {
    SettingsGroup(label = label) {
        SettingsRow(
            title = "Imagen de la galería",
            subtitle = if (slot != null && !slot.isVideo) "Ahora: imagen elegida" else null,
            icon = Icons.Rounded.Image,
            onClick = { onPick(MediaKind.IMAGE) },
            trailing = if (slot != null && !slot.isVideo) {
                { WallpaperThumb(slot, icon) }
            } else {
                null
            },
        )
        SettingsDivider()
        SettingsRow(
            title = "Vídeo de la galería",
            subtitle = if (slot != null && slot.isVideo) "Ahora: vídeo elegido" else "Se reproduce mudo y en bucle",
            icon = Icons.Rounded.Videocam,
            onClick = { onPick(MediaKind.VIDEO) },
            trailing = if (slot != null && slot.isVideo) {
                { WallpaperThumb(slot, icon) }
            } else {
                null
            },
        )
        if (slot != null) {
            SettingsDivider()
            SettingsRow(
                title = "Quitar este fondo",
                icon = Icons.Rounded.Delete,
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun WallpaperThumb(slot: WallpaperSlot, placeholder: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (slot.isVideo) {
            Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = slot.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
