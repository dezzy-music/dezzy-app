/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Una lista oficial de éxitos de YouTube Music (por ejemplo «Daily Top Music Videos - México»). */
data class ChartPlaylist(val id: String, val title: String, val thumbnail: String?)

/**
 * Lee las listas de éxitos de la página `FEmusic_charts`.
 *
 * YouTube Music ya no devuelve canciones en esa página: devuelve las playlists oficiales de cada
 * país («Daily Top Music Videos», «Trending 20», «Top 100 Music Videos», géneros, directos…).
 */
internal object YouTubeCharts {
    /** Código que YouTube Music usa para el chart mundial. */
    const val GLOBAL = "ZZ"

    fun parsePlaylists(response: JsonObject): List<ChartPlaylist> {
        val sections = response["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray?.getOrNull(0)?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents") as? JsonArray ?: return emptyList()
        val out = ArrayList<ChartPlaylist>()
        for (section in sections) {
            val shelf = (section as? JsonObject)?.get("musicCarouselShelfRenderer") as? JsonObject ?: continue
            val items = shelf["contents"] as? JsonArray ?: continue
            for (entry in items) {
                val item = (entry as? JsonObject)?.get("musicTwoRowItemRenderer") as? JsonObject ?: continue
                val browse = item["navigationEndpoint"]?.jsonObject?.get("browseEndpoint") as? JsonObject ?: continue
                val pageType = browse["browseEndpointContextSupportedConfigs"]?.jsonObject
                    ?.get("browseEndpointContextMusicConfig")?.jsonObject
                    ?.get("pageType")?.jsonPrimitive?.content
                if (pageType != "MUSIC_PAGE_TYPE_PLAYLIST") continue
                val id = browse["browseId"]?.jsonPrimitive?.content?.removePrefix("VL").orEmpty()
                if (id.isBlank()) continue
                val title = item["title"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content.orEmpty()
                val thumbnail = item["thumbnailRenderer"]?.jsonObject
                    ?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject
                    ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.content
                out += ChartPlaylist(id, title, thumbnail)
            }
        }
        return out
    }

    /**
     * La lista que sirve como top del país: top diario → tendencias → top 100. Nunca directos ni
     * géneros. Si YouTube no tiene top de ese país devuelve el mundial, que no vale para el país.
     */
    fun pickTop(code: String, playlists: List<ChartPlaylist>): ChartPlaylist? {
        val candidates = if (code == GLOBAL) playlists else playlists.filterNot { it.title.lowercase().contains("global") }
        val rules = listOf<(String) -> Boolean>(
            { it.startsWith("daily top music videos") },
            { it.startsWith("trending") },
            { it.startsWith("top 100 music videos") },
        )
        for (rule in rules) {
            candidates.firstOrNull { rule(it.title.lowercase()) }?.let { return it }
        }
        return candidates.firstOrNull { !it.title.lowercase().contains("live") }
    }
}
