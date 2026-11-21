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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class DefaultMusicRepository : MusicRepository {
    private val mutex = Mutex()

    private val _favorites = MutableStateFlow<Map<String, Track>>(emptyMap())
    private val _history = MutableStateFlow<List<Track>>(emptyList())
    private val _playlists = MutableStateFlow<Map<String, Playlist>>(emptyMap())
    private val _tracksCache = MutableStateFlow<Map<String, Track>>(emptyMap())
    private val _albumsCache = MutableStateFlow<Map<String, Album>>(emptyMap())
    private val _artistsCache = MutableStateFlow<Map<String, Artist>>(emptyMap())

    override fun getFavorites(): Flow<List<Track>> {
        return _favorites.map { it.values.toList() }
    }

    override suspend fun isFavorite(videoId: String): Boolean = mutex.withLock {
        _favorites.value.containsKey(videoId)
    }

    override suspend fun setFavorite(track: Track, favorite: Boolean) = mutex.withLock {
        val key = track.videoId ?: return@withLock
        val current = _favorites.value.toMutableMap()
        if (favorite) {
            current[key] = track
        } else {
            current.remove(key)
        }
        _favorites.value = current
    }

    override fun getHistory(limit: Int): Flow<List<Track>> {
        return _history.map { it.take(limit) }
    }

    override suspend fun addToHistory(track: Track) = mutex.withLock {
        val current = _history.value.toMutableList()
        // Remover duplicado anterior si existe para ponerlo al principio
        current.removeAll { it.videoId == track.videoId }
        current.add(0, track)
        _history.value = current
    }

    override suspend fun clearHistory() = mutex.withLock {
        _history.value = emptyList()
    }

    override fun getPlaylists(): Flow<List<Playlist>> {
        return _playlists.map { it.values.toList() }
    }

    override suspend fun getPlaylistById(id: String): Playlist? = mutex.withLock {
        _playlists.value[id]
    }

    override suspend fun createPlaylist(title: String, author: String?): Playlist = mutex.withLock {
        val id = "pl_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        val playlist = Playlist(
            id = id,
            title = title,
            author = author ?: "Dezzy",
            trackCount = 0,
            tracks = emptyList()
        )
        val current = _playlists.value.toMutableMap()
        current[id] = playlist
        _playlists.value = current
        playlist
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track) = mutex.withLock {
        val playlist = _playlists.value[playlistId] ?: return@withLock
        val newTracks = playlist.tracks + track
        val updated = playlist.copy(
            tracks = newTracks,
            trackCount = newTracks.size
        )
        val current = _playlists.value.toMutableMap()
        current[playlistId] = updated
        _playlists.value = current
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, videoId: String) = mutex.withLock {
        val playlist = _playlists.value[playlistId] ?: return@withLock
        val newTracks = playlist.tracks.filterNot { it.videoId == videoId }
        val updated = playlist.copy(
            tracks = newTracks,
            trackCount = newTracks.size
        )
        val current = _playlists.value.toMutableMap()
        current[playlistId] = updated
        _playlists.value = current
    }

    override suspend fun deletePlaylist(playlistId: String) = mutex.withLock {
        val current = _playlists.value.toMutableMap()
        current.remove(playlistId)
        _playlists.value = current
    }

    override suspend fun saveTrack(track: Track) = mutex.withLock {
        val id = track.videoId ?: return@withLock
        val current = _tracksCache.value.toMutableMap()
        current[id] = track
        _tracksCache.value = current
    }

    override suspend fun getTrack(videoId: String): Track? = mutex.withLock {
        _tracksCache.value[videoId]
    }

    override suspend fun saveAlbum(album: Album) = mutex.withLock {
        val current = _albumsCache.value.toMutableMap()
        current[album.id] = album
        _albumsCache.value = current
    }

    override suspend fun getAlbum(id: String): Album? = mutex.withLock {
        _albumsCache.value[id]
    }

    override suspend fun saveArtist(artist: Artist) = mutex.withLock {
        val current = _artistsCache.value.toMutableMap()
        current[artist.id] = artist
        _artistsCache.value = current
    }

    override suspend fun getArtist(id: String): Artist? = mutex.withLock {
        _artistsCache.value[id]
    }
}
