/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

/** A dónde va lo que se comparte desde la hoja de Compartir. */
enum class ShareTarget { Chooser, Stories, WhatsApp }

/** Comparte la imagen [png] en [target]; si esa app no está, abre el menú del sistema. */
expect fun sharePngTo(png: ByteArray, fileName: String, text: String?, target: ShareTarget)

/** true si esta plataforma sabe crear el vídeo del Canvas. */
expect val canvasExportSupported: Boolean

/**
 * Vídeo vertical (720×1280) del Canvas: el trozo en bucle del videoclip con [durationMs] de la
 * canción [videoId] desde [audioStartMs]. Con [branded], el vídeo va dentro de la tarjeta (marco del
 * color [frameColor], título, artista y «★ Dezzy»); sin él, el Canvas ocupa todo el vídeo.
 */
class CanvasExportRequest(
    val videoUrl: String,
    val loopStartMs: Long,
    val loopLengthMs: Long,
    val videoId: String?,
    val audioStartMs: Long,
    val durationMs: Long,
    val branded: Boolean,
    val frameColor: Int,
    val gradient: Boolean,
    val title: String,
    val artist: String,
    val fileName: String,
)

/** Crea el vídeo y lo guarda en la galería (Películas/Dezzy). Devuelve su URI, o null si falla. */
expect suspend fun exportCanvasVideo(request: CanvasExportRequest): String?

/** Comparte el vídeo ya guardado [uri] en [target]; si esa app no está, abre el menú del sistema. */
expect fun shareVideoTo(uri: String, text: String?, target: ShareTarget)
