/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import com.dezzy.app.core.database.DefaultMusicRepository
import com.dezzy.app.core.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicRepositoryTest {
    private val repo = DefaultMusicRepository()

    private val sampleTrack = Track(
        videoId = "track_1",
        title = "Canción de prueba",
        artists = "Dezzy",
        album = "Álbum KMP",
        durationSeconds = 180
    )

    @Test
    fun testFavoritesWorkflow() = runTest {
        assertFalse(repo.isFavorite("track_1"))

        repo.setFavorite(sampleTrack, true)
        assertTrue(repo.isFavorite("track_1"))

        val favorites = repo.getFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("Canción de prueba", favorites.first().title)

        repo.setFavorite(sampleTrack, false)
        assertFalse(repo.isFavorite("track_1"))
        val emptyFavorites = repo.getFavorites().first()
        assertEquals(0, emptyFavorites.size)
    }

    @Test
    fun testHistoryWorkflow() = runTest {
        val track2 = sampleTrack.copy(videoId = "track_2", title = "Segunda canción")

        repo.addToHistory(sampleTrack)
        repo.addToHistory(track2)

        val history = repo.getHistory().first()
        assertEquals(2, history.size)
        assertEquals("track_2", history[0].videoId) // Más reciente primero

        // Reinsertar la primera debe moverla al frente sin duplicar
        repo.addToHistory(sampleTrack)
        val updatedHistory = repo.getHistory().first()
        assertEquals(2, updatedHistory.size)
        assertEquals("track_1", updatedHistory[0].videoId)

        repo.clearHistory()
        assertEquals(0, repo.getHistory().first().size)
    }

    @Test
    fun testPlaylistWorkflow() = runTest {
        val playlist = repo.createPlaylist("Favoritas del mes")
        assertEquals("Favoritas del mes", playlist.title)
        assertEquals(0, playlist.trackCount)

        repo.addTrackToPlaylist(playlist.id, sampleTrack)
        val updated = repo.getPlaylistById(playlist.id)
        assertEquals(1, updated?.trackCount)
        assertEquals("Canción de prueba", updated?.tracks?.first()?.title)

        repo.removeTrackFromPlaylist(playlist.id, "track_1")
        val emptyPl = repo.getPlaylistById(playlist.id)
        assertEquals(0, emptyPl?.trackCount)

        repo.deletePlaylist(playlist.id)
        val deleted = repo.getPlaylistById(playlist.id)
        assertEquals(null, deleted)
    }
}
