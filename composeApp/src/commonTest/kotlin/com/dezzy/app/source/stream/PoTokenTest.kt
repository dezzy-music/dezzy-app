/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoTokenTest {

    @Test
    fun testColdStartTokenGenerationAndDecoding() {
        val binding = "BSTsnWoslP4"
        val token = PoToken.createColdStartToken(binding, clientState = 1)
        assertTrue(token.isNotBlank(), "El token de inicio en frío no debe estar vacío")

        val decoded = PoToken.decodeColdStartToken(token)
        assertEquals(binding, decoded.contentBinding, "El contentBinding decodificado debe coincidir")
        assertEquals(1, decoded.clientState, "El clientState debe ser 1")
        assertEquals(2, decoded.keys.size, "Debe haber 2 claves aleatorias")
        assertTrue(decoded.timestampSeconds > 0, "El timestamp debe ser positivo")
    }

    @Test
    fun testDeterministicVector() {
        val binding = "test_visitor_data_12345"
        val forcedKeys = byteArrayOf(84.toByte(), 117.toByte())
        val timestamp = 1795294008L
        val token = PoToken.createColdStartToken(
            contentBinding = binding,
            clientState = 1,
            timestampSeconds = timestamp,
            forcedKeys = forcedKeys
        )

        val decoded = PoToken.decodeColdStartToken(token)
        assertEquals(binding, decoded.contentBinding)
        assertEquals(timestamp, decoded.timestampSeconds)
        assertEquals(listOf(84, 117), decoded.keys)
    }
}
