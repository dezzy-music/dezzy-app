/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.downloads.DownloadItem
import com.dezzy.app.feature.downloads.DownloadPostProcessor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Tras guardar una descarga lossless: etiqueta el FLAC (TITLE, ARTIST, ALBUM, ISRC y portada JPEG) y,
 * si [LosslessExportToPublicKey] está activo, copia el archivo a Música/Dezzy (sólo Android).
 *
 * Como la cola acotada de Dezzy 3.x: como mucho [MAX_CONCURRENT] trabajos a la vez (el semáforo es FIFO),
 * una ruta ya en curso no se repite y un fallo nunca se propaga (el gestor no marca error por esto).
 */
class LosslessPostProcessor(
    private val http: HttpClient,
    private val prefs: DezzyPreferences,
    private val client: LosslessClient,
    private val copier: PublicMusicCopier,
) : DownloadPostProcessor {
    private val permits = Semaphore(MAX_CONCURRENT)
    private val lock = Mutex()
    private val inFlight = HashSet<String>()

    override suspend fun process(item: DownloadItem, filePath: String) {
        val ref = item.lossless ?: return
        if (!lock.withLock { inFlight.add(filePath) }) return
        try {
            permits.withPermit {
                try {
                    finish(item, ref.trackId, ref.isrc, filePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Fallar aquí no marca error la descarga.
                }
            }
        } finally {
            lock.withLock { inFlight.remove(filePath) }
        }
    }

    private suspend fun finish(item: DownloadItem, trackId: String, refIsrc: String?, filePath: String) {
        val isFlac = filePath.endsWith(".flac", ignoreCase = true)
        if (isFlac) tag(item, trackId, refIsrc, filePath)
        if (prefs.value(LosslessExportToPublicKey, LOSSLESS_EXPORT_PUBLIC_DEFAULT).first()) {
            val name = filePath.substringAfterLast('/').substringAfterLast('\\')
            val mime = if (isFlac) "audio/flac" else "audio/mpeg"
            copier.copy(filePath, name, mime)
        }
    }

    private suspend fun tag(item: DownloadItem, trackId: String, refIsrc: String?, filePath: String) {
        val head = readFileHead(filePath, FlacTagger.MAX_HEAD_BYTES) ?: return
        val track = item.track
        val isrc = refIsrc ?: client.isrc(trackId)
        val tags = LinkedHashMap<String, String>()
        tags["TITLE"] = track.title
        tags["ARTIST"] = track.artists
        track.album?.takeIf { it.isNotBlank() }?.let { tags["ALBUM"] = it }
        isrc?.takeIf { it.isNotBlank() }?.let { tags["ISRC"] = it }
        val cover = track.thumbnail?.takeIf { it.startsWith("http") }?.let { downloadCover(it) }
        val rewrite = FlacTagger.apply(head, tags, cover) ?: return
        replaceFileHead(filePath, rewrite.oldHeadLength, rewrite.head)
    }

    /** Portada JPEG (máx. 8 MB); null si falla o no es JPEG. */
    private suspend fun downloadCover(url: String): ByteArray? = try {
        val bytes = withTimeout(30_000) { http.get(url).body<ByteArray>() }
        bytes.takeIf { it.size in 4..MAX_COVER_BYTES && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_CONCURRENT = 2
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
    }
}
