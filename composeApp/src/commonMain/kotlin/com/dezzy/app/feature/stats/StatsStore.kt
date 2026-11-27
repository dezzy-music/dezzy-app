/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.stats

import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Lee el historial (`play_event`, `listen_event`, `track`, `favorite`) y arma las cifras de
 * Estadísticas y de «Tu año en música». Todas las marcas son epoch ms reales; los días y las
 * horas se agrupan con el desplazamiento local que pasa quien llama (ver [StatsDates.offsetMs]).
 */
class StatsStore(
    private val db: DezzyDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val q get() = db.statsQueries

    private class Range(val from: Long, val to: Long)

    // Resumen ---------------------------------------------------------------

    suspend fun overview(offsetMs: Long): StatsOverview = withContext(Dispatchers.Default) {
        val nowMs = now()
        val today = StatsDates.localDay(nowMs, offsetMs)
        val days = q.statsListenDays(offsetMs).executeAsList().mapNotNull { it }
        val (current, longest) = streaks(days, today)
        fun listened(range: Range) = db.dezzyQueries.listenSecondsBetween(range.from, range.to).executeAsOne() * 1000L
        StatsOverview(
            streakDays = current,
            longestStreakDays = longest,
            todayMs = listened(Range(StatsDates.dayStartMs(today, offsetMs), StatsDates.dayStartMs(today + 1, offsetMs))),
            weekMs = listened(rangeOf(StatsPeriod.WEEK, today, offsetMs, nowMs)),
            monthMs = listened(rangeOf(StatsPeriod.MONTH, today, offsetMs, nowMs)),
            yearMs = listened(rangeOf(StatsPeriod.YEAR, today, offsetMs, nowMs)),
        )
    }

    /** (racha actual, racha más larga). [daysDesc] viene del más reciente al más viejo. */
    private fun streaks(daysDesc: List<Long>, today: Long): Pair<Int, Int> {
        if (daysDesc.isEmpty()) return 0 to 0
        val set = daysDesc.toSet()
        var current = 0
        var cursor = if (today in set) today else today - 1
        while (cursor in set) {
            current++
            cursor--
        }
        return current to longestRun(daysDesc)
    }

    private fun longestRun(days: List<Long>): Int {
        var best = 0
        var run = 0
        var previous: Long? = null
        for (day in days.sorted()) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            if (run > best) best = run
            previous = day
        }
        return best
    }

    // Por periodo -----------------------------------------------------------

    suspend fun period(period: StatsPeriod, offsetMs: Long): PeriodStats = withContext(Dispatchers.Default) {
        val nowMs = now()
        val today = StatsDates.localDay(nowMs, offsetMs)
        val range = rangeOf(period, today, offsetMs, nowMs)
        val dayMap = daySeconds(range, offsetMs)
        val buckets = buckets(period, today, dayMap)
        val rows = trackRows(range)
        val hours = MutableList(24) { 0L }
        q.statsListenByHour(offsetMs, range.from, range.to) { hour, seconds -> (hour ?: 0L).toInt() to (seconds ?: 0L) }
            .executeAsList()
            .forEach { (h, s) -> if (h in 0..23) hours[h] = s }
        PeriodStats(
            period = period,
            listening = StatsListening(totalMs = dayMap.values.sum() * 1000L, buckets = buckets),
            totalPlays = rows.sumOf { it.plays },
            distinctTracks = rows.size,
            topTracks = rows.take(25),
            topArtists = rankArtists(rows).take(15),
            topAlbums = rankAlbums(rows).take(15),
            hourSeconds = hours,
        )
    }

    private fun rangeOf(period: StatsPeriod, today: Long, offsetMs: Long, nowMs: Long): Range {
        val end = StatsDates.dayStartMs(today + 1, offsetMs)
        return when (period) {
            StatsPeriod.WEEK -> Range(StatsDates.dayStartMs(today - 6, offsetMs), end)
            StatsPeriod.MONTH -> Range(StatsDates.dayStartMs(today - 29, offsetMs), end)
            StatsPeriod.YEAR -> {
                val (y, m, _) = StatsDates.civil(today)
                val index = y * 12 + (m - 1) - 11
                val firstDay = StatsDates.daysFromCivil(index.floorDiv(12), index.mod(12) + 1, 1)
                Range(StatsDates.dayStartMs(firstDay, offsetMs), end)
            }
            StatsPeriod.ALL -> Range(0L, end)
        }
    }

    private fun daySeconds(range: Range, offsetMs: Long): Map<Long, Long> =
        q.statsListenByDay(offsetMs, range.from, range.to) { day, seconds -> (day ?: 0L) to (seconds ?: 0L) }
            .executeAsList()
            .toMap()

    private fun buckets(period: StatsPeriod, today: Long, dayMap: Map<Long, Long>): List<StatsBucket> = when (period) {
        StatsPeriod.WEEK -> (today - 6..today).map { day ->
            StatsBucket(WEEKDAYS[StatsDates.dayOfWeek(day)], (dayMap[day] ?: 0L) * 1000L)
        }
        StatsPeriod.MONTH -> (0 until 5).map { block ->
            val start = today - 29 + block * 6L
            val total = (start until start + 6).sumOf { dayMap[it] ?: 0L }
            val (_, m, d) = StatsDates.civil(start)
            StatsBucket("$d ${StatsDates.MONTHS_SHORT[m - 1]}", total * 1000L)
        }
        StatsPeriod.YEAR -> {
            val (y, m, _) = StatsDates.civil(today)
            val last = y * 12 + (m - 1)
            val perMonth = HashMap<Int, Long>()
            for ((day, s) in dayMap) {
                val (dy, dm, _) = StatsDates.civil(day)
                perMonth[dy * 12 + (dm - 1)] = (perMonth[dy * 12 + (dm - 1)] ?: 0L) + s
            }
            (last - 11..last).map { key ->
                StatsBucket(MONTH_INITIALS[key.mod(12)], (perMonth[key] ?: 0L) * 1000L)
            }
        }
        StatsPeriod.ALL -> {
            val currentYear = StatsDates.civil(today).first
            val perYear = HashMap<Int, Long>()
            for ((day, s) in dayMap) {
                val year = StatsDates.civil(day).first
                perYear[year] = (perYear[year] ?: 0L) + s
            }
            val first = perYear.keys.minOrNull() ?: currentYear
            (first..currentYear).map { StatsBucket("$it", (perYear[it] ?: 0L) * 1000L) }
        }
    }

    // Tops --------------------------------------------------------------------

    private fun trackRows(range: Range): List<RankedTrack> =
        q.statsTrackPlays(range.from, range.to) { videoId, title, artists, album, thumbnail, duration, plays, _ ->
            RankedTrack(Track(videoId, title, artists, album, thumbnail, duration), plays.toInt())
        }.executeAsList()

    private fun splitArtists(raw: String): List<String> =
        raw.split(ARTIST_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    private fun rankArtists(rows: List<RankedTrack>): List<RankedName> {
        val plays = LinkedHashMap<String, Int>()
        val names = HashMap<String, String>()
        val covers = HashMap<String, String?>()
        for (row in rows) {
            for (artist in splitArtists(row.track.artists)) {
                val key = artist.lowercase()
                plays[key] = (plays[key] ?: 0) + row.plays
                names.getOrPut(key) { artist }
                // Las filas vienen de más a menos escuchada: se queda la carátula de la mejor.
                if (covers[key] == null) covers[key] = row.track.thumbnail
            }
        }
        return plays.entries.sortedByDescending { it.value }
            .map { RankedName(names.getValue(it.key), null, covers[it.key], it.value) }
    }

    private fun rankAlbums(rows: List<RankedTrack>): List<RankedName> {
        val plays = LinkedHashMap<String, Int>()
        val names = HashMap<String, RankedName>()
        for (row in rows) {
            val album = row.track.album?.trim().orEmpty()
            if (album.isEmpty()) continue
            val artist = splitArtists(row.track.artists).firstOrNull()
            val key = album.lowercase() + "|" + (artist ?: "").lowercase()
            plays[key] = (plays[key] ?: 0) + row.plays
            names.getOrPut(key) { RankedName(album, artist, row.track.thumbnail, 0) }
        }
        return plays.entries.sortedByDescending { it.value }
            .map { names.getValue(it.key).copy(plays = it.value) }
    }

    // Tu año en música ----------------------------------------------------------

    /** Años con algo guardado, del más reciente al más viejo. */
    suspend fun availableYears(): List<Int> = withContext(Dispatchers.Default) {
        val first = listOfNotNull(q.statsFirstListen().executeAsOne().min, q.statsFirstPlay().executeAsOne().min)
            .minOrNull() ?: return@withContext emptyList()
        val offset = StatsDates.offsetMs(now())
        val firstYear = StatsDates.civil(StatsDates.localDay(first, offset)).first
        val current = StatsDates.currentYear(now())
        (current downTo firstYear).toList()
    }

    suspend fun yearInMusic(year: Int, offsetMs: Long): YearInMusic? = withContext(Dispatchers.Default) {
        val range = Range(
            StatsDates.dayStartMs(StatsDates.daysFromCivil(year, 1, 1), offsetMs),
            StatsDates.dayStartMs(StatsDates.daysFromCivil(year + 1, 1, 1), offsetMs),
        )
        val dayMap = daySeconds(range, offsetMs)
        val rows = trackRows(range)
        val totalSeconds = dayMap.values.sum()
        if (totalSeconds == 0L && rows.isEmpty()) return@withContext null

        val monthMs = MutableList(12) { 0L }
        for ((day, s) in dayMap) {
            val (_, m, _) = StatsDates.civil(day)
            monthMs[m - 1] += s * 1000L
        }
        val busiestMonth = monthMs.indices.maxByOrNull { monthMs[it] }?.takeIf { monthMs[it] > 0 }
        val busiestDay = dayMap.maxByOrNull { it.value }
        val busiestLabel = busiestDay?.let {
            val (_, m, d) = StatsDates.civil(it.key)
            "$d de ${StatsDates.MONTHS_LONG[m - 1]}"
        }

        val hours = MutableList(24) { 0L }
        q.statsListenByHour(offsetMs, range.from, range.to) { hour, seconds -> (hour ?: 0L).toInt() to (seconds ?: 0L) }
            .executeAsList()
            .forEach { (h, s) -> if (h in 0..23) hours[h] = s }
        val peakHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }

        val artists = rankArtists(rows)
        YearInMusic(
            year = year,
            totalMs = totalSeconds * 1000L,
            totalPlays = rows.sumOf { it.plays },
            distinctTracks = rows.size,
            distinctArtists = artists.size,
            topTracks = rows.take(5),
            topArtists = artists.take(5),
            topAlbum = rankAlbums(rows).firstOrNull(),
            monthMs = monthMs,
            busiestMonth = busiestMonth,
            busiestDayLabel = busiestLabel,
            busiestDayMs = (busiestDay?.value ?: 0L) * 1000L,
            longestStreakDays = longestRun(dayMap.keys.toList()),
            favoritesAdded = q.statsFavoritesAdded(range.from, range.to).executeAsOne().toInt(),
            peakHour = peakHour,
            personality = personality(peakHour),
        )
    }

    private fun personality(hour: Int?): String = when (hour) {
        null -> "Oyente misterioso"
        in 5..8 -> "Madrugador"
        in 9..12 -> "De mañanas"
        in 13..17 -> "De tardes"
        in 18..22 -> "De noches"
        else -> "Noctámbulo"
    }

    private companion object {
        val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")
        val MONTH_INITIALS = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val ARTIST_SEPARATORS = Regex(",| & | feat\\. | • ", RegexOption.IGNORE_CASE)
    }
}
