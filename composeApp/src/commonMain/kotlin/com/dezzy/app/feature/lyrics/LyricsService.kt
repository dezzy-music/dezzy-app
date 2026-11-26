/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import com.dezzy.app.source.youtube.YouTubeMusicClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Busca la letra: LRCLIB, SimpMusic, BetterLyrics, KuGou y, al final, la letra sin tiempos del
 * catálogo de YouTube Music. Se para en la primera sincronizada; si sólo hay letras sin tiempos
 * se queda con la primera. Caché en memoria de 200 canciones.
 */
class LyricsService(
    private val http: HttpClient,
    private val yt: YouTubeMusicClient,
) {
    private val cache = LinkedHashMap<String, LyricsResult>()
    private val lock = Mutex()

    private val noiseInTitle = Regex("""\s*[(\[](official|video|audio|lyric|letra)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /** [skipCache] = true vuelve a pedirla aunque esté en caché (reintentar). */
    suspend fun fetch(q: LyricsQuery, skipCache: Boolean = false): LyricsResult? {
        val key = q.videoId ?: "${q.title}|${q.artists.joinToString(", ")}"
        if (!skipCache) {
            lock.withLock {
                val hit = cache.remove(key)
                if (hit != null) {
                    cache[key] = hit
                    return hit
                }
            }
        }
        val lookup = LyricsLookup(
            videoId = q.videoId,
            title = q.title.replace(noiseInTitle, "").trim(),
            artist = q.artists.firstOrNull()?.trim().orEmpty(),
            album = q.album,
            duration = q.durationSec?.takeIf { it > 0 },
        )
        var result: LyricsResult? = null
        var plainOnly: LyricsResult? = null
        for ((_, provider) in lyricsProviders) {
            try {
                val r = provider(http, lookup)
                if (r != null && !r.synced.isNullOrEmpty()) {
                    result = r
                    break
                }
                if (r != null && plainOnly == null) plainOnly = r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Una fuente que falla no corta la búsqueda.
            }
        }
        if (result == null) result = plainOnly
        if (result == null && q.videoId != null) {
            val text = try {
                yt.getLyrics(q.videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (!text.isNullOrBlank()) result = LyricsResult(null, text.trim(), "YouTube Music")
        }
        val found = result ?: return null
        lock.withLock {
            cache.remove(key)
            cache[key] = found
            while (cache.size > 200) cache.remove(cache.keys.first())
        }
        return found
    }
}
