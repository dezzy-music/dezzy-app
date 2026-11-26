/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyMatcherTest {

    @Test
    fun normalizaAcentosYMayusculas() {
        assertEquals("corazon espinado", normalizeTitle("Corazón Espinado"))
    }

    @Test
    fun quitaFeatParentesisYRemaster() {
        assertEquals("song", normalizeTitle("Song (feat. Someone)"))
        assertEquals("song", normalizeTitle("Song [Live]"))
        assertEquals("song", normalizeTitle("Song - Remastered 2011"))
        assertEquals("song", normalizeTitle("Song ft. Someone"))
    }

    @Test
    fun conservaTitulosNoLatinos() {
        assertEquals("夜に駆ける", normalizeTitle("夜に駆ける"))
    }
}
