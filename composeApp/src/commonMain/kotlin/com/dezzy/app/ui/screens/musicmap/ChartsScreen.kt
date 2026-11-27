/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.musicmap.CountryNames
import com.dezzy.app.feature.musicmap.MusicMapCharts
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.emptyRow
import com.dezzy.app.ui.screens.loadingRows
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Los éxitos de un país (o los mundiales) según YouTube Music, con flechas de tendencia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(ctx: ScreenContext, countryCode: String?) {
    val charts = koinInject<MusicMapCharts>()
    var code by remember { mutableStateOf(countryCode?.uppercase()?.takeIf { it.length == 2 } ?: MusicMapCharts.GLOBAL) }
    var reload by remember { mutableStateOf(0) }
    val codes = remember { listOf(MusicMapCharts.GLOBAL) + MAP_COUNTRIES.map { it.code } }

    // null = cargando
    val songs by produceState<List<Track>?>(null, code, reload) {
        value = null
        value = runCatching { charts.forCountry(code) }.getOrDefault(emptyList())
    }
    val previous by produceState<Map<String, Int>>(emptyMap(), code, songs) {
        value = charts.previousRanks(code)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(code) { listState.scrollToItem(0) }
    val scope = ctx.scope
    val title = if (code == MusicMapCharts.GLOBAL) "Éxitos mundiales" else "Éxitos en ${CountryNames.name(code)}"

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopSpacer() }
        item { ScreenTitle(title, onBack = { ctx.navigator.back() }) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                items(codes, key = { it }) { c ->
                    FilterChip(
                        selected = c == code,
                        onClick = { code = c },
                        label = { Text("${CountryNames.flag(c)} ${if (c == MusicMapCharts.GLOBAL) "Mundial" else CountryNames.name(c)}") },
                    )
                }
            }
        }
        val list = songs
        when {
            list == null -> loadingRows()
            list.isEmpty() -> emptyRow("No se pudo cargar este top. Revisa la conexión e inténtalo de nuevo.")
            else -> {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${cancionesLabel(list.size)} hoy",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = { ctx.player.playTracks(list, 0, title) }) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text("Reproducir")
                        }
                    }
                }
                items(list.size, key = { "${list[it].videoId}_$it" }) { i ->
                    val song = list[i]
                    TopSongRow(
                        rank = i + 1,
                        song = song,
                        isCurrent = song.videoId != null && song.videoId == ctx.playingId,
                        previousRanks = previous,
                        onClick = { ctx.player.playTracks(list, i, title) },
                        onStats = {
                            val id = song.videoId ?: return@TopSongRow
                            scope.launch {
                                ctx.repository.saveTrack(song)
                                ctx.navigator.push(Screen.SongStats(id))
                            }
                        },
                    )
                }
            }
        }
    }
}
