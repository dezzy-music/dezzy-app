/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadNamingTest {
    @Test
    fun joinsArtistsAndTitle() {
        assertEquals("Daft Punk - One More Time", safeFileName("Daft Punk", "One More Time"))
    }

    @Test
    fun removesIllegalCharacters() {
        assertEquals("ACDC - Back In Black", safeFileName("AC/DC", "Back <In> \"Black\"?"))
        assertEquals("ab - cd", safeFileName("a:b", "c|d*"))
    }

    @Test
    fun capsLengthAt120() {
        val name = safeFileName("A".repeat(100), "B".repeat(100))
        assertTrue(name.length <= 120)
    }

    @Test
    fun trimsTrailingDotsAndFallsBack() {
        assertEquals("Artista - Titulo", safeFileName("Artista", "Titulo..."))
        assertEquals("Sin título", safeFileName("", "???"))
        assertEquals("Solo título", safeFileName("", "Solo título"))
    }
}
