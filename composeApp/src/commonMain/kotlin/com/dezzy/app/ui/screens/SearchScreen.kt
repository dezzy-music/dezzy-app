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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.navigation.Screen
import kotlinx.coroutines.delay

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
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit(text: String) {
        query = text
        submitted = text.trim()
        keyboard?.hide()
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
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Buscar música, artistas, álbumes") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Rounded.Clear, "Borrar") }
                }
            },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(query) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            query.isBlank() -> SearchHome(ctx)
            query != submitted -> SuggestionsList(ctx, suggestions, onQuery = ::submit)
            else -> SearchResults(ctx, submitted)
        }
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
private fun SearchHome(ctx: ScreenContext) {
    val moods = rememberLoad("moods") { ctx.client.getMoodsAndGenres() }
    val discover = listOf(
        DiscoverEntry(Icons.Rounded.TrendingUp, "Éxitos", "Lo más escuchado", { ctx.pending("Éxitos", "10") }, highlighted = true),
        DiscoverEntry(Icons.Rounded.NewReleases, "Novedades", "Álbumes y sencillos", { ctx.pending("Novedades", "10") }),
        DiscoverEntry(Icons.Rounded.SlowMotionVideo, "Videos", "Los más vistos", { ctx.pending("Videos", "5d") }),
        DiscoverEntry(Icons.Rounded.Mic, "Reconocer", "¿Qué está sonando?", { ctx.pending("Reconocer música", "10") }),
    )

    LazyColumn(Modifier.fillMaxSize()) {
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
private fun SearchResults(ctx: ScreenContext, query: String) {
    var source by rememberSaveable { mutableStateOf(SearchSourceOption.YOUTUBE) }
    val result = rememberLoad(query) { ctx.client.search(query) }

    Column(Modifier.fillMaxSize()) {
        SearchSourceChips(
            current = source,
            onSelect = {
                if (it == SearchSourceOption.YOUTUBE) source = it
                else ctx.toast("Llega en la fase 6")
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            when (val r = result) {
                Load.Loading -> loadingRows()
                is Load.Failed -> errorRow(r.message)
                is Load.Done -> resultItems(r.value, query, ctx)
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

private fun LazyListScope.resultItems(result: SearchResult, query: String, ctx: ScreenContext) {
    if (result.songs.isEmpty() && result.albums.isEmpty() && result.artists.isEmpty() && result.playlists.isEmpty()) {
        emptyRow("Sin resultados para «$query»")
        return
    }
    val artist = result.artists.firstOrNull { it.title.equals(query, ignoreCase = true) }
    val song = result.songs.firstOrNull()
    if (artist != null) {
        item(key = "top") {
            SearchResultSectionHeader("Mejor resultado")
            SearchTopResultCard(
                title = artist.title,
                subtitle = "Artista",
                thumbnail = artist.thumbnail,
                isArtist = true,
                onOpen = { ctx.open(artist) },
                onPlay = null,
                onShuffle = null,
            )
        }
    } else if (song != null) {
        item(key = "top") {
            SearchResultSectionHeader("Mejor resultado")
            SearchTopResultCard(
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
    if (result.songs.isNotEmpty()) {
        item(key = "songs_h") { SearchResultSectionHeader("Canciones") }
        items(result.songs.size, key = { "song:$it:${result.songs[it].videoId}" }) { i ->
            val t = result.songs[i]
            TrackRow(
                t,
                onClick = { ctx.player.playTracks(result.songs, i, "search") },
                playing = t.videoId == ctx.playingId,
            )
        }
    }
    if (result.artists.isNotEmpty()) carousel(MusicSection("Artistas", items = result.artists), ctx, "s:")
    if (result.albums.isNotEmpty()) carousel(MusicSection("Álbumes", items = result.albums), ctx, "s:")
    if (result.playlists.isNotEmpty()) carousel(MusicSection("Listas", items = result.playlists), ctx, "s:")
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
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
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onPlay != null || onShuffle != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onPlay != null) {
                            Button(onClick = onPlay, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Reproducir", maxLines = 1)
                            }
                        }
                        if (onShuffle != null) {
                            FilledTonalIconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Aleatorio") }
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
