/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import app.cash.sqldelight.db.SqlDriver
import com.dezzy.app.core.database.db.DezzyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Canción de la biblioteca con su peso (una reproducción vale 1, un favorito 3). */
class LibraryTrackRow(
    val videoId: String,
    val title: String,
    val artists: String,
    val thumbnail: String?,
    val weight: Long,
)

/** Una canción de las más reproducidas. */
class PlayedTrackRow(
    val videoId: String,
    val title: String,
    val artists: String,
    val thumbnail: String?,
    val plays: Long,
)

/**
 * Caché local del mapa musical (tabla `music_map_cache` de MusicMap.sq): clave, valor de texto y
 * fecha. Quien lee decide cuánto vale cada fila. La tabla se crea también aquí (IF NOT EXISTS)
 * por si la base ya existía antes de la fase 10.
 */
class MusicMapStore(db: DezzyDatabase, driver: SqlDriver) {

    private val q = db.musicMapQueries

    init {
        runCatching {
            driver.execute(
                null,
                "CREATE TABLE IF NOT EXISTS music_map_cache (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL, saved_at INTEGER NOT NULL)",
                0,
            )
        }
    }

    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Valor guardado, o null si no existe o tiene más de [maxAgeMs] (sin límite si es null). */
    suspend fun get(key: String, maxAgeMs: Long? = null): String? = withContext(Dispatchers.Default) {
        runCatching {
            val value = q.mapCacheValue(key).executeAsOneOrNull() ?: return@runCatching null
            if (maxAgeMs != null) {
                val at = q.mapCacheSavedAt(key).executeAsOneOrNull() ?: 0L
                if (nowMs() - at > maxAgeMs) return@runCatching null
            }
            value
        }.getOrNull()
    }

    suspend fun put(key: String, value: String) = withContext(Dispatchers.Default) {
        runCatching { q.mapCachePut(key, value, nowMs()) }
        Unit
    }

    suspend fun delete(key: String) = withContext(Dispatchers.Default) {
        runCatching { q.mapCacheDelete(key) }
        Unit
    }

    suspend fun deleteLike(pattern: String) = withContext(Dispatchers.Default) {
        runCatching { q.mapCacheDeleteLike(pattern) }
        Unit
    }

    suspend fun savedAt(key: String): Long? = withContext(Dispatchers.Default) {
        runCatching { q.mapCacheSavedAt(key).executeAsOneOrNull() }.getOrNull()
    }

    suspend fun libraryTracks(limit: Long = 400): List<LibraryTrackRow> = withContext(Dispatchers.Default) {
        runCatching {
            q.mapLibraryTracks(limit) { videoId, title, artists, thumbnail, weight ->
                LibraryTrackRow(videoId, title, artists, thumbnail, weight)
            }.executeAsList()
        }.getOrDefault(emptyList())
    }

    suspend fun mostPlayed(limit: Long = 100): List<PlayedTrackRow> = withContext(Dispatchers.Default) {
        runCatching {
            q.mapMostPlayed(limit) { videoId, title, artists, thumbnail, plays ->
                PlayedTrackRow(videoId, title, artists, thumbnail, plays)
            }.executeAsList()
        }.getOrDefault(emptyList())
    }
}
