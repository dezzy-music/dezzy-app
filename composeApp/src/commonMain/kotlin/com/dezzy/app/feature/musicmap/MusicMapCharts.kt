/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Charts por país para el Mapa musical y la pantalla de éxitos.
 *
 * Sin servidor propio ni claves: cada dispositivo saca los charts de YouTube Music con el cliente
 * de la app (playlists oficiales de cada país).
 *  1. Caché local de 6 h (tabla `music_map_cache`).
 *  2. Descarga con concurrencia limitada (4). El id de la playlist de cada país lo guarda
 *     YouTubeMusicClient por llamada; aquí sólo se cachean las canciones.
 * Se guarda una foto del top como mucho una vez al día para las flechas de tendencia.
 */
class MusicMapCharts(
    private val store: MusicMapStore,
    private val client: YouTubeMusicClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotLock = Mutex()

    /**
     * Devuelve el top de cada país pedido. [onPartial] recibe resultados según van llegando
     * para que el mapa se pinte poco a poco. Los países sin datos no aparecen.
     */
    suspend fun load(
        countryCodes: List<String>,
        onPartial: suspend (Map<String, List<Track>>) -> Unit = {},
    ): Map<String, List<Track>> = withContext(Dispatchers.Default) {
        val result = LinkedHashMap<String, List<Track>>()
        val resultLock = Mutex()

        for (code in countryCodes) {
            readLocal(code)?.let { result[code] = it }
        }
        if (result.isNotEmpty()) onPartial(LinkedHashMap(result))

        val missing = countryCodes.filter { it !in result }
        if (missing.isNotEmpty()) {
            val gate = Semaphore(4)
            coroutineScope {
                missing.map { code ->
                    async {
                        val songs = gate.withPermit {
                            try {
                                fetchCountry(code)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                emptyList()
                            }
                        }
                        if (songs.isNotEmpty()) {
                            writeLocal(code, songs)
                            val snapshot = resultLock.withLock {
                                result[code] = songs
                                LinkedHashMap(result)
                            }
                            onPartial(snapshot)
                        }
                    }
                }.awaitAll()
            }
        }
        resultLock.withLock { LinkedHashMap(result) }
    }

    /** El top de un solo país (caché o red). Vacío si no hay. */
    suspend fun forCountry(code: String): List<Track> = load(listOf(code))[code].orEmpty()

    /** Borra la caché de tops para volver a pedirlos (pull to refresh). */
    suspend fun invalidate(code: String) {
        store.delete("chart_songs_$code")
    }

    /**
     * Posiciones del top anterior (de hace un día o más): id de canción → puesto (1 = primero).
     * Vacío si aún no hay foto anterior.
     */
    suspend fun previousRanks(code: String): Map<String, Int> {
        val raw = store.get("chart_prev_$code") ?: return emptyMap()
        return raw.split(',')
            .filter { it.isNotBlank() }
            .withIndex()
            .associate { (index, id) -> id to index + 1 }
    }

    private suspend fun fetchCountry(code: String): List<Track> =
        client.getChartTracks(code, SONGS_PER_COUNTRY).getOrThrow()
            .filter { !it.videoId.isNullOrBlank() }
            .distinctBy { it.videoId }
            .take(SONGS_PER_COUNTRY)

    // ---- Caché ----

    private suspend fun readLocal(code: String): List<Track>? {
        val raw = store.get("chart_songs_$code", LOCAL_TTL_MS) ?: return null
        return runCatching { parseSongs(json.parseToJsonElement(raw).jsonArray) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun writeLocal(code: String, songs: List<Track>) {
        store.put("chart_songs_$code", songsToJson(songs).toString())
        snapshotLock.withLock {
            val now = store.nowMs()
            val last = store.savedAt("chart_snap_$code") ?: 0L
            if (now - last >= SNAPSHOT_MIN_AGE_MS) {
                store.get("chart_snap_$code")?.let { store.put("chart_prev_$code", it) }
                store.put("chart_snap_$code", songs.mapNotNull { it.videoId }.joinToString(","))
            }
        }
    }

    private fun parseSongs(array: JsonArray): List<Track> = array.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = o["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Track(
            videoId = id,
            title = title,
            artists = o["artists"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            thumbnail = o["thumbnail"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationSeconds = o["duration"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.toLong(),
        )
    }

    private fun songsToJson(songs: List<Track>): JsonArray = buildJsonArray {
        for (song in songs) {
            val id = song.videoId ?: continue
            add(
                buildJsonObject {
                    put("id", id)
                    put("title", song.title)
                    put("artists", song.artists)
                    put("thumbnail", song.thumbnail.orEmpty())
                    put("duration", (song.durationSeconds ?: 0L).toInt())
                },
            )
        }
    }

    companion object {
        /** Código que YouTube Music usa para el chart mundial. */
        const val GLOBAL = CountryNames.GLOBAL
        private const val LOCAL_TTL_MS = 6L * 60 * 60 * 1000
        private const val SONGS_PER_COUNTRY = 50

        /** Una foto del top al día como mucho: sirve para las flechas de tendencia. */
        private const val SNAPSHOT_MIN_AGE_MS = 20L * 60 * 60 * 1000
    }
}
