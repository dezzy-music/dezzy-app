/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

// El escritorio de Compose es sólo de pruebas: no comparte imágenes ni crea vídeos.

actual fun sharePngTo(png: ByteArray, fileName: String, text: String?, target: ShareTarget) = Unit

actual val canvasExportSupported: Boolean = false

actual suspend fun exportCanvasVideo(request: CanvasExportRequest): String? = null

actual fun shareVideoTo(uri: String, text: String?, target: ShareTarget) = Unit
