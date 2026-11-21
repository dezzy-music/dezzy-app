/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.core.model.SearchResult
import com.dezzy.app.core.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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
    private val httpClient: HttpClient
) {
    private fun buildContext(hl: String = "es", gl: String = "US"): JsonObject {
        return buildJsonObject {
            putJsonObject("client") {
                put("clientName", YouTubeEndpoints.CLIENT_NAME)
                put("clientVersion", YouTubeEndpoints.CLIENT_VERSION)
                put("hl", hl)
                put("gl", gl)
            }
        }
    }

    suspend fun getHome(gl: String = "US"): Result<List<MusicSection>> = runCatching {
        val payload = buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", YouTubeEndpoints.BROWSE_ID_HOME)
        }

        val response: JsonObject = httpClient.post(YouTubeEndpoints.BROWSE_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, YouTubeEndpoints.ORIGIN)
            header(HttpHeaders.Referrer, YouTubeEndpoints.REFERER)
            setBody(payload)
        }.body()

        YouTubeParsers.parseHomeSections(response)
    }

    suspend fun search(query: String, filterParam: String? = null, gl: String = "US"): Result<SearchResult> = runCatching {
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

    private suspend fun searchSingle(query: String, filterParam: String?, gl: String): SearchResult {
        val payload = buildJsonObject {
            put("context", buildContext(gl = gl))
            put("query", query)
            if (filterParam != null) {
                put("params", filterParam)
            }
        }

        val response: JsonObject = httpClient.post(YouTubeEndpoints.SEARCH_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, YouTubeEndpoints.ORIGIN)
            header(HttpHeaders.Referrer, YouTubeEndpoints.REFERER)
            setBody(payload)
        }.body()

        return YouTubeParsers.parseSearchResults(response)
    }

    suspend fun getAlbum(browseId: String, gl: String = "US"): Result<Album> = runCatching {
        val id = if (browseId.startsWith("MPREb_")) browseId else "MPREb_$browseId"
        val payload = buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", id)
        }

        val response: JsonObject = httpClient.post(YouTubeEndpoints.BROWSE_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, YouTubeEndpoints.ORIGIN)
            header(HttpHeaders.Referrer, YouTubeEndpoints.REFERER)
            setBody(payload)
        }.body()

        YouTubeParsers.parseAlbum(response, id)
    }

    suspend fun getPlaylist(playlistId: String, gl: String = "US"): Result<Playlist> = runCatching {
        val id = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val payload = buildJsonObject {
            put("context", buildContext(gl = gl))
            put("browseId", id)
        }

        val response: JsonObject = httpClient.post(YouTubeEndpoints.BROWSE_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, YouTubeEndpoints.ORIGIN)
            header(HttpHeaders.Referrer, YouTubeEndpoints.REFERER)
            setBody(payload)
        }.body()

        YouTubeParsers.parsePlaylist(response, playlistId)
    }

    suspend fun getCountryCharts(countryCode: String): Result<List<Track>> = runCatching {
        val payload = buildJsonObject {
            put("context", buildContext(hl = "en", gl = countryCode))
            put("browseId", YouTubeEndpoints.BROWSE_ID_CHARTS)
            put("params", YouTubeEndpoints.CHARTS_PARAMS)
            putJsonObject("formData") {
                putJsonArray("selectedValues") {
                    add(kotlinx.serialization.json.JsonPrimitive(countryCode))
                }
            }
        }

        val response: JsonObject = httpClient.post(YouTubeEndpoints.BROWSE_URL) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, YouTubeEndpoints.ORIGIN)
            header(HttpHeaders.Referrer, YouTubeEndpoints.REFERER)
            setBody(payload)
        }.body()

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
