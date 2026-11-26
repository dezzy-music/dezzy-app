/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.feature.downloads.LosslessDownload
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Búsqueda en la API pública de Deezer (sin clave) y contacto con el resolver del usuario.
 * La URL del resolver la pone el usuario en Ajustes; el ARL vive sólo en su servidor.
 */
class LosslessClient(
    private val http: HttpClient,
    private val prefs: DezzyPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchTracks(query: String): List<LosslessTrack> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val root = getJson("$API/search/track") { parameter("q", q); parameter("limit", 50) } ?: return emptyList()
        return root.array("data").mapNotNull { it.asTrack(null) }
    }

    suspend fun searchAlbums(query: String): List<LosslessAlbum> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val root = getJson("$API/search/album") { parameter("q", q); parameter("limit", 40) } ?: return emptyList()
        return root.array("data").mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            LosslessAlbum(
                id = id,
                title = o.str("title") ?: return@mapNotNull null,
                artist = o.obj("artist")?.str("name") ?: "",
                cover = o.cover(),
                trackCount = o.int("nb_tracks"),
                year = o.str("release_date")?.take(4)?.takeIf { it.length == 4 },
            )
        }
    }

    suspend fun albumTracks(albumId: String): List<LosslessTrack> {
        val id = albumId.trim()
        if (id.isEmpty()) return emptyList()
        // Las pistas del listado no traen álbum ni portada: se piden aparte (un fallo no tumba la lista).
        val album = getJson("$API/album/$id")
        val albumTitle = album?.str("title")
        val albumCover = album?.cover()
        val root = getJson("$API/album/$id/tracks") { parameter("limit", 500) } ?: return emptyList()
        return root.array("data").mapNotNull { el ->
            el.asTrack(Triple(id, albumTitle, albumCover))
        }
    }

    /** ISRC de una pista de Deezer (`/track/{id}`), o null. */
    suspend fun isrc(trackId: String): String? =
        getJson("$API/track/${trackId.trim()}")?.str("isrc")?.takeIf { it.isNotBlank() }

    suspend fun health(): Result<String> {
        val base = resolverUrl() ?: return Result.failure(IllegalStateException(LOSSLESS_NO_RESOLVER_MESSAGE))
        return try {
            val text = withTimeout(20_000) { http.get("$base/health").bodyAsText() }
            val obj = json.parseToJsonElement(text).jsonObject
            val ok = (obj["ok"] as? JsonPrimitive)?.booleanOrNull == true
            val deezer = obj.obj("sources")?.obj("deezer")
            val lossless = (deezer?.get("lossless") as? JsonPrimitive)?.booleanOrNull
            if (ok) {
                Result.success(
                    when (lossless) {
                        true -> "Resolver listo (Deezer HiFi: FLAC)"
                        false -> "Resolver listo (cuenta gratuita: sólo MP3)"
                        null -> "Resolver listo"
                    },
                )
            } else {
                Result.failure(IllegalStateException(deezer?.str("message") ?: "El resolver no tiene ninguna fuente válida"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("No se pudo contactar con el resolver"))
        }
    }

    /** URL para reproducir un `dzr:` no descargado. Null si no hay resolver o falla. */
    suspend fun streamUrl(track: Track): String? {
        val id = track.videoId?.takeIf { it.startsWith(LOSSLESS_ID_PREFIX) }?.removePrefix(LOSSLESS_ID_PREFIX) ?: return null
        val quality = prefs.value(LosslessDefaultQualityKey, LOSSLESS_QUALITY_DEFAULT).first()
        return try {
            requestDownload(id, null, quality, track.title, track.artists).url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** URL base del resolver sin barra final, o null si el usuario no la ha puesto. */
    suspend fun resolverUrl(): String? {
        val raw = prefs.value(LosslessResolverUrlKey, "").first().trim().trimEnd('/')
        if (raw.isEmpty()) return null
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    /**
     * `POST <resolver>/dl/dzr` con `{ id, isrc, quality, title, artist }`.
     * Respuesta: `{ success, download_url, format, size, message }` (siempre HTTP 200 salvo caídas).
     */
    internal suspend fun requestDownload(
        id: String,
        isrc: String?,
        quality: String,
        title: String,
        artist: String,
    ): LosslessDownload {
        val base = resolverUrl() ?: throw IllegalStateException(LOSSLESS_NO_RESOLVER_MESSAGE)
        val body = buildJsonObject {
            put("id", id)
            if (!isrc.isNullOrBlank()) put("isrc", isrc)
            put("quality", quality)
            put("title", title)
            put("artist", artist)
        }
        val text = withTimeout(60_000) {
            val response = http.post("$base/dl/dzr") { setBody(TextContent(body.toString(), ContentType.Application.Json)) }
            val code = response.status.value
            if (code >= 500) throw IllegalStateException("El resolver respondió $code")
            response.bodyAsText()
        }
        val obj = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IllegalStateException("Respuesta no válida del resolver")
        }
        val success = (obj["success"] as? JsonPrimitive)?.booleanOrNull == true
        val url = obj.str("download_url")
        if (!success || url.isNullOrBlank()) {
            throw IllegalStateException(obj.str("message") ?: "El resolver no pudo resolver la canción")
        }
        val container = obj.str("format")?.lowercase().orEmpty()
        val isFlac = container.contains("flac")
        return LosslessDownload(
            url = url,
            format = if (isFlac) "FLAC" else "MP3 320",
            ext = if (isFlac) "flac" else "mp3",
            sizeBytes = (obj["size"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
        )
    }

    private suspend fun getJson(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): JsonObject? =
        try {
            val text = withTimeout(20_000) { http.get(url, block).bodyAsText() }
            val obj = json.parseToJsonElement(text).jsonObject
            if (obj.containsKey("error")) null else obj
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun JsonElement.asTrack(album: Triple<String, String?, String?>?): LosslessTrack? {
        val o = this as? JsonObject ?: return null
        val id = o.str("id") ?: return null
        val title = o.str("title") ?: return null
        val alb = o.obj("album")
        return LosslessTrack(
            id = id,
            title = title,
            artist = o.obj("artist")?.str("name") ?: "",
            album = alb?.str("title") ?: album?.second,
            albumId = alb?.str("id") ?: album?.first,
            cover = alb?.cover() ?: album?.third,
            durationSec = o.int("duration"),
            isrc = o.str("isrc")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.cover(): String? =
        str("cover_xl") ?: str("cover_big") ?: str("cover_medium") ?: str("cover")

    private companion object {
        const val API = "https://api.deezer.com"
    }
}
