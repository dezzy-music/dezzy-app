/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.navigation.Tab
import com.dezzy.app.ui.screens.detail.CircleIconButton
import com.dezzy.app.ui.screens.detail.coverGradient
import com.dezzy.app.ui.screens.detail.rememberCoverColor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val SUGGESTIONS_MAX = 65
private const val SUGGESTIONS_PAGE = 5

/** m:ss, o h:mm:ss si pasa de una hora. */
private fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}

/** Página de una lista propia: cabecera con degradado, acciones, canciones y «Tal vez te guste». */
@Composable
fun LocalPlaylistScreen(id: String, ctx: ScreenContext) {
    var version by remember { mutableIntStateOf(0) }
    val load = rememberLoad(id to version) {
        runCatching { ctx.repository.getPlaylistById(id) ?: error("No existe") }
    }
    val manager = koinInject<DownloadManager>()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var locked by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    // Tal vez te guste: radio de la última canción de la lista.
    val currentTracks = (load as? Load.Done)?.value?.tracks.orEmpty()
    // La semilla se fija al abrir: añadir sugerencias no recarga la lista de sugerencias.
    val seed = remember(id, currentTracks.isNotEmpty()) { currentTracks.lastOrNull()?.videoId }
    var suggestions by remember { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(seed) {
        suggestions = emptyList()
        page = 0
        if (seed.isNullOrEmpty()) return@LaunchedEffect
        val items = runCatching { ctx.client.getWatchNext(seed).getOrNull()?.items }.getOrNull().orEmpty()
        suggestions = items.filter { !it.videoId.isNullOrEmpty() }.take(SUGGESTIONS_MAX)
    }

    LazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        when (load) {
            is Load.Loading -> {
                item { TopSpacer(); ScreenTitle("", onBack = { ctx.navigator.back() }) }
                loadingRows()
            }
            is Load.Failed -> {
                item { TopSpacer(); ScreenTitle("", onBack = { ctx.navigator.back() }) }
                errorRow(load.message)
            }
            is Load.Done -> {
                val p = load.value
                val tracks = p.tracks
                val source = "playlist:$id"
                val coverUrl = p.thumbnail ?: tracks.firstOrNull()?.thumbnail
                val totalSeconds = tracks.sumOf { it.durationSeconds ?: 0L }

                item {
                    val gradient = coverGradient(rememberCoverColor(coverUrl))
                    Column(
                        Modifier.fillMaxWidth().background(gradient),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TopSpacer()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { ctx.navigator.back() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                searching = !searching
                                if (!searching) query = ""
                            }) { Icon(Icons.Rounded.Search, "Buscar en la lista", tint = Color.White) }
                        }
                        if (searching) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                label = { Text("Buscar en la lista") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        CoverImage(coverUrl, Modifier.size(200.dp), RoundedCornerShape(16.dp), sizePx = 600)
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoChip(
                                Icons.Rounded.MusicNote,
                                if (tracks.size == 1) "1 canción" else "${tracks.size} canciones",
                            )
                            InfoChip(Icons.Rounded.Schedule, formatClock(totalSeconds))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircleIconButton(
                                Icons.Rounded.Delete, "Eliminar lista", { confirmDelete = true },
                                tint = Color(0xFFFF8FB1),
                            )
                            WhitePill(Icons.Rounded.PlayArrow, "Reproducir", Modifier.size(width = 52.dp, height = 64.dp)) {
                                if (tracks.isNotEmpty()) ctx.player.playTracks(tracks, 0, source)
                            }
                            WhitePill(Icons.Rounded.Shuffle, "Aleatorio", Modifier.size(width = 52.dp, height = 44.dp)) {
                                if (tracks.isNotEmpty()) ctx.player.playTracks(tracks.shuffled(), 0, source)
                            }
                            CircleIconButton(Icons.Rounded.Download, "Descargar", {
                                if (tracks.isNotEmpty()) {
                                    manager.enqueue(tracks)
                                    ctx.toast("${tracks.size} a descargas")
                                }
                            })
                            CircleIconButton(Icons.Rounded.Translate, "Traducir", {
                                ctx.toast("Traducir títulos: próximamente")
                            })
                            CircleIconButton(Icons.Rounded.Edit, "Editar lista", { editing = true })
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .clickable {
                                        ctx.navigator.select(Tab.SEARCH)
                                        ctx.toast("Busca una canción y añádela con ⋮ → Añadir a lista")
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+ Añadir", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                                    .background(Color.White)
                                    .clickable {
                                        tracks.firstOrNull()?.let { ctx.player.playRadio(it, source) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.GraphicEq, "Radio", tint = Color.Black)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Orden personalizado",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            locked = !locked
                            ctx.toast(if (locked) "Orden bloqueado" else "Orden desbloqueado")
                        }) {
                            Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, "Bloquear orden")
                        }
                    }
                }

                if (tracks.isEmpty()) {
                    emptyRow("Esta lista está vacía. Añade canciones con + Añadir.")
                } else {
                    val q = query.trim()
                    val shown = if (q.isEmpty()) tracks else tracks.filter {
                        it.title.contains(q, ignoreCase = true) || it.artists.contains(q, ignoreCase = true)
                    }
                    items(shown) { t ->
                        TrackRow(
                            t,
                            onClick = { ctx.player.playTracks(tracks, tracks.indexOf(t).coerceAtLeast(0), source) },
                            playing = t.videoId == ctx.playingId,
                            extraMenu = t.videoId?.let { vid ->
                                listOf<Pair<String, () -> Unit>>(
                                    "Quitar de la lista" to {
                                        ctx.scope.launch {
                                            ctx.repository.removeTrackFromPlaylist(id, vid)
                                            version++
                                        }
                                        ctx.toast("Quitada de la lista")
                                    },
                                )
                            } ?: emptyList(),
                        )
                    }
                }

                // Tal vez te guste (sin las que ya están en la lista).
                val existing = tracks.mapNotNullTo(HashSet()) { it.videoId }
                val available = suggestions.filter { it.videoId !in existing }
                if (available.isNotEmpty()) {
                    val pages = (available.size + SUGGESTIONS_PAGE - 1) / SUGGESTIONS_PAGE
                    val cur = page.coerceIn(0, pages - 1)
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 22.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Tal vez te guste",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { page = (cur - 1).coerceAtLeast(0) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.ChevronLeft, "Anterior", Modifier.size(20.dp))
                            }
                            Text(
                                "${cur + 1} / $pages",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { page = (cur + 1).coerceAtMost(pages - 1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.ChevronRight, "Siguiente", Modifier.size(20.dp))
                            }
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                    items(available.drop(cur * SUGGESTIONS_PAGE).take(SUGGESTIONS_PAGE), key = { "sug:${it.videoId}" }) { t ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { ctx.player.playTracks(listOf(t), 0, source) }
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(t.thumbnail, Modifier.size(48.dp), RoundedCornerShape(6.dp), sizePx = 144)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(t.artists.takeIf { it.isNotBlank() }, t.durationSeconds?.let { formatClock(it) })
                                        .joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                ctx.scope.launch {
                                    ctx.repository.addTrackToPlaylist(id, t)
                                    suggestions = suggestions.filter { it.videoId != t.videoId }
                                    version++
                                }
                                ctx.toast("Añadida a la lista")
                            }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a la lista") }
                        }
                    }
                }

                if (confirmDelete) {
                    item {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("¿Eliminar lista?") },
                            text = { Text("Se borrará «${p.title}». No se puede deshacer.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    ctx.scope.launch { ctx.repository.deletePlaylist(id) }
                                    ctx.toast("Lista eliminada")
                                    ctx.navigator.back()
                                }) { Text("Eliminar") }
                            },
                            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
                        )
                    }
                }
                if (editing) {
                    item {
                        var name by remember(p.title) { mutableStateOf(p.title) }
                        AlertDialog(
                            onDismissRequest = { editing = false },
                            title = { Text("Editar lista") },
                            text = {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    label = { Text("Nombre") },
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = name.isNotBlank(),
                                    onClick = {
                                        editing = false
                                        ctx.scope.launch {
                                            ctx.repository.renamePlaylist(id, name.trim())
                                            version++
                                        }
                                    },
                                ) { Text("Guardar") }
                            },
                            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

/** Chip gris con icono y texto. */
@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Píldora blanca estrecha con un icono negro. */
@Composable
private fun WhitePill(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(26.dp), tint = Color.Black)
    }
}
