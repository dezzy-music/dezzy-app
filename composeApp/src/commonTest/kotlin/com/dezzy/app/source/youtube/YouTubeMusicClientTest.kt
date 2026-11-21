/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.net.HttpClientFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YouTubeMusicClientTest {
    private val client = YouTubeMusicClient(HttpClientFactory.create())

    @Test
    fun testRealNetworkSearch() = runTest {
        val result = client.search("Queen Bohemian Rhapsody")
        assertTrue(result.isSuccess, "La búsqueda en YouTube Music debe responder con éxito: ${result.exceptionOrNull()?.message}")
        val searchResult = result.getOrThrow()
        assertTrue(searchResult.songs.isNotEmpty(), "La búsqueda debe devolver al menos una canción")

        val firstSong = searchResult.songs.first()
        assertNotNull(firstSong.videoId, "El videoId no debe ser nulo")
        assertTrue(firstSong.title.isNotBlank(), "El título de la canción no debe estar vacío")
    }

    @Test
    fun testRealNetworkHome() = runTest {
        val result = client.getHome()
        assertTrue(result.isSuccess, "El feed de inicio debe responder con éxito: ${result.exceptionOrNull()?.message}")
        val sections = result.getOrThrow()
        assertTrue(sections.isNotEmpty(), "El feed de inicio debe contener estanterías de música")
    }

    @Test
    fun testRealNetworkAlbum() = runTest {
        val search = client.search("Random Access Memories Daft Punk", filterParam = YouTubeEndpoints.FILTER_ALBUMS)
        assertTrue(search.isSuccess, "La búsqueda de álbumes debe responder con éxito")
        val albums = search.getOrThrow().albums
        assertTrue(albums.isNotEmpty(), "Debe encontrarse al menos un álbum")

        val albumBrowseId = albums.first().id
        val albumResult = client.getAlbum(albumBrowseId)
        assertTrue(albumResult.isSuccess, "La llamada del álbum debe responder con éxito: ${albumResult.exceptionOrNull()?.message}")
        val album = albumResult.getOrThrow()
        assertTrue(album.tracks.isNotEmpty(), "El álbum debe tener pistas")
    }
}
