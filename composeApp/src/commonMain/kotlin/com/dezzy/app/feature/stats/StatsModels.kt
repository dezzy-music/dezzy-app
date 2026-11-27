/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.stats

import androidx.compose.runtime.Immutable
import com.dezzy.app.core.model.Track

/** Una barra del gráfico de Estadísticas: etiqueta corta (día, semana o mes) y tiempo escuchado. */
@Immutable
data class StatsBucket(
    val label: String,
    val playTimeMs: Long,
)

/** Tiempo total del periodo y su reparto en barras. */
@Immutable
data class StatsListening(
    val totalMs: Long = 0L,
    val buckets: List<StatsBucket> = emptyList(),
)

/** Periodos que se pueden elegir en Estadísticas. */
enum class StatsPeriod(val label: String, val phrase: String) {
    WEEK("Semana", "últimos 7 días"),
    MONTH("Mes", "últimos 30 días"),
    YEAR("Año", "últimos 12 meses"),
    ALL("Todo", "desde siempre"),
}

@Immutable
data class RankedTrack(val track: Track, val plays: Int)

/** Artista o álbum del ranking; [subtitle] es el artista del álbum. */
@Immutable
data class RankedName(val name: String, val subtitle: String?, val thumbnail: String?, val plays: Int)

/** Cifras sueltas de la parte de arriba. */
@Immutable
data class StatsOverview(
    val streakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val todayMs: Long = 0L,
    val weekMs: Long = 0L,
    val monthMs: Long = 0L,
    val yearMs: Long = 0L,
)

@Immutable
data class PeriodStats(
    val period: StatsPeriod,
    val listening: StatsListening = StatsListening(),
    val totalPlays: Int = 0,
    val distinctTracks: Int = 0,
    val topTracks: List<RankedTrack> = emptyList(),
    val topArtists: List<RankedName> = emptyList(),
    val topAlbums: List<RankedName> = emptyList(),
    /** Segundos escuchados en cada hora local (24 valores). */
    val hourSeconds: List<Long> = List(24) { 0L },
)

/** Resumen anual para «Tu año en música». */
@Immutable
data class YearInMusic(
    val year: Int,
    val totalMs: Long,
    val totalPlays: Int,
    val distinctTracks: Int,
    val distinctArtists: Int,
    val topTracks: List<RankedTrack>,
    val topArtists: List<RankedName>,
    val topAlbum: RankedName?,
    /** Tiempo escuchado en cada mes (12 valores, enero primero). */
    val monthMs: List<Long>,
    val busiestMonth: Int?,
    /** Día más escuchado como «d de mes» y su tiempo. */
    val busiestDayLabel: String?,
    val busiestDayMs: Long,
    val longestStreakDays: Int,
    val favoritesAdded: Int,
    /** Hora local (0 a 23) en la que más se escuchó, o null sin datos. */
    val peakHour: Int?,
    val personality: String,
)
