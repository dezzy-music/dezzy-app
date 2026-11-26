/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.dezzy.app.source.stream.AudioQuality
import com.dezzy.app.source.stream.StreamPriority
import com.dezzy.app.source.stream.StreamUrlResolver
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/**
 * Fuente de audio de ExoPlayer para `dezzy://stream/<videoId>`:
 * - si la canción está descargada, la abre del archivo local;
 * - si es lossless (`dzr:`) y no está descargada, usa la URL del `/stream` del resolver del usuario;
 * - si no, pide la URL al resolvedor y la abre por HTTP. Si YouTube contesta 403/410 (URL caducada
 *   o de otra IP), vuelve a resolver una vez con `force` y reintenta.
 */
@OptIn(UnstableApi::class)
class StreamDataSource(
    private val resolver: StreamUrlResolver,
    private val upstream: HttpDataSource,
    private val quality: () -> AudioQuality,
    private val localFile: (String) -> String? = { null },
    private val losslessUrl: suspend (String) -> String? = { null },
) : DataSource {

    private var uri: Uri? = null
    private var active: DataSource = upstream
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val videoId = videoIdOf(dataSpec.uri)
        if (videoId == null) {
            active = upstream
            return upstream.open(dataSpec).also { uri = dataSpec.uri }
        }
        uri = dataSpec.uri

        localFile(videoId)?.let { path ->
            val file = FileDataSource()
            listeners.forEach(file::addTransferListener)
            try {
                val length = file.open(dataSpec.withUri(Uri.fromFile(File(path))))
                active = file
                return length
            } catch (e: IOException) {
                runCatching { file.close() } // archivo ilegible: se sigue por red
            }
        }

        active = upstream
        if (videoId.startsWith(LOSSLESS_PREFIX)) {
            val url = runBlocking { losslessUrl(videoId) }
                ?: throw IOException("No se pudo obtener el audio lossless (¿resolver configurado?)")
            return upstream.open(dataSpec.withUri(Uri.parse(url)))
        }
        return try {
            upstream.open(dataSpec.withUri(Uri.parse(resolve(videoId, force = false))))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != 403 && e.responseCode != 410) throw e
            upstream.close()
            upstream.open(dataSpec.withUri(Uri.parse(resolve(videoId, force = true))))
        }
    }

    private fun resolve(videoId: String, force: Boolean): String = runBlocking {
        resolver.resolveStream(videoId, StreamPriority.PLAYBACK, quality(), force)
    }.getOrElse { throw IOException(it.message ?: "No se pudo resolver $videoId", it) }.url

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun close() {
        uri = null
        val current = active
        active = upstream
        current.close()
        if (current !== upstream) upstream.close()
    }

    class Factory(
        private val resolver: StreamUrlResolver,
        private val upstream: HttpDataSource.Factory,
        private val quality: () -> AudioQuality,
        private val localFile: (String) -> String? = { null },
        private val losslessUrl: suspend (String) -> String? = { null },
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            StreamDataSource(resolver, upstream.createDataSource(), quality, localFile, losslessUrl)
    }

    companion object {
        const val SCHEME = "dezzy"
        const val LOSSLESS_PREFIX = "dzr:"

        fun uriFor(videoId: String): Uri = Uri.parse("$SCHEME://stream/$videoId")

        fun videoIdOf(uri: Uri): String? =
            if (uri.scheme == SCHEME && uri.host == "stream") uri.lastPathSegment?.takeIf { it.isNotBlank() } else null
    }
}
