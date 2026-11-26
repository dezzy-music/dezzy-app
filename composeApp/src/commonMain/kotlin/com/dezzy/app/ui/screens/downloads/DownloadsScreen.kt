/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.DownloadItem
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.downloads.DownloadStatus
import com.dezzy.app.feature.downloads.export.DownloadExporter
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class DownloadFilter { ALL, ACTIVE, ERROR }

/** Tamaño legible con 1 decimal: KB, MB o GB. */
fun formatBytes(bytes: Long): String {
    fun one(value: Double): String {
        val tenths = (value * 10).toLong()
        return "${tenths / 10},${tenths % 10}"
    }
    return when {
        bytes >= 1L shl 30 -> "${one(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        bytes >= 1L shl 20 -> "${one(bytes / (1024.0 * 1024.0))} MB"
        else -> "${one(bytes / 1024.0)} KB"
    }
}

private fun isActive(s: DownloadStatus) = s == DownloadStatus.QUEUED || s == DownloadStatus.RESOLVING || s == DownloadStatus.SAVING

/** Descargas: cola, estado, reproducir lo descargado, pausa, exportar y ajustes. */
@Composable
internal fun DownloadsScreen(ctx: ScreenContext) {
    val manager = koinInject<DownloadManager>()
    val exporter = koinInject<DownloadExporter>()
    val state by manager.state.collectAsState()
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }

    val items = state.items
    val done = remember(items) { items.filter { it.status == DownloadStatus.DONE }.sortedByDescending { it.finishedAt ?: 0L } }
    val active = remember(items) { items.filter { isActive(it.status) } }
    val failed = remember(items) { items.filter { it.status == DownloadStatus.ERROR } }
    val doneTracks = remember(done) { done.map { it.track } }
    val shown = when (filter) {
        DownloadFilter.ALL -> active + failed + done
        DownloadFilter.ACTIVE -> active
        DownloadFilter.ERROR -> failed
    }
    val paused = state.paused

    val pickFolder = rememberExportFolderPicker { folder ->
        ctx.scope.launch {
            val copied = runCatching { exporter.export(folder, done) }.getOrDefault(0)
            ctx.toast(if (copied == 1) "1 canción copiada" else "$copied canciones copiadas")
        }
    }

    val meta = listOfNotNull(
        "${done.size} ${if (done.size == 1) "canción" else "canciones"}",
        "${formatBytes(state.usedBytes)} usados",
        state.freeBytes?.let { "${formatBytes(it)} libres" },
    ).joinToString(" • ")

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Column {
                TopSpacer()
                ScreenTitle("Descargas", onBack = { ctx.navigator.back() }, actions = {
                    IconButton(onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.STORAGE)) }) {
                        Icon(Icons.Rounded.Tune, "Ajustes de descargas")
                    }
                })
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item(key = "actions") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(onClick = { ctx.player.playTracks(doneTracks, 0, "downloads") }, enabled = doneTracks.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Reproducir")
                }
                FilledTonalButton(onClick = { ctx.player.playTracks(doneTracks.shuffled(), 0, "downloads") }, enabled = doneTracks.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Aleatorio")
                }
                FilledTonalButton(onClick = { manager.setPaused(!paused) }, enabled = paused || active.isNotEmpty()) {
                    Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (paused) "Reanudar" else "Pausar")
                }
                AssistChip(
                    onClick = pickFolder,
                    enabled = done.isNotEmpty(),
                    label = { Text("Exportar a una carpeta") },
                    leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { ctx.navigator.push(Screen.LosslessDownloads) },
                    label = { Text("Descargas lossless") },
                    leadingIcon = { Icon(Icons.Rounded.HighQuality, null, Modifier.size(18.dp)) },
                )
            }
        }
        item(key = "filters") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                FilterChip(selected = filter == DownloadFilter.ALL, onClick = { filter = DownloadFilter.ALL }, label = { Text("Todo") })
                if (active.isNotEmpty() || filter == DownloadFilter.ACTIVE) {
                    FilterChip(
                        selected = filter == DownloadFilter.ACTIVE,
                        onClick = { filter = DownloadFilter.ACTIVE },
                        label = { Text("En curso · ${active.size}") },
                    )
                }
                if (failed.isNotEmpty() || filter == DownloadFilter.ERROR) {
                    FilterChip(
                        selected = filter == DownloadFilter.ERROR,
                        onClick = { filter = DownloadFilter.ERROR },
                        label = { Text("Error · ${failed.size}") },
                    )
                }
                if (failed.isNotEmpty()) {
                    TextButton(onClick = { manager.retryFailed() }) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Reintentar las ${failed.size}")
                    }
                }
            }
        }
        if (paused && active.isNotEmpty()) {
            item(key = "paused") {
                Text(
                    "Descargas en pausa: ${active.size} esperando.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                    if (filter == DownloadFilter.ALL) {
                        Text("Aún no has descargado nada", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Abre una playlist o un álbum y pulsa «Descargar», o usa «Descargar» en el menú de cualquier canción.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Nada por aquí", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(shown, key = { it.id }) { item ->
                DownloadRow(
                    item = item,
                    playing = item.id == ctx.playingId,
                    onPlay = if (item.status == DownloadStatus.DONE) {
                        { ctx.player.playTracks(doneTracks, doneTracks.indexOf(item.track).coerceAtLeast(0), "downloads") }
                    } else null,
                    onRetry = { manager.retry(item.id) },
                    onRemove = { manager.remove(item.id) },
                )
            }
        }
        item(key = "end") { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, playing: Boolean, onPlay: (() -> Unit)?, onRetry: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val busy = item.status == DownloadStatus.RESOLVING || item.status == DownloadStatus.SAVING
    val pct = (item.progress * 100).toInt().coerceIn(0, 100)
    val statusText = when (item.status) {
        DownloadStatus.RESOLVING -> "Resolviendo…"
        DownloadStatus.QUEUED -> "En cola"
        DownloadStatus.SAVING -> "Guardando · $pct %"
        DownloadStatus.DONE -> "Descargado"
        DownloadStatus.ERROR -> item.error?.takeIf { it.isNotBlank() } ?: "Error"
    }
    val statusColor = when (item.status) {
        DownloadStatus.ERROR -> colors.error
        DownloadStatus.DONE -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onPlay != null) Modifier.clickable(onClick = onPlay) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(item.track.thumbnail, Modifier.size(48.dp), RoundedCornerShape(6.dp), sizePx = 144)
        Column(Modifier.weight(1f)) {
            Text(
                item.track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.track.artists,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (busy) {
                Spacer(Modifier.height(4.dp))
                if (item.status == DownloadStatus.SAVING) {
                    LinearProgressIndicator(progress = { item.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                }
            }
        }
        val format = listOfNotNull(
            item.format,
            if (item.status == DownloadStatus.DONE && item.bytes > 0) formatBytes(item.bytes) else null,
        ).joinToString(" · ")
        if (format.isNotEmpty()) {
            Text(format, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (item.status == DownloadStatus.ERROR) {
            IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "Reintentar", tint = colors.onSurfaceVariant) }
        }
        IconButton(onClick = onRemove, enabled = !busy) {
            Icon(
                Icons.Rounded.Delete,
                if (item.status == DownloadStatus.DONE) "Borrar del dispositivo" else "Quitar de la cola",
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Encola las canciones de las listas importadas de Spotify. El repositorio no expone qué lista local
 * viene de cada id remoto, así que se recorren todas las listas locales y se toman las canciones con
 * `spotifyId` (sólo las importadas de Spotify lo traen). Devuelve cuántas canciones se enviaron.
 */
suspend fun enqueueSpotifyImports(ctx: ScreenContext, manager: DownloadManager): Int {
    val tracks = ctx.repository.getPlaylists().first()
        .flatMap { ctx.repository.getPlaylistById(it.id)?.tracks.orEmpty() }
        .filter { it.spotifyId != null && it.videoId != null }
        .distinctBy { it.videoId }
    tracks.chunked(500).forEach { manager.enqueue(it) }
    return tracks.size
}

/** Botón «Descargar» de las cabeceras de álbum / lista / me gusta: encola todo y pasa a «En descargas». */
@Composable
fun DownloadAllButton(tracks: List<Track>, ctx: ScreenContext) {
    val manager = koinInject<DownloadManager>()
    val state by manager.state.collectAsState()
    val ids = remember(tracks) { tracks.mapNotNull { it.videoId } }
    val present = remember(state.items) { state.items.mapTo(HashSet()) { it.id } }
    val allSent = ids.isNotEmpty() && ids.all { it in present }
    FilledTonalButton(
        onClick = {
            if (allSent) {
                ctx.navigator.push(Screen.Downloads)
            } else {
                manager.enqueue(tracks)
                ctx.toast(if (ids.size == 1) "1 canción a descargas" else "${ids.size} canciones a descargas")
            }
        },
        enabled = ids.isNotEmpty(),
    ) {
        Icon(if (allSent) Icons.Rounded.DownloadDone else Icons.Rounded.Download, null)
        Spacer(Modifier.size(6.dp))
        Text(if (allSent) "En descargas" else "Descargar")
    }
}
