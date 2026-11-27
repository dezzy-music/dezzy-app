/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.stats.PeriodStats
import com.dezzy.app.feature.stats.RankedName
import com.dezzy.app.feature.stats.StatsDates
import com.dezzy.app.feature.stats.StatsOverview
import com.dezzy.app.feature.stats.StatsPeriod
import com.dezzy.app.feature.stats.StatsStore
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.SectionHeader
import com.dezzy.app.ui.components.glass.DezzyFilterChip
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.emptyRow
import org.koin.compose.koinInject

/**
 * Estadísticas: racha y minutos de hoy / semana / mes / año, tiempo escuchado por periodo con
 * barras, tops de canciones, artistas y álbumes, horas del día y accesos a «Tu año en música»,
 * la Máquina del tiempo y el ADN de la canción.
 */
@Composable
fun StatsScreen(ctx: ScreenContext) {
    val store: StatsStore = koinInject()
    val offset = remember { StatsDates.offsetMs() }
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }

    // Se recalcula al cambiar de periodo y cada vez que empieza otra canción (entra una reproducción nueva).
    val overview by produceState<StatsOverview?>(null, ctx.playingId) { value = store.overview(offset) }
    val stats by produceState<PeriodStats?>(null, period, ctx.playingId) { value = store.period(period, offset) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "top") {
            TopSpacer()
            ScreenTitle("Estadísticas", onBack = { ctx.navigator.back() }) {
                IconButton(onClick = { ctx.navigator.push(Screen.YearInMusic) }) {
                    Icon(Icons.Rounded.AutoAwesome, "Tu año en música")
                }
            }
        }

        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(StatsPeriod.entries.size) { i ->
                    val item = StatsPeriod.entries[i]
                    DezzyFilterChip(
                        selected = item == period,
                        onClick = { period = item },
                        label = { Text(item.label) },
                    )
                }
            }
        }

        val current = stats
        if (current == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("Calculando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@LazyColumn
        }

        item(key = "hero") {
            val summary = if (current.totalPlays == 0) "Aún no hay reproducciones en este periodo"
            else "${current.totalPlays} reproducciones, ${current.distinctTracks} canciones distintas"
            StatsListeningCard(
                periodLabel = current.period.phrase,
                listening = current.listening,
                summary = summary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item(key = "pills") {
            val o = overview ?: StatsOverview()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                item { StatPill(if (o.streakDays == 1) "1 día" else "${o.streakDays} días", "Racha actual") }
                item { StatPill(if (o.longestStreakDays == 1) "1 día" else "${o.longestStreakDays} días", "Mejor racha") }
                item { StatPill(formatMinutes(o.todayMs), "Hoy") }
                item { StatPill(formatMinutes(o.weekMs), "Esta semana") }
                item { StatPill(formatMinutes(o.monthMs), "Este mes") }
                item { StatPill(formatMinutes(o.yearMs), "Este año") }
            }
        }

        val topSong = current.topTracks.firstOrNull()
        val topArtist = current.topArtists.firstOrNull()
        if (topSong != null || topArtist != null) {
            item(key = "favorites") {
                StatsFavoritesRow(
                    songTitle = topSong?.track?.title,
                    songSubtitle = topSong?.let { "${it.track.artists} · ${it.plays} reproducciones" },
                    songThumbnail = topSong?.track?.thumbnail,
                    onSongClick = { topSong?.let { ctx.player.playTracks(listOf(it.track), source = "Estadísticas") } },
                    artistName = topArtist?.name,
                    artistSubtitle = topArtist?.let { "${it.plays} reproducciones" },
                    artistThumbnail = topArtist?.thumbnail,
                    onArtistClick = { topArtist?.let { openArtistByName(ctx, it.name) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        item(key = "h-songs") { SectionHeader("Canciones más escuchadas") }
        if (current.topTracks.isEmpty()) {
            emptyRow("Escucha algo y aquí aparecerán tus canciones más repetidas.")
        } else {
            val queue = current.topTracks.map { it.track }
            current.topTracks.take(10).forEachIndexed { index, ranked ->
                item(key = "song:${ranked.track.videoId}:$index") {
                    RankedRow(
                        rank = index + 1,
                        title = ranked.track.title,
                        subtitle = ranked.track.artists,
                        thumbnail = ranked.track.thumbnail,
                        plays = ranked.plays,
                        round = false,
                        highlighted = ranked.track.videoId == ctx.playingId,
                        onClick = { ctx.player.playTracks(queue, start = index, source = "Estadísticas") },
                    )
                }
            }
        }

        if (current.topArtists.isNotEmpty()) {
            item(key = "h-artists") { SectionHeader("Artistas más escuchados") }
            current.topArtists.take(10).forEachIndexed { index, artist ->
                item(key = "artist:${artist.name}") {
                    RankedRow(
                        rank = index + 1,
                        title = artist.name,
                        subtitle = null,
                        thumbnail = artist.thumbnail,
                        plays = artist.plays,
                        round = true,
                        highlighted = false,
                        onClick = { openArtistByName(ctx, artist.name) },
                    )
                }
            }
        }

        if (current.topAlbums.isNotEmpty()) {
            item(key = "h-albums") { SectionHeader("Álbumes más escuchados") }
            current.topAlbums.take(10).forEachIndexed { index, album: RankedName ->
                item(key = "album:${album.name}:${album.subtitle}") {
                    RankedRow(
                        rank = index + 1,
                        title = album.name,
                        subtitle = album.subtitle,
                        thumbnail = album.thumbnail,
                        plays = album.plays,
                        round = false,
                        highlighted = false,
                        onClick = { openAlbumByName(ctx, album.name, album.subtitle) },
                    )
                }
            }
        }

        item(key = "h-hours") { SectionHeader("¿A qué hora escuchas?") }
        item(key = "hours") {
            HourBars(current.hourSeconds, Modifier.padding(horizontal = 16.dp))
        }

        item(key = "h-more") { SectionHeader("Más de lo tuyo") }
        item(key = "more-year") {
            MoreRow("Tu año en música", "Tu resumen anual, en historias") { ctx.navigator.push(Screen.YearInMusic) }
        }
        item(key = "more-time") {
            MoreRow("Máquina del tiempo", "Viaja a un año y escucha lo que sonaba") { ctx.navigator.push(Screen.TimeMachine) }
        }
        ctx.playingId?.let { id ->
            item(key = "more-dna") {
                MoreRow("ADN de lo que suena", "Artistas y canciones que conectan con esta canción") {
                    ctx.navigator.push(Screen.SongDna(id))
                }
            }
        }
    }
}

@Composable
private fun RankedRow(
    rank: Int,
    title: String,
    subtitle: String?,
    thumbnail: String?,
    plays: Int,
    round: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsRankNumber(rank)
        CoverImage(thumbnail, Modifier.size(48.dp), if (round) CircleShape else RoundedCornerShape(6.dp), sizePx = 144)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (plays == 1) "1 vez" else "$plays veces",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Las 24 horas del día; la más escuchada va resaltada. */
@Composable
private fun HourBars(hourSeconds: List<Long>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val max = hourSeconds.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val peak = hourSeconds.indices.maxByOrNull { hourSeconds[it] }?.takeIf { hourSeconds[it] > 0 }
    Column(modifier) {
        Text(
            if (peak == null) "Sin datos todavía" else "Escuchas más a las ${peak.toString().padStart(2, '0')}:00",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            hourSeconds.forEachIndexed { hour, seconds ->
                val fraction = (seconds.toFloat() / max).coerceIn(0.03f, 1f)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(fraction, fill = true)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (hour == peak) colors.primary else colors.primary.copy(alpha = 0.35f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach {
                Text(it + " h", fontSize = 11.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(Icons.Rounded.History, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
