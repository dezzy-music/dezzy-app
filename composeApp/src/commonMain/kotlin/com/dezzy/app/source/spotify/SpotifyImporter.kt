/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

sealed interface SpotifyImportState {
    data object Idle : SpotifyImportState
    data object LoadingPlaylists : SpotifyImportState
    data class Choosing(
        val playlists: List<SpotifyPlaylistSummary>,
        val alreadyImported: Set<String>,
    ) : SpotifyImportState
    /** [playlistIndex] cuenta desde 1. */
    data class Importing(
        val playlistName: String,
        val done: Int,
        val total: Int,
        val playlistIndex: Int,
        val playlistCount: Int,
    ) : SpotifyImportState
    data class Done(val playlists: Int, val tracks: Int, val notFound: Int) : SpotifyImportState
    data class Failed(val message: String) : SpotifyImportState
}

/** Trae las playlists de la biblioteca y las pasa a listas locales emparejando cada canción. */
class SpotifyImporter(
    private val client: SpotifyClient,
    private val matcher: SpotifyMatcher,
    private val repo: MusicRepository,
    private val prefs: DezzyPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val state: StateFlow<SpotifyImportState> = _state.asStateFlow()

    private var job: Job? = null
    private var known: List<SpotifyPlaylistSummary> = emptyList()

    private fun busy(): Boolean = job?.isActive == true

    fun loadPlaylists() {
        if (busy()) return
        _state.value = SpotifyImportState.LoadingPlaylists
        job = scope.launch {
            try {
                val list = client.playlists()
                known = list
                val imported = repo.importedRemoteIds(SOURCE).toSet()
                _state.value = SpotifyImportState.Choosing(list, imported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SpotifyImportState.Failed(e.message ?: DEFAULT_ERROR)
            }
        }
    }

    fun import(ids: Set<String>) {
        if (busy() || ids.isEmpty()) return
        job = scope.launch {
            try {
                val result = run(ids, silent = false)
                _state.value = SpotifyImportState.Done(result.playlists, result.tracks, result.notFound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SpotifyImportState.Failed(e.message ?: DEFAULT_ERROR)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SpotifyImportState.Idle
    }

    fun reset() {
        if (busy()) return
        _state.value = SpotifyImportState.Idle
    }

    /** Al arrancar: si hay sesión, la sincronización automática está activa y han pasado ≥24 h. */
    fun startAutoSync() {
        if (busy()) return
        job = scope.launch {
            try {
                if (!client.hasSession()) return@launch
                if (!prefs.spotifyAutoSync.first()) return@launch
                if (nowMs() - prefs.spotifyLastSync.first() < AUTO_SYNC_INTERVAL_MS) return@launch
                val ids = repo.importedRemoteIds(SOURCE).toSet()
                if (ids.isEmpty()) return@launch
                run(ids, silent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // En silencio: se reintentará en el próximo arranque.
            }
        }
    }

    private class Totals(val playlists: Int, val tracks: Int, val notFound: Int)

    /**
     * Importa [ids]. Con [silent] no toca el estado y, si una lista falla, sigue con la siguiente
     * (una descarga a medias nunca vacía una lista ya importada).
     */
    private suspend fun run(ids: Set<String>, silent: Boolean): Totals {
        val likedAsPlaylist = prefs.spotifyLikedAsPlaylist.first()
        var summaries = known.filter { it.id in ids }
        if (summaries.size < ids.size) {
            known = client.playlists()
            summaries = known.filter { it.id in ids }
        }
        // Las que ya no existen en la biblioteca se ignoran.
        var writtenLists = 0
        var matchedTotal = 0
        var notFoundTotal = 0

        summaries.forEachIndexed { index, summary ->
            val asFavorites = summary.isLiked && !likedAsPlaylist
            val title = if (summary.isLiked && likedAsPlaylist) LIKED_TITLE else summary.name
            if (!silent) {
                _state.value = SpotifyImportState.Importing(title, 0, 0, index + 1, summaries.size)
            }
            val tracks = try {
                client.tracks(summary.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silent) return@forEachIndexed else throw e
            }
            if (!silent) {
                _state.value = SpotifyImportState.Importing(title, 0, tracks.size, index + 1, summaries.size)
            }

            val found = ArrayList<Track>()
            var missing = 0
            tracks.forEachIndexed { n, spotifyTrack ->
                val match = matcher.match(spotifyTrack)
                if (match != null) found += match else missing++
                if (!silent) {
                    _state.value = SpotifyImportState.Importing(title, n + 1, tracks.size, index + 1, summaries.size)
                }
                if (matcher.lastCallSearched) delay(SEARCH_PAUSE_MS) // sin prisa, para no saturar
            }
            matchedTotal += found.size
            notFoundTotal += missing

            // Si no se emparejó nada no se escribe: así un fallo de red no vacía la lista.
            if (found.isNotEmpty()) {
                if (asFavorites) {
                    found.forEach { repo.setFavorite(it, true) }
                } else {
                    repo.replaceImportedPlaylist(SOURCE, summary.id, title, summary.imageUrl, found.distinctBy { it.videoId })
                }
                writtenLists++
            }
        }
        prefs.setSpotifyLastSync(nowMs())
        return Totals(writtenLists, matchedTotal, notFoundTotal)
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val SOURCE = "spotify"
        const val LIKED_TITLE = "Me gusta de Spotify"
        const val DEFAULT_ERROR = "No se pudo leer tu biblioteca"
        const val SEARCH_PAUSE_MS = 150L
        const val AUTO_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
