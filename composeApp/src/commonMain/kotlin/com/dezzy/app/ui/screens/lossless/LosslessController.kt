/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.lossless

import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.downloads.DownloadItem
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.lossless.LosslessAlbum
import com.dezzy.app.feature.lossless.LosslessClient
import com.dezzy.app.feature.lossless.LosslessDefaultQualityKey
import com.dezzy.app.feature.lossless.LosslessQuality
import com.dezzy.app.feature.lossless.LosslessRecentSearchesKey
import com.dezzy.app.feature.lossless.LosslessTrack
import com.dezzy.app.feature.lossless.ref
import com.dezzy.app.feature.lossless.toTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal enum class LosslessMode { TRACKS, ALBUMS }

private const val MaxRecentSearches = 10

internal fun losslessQualityFromWire(wire: String): LosslessQuality =
    LosslessQuality.entries.firstOrNull { it.wire == wire } ?: LosslessQuality.FLAC

/** Id con el que el gestor de descargas guarda una pista lossless. */
internal fun LosslessTrack.downloadId(): String = "dzr:$id"

/**
 * Estado de la pantalla de búsqueda lossless: consulta, modo (canciones/álbumes), resultados,
 * álbum abierto con sus pistas, búsquedas recientes y calidad elegida.
 */
internal class LosslessController(
    private val client: LosslessClient,
    private val downloads: DownloadManager,
    private val prefs: DezzyPreferences,
    private val scope: CoroutineScope,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _mode = MutableStateFlow(LosslessMode.TRACKS)
    val mode: StateFlow<LosslessMode> = _mode.asStateFlow()

    private val _results = MutableStateFlow<List<LosslessTrack>>(emptyList())
    val results: StateFlow<List<LosslessTrack>> = _results.asStateFlow()

    private val _albums = MutableStateFlow<List<LosslessAlbum>>(emptyList())
    val albums: StateFlow<List<LosslessAlbum>> = _albums.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Última consulta ya resuelta (para distinguir «cargando» de «sin resultados»). */
    private val _searchedQuery = MutableStateFlow("")
    val searchedQuery: StateFlow<String> = _searchedQuery.asStateFlow()

    private val _expandedAlbumId = MutableStateFlow<String?>(null)
    val expandedAlbumId: StateFlow<String?> = _expandedAlbumId.asStateFlow()

    private val _albumTracks = MutableStateFlow<Map<String, List<LosslessTrack>>>(emptyMap())
    val albumTracks: StateFlow<Map<String, List<LosslessTrack>>> = _albumTracks.asStateFlow()

    private val _loadingAlbumId = MutableStateFlow<String?>(null)
    val loadingAlbumId: StateFlow<String?> = _loadingAlbumId.asStateFlow()

    val recents: StateFlow<List<String>> = prefs.value(LosslessRecentSearchesKey, "")
        .map { raw -> raw.lines().map { it.trim() }.filter { it.isNotEmpty() } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quality: StateFlow<LosslessQuality> = prefs.value(LosslessDefaultQualityKey, LosslessQuality.FLAC.wire)
        .map(::losslessQualityFromWire)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LosslessQuality.FLAC)

    /** Descargas lossless por id (`dzr:<id>`). */
    val downloadItems: StateFlow<Map<String, DownloadItem>> = downloads.state
        .map { state -> state.items.filter { it.lossless != null }.associateBy { it.id } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var searchJob: Job? = null
    private var ticket = 0

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onModeChange(value: LosslessMode) {
        if (_mode.value == value) return
        _mode.value = value
        if (_query.value.trim().length >= 2) search()
    }

    fun search() {
        val q = _query.value.trim()
        searchJob?.cancel()
        val mine = ++ticket
        if (q.length < 2) {
            _results.value = emptyList()
            _albums.value = emptyList()
            _error.value = null
            _searchedQuery.value = ""
            _isSearching.value = false
            return
        }
        val tracksMode = _mode.value == LosslessMode.TRACKS
        searchJob = scope.launch {
            _isSearching.value = true
            _error.value = null
            try {
                if (tracksMode) _results.value = client.searchTracks(q) else _albums.value = client.searchAlbums(q)
                _searchedQuery.value = q
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (mine == ticket) {
                    _error.value = e.message ?: "No se pudo buscar"
                    if (tracksMode) _results.value = emptyList() else _albums.value = emptyList()
                    _searchedQuery.value = q
                }
            } finally {
                if (mine == ticket) _isSearching.value = false
            }
        }
    }

    fun toggleAlbum(album: LosslessAlbum) {
        if (_expandedAlbumId.value == album.id) {
            _expandedAlbumId.value = null
            return
        }
        _expandedAlbumId.value = album.id
        if (_albumTracks.value[album.id].isNullOrEmpty()) {
            scope.launch {
                _loadingAlbumId.value = album.id
                try {
                    val tracks = client.albumTracks(album.id)
                    _albumTracks.value = _albumTracks.value + (album.id to tracks)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    _albumTracks.value = _albumTracks.value + (album.id to emptyList())
                } finally {
                    if (_loadingAlbumId.value == album.id) _loadingAlbumId.value = null
                }
            }
        }
    }

    fun addRecent(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        scope.launch {
            val current = readRecents()
            val next = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MaxRecentSearches)
            if (next != current) prefs.set(LosslessRecentSearchesKey, next.joinToString("\n"))
        }
    }

    fun clearRecents() {
        scope.launch { prefs.set(LosslessRecentSearchesKey, "") }
    }

    private suspend fun readRecents(): List<String> =
        prefs.value(LosslessRecentSearchesKey, "").first().lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun setQuality(q: LosslessQuality) {
        scope.launch { prefs.set(LosslessDefaultQualityKey, q.wire) }
    }

    fun download(track: LosslessTrack, q: LosslessQuality) {
        downloads.enqueueLossless(track.toTrack(), track.ref(q))
    }

    fun downloadAll(tracks: List<LosslessTrack>, q: LosslessQuality) {
        tracks.forEach { download(it, q) }
    }

    fun cancel(track: LosslessTrack) = downloads.remove(track.downloadId())

    fun retry(track: LosslessTrack) = downloads.retry(track.downloadId())
}
