/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Instant

/** Reloj del DJ: milisegundos reales (epoch) y hora local sin APIs de Java. */
internal object DjClock {
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 24 * HOUR_MS

    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Desplazamiento de la zona local respecto a UTC en el instante [atMs]. */
    fun offsetMs(atMs: Long = nowMs()): Long =
        TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochMilliseconds(atMs)).totalSeconds * 1000L

    /** Hora local (0–23) del instante [atMs]. */
    fun hourOf(atMs: Long): Int {
        val local = atMs + offsetMs(atMs)
        return (((local / HOUR_MS) % 24 + 24) % 24).toInt()
    }
}

/** Normaliza textos para comparar artistas y títulos sin tildes, mayúsculas ni espacios de más. */
internal object DjText {
    private val ARTIST_SPLIT =
        Regex("""\s*(?:,|&|·|/|\bfeat\.?|\bft\.?|\bfeaturing\b|\bx\b|\by\b|\bcon\b)\s*""", RegexOption.IGNORE_CASE)

    /** «(Official Video)», «[Lyrics]», «- Remastered 2011»… para reconocer la misma canción en otro vídeo. */
    private val TITLE_NOISE = Regex("""\s*[(\[][^)\]]*[)\]]|\s+-\s+.*$""")

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    private const val ACCENTED = "áàâäãåā" + "éèêëē" + "íìîïī" + "óòôöõō" + "úùûüū" + "ýÿ" + "ñ" + "ç"
    private const val PLAIN = "aaaaaaa" + "eeeee" + "iiiii" + "oooooo" + "uuuuu" + "yy" + "n" + "c"

    /** Quita tildes a mano (no hay Normalizer en código común). */
    private fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val i = ACCENTED.indexOf(c)
            sb.append(if (i >= 0) PLAIN[i] else c)
        }
        return sb.toString()
    }

    fun normalize(text: String): String = fold(text.lowercase().trim())

    fun artistKeys(artist: String?): Set<String> =
        artist.orEmpty().split(ARTIST_SPLIT).map(::normalize).filter { it.length > 1 }.toSet()

    fun titleKey(title: String?): String =
        normalize(TITLE_NOISE.replace(title.orEmpty(), "")).replace(NON_WORD, " ").trim()

    /** «la misma canción» aunque sea otro videoId: título limpio + artista principal. */
    fun songKey(title: String?, artist: String?): String =
        titleKey(title) + "|" + (artistKeys(artist).firstOrNull() ?: "")

    /** Palabras sueltas con contenido (sin tildes). */
    fun words(text: String): List<String> = normalize(text).split(NON_WORD).filter { it.isNotEmpty() }

    /**
     * Versiones que casi nunca son lo que el oyente quiere en una sesión: karaoke, instrumentales,
     * en 8D, slowed/sped up, nightcore, «1 hora», reacciones, tutoriales, tonos…
     * Sólo se descartan si el oyente no suele escuchar ese tipo de versión.
     */
    private val JUNK = Regex(
        """\b(karaoke|instrumental|8d|slowed|sped ?up|nightcore|1 ?hora|1 ?hour|10 ?hours?|""" +
            """reaction|reaccion|tutorial|cover|ringtone|tono de llamada|bass ?boosted|tiktok version)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun variantOf(title: String?): String? = JUNK.find(normalize(title.orEmpty()))?.value?.lowercase()
}
