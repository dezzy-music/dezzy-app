/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import app.cash.sqldelight.db.SqlDriver
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una reproducción del historial. */
class DjPlayEvent(val videoId: String, val playedAt: Long)

/** Un salto del oyente. */
class DjSkipRow(val videoId: String, val artist: String?, val skippedAt: Long)

/**
 * Lectura del historial y de los saltos del oyente (tabla `dj_skip_event` de Dj.sq).
 * La tabla se crea también aquí (IF NOT EXISTS) por si la base ya existía antes de la fase 10:
 * el esquema de SQLDelight sólo se ejecuta al crear la base.
 */
class DjStore(db: DezzyDatabase, driver: SqlDriver) {

    private val q = db.djQueries

    init {
        for (sql in SCHEMA) runCatching { driver.execute(null, sql, 0) }
    }

    suspend fun recordSkip(videoId: String, artist: String?, at: Long = DjClock.nowMs()) = withContext(Dispatchers.Default) {
        q.insertSkip(videoId, artist, at)
        // Un año de saltos es más que suficiente para el perfil.
        q.pruneSkips(at - 400 * DjClock.DAY_MS)
    }

    suspend fun skips(sinceMs: Long): List<DjSkipRow> = withContext(Dispatchers.Default) {
        q.skipsSince(sinceMs) { id, artist, at -> DjSkipRow(id, artist, at) }.executeAsList()
    }

    suspend fun clearSkips() = withContext(Dispatchers.Default) { q.clearSkips() }

    suspend fun playEvents(fromMs: Long): List<DjPlayEvent> = withContext(Dispatchers.Default) {
        q.tasteEvents(fromMs) { id, at -> DjPlayEvent(id, at) }.executeAsList()
    }

    suspend fun likedIds(): Set<String> = withContext(Dispatchers.Default) { q.likedIds().executeAsList().toSet() }

    suspend fun isLiked(videoId: String): Boolean = withContext(Dispatchers.Default) {
        q.isLiked(videoId).executeAsOne() > 0L
    }

    suspend fun tracks(ids: Collection<String>): Map<String, Track> = withContext(Dispatchers.Default) {
        val out = HashMap<String, Track>()
        // SQLite limita las variables de una consulta: se pide por tandas.
        ids.distinct().chunked(400).forEach { chunk ->
            q.tracksByIds(chunk, ::toTrack).executeAsList().forEach { t -> t.videoId?.let { out[it] = t } }
        }
        out
    }

    suspend fun topPlayed(limit: Int): List<Track> = withContext(Dispatchers.Default) {
        q.topPlayed(limit.toLong(), ::toTrack).executeAsList()
    }

    suspend fun recentlyPlayed(limit: Int): List<Track> = withContext(Dispatchers.Default) {
        q.recentlyPlayed(limit.toLong(), ::toTrack).executeAsList()
    }

    private fun toTrack(id: String, title: String, artists: String, album: String?, thumbnail: String?, duration: Long?) =
        Track(videoId = id, title = title, artists = artists, album = album, thumbnail = thumbnail, durationSeconds = duration)

    private companion object {
        val SCHEMA = listOf(
            """
            CREATE TABLE IF NOT EXISTS dj_skip_event (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL,
                artist TEXT,
                skipped_at INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS dj_skip_event_at ON dj_skip_event(skipped_at)",
        )
    }
}
