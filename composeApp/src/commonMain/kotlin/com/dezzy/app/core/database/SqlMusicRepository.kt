/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Repositorio de la biblioteca sobre SQLDelight. Persiste entre arranques.
 *
 * @param now reloj en milisegundos UTC; se inyecta para las pruebas.
 */
class SqlMusicRepository(
    private val db: DezzyDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : MusicRepository {

    private val q get() = db.dezzyQueries
    private val json = Json { ignoreUnknownKeys = true }

    // Favoritos ------------------------------------------------------------

    override fun getFavorites(): Flow<List<Track>> =
        q.favorites(::trackOf).asFlow().mapToList(io)

    override suspend fun isFavorite(videoId: String): Boolean = withContext(io) {
        q.isFavorite(videoId).executeAsOne() > 0
    }

    override suspend fun setFavorite(track: Track, favorite: Boolean) = withContext(io) {
        val id = track.videoId ?: return@withContext
        db.transaction {
            if (favorite) {
                upsert(track)
                q.addFavorite(id, now())
            } else {
                q.removeFavorite(id)
            }
        }
    }

    // Historial ------------------------------------------------------------

    override fun getHistory(limit: Int): Flow<List<Track>> =
        q.recentTracks(limit.toLong(), ::trackOf).asFlow().mapToList(io)

    override suspend fun addToHistory(track: Track) = withContext(io) {
        val id = track.videoId ?: return@withContext
        db.transaction {
            upsert(track)
            q.addPlay(id, now())
        }
    }

    override suspend fun clearHistory() = withContext(io) { q.clearPlays() }

    // Playlists ------------------------------------------------------------

    override fun getPlaylists(): Flow<List<Playlist>> =
        q.playlists { id, title, author, thumbnail, _, trackCount ->
            Playlist(id = id, title = title, author = author, thumbnail = thumbnail, trackCount = trackCount.toInt())
        }.asFlow().mapToList(io)

    override suspend fun getPlaylistById(id: String): Playlist? = withContext(io) {
        val row = q.playlistById(id).executeAsOneOrNull() ?: return@withContext null
        val tracks = q.playlistTracks(id, ::trackOf).executeAsList()
        Playlist(
            id = row.id,
            title = row.title,
            author = row.author,
            thumbnail = row.thumbnail,
            trackCount = tracks.size,
            tracks = tracks,
        )
    }

    override suspend fun createPlaylist(title: String, author: String?): Playlist = withContext(io) {
        val createdAt = now()
        val id = "pl_${createdAt}_${Random.nextInt(1000, 9999)}"
        val resolvedAuthor = author ?: "Dezzy"
        q.insertPlaylist(id, title, resolvedAuthor, null, createdAt)
        Playlist(id = id, title = title, author = resolvedAuthor)
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track) = withContext(io) {
        val id = track.videoId ?: return@withContext
        db.transaction {
            if (q.playlistById(playlistId).executeAsOneOrNull() == null) return@transaction
            upsert(track)
            val position = q.nextPosition(playlistId).executeAsOne() ?: 0L
            q.addEntry(playlistId, position, id)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, videoId: String) = withContext(io) {
        q.removeEntriesForVideo(playlistId, videoId)
    }

    override suspend fun deletePlaylist(playlistId: String) = withContext(io) {
        db.transaction {
            q.deletePlaylistEntries(playlistId)
            q.deletePlaylist(playlistId)
        }
    }

    // Caché de catálogo ----------------------------------------------------

    override suspend fun saveTrack(track: Track) = withContext(io) {
        if (track.videoId == null) return@withContext
        db.transaction { upsert(track) }
    }

    override suspend fun getTrack(videoId: String): Track? = withContext(io) {
        q.trackById(videoId, ::trackOf).executeAsOneOrNull()
    }

    override suspend fun saveAlbum(album: Album) = withContext(io) {
        q.putCatalog(KIND_ALBUM, album.id, json.encodeToString(Album.serializer(), album), now())
    }

    override suspend fun getAlbum(id: String): Album? = withContext(io) {
        q.getCatalog(KIND_ALBUM, id).executeAsOneOrNull()
            ?.let { runCatching { json.decodeFromString(Album.serializer(), it) }.getOrNull() }
    }

    override suspend fun saveArtist(artist: Artist) = withContext(io) {
        q.putCatalog(KIND_ARTIST, artist.id, json.encodeToString(Artist.serializer(), artist), now())
    }

    override suspend fun getArtist(id: String): Artist? = withContext(io) {
        q.getCatalog(KIND_ARTIST, id).executeAsOneOrNull()
            ?.let { runCatching { json.decodeFromString(Artist.serializer(), it) }.getOrNull() }
    }

    // ---------------------------------------------------------------------

    /** Inserta o actualiza sin borrar datos que la versión nueva no trae. Llamar dentro de una transacción. */
    private fun upsert(track: Track) {
        val id = track.videoId ?: return
        val t = now()
        q.insertTrackIfAbsent(id, track.title, track.artists, track.album, track.thumbnail, track.durationSeconds, track.spotifyId, t)
        q.updateTrack(
            title = track.title,
            artists = track.artists,
            album = track.album,
            thumbnail = track.thumbnail,
            durationSeconds = track.durationSeconds,
            spotifyId = track.spotifyId,
            updatedAt = t,
            videoId = id,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun trackOf(
        videoId: String,
        title: String,
        artists: String,
        album: String?,
        thumbnail: String?,
        durationSeconds: Long?,
        spotifyId: String?,
        updatedAt: Long,
    ) = Track(
        videoId = videoId,
        title = title,
        artists = artists,
        album = album,
        thumbnail = thumbnail,
        durationSeconds = durationSeconds,
        spotifyId = spotifyId,
    )

    private companion object {
        const val KIND_ALBUM = "album"
        const val KIND_ARTIST = "artist"
    }
}
