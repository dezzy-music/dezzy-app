/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.downloads.DownloadAllButton
import com.dezzy.app.ui.navigation.SettingsPage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class LibraryTab(val label: String) {
    PLAYLISTS("Listas"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    SONGS("Canciones"),
    GENRES("Géneros"),
}

private class LibraryPill(
    val title: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/** Biblioteca (fase 4, boceto B): píldoras de accesos, pestañas por tipo y rejilla de listas. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(ctx: ScreenContext) {
    val playlists by ctx.repository.getPlaylists().collectAsState(emptyList())
    val favorites by ctx.repository.getFavorites().collectAsState(null)
    var tab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }
    var creating by remember { mutableStateOf(false) }
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val hasYouTube = SessionProvider.YOUTUBE in sessions
    val hasSpotify = SessionProvider.SPOTIFY in sessions

    val colors = MaterialTheme.colorScheme
    val pills = listOf(
        LibraryPill("Me gusta", Icons.Rounded.Favorite, colors.primaryContainer, colors.onPrimaryContainer) { ctx.navigator.push(Screen.Liked) },
        LibraryPill("Historial", Icons.Rounded.History, colors.secondaryContainer, colors.onSecondaryContainer) { ctx.navigator.push(Screen.History) },
        LibraryPill("Descargas", Icons.Rounded.SaveAlt, colors.tertiaryContainer, colors.onTertiaryContainer) { ctx.navigator.push(Screen.Downloads) },
        LibraryPill("Estadísticas", Icons.Outlined.QueryStats, colors.surfaceContainerHighest, colors.onSurfaceVariant) { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Stats) },
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopSpacer()
            ScreenTitle("Biblioteca", actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Crear lista") }
            })
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                pills.forEach { LibraryPillButton(it) }
            }
        }
        item {
            @OptIn(ExperimentalMaterial3Api::class)
            PrimaryScrollableTabRow(
                selectedTabIndex = tab.ordinal,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                LibraryTab.entries.forEach { t ->
                    Tab(
                        selected = t == tab,
                        onClick = {
                            if (t == LibraryTab.GENRES) ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.LibraryGenres)
                            else tab = t
                        },
                        text = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        when (tab) {
            LibraryTab.PLAYLISTS -> {
                // Tarjetas fijas + listas locales, en filas de dos (rejilla).
                val cards = buildList<@Composable (Modifier) -> Unit> {
                    add { m ->
                        LibraryCard(
                            m, title = "Tus me gusta",
                            subtitle = favorites?.let { "${it.size} canciones" } ?: "",
                            thumbnail = null, placeholder = Icons.Rounded.Favorite, highlight = true,
                            onClick = { ctx.navigator.push(Screen.Liked) },
                        )
                    }
                    playlists.forEach { p ->
                        add { m ->
                            LibraryPlaylistGridItem(ctx, p, m)
                        }
                    }
                }
                items(cards.chunked(2)) { rowCards ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowCards.forEach { it(Modifier.weight(1f)) }
                        if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Text(
                        "Listas de YouTube Music y Spotify",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                ctx.navigator.push(
                                    if (hasYouTube) Screen.AccountPlaylists else Screen.Settings(SettingsPage.YOUTUBE_LOGIN),
                                )
                            }
                            .padding(16.dp),
                    )
                }
                item {
                    Text(
                        "Importar desde Spotify",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                ctx.navigator.push(
                                    Screen.Settings(if (hasSpotify) SettingsPage.SPOTIFY_IMPORT else SettingsPage.SPOTIFY_LOGIN),
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            LibraryTab.SONGS -> {
                val tracks = favorites
                when {
                    tracks == null -> loadingRows()
                    tracks.isEmpty() -> emptyRow("Todavía no tienes canciones que te gusten.")
                    else -> itemsIndexed(tracks) { i, t ->
                        TrackRow(t, onClick = { ctx.player.playTracks(tracks, i, "liked") }, playing = t.videoId == ctx.playingId)
                    }
                }
            }
            LibraryTab.ALBUMS, LibraryTab.ARTISTS, LibraryTab.GENRES ->
                emptyRow("Llega en la fase 6b con la sincronización de la cuenta")
        }
        item { Spacer(Modifier.size(24.dp)) }
    }

    if (creating) {
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name -> ctx.scope.launch { ctx.repository.createPlaylist(name) } },
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva lista") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nombre") })
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()); onDismiss() },
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun LibraryPillButton(pill: LibraryPill) {
    Surface(onClick = pill.onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 40.dp).padding(start = 6.dp, end = 16.dp),
        ) {
            Surface(shape = CircleShape, color = pill.container, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(pill.icon, null, tint = pill.content, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(pill.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/** Tarjeta cuadrada de la rejilla: carátula (o icono) y dos líneas de texto. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    thumbnail: String?,
    placeholder: ImageVector,
    highlight: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape), contentAlignment = Alignment.Center) {
            if (thumbnail != null) {
                CoverImage(thumbnail, Modifier.fillMaxSize(), shape, sizePx = 480)
            } else {
                Surface(
                    shape = shape,
                    color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            placeholder, null,
                            tint = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

/** Celda de lista local para la rejilla: toca para abrir, pulsación larga o ⋮ para el menú. */
@Composable
private fun LibraryPlaylistGridItem(ctx: ScreenContext, playlist: Playlist, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    LibraryCard(
        modifier,
        title = playlist.title,
        subtitle = "${playlist.trackCount} canciones",
        thumbnail = playlist.thumbnail,
        placeholder = Icons.Rounded.QueueMusic,
        onClick = { ctx.navigator.push(Screen.LocalPlaylist(playlist.id)) },
        onLongClick = { menu = true },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            menu = false
                            ctx.scope.launch { ctx.repository.deletePlaylist(playlist.id) }
                        },
                    )
                }
            }
        },
    )
}

@Composable
fun LikedScreen(ctx: ScreenContext) {
    val tracks by ctx.repository.getFavorites().collectAsState(null)
    val list = tracks
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            CollectionHeader(
                title = "Tus me gusta",
                subtitle = list?.let { "${it.size} canciones" },
                thumbnail = null,
                round = false,
                onBack = { ctx.navigator.back() },
                onPlay = list?.takeIf { it.isNotEmpty() }?.let { l -> { ctx.player.playTracks(l, 0, "liked") } },
                onShuffle = list?.takeIf { it.isNotEmpty() }?.let { l -> { ctx.player.playTracks(l.shuffled(), 0, "liked") } },
                extraActions = list?.takeIf { it.isNotEmpty() }?.let { l -> { DownloadAllButton(l, ctx) } },
                thumbnailContent = {
                    Box(
                        Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(96.dp))
                    }
                },
            )
        }
        when {
            list == null -> loadingRows()
            list.isEmpty() -> emptyRow("Todavía no tienes canciones que te gusten.")
            else -> itemsIndexed(list) { i, t ->
                TrackRow(t, onClick = { ctx.player.playTracks(list, i, "liked") }, playing = t.videoId == ctx.playingId)
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
fun HistoryScreen(ctx: ScreenContext) {
    val tracks by ctx.repository.getHistory(200).collectAsState(null)
    val list = tracks
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopSpacer()
            ScreenTitle("Historial", onBack = { ctx.navigator.back() }, actions = {
                TextButton(onClick = { confirm = true }, enabled = !list.isNullOrEmpty()) { Text("Borrar") }
            })
        }
        when {
            list == null -> loadingRows()
            list.isEmpty() -> emptyRow("Todavía no has escuchado nada.")
            else -> itemsIndexed(list) { _, t ->
                TrackRow(t, onClick = { ctx.player.playRadio(t, "radio:${t.title}") }, playing = t.videoId == ctx.playingId)
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Borrar historial") },
            text = { Text("Se borrará todo tu historial de reproducción. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    ctx.scope.launch { ctx.repository.clearHistory() }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun LocalPlaylistScreen(id: String, ctx: ScreenContext) {
    var version by remember { mutableIntStateOf(0) }
    val load = rememberLoad(id to version) {
        runCatching { ctx.repository.getPlaylistById(id) ?: error("No existe") }
    }
    LazyColumn(Modifier.fillMaxSize()) {
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
                item {
                    CollectionHeader(
                        title = p.title,
                        subtitle = "${tracks.size} canciones",
                        thumbnail = p.thumbnail ?: tracks.firstOrNull()?.thumbnail,
                        round = false,
                        onBack = { ctx.navigator.back() },
                        onPlay = tracks.takeIf { it.isNotEmpty() }?.let { l -> { ctx.player.playTracks(l, 0, "playlist:${p.id}") } },
                        onShuffle = tracks.takeIf { it.isNotEmpty() }?.let { l -> { ctx.player.playTracks(l.shuffled(), 0, "playlist:${p.id}") } },
                        extraActions = tracks.takeIf { it.isNotEmpty() }?.let { l -> { DownloadAllButton(l, ctx) } },
                    )
                }
                if (tracks.isEmpty()) {
                    emptyRow("Esta lista está vacía. Añade canciones desde el menú de cualquier pista.")
                } else {
                    itemsIndexed(tracks) { i, t ->
                        TrackRow(
                            t,
                            onClick = { ctx.player.playTracks(tracks, i, "playlist:${p.id}") },
                            playing = t.videoId == ctx.playingId,
                            onMore = t.videoId?.let { vid ->
                                {
                                    ctx.scope.launch {
                                        ctx.repository.removeTrackFromPlaylist(id, vid)
                                        version++
                                    }
                                    ctx.toast("Quitada de la lista")
                                }
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

/** Selector de lista fijada en la pantalla de inicio (ranura [slot]). */
@Composable
fun PinnedPlaylistDialog(ctx: ScreenContext, slot: Int, onDismiss: () -> Unit) {
    val playlists by ctx.repository.getPlaylists().collectAsState(emptyList())
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn {
                item {
                    Text(
                        "Seleccionar lista",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (playlists.isEmpty()) emptyRow("Aún no tienes listas propias.")
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ctx.scope.launch { ctx.prefs.setPinnedPlaylist(slot, playlist.id) }
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        CoverImage(playlist.thumbnail, Modifier.size(48.dp), RoundedCornerShape(4.dp), sizePx = 144)
                        Text(
                            playlist.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                    }
                }
            }
        }
    }
}
