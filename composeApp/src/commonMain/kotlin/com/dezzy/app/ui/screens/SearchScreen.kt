/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.SearchResult
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.MoodItem
import com.dezzy.app.source.youtube.MoodSection
import com.dezzy.app.source.youtube.SearchSuggestions
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.source.spotify.SpotifyFeatures
import com.dezzy.app.source.spotify.SpotifyTrack
import com.dezzy.app.ui.components.SpotifyTrackRow
import com.dezzy.app.ui.components.SongMenuSheet
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.components.rememberSpotifyTrackPlayer
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.search.SearchHistoryKey
import com.dezzy.app.ui.screens.search.TopVideosScreen
import com.dezzy.app.ui.screens.search.addSearchHistory
import com.dezzy.app.ui.screens.search.parseSearchHistory
import com.dezzy.app.ui.screens.search.removeSearchHistory
import com.dezzy.app.ui.screens.search.rememberVideoSearch
import com.dezzy.app.ui.screens.search.videoSearchResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Cuántos géneros se muestran en la portada antes de «Ver todo». */
private const val SearchHomeGenreCount = 8

private enum class SearchSourceOption(val label: String) {
    YOUTUBE("YouTube Music"),
    LIBRARY("Biblioteca"),
    LOSSLESS("Lossless"),
}

@Composable
fun SearchScreen(ctx: ScreenContext, initial: String) {
    var query by rememberSaveable(initial) { mutableStateOf(initial) }
    var submitted by rememberSaveable(initial) { mutableStateOf(initial) }
    var suggestions by remember { mutableStateOf<SearchSuggestions?>(null) }
    var topVideos by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(SearchSourceOption.YOUTUBE) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    val historyRaw by remember { ctx.prefs.value(SearchHistoryKey, "") }.collectAsState("")
    val history = remember(historyRaw) { parseSearchHistory(historyRaw) }

    fun submit(text: String) {
        query = text
        submitted = text.trim()
        keyboard?.hide()
        // Sin foco la barra pasa a la píldora compacta de resultados.
        if (submitted.isNotEmpty()) focusManager.clearFocus()
        if (submitted.isNotEmpty()) ctx.scope.launch { addSearchHistory(ctx.prefs, submitted) }
    }

    // Al abrir la pestaña el campo recibe el foco y sale el teclado.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) {
            suggestions = null
            return@LaunchedEffect
        }
        delay(250)
        suggestions = ctx.client.getSearchSuggestions(query).getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        val compact = submitted.isNotEmpty() && query == submitted && !topVideos && !fieldFocused
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("¿Qué quieres escuchar?", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            leadingIcon = {
                IconButton(onClick = {
                    if (query.isNotEmpty() || topVideos) { query = ""; submitted = ""; topVideos = false }
                    else { keyboard?.hide(); focusManager.clearFocus() }
                }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            },
            trailingIcon = if (compact) null else {
                {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Rounded.Clear, "Borrar") }
                    } else {
                        Icon(Icons.Rounded.Language, null)
                    }
                }
            },
            // Barra plana como en la app actual: sin caja ni borde, con una línea debajo.
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(query) }),
            modifier = (
                if (compact) {
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                } else Modifier.fillMaxWidth()
                ).focusRequester(focusRequester).onFocusChanged { fieldFocused = it.isFocused },
        )
        if (!compact) {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.size(8.dp))
            SearchSourceChips(
                current = source,
                onSelect = {
                    when (it) {
                        SearchSourceOption.LOSSLESS -> ctx.navigator.push(Screen.Lossless)
                        else -> source = it
                    }
                },
            )
        }
        when {
            query.isBlank() && topVideos -> TopVideosScreen(ctx, onBack = { topVideos = false })
            query.isBlank() -> SearchHome(
                ctx,
                history = history,
                onPick = ::submit,
                onFill = { query = it },
                onRemove = { q -> ctx.scope.launch { removeSearchHistory(ctx.prefs, q) } },
                onTopVideos = { topVideos = true },
            )
            query != submitted -> SuggestionsList(ctx, suggestions, onQuery = ::submit)
            else -> SearchResults(ctx, submitted, source)
        }
    }
}

/** Filtros por tipo de los resultados (Todos ✓ · Canciones · Vídeos · Álbumes · Artistas · Listas). */
private enum class SearchFilter(val label: String) {
    ALL("Todos"), SONGS("Canciones"), VIDEOS("Vídeos"), ALBUMS("Álbumes"), ARTISTS("Artistas"), PLAYLISTS("Listas"),
}

@Composable
private fun SearchFilterChips(current: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SearchFilter.entries.forEach { f ->
            val selected = f == current
            FilterChip(
                selected = selected,
                onClick = { onSelect(f) },
                label = { Text(f.label, maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                leadingIcon = {
                    val icon = when (f) {
                        SearchFilter.ALL -> if (selected) Icons.Rounded.Check else null
                        SearchFilter.SONGS -> Icons.Rounded.MusicNote
                        SearchFilter.VIDEOS -> Icons.Rounded.PlayCircleOutline
                        SearchFilter.ALBUMS -> Icons.Rounded.Album
                        SearchFilter.ARTISTS -> Icons.Rounded.Person
                        SearchFilter.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
                    }
                    if (icon != null) Icon(icon, null, Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = null,
            )
        }
    }
}

/** Historial de búsqueda: 🕘 texto ✕ (borra esa) ↖ (rellena el campo). */
private fun LazyListScope.searchHistoryRows(
    history: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    items(history.size, key = { "hist:${history[it]}" }) { i ->
        val q = history[i]
        ListItem(
            headlineContent = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { Icon(Icons.Rounded.History, null) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onRemove(q) }) { Icon(Icons.Rounded.Close, "Borrar del historial") }
                    IconButton(onClick = { onFill(q) }) { Icon(Icons.Rounded.NorthWest, "Rellenar") }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onPick(q) },
        )
    }
}

// ---------------------------------------------------------------- sugerencias

@Composable
private fun SuggestionsList(ctx: ScreenContext, suggestions: SearchSuggestions?, onQuery: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (suggestions != null) {
            items(suggestions.queries.size) { i ->
                val q = suggestions.queries[i]
                ListItem(
                    headlineContent = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Rounded.Search, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onQuery(q) },
                )
            }
            items(suggestions.tracks.size) { i ->
                val t = suggestions.tracks[i]
                TrackRow(t, onClick = { ctx.player.playRadio(t, "radio:${t.title}") }, playing = t.videoId == ctx.playingId)
            }
            items(suggestions.items.size) { i ->
                val item = suggestions.items[i]
                ItemRow(item) { ctx.open(item) }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ---------------------------------------------------------------- portada «Descubre»

private data class DiscoverEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val highlighted: Boolean = false,
)

@Composable
private fun SearchHome(
    ctx: ScreenContext,
    history: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemove: (String) -> Unit,
    onTopVideos: () -> Unit,
) {
    val moods = rememberLoad("moods") { ctx.client.getMoodsAndGenres() }
    val discover = listOf(
        DiscoverEntry(Icons.Rounded.TrendingUp, "Éxitos", "Lo más escuchado", { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Charts()) }, highlighted = true),
        DiscoverEntry(Icons.Rounded.NewReleases, "Novedades", "Álbumes y sencillos", { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.NewReleases) }),
        DiscoverEntry(Icons.Rounded.SlowMotionVideo, "Vídeos", "Los más vistos", onTopVideos),
        DiscoverEntry(Icons.Rounded.Mic, "Reconocer", "¿Qué está sonando?", { ctx.pending("Reconocer música", "10") }),
    )

    LazyColumn(Modifier.fillMaxSize()) {
        searchHistoryRows(history, onPick, onFill, onRemove)
        item { HomeSectionTitle("Descubre") }
        items(discover.chunked(2).size) { row ->
            val pair = discover.chunked(2)[row]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { DiscoverCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            HomeSectionTitle(
                "Estados de ánimo y géneros",
                modifier = Modifier.padding(top = 8.dp),
                actionLabel = "Ver todo",
                onAction = { ctx.navigator.push(Screen.MoodsAndGenres) },
            )
        }
        when (val m = moods) {
            Load.Loading -> items(SearchHomeGenreCount / 2) { MoodRowPlaceholder() }
            is Load.Failed -> errorRow(m.message)
            is Load.Done -> {
                val all = m.value.flatMap { it.items }.take(SearchHomeGenreCount)
                moodGrid(all, ctx, "home")
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun MoodRowPlaceholder() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.weight(1f).height(MoodCardHeight),
            ) {}
        }
    }
}

private val MoodCardHeight = 56.dp

/** Rejilla de 2 columnas de tarjetas de color dentro de una lista perezosa. */
private fun LazyListScope.moodGrid(moods: List<MoodItem>, ctx: ScreenContext, keyPrefix: String) {
    val rows = moods.chunked(2)
    items(rows.size, key = { "mood:$keyPrefix:$it" }) { r ->
        val pair = rows[r]
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { item ->
                MoodCard(
                    item,
                    onClick = { ctx.navigator.push(Screen.Browse(item.title, item.browseId, item.params)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun DiscoverCard(entry: DiscoverEntry, modifier: Modifier = Modifier) {
    val container =
        if (entry.highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer
    val iconTint =
        if (entry.highlighted) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.primary
    val supportingColor =
        if (entry.highlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = entry.onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = modifier.fillMaxWidth().heightIn(min = 88.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(entry.icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(10.dp))
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = supportingColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tarjeta de género / ánimo: franja de color a la izquierda y el nombre. */
@Composable
private fun MoodCard(item: MoodItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stripe = item.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().height(MoodCardHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(stripe))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- resultados

@Composable
private fun SearchResults(ctx: ScreenContext, query: String, source: SearchSourceOption) {
    if (source == SearchSourceOption.LIBRARY) {
        LibrarySearchResults(ctx, query)
        return
    }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    val videosOnly = filter == SearchFilter.VIDEOS
    val result = rememberLoad(query) { ctx.client.search(query) }
    val videoResult = rememberVideoSearch(ctx, query, videosOnly)
    val features: SpotifyFeatures = koinInject()
    val spotifyPlayer = rememberSpotifyTrackPlayer(ctx, features)
    // Sólo con sesión y «Usar para búsquedas» activo; si Spotify falla, queda vacío sin avisar.
    val spotifyTracks by produceState<List<SpotifyTrack>>(emptyList(), query) {
        value = emptyList()
        if (features.searchEnabled()) value = features.searchTracks(query, 10)
    }

    Column(Modifier.fillMaxSize()) {
        SearchFilterChips(current = filter, onSelect = { filter = it })
        LazyColumn(Modifier.fillMaxSize()) {
            if (videosOnly) {
                videoSearchResults(ctx, query, videoResult)
            } else when (val r = result) {
                Load.Loading -> loadingRows()
                is Load.Failed -> errorRow(r.message)
                is Load.Done -> resultItems(r.value, query, ctx, filter)
            }
            if (filter == SearchFilter.ALL && spotifyTracks.isNotEmpty()) {
                item(key = "spotify_h") { SearchResultSectionHeader("De tu Spotify") }
                items(spotifyTracks.size, key = { "spotify:${spotifyTracks[it].id}" }) { i ->
                    SpotifyTrackRow(spotifyTracks[i], spotifyPlayer)
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

private fun LazyListScope.resultItems(result: SearchResult, query: String, ctx: ScreenContext, filter: SearchFilter) {
    if (result.songs.isEmpty() && result.albums.isEmpty() && result.artists.isEmpty() && result.playlists.isEmpty()) {
        emptyRow("Sin resultados para «$query»")
        return
    }
    val artist = result.artists.firstOrNull { it.title.equals(query, ignoreCase = true) }
    val song = result.songs.firstOrNull()
    if (filter != SearchFilter.ALL) {
        // Con un filtro por tipo no se muestra la tarjeta de mejor resultado.
    } else if (artist != null) {
        item(key = "top") {
            SearchResultSectionHeader("Top results")
            SearchTopResultCard(
                title = artist.title,
                subtitle = "Artista",
                thumbnail = artist.thumbnail,
                isArtist = true,
                onOpen = { ctx.open(artist) },
                // «Reproducir» del artista: carga su ficha y suena desde sus canciones más escuchadas.
                onPlay = {
                    ctx.scope.launch {
                        ctx.client.getArtist(artist.id).getOrNull()?.songs
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { ctx.player.playTracks(it, 0, "artist:${artist.title}") }
                    }
                },
                onShuffle = null,
            )
        }
    } else if (song != null) {
        item(key = "top") {
            SearchResultSectionHeader("Top results")
            SearchTopResultCard(
                track = song,
                title = song.title,
                subtitle = listOfNotNull("Canción", song.artists.ifBlank { null }).joinToString(", "),
                thumbnail = song.thumbnail,
                isArtist = false,
                onOpen = { ctx.player.playTracks(result.songs, 0, "search") },
                onPlay = { ctx.player.playTracks(result.songs, 0, "search") },
                onShuffle = { ctx.player.playTracks(result.songs.shuffled(), 0, "search") },
            )
        }
    }
    if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
        if (result.songs.isNotEmpty()) {
            // Con «Todos» la lista va seguida, sin encabezados, como en la app actual.
            if (filter != SearchFilter.ALL) item(key = "songs_h") { SearchResultSectionHeader("Canciones") }
            items(result.songs.size, key = { "song:$it:${result.songs[it].videoId}" }) { i ->
                val t = result.songs[i]
                TrackRow(
                    t,
                    onClick = { ctx.player.playTracks(result.songs, i, "search") },
                    playing = t.videoId == ctx.playingId,
                )
            }
        }
    }
    if (filter == SearchFilter.ALL) {
        val rest = result.artists.filter { it != artist } + result.albums + result.playlists
        items(rest.size, key = { "all:$it:${rest[it].type.name}:${rest[it].id}" }) { i ->
            ItemRow(rest[i]) { ctx.open(rest[i]) }
        }
    } else {
        val filtered = when (filter) {
            SearchFilter.ALBUMS -> result.albums
            SearchFilter.ARTISTS -> result.artists
            SearchFilter.PLAYLISTS -> result.playlists
            else -> emptyList()
        }
        items(filtered.size, key = { "f:${filter.name}:$it:${filtered[it].id}" }) { i ->
            ItemRow(filtered[i]) { ctx.open(filtered[i]) }
        }
    }
}

/** Biblioteca: busca en local (favoritos e historial) por título, artista o álbum. */
@Composable
private fun LibrarySearchResults(ctx: ScreenContext, query: String) {
    val favorites by remember { ctx.repository.getFavorites() }.collectAsState(emptyList())
    val history by remember { ctx.repository.getHistory(500) }.collectAsState(emptyList())
    val matches = remember(favorites, history, query) {
        (favorites + history)
            .distinctBy { it.videoId ?: it.title + it.artists }
            .filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artists.contains(query, ignoreCase = true) ||
                    (it.album?.contains(query, ignoreCase = true) == true)
            }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (matches.isEmpty()) {
            emptyRow("Sin resultados en tu biblioteca para «$query»")
        } else {
            items(matches.size, key = { "lib:$it:${matches[it].videoId}" }) { i ->
                val t = matches[i]
                TrackRow(
                    t,
                    onClick = { ctx.player.playTracks(matches, i, "search") },
                    playing = t.videoId == ctx.playingId,
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** Fuente de búsqueda visible como chips (boceto fase 3 A). Sólo YouTube Music funciona por ahora. */
@Composable
private fun SearchSourceChips(
    current: SearchSourceOption,
    onSelect: (SearchSourceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SearchSourceOption.entries.forEach { option ->
            val selected = option == current
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onSelect(option) },
                label = { Text(option.label, maxLines = 1) },
                leadingIcon = {
                    Icon(
                        if (selected) Icons.Rounded.Check else when (option) {
                            SearchSourceOption.YOUTUBE -> Icons.Rounded.Language
                            SearchSourceOption.LIBRARY -> Icons.Rounded.LibraryMusic
                            SearchSourceOption.LOSSLESS -> Icons.Rounded.GraphicEq
                        },
                        null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                colors = com.dezzy.app.ui.components.dezzyFilterChipColors(),
            )
        }
    }
}

/** Encabezado de sección en los resultados de búsqueda. */
@Composable
private fun SearchResultSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Tarjeta «Mejor resultado» con Reproducir y Aleatorio opcionales. */
@Composable
private fun SearchTopResultCard(
    title: String,
    subtitle: String,
    thumbnail: String?,
    isArtist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    track: Track? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen && track != null) SongMenuSheet(track, onDismiss = { menuOpen = false })
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            CoverImage(
                thumbnail,
                Modifier.size(88.dp),
                if (isArtist) CircleShape else RoundedCornerShape(12.dp),
                sizePx = 264,
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onPlay != null || onShuffle != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onPlay != null) {
                            Button(
                                onClick = onPlay,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Reproducir", maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                        if (onShuffle != null) {
                            FilledTonalIconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Aleatorio") }
                        }
                        if (track != null) {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ánimo y géneros

@Composable
fun MoodsAndGenresScreen(ctx: ScreenContext) {
    val moods = rememberLoad("moods") { ctx.client.getMoodsAndGenres() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopSpacer() }
        item { ScreenTitle("Estados de ánimo y géneros", onBack = { ctx.navigator.back() }) }
        when (val m = moods) {
            Load.Loading -> items(6) { MoodRowPlaceholder() }
            is Load.Failed -> errorRow(m.message)
            is Load.Done -> m.value.forEachIndexed { index, section: MoodSection ->
                item(key = "mh:$index:${section.title}") { HomeSectionTitle(section.title) }
                moodGrid(section.items, ctx, "all$index")
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ---------------------------------------------------------------- explorar

@Composable
fun BrowseScreen(screen: Screen.Browse, ctx: ScreenContext) {
    val sections = rememberLoad(screen.browseId + screen.params) { ctx.client.getBrowseSections(screen.browseId, screen.params) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopSpacer() }
        item { ScreenTitle(screen.title, onBack = { ctx.navigator.back() }) }
        when (val s = sections) {
            Load.Loading -> loadingRows()
            is Load.Failed -> errorRow(s.message)
            is Load.Done -> s.value.forEachIndexed { i, section -> carousel(section, ctx, "b$i:") }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
