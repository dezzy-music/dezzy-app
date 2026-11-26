/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.ui.screens.downloads.enqueueSpotifyImports
import com.dezzy.app.source.spotify.SpotifyClient
import com.dezzy.app.source.spotify.SpotifyImportState
import com.dezzy.app.source.spotify.SpotifyImporter
import com.dezzy.app.source.spotify.SpotifyPlaylistSummary
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Importar tu biblioteca de Spotify»: elegir listas, ver el progreso y el resultado. */
@Composable
internal fun SpotifyImportScreen(ctx: ScreenContext) {
    val importer: SpotifyImporter = koinInject()
    val state by importer.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state is SpotifyImportState.Idle) importer.loadPlaylists()
    }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle("Importar de Spotify", onBack = {
            // Salir a mitad de una importación no la cancela: sigue en segundo plano.
            if (state !is SpotifyImportState.Importing) importer.reset()
            ctx.navigator.back()
        })

        when (val s = state) {
            is SpotifyImportState.Choosing -> ChoosingContent(ctx, importer, s)
            else -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        SpotifyImportState.Idle, SpotifyImportState.LoadingPlaylists -> {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Buscando tus playlists y canciones…")
                        }
                        is SpotifyImportState.Choosing -> Unit
                        is SpotifyImportState.Importing -> {
                            val progress = if (s.total > 0) s.done.toFloat() / s.total else 0f
                            Text(
                                "Lista ${s.playlistIndex} de ${s.playlistCount}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s.playlistName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("${s.done} / ${s.total} canciones")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Cada canción se busca en YouTube Music. Puede tardar un rato.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = { importer.cancel() }) { Text("Cancelar") }
                        }
                        is SpotifyImportState.Done -> {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Importación completada", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${s.playlists} ${if (s.playlists == 1) "lista" else "listas"}, " +
                                    "${s.tracks} ${if (s.tracks == 1) "canción" else "canciones"}; ${s.notFound} sin encontrar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                importer.reset()
                                ctx.navigator.back()
                            }) { Text("Listo") }
                        }
                        is SpotifyImportState.Failed -> {
                            Text(
                                "Error: ${s.message}",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                importer.reset()
                                importer.loadPlaylists()
                            }) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChoosingContent(ctx: ScreenContext, importer: SpotifyImporter, state: SpotifyImportState.Choosing) {
    val playlists = state.playlists
    // Las ya importadas llegan marcadas; la selección se rehace si cambia la lista recibida.
    var selected by remember(state) {
        mutableStateOf(playlists.map { it.id }.filter { it in state.alreadyImported }.toSet())
    }
    val likedAsPlaylist by ctx.prefs.spotifyLikedAsPlaylist.collectAsState(true)

    Text(
        "Elige qué quieres importar",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { selected = playlists.map { it.id }.toSet() }) { Text("Seleccionar todo") }
        TextButton(onClick = { selected = emptySet() }) { Text("Nada") }
        Spacer(Modifier.weight(1f))
        Text(
            "${selected.size} de ${playlists.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
    }

    LazyColumn(Modifier.weight(1f)) {
        items(playlists, key = { it.id }) { playlist ->
            val checked = playlist.id in selected
            fun toggle() { selected = if (checked) selected - playlist.id else selected + playlist.id }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = { toggle() })
                Spacer(Modifier.width(4.dp))
                PlaylistThumb(playlist)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = buildList {
                        playlist.trackCount?.let { add("$it canciones") }
                        if (playlist.id in state.alreadyImported) add("Ya importada")
                    }.joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }

    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        SettingsPendingRowPlain(ctx)
        // Destino de «Canciones que te gustan»: sólo aplica si esa entrada está marcada.
        if (SpotifyClient.LIKED_ID in selected) {
            Spacer(Modifier.height(12.dp))
            Text("¿Dónde guardar tus me gusta de Spotify?", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = likedAsPlaylist,
                    onClick = { ctx.scope.launch { ctx.prefs.setSpotifyLikedAsPlaylist(true) } },
                    label = { Text("Sólo lista «Me gusta de Spotify»") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !likedAsPlaylist,
                    onClick = { ctx.scope.launch { ctx.prefs.setSpotifyLikedAsPlaylist(false) } },
                    label = { Text("También en Me gusta de la app") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (likedAsPlaylist) {
                    "Se crea una lista aparte; tus me gusta de la app (YouTube Music) quedan intactos, sin duplicados."
                } else {
                    "Además de la lista, cada canción se marca como me gusta en la app (se mezclan con los de YouTube Music)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { importer.import(selected) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (selected.isEmpty()) "Importar" else "Importar (${selected.size})") }
    }
}

/** Encola para descargar las canciones de las listas de Spotify ya importadas. */
@Composable
private fun SettingsPendingRowPlain(ctx: ScreenContext) {
    val downloads: DownloadManager = koinInject()
    TextButton(
        onClick = {
            ctx.scope.launch {
                val n = enqueueSpotifyImports(ctx, downloads)
                ctx.toast(if (n == 0) "No hay canciones importadas que descargar" else "$n canciones a descargas")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Descargar para escuchar sin conexión") }
}

@Composable
private fun PlaylistThumb(playlist: SpotifyPlaylistSummary) {
    if (playlist.isLiked || playlist.id == SpotifyClient.LIKED_ID) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
    } else {
        CoverImage(playlist.imageUrl, Modifier.size(44.dp), RoundedCornerShape(8.dp), sizePx = 132)
    }
}
