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
