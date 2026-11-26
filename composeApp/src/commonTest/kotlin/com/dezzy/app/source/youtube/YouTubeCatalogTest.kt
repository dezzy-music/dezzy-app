/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.model.Track
import com.dezzy.app.core.net.HttpClientFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeCatalogTest {

    private fun video(id: String, title: String, artists: String, d: Long?) =
        Track(videoId = id, title = title, artists = artists, durationSeconds = d)

    @Test
    fun normalizeDropsAccentsAndSymbols() {
        assertEquals("cancion de amor", YouTubeCatalogParsers.normalize("Canción de Amor!"))
        assertEquals("beyonce", YouTubeCatalogParsers.normalize("Beyoncé"))
    }

    @Test
    fun picksTheRealMusicVideo() {
        val candidates = listOf(
            video("lyr", "Blinding Lights (Lyrics)", "The Weeknd", 200),
            video("cov", "Blinding Lights cover", "Someone", 199),
            video("mv", "The Weeknd - Blinding Lights (Official Video)", "The Weeknd", 263),
        )
        // La oficial dura más que la canción pero menos de un minuto de diferencia.
        assertEquals("mv", YouTubeCatalogParsers.pickMusicVideo(candidates, "Blinding Lights", "The Weeknd", 220))
    }

    @Test
    fun noVideoWhenNothingMatches() {
        val candidates = listOf(video("x", "Otra cosa", "Nadie", 600))
        assertNull(YouTubeCatalogParsers.pickMusicVideo(candidates, "Blinding Lights", "The Weeknd", 200))
    }

    @Test
    fun cleanDescriptionRemovesPlaceholders() {
        assertEquals("", YouTubeCatalogParsers.cleanDescription("N/A"))
        assertEquals("Bio", YouTubeCatalogParsers.cleanDescription(" Bio "))
    }

    @Test
    fun parsesQueuePanel() {
        val json = Json.parseToJsonElement(
            """
            {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"musicQueueRenderer":{"content":{"playlistPanelRenderer":{"contents":[
              {"playlistPanelVideoRenderer":{"videoId":"seed","title":{"runs":[{"text":"Semilla"}]}}},
              {"playlistPanelVideoRenderer":{"videoId":"a1","title":{"runs":[{"text":"Uno"}]},
                "longBylineText":{"runs":[
                  {"text":"Artista","navigationEndpoint":{"browseEndpoint":{"browseId":"UC1","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
                  {"text":" • "},
                  {"text":"Disco","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_1","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}
                ]},
                "lengthText":{"runs":[{"text":"3:05"}]},
                "thumbnail":{"thumbnails":[{"url":"u","width":300}]}}},
              {"playlistPanelVideoWrapperRenderer":{"primaryRenderer":{"playlistPanelVideoRenderer":{"videoId":"a2","title":{"runs":[{"text":"Dos"}]},"shortBylineText":{"runs":[{"text":"Subido • 1 M vistas"}]}}}}},
              {"playlistPanelVideoRenderer":{"videoId":"a1","title":{"runs":[{"text":"Uno repetido"}]}}}
            ]}}}}}}]}}}}}
            """.trimIndent(),
        ).jsonObject
        val tracks = YouTubeCatalogParsers.parseUpNext(json, "seed")
        assertEquals(listOf("a1", "a2"), tracks.map { it.videoId })
        assertEquals("Artista", tracks[0].artists)
        assertEquals("Disco", tracks[0].album)
        assertEquals(185L, tracks[0].durationSeconds)
        assertEquals("Subido", tracks[1].artists)
    }

    // Con red ----------------------------------------------------------------------

    private val client = YouTubeMusicClient(HttpClientFactory.create())

    @Test
    fun realUpNextArtistAndSuggestions() = runTest {
        val song = client.search("Queen Bohemian Rhapsody").getOrThrow().songs.first()
        val upNext = client.getUpNext(song.videoId!!).getOrThrow()
        assertTrue(upNext.size >= 5, "La radio debe traer canciones: ${upNext.size}")
        assertTrue(upNext.none { it.videoId == song.videoId })

        val artistId = client.findArtistId("Queen").getOrThrow()
        assertTrue(artistId?.startsWith("UC") == true, "Id de artista: $artistId")
        val artist = client.getArtist(artistId!!).getOrThrow()
        assertTrue(artist.name.isNotBlank())
        assertTrue(artist.songs.isNotEmpty() || artist.shelves.isNotEmpty(), "El artista debe tener contenido")

        val suggestions = client.getSearchSuggestions("bohemian").getOrThrow()
        assertTrue(suggestions.queries.isNotEmpty(), "Debe haber sugerencias")
    }

    @Test
    fun realMoodsAndGenres() = runTest {
        val moods = client.getMoodsAndGenres().getOrThrow()
        assertTrue(moods.isNotEmpty(), "sin bloques")
        val first = moods.first().items.first()
        val page = client.getBrowseSections(first.browseId, first.params).getOrThrow()
        println("moods=${moods.map { it.title + ":" + it.items.size }} page(${first.title})=${page.map { it.title + ":" + it.items.size }}")
        assertTrue(page.isNotEmpty(), "página vacía")
    }
}
