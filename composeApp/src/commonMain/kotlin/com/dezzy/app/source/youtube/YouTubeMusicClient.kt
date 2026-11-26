/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.core.model.SearchResult
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.model.TrackPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class YouTubeMusicClient(
    private val httpClient: HttpClient,
    private val auth: YouTubeAuth? = null,
    private val locale: ContentLocale? = null,
) {
    /** hl: [hl] ?: ajustes ?: «es». gl: [gl] ?: ajustes ?: «US». Con sesión de marca añade `user.onBehalfOfUser`. */
    private suspend fun buildContext(gl: String? = null, hl: String? = null): JsonObject {
        val resolvedHl = hl ?: locale?.hl() ?: "es"
        val resolvedGl = gl ?: locale?.gl() ?: "US"
        val onBehalfOf = auth?.onBehalfOfUser()
        return buildJsonObject {
            putJsonObject("client") {
                put("clientName", YouTubeEndpoints.CLIENT_NAME)
                put("clientVersion", YouTubeEndpoints.CLIENT_VERSION)
                put("hl", resolvedHl)
                put("gl", resolvedGl)
            }
            if (onBehalfOf != null) {
                putJsonObject("user") { put("onBehalfOfUser", onBehalfOf) }
            }
        }
    }

    private suspend fun post(url: String, payload: JsonObject): JsonObject {
        val authHeaders = auth?.headers().orEmpty()
        val response = try {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Origin, YouTubeEndpoints.ORIGIN)
                header(HttpHeaders.Referrer, YouTubeEndpoints.REFERER)
                authHeaders.forEach { (name, value) -> header(name, value) }
                setBody(payload)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (authHeaders.isNotEmpty()) auth?.reportTransient()
            throw e
        }
        if (authHeaders.isNotEmpty()) {
            val code = response.status.value
            when {
                code == 401 || code == 403 -> auth?.reportRejected()
                code in 200..299 -> auth?.reportSuccess()
                else -> auth?.reportTransient() // 5xx, 429…: nunca cuenta como rechazo
            }
        }
        return response.body()
    }

    // Cuenta y biblioteca (requieren sesión) -----------------------------------

    /** Nombre, usuario, foto e id de sincronización de la cuenta conectada; null si no hay sesión o falla. */
    suspend fun getAccountInfo(): AccountInfo? {
        if (auth?.headers().isNullOrEmpty()) return null
        return try {
            val response = post(YouTubeEndpoints.ACCOUNT_MENU_URL, buildJsonObject {
                put("context", buildContext())
            })
            val header = response["actions"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("openPopupAction")?.jsonObject
                ?.get("popup")?.jsonObject
                ?.get("multiPageMenuRenderer")?.jsonObject
                ?.get("header")?.jsonObject
                ?.get("activeAccountHeaderRenderer")?.jsonObject
            val name = header?.get("accountName")?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
            val handle = header?.get("channelHandle")?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
            val avatar = header?.get("accountPhoto")?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.content
            val dataSyncId = response["responseContext"]?.jsonObject
                ?.get("mainAppWebResponseContext")?.jsonObject
                ?.get("datasyncId")?.jsonPrimitive?.content
            if (name == null && handle == null && avatar == null && dataSyncId == null) null
            else AccountInfo(name = name, handle = handle, avatarUrl = avatar, dataSyncId = dataSyncId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Listas de la biblioteca de la cuenta (sin «Nueva playlist» ni «Me gusta», que va aparte). */
    suspend fun getLibraryPlaylists(): List<Playlist> {
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext())
            put("browseId", YouTubeEndpoints.BROWSE_ID_LIBRARY_PLAYLISTS)
        })
        return YouTubeParsers.parseHomeSections(response)
            .flatMap { it.items }
            .filter { it.type == MusicItemType.PLAYLIST }
            .map { it.copy(id = it.id.removePrefix("VL")) }
            .filter { it.id.isNotBlank() && it.id != "LM" && it.id != "SE" }
            .map { Playlist(id = it.id, title = it.title, author = it.subtitle.ifBlank { null }, thumbnail = it.thumbnail) }
    }

    /** Canciones de «Me gusta» (la lista «LM»). */
    suspend fun getLikedSongs(): List<Track> = getPlaylist("LM").getOrThrow().tracks

    /** Da o quita «me gusta» a un vídeo. true si YouTube lo aceptó. */
    suspend fun setLiked(videoId: String, liked: Boolean): Boolean {
        if (auth?.headers().isNullOrEmpty()) return false
        return try {
            val response = post(if (liked) YouTubeEndpoints.LIKE_URL else YouTubeEndpoints.REMOVE_LIKE_URL, buildJsonObject {
                put("context", buildContext())
                putJsonObject("target") { put("videoId", videoId) }
            })
            !response.containsKey("error")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Radio automática a partir de una canción (lo que suena después), sin la propia canción. */
    suspend fun getUpNext(videoId: String, gl: String? = null): Result<List<Track>> = runCatching {
        val response = post(YouTubeEndpoints.NEXT_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
            put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        })
        YouTubeCatalogParsers.parseUpNext(response, videoId)
    }

    /** Búsqueda de vídeos (filtro «Vídeos»): primera página. */
    suspend fun searchVideos(query: String, gl: String? = null): Result<TrackPage> = runCatching {
        val response = post(YouTubeEndpoints.SEARCH_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("query", query)
            put("params", YouTubeEndpoints.FILTER_VIDEOS)
        })
        TrackPage(
            items = YouTubeParsers.parseSearchResults(response).songs,
            continuation = YouTubeCatalogParsers.parseSearchContinuationToken(response),
        )
    }

    /** Siguiente página de una búsqueda de vídeos. */
    suspend fun searchContinuation(continuation: String, gl: String? = null): Result<TrackPage> = runCatching {
        val c = continuation.encodeURLParameter()
        val response = post("${YouTubeEndpoints.SEARCH_URL}&continuation=$c&ctoken=$c&type=next", buildJsonObject {
            put("context", buildContext(gl = gl))
        })
        YouTubeCatalogParsers.parseSearchContinuation(response)
    }

    /** Vídeos relacionados con uno (incluye el propio vídeo si viene). */
    suspend fun getWatchNext(videoId: String, gl: String? = null): Result<TrackPage> = runCatching {
        val response = post(YouTubeEndpoints.NEXT_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", false)
            put("enablePersistentPlaylistPanel", true)
            put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        })
        TrackPage(
            items = YouTubeCatalogParsers.parseUpNext(response, videoId, dropSeed = false),
            continuation = YouTubeCatalogParsers.parseWatchNextContinuation(response),
        )
    }

    suspend fun getWatchNextContinuation(videoId: String, continuation: String, gl: String? = null): Result<TrackPage> = runCatching {
        val response = post(YouTubeEndpoints.NEXT_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("continuation", continuation)
            put("isAudioOnly", false)
            put("enablePersistentPlaylistPanel", true)
        })
        TrackPage(
            items = YouTubeCatalogParsers.parseUpNext(response, videoId, dropSeed = false),
            continuation = YouTubeCatalogParsers.parseWatchNextContinuation(response),
        )
    }

    suspend fun getArtist(id: String, gl: String? = null): Result<Artist> = runCatching {
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", id)
        })
        YouTubeCatalogParsers.parseArtist(response, id)
    }

    /** Id del artista que mejor encaja con un nombre (los enlaces sólo traen el nombre). */
    suspend fun findArtistId(name: String, gl: String? = null): Result<String?> = runCatching {
        val first = name.split(Regex(",| & | feat\\. ", RegexOption.IGNORE_CASE)).firstOrNull()?.trim()
        if (first.isNullOrEmpty()) return@runCatching null
        val artists = searchSingle(first.take(120), YouTubeEndpoints.FILTER_ARTISTS, gl).artists
        val wanted = YouTubeCatalogParsers.normalize(first)
        (artists.firstOrNull { YouTubeCatalogParsers.normalize(it.title) == wanted } ?: artists.firstOrNull())?.id
    }

    suspend fun getSearchSuggestions(input: String, gl: String? = null): Result<SearchSuggestions> = runCatching {
        if (input.isBlank()) return@runCatching SearchSuggestions()
        val response = post(YouTubeEndpoints.SUGGESTIONS_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("input", input)
        })
        YouTubeCatalogParsers.parseSuggestions(response)
    }

    /** «Estados de ánimo y géneros» de la pestaña Descubre. */
    suspend fun getMoodsAndGenres(gl: String? = null): Result<List<MoodSection>> = runCatching {
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", "FEmusic_moods_and_genres")
        })
        YouTubeCatalogParsers.parseMoodsAndGenres(response)
    }

    /** Cualquier página de `browse` hecha de carruseles (un estado de ánimo, un género…). */
    suspend fun getBrowseSections(browseId: String, params: String?, gl: String? = null): Result<List<MusicSection>> = runCatching {
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", browseId)
            if (params != null) put("params", params)
        })
        YouTubeParsers.parseHomeSections(response)
    }

    /** Videoclip de la canción: el vídeo que mejor encaje con título, artista y duración. */
    suspend fun findMusicVideo(track: Track, gl: String? = null): Result<String?> = runCatching {
        val query = "${track.title} ${track.artists}".take(200)
        val candidates = searchSingle(query, YouTubeEndpoints.FILTER_VIDEOS, gl).songs
        YouTubeCatalogParsers.pickMusicVideo(candidates, track.title, track.artists, track.durationSeconds)
    }

    suspend fun getHome(gl: String? = null): Result<List<MusicSection>> = runCatching {
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", YouTubeEndpoints.BROWSE_ID_HOME)
        })

        YouTubeParsers.parseHomeSections(response)
    }

    suspend fun search(query: String, filterParam: String? = null, gl: String? = null): Result<SearchResult> = runCatching {
        if (filterParam != null) {
            searchSingle(query, filterParam, gl)
        } else {
            // Disparar en paralelo búsquedas para cada categoría (igual que dezzy-desktop/src/main/music.ts)
            coroutineScope {
                val songsDef = async { runCatching { searchSingle(query, YouTubeEndpoints.FILTER_SONGS, gl).songs }.getOrDefault(emptyList()) }
                val albumsDef = async { runCatching { searchSingle(query, YouTubeEndpoints.FILTER_ALBUMS, gl).albums }.getOrDefault(emptyList()) }
                val artistsDef = async { runCatching { searchSingle(query, YouTubeEndpoints.FILTER_ARTISTS, gl).artists }.getOrDefault(emptyList()) }
                val playlistsDef = async { runCatching { searchSingle(query, YouTubeEndpoints.FILTER_PLAYLISTS, gl).playlists }.getOrDefault(emptyList()) }

                SearchResult(
                    songs = songsDef.await(),
                    albums = albumsDef.await(),
                    artists = artistsDef.await(),
                    playlists = playlistsDef.await()
                )
            }
        }
    }

    private suspend fun searchSingle(query: String, filterParam: String?, gl: String?): SearchResult {
        val response = post(YouTubeEndpoints.SEARCH_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("query", query)
            if (filterParam != null) {
                put("params", filterParam)
            }
        })

        return YouTubeParsers.parseSearchResults(response)
    }

    suspend fun getAlbum(browseId: String, gl: String? = null): Result<Album> = runCatching {
        val id = if (browseId.startsWith("MPREb_")) browseId else "MPREb_$browseId"
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", id)
        })

        YouTubeParsers.parseAlbum(response, id)
    }

    suspend fun getPlaylist(playlistId: String, gl: String? = null): Result<Playlist> = runCatching {
        val id = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", id)
        })

        YouTubeParsers.parsePlaylist(response, playlistId)
    }

    suspend fun getCountryCharts(countryCode: String): Result<List<Track>> = runCatching {
        val response = post(YouTubeEndpoints.BROWSE_URL, buildJsonObject {
            put("context", buildContext(hl = "en", gl = countryCode))
            put("browseId", YouTubeEndpoints.BROWSE_ID_CHARTS)
            put("params", YouTubeEndpoints.CHARTS_PARAMS)
            putJsonObject("formData") {
                putJsonArray("selectedValues") {
                    add(kotlinx.serialization.json.JsonPrimitive(countryCode))
                }
            }
        })

        // Buscar la playlist recomendada de chart para el país
        val sections = response["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray ?: emptyList()

        var chartPlaylistId: String? = null
        for (section in sections) {
            val shelf = (section as? JsonObject)?.get("musicCarouselShelfRenderer")?.jsonObject ?: continue
            val contents = shelf["contents"]?.jsonArray ?: continue
            for (entry in contents) {
                val item = (entry as? JsonObject)?.get("musicTwoRowItemRenderer")?.jsonObject ?: continue
                val nav = item["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject
                val pageType = nav?.get("browseEndpointContextSupportedConfigs")?.jsonObject
                    ?.get("browseEndpointContextMusicConfig")?.jsonObject
                    ?.get("pageType")?.jsonPrimitive?.content
                if (pageType == "MUSIC_PAGE_TYPE_PLAYLIST") {
                    val rawId = nav["browseId"]?.jsonPrimitive?.content ?: continue
                    chartPlaylistId = rawId.removePrefix("VL")
                    break
                }
            }
            if (chartPlaylistId != null) break
        }

        if (chartPlaylistId != null) {
            getPlaylist(chartPlaylistId, gl = countryCode).getOrThrow().tracks
        } else {
            emptyList()
        }
    }
}
