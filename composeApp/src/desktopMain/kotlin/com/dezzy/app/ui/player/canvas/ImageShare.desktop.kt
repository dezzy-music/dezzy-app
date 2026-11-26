/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import androidx.compose.ui.graphics.ImageBitmap

// El escritorio de Compose es sólo de pruebas: no comparte ni guarda imágenes.

actual fun encodePng(image: ImageBitmap): ByteArray = ByteArray(0)

actual fun shareImage(png: ByteArray, fileName: String, text: String?) = Unit

actual fun saveImage(png: ByteArray, fileName: String): Boolean = false
