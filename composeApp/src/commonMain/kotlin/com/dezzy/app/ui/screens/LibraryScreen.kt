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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.GridView
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
import com.dezzy.app.ui.components.DezzyTopBar
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.downloads.DownloadAllButton
import com.dezzy.app.ui.navigation.SettingsPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class LibraryTab(val label: String) {
    PLAYLISTS("Listas de reproducción"),
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
    // Pestaña inicial (Ajustes › Apariencia); GENRES abre otra pantalla, así que cae en Listas.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val saved = ctx.prefs.value(com.dezzy.app.ui.screens.settings.prefs.DefaultLibraryTabKey, "PLAYLISTS").first()
        tab = LibraryTab.entries.firstOrNull { it.name == saved && it != LibraryTab.GENRES } ?: LibraryTab.PLAYLISTS
    }
    val libraryTagsOn by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.LibraryTagsKey, true)
    val gridCellSize by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.GridCellSizeKey, "SMALL")
    // SMALL = 3 columnas, LARGE = 2 columnas.
    val gridCols = if (gridCellSize == "LARGE") 2 else 3
    val showLiked by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.ShowLikedAutoPlaylistKey, true)
    val showDownloaded by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.ShowDownloadedAutoPlaylistKey, true)
    val showTop by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.ShowTopAutoPlaylistKey, true)
    val savedAlbums by remember { com.dezzy.app.ui.screens.library.localItems(ctx.prefs, com.dezzy.app.ui.screens.library.SavedAlbumsKey) }.collectAsState(null)
    val subscribedArtists by remember { com.dezzy.app.ui.screens.library.localItems(ctx.prefs, com.dezzy.app.ui.screens.library.SubscribedArtistsKey) }.collectAsState(null)
    var creating by remember { mutableStateOf(false) }
    // Filtros y orden de las pestañas Álbumes, Artistas y Canciones (como en la app actual).
    var songFilter by rememberSaveable { mutableIntStateOf(0) }
    var albumFilter by rememberSaveable { mutableIntStateOf(1) }
    var artistFilter by rememberSaveable { mutableIntStateOf(0) }
    var songSort by rememberSaveable { mutableIntStateOf(0) }
    var albumSort by rememberSaveable { mutableIntStateOf(0) }
    var artistSort by rememberSaveable { mutableIntStateOf(0) }
    var songAsc by rememberSaveable { mutableStateOf(false) }
    var albumAsc by rememberSaveable { mutableStateOf(false) }
    var artistAsc by rememberSaveable { mutableStateOf(false) }
    var itemsGrid by rememberSaveable { mutableStateOf(true) }
    // «Tu biblioteca»: me gusta + canciones de tus listas, sin repetir.
    val libraryTracks by androidx.compose.runtime.produceState<List<com.dezzy.app.core.model.Track>?>(null, playlists, favorites) {
        val fav = favorites ?: return@produceState
        val fromLists = playlists.flatMap { p -> runCatching { ctx.repository.getPlaylistById(p.id)?.tracks.orEmpty() }.getOrDefault(emptyList()) }
        value = (fav + fromLists).filter { it.videoId != null }.distinctBy { it.videoId }
    }
    val downloadManager = koinInject<com.dezzy.app.feature.downloads.DownloadManager>()
    val downloadState by downloadManager.state.collectAsState()
    val downloadedTracks = remember(downloadState) {
        downloadState.items.filter { it.status == com.dezzy.app.feature.downloads.DownloadStatus.DONE }.map { it.track }
    }
    // Artistas de las canciones de la biblioteca, con cuántas canciones tiene cada uno.
    val libraryArtists = remember(libraryTracks) {
        libraryTracks?.let { list ->
            list.groupBy { it.artists.substringBefore(",").substringBefore(" & ").trim() }
                .filterKeys { it.isNotEmpty() }
                .map { (name, ts) ->
                    com.dezzy.app.core.model.MusicItem(
                        id = "name:$name",
                        type = com.dezzy.app.core.model.MusicItemType.ARTIST,
                        title = name,
                        subtitle = "${ts.size} ${if (ts.size == 1) "canción" else "canciones"}",
                        thumbnail = ts.first().thumbnail,
                    )
                }
        }
    }
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val hasYouTube = SessionProvider.YOUTUBE in sessions
    val hasSpotify = SessionProvider.SPOTIFY in sessions

    val colors = MaterialTheme.colorScheme
    val pills = listOfNotNull(
        if (showLiked) LibraryPill("Favorito", Icons.Rounded.Favorite, colors.primaryContainer, colors.onPrimaryContainer) { ctx.navigator.push(Screen.Liked) } else null,
        if (showDownloaded) LibraryPill("Descargado", Icons.Rounded.SaveAlt, colors.tertiaryContainer, colors.onTertiaryContainer) { ctx.navigator.push(Screen.Downloads) } else null,
        if (showTop) LibraryPill("Mi top", Icons.AutoMirrored.Rounded.TrendingUp, colors.surfaceContainerHighest, colors.onSurfaceVariant) { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Stats) } else null,
    )
    // Orden y vista de la rejilla (se recuerdan entre sesiones).
    var sort by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.library.LibrarySortKey, 0)
    var gridView by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.library.LibraryGridViewKey, true)
    var sortMenu by remember { mutableStateOf(false) }
    val sortLabels = listOf("Orden personalizado", "Nombre (A-Z)", "Más canciones")
    val sortedPlaylists = when (sort) {
        1 -> playlists.sortedBy { it.title.lowercase() }
        2 -> playlists.sortedByDescending { it.trackCount }
        else -> playlists
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { DezzyTopBar(ctx) }
        if (pills.isNotEmpty()) item {
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
                        text = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (t == tab) FontWeight.Bold else FontWeight.SemiBold) },
                    )
                }
            }
        }

        when (tab) {
            LibraryTab.PLAYLISTS -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Text(
                                sortLabels[sort],
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(max = 110.dp).clickable { sortMenu = true }.padding(vertical = 8.dp),
                            )
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                sortLabels.forEachIndexed { i, l ->
                                    DropdownMenuItem(text = { Text(l) }, onClick = { sort = i; sortMenu = false })
                                }
                            }
                        }
                        Text(
                            "${playlists.size} listas de reproducción",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        // Como en la app actual: el icono muestra la vista en uso.
                        IconButton(onClick = { gridView = !gridView }) {
                            Icon(if (gridView) Icons.Outlined.GridView else Icons.Rounded.ViewList, if (gridView) "Ver como lista" else "Ver como rejilla")
                        }
                    }
                }
                // Tarjetas fijas + listas locales, en filas de dos (rejilla) o de una (lista).
                val cards = buildList<@Composable (Modifier) -> Unit> {
                    if (showLiked) add { m ->
                        LibraryCard(
                            m, title = "Tus me gusta",
                            subtitle = favorites?.let { "${it.size} canciones" } ?: "",
                            thumbnail = null, placeholder = Icons.Rounded.Favorite, highlight = true,
                            onClick = { ctx.navigator.push(Screen.Liked) },
                        )
                    }
                    sortedPlaylists.forEach { p ->
                        add { m ->
                            LibraryPlaylistGridItem(ctx, p, m)
                        }
                    }
                }
                items(cards.chunked(if (gridView) 2 else 1)) { rowCards ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowCards.forEach { it(Modifier.weight(1f)) }
                        if (gridView && rowCards.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Text(
                        "Nueva lista de reproducción",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
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
                if (libraryTagsOn) item(key = "songs:chips") {
                    LibraryFilterChips(listOf("Me gustan", "Tu biblioteca", "Descargados"), songFilter) { songFilter = it }
                }
                val tracks = when (songFilter) {
                    0 -> favorites
                    1 -> libraryTracks
                    else -> downloadedTracks
                }?.let { list -> sortTracks(list, songSort, songAsc) }
                item(key = "songs:sort") {
                    LibrarySortRow(
                        labels = TrackSortLabels, sort = songSort, ascending = songAsc,
                        count = tracks?.let { "${it.size} ${if (it.size == 1) "canción" else "canciones"}" } ?: "",
                        onSort = { songSort = it }, onToggleOrder = { songAsc = !songAsc },
                    )
                }
                item(key = "songs:add") {
                    Surface(
                        onClick = { ctx.navigator.select(com.dezzy.app.ui.navigation.Tab.SEARCH) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Añadir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                when {
                    tracks == null -> loadingRows()
                    tracks.isEmpty() -> item(key = "songs:empty") { LibraryEmptyState(Icons.Rounded.MusicNote, "Las canciones de la biblioteca aparecerán aquí") }
                    else -> itemsIndexed(tracks, key = { i, t -> "s:$i:${t.videoId}" }) { i, t ->
                        TrackRow(t, onClick = { ctx.player.playTracks(tracks, i, "liked") }, playing = t.videoId == ctx.playingId)
                    }
                }
            }
            LibraryTab.ALBUMS -> {
                if (libraryTagsOn) item(key = "albums:chips") {
                    LibraryFilterChips(listOf("Me gustan", "Tu biblioteca", "Descargados"), albumFilter) { albumFilter = it }
                }
                val list = when (albumFilter) {
                    0, 1 -> savedAlbums
                    else -> emptyList()
                }?.let { l -> sortItems(l, albumSort, albumAsc) }
                item(key = "albums:sort") {
                    LibrarySortRow(
                        labels = ItemSortLabels, sort = albumSort, ascending = albumAsc,
                        count = list?.let { "${it.size} ${if (it.size == 1) "álbum" else "álbumes"}" } ?: "",
                        onSort = { albumSort = it }, onToggleOrder = { albumAsc = !albumAsc },
                        gridView = itemsGrid, onToggleView = { itemsGrid = !itemsGrid },
                    )
                }
                when {
                    list == null -> loadingRows()
                    list.isEmpty() -> item(key = "albums:empty") { LibraryEmptyState(Icons.Rounded.Album, "Los álbumes de la biblioteca aparecerán aquí") }
                    itemsGrid -> items(list.chunked(gridCols), key = { row -> "ar:" + row.first().id }) { row ->
                        LibraryItemGridRow(row, gridCols) { item -> LibraryAlbumCell(item, liked = albumFilter != 2, onPlay = { ctx.open(item) }) { ctx.open(item) } }
                    }
                    else -> items(list, key = { "album:" + it.id }) { item -> ItemRow(item) { ctx.open(item) } }
                }
            }
            LibraryTab.ARTISTS -> {
                if (libraryTagsOn) item(key = "artists:chips") {
                    LibraryFilterChips(listOf("Me gustan", "Tu biblioteca"), artistFilter) { artistFilter = it }
                }
                val list = (if (artistFilter == 0) subscribedArtists else libraryArtists)?.let { l -> sortItems(l, artistSort, artistAsc) }
                item(key = "artists:sort") {
                    LibrarySortRow(
                        labels = ItemSortLabels, sort = artistSort, ascending = artistAsc,
                        count = list?.let { "${it.size} ${if (it.size == 1) "artista" else "artistas"}" } ?: "",
                        onSort = { artistSort = it }, onToggleOrder = { artistAsc = !artistAsc },
                        gridView = itemsGrid, onToggleView = { itemsGrid = !itemsGrid },
                    )
                }
                val openArtist: (com.dezzy.app.core.model.MusicItem) -> Unit = { item ->
                    if (item.id.startsWith("name:")) ctx.scope.launch {
                        ctx.client.findArtistId(item.title).getOrNull()?.let { ctx.navigator.push(Screen.Artist(it)) }
                            ?: ctx.toast("No he encontrado a ${item.title}")
                    } else ctx.open(item)
                }
                when {
                    list == null -> loadingRows()
                    list.isEmpty() -> item(key = "artists:empty") { LibraryEmptyState(Icons.Rounded.PersonSearch, "Los artistas de la biblioteca aparecerán aquí") }
                    itemsGrid -> items(list.chunked(gridCols), key = { row -> "rr:" + row.first().id }) { row ->
                        LibraryItemGridRow(row, gridCols) { item -> LibraryArtistCell(item) { openArtist(item) } }
                    }
                    else -> items(list, key = { "artist:" + it.id }) { item -> ItemRow(item) { openArtist(item) } }
                }
            }
            LibraryTab.GENRES -> Unit
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
    // Canciones: aleatorio flotante encima del minirreproductor, como en la app actual.
    if (tab == LibraryTab.SONGS) {
        val shuffleList = when (songFilter) {
            0 -> favorites
            1 -> libraryTracks
            else -> downloadedTracks
        }.orEmpty()
        if (shuffleList.isNotEmpty()) {
            com.dezzy.app.ui.components.glass.DezzyFloatingActionButton(
                onClick = { ctx.player.playTracks(shuffleList.shuffled(), 0, "liked") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) { Icon(Icons.Rounded.Shuffle, "Aleatorio") }
        }
    }
    }

    if (creating) {
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name -> ctx.scope.launch { ctx.repository.createPlaylist(name) } },
        )
    }
}

private val TrackSortLabels = listOf("Fecha añadida", "Nombre", "Artista")
private val ItemSortLabels = listOf("Fecha añadida", "Nombre")

private fun sortTracks(list: List<com.dezzy.app.core.model.Track>, sort: Int, ascending: Boolean): List<com.dezzy.app.core.model.Track> {
    val sorted = when (sort) {
        1 -> list.sortedBy { it.title.lowercase() }
        2 -> list.sortedBy { it.artists.lowercase() }
        else -> list
    }
    // «Fecha añadida» llega del más reciente al más antiguo; los demás, de la A a la Z.
    return if (sort == 0) (if (ascending) sorted.asReversed() else sorted) else (if (ascending) sorted else sorted.asReversed())
}

private fun sortItems(list: List<com.dezzy.app.core.model.MusicItem>, sort: Int, ascending: Boolean): List<com.dezzy.app.core.model.MusicItem> {
    val sorted = if (sort == 1) list.sortedBy { it.title.lowercase() } else list
    return if (sort == 0) (if (ascending) sorted.asReversed() else sorted) else (if (ascending) sorted else sorted.asReversed())
}

/** Chips de filtro: el elegido en verde con ✓, los demás en gris. */
@Composable
private fun LibraryFilterChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                onClick = { onSelect(i) },
                shape = CircleShape,
                color = if (on) Color(0xFF1ED760) else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) Color.Black else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (on) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** «Fecha añadida ↓ · N elementos · ⊞»: orden (menú), sentido (flecha), recuento y vista. */
@Composable
private fun LibrarySortRow(
    labels: List<String>,
    sort: Int,
    ascending: Boolean,
    count: String,
    onSort: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    gridView: Boolean? = null,
    onToggleView: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Text(
                labels[sort.coerceIn(0, labels.lastIndex)],
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { menu = true }.padding(vertical = 12.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                labels.forEachIndexed { i, l -> DropdownMenuItem(text = { Text(l) }, onClick = { onSort(i); menu = false }) }
            }
        }
        IconButton(onClick = onToggleOrder) {
            Icon(
                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                if (ascending) "Ascendente" else "Descendente",
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        if (gridView != null) {
            IconButton(onClick = onToggleView) {
                Icon(if (gridView) Icons.Outlined.GridView else Icons.Rounded.ViewList, if (gridView) "Ver como lista" else "Ver como rejilla")
            }
        } else Spacer(Modifier.width(12.dp))
    }
}

/** Vacío de pestaña: icono en un círculo y texto gris centrado. */
@Composable
private fun LibraryEmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun LibraryItemGridRow(
    row: List<com.dezzy.app.core.model.MusicItem>,
    cols: Int,
    cell: @Composable (com.dezzy.app.core.model.MusicItem) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (cols >= 3) 12.dp else 24.dp)) {
        row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
        // Rellena los huecos de la última fila para que las celdas no se estiren.
        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Celda de álbum: portada con ▶ abajo a la derecha, título y artista (♥ si te gusta). */
@Composable
private fun LibraryAlbumCell(item: com.dezzy.app.core.model.MusicItem, liked: Boolean, onPlay: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverImage(item.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(6.dp), sizePx = 480)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (liked) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            val artist = item.subtitle.split(" • ").firstOrNull { it.isNotBlank() && it != "Álbum" && it != "Album" && it.toIntOrNull() == null } ?: item.subtitle
            Text(artist, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Celda de artista: foto redonda, nombre y recuento de canciones. */
@Composable
private fun LibraryArtistCell(item: com.dezzy.app.core.model.MusicItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        CoverImage(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), CircleShape, sizePx = 480)
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
            // Círculo claro con el icono oscuro, como en la app actual.
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f), modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(pill.icon, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(pill.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
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
