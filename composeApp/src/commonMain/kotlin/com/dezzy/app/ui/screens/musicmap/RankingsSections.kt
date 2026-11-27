/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.musicmap.CountryNames
import com.dezzy.app.feature.musicmap.MusicMapStore
import com.dezzy.app.feature.musicmap.PlayedTrackRow
import com.dezzy.app.feature.musicmap.SongChartStats
import com.dezzy.app.feature.musicmap.SongStatsResult
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ── Tus canciones en los rankings (historial) ────────────────────────────────

/** Mejor puesto conocido de una canción del historial. */
internal sealed interface HistoryPeak {
    data object Loading : HistoryPeak
    data class Charted(val peak: Int, val countryCode: String, val countries: Int) : HistoryPeak
    data object NeverCharted : HistoryPeak
    data object Unknown : HistoryPeak
}

/** Estado de la sección «Tus canciones en los rankings». */
internal class HistoryRankingsState {
    var hasSpotify by mutableStateOf<Boolean?>(null)
    val peaks: SnapshotStateMap<String, HistoryPeak> = mutableStateMapOf()
}

/**
 * Consulta, una a una, las listas de tus canciones más escuchadas. Cada resultado queda en la
 * caché local de [SongChartStats] (12 h), así que volver a entrar no repite las peticiones.
 * Spotify sólo se usa con la sesión del propio usuario para localizar la canción.
 */
@Composable
internal fun rememberHistoryRankings(songs: List<PlayedTrackRow>, service: SongChartStats): HistoryRankingsState {
    val state = remember { HistoryRankingsState() }
    val ids = songs.map { it.videoId }
    LaunchedEffect(ids) {
        val has = service.hasSpotifySession()
        state.hasSpotify = has
        if (!has) return@LaunchedEffect
        songs.forEach { song ->
            if (state.peaks[song.videoId].let { it != null && it != HistoryPeak.Loading }) return@forEach
            state.peaks[song.videoId] = HistoryPeak.Loading
            state.peaks[song.videoId] = peakOf(service, song)
        }
    }
    return state
}

private suspend fun peakOf(service: SongChartStats, song: PlayedTrackRow): HistoryPeak {
    val artist = song.artists
    return when (val r = service.forSong(song.title, artist)) {
        is SongStatsResult.Ok -> {
            val countries = r.stats.byCountry
            val best = countries.minWithOrNull(compareBy({ it.peak }, { -it.totalStreams })) ?: r.stats.global
            if (best == null) HistoryPeak.NeverCharted else HistoryPeak.Charted(best.peak, best.code, countries.size)
        }
        is SongStatsResult.NeverCharted -> HistoryPeak.NeverCharted
        else -> HistoryPeak.Unknown
    }
}

@Composable
internal fun RankingsSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Aviso dentro de Rankings cuando no hay sesión de Spotify. */
@Composable
internal fun ConnectSpotifyCard(onConnect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "Conecta Spotify para ver en qué listas han estado tus canciones.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onConnect) { Text("Conectar") }
        }
    }
}

/** Fila de canción del historial con su mejor puesto y botón de estadísticas. */
@Composable
internal fun RankingSongRow(
    title: String,
    subtitle: String,
    thumbnail: String?,
    isActive: Boolean,
    onPlay: () -> Unit,
    onStats: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Escuchar $title", onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        CoverImage(thumbnail, Modifier.size(48.dp), RoundedCornerShape(8.dp), sizePx = 144)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        IconButton(onClick = onStats) {
            Icon(Icons.Rounded.BarChart, contentDescription = "Estadísticas de $title")
        }
    }
}

/** Mejor puesto a la derecha de la fila del historial. */
@Composable
internal fun HistoryPeakBadge(peak: HistoryPeak?) {
    val scheme = MaterialTheme.colorScheme
    when (peak) {
        HistoryPeak.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        is HistoryPeak.Charted -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CountryNames.flag(peak.countryCode), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                "#${peak.peak}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (peak.peak <= 10) scheme.primary else scheme.onSurface,
            )
        }
        else -> Unit
    }
}

/** Texto bajo el título de una canción del historial. */
internal fun historySubtitle(song: PlayedTrackRow, peak: HistoryPeak?): String {
    val artist = song.artists
    return when (peak) {
        is HistoryPeak.Charted -> "$artist, mejor #${peak.peak} en ${CountryNames.name(peak.countryCode)}" +
            if (peak.countries > 1) ", en ${peak.countries} países" else ""
        HistoryPeak.NeverCharted -> "$artist, no ha entrado en ninguna lista"
        else -> artist
    }
}

/**
 * Sección «Tus canciones en los rankings»: tus más escuchadas (historial local) con el mejor puesto
 * que han tenido en las listas por país. Sin historial no pinta nada.
 */
internal fun LazyListScope.historyRankingsSection(ctx: ScreenContext, songs: List<PlayedTrackRow>, state: HistoryRankingsState) {
    if (songs.isEmpty()) return
    item(key = "history_title") {
        RankingsSectionTitle("Tus canciones en los rankings", "Tus más escuchadas y su mejor puesto en las listas")
    }
    if (state.hasSpotify == false) {
        item(key = "history_connect") {
            ConnectSpotifyCard { ctx.navigator.push(Screen.Settings(SettingsPage.SPOTIFY)) }
        }
    }
    items(songs.size, key = { "history_${songs[it].videoId}" }) { i ->
        val song = songs[i]
        val peak = state.peaks[song.videoId]
        val track = com.dezzy.app.core.model.Track(song.videoId, song.title, song.artists, thumbnail = song.thumbnail)
        RankingSongRow(
            title = song.title,
            subtitle = historySubtitle(song, peak),
            thumbnail = song.thumbnail,
            isActive = song.videoId == ctx.playingId,
            onPlay = { ctx.player.playRadio(track, "mapa:${song.title}") },
            onStats = {
                ctx.scope.launch {
                    ctx.repository.saveTrack(track)
                    ctx.navigator.push(Screen.SongStats(song.videoId))
                }
            },
            trailing = { HistoryPeakBadge(peak) },
        )
    }
}

/** Carga las más escuchadas del historial local (hasta [limit]). */
@Composable
internal fun rememberMostPlayed(limit: Long = 10): List<PlayedTrackRow> {
    val store = koinInject<MusicMapStore>()
    val songs by produceState<List<PlayedTrackRow>>(emptyList(), limit) { value = store.mostPlayed(limit) }
    return songs
}
