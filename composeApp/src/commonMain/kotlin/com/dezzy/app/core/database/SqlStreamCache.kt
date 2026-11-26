/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.database

import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.source.stream.AudioQuality
import com.dezzy.app.source.stream.StreamAudio
import com.dezzy.app.source.stream.StreamCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/** Caché de streams en la tabla `stream_cache`: sobrevive a reinicios de la app. */
class SqlStreamCache(
    private val db: DezzyDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : StreamCache {

    private val q get() = db.dezzyQueries
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(videoId: String, quality: AudioQuality, now: Long): StreamAudio? = withContext(io) {
        val row = q.getStream(videoId, quality.name).executeAsOneOrNull() ?: return@withContext null
        if (now >= row.expires_at) return@withContext null
        runCatching { json.decodeFromString(StreamAudio.serializer(), row.payload) }.getOrNull()
    }

    override suspend fun put(quality: AudioQuality, audio: StreamAudio) = withContext(io) {
        db.transaction {
            q.purgeExpiredStreams(now())
            q.putStream(audio.videoId, quality.name, json.encodeToString(StreamAudio.serializer(), audio), audio.expiresAtMillis)
        }
    }

    override suspend fun remove(videoId: String) = withContext(io) { q.deleteStream(videoId) }

    override suspend fun clear() = withContext(io) { q.clearStreams() }
}
