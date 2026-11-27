/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import com.dezzy.app.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** Cuántos saltos lleva una canción o un artista y cuándo fue el último. */
class DjSkip(val count: Int, val lastAt: Long)

/**
 * Perfil de gustos del oyente, sacado de su historial real (no sólo de la biblioteca):
 *  - Cada escucha pesa menos cuanto más vieja es (vida media de [HALF_LIFE_DAYS] días).
 *  - Las de esta franja horaria (±2 h) pesan aparte: el DJ pone lo que sueles oír a esta hora.
 *  - Me gusta suma; los saltos restan a la canción y a su artista.
 *  - [explorationRate]: cuánto descubre el oyente normalmente, para dosificar lo nuevo.
 */
class TasteProfile private constructor(
    val songs: Map<String, Track>,
    /** Afinidad total por canción (≈ escuchas recientes ponderadas). */
    val songScore: Map<String, Double>,
    /** Peso de las escuchas de los últimos 14 días. */
    val recentScore: Map<String, Double>,
    /** Peso de las escuchas de hace más de 60 días (para «recuerdos» de verdad). */
    val oldScore: Map<String, Double>,
    /** Peso de las escuchas en esta franja horaria (±2 h). */
    val hourScore: Map<String, Double>,
    /** Última vez que sonó cada canción (epoch ms). */
    val lastPlayed: Map<String, Long>,
    val artistScore: Map<String, Double>,
    /** Veces que el oyente escuchó al artista (en el año), para contarlo en voz alta. */
    val artistPlays: Map<String, Int>,
    val liked: Set<String>,
    private val skips: Map<String, DjSkip>,
    /** «título|artista» de todo lo que ya conoce, para no venderle como nueva otra versión. */
    val knownSongKeys: Set<String>,
    /** Tipos de versión que sí escucha (si oye «slowed», no se le quitan). */
    private val variants: Set<String>,
    val explorationRate: Double,
    /** Instante (epoch ms) en que se calculó el perfil. */
    val builtAt: Long,
) {
    val isEmpty: Boolean get() = songScore.isEmpty()

    fun songSkips(id: String): Int = skips["s:$id"]?.count ?: 0

    /** Proporción de saltos del artista frente a sus escuchas (0 = nunca lo salta). */
    fun artistSkipRatio(keys: Set<String>): Double = keys.maxOfOrNull { key ->
        val s = skips["a:$key"]?.count ?: 0
        val p = artistPlays[key] ?: 0
        if (s + p == 0) 0.0 else s.toDouble() / (s + p)
    } ?: 0.0

    fun artistAffinity(keys: Set<String>): Double = keys.maxOfOrNull { artistScore[it] ?: 0.0 } ?: 0.0

    fun topArtistOf(keys: Set<String>): String? = keys.maxByOrNull { artistScore[it] ?: 0.0 }

    fun likesVariant(variant: String): Boolean = variant in variants

    fun knows(title: String?, artist: String?): Boolean = DjText.songKey(title, artist) in knownSongKeys

    /** Días desde la última vez que sonó [id] (999 si nunca). */
    fun daysSince(id: String): Double =
        lastPlayed[id]?.let { (builtAt - it) / DjClock.DAY_MS.toDouble() } ?: 999.0

    companion object {
        const val HALF_LIFE_DAYS = 30.0

        /** Una escucha del historial cuenta como una escucha completa. */
        private const val PLAY_WEIGHT = 1.0

        suspend fun build(store: DjStore, now: Long = DjClock.nowMs()): TasteProfile = withContext(Dispatchers.Default) {
            val from = now - 365 * DjClock.DAY_MS
            val events = djCatching { store.playEvents(from) }.getOrDefault(emptyList())
            val likedIds = djCatching { store.likedIds() }.getOrDefault(emptySet())
            val skipRows = djCatching { store.skips(from) }.getOrDefault(emptyList())

            val skips = HashMap<String, DjSkip>()
            for (row in skipRows) {
                val keys = listOf("s:${row.videoId}") + DjText.artistKeys(row.artist).map { "a:$it" }
                for (k in keys) {
                    val prev = skips[k]
                    skips[k] = DjSkip((prev?.count ?: 0) + 1, maxOf(prev?.lastAt ?: 0L, row.skippedAt))
                }
            }

            val songScore = HashMap<String, Double>()
            val recent = HashMap<String, Double>()
            val old = HashMap<String, Double>()
            val hour = HashMap<String, Double>()
            val last = HashMap<String, Long>()
            val first = HashMap<String, Long>()
            val nowHour = DjClock.hourOf(now)
            for (e in events) {
                val ageDays = (now - e.playedAt) / DjClock.DAY_MS.toDouble()
                val w = PLAY_WEIGHT * 0.5.pow(ageDays / HALF_LIFE_DAYS)
                songScore.merge(e.videoId, w, Double::plus)
                if (ageDays <= 14) recent.merge(e.videoId, PLAY_WEIGHT, Double::plus)
                if (ageDays > 60) old.merge(e.videoId, PLAY_WEIGHT, Double::plus)
                val dh = abs(DjClock.hourOf(e.playedAt) - nowHour).let { min(it, 24 - it) }
                if (dh <= 2) hour.merge(e.videoId, PLAY_WEIGHT * 0.5.pow(ageDays / 90.0), Double::plus)
                if ((last[e.videoId] ?: Long.MIN_VALUE) < e.playedAt) last[e.videoId] = e.playedAt
                if ((first[e.videoId] ?: Long.MAX_VALUE) > e.playedAt) first[e.videoId] = e.playedAt
            }
            likedIds.forEach { songScore.merge(it, 2.5, Double::plus) }

            val songs = djCatching { store.tracks(songScore.keys + likedIds) }.getOrDefault(emptyMap())

            val artistScore = HashMap<String, Double>()
            val artistPlays = HashMap<String, Int>()
            val playsPerSong = events.groupingBy { it.videoId }.eachCount()
            val known = HashSet<String>()
            val variants = HashMap<String, Int>()
            songs.forEach { (id, song) ->
                known += DjText.songKey(song.title, song.artists)
                DjText.variantOf(song.title)?.let { v -> variants.merge(v, playsPerSong[id] ?: 1, Int::plus) }
                val score = songScore[id] ?: 0.0
                DjText.artistKeys(song.artists).forEach { key ->
                    artistScore.merge(key, score, Double::plus)
                    artistPlays.merge(key, playsPerSong[id] ?: 0, Int::plus)
                }
            }

            // Cuánto descubre: escuchas del último mes a canciones que conoció ese mismo mes.
            val monthAgo = now - 30 * DjClock.DAY_MS
            val monthEvents = events.filter { it.playedAt > monthAgo }
            val fresh = monthEvents.count { (first[it.videoId] ?: 0L) > monthAgo }
            val exploration = if (monthEvents.size < 20) 0.35 else (fresh.toDouble() / monthEvents.size).coerceIn(0.2, 0.55)

            TasteProfile(
                songs = songs,
                songScore = songScore,
                recentScore = recent,
                oldScore = old,
                hourScore = hour,
                lastPlayed = last,
                artistScore = artistScore,
                artistPlays = artistPlays,
                liked = likedIds,
                skips = skips,
                knownSongKeys = known,
                // Un tipo de versión «cuenta» como gusto si suma 5 escuchas o más.
                variants = variants.filterValues { it >= 5 }.keys,
                explorationRate = exploration,
                builtAt = now,
            )
        }

        /** log suave para que un artista escuchadísimo no aplaste todo lo demás. */
        internal fun soft(x: Double): Double = ln(1.0 + x.coerceAtLeast(0.0))
    }
}
