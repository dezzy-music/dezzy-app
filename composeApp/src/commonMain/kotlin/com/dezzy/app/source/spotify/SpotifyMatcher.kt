/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeEndpoints
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/** Busca en el catálogo de música la canción equivalente a una de la biblioteca. */
class SpotifyMatcher(
    private val yt: YouTubeMusicClient,
    private val repo: MusicRepository,
) {
    // Caché de la sesión: id de la biblioteca -> pista elegida (null = sin equivalente).
    private val cache = HashMap<String, Track?>()

    /** true si la última llamada a [match] hizo una búsqueda en red (para espaciarlas). */
    internal var lastCallSearched: Boolean = false
        private set

    suspend fun match(t: SpotifyTrack): Track? {
        lastCallSearched = false
        if (cache.containsKey(t.id)) return cache[t.id]
        repo.findTrackBySpotifyId(t.id)?.let {
            cache[t.id] = it
            return it
        }

        lastCallSearched = true
        val query = "${t.title} ${t.artists.firstOrNull().orEmpty()}".trim()
        val result = try {
            yt.search(query, YouTubeEndpoints.FILTER_SONGS).getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // Fallo de red: no se guarda en la caché para poder reintentarlo.
        val found = result ?: return null

        val best = found.songs
            .filter { it.videoId != null }
            .take(CANDIDATES)
            .map { it to score(t, it) }
            .filter { it.second >= THRESHOLD }
            .maxByOrNull { it.second } // en empate gana el primero, el mejor puesto del catálogo
            ?.first
        val chosen = best?.copy(spotifyId = t.id)
        if (chosen != null) repo.saveTrack(chosen)
        cache[t.id] = chosen
        return chosen
    }

    private fun score(t: SpotifyTrack, c: Track): Int {
        val wanted = normalizeTitle(t.title)
        val got = normalizeTitle(c.title)
        val title = when {
            wanted.isEmpty() || got.isEmpty() -> 0
            wanted == got -> 4
            wanted.length >= 3 && got.length >= 3 && (got.contains(wanted) || wanted.contains(got)) -> 2
            else -> 0
        }
        if (title == 0) return 0 // con otro título no puede ser la misma canción

        val candidateArtists = normalizeTitle(c.artists)
        val artist = if (t.artists.any { a ->
                val n = normalizeTitle(a)
                n.isNotEmpty() && candidateArtists.isNotEmpty() &&
                    (candidateArtists.contains(n) || n.contains(candidateArtists))
            }
        ) 3 else 0

        val duration = run {
            val wantedS = t.durationMs?.div(1000) ?: return@run 0
            val gotS = c.durationSeconds ?: return@run 0
            val diff = abs(wantedS - gotS)
            when {
                diff <= 5 -> 3
                diff <= 15 -> 1
                else -> 0
            }
        }
        return title + artist + duration
    }

    private companion object {
        const val CANDIDATES = 5
        const val THRESHOLD = 6
    }
}

private val bracketed = Regex("""\([^)]*\)|\[[^\]]*\]""")
private val featuring = Regex("""\s+(feat\.?|ft\.?|featuring)\s.*$""")
private val dashSuffix = Regex("""\s+-\s+.*(remaster|mono|stereo|deluxe|version|edit|live).*$""")

/** Minúsculas, sin acentos, sin «(feat…)», « - Remastered…», paréntesis ni corchetes. */
internal fun normalizeTitle(raw: String): String {
    var s = raw.lowercase()
    s = bracketed.replace(s, " ")
    s = featuring.replace(s, "")
    s = dashSuffix.replace(s, "")
    val sb = StringBuilder(s.length)
    for (ch in s) {
        val folded = foldAccent(ch)
        sb.append(if (folded.isLetterOrDigit()) folded else ' ')
    }
    return sb.toString().trim().replace(Regex("\\s+"), " ")
}

private fun foldAccent(c: Char): Char = when (c) {
    'á', 'à', 'ä', 'â', 'ã', 'å', 'ā' -> 'a'
    'é', 'è', 'ë', 'ê', 'ē' -> 'e'
    'í', 'ì', 'ï', 'î', 'ī' -> 'i'
    'ó', 'ò', 'ö', 'ô', 'õ', 'ø', 'ō' -> 'o'
    'ú', 'ù', 'ü', 'û', 'ū' -> 'u'
    'ñ' -> 'n'
    'ç' -> 'c'
    'ý', 'ÿ' -> 'y'
    else -> c
}
