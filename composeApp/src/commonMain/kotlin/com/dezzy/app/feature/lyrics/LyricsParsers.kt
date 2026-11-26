/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import kotlin.math.max
import kotlin.math.roundToLong

private val LINE_STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
private val BRACKETS = Regex("""\[[^\]]*\]""")
private val WS = Regex("""\s+""")
private val NEWLINE = Regex("""\r?\n""")
private val TAG = Regex("""<[^>]+>""")

/** «[01:02.34]» o «<01:02.5>» → ms. La fracción acepta 1-3 dígitos (".5" = 500 ms, ".05" = 50 ms). */
private fun stampMs(m: MatchResult): Long {
    val min = m.groupValues[1].toLong()
    val sec = m.groupValues[2].toLong()
    val frac = m.groupValues[3].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toLong() ?: 0L
    return min * 60_000 + sec * 1000 + frac
}

/** Quita las marcas por palabra («<00:01.23>») de una letra sin tiempos de línea. */
fun stripWordStamps(text: String): String =
    WORD_STAMP.replace(text, "").replace(Regex("""[ \t]+"""), " ").trim()

private class Wd(val start: Long, var end: Long, var text: String)

private fun buildLrcWords(body: String, matches: List<MatchResult>): List<LyricWord>? {
    val list = ArrayList<Wd>()
    for ((k, m) in matches.withIndex()) {
        val textEnd = if (k + 1 < matches.size) matches[k + 1].range.first else body.length
        val seg = WS.replace(body.substring(m.range.last + 1, textEnd), " ")
        val start = stampMs(m)
        val end = if (k + 1 < matches.size) stampMs(matches[k + 1]) else -1L
        val text = seg.trimStart()
        if (text.isBlank()) {
            // Sólo un espacio: la palabra anterior acaba con espacio.
            if (seg.isNotEmpty()) list.lastOrNull()?.let { if (!it.text.endsWith(" ")) it.text += " " }
            continue
        }
        list += Wd(start, max(end, start).let { if (end < 0) -1L else it }, text)
    }
    if (list.isEmpty()) return null
    list.last().text = list.last().text.trimEnd()
    return list.map { LyricWord(it.start, it.end, it.text) }
}

/**
 * «[01:02.34] texto» → líneas con tiempo; las líneas vacías se conservan como pausas. Admite varias
 * marcas por línea y, en el LRC mejorado, marcas por palabra «<mm:ss.xx>» que se guardan en `words`.
 */
fun parseLrc(text: String): List<SyncedLine> {
    val lines = ArrayList<SyncedLine>()
    for (raw in text.split(NEWLINE)) {
        val stamps = LINE_STAMP.findAll(raw).map { stampMs(it) }.toList()
        if (stamps.isEmpty()) continue
        val body = BRACKETS.replace(raw, "")
        val wordMatches = WORD_STAMP.findAll(body).toList()
        val clean = WS.replace(WORD_STAMP.replace(body, ""), " ").trim()
        val words = if (wordMatches.isEmpty()) null else buildLrcWords(body, wordMatches)
        for (s in stamps) lines += SyncedLine(s, clean, words)
    }
    val sorted = lines.sortedBy { it.startMs }
    // La última palabra de cada línea acaba donde empieza la línea siguiente.
    return sorted.mapIndexed { i, line ->
        val w = line.words
        if (w == null || w.last().endMs >= 0) {
            line
        } else {
            val last = w.last()
            val next = sorted.getOrNull(i + 1)?.startMs
            val end = if (next != null && next > last.startMs) next else last.startMs + 1000
            line.copy(words = w.dropLast(1) + last.copy(endMs = end))
        }
    }
}

private fun ttmlMs(value: String?): Long? {
    if (value == null) return null
    val parts = value.trim().removeSuffix("s").split(":").map { it.toDoubleOrNull() ?: return null }
    if (parts.isEmpty()) return null
    return (parts.fold(0.0) { acc, n -> acc * 60 + n } * 1000).roundToLong()
}

private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'")
private val ENTITY = Regex("""&(#x?[0-9a-fA-F]+|\w+);""")

private fun codePointString(cp: Int): String? = when {
    cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF -> null
    cp < 0x10000 -> cp.toChar().toString()
    else -> {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
}

private fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
    val e = m.groupValues[1]
    if (e[0] != '#') {
        ENTITIES[e] ?: m.value
    } else {
        val hex = e.length > 1 && (e[1] == 'x' || e[1] == 'X')
        val code = e.substring(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10)
        (code?.let { codePointString(it) }) ?: m.value
    }
}

private val P_TAG = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""")
private val BEGIN_ATTR = Regex("""\bbegin="([^"]+)"""")
private val END_ATTR = Regex("""\bend="([^"]+)"""")
private val BG_SPAN = Regex("""<span[^>]*ttm:role="x-bg"[^>]*>([\s\S]*?</span>)\s*</span>""")
private val LEAF_SPAN = Regex("""<span\b([^>]*)>([^<]*)</span>""")
private val BR_TAG = Regex("""<br\s*/?>""")
private val OPEN_GAP = Regex("""\(\s+""")
private val CLOSE_GAP = Regex("""\s+\)""")

private class TtmlWord(val start: Long, val end: Long, var text: String, val bg: Int)

private fun buildTtmlWords(raw: String, bgRanges: List<IntRange>): List<LyricWord>? {
    val leafs = LEAF_SPAN.findAll(raw).toList()
    val list = ArrayList<TtmlWord>()
    for ((i, lm) in leafs.withIndex()) {
        val attrs = lm.groupValues[1]
        val start = ttmlMs(BEGIN_ATTR.find(attrs)?.groupValues?.get(1)) ?: continue
        val end = max(ttmlMs(END_ATTR.find(attrs)?.groupValues?.get(1)) ?: start, start)
        val t = WS.replace(decodeEntities(lm.groupValues[2]), " ")
        val core = t.trim()
        if (core.isEmpty()) continue
        val gapEnd = if (i + 1 < leafs.size) leafs[i + 1].range.first else raw.length
        val gap = TAG.replace(raw.substring(lm.range.last + 1, gapEnd), "")
        val spaced = t.endsWith(" ") || gap.any { it.isWhitespace() }
        val bg = bgRanges.indexOfFirst { lm.range.first in it }
        list += TtmlWord(start, end, if (spaced) "$core " else core, bg)
    }
    if (list.isEmpty()) return null
    for (group in list.filter { it.bg >= 0 }.groupBy { it.bg }.values) {
        group.first().text = "(" + group.first().text
        val last = group.last()
        last.text = last.text.trimEnd() + ")" + (if (last.text.endsWith(" ")) " " else "")
    }
    list.last().text = list.last().text.trimEnd()
    return list.map { LyricWord(it.start, it.end, it.text) }
}

/**
 * Líneas de un TTML (el formato de BetterLyrics): un `<p begin>` por línea. Los `<span begin end>`
 * con texto se guardan como palabras; los coros de fondo (x-bg) van entre paréntesis.
 */
fun parseTtml(text: String): List<SyncedLine> {
    val lines = ArrayList<SyncedLine>()
    for (m in P_TAG.findAll(text)) {
        val start = ttmlMs(BEGIN_ATTR.find(m.groupValues[1])?.groupValues?.get(1)) ?: continue
        val raw = m.groupValues[2]
        val bgRanges = BG_SPAN.findAll(raw).map { it.range }.toList()
        val body = BG_SPAN.replace(raw) { " (" + TAG.replace(it.groupValues[1], "") + ") " }
        val line = decodeEntities(TAG.replace(BR_TAG.replace(body, " "), ""))
            .replace(OPEN_GAP, "(")
            .replace(CLOSE_GAP, ")")
            .replace(WS, " ")
            .trim()
        lines += SyncedLine(start, line, buildTtmlWords(raw, bgRanges))
    }
    return lines.sortedBy { it.startMs }
}

private fun two(n: Long): String = if (n < 10) "0$n" else "$n"

/** «[mm:ss.xx]texto» por línea. */
fun List<SyncedLine>.toLrc(): String = joinToString("\n") { line ->
    val cs = line.startMs / 10
    val min = cs / 6000
    val sec = (cs / 100) % 60
    "[${two(min)}:${two(sec)}.${two(cs % 100)}]${line.text}"
}

/** Línea que suena en [positionMs] (búsqueda binaria); -1 antes de la primera. */
fun activeLineIndex(lines: List<SyncedLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].startMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}
