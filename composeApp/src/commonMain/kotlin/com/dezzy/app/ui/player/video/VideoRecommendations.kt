/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val MUSIC_VIDEO_TYPE_OMV = "MUSIC_VIDEO_TYPE_OMV"
private const val MUSIC_VIDEO_TYPE_UGC = "MUSIC_VIDEO_TYPE_UGC"

/** Filtros de los videos recomendados (chips de "Solo videos"). */
enum class VideoRecoFilter(val label: String) {
    FOR_SONG("Para esta canción"),
    OFFICIAL("Oficiales"),
    ARTIST("Del artista"),
}

/** Video de verdad (oficial o subido), no un Art Track de audio con portada fija. */
internal fun Track.isVideo(): Boolean = musicVideoType.let { it == MUSIC_VIDEO_TYPE_OMV || it == MUSIC_VIDEO_TYPE_UGC }

internal fun Track.isOfficialVideo(): Boolean = musicVideoType == MUSIC_VIDEO_TYPE_OMV

/** Miniatura 16:9 del video (hqdefault trae bandas negras que el recorte 16:9 quita). */
internal fun videoThumb(id: String): String = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

/**
 * Carga los videos recomendados para la canción actual y los mantiene en estado de Compose.
 * Lo comparten el carrusel del modo video y la pantalla "Solo videos", así que cambiar de
 * una a otra no vuelve a pedir nada a la red. Guarda una caché corta por (video, filtro).
 */
@Stable
class VideoRecommendations(
    private val scope: CoroutineScope,
    private val client: YouTubeMusicClient,
) {
    val items = mutableStateListOf<Track>()
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    private var key: String? = null
    private var filter: VideoRecoFilter = VideoRecoFilter.FOR_SONG
    private var currentId: String? = null
    private var continuation: String? = null
    private var job: Job? = null

    private class Snapshot(
        val items: List<Track>,
        val continuation: String?,
    )

    // Caché LRU de 8 entradas (LinkedHashMap con orden de acceso no existe en common).
    private val cache = LinkedHashMap<String, Snapshot>()

    private fun cacheGet(k: String): Snapshot? {
        val snap = cache.remove(k) ?: return null
        cache[k] = snap
        return snap
    }

    private fun cachePut(k: String, snap: Snapshot) {
        cache.remove(k)
        cache[k] = snap
        while (cache.size > 8) {
            val eldest = cache.keys.firstOrNull() ?: break
            cache.remove(eldest)
        }
    }

    fun load(videoId: String, title: String, artist: String, filter: VideoRecoFilter) {
        val newKey = "$videoId|${filter.name}"
        if (newKey == key && (items.isNotEmpty() || loading)) return
        saveSnapshot()
        job?.cancel()
        key = newKey
        this.filter = filter
        currentId = videoId
        failed = false
        loadingMore = false

        cacheGet(newKey)?.let { snap ->
            items.clear()
            items.addAll(snap.items)
            continuation = snap.continuation
            loading = false
            return
        }

        items.clear()
        continuation = null
        loading = true
        job = scope.launch {
            val result = runCatching { fetchFirstPage(videoId, title, artist, filter) }.getOrNull()
            if (key != newKey) return@launch
            loading = false
            if (result == null) {
                failed = true
                return@launch
            }
            items.addAll(result.items)
            continuation = result.continuation
            saveSnapshot()
        }
    }

    /** Siguiente página (radio o búsqueda). Prueba hasta 3 páginas para traer algo nuevo. */
    fun loadMore() {
        if (loading || loadingMore || continuation == null) return
        val requestKey = key ?: return
        loadingMore = true
        job = scope.launch {
            var added = 0
            var attempts = 0
            while (added == 0 && continuation != null && attempts < 3 && key == requestKey) {
                attempts++
                val token = continuation ?: break
                val page = runCatching { fetchNextPage(token) }.getOrNull()
                if (key != requestKey) return@launch
                if (page == null) {
                    continuation = null
                    break
                }
                val known = items.mapTo(HashSet()) { it.videoId }
                val fresh = page.items.filter { it.videoId != currentId && known.add(it.videoId) }
                items.addAll(fresh)
                added += fresh.size
                continuation = page.continuation.takeIf { it != token }
            }
            if (key == requestKey) {
                loadingMore = false
                saveSnapshot()
            }
        }
    }

    fun retry(videoId: String, title: String, artist: String, filter: VideoRecoFilter) {
        key?.let { cache.remove(it) }
        key = null
        load(videoId, title, artist, filter)
    }

    private fun saveSnapshot() {
        val k = key ?: return
        if (loading || items.isEmpty()) return
        cachePut(k, Snapshot(items.toList(), continuation))
    }

    private class Page(
        val items: List<Track>,
        val continuation: String?,
    )

    private suspend fun fetchFirstPage(
        videoId: String,
        title: String,
        artist: String,
        filter: VideoRecoFilter,
    ): Page = coroutineScope {
        when (filter) {
            VideoRecoFilter.ARTIST -> {
                val result = client.searchVideos(artist.ifBlank { title }).getOrThrow()
                Page(
                    items = result.items.filter { it.videoId != null && it.videoId != videoId }.distinctBy { it.videoId },
                    continuation = result.continuation,
                )
            }

            VideoRecoFilter.FOR_SONG, VideoRecoFilter.OFFICIAL -> {
                val radioDeferred = async { runCatching { client.getWatchNext(videoId).getOrThrow() }.getOrNull() }
                val searchDeferred = async {
                    runCatching {
                        client.searchVideos("$title $artist".trim()).getOrThrow()
                    }.getOrNull()
                }
                val radio = radioDeferred.await()
                val search = searchDeferred.await()
                if (radio == null && search == null) error("Sin recomendaciones")

                val keep = videoFilter(filter)
                val currentTitle = normalizeTitle(title)
                val radioVideos = radio?.items.orEmpty().filter { it.videoId != null && it.videoId != videoId && keep(it) }
                val searchVideos = search?.items.orEmpty()
                    .filter { it.videoId != null && it.videoId != videoId && (filter != VideoRecoFilter.OFFICIAL || it.isOfficialVideo()) }
                    // El videoclip oficial de la propia canción ya es lo que se está viendo.
                    .filterNot { currentTitle.isNotEmpty() && normalizeTitle(it.title) == currentTitle && it.isOfficialVideo() }

                val merged = (radioVideos.take(2) + searchVideos.take(4) + radioVideos.drop(2))
                    .distinctBy { it.videoId }
                Page(
                    items = merged,
                    continuation = radio?.continuation,
                )
            }
        }
    }

    private suspend fun fetchNextPage(token: String): Page = when (filter) {
        VideoRecoFilter.ARTIST -> {
            val result = client.searchContinuation(token).getOrThrow()
            Page(result.items.filter { it.videoId != null }, result.continuation)
        }

        VideoRecoFilter.FOR_SONG, VideoRecoFilter.OFFICIAL -> {
            val id = currentId ?: error("Sin video actual")
            val result = client.getWatchNextContinuation(id, token).getOrThrow()
            val keep = videoFilter(filter)
            Page(result.items.filter { it.videoId != null && keep(it) }, result.continuation)
        }
    }

    private companion object {
        private val OfficialTag = Regex(
            """[(\[][^)\]]*(official|oficial|video|vídeo|audio|visualizer|\bmv\b|\bhd\b|4k)[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )

        fun videoFilter(filter: VideoRecoFilter): (Track) -> Boolean {
            val officialOnly = filter == VideoRecoFilter.OFFICIAL
            return { item -> if (officialOnly) item.isOfficialVideo() else item.isVideo() }
        }

        fun normalizeTitle(title: String): String =
            title.replace(OfficialTag, "")
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
    }
}
