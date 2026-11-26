/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.database

import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    // Favoritos
    fun getFavorites(): Flow<List<Track>>
    suspend fun isFavorite(videoId: String): Boolean
    suspend fun setFavorite(track: Track, favorite: Boolean)

    // Historial
    fun getHistory(limit: Int = 100): Flow<List<Track>>
    suspend fun addToHistory(track: Track)
    suspend fun clearHistory()

    // Inicio: marcación rápida, favoritos olvidados, racha y minutos de hoy
    fun getMostPlayed(sinceMs: Long, limit: Int = 24): Flow<List<Track>>
    fun getForgottenFavorites(notPlayedSinceMs: Long, limit: Int = 20): Flow<List<Track>>
    /** Días seguidos (hora local) con algo escuchado, contando hoy o, si hoy aún no, desde ayer. */
    suspend fun listenStreakDays(utcOffsetMs: Long): Int
    /** Segundos escuchados desde la medianoche local. */
    suspend fun todayListenSeconds(utcOffsetMs: Long): Long
    suspend fun listenedSecondsSince(videoId: String, sinceMs: Long): Long

    // Playlists
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun createPlaylist(title: String, author: String? = null): Playlist
    suspend fun addTrackToPlaylist(playlistId: String, track: Track)
    suspend fun removeTrackFromPlaylist(playlistId: String, videoId: String)
    suspend fun deletePlaylist(playlistId: String)

    // Caché de Catálogo
    suspend fun saveTrack(track: Track)
    suspend fun getTrack(videoId: String): Track?
    suspend fun saveAlbum(album: Album)
    suspend fun getAlbum(id: String): Album?
    suspend fun saveArtist(artist: Artist)
    suspend fun getArtist(id: String): Artist?
}
