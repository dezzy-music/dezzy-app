/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Sugerencias del buscador: textos para completar y resultados directos. */
data class SearchSuggestions(
    val queries: List<String> = emptyList(),
    val items: List<MusicItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

/**
 * Lectores de «a continuación», artista y sugerencias del catálogo, y la elección del videoclip.
 * El comportamiento es el de Dezzy Desktop (`src/main/music.ts`); el formato es el JSON público
 * de los endpoints `next`, `browse` y `music/get_search_suggestions`.
 */
object YouTubeCatalogParsers {

    private fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
    private fun JsonElement?.arr(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
    private fun JsonElement?.str(key: String): String? =
        ((this as? JsonObject)?.get(key))?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun pageType(nav: JsonObject?): String? =
        nav.obj("browseEndpoint").obj("browseEndpointContextSupportedConfigs")
            .obj("browseEndpointContextMusicConfig").str("pageType")

    /** Una fila de la cola del reproductor (`playlistPanelVideoRenderer`). */
    fun parsePanelVideo(node: JsonObject): Track? {
        val renderer = node.obj("playlistPanelVideoRenderer")
            ?: node.obj("playlistPanelVideoWrapperRenderer").obj("primaryRenderer").obj("playlistPanelVideoRenderer")
            ?: return null
        val videoId = renderer.str("videoId") ?: return null
        val title = YouTubeJsonHelper.runsText(renderer["title"])
        val byline = renderer.obj("longBylineText") ?: renderer.obj("shortBylineText")
        val artists = mutableListOf<String>()
        var album: String? = null
        val runs = byline.arr("runs")
        if (runs != null) {
            for (run in runs) {
                val text = run.str("text") ?: continue
                when (pageType(run.obj("navigationEndpoint"))) {
                    "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> artists += text
                    "MUSIC_PAGE_TYPE_ALBUM" -> album = text
                }
            }
            // Sin enlaces (vídeos subidos): el primer trozo antes de « • » es el artista.
            if (artists.isEmpty()) {
                YouTubeJsonHelper.runsText(byline).split(" • ").firstOrNull()?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { artists += it }
            }
        }
        return Track(
            videoId = videoId,
            title = title,
            artists = artists.joinToString(", "),
            album = album,
            thumbnail = YouTubeJsonHelper.pickThumb(renderer["thumbnail"]),
            durationSeconds = YouTubeJsonHelper.parseDurationSeconds(YouTubeJsonHelper.runsText(renderer["lengthText"])),
        )
    }

    /** La radio que sigue a una canción, sin la propia canción. */
    fun parseUpNext(json: JsonObject, seedVideoId: String): List<Track> {
        val tabs = json.obj("contents").obj("singleColumnMusicWatchNextResultsRenderer").obj("tabbedRenderer")
            .obj("watchNextTabbedResultsRenderer").arr("tabs")
        val panel = (tabs?.firstOrNull()).obj("tabRenderer").obj("content").obj("musicQueueRenderer")
            .obj("content").obj("playlistPanelRenderer")
            // La continuación trae el panel en otro sitio.
            ?: json.obj("continuationContents").obj("playlistPanelContinuation")
            ?: return emptyList()
        val seen = HashSet<String>()
        return panel.arr("contents").orEmpty().mapNotNull { (it as? JsonObject)?.let(::parsePanelVideo) }
            .filter { it.videoId != seedVideoId && seen.add(it.videoId!!) }
    }

    /** Página de artista: cabecera, canciones populares y estanterías (álbumes, sencillos, parecidos…). */
    fun parseArtist(json: JsonObject, id: String): Artist {
        val headerRoot = json.obj("header")
        val header = headerRoot.obj("musicImmersiveHeaderRenderer")
            ?: headerRoot.obj("musicVisualHeaderRenderer")
            ?: headerRoot.obj("musicResponsiveHeaderRenderer")
        val name = YouTubeJsonHelper.runsText(header?.get("title"))
        // Para la cabecera se quiere la foto grande, no la de 240 px.
        val thumbs = header.obj("thumbnail").obj("musicThumbnailRenderer").obj("thumbnail").arr("thumbnails")
            ?: header.obj("foregroundThumbnail").obj("musicThumbnailRenderer").obj("thumbnail").arr("thumbnails")
        val big = thumbs?.mapNotNull { t ->
            val url = t.str("url") ?: return@mapNotNull null
            (t.str("width")?.toIntOrNull() ?: 0) to url
        }?.maxByOrNull { it.first }?.second
        val sub = header.obj("subscriptionButton").obj("subscribeButtonRenderer")
        val listeners = YouTubeJsonHelper.runsText(header?.get("monthlyListenerCount")).ifBlank {
            YouTubeJsonHelper.runsText(sub?.get("subscriberCountText")).ifBlank {
                YouTubeJsonHelper.runsText(sub?.get("shortSubscriberCountText"))
            }
        }
        var description = YouTubeJsonHelper.runsText(header?.get("description"))

        val sections = json.obj("contents").obj("singleColumnBrowseResultsRenderer").arr("tabs")?.firstOrNull()
            .obj("tabRenderer").obj("content").obj("sectionListRenderer").arr("contents").orEmpty()
        var songs = emptyList<Track>()
        val shelves = mutableListOf<MusicSection>()
        for (section in sections) {
            val obj = section as? JsonObject ?: continue
            val shelf = obj.obj("musicShelfRenderer")
            if (shelf != null && songs.isEmpty()) {
                songs = shelf.arr("contents").orEmpty()
                    .mapNotNull { (it as? JsonObject)?.let { n -> YouTubeParsers.parseTrack(n) } }.take(10)
                continue
            }
            val carousel = YouTubeParsers.parseCarouselShelf(obj)
            if (carousel != null) {
                shelves += carousel
                continue
            }
            val about = obj.obj("musicDescriptionShelfRenderer")
            if (about != null && description.isBlank()) description = YouTubeJsonHelper.runsText(about["description"])
        }
        return Artist(
            id = id,
            name = name,
            thumbnail = big,
            listeners = listeners.ifBlank { null },
            description = cleanDescription(description).ifBlank { null },
            songs = songs,
            shelves = shelves,
            subscribed = (sub?.get("subscribed"))?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() },
        )
    }

    /** El catálogo devuelve «N/A» o vacío cuando no hay biografía. */
    fun cleanDescription(text: String?): String {
        val t = text.orEmpty().trim()
        return if (Regex("^(n/a|null|undefined)$", RegexOption.IGNORE_CASE).matches(t)) "" else t
    }

    fun parseSuggestions(json: JsonObject): SearchSuggestions {
        val queries = mutableListOf<String>()
        val items = mutableListOf<MusicItem>()
        val tracks = mutableListOf<Track>()
        for (section in json.arr("contents").orEmpty()) {
            for (entry in section.obj("searchSuggestionsSectionRenderer").arr("contents").orEmpty()) {
                val obj = entry as? JsonObject ?: continue
                val suggestion = obj.obj("searchSuggestionRenderer")
                if (suggestion != null) {
                    val text = suggestion.obj("navigationEndpoint").obj("searchEndpoint").str("query")
                        ?: YouTubeJsonHelper.runsText(suggestion["suggestion"])
                    if (text.isNotBlank()) queries += text
                    continue
                }
                if (obj.containsKey("musicResponsiveListItemRenderer")) {
                    val item = YouTubeParsers.parseMusicItem(obj)
                    if (item != null) items += item else YouTubeParsers.parseTrack(obj)?.let { tracks += it }
                }
            }
        }
        return SearchSuggestions(queries.distinct(), items, tracks)
    }

    // Videoclip ---------------------------------------------------------------------

    private val ACCENTS = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'ã' to 'a', 'å' to 'a',
        'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o', 'õ' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
        'ñ' to 'n', 'ç' to 'c', 'ý' to 'y', 'ÿ' to 'y',
    )

    /** Minúsculas, sin tildes y sólo letras, cifras y espacios (como `norm` del escritorio). */
    fun normalize(s: String): String =
        s.lowercase().map { c -> ACCENTS[c] ?: c }
            .map { c -> if (c in 'a'..'z' || c in '0'..'9' || c == ' ') c else ' ' }
            .joinToString("").trim()

    private val VERSION_WORDS = Regex("lyric|letra|audio|cover|live|en vivo", RegexOption.IGNORE_CASE)
    private val WANTED_VERSION = Regex("lyric|letra|live|en vivo", RegexOption.IGNORE_CASE)

    /**
     * El videoclip que mejor encaja con la canción entre los primeros seis vídeos: artista (+2),
     * título (+3), duración parecida (+2, o −3 si difiere más de un minuto) y versiones que no se
     * pidieron (−2). Hace falta llegar a 4 puntos.
     */
    fun pickMusicVideo(candidates: List<Track>, title: String, artists: String, durationSeconds: Long?): String? {
        val firstArtist = normalize(artists.split(',').firstOrNull().orEmpty())
        val base = normalize(title.split(" (").first())
        var bestId: String? = null
        var bestScore = Int.MIN_VALUE
        for (t in candidates.take(6)) {
            val id = t.videoId ?: continue
            var s = 0
            if (firstArtist.isNotEmpty() && normalize("${t.artists} ${t.title}").contains(firstArtist)) s += 2
            if (base.isNotEmpty() && normalize(t.title).contains(base)) s += 3
            val d = t.durationSeconds
            if (durationSeconds != null && durationSeconds > 0 && d != null && d > 0) {
                val diff = kotlin.math.abs(d - durationSeconds)
                s += if (diff <= 15) 2 else if (diff > 60) -3 else 0
            }
            if (VERSION_WORDS.containsMatchIn(t.title) && !WANTED_VERSION.containsMatchIn(title)) s -= 2
            if (bestId == null || s > bestScore) {
                bestId = id
                bestScore = s
            }
        }
        return if (bestId != null && bestScore >= 4) bestId else null
    }
}
