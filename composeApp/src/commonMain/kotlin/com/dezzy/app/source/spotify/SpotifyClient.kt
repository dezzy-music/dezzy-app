/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.accounts.StoredAccount
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val imageUrl: String?,
    val durationMs: Long?,
)

data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int?,
    val isLiked: Boolean = false,
)

/** Error de la biblioteca con un mensaje que se puede enseñar tal cual (no nombra el servicio). */
class SpotifyException(message: String) : Exception(message)

/**
 * Biblioteca de Spotify con la cookie `sp_dc`. El token de acceso (~1 h) lo consigue [tokens]; las
 * lecturas van por la API GraphQL del reproductor web («pathfinder»). Los fallos pasajeros nunca
 * cuentan como rechazo de la sesión: sólo un 401/403 con token recién renovado.
 */
class SpotifyClient(
    private val http: HttpClient,
    private val accounts: AccountStore,
    private val tokens: SpotifyTokenSource,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tokenMutex = Mutex()
    private var cached: CachedToken? = null

    private class CachedToken(val value: String, val spDc: String, val expiresAt: Long)

    private enum class Operation(val hashes: List<String>) {
        profileAttributes(
            listOf(
                "b197b5adb4b761690f76ad9d9fb278c14c14e7331f357c04a56e7001af7106e0",
                "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced",
            ),
        ),
        libraryV3(
            listOf(
                "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3",
                "2de10199b2441d6e4ae875f27d2db361020c399fb10b03951120223fbed10b08",
            ),
        ),
        fetchPlaylist(
            listOf(
                "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4",
                "32b05e92e438438408674f95d0fdad8082865dc32acd55bd97f5113b8579092b",
            ),
        ),
        fetchLibraryTracks(listOf("087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240")),
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** true si hay una sesión de Spotify guardada (y legible ahora). */
    suspend fun hasSession(): Boolean = accounts.secret(SessionProvider.SPOTIFY) != null

    /** Guarda la sesión, intenta leer el perfil y devuelve la sesión (aunque el perfil falle). */
    suspend fun completeLogin(spDc: String): Result<UserSession> {
        return try {
            val connectedAt = nowMs()
            accounts.save(StoredAccount(SessionProvider.SPOTIFY, spDc, connectedAtEpochMs = connectedAt))
            val profile = profile()
            if (profile != null) {
                accounts.updateProfile(SessionProvider.SPOTIFY, profile.first, null, profile.second)
            }
            Result.success(
                UserSession(
                    provider = SessionProvider.SPOTIFY,
                    name = profile?.first,
                    avatarUrl = profile?.second,
                    connectedAtEpochMs = connectedAt,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Nombre y foto de la cuenta; null si no responde. */
    suspend fun profile(): Pair<String?, String?>? {
        return try {
            val data = gql(Operation.profileAttributes, buildJsonObject { })
            val profile = data.o("me").o("profile") ?: return null
            Pair(profile.s("name"), pickImage(profile.o("avatar").a("sources")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Las playlists de la cuenta; «Canciones que te gustan» va la primera. */
    suspend fun playlists(): List<SpotifyPlaylistSummary> {
        val out = ArrayList<SpotifyPlaylistSummary>()
        out += SpotifyPlaylistSummary(LIKED_ID, LIKED_NAME, imageUrl = null, trackCount = null, isLiked = true)
        val limit = 50
        var offset = 0
        while (offset < LIBRARY_CAP) {
            val page = gql(
                Operation.libraryV3,
                buildJsonObject {
                    putJsonArray("filters") { add("Playlists") }
                    put("order", JsonNull)
                    put("textFilter", "")
                    putJsonArray("features") {
                        add("LIKED_SONGS"); add("YOUR_EPISODES_V2"); add("PRERELEASES"); add("EVENTS")
                    }
                    put("limit", limit)
                    put("offset", offset)
                    put("flatten", true)
                    putJsonArray("expandedFolders") { }
                    put("folderUri", JsonNull)
                    put("includeFoldersWhenFlattening", false)
                },
            )
            val lib = page.o("me").o("libraryV3")
            val rows = lib.a("items") ?: JsonArray(emptyList())
            for (row in rows) {
                val item = row.o("item")
                val uri = item.s("_uri") ?: continue
                val data = item.o("data")
                val name = data.s("name")
                if (!uri.startsWith(PLAYLIST_URI) || data.s("__typename") != "Playlist" || name.isNullOrBlank()) continue
                out += SpotifyPlaylistSummary(
                    id = uri.removePrefix(PLAYLIST_URI),
                    name = name,
                    imageUrl = pickImage(data.o("images").a("items")?.firstOrNull().a("sources")),
                    trackCount = data.o("content").n("totalCount")?.toInt(),
                )
            }
            val total = lib.n("totalCount") ?: 0L
            if (rows.size < limit || offset + limit >= total) break
            offset += limit
        }
        return out
    }

    /** Todas las canciones de una playlist o, con [LIKED_ID], de «Canciones que te gustan». */
    suspend fun tracks(collectionId: String): List<SpotifyTrack> {
        val out = ArrayList<SpotifyTrack>()
        if (collectionId == LIKED_ID) {
            val limit = 50
            var offset = 0
            while (offset < TRACKS_CAP) {
                val page = gql(Operation.fetchLibraryTracks, buildJsonObject { put("offset", offset); put("limit", limit) })
                val list = page.o("me").o("library").o("tracks")
                val rows = list.a("items") ?: JsonArray(emptyList())
                for (row in rows) toTrack(row.o("track").s("_uri"), row.o("track").o("data"))?.let { out += it }
                if (rows.size < limit || offset + limit >= (list.n("totalCount") ?: 0L)) break
                offset += limit
            }
            return out
        }
        if (!collectionId.all { it.isLetterOrDigit() } || collectionId.isEmpty()) {
            throw SpotifyException("Playlist no válida")
        }
        val limit = 100
        var offset = 0
        while (offset < TRACKS_CAP) {
            val page = gql(
                Operation.fetchPlaylist,
                buildJsonObject {
                    put("uri", "$PLAYLIST_URI$collectionId")
                    put("offset", offset)
                    put("limit", limit)
                    put("enableWatchFeedEntrypoint", false)
                },
            )
            val playlist = page.o("playlistV2") ?: throw SpotifyException("No he encontrado esta playlist")
            val rows = playlist.o("content").a("items") ?: JsonArray(emptyList())
            for (row in rows) toTrack(row.o("itemV2").s("_uri"), row.o("itemV2").o("data"))?.let { out += it }
            if (rows.size < limit || offset + limit >= (playlist.o("content").n("totalCount") ?: 0L)) break
            offset += limit
        }
        return out
    }

    // --- token ---

    private suspend fun accessToken(spDc: String, force: Boolean): String = tokenMutex.withLock {
        val c = cached
        if (!force && c != null && c.spDc == spDc && nowMs() < c.expiresAt) return@withLock c.value
        // Una sola captura a la vez: el resto espera aquí y recibe el token ya guardado.
        val value = tokens.capture(spDc)
        cached = CachedToken(value, spDc, nowMs() + TOKEN_TTL_MS)
        value
    }

    // --- consultas ---

    /**
     * Lanza una consulta probando cada hash; reintenta un 401 con token nuevo y los 429 tras esperar.
     * Devuelve el objeto `data` de la respuesta.
     */
    private suspend fun gql(op: Operation, variables: JsonObject): JsonObject {
        val health = accounts.health
        val spDc = accounts.secret(SessionProvider.SPOTIFY)
            ?: throw SpotifyException("No hay ninguna sesión vinculada")
        var lastError = "Tu biblioteca no responde"
        for (hash in op.hashes) {
            var renewed = false
            var attempt = 0
            while (attempt <= MAX_RETRIES) {
                val bearer = try {
                    accessToken(spDc, force = renewed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No poder capturar el token es un fallo pasajero, nunca un rechazo.
                    health.onTransientFailure(SessionProvider.SPOTIFY)
                    throw SpotifyException(e.message ?: lastError)
                }
                val response = try {
                    http.post(PATHFINDER) {
                        header(HttpHeaders.Authorization, "Bearer $bearer")
                        header(HttpHeaders.Accept, "application/json")
                        header("app-platform", "WebPlayer")
                        header(HttpHeaders.Origin, WEB_URL)
                        header(HttpHeaders.Referrer, "$WEB_URL/")
                        header(HttpHeaders.UserAgent, SPOTIFY_USER_AGENT)
                        setBody(TextContent(queryBody(op, hash, variables), ContentType.Application.Json))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    health.onTransientFailure(SessionProvider.SPOTIFY)
                    throw SpotifyException("Tu biblioteca no responde; revisa tu conexión")
                }
                val status = response.status.value
                if (status == 401 && !renewed) {
                    renewed = true
                    attempt++
                    continue
                }
                if (status == 401 || status == 403) {
                    health.onRejected(SessionProvider.SPOTIFY)
                    throw SpotifyException("Tu biblioteca no acepta la sesión")
                }
                if (status == 429) {
                    health.onTransientFailure(SessionProvider.SPOTIFY)
                    lastError = "Tu biblioteca pide esperar un momento; vuelve a intentarlo enseguida"
                    if (attempt < MAX_RETRIES) {
                        val retryAfter = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
                        val waitSeconds = (retryAfter ?: (2L * (attempt + 1))).coerceIn(1L, 30L)
                        delay(waitSeconds * 1000)
                        attempt++
                        continue
                    }
                    throw SpotifyException(lastError)
                }
                if (status >= 500) {
                    health.onTransientFailure(SessionProvider.SPOTIFY)
                    throw SpotifyException("Tu biblioteca respondió $status")
                }
                if (status !in 200..299) {
                    // 400 y 412 suelen ser un hash retirado: se prueba el siguiente.
                    lastError = "Tu biblioteca respondió $status"
                    break
                }
                health.onSuccess(SessionProvider.SPOTIFY)
                val body = try {
                    json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (body == null) {
                    lastError = "Tu biblioteca devolvió algo ilegible"
                    break
                }
                val errors = body.a("errors")
                if (errors?.any { it.s("message")?.contains("PersistedQueryNotFound", ignoreCase = true) == true } == true) break
                val data = body.o("data")
                if (data != null) return data
                lastError = errors?.firstOrNull().s("message") ?: lastError
                break
            }
        }
        throw SpotifyException(lastError)
    }

    private fun queryBody(op: Operation, hash: String, variables: JsonObject): String =
        buildJsonObject {
            put("variables", variables)
            put("operationName", op.name)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", hash)
                }
            }
        }.toString()

    // --- parseo ---

    private fun toTrack(uri: String?, data: JsonObject?): SpotifyTrack? {
        if (data == null) return null
        val name = data.s("name")
        if (name.isNullOrBlank()) return null
        val type = data.s("__typename")
        if (type != null && type != "Track") return null
        // Las pistas locales no tienen equivalente que se pueda buscar.
        if (uri == null || !uri.startsWith(TRACK_URI)) return null
        val id = uri.removePrefix(TRACK_URI)
        if (id.isEmpty()) return null
        val artists = (data.o("artists").a("items") ?: JsonArray(emptyList()))
            .mapNotNull { it.o("profile").s("name") }
            .filter { it.isNotBlank() }
        val album = data.o("albumOfTrack")
        return SpotifyTrack(
            id = id,
            title = name,
            artists = artists,
            album = album.s("name"),
            imageUrl = pickImage(album.o("coverArt").a("sources")),
            durationMs = data.o("duration").n("totalMilliseconds"),
        )
    }

    /** La imagen más pequeña que llegue a 240 px; si no hay, la más grande. */
    private fun pickImage(sources: JsonArray?): String? {
        val usable = (sources ?: return null).filter { it.s("url") != null }
        if (usable.isEmpty()) return null
        val sorted = usable.sortedBy { it.n("width") ?: 0L }
        return (sorted.firstOrNull { (it.n("width") ?: 640L) >= 240 } ?: sorted.last()).s("url")
    }

    private fun JsonElement?.o(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
    private fun JsonElement?.a(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
    private fun JsonElement?.s(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.n(key: String): Long? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull

    companion object {
        const val LIKED_ID = "liked"
        const val LIKED_NAME = "Canciones que te gustan"

        private const val WEB_URL = "https://open.spotify.com"
        private const val PATHFINDER = "https://api-partner.spotify.com/pathfinder/v2/query"
        private const val PLAYLIST_URI = "spotify:playlist:"
        private const val TRACK_URI = "spotify:track:"
        private const val TOKEN_TTL_MS = 45 * 60 * 1000L
        private const val MAX_RETRIES = 2
        private const val LIBRARY_CAP = 200   // igual que el escritorio
        private const val TRACKS_CAP = 20_000 // sólo un freno de seguridad
    }
}
