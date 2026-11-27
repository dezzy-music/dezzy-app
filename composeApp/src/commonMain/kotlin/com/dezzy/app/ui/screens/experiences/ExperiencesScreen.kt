/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.experiences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.stats.StatsDates
import com.dezzy.app.feature.stats.StatsStore
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/** Colores propios del DJ (su tarjeta conserva el aire oscuro de la pantalla del DJ). */
private val ExperienceDjStart = Color(0xFF2A0F2E)
private val ExperienceDjMid = Color(0xFF5B2830)
private val ExperienceDjEnd = Color(0xFF9C3F48)
private val ExperienceDjAccent = Color(0xFFFFB3B9)

private enum class ExperienceTone { Neutral, Primary, Tertiary }

private class ExperienceTile(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val tone: ExperienceTone = ExperienceTone.Neutral,
    val badge: String? = null,
    val onClick: () -> Unit,
)

private class ExperienceLink(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/**
 * Pantalla «Experiencias»: reúne en un mosaico las experiencias de Dezzy (DJ, Tu año,
 * estadísticas, Máquina del tiempo, Mapa, Song DNA y Lossless) y debajo el resto de accesos.
 */
@Composable
fun ExperiencesScreen(ctx: ScreenContext) {
    val stats: StatsStore = koinInject()
    val djName by ctx.prefs.djName.collectAsState("")

    // Minutos escuchados esta semana.
    val weekMs by produceState(0L) {
        value = runCatching { stats.overview(StatsDates.offsetMs()).weekMs }.getOrDefault(0L)
    }

    // Song DNA necesita una canción: la actual o, si no hay, la más escuchada.
    val topVideoId by produceState<String?>(null) {
        value = runCatching { ctx.repository.getMostPlayed(0L, 1).first().firstOrNull()?.videoId }.getOrNull()
    }
    val dnaVideoId = ctx.playingId ?: topVideoId

    val tiles = listOf(
        ExperienceTile(
            key = "year",
            icon = Icons.Rounded.AutoAwesome,
            title = "Tu año en música",
            subtitle = "Tu resumen ${com.dezzy.app.feature.stats.StatsDates.currentYear()}",
            tone = ExperienceTone.Primary,
            onClick = { ctx.navigator.push(Screen.YearInMusic) },
        ),
        ExperienceTile(
            key = "stats",
            icon = Icons.Rounded.Insights,
            title = formatExperienceListenTime(weekMs),
            subtitle = "Escuchado esta semana",
            onClick = { ctx.navigator.push(Screen.Stats) },
        ),
        ExperienceTile(
            key = "time_machine",
            icon = Icons.Rounded.Restore,
            title = "Máquina del tiempo",
            subtitle = "Viaja por décadas",
            onClick = { ctx.navigator.push(Screen.TimeMachine) },
        ),
        ExperienceTile(
            key = "map",
            icon = Icons.Rounded.Public,
            title = "Mapa musical",
            subtitle = "Sonidos por país",
            onClick = { ctx.navigator.push(Screen.MusicMap) },
        ),
        ExperienceTile(
            key = "song_dna",
            icon = Icons.Rounded.Hub,
            title = "Song DNA",
            subtitle = if (dnaVideoId != null) "Artistas afines" else "Escucha algo primero",
            badge = "Beta",
            onClick = { dnaVideoId?.let { ctx.navigator.push(Screen.SongDna(it)) } ?: ctx.toast("Escucha algo primero") },
        ),
        ExperienceTile(
            key = "lossless",
            icon = Icons.Rounded.Headphones,
            title = "Lossless",
            subtitle = "FLAC sin pérdida",
            tone = ExperienceTone.Tertiary,
            onClick = { ctx.navigator.push(Screen.Lossless) },
        ),
    )

    val links = listOf(
        ExperienceLink("new", Icons.Rounded.NewReleases, "Novedades", "Lo nuevo de los artistas que sigues") { ctx.navigator.push(Screen.NewReleases) },
        ExperienceLink("history", Icons.Rounded.History, "Historial", "Lo que escuchaste hace poco") { ctx.navigator.push(Screen.History) },
        ExperienceLink("downloads", Icons.Rounded.Download, "Mis descargas", "Canciones descargadas y cola") { ctx.navigator.push(Screen.Downloads) },
        ExperienceLink("together", Icons.Rounded.Groups, "Escuchar juntos", "Salas públicas y privadas") { ctx.navigator.push(Screen.Together()) },
        ExperienceLink("charts", Icons.Rounded.Leaderboard, "Listas de éxitos", "Lo más viral y charts mundiales") { ctx.navigator.push(Screen.Charts()) },
        ExperienceLink("recognize", Icons.Rounded.Mic, "Reconocer música", "Qué canción está sonando") { ctx.pending("Reconocer música", "10") },
    )

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle("Experiencias", onBack = { ctx.navigator.back() })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "dj", span = { GridItemSpan(maxLineSpan) }) {
                ExperienceDjCard(djName, onClick = { ctx.navigator.push(Screen.Dj) })
            }

            items(items = tiles, key = { "tile_${it.key}" }) { tile -> ExperienceTileCard(tile) }

            item(key = "more_title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Más de Dezzy",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp).semantics { heading() },
                )
            }

            items(items = links, key = { "link_${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { link ->
                ListItem(
                    headlineContent = { Text(link.title) },
                    supportingContent = { Text(link.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                link.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = link.onClick),
                )
            }
        }
    }
}

/** Tarjeta grande del DJ: ocupa las dos columnas y lleva el degradado oscuro del DJ. */
@Composable
private fun ExperienceDjCard(djName: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ExperienceDjStart, ExperienceDjMid, ExperienceDjEnd)))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        // Orbe decorativo arriba a la derecha.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = (-28).dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White, ExperienceDjAccent, Color(0xFFED5564), ExperienceDjEnd, Color.Transparent),
                    ),
                ),
        )
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = ExperienceDjAccent, modifier = Modifier.size(28.dp))
            Column(Modifier.fillMaxWidth(0.7f)) {
                Text(
                    text = if (djName.isBlank()) "DJ Dezzy" else "DJ $djName",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Text(
                    text = "Tu música, mezclada y presentada por voz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DezzyButton(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ExperienceDjEnd),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Iniciar")
            }
        }
    }
}

@Composable
private fun ExperienceTileCard(tile: ExperienceTile) {
    val colorScheme = MaterialTheme.colorScheme
    val (container, content, iconTint) = when (tile.tone) {
        ExperienceTone.Primary -> Triple(colorScheme.primaryContainer, colorScheme.onPrimaryContainer, colorScheme.onPrimaryContainer)
        ExperienceTone.Tertiary -> Triple(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer, colorScheme.tertiary)
        ExperienceTone.Neutral -> Triple(colorScheme.surfaceContainer, colorScheme.onSurface, colorScheme.primary)
    }
    Surface(
        onClick = tile.onClick,
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(tile.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tile.badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.inverseSurface,
                            contentColor = colorScheme.inverseOnSurface,
                        ) {
                            Text(
                                text = tile.badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = content.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** «38 h 12 min», «45 min» o «0 min» a partir de milisegundos. */
internal fun formatExperienceListenTime(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }
}
