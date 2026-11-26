/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Lector de códigos QR a pantalla completa. En Android abre la cámara (CameraX + ZXing) y pide el
 * permiso si hace falta; en el escritorio muestra un campo para pegar el contenido del código.
 * [onResult] recibe el texto leído (una sola vez); [onClose] se llama al cancelar.
 */
@Composable
expect fun QrScanner(onResult: (String) -> Unit, onClose: () -> Unit)

/** Matriz del QR de [text] (true = módulo oscuro), con ZXing; para pintarlo con Canvas. */
expect fun qrMatrix(text: String): Array<BooleanArray>
