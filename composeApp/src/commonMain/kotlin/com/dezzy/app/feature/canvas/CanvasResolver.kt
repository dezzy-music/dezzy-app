/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.canvas

import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.video.MusicVideoResolver
import com.dezzy.app.feature.video.VideoError
import com.dezzy.app.ui.player.kit.CanvasArtwork
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** Trozo del videoclip que se repite: desde [startMs], durante [lengthMs] (0 = hasta el final). */
data class CanvasLoop(val startMs: Long, val lengthMs: Long)

/**
 * Canvas de una canción: un trozo corto (8 s) del videoclip, sin sonido, a 360p, que empieza hacia
 * el 35 % de su duración (suele ser el estribillo). Es el mismo método de la versión de escritorio.
 * El videoclip se busca y se resuelve con [MusicVideoResolver]; aquí sólo se elige el trozo y se
 * guarda en memoria por videoId (null = no hay videoclip). Nunca lanza.
 */
class CanvasResolver(private val videos: MusicVideoResolver) {
    private class Entry(val value: CanvasArtwork?, val expiresAtMs: Long)

    private val cache = LinkedHashMap<String, Entry>()
    private val loops = LinkedHashMap<String, CanvasLoop>()
    private val mutex = Mutex()

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Trozo en bucle de una URL devuelta por [resolve]; null si no se conoce (se reproduce entero). */
    suspend fun loopFor(url: String?): CanvasLoop? {
        if (url == null) return null
        return mutex.withLock { loops[url] }
    }

    suspend fun forTrack(track: Track, maxHeight: Int = QUALITY): CanvasArtwork? =
        resolve(
            mediaId = track.videoId,
            title = track.title,
            artist = track.artists,
            durationSec = track.durationSeconds?.toInt(),
            maxHeight = maxHeight,
        )

    /** Canvas de la canción, o null si no tiene videoclip. Nunca lanza. */
    suspend fun resolve(
        mediaId: String?,
        title: String?,
        artist: String?,
        durationSec: Int?,
        maxHeight: Int = QUALITY,
    ): CanvasArtwork? {
        val id = mediaId?.takeIf { it.isNotBlank() }
        val name = title?.takeIf { it.isNotBlank() }
        if (id == null && name == null) return null
        val key = (id ?: "m:${name.orEmpty().lowercase()}|${artist.orEmpty().lowercase()}") + ":$maxHeight"

        mutex.withLock {
            val hit = cache[key]
            if (hit != null) {
                if (hit.expiresAtMs > nowMs()) {
                    cache.remove(key)
                    cache[key] = hit // orden de uso
                    return hit.value
                }
                cache.remove(key)
            }
        }

        return try {
            val result = if (id != null) {
                videos.resolveForSong(id, name, artist, durationSec, maxHeight)
            } else {
                videos.resolveByMetadata(name.orEmpty(), artist, durationSec, maxHeight)
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            val video = result.getOrNull()?.takeUnless { it.isArtTrack }
            val url = video?.silentVideoUrl()
            if (video == null || url.isNullOrBlank()) {
                val transient = error is VideoError.Network || error is VideoError.RateLimited
                store(key, null, nowMs() + if (transient) SHORT_MISS_MS else MISS_MS)
                null
            } else {
                val totalSec = video.lengthSeconds ?: durationSec ?: 0
                val loop = if (totalSec > LOOP_SECONDS * 3) {
                    CanvasLoop(startMs = (totalSec * 0.35).toLong() * 1000L, lengthMs = LOOP_SECONDS * 1000L)
                } else if (totalSec > LOOP_SECONDS) {
                    CanvasLoop(startMs = 0L, lengthMs = LOOP_SECONDS * 1000L)
                } else {
                    CanvasLoop(startMs = 0L, lengthMs = 0L)
                }
                val artwork = CanvasArtwork(
                    name = name,
                    artist = artist,
                    animated = url,
                    animatedVertical = url,
                    videoUrl = url,
                    videoUrlVertical = url,
                    source = CanvasArtwork.SOURCE_APPLE,
                    loopStartMs = loop.startMs,
                    loopLengthMs = loop.lengthMs,
                )
                mutex.withLock {
                    loops[url] = loop
                    while (loops.size > MAX_ENTRIES) loops.remove(loops.keys.first())
                }
                store(key, artwork, minOf(video.expiresAtMs, nowMs() + HIT_MAX_MS))
                artwork
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun store(key: String, value: CanvasArtwork?, expiresAtMs: Long) = mutex.withLock {
        cache.remove(key)
        cache[key] = Entry(value, expiresAtMs)
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
    }

    companion object {
        /** Altura del vídeo del canvas (360p; el de la cabecera del artista usa 480). */
        const val QUALITY = 360
        private const val LOOP_SECONDS = 8
        private const val MAX_ENTRIES = 128
        private const val MISS_MS = 30L * 60 * 1000
        private const val SHORT_MISS_MS = 60L * 1000
        private const val HIT_MAX_MS = 5L * 60 * 60 * 1000
    }
}
