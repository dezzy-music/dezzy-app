/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Género de cada artista para las secciones por género del Mapa musical.
 *
 * YouTube Music no da géneros en sus charts, así que se pregunta a la búsqueda pública de iTunes
 * (sin clave, una petición por artista) y se guarda para siempre en el dispositivo: el género de
 * un artista casi nunca cambia. Sólo se clasifican los artistas que el usuario abre.
 */
class MusicMapGenres(
    private val store: MusicMapStore,
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = HashMap<String, String>()
    private val memoryLock = Mutex()

    /** Género ya conocido (memoria o disco) o null si aún no se ha preguntado. */
    suspend fun cached(artist: String): String? {
        val k = key(artist)
        if (k.isEmpty()) return OTHER
        memoryLock.withLock { memory[k] }?.let { return it }
        store.get("genre_g_$k")?.let { remember(k, it); return it }
        if (store.get("genre_miss_$k", MISS_TTL_MS) != null) {
            remember(k, OTHER)
            return OTHER
        }
        return null
    }

    private suspend fun remember(k: String, genre: String) = memoryLock.withLock { memory[k] = genre }

    /**
     * Clasifica [artists] y devuelve clave de artista → grupo de género. [onPartial] recibe los
     * resultados según llegan. Si iTunes limita las peticiones, se para y lo que falte queda sin
     * clasificar hasta la próxima vez.
     */
    suspend fun classify(
        artists: List<String>,
        onPartial: suspend (Map<String, String>) -> Unit = {},
    ): Map<String, String> = withContext(Dispatchers.Default) {
        val result = HashMap<String, String>()
        val resultLock = Mutex()
        val pending = ArrayList<String>()
        for (artist in artists.distinctBy { key(it) }) {
            val k = key(artist)
            if (k.isEmpty()) continue
            val known = cached(artist)
            if (known != null) result[k] = known else pending += artist
        }
        if (result.isNotEmpty()) onPartial(HashMap(result))
        if (pending.isEmpty()) return@withContext HashMap(result)

        val gate = Semaphore(2)
        var throttled = false
        coroutineScope {
            pending.map { artist ->
                async {
                    gate.withPermit {
                        if (throttled) return@withPermit
                        val k = key(artist)
                        val genre = try {
                            lookup(artist)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ThrottledException) {
                            throttled = true
                            return@withPermit
                        } catch (_: Throwable) {
                            return@withPermit
                        }
                        val value = if (genre != null) {
                            store.put("genre_g_$k", genre)
                            genre
                        } else {
                            store.put("genre_miss_$k", "1")
                            OTHER
                        }
                        remember(k, value)
                        val snapshot = resultLock.withLock {
                            result[k] = value
                            HashMap(result)
                        }
                        onPartial(snapshot)
                    }
                }
            }.awaitAll()
        }
        resultLock.withLock { HashMap(result) }
    }

    private class ThrottledException : RuntimeException("iTunes limitó las peticiones")

    /** Género agrupado del artista o null si iTunes no lo conoce. */
    private suspend fun lookup(artist: String): String? {
        // La tienda de México da los nombres en español; si no lo encuentra, la de EE. UU.
        for (store in listOf("mx", "us")) {
            val response = http.get(SEARCH_URL) {
                parameter("entity", "musicArtist")
                parameter("limit", "3")
                parameter("country", store)
                parameter("term", artist)
            }
            val code = response.status.value
            if (code == 403 || code == 429) throw ThrottledException()
            if (code !in 200..299) return null
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val results = root["results"]?.jsonArray ?: continue
            val wanted = key(artist)
            for (el in results) {
                val r = el.jsonObject
                val name = key(r["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty())
                if (name.isEmpty() || !(name == wanted || name.contains(wanted) || wanted.contains(name))) continue
                val raw = r["primaryGenreName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                return group(raw)
            }
        }
        return null
    }

    companion object {
        private const val MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000
        private const val SEARCH_URL = "https://itunes.apple.com/search"

        /** Grupo para los artistas que no se pudieron clasificar. */
        const val OTHER = "Otros"

        /** Clave estable de un artista: minúsculas, sin acentos ni signos. */
        fun key(artist: String): String = CountryNames.fold(artist)

        /** Junta los nombres de iTunes (en español o inglés) en grupos fáciles de leer. */
        fun group(raw: String): String {
            val g = key(raw)
            fun has(vararg words: String) = words.any { g.contains(it) }
            return when {
                has("mexican", "regional", "banda", "norten", "grupera") -> "Regional mexicano"
                has("urbano", "urban", "reggaeton", "trap latino") -> "Urbano latino"
                has("pop latino", "latin pop") -> "Pop latino"
                has("k pop", "kpop", "korean") -> "K-pop"
                has("j pop", "jpop", "anime", "japanese") -> "J-pop"
                has("hip hop", "rap") -> "Hip-hop y rap"
                has("r b", "soul") -> "R&B y soul"
                has("electr", "dance", "house", "techno") -> "Electrónica"
                has("rock", "alternativ", "metal", "indie", "punk") -> "Rock y alternativa"
                has("afro", "african", "amapiano") -> "Afrobeats"
                has("bollywood", "indian", "india", "punjabi", "tamil") -> "Música india"
                has("sertanejo", "brasil", "brazil", "mpb", "pagode", "forro") -> "Música brasileña"
                has("cumbia") -> "Cumbia"
                has("salsa", "tropical", "bachata", "merengue") -> "Tropical"
                has("latin", "latina", "latino") -> "Latina"
                has("country") -> "Country"
                has("christian", "cristian", "gospel", "worship") -> "Cristiana"
                has("arab", "khaleeji") -> "Árabe"
                has("turk") -> "Pop turco"
                has("pop") -> "Pop"
                else -> raw.trim().replaceFirstChar { it.uppercase() }
            }
        }
    }
}
