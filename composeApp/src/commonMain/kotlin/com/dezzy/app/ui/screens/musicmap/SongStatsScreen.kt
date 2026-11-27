/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.musicmap.ChartCountryStats
import com.dezzy.app.feature.musicmap.CountryNames
import com.dezzy.app.feature.musicmap.SongChartStats
import com.dezzy.app.feature.musicmap.SongStats
import com.dezzy.app.feature.musicmap.SongStatsResult
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.SectionHeader
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import org.koin.compose.koinInject

/**
 * Estadísticas de una canción en las listas de Spotify por país (datos públicos de kworb):
 * streams totales, mejor puesto, dónde suena hoy y cómo ha ido semana a semana.
 */

private sealed interface StatsState {
    data object Loading : StatsState
    data class Done(val title: String, val artist: String, val thumbnail: String?, val result: SongStatsResult) : StatsState
    data object NoTrack : StatsState
}

@Composable
fun SongStatsScreen(ctx: ScreenContext, videoId: String) {
    val service = koinInject<SongChartStats>()
    var reload by remember { mutableStateOf(0) }
    val state by produceState<StatsState>(StatsState.Loading, videoId, reload) {
        value = StatsState.Loading
        val track = ctx.repository.getTrack(videoId)
        value = if (track == null) {
            StatsState.NoTrack
        } else {
            StatsState.Done(track.title, track.firstArtist(), track.thumbnail, service.forSong(track.title, track.firstArtist()))
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { TopSpacer() }
        item { ScreenTitle("Estadísticas", onBack = { ctx.navigator.back() }) }
        when (val s = state) {
            StatsState.Loading -> item {
                Row(Modifier.fillMaxWidth().padding(48.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            StatsState.NoTrack -> item {
                EmptyStatsCard("No encontramos esta canción", "Ábrela otra vez desde un ranking para ver sus estadísticas.", null, null)
            }
            is StatsState.Done -> {
                item { SongHeader(s.title, s.artist, s.thumbnail) }
                when (val r = s.result) {
                    is SongStatsResult.Ok -> statsBlocks(r.stats)
                    is SongStatsResult.NeverCharted -> item {
                        EmptyStatsCard(
                            "Aún no ha entrado en ninguna lista",
                            "Esta canción existe en Spotify, pero no aparece en los rankings diarios de ningún país.",
                            null, null,
                        )
                    }
                    SongStatsResult.NoSpotifySession -> item {
                        EmptyStatsCard(
                            "Falta tu sesión de Spotify",
                            "Usamos Spotify sólo para localizar la canción. Conéctalo para ver sus estadísticas.",
                            "Conectar Spotify",
                        ) { ctx.navigator.push(Screen.Settings(SettingsPage.SPOTIFY)) }
                    }
                    SongStatsResult.NotFoundOnSpotify -> item {
                        EmptyStatsCard("No está en Spotify", "No encontramos esta canción allí, así que no hay estadísticas.", null, null)
                    }
                    SongStatsResult.NetworkError -> item {
                        EmptyStatsCard("No se pudo cargar", "Revisa tu conexión e inténtalo de nuevo.", "Reintentar") { reload++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongHeader(title: String, artist: String, thumbnail: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(thumbnail, Modifier.size(72.dp), RoundedCornerShape(12.dp), sizePx = 216)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.statsBlocks(stats: SongStats) {
    item(key = "kpi") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Streams totales", formatStreams(stats.totalStreams), Modifier.weight(1f))
            Kpi("Mejor puesto", stats.bestPeak?.let { "#$it" } ?: "-", Modifier.weight(1f))
            Kpi("Países", stats.byCountry.size.toString(), Modifier.weight(1f))
        }
    }

    stats.today?.takeIf { it.positions.isNotEmpty() }?.let { today ->
        item(key = "today") {
            Column {
                SectionHeader("Ahora mismo", strapline = longDate(today.date))
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    today.positions.forEach { (code, pos) ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "${CountryNames.flag(code)} ${CountryNames.name(code)} #$pos",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    stats.global?.takeIf { it.weeks.size >= 2 }?.let { g ->
        item(key = "chart") {
            Column {
                SectionHeader("Evolución mundial", strapline = "Últimas ${minOf(g.weeks.size, 26)} semanas")
                WeeklyChart(g, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
    }

    item(key = "countries") { CountriesBlock(stats.byCountry) }
}

@Composable
private fun CountriesBlock(countries: List<ChartCountryStats>) {
    var all by remember { mutableStateOf(false) }
    val shown = if (all) countries else countries.take(8)
    Column {
        SectionHeader(
            "Por país",
            strapline = "Streams en las listas diarias",
            action = if (countries.size > 8) (if (all) "Ver menos" else "Ver todos") else null,
            onAction = { all = !all },
        )
        shown.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(CountryNames.flag(c.code), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Mejor puesto #${c.peak}, ${formatStreams(c.peakStreams)} en su mejor día",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatStreams(c.totalStreams), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Línea de streams por semana (las últimas 26). */
@Composable
private fun WeeklyChart(stats: ChartCountryStats, modifier: Modifier = Modifier) {
    val weeks = stats.weeks.takeLast(26)
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = weeks.maxOf { it.streams }.coerceAtLeast(1L)
    val peak = weeks.maxByOrNull { it.streams }
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).semantics {
                contentDescription = "Gráfica semanal de streams, máximo ${formatStreams(max)}"
            },
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            val path = Path()
            weeks.forEachIndexed { i, p ->
                val x = if (weeks.size == 1) 0f else w * i / (weeks.size - 1f)
                val y = h - (p.streams.toFloat() / max) * (h - 8f) - 4f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortDate(weeks.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (peak != null) {
                Text("Pico ${formatStreams(peak.streams)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(shortDate(weeks.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyStatsCard(title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction, modifier = Modifier.height(48.dp)) { Text(action) }
            }
        }
    }
}

// ── Formato a mano (sin Locale ni String.format, que no existen en commonMain) ──

internal fun formatStreams(n: Long): String = when {
    n >= 1_000_000_000L -> "${oneDecimal(n / 1_000_000_000.0)} mil M"
    n >= 1_000_000L -> "${oneDecimal(n / 1_000_000.0)} M"
    n >= 1_000L -> "${oneDecimal(n / 1_000.0)} mil"
    else -> n.toString()
}

private fun oneDecimal(v: Double): String {
    val tenths = (v * 10).toLong()
    val whole = tenths / 10
    val dec = tenths % 10
    return if (dec == 0L) whole.toString() else "$whole,$dec"
}

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun parts(date: String): Triple<Int, Int, Int>? {
    val p = date.take(10).split("-", "/")
    if (p.size < 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return Triple(y, m, d)
}

private fun shortDate(date: String): String = parts(date)?.let { (_, m, d) -> "$d ${MONTHS[m - 1]}" } ?: date

private fun longDate(date: String): String = parts(date)?.let { (y, m, d) -> "$d ${MONTHS[m - 1]} $y" } ?: date
