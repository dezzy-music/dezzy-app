/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LyricsParsersTest {
    @Test
    fun parseLrcBasico() {
        val lines = parseLrc("[ar:Alguien]\n[00:10.00] Hola  mundo\r\n[00:05.50]Antes")
        assertEquals(listOf(5500L, 10000L), lines.map { it.startMs })
        assertEquals(listOf("Antes", "Hola mundo"), lines.map { it.text })
        assertNull(lines[0].words)
    }

    @Test
    fun parseLrcVariasMarcasPorLinea() {
        val lines = parseLrc("[00:10.00][00:20.00]coro\n[00:15.00]verso")
        assertEquals(listOf(10000L, 15000L, 20000L), lines.map { it.startMs })
        assertEquals(listOf("coro", "verso", "coro"), lines.map { it.text })
    }

    @Test
    fun parseLrcMejoradoConPalabras() {
        val lines = parseLrc("[00:10.00]<00:10.00>Hello <00:10.50>world\n[00:12.00]siguiente")
        assertEquals("Hello world", lines[0].text)
        val words = assertNotNull(lines[0].words)
        assertEquals(2, words.size)
        assertEquals(LyricWord(10000, 10500, "Hello "), words[0])
        assertEquals(LyricWord(10500, 12000, "world"), words[1])
        assertNull(lines[1].words)
    }

    @Test
    fun parseLrcFracciones() {
        val lines = parseLrc("[00:01.5]a\n[00:02.05]b\n[00:03.005]c\n[00:04:50]d\n[1:05]e")
        assertEquals(listOf(1500L, 2050L, 3005L, 4500L, 65000L), lines.map { it.startMs })
    }

    @Test
    fun parseLrcConservaLineasVacias() {
        val lines = parseLrc("[00:01.00]\n[00:02.00]hola")
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
    }

    @Test
    fun parseTtmlConSpans() {
        val ttml = """
            <tt><body><div>
            <p begin="00:10.000" end="00:12.000"><span begin="00:10.000" end="00:10.500">Hello</span> <span begin="00:10.500" end="00:12.000">world</span></p>
            <p begin="14.5s">Sin spans &amp; entidades</p>
            </div></body></tt>
        """.trimIndent()
        val lines = parseTtml(ttml)
        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].startMs)
        assertEquals("Hello world", lines[0].text)
        val words = assertNotNull(lines[0].words)
        assertEquals(LyricWord(10000, 10500, "Hello "), words[0])
        assertEquals(LyricWord(10500, 12000, "world"), words[1])
        assertEquals(14500L, lines[1].startMs)
        assertEquals("Sin spans & entidades", lines[1].text)
        assertNull(lines[1].words)
    }

    @Test
    fun parseTtmlConCoroDeFondo() {
        val ttml = """<p begin="12.5s"><span begin="12.5" end="13">uno</span> <span ttm:role="x-bg"><span begin="13" end="13.5">oh</span></span></p>"""
        val lines = parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals("uno (oh)", lines[0].text)
        val words = assertNotNull(lines[0].words)
        assertEquals("(oh)", words.last().text)
        assertEquals(13500L, words.last().endMs)
    }

    @Test
    fun activeLineIndexAntesEnMedioYDespues() {
        val lines = listOf(SyncedLine(1000, "a"), SyncedLine(2000, "b"), SyncedLine(3000, "c"))
        assertEquals(-1, activeLineIndex(lines, 500))
        assertEquals(0, activeLineIndex(lines, 1000))
        assertEquals(1, activeLineIndex(lines, 2500))
        assertEquals(2, activeLineIndex(lines, 99999))
        assertEquals(-1, activeLineIndex(emptyList(), 100))
    }

    @Test
    fun toLrcIdaYVuelta() {
        val lines = listOf(SyncedLine(1500, "a"), SyncedLine(62340, "b"), SyncedLine(70000, ""))
        val lrc = lines.toLrc()
        assertEquals("[00:01.50]a\n[01:02.34]b\n[01:10.00]", lrc)
        assertEquals(lines, parseLrc(lrc))
    }

    @Test
    fun stripWordStampsQuitaMarcas() {
        assertEquals("Hello world", stripWordStamps("<00:01.00>Hello <00:01.50>world"))
    }
}
