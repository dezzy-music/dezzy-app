/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import com.dezzy.app.core.net.HttpClientFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamUrlResolverTest {

    private val httpClient = HttpClientFactory.create()
    private val poTokenProvider = PlatformPoTokenProvider()
    private val resolver = StreamUrlResolver(httpClient, poTokenProvider)

    @Test
    fun testRealNetworkStreamResolution() = runTest {
        // Bohemian Rhapsody - Queen
        val videoId = "BSTsnWoslP4"
        val result = resolver.resolveStream(videoId, quality = AudioQuality.AUTO)

        assertTrue(result.isSuccess, "La resolución del stream de audio debe tener éxito: ${result.exceptionOrNull()?.message}")
        val audio = result.getOrThrow()

        assertEquals(videoId, audio.videoId)
        assertTrue(audio.url.startsWith("https://"), "La URL del stream debe ser HTTPS")
        assertTrue(audio.url.contains("pot="), "La URL del stream debe contener el parámetro poToken (pot=)")
        assertTrue(audio.mimeType.startsWith("audio/"), "El tipo MIME debe ser de audio: ${audio.mimeType}")
        assertTrue(audio.bitrateKbps > 0, "El bitrate debe ser positivo: ${audio.bitrateKbps}")
        assertTrue(audio.expiresAtMillis > 0, "El tiempo de expiración debe ser válido")
        assertNotNull(audio.clientName, "El nombre del cliente no debe ser nulo")

        // Probar caché: la segunda petición debe devolver exactamente el mismo objeto en memoria
        val cachedResult = resolver.resolveStream(videoId)
        assertTrue(cachedResult.isSuccess)
        assertEquals(audio.url, cachedResult.getOrThrow().url, "La segunda llamada debe venir de la caché")
    }

    @Test
    fun testRealNetworkMultipleTracks() = runTest {
        // Resolver 3 canciones distintas contra la red real para verificar estabilidad
        val testVideos = listOf(
            "fJ9rUzIMcZQ", // Queen - Bohemian Rhapsody (Official Video)
            "L_LUpnjgPso", // Daft Punk - Get Lucky
            "JGwWNGJdvx8"  // Ed Sheeran - Shape of You
        )

        for (id in testVideos) {
            val result = resolver.resolveStream(id, quality = AudioQuality.AUTO)
            assertTrue(result.isSuccess, "Debe resolver audio exitosamente para videoId $id: ${result.exceptionOrNull()?.message}")
            val audio = result.getOrThrow()
            assertTrue(audio.url.isNotBlank(), "La URL no debe estar vacía para $id")
            assertTrue(audio.contentLength > 0L, "El tamaño de contenido debe ser mayor a 0 para $id")
        }
    }
}
