/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import androidx.compose.ui.graphics.ImageBitmap

/** Codifica una imagen como PNG (vacío si la plataforma no puede). */
expect fun encodePng(image: ImageBitmap): ByteArray

/** Abre el menú de compartir del sistema con la imagen [png] (y [text] si hay). */
expect fun shareImage(png: ByteArray, fileName: String, text: String?)

/** Guarda la imagen [png] en la galería; devuelve si se pudo. */
expect fun saveImage(png: ByteArray, fileName: String): Boolean
