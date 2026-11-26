/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import com.dezzy.app.core.model.Track
import kotlinx.serialization.Serializable

enum class DownloadStatus { RESOLVING, QUEUED, SAVING, DONE, ERROR }

@Serializable
data class LosslessRef(
    val provider: String, // "dzr"
    val trackId: String,
    val isrc: String? = null,
    val quality: String, // "flac" | "mp3_320"
)

@Serializable
data class DownloadItem(
    val id: String, // = track.videoId
    val track: Track,
    val status: DownloadStatus,
    val progress: Float = 0f, // 0..1
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val format: String? = null, // "Opus", "AAC", "FLAC", "MP3 320"
    val filePath: String? = null, // ruta absoluta cuando DONE
    val error: String? = null,
    val attempts: Int = 0,
    val addedAt: Long,
    val finishedAt: Long? = null,
    val lossless: LosslessRef? = null,
)

data class DownloadsState(
    val items: List<DownloadItem> = emptyList(),
    val paused: Boolean = false,
    val usedBytes: Long = 0,
    val freeBytes: Long? = null,
)

/** Lo implementa el escritor B (lossless). */
data class LosslessDownload(
    val url: String,
    val format: String, // "FLAC" | "MP3 320"
    val ext: String, // "flac" | "mp3"
    val sizeBytes: Long?,
)

interface LosslessUrlProvider {
    suspend fun resolve(ref: LosslessRef, track: Track): LosslessDownload
}

/** Lo implementa el escritor B: etiquetas (y copia pública si toca) tras guardar. Fallar aquí NO marca error la descarga. */
interface DownloadPostProcessor {
    suspend fun process(item: DownloadItem, filePath: String)
}

/** Fallo de descarga con mensaje para el usuario; [retry] = false no se reintenta solo. */
internal class DownloadFailure(message: String, val retry: Boolean = true) : Exception(message)
