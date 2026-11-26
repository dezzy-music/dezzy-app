/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.SearchResult
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.source.youtube.SearchSuggestions
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.ItemCard
import com.dezzy.app.ui.components.SectionHeader
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.components.TrackRowPlaceholder
import com.dezzy.app.ui.navigation.Navigator
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.Tab
import kotlinx.coroutines.delay

/**
 * Contenido de la pestaña activa o de la pantalla apilada encima. Las pantallas de aquí son el
 * armazón de la 5a; en la 5b se sustituyen por el Inicio, Buscar, Biblioteca y fichas de los bocetos.
 */
@Composable
fun TabContent(navigator: Navigator, player: PlayerController, client: YouTubeMusicClient, state: PlaybackState) {
    val ctx = ScreenContext(navigator, player, client, state.queue.current?.track?.videoId)
    when (val top = navigator.top) {
        is Screen.Album -> AlbumScreen(top.id, ctx)
        is Screen.Artist -> ArtistScreen(top.id, ctx)
        is Screen.Playlist -> PlaylistScreen(top.id, ctx)
        is Screen.Search -> SearchScreen(ctx, top.query)
        null -> when (navigator.tab) {
            Tab.HOME -> HomeScreen(ctx)
            Tab.SEARCH -> SearchScreen(ctx, "")
            Tab.LIBRARY -> ComingSoon("Biblioteca", "Tus canciones, álbumes, artistas y listas llegan en la 5b.")
            Tab.TOGETHER -> ComingSoon("Juntos", "Escuchar juntos, Connect y la red social llegan en sus fases.")
        }
    }
}

/** Lo que toda pantalla necesita para navegar y reproducir. */
class ScreenContext(
    val navigator: Navigator,
    val player: PlayerController,
    val client: YouTubeMusicClient,
    val playingId: String?,
) {
    fun open(item: MusicItem) {
        when (item.type) {
            MusicItemType.SONG, MusicItemType.VIDEO -> player.playRadio(
                Track(videoId = item.id, title = item.title, artists = item.subtitle.substringBefore(" • "), thumbnail = item.thumbnail),
                source = "radio:${item.title}",
            )
            MusicItemType.ALBUM -> navigator.push(Screen.Album(item.id))
            MusicItemType.PLAYLIST -> navigator.push(Screen.Playlist(item.id))
            MusicItemType.ARTIST -> navigator.push(Screen.Artist(item.id))
            MusicItemType.OTHER -> Unit
        }
    }
}

/** Carga asíncrona sencilla: null mientras carga. */
private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Composable
private fun <T> rememberLoad(key: Any?, block: suspend () -> Result<T>): Load<T> {
    val result by produceState<Load<T>>(Load.Loading, key) {
        value = Load.Loading
        value = block().fold({ Load.Done(it) }, { Load.Failed(it.message ?: "Error") })
    }
    return result
}

@Composable
private fun TopSpacer() = Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

@Composable
private fun ScreenTitle(text: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack == null) 16.dp else 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
        Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun LazyListScope.loadingRows() = items(8) { TrackRowPlaceholder() }

private fun LazyListScope.errorRow(message: String) = item {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
}

private fun LazyListScope.carousel(section: MusicSection, ctx: ScreenContext) {
    item(key = "h:${section.title}") { SectionHeader(section.title, strapline = section.strapline) }
    item(key = "r:${section.title}") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(section.items, key = { it.type.name + it.id }) { ItemCard(it, onClick = { ctx.open(it) }) }
        }
    }
}

// ───────────────────────────── Inicio ─────────────────────────────

@Composable
private fun HomeScreen(ctx: ScreenContext) {
    val home = rememberLoad(Unit) { ctx.client.getHome() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TopSpacer() }
        item { ScreenTitle("Inicio") }
        when (home) {
            Load.Loading -> loadingRows()
            is Load.Failed -> errorRow(home.message)
            is Load.Done -> home.value.forEach { carousel(it, ctx) }
        }
    }
}

// ───────────────────────────── Buscar ─────────────────────────────

@Composable
private fun SearchScreen(ctx: ScreenContext, initial: String) {
    var query by rememberSaveable(initial) { mutableStateOf(initial) }
    var submitted by rememberSaveable(initial) { mutableStateOf(initial) }
    var suggestions by remember { mutableStateOf(SearchSuggestions()) }

    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) { suggestions = SearchSuggestions(); return@LaunchedEffect }
        delay(250)
        ctx.client.getSearchSuggestions(query).onSuccess { suggestions = it }
    }
    val results = if (submitted.isBlank()) null else rememberLoad(submitted) { ctx.client.search(submitted) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TopSpacer() }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Canciones, artistas, álbumes…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Rounded.Close, "Borrar") }
                },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submitted = query.trim() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            )
        }
        if (query != submitted && query.isNotBlank()) {
            items(suggestions.queries, key = { "q:$it" }) { q ->
                ListItem(
                    headlineContent = { Text(q) },
                    leadingContent = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.clickableNoRipple { query = q; submitted = q },
                )
            }
            items(suggestions.tracks, key = { "t:${it.videoId}" }) { t ->
                TrackRow(t, onClick = { ctx.player.playRadio(t, "radio:${t.title}") }, playing = t.videoId == ctx.playingId)
            }
            items(suggestions.items, key = { "i:${it.type}${it.id}" }) { item -> ItemRow(item) { ctx.open(item) } }
        } else when (results) {
            null -> item {
                Text(
                    "Busca lo que quieras escuchar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Load.Loading -> loadingRows()
            is Load.Failed -> errorRow(results.message)
            is Load.Done -> searchResults(results.value, ctx)
        }
    }
}

private fun LazyListScope.searchResults(result: SearchResult, ctx: ScreenContext) {
    if (result.songs.isNotEmpty()) {
        item { SectionHeader("Canciones") }
        itemsIndexed(result.songs, key = { i, t -> "s$i:${t.videoId}" }) { i, t ->
            TrackRow(t, onClick = { ctx.player.playTracks(result.songs, i, "search") }, playing = t.videoId == ctx.playingId)
        }
    }
    listOf("Artistas" to result.artists, "Álbumes" to result.albums, "Listas" to result.playlists).forEach { (title, list) ->
        if (list.isNotEmpty()) carousel(MusicSection(title, items = list), ctx)
    }
}

@Composable
private fun ItemRow(item: MusicItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            CoverImage(item.thumbnail, Modifier.size(48.dp), if (item.type == MusicItemType.ARTIST) CircleShape else RoundedCornerShape(6.dp), sizePx = 144)
        },
        modifier = Modifier.clickableNoRipple(onClick),
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

// ───────────────────────────── Fichas ─────────────────────────────

@Composable
private fun CollectionHeader(title: String, subtitle: String?, thumbnail: String?, round: Boolean, onPlay: (() -> Unit)?, ctx: ScreenContext) {
    Column {
        TopSpacer()
        ScreenTitle("", onBack = { ctx.navigator.back() })
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CoverImage(thumbnail, Modifier.size(200.dp), if (round) CircleShape else RoundedCornerShape(16.dp), sizePx = 720)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
            if (onPlay != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, null); Text("Reproducir") }
            }
        }
    }
}

@Composable
private fun AlbumScreen(id: String, ctx: ScreenContext) {
    val album = rememberLoad(id) { ctx.client.getAlbum(id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        when (album) {
            Load.Loading -> { item { TopSpacer() }; loadingRows() }
            is Load.Failed -> { item { TopSpacer() }; errorRow(album.message) }
            is Load.Done -> {
                val a = album.value
                item {
                    CollectionHeader(a.title, listOfNotNull(a.artist, a.year).joinToString(" • "), a.thumbnail, false,
                        a.tracks.takeIf { it.isNotEmpty() }?.let { { ctx.player.playTracks(it, 0, "album:${a.title}") } }, ctx)
                }
                itemsIndexed(a.tracks, key = { i, t -> "$i:${t.videoId}" }) { i, t ->
                    TrackRow(t, onClick = { ctx.player.playTracks(a.tracks, i, "album:${a.title}") }, playing = t.videoId == ctx.playingId, number = i + 1, showCover = false)
                }
            }
        }
    }
}

@Composable
private fun PlaylistScreen(id: String, ctx: ScreenContext) {
    val playlist = rememberLoad(id) { ctx.client.getPlaylist(id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        when (playlist) {
            Load.Loading -> { item { TopSpacer() }; loadingRows() }
            is Load.Failed -> { item { TopSpacer() }; errorRow(playlist.message) }
            is Load.Done -> {
                val p = playlist.value
                item {
                    CollectionHeader(p.title, p.author, p.thumbnail, false,
                        p.tracks.takeIf { it.isNotEmpty() }?.let { { ctx.player.playTracks(it, 0, "playlist:${p.title}") } }, ctx)
                }
                itemsIndexed(p.tracks, key = { i, t -> "$i:${t.videoId}" }) { i, t ->
                    TrackRow(t, onClick = { ctx.player.playTracks(p.tracks, i, "playlist:${p.title}") }, playing = t.videoId == ctx.playingId)
                }
            }
        }
    }
}

@Composable
private fun ArtistScreen(id: String, ctx: ScreenContext) {
    val artist = rememberLoad(id) { ctx.client.getArtist(id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        when (artist) {
            Load.Loading -> { item { TopSpacer() }; loadingRows() }
            is Load.Failed -> { item { TopSpacer() }; errorRow(artist.message) }
            is Load.Done -> {
                val a = artist.value
                item {
                    CollectionHeader(a.name, a.listeners, a.thumbnail, true,
                        a.songs.takeIf { it.isNotEmpty() }?.let { { ctx.player.playTracks(it, 0, "artist:${a.name}") } }, ctx)
                }
                if (a.songs.isNotEmpty()) {
                    item { SectionHeader("Canciones") }
                    itemsIndexed(a.songs, key = { i, t -> "$i:${t.videoId}" }) { i, t ->
                        TrackRow(t, onClick = { ctx.player.playTracks(a.songs, i, "artist:${a.name}") }, playing = t.videoId == ctx.playingId)
                    }
                }
                a.shelves.forEach { carousel(it, ctx) }
                a.description?.let { d ->
                    item { SectionHeader("Acerca de") }
                    item { Text(d, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
            }
        }
    }
}

// ───────────────────────────── Pendientes ─────────────────────────────

@Composable
private fun ComingSoon(title: String, message: String) {
    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle(title)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
