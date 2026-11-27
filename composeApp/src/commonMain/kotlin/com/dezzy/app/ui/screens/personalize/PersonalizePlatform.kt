/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Qué se puede elegir de la galería. */
enum class MediaKind { IMAGE, VIDEO, IMAGE_OR_VIDEO }

/**
 * Devuelve una función que abre el selector de la galería; al elegir llama a [onPicked] con la URI
 * (`content://…` en Android, `file:///…` en escritorio) y su tipo MIME si se conoce.
 */
@Composable
expect fun rememberMediaPicker(onPicked: (uri: String, mime: String?) -> Unit): (MediaKind) -> Unit

/** Vídeo mudo, en bucle y recortado a pantalla, para el fondo de la app. Escritorio: no pinta nada. */
@Composable
expect fun WallpaperVideo(uri: String, modifier: Modifier)

/** Extensión de archivo para un tipo MIME de imagen o vídeo (jpg, png, mp4…). */
internal fun extensionForMime(mime: String?, isVideo: Boolean): String = when (mime?.lowercase()) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/avif" -> "avif"
    "image/jpeg", "image/jpg" -> "jpg"
    "video/webm" -> "webm"
    "video/mp4" -> "mp4"
    else -> if (isVideo) "mp4" else "jpg"
}
