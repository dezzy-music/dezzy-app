/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlacTaggerTest {
    private val streamInfo = ByteArray(34) { (it + 1).toByte() }

    /** "fLaC" + STREAMINFO (último bloque) + unos bytes de "audio". */
    private fun minimalFlac(): ByteArray {
        val head = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(),
            0x80.toByte(), 0, 0, 34)
        return head + streamInfo + byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 1, 2, 3)
    }

    private val jpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xC0.toByte(), 0, 17, 8, 0x01, 0x2C, 0x01, 0x90.toByte(), 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1,
        0xFF.toByte(), 0xD9.toByte(),
    )

    @Test
    fun etiquetasYPortada() {
        val flac = minimalFlac()
        val rewrite = assertNotNull(
            FlacTagger.apply(flac, mapOf("TITLE" to "Canción", "ARTIST" to "Ana", "ALBUM" to "Disco", "ISRC" to "USRC17607839"), jpeg),
        )
        assertEquals(4 + 4 + 34, rewrite.oldHeadLength)

        val blocks = assertNotNull(FlacTagger.readBlocks(rewrite.head))
        assertEquals(
            listOf(FlacTagger.TYPE_STREAMINFO, FlacTagger.TYPE_VORBIS_COMMENT, FlacTagger.TYPE_PICTURE),
            blocks.map { it.type },
        )
        assertContentEquals(streamInfo, blocks[0].data)
        // Sólo el último bloque lleva el bit de "último".
        assertEquals(0, rewrite.head[4].toInt() and 0x80)
        assertTrue(blocks.size == 3)

        val comments = assertNotNull(FlacTagger.readComments(rewrite.head))
        assertEquals("Canción", comments["TITLE"])
        assertEquals("Ana", comments["ARTIST"])
        assertEquals("Disco", comments["ALBUM"])
        assertEquals("USRC17607839", comments["ISRC"])

        val pic = blocks[2].data
        // tipo 3 (portada frontal), mime "image/jpeg"
        assertEquals(3, pic[3].toInt())
        assertEquals("image/jpeg".length, pic[7].toInt())
        assertEquals("image/jpeg", pic.decodeToString(8, 8 + "image/jpeg".length))
        // ancho 400 (alto 300) leídos del SOF del JPEG
        val o = 8 + "image/jpeg".length + 4
        assertEquals(400, ((pic[o].toInt() and 0xFF) shl 24) or ((pic[o + 1].toInt() and 0xFF) shl 16) or
            ((pic[o + 2].toInt() and 0xFF) shl 8) or (pic[o + 3].toInt() and 0xFF))
        assertContentEquals(jpeg, pic.copyOfRange(pic.size - jpeg.size, pic.size))
    }

    @Test
    fun volverAEtiquetarSustituyeEnVezDeDuplicar() {
        val first = assertNotNull(FlacTagger.apply(minimalFlac(), mapOf("TITLE" to "Uno", "COMMENT" to "x"), jpeg))
        val second = assertNotNull(FlacTagger.apply(first.head, mapOf("TITLE" to "Dos"), jpeg))
        val blocks = assertNotNull(FlacTagger.readBlocks(second.head))
        assertEquals(1, blocks.count { it.type == FlacTagger.TYPE_VORBIS_COMMENT })
        assertEquals(1, blocks.count { it.type == FlacTagger.TYPE_PICTURE })
        val comments = assertNotNull(FlacTagger.readComments(second.head))
        assertEquals("Dos", comments["TITLE"])
        assertEquals("x", comments["COMMENT"])
        assertEquals(first.head.size, second.oldHeadLength)
    }

    @Test
    fun sinPortadaNoAnadeBloquePicture() {
        val r = assertNotNull(FlacTagger.apply(minimalFlac(), mapOf("TITLE" to "Solo texto"), null))
        val blocks = assertNotNull(FlacTagger.readBlocks(r.head))
        assertEquals(listOf(FlacTagger.TYPE_STREAMINFO, FlacTagger.TYPE_VORBIS_COMMENT), blocks.map { it.type })
    }

    @Test
    fun noFlacOCabeceraCortadaDevuelveNull() {
        assertNull(FlacTagger.apply(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9), mapOf("TITLE" to "x"), null))
        assertNull(FlacTagger.apply(minimalFlac().copyOf(20), mapOf("TITLE" to "x"), null))
    }
}
