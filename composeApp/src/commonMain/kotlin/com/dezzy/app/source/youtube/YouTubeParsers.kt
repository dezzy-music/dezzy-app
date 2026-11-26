/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.core.model.SearchResult
import com.dezzy.app.core.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object YouTubeParsers {

    fun parseTrack(
        node: JsonObject,
        fallbackThumb: String? = null,
        fallbackAlbum: String? = null
    ): Track? {
        val renderer = node["musicResponsiveListItemRenderer"]?.jsonObject
            ?: node["playlistPanelVideoRenderer"]?.jsonObject
            ?: node

        // Si el elemento es explícitamente un artista o álbum, no es un track
        val navEndpoint = renderer["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject
        val browseId = navEndpoint?.get("browseId")?.jsonPrimitive?.content
        val pageType = navEndpoint?.get("browseEndpointContextSupportedConfigs")?.jsonObject
            ?.get("browseEndpointContextMusicConfig")?.jsonObject
            ?.get("pageType")?.jsonPrimitive?.content

        if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_ALBUM") {
            return null
        }
        if (browseId != null && (browseId.startsWith("MPREb_") || browseId.startsWith("UC"))) {
            return null
        }

        // Extraer videoId
        val videoId = renderer["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
            ?: renderer["videoId"]?.jsonPrimitive?.content
            ?: renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.content
            ?: renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchPlaylistEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.content
            ?: return null

        val flexColumns = renderer["flexColumns"]?.jsonArray
        val titleText = flexColumns?.getOrNull(0)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")
        val title = YouTubeJsonHelper.runsText(titleText).ifBlank {
            YouTubeJsonHelper.runsText(renderer["title"])
        }

        // Subtítulo / Artistas y Álbum
        val subtitleText = flexColumns?.getOrNull(1)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")
        val runs = subtitleText?.jsonObject?.get("runs")?.jsonArray

        var artists = ""
        var album = fallbackAlbum
        var duration: Long? = null

        if (runs != null && runs.isNotEmpty()) {
            val parts = mutableListOf<String>()
            for (run in runs) {
                val obj = run as? JsonObject ?: continue
                val text = obj["text"]?.jsonPrimitive?.content ?: ""
                val nav = obj["navigationEndpoint"]?.jsonObject
                val pageType = nav?.get("browseEndpoint")?.jsonObject
                    ?.get("browseEndpointContextSupportedConfigs")?.jsonObject
                    ?.get("browseEndpointContextMusicConfig")?.jsonObject
                    ?.get("pageType")?.jsonPrimitive?.content

                when (pageType) {
                    "MUSIC_PAGE_TYPE_ARTIST" -> parts.add(text)
                    "MUSIC_PAGE_TYPE_ALBUM" -> album = text
                    else -> {
                        if (text.contains(":") && duration == null) {
                            duration = YouTubeJsonHelper.parseDurationSeconds(text)
                        } else if (text != " • " && text != " & " && text != ", " && parts.isEmpty()) {
                            parts.add(text)
                        }
                    }
                }
            }
            artists = parts.filter { it.isNotBlank() && it != " • " }.joinToString(", ")
        }

        if (artists.isBlank()) {
            artists = YouTubeJsonHelper.runsText(subtitleText)
        }

        // Duración en columna fija si existe
        if (duration == null) {
            val fixedCols = renderer["fixedColumns"]?.jsonArray
            val fixedText = fixedCols?.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                ?.get("text")
            duration = YouTubeJsonHelper.parseDurationSeconds(YouTubeJsonHelper.runsText(fixedText))
        }

        val thumbnail = YouTubeJsonHelper.pickThumb(renderer["thumbnail"]) ?: fallbackThumb

        return Track(
            videoId = videoId,
            title = title,
            artists = artists,
            album = album,
            thumbnail = thumbnail,
            durationSeconds = duration
        )
    }

    fun parseMusicItem(node: JsonObject): MusicItem? {
        val renderer = node["musicResponsiveListItemRenderer"]?.jsonObject
            ?: node["musicTwoRowItemRenderer"]?.jsonObject
            ?: node

        val isResponsive = node.containsKey("musicResponsiveListItemRenderer") || renderer.containsKey("flexColumns")

        val nav = renderer["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject
            ?: if (isResponsive) {
                renderer["flexColumns"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
            } else null

        val browseId = nav?.get("browseId")?.jsonPrimitive?.content ?: return null

        val pageType = nav["browseEndpointContextSupportedConfigs"]?.jsonObject
            ?.get("browseEndpointContextMusicConfig")?.jsonObject
            ?.get("pageType")?.jsonPrimitive?.content

        val type = when (pageType) {
            "MUSIC_PAGE_TYPE_ALBUM" -> MusicItemType.ALBUM
            "MUSIC_PAGE_TYPE_ARTIST" -> MusicItemType.ARTIST
            "MUSIC_PAGE_TYPE_PLAYLIST" -> MusicItemType.PLAYLIST
            else -> {
                if (browseId.startsWith("MPREb_")) MusicItemType.ALBUM
                else if (browseId.startsWith("VL") || browseId.startsWith("PL")) MusicItemType.PLAYLIST
                else if (browseId.startsWith("UC")) MusicItemType.ARTIST
                else MusicItemType.OTHER
            }
        }

        val title: String
        val subtitle: String

        if (isResponsive) {
            val flexCols = renderer["flexColumns"]?.jsonArray
            val col0 = flexCols?.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")
            val col1 = flexCols?.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")
            title = YouTubeJsonHelper.runsText(col0).ifBlank { YouTubeJsonHelper.runsText(renderer["title"]) }
            subtitle = YouTubeJsonHelper.runsText(col1).ifBlank { YouTubeJsonHelper.runsText(renderer["subtitle"]) }
        } else {
            title = YouTubeJsonHelper.runsText(renderer["title"])
            subtitle = YouTubeJsonHelper.runsText(renderer["subtitle"])
        }

        val thumbnail = YouTubeJsonHelper.pickThumb(renderer["thumbnailRenderer"] ?: renderer["thumbnail"])

        return MusicItem(
            id = browseId,
            type = type,
            title = title,
            subtitle = subtitle,
            thumbnail = thumbnail
        )
    }

    fun parseCarouselShelf(shelfObj: JsonObject): MusicSection? {
        val shelf = shelfObj["musicCarouselShelfRenderer"]?.jsonObject ?: return null
        val header = shelf["header"]?.jsonObject?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject

        val title = YouTubeJsonHelper.runsText(header?.get("title"))
        val strapline = YouTubeJsonHelper.runsText(header?.get("strapline")).ifBlank { null }

        val contents = shelf["contents"]?.jsonArray ?: return null
        val items = contents.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            parseMusicItem(obj)
        }

        if (items.isEmpty()) return null

        return MusicSection(
            title = title,
            strapline = strapline,
            items = items
        )
    }

    /** Rejilla con cabecera (páginas de estados de ánimo y géneros). */
    fun parseGridShelf(shelfObj: JsonObject): MusicSection? {
        val grid = shelfObj["gridRenderer"]?.jsonObject ?: return null
        val title = YouTubeJsonHelper.runsText(
            grid["header"]?.jsonObject?.get("gridHeaderRenderer")?.jsonObject?.get("title")
        )
        val items = grid["items"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseMusicItem) }
        return if (items.isEmpty()) null else MusicSection(title = title, items = items)
    }

    fun parseHomeSections(json: JsonObject): List<MusicSection> {
        val sections = mutableListOf<MusicSection>()
        val contents = json["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray ?: return emptyList()

        for (section in contents) {
            val secObj = section as? JsonObject ?: continue
            val shelf = parseCarouselShelf(secObj) ?: parseGridShelf(secObj)
            shelf?.let { sections.add(it) }
        }

        return sections
    }

    fun parseSearchResults(json: JsonObject): SearchResult {
        val songs = mutableListOf<Track>()
        val albums = mutableListOf<MusicItem>()
        val artists = mutableListOf<MusicItem>()
        val playlists = mutableListOf<MusicItem>()

        fun processItem(itemObj: JsonObject) {
            parseTrack(itemObj)?.let { songs.add(it) }
            parseMusicItem(itemObj)?.let {
                when (it.type) {
                    MusicItemType.ALBUM -> albums.add(it)
                    MusicItemType.ARTIST -> artists.add(it)
                    MusicItemType.PLAYLIST -> playlists.add(it)
                    else -> {}
                }
            }
        }

        val tabs = json["contents"]?.jsonObject
            ?.get("tabbedSearchResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray
        val sectionList = tabs?.getOrNull(0)?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray

        if (sectionList != null) {
            for (section in sectionList) {
                val secObj = section as? JsonObject ?: continue

                // 1. musicCardShelfRenderer (Top result / Tarjeta destacada)
                secObj["musicCardShelfRenderer"]?.jsonObject?.let { card ->
                    val videoId = card["onTap"]?.jsonObject?.get("watchEndpoint")?.jsonObject
                        ?.get("videoId")?.jsonPrimitive?.content
                    if (videoId != null) {
                        val title = YouTubeJsonHelper.runsText(card["title"])
                        val subtitle = YouTubeJsonHelper.runsText(card["subtitle"])
                        val thumb = YouTubeJsonHelper.pickThumb(card["thumbnail"])
                        songs.add(
                            Track(
                                videoId = videoId,
                                title = title,
                                artists = subtitle,
                                album = null,
                                thumbnail = thumb,
                                durationSeconds = null
                            )
                        )
                    }
                    val cardContents = card["contents"]?.jsonArray
                    if (cardContents != null) {
                        for (cItem in cardContents) {
                            val cObj = cItem as? JsonObject ?: continue
                            processItem(cObj)
                        }
                    }
                }

                // 2. musicShelfRenderer
                secObj["musicShelfRenderer"]?.jsonObject?.let { shelf ->
                    val contents = shelf["contents"]?.jsonArray ?: return@let
                    for (item in contents) {
                        val itemObj = item as? JsonObject ?: continue
                        processItem(itemObj)
                    }
                }

                // 3. itemSectionRenderer
                secObj["itemSectionRenderer"]?.jsonObject?.let { itemSec ->
                    val contents = itemSec["contents"]?.jsonArray ?: return@let
                    for (item in contents) {
                        val itemObj = item as? JsonObject ?: continue
                        processItem(itemObj)
                    }
                }
            }
        }

        return SearchResult(
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists
        )
    }

    fun parseAlbum(json: JsonObject, albumId: String): Album {
        val twoColumn = json["contents"]?.jsonObject?.get("twoColumnBrowseResultsRenderer")?.jsonObject
        val header = json["header"]?.jsonObject?.get("musicDetailHeaderRenderer")?.jsonObject
            ?: json["header"]?.jsonObject?.get("musicResponsiveHeaderRenderer")?.jsonObject

        val title = YouTubeJsonHelper.runsText(header?.get("title")).ifBlank { "Álbum" }
        val subtitle = YouTubeJsonHelper.runsText(header?.get("subtitle"))
        val thumbnail = YouTubeJsonHelper.pickThumb(header?.get("thumbnail"))

        val tracks = mutableListOf<Track>()
        val tabs = twoColumn?.get("secondaryContents")?.jsonObject
            ?: json["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject

        val sectionList = tabs?.get("sectionListRenderer")?.jsonObject?.get("contents")?.jsonArray
        if (sectionList != null) {
            for (section in sectionList) {
                val shelf = (section as? JsonObject)?.get("musicShelfRenderer")?.jsonObject ?: continue
                val contents = shelf["contents"]?.jsonArray ?: continue
                for (item in contents) {
                    val itemObj = item as? JsonObject ?: continue
                    parseTrack(itemObj, fallbackThumb = thumbnail, fallbackAlbum = title)?.let {
                        tracks.add(it)
                    }
                }
            }
        }

        return Album(
            id = albumId,
            title = title,
            artist = subtitle,
            thumbnail = thumbnail,
            trackCount = tracks.size,
            tracks = tracks
        )
    }

    fun parsePlaylist(json: JsonObject, playlistId: String): Playlist {
        val header = json["header"]?.jsonObject?.get("musicDetailHeaderRenderer")?.jsonObject
            ?: json["header"]?.jsonObject?.get("musicResponsiveHeaderRenderer")?.jsonObject

        val title = YouTubeJsonHelper.runsText(header?.get("title")).ifBlank { "Playlist" }
        val author = YouTubeJsonHelper.runsText(header?.get("subtitle"))
        val thumbnail = YouTubeJsonHelper.pickThumb(header?.get("thumbnail"))

        val tracks = mutableListOf<Track>()
        val contents = json["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray

        if (contents != null) {
            for (section in contents) {
                val playlistShelf = (section as? JsonObject)?.get("musicPlaylistShelfRenderer")?.jsonObject ?: continue
                val items = playlistShelf["contents"]?.jsonArray ?: continue
                for (item in items) {
                    val itemObj = item as? JsonObject ?: continue
                    parseTrack(itemObj)?.let { tracks.add(it) }
                }
            }
        }

        return Playlist(
            id = playlistId,
            title = title,
            author = author,
            thumbnail = thumbnail,
            trackCount = tracks.size,
            tracks = tracks
        )
    }
}
