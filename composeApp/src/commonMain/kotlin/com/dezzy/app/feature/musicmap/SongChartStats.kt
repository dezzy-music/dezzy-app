/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import com.dezzy.app.source.spotify.SpotifyFeatures
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.time.Clock

data class ChartWeekPoint(val date: String, val position: Int, val streams: Long)

data class ChartCountryStats(
    val code: String,
    val totalStreams: Long,
    val peak: Int,
    val peakStreams: Long,
    /** Semanas en la lista, de la más antigua a la más reciente. */
    val weeks: List<ChartWeekPoint>,
) {
    val name: String get() = CountryNames.name(code)
}

/** Puestos del último día publicado en la lista diaria de Spotify. */
data class ChartToday(val date: String, val positions: List<Pair<String, Int>>)

data class SongStats(
    val trackId: String,
    /** «Artista - Canción» tal como lo nombra kworb. */
    val label: String?,
    val countries: List<ChartCountryStats>,
    val today: ChartToday?,
) {
    val global: ChartCountryStats? get() = countries.firstOrNull { it.code == CountryNames.GLOBAL }

    /** Países (sin el global) ordenados por streams. */
    val byCountry: List<ChartCountryStats> get() = countries.filter { it.code != CountryNames.GLOBAL }.sortedByDescending { it.totalStreams }
    val bestPeak: Int? get() = countries.minOfOrNull { it.peak }
    val totalStreams: Long get() = global?.totalStreams ?: byCountry.sumOf { it.totalStreams }
}

sealed interface SongStatsResult {
    data class Ok(val stats: SongStats) : SongStatsResult

    /** La canción existe en Spotify pero nunca entró en ninguna lista por país. */
    data class NeverCharted(val trackId: String) : SongStatsResult

    /** No hay sesión de Spotify (o ya no vale): hay que iniciarla para buscar la canción. */
    data object NoSpotifySession : SongStatsResult
    data object NotFoundOnSpotify : SongStatsResult
    data object NetworkError : SongStatsResult
}

/**
 * Estadísticas de ranking de CUALQUIER canción (no sólo las del top de hoy).
 *
 *  1. Spotify sólo sirve para encontrar el id de la canción (hace falta sesión de Spotify).
 *  2. kworb.net/spotify/track/<id>.html da, para cada país donde la canción entró en la lista de
 *     Spotify, los streams totales, el mejor puesto y el historial semana a semana. Un 404 de
 *     kworb significa que la canción nunca entró en ninguna lista: no se inventa nada.
 *
 * Caché local: id de Spotify 30 días, estadísticas 12 h (máximo 40 canciones guardadas).
 */
class SongChartStats(
    private val store: MusicMapStore,
    private val http: HttpClient,
    private val spotify: SpotifyFeatures,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** ¿Hay sesión de Spotify guardada? Barato: no hace peticiones. */
    fun hasSpotifySession(): Boolean = spotify.isActive()

    /** Estadísticas de una canción por título y artista (busca su id en Spotify). */
    suspend fun forSong(title: String, artist: String): SongStatsResult = withContext(Dispatchers.Default) {
        val key = "stats_id_" + normalize("$title|$artist")
        val cachedId = store.get(key, ID_TTL_MS)
        val trackId = cachedId ?: run {
            if (!spotify.isActive()) return@withContext SongStatsResult.NoSpotifySession
            val found = findTrackId(title, artist) ?: return@withContext SongStatsResult.NotFoundOnSpotify
            store.put(key, found)
            found
        }
        forTrackId(trackId)
    }

    /** Estadísticas de un id de Spotify ya conocido. */
    suspend fun forTrackId(trackId: String): SongStatsResult = withContext(Dispatchers.Default) {
        readCached(trackId)?.let { return@withContext it }
        val result: SongStatsResult = try {
            val response = http.get("https://kworb.net/spotify/track/$trackId.html") {
                header("User-Agent", "Mozilla/5.0 (Linux; Android) DezzyMusic")
            }
            val code = response.status.value
            when {
                code == 404 -> SongStatsResult.NeverCharted(trackId)
                code !in 200..299 -> SongStatsResult.NetworkError
                else -> {
                    val stats = parse(response.bodyAsText(), trackId)
                    if (stats.countries.isEmpty()) SongStatsResult.NeverCharted(trackId) else SongStatsResult.Ok(stats)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            SongStatsResult.NetworkError
        }
        if (result is SongStatsResult.Ok || result is SongStatsResult.NeverCharted) writeCached(trackId, result)
        result
    }

    // ── Búsqueda del id en Spotify ─────────────────────────────────────────────

    private suspend fun findTrackId(title: String, artist: String): String? {
        val wantTitle = normalize(cleanTitle(title))
        val wantArtists = artist.split(",", "&", " y ", " x ", " feat.", " ft.").map { normalize(it) }.filter { it.isNotBlank() }
        val candidates = spotify.searchTracks("$title $artist", limit = 8)
        val best = candidates.maxByOrNull { track ->
            val t = normalize(cleanTitle(track.title))
            val a = track.artists.map { normalize(it) }
            var score = 0
            if (t == wantTitle) score += 4 else if (t.contains(wantTitle) || wantTitle.contains(t)) score += 2
            if (a.any { it in wantArtists }) score += 3
            else if (a.any { name -> wantArtists.any { name.contains(it) || it.contains(name) } }) score += 1
            score
        }
        return best?.id
    }

    private fun cleanTitle(title: String): String = title
        .replace(Regex("\\s*[(\\[][^)\\]]*(feat|ft\\.|with|remaster|version|versión|live|en vivo)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+-\\s+.*(remaster|version|live).*$", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun normalize(s: String): String = CountryNames.fold(s)

    // ── Lectura de la página de kworb ──────────────────────────────────────────

    private class Table(val codes: List<String>, val rows: List<List<String>>)

    private fun table(html: String, cls: String): Table? {
        val start = html.indexOf("<div class=\"$cls\">").takeIf { it >= 0 } ?: return null
        val end = html.indexOf("</table>", start).takeIf { it >= 0 } ?: return null
        val section = html.substring(start, end)
        val codes = HEAD.findAll(section).map { it.groupValues[1].trim() }.drop(1)
            .map { if (it.equals("Global", ignoreCase = true)) CountryNames.GLOBAL else it.uppercase() }
            .toList()
        val rows = ROW.findAll(section).map { row -> CELL.findAll(row.groupValues[1]).map { it.groupValues[1] }.toList() }
            .filter { it.isNotEmpty() }
            .toList()
        return Table(codes, rows)
    }

    private fun number(s: String): Long = s.filter { it.isDigit() }.toLongOrNull() ?: 0L

    internal fun parse(html: String, trackId: String): SongStats {
        val label = TITLE.find(html)?.groupValues?.get(1)
            ?.substringBefore(" - Spotify Chart History")
            ?.replace("&amp;", "&")?.replace("&#39;", "'")?.replace("&quot;", "\"")
            ?.trim()
        val weekly = table(html, "weekly")
        val countries = mutableListOf<ChartCountryStats>()
        if (weekly != null) {
            val totals = weekly.rows.firstOrNull { it.firstOrNull()?.trim() == "Total" }
            val peaks = weekly.rows.firstOrNull { it.firstOrNull()?.trim() == "Peak" }
            val weekRows = weekly.rows.filter { DATE.matches(it.firstOrNull()?.trim().orEmpty()) }
            weekly.codes.forEachIndexed { i, code ->
                val col = i + 1
                val weeks = weekRows.mapNotNull { row ->
                    val cell = row.getOrNull(col) ?: return@mapNotNull null
                    val pos = POS.find(cell)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                    ChartWeekPoint(row[0].trim(), pos, STREAMS.find(cell)?.groupValues?.get(1)?.let(::number) ?: 0L)
                }.sortedBy { it.date }
                val peakCell = peaks?.getOrNull(col).orEmpty()
                val peak = POS.find(peakCell)?.groupValues?.get(1)?.toIntOrNull() ?: weeks.minOfOrNull { it.position }
                    ?: return@forEachIndexed
                val peakStreams = STREAMS.find(peakCell)?.groupValues?.get(1)?.let(::number) ?: 0L
                val total = totals?.getOrNull(col)?.let(::number)?.takeIf { it > 0 } ?: weeks.sumOf { it.streams }
                countries += ChartCountryStats(code, total, peak, peakStreams, weeks)
            }
        }
        return SongStats(trackId, label, countries, today(html))
    }

    private fun today(html: String): ChartToday? {
        val daily = table(html, "daily") ?: return null
        val last = daily.rows.lastOrNull { DATE.matches(it.firstOrNull()?.trim().orEmpty()) } ?: return null
        val date = last[0].trim()
        val dayOfDate = epochDay(date) ?: return null
        val age = Clock.System.now().toEpochMilliseconds() / DAY_MS - dayOfDate
        if (age > TODAY_MAX_AGE_DAYS) return null
        val positions = daily.codes.mapIndexedNotNull { i, code ->
            POS.find(last.getOrNull(i + 1).orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.let { code to it }
        }.sortedBy { it.second }
        return if (positions.isEmpty()) null else ChartToday(date, positions)
    }

    /** Días desde 1970-01-01 de una fecha «yyyy/MM/dd» (algoritmo civil de Howard Hinnant). */
    private fun epochDay(date: String): Long? {
        val p = date.split('/')
        if (p.size != 3) return null
        val y0 = p[0].toLongOrNull() ?: return null
        val m = p[1].toLongOrNull() ?: return null
        val d = p[2].toLongOrNull() ?: return null
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    // ── Caché ──────────────────────────────────────────────────────────────────

    private suspend fun readCached(trackId: String): SongStatsResult? {
        val raw = store.get("stats_$trackId", STATS_TTL_MS) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            if (o["never"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching SongStatsResult.NeverCharted(trackId)
            val countries = o.getValue("c").jsonArray.map { el ->
                val c = el.jsonObject
                ChartCountryStats(
                    code = c.getValue("k").jsonPrimitive.content,
                    totalStreams = c.getValue("t").jsonPrimitive.long,
                    peak = c.getValue("p").jsonPrimitive.int,
                    peakStreams = c["ps"]?.jsonPrimitive?.long ?: 0L,
                    weeks = c.getValue("w").jsonArray.map { w ->
                        val a = w.jsonArray
                        ChartWeekPoint(a[0].jsonPrimitive.content, a[1].jsonPrimitive.int, a[2].jsonPrimitive.long)
                    },
                )
            }
            val today = (o["d"] as? JsonObject)?.let { d ->
                ChartToday(
                    d.getValue("date").jsonPrimitive.content,
                    d.getValue("p").jsonArray.map { p -> p.jsonArray[0].jsonPrimitive.content to p.jsonArray[1].jsonPrimitive.int },
                )
            }
            SongStatsResult.Ok(SongStats(trackId, o["l"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }, countries, today))
        }.getOrNull()
    }

    private suspend fun writeCached(trackId: String, result: SongStatsResult) {
        val o: JsonObject = when (result) {
            is SongStatsResult.NeverCharted -> buildJsonObject { put("never", true) }
            is SongStatsResult.Ok -> {
                val s = result.stats
                buildJsonObject {
                    put("l", s.label.orEmpty())
                    put("c", buildJsonArray {
                        for (c in s.countries) {
                            add(buildJsonObject {
                                put("k", c.code)
                                put("t", c.totalStreams)
                                put("p", c.peak)
                                put("ps", c.peakStreams)
                                put("w", buildJsonArray {
                                    for (w in c.weeks) add(buildJsonArray {
                                        add(JsonPrimitive(w.date)); add(JsonPrimitive(w.position)); add(JsonPrimitive(w.streams))
                                    })
                                })
                            })
                        }
                    })
                    s.today?.let { t ->
                        put("d", buildJsonObject {
                            put("date", t.date)
                            put("p", buildJsonArray {
                                for (p in t.positions) add(buildJsonArray { add(JsonPrimitive(p.first)); add(JsonPrimitive(p.second)) })
                            })
                        })
                    }
                }
            }
            else -> return
        }
        // Lista de ids guardados, de más antiguo a más nuevo, para no crecer sin límite.
        val order = store.get("stats_order").orEmpty().split(",").filter { it.isNotBlank() && it != trackId } + trackId
        order.dropLast(MAX_CACHED_STATS).forEach { old -> store.delete("stats_$old") }
        store.put("stats_order", order.takeLast(MAX_CACHED_STATS).joinToString(","))
        store.put("stats_$trackId", o.toString())
    }

    companion object {
        private const val STATS_TTL_MS = 12L * 60 * 60 * 1000
        private const val ID_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_CACHED_STATS = 40
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** La última fila diaria cuenta como «ahora mismo» si no tiene más de estos días. */
        private const val TODAY_MAX_AGE_DAYS = 4L

        private val ROW = Regex("<tr[^>]*>([\\s\\S]*?)</tr>")
        private val CELL = Regex("<td[^>]*>([\\s\\S]*?)</td>")
        private val HEAD = Regex("<th[^>]*>([\\s\\S]*?)</th>")
        private val POS = Regex("class=\"p\">(\\d+)<")
        private val STREAMS = Regex("class=\"s\">([\\d,]+)<")
        private val DATE = Regex("^\\d{4}/\\d{2}/\\d{2}$")
        private val TITLE = Regex("<title>([\\s\\S]*?)</title>")
    }
}
