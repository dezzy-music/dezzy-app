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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.Artist
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.ItemCard
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.player.kit.rememberShareText
import com.dezzy.app.ui.screens.detail.AlbumMenuSheet
import com.dezzy.app.ui.screens.detail.ArrowSectionHeader
import com.dezzy.app.ui.screens.detail.CircleIconButton
import com.dezzy.app.ui.screens.detail.coverGradient
import com.dezzy.app.ui.screens.detail.formatLongDuration
import com.dezzy.app.ui.screens.detail.rememberCoverColor
import com.dezzy.app.ui.screens.downloads.DownloadAllButton
import com.dezzy.app.ui.screens.stats.openArtistByName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun albumTracks(a: Album): List<Track> =
    a.tracks.map { if (it.thumbnail == null) it.copy(thumbnail = a.thumbnail, album = it.album ?: a.title) else it }

/** Ficha de álbum: degradado del color de la portada, cabecera calcada de la app actual y pistas numeradas. */
@Composable
fun AlbumScreen(id: String, ctx: ScreenContext) {
    var reload by remember(id) { mutableIntStateOf(0) }
    val album = rememberLoad(id to reload) { ctx.client.getAlbum(id) }
    var menu by remember { mutableStateOf(false) }
    val done = (album as? Load.Done)?.value
    val coverColor = rememberCoverColor(done?.thumbnail)
    Box(Modifier.fillMaxSize().background(coverGradient(coverColor))) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            when (album) {
                Load.Loading -> {
                    item { Column { TopSpacer(); ScreenTitle("", onBack = { ctx.navigator.back() }) } }
                    loadingRows()
                }
                is Load.Failed -> {
                    item { Column { TopSpacer(); ScreenTitle("Álbum", onBack = { ctx.navigator.back() }) } }
                    errorRow(album.message)
                }
                is Load.Done -> {
                    val a = album.value
                    val tracks = albumTracks(a)
                    item(key = "header") { AlbumHeader(a, tracks, ctx, onMenu = { menu = true }) }
                    trackList(tracks, ctx, "album:${a.title}", numbered = true)
                }
            }
        }
    }
    if (menu && done != null) {
        AlbumMenuSheet(done, albumTracks(done), ctx, onDismiss = { menu = false }, onReload = { reload++ })
    }
}

@Composable
private fun AlbumHeader(a: Album, tracks: List<Track>, ctx: ScreenContext, onMenu: () -> Unit) {
    val repository: MusicRepository = koinInject()
    val downloads: DownloadManager = koinInject()
    val scope = rememberCoroutineScope()
    val favorites by remember { repository.getFavorites() }.collectAsState(emptyList())
    val favIds = remember(favorites) { favorites.mapNotNullTo(HashSet()) { it.videoId } }
    val allLiked = tracks.isNotEmpty() && tracks.all { it.videoId != null && it.videoId in favIds }
    var share by remember { mutableStateOf(false) }
    val source = "album:${a.title}"
    val totalSeconds = tracks.sumOf { it.durationSeconds ?: 0L }
    val meta = listOfNotNull(
        "Álbum",
        a.year,
        "${a.trackCount ?: tracks.size} canciones",
        totalSeconds.takeIf { it > 0 }?.let { formatLongDuration(it) },
    ).joinToString(", ")

    Column(Modifier.fillMaxWidth()) {
        TopSpacer()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { ctx.navigator.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Más", tint = Color.White) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp)) {
                CoverImage(a.thumbnail, Modifier.size(200.dp), RoundedCornerShape(12.dp), sizePx = 720)
                CircleIconButton(
                    Icons.Rounded.Download, "Descargar",
                    onClick = {
                        downloads.enqueue(tracks)
                        ctx.toast("${tracks.size} canciones a descargas")
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    background = Color.Black.copy(alpha = 0.6f),
                    size = 36.dp,
                    iconSize = 20.dp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                a.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (a.artist.isNotBlank()) {
                Row(
                    Modifier.clickable { openArtistByName(ctx, a.artist.substringBefore(",").trim()) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, null, Modifier.size(14.dp), tint = Color.White)
                    }
                    Text(
                        a.artist,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = {
                        scope.launch { tracks.forEach { repository.setFavorite(it, !allLiked) } }
                        ctx.toast(if (allLiked) "Quitado de Me gusta" else "Álbum añadido a Me gusta")
                    }) {
                        Icon(
                            if (allLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Me gusta",
                            tint = if (allLiked) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = {
                        downloads.enqueue(tracks)
                        ctx.toast("${tracks.size} canciones a descargas")
                    }) { Icon(Icons.Rounded.Download, "Descargar", tint = Color.White) }
                    androidx.compose.material3.IconButton(onClick = { share = true }) { Icon(Icons.Rounded.Share, "Compartir", tint = Color.White) }
                    androidx.compose.material3.IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Más", tint = Color.White) }
                }
                androidx.compose.material3.IconButton(onClick = {
                    if (tracks.isNotEmpty()) ctx.player.playTracks(tracks.shuffled(), 0, source)
                }) { Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = Color.White) }
                Spacer(Modifier.size(8.dp))
                CircleIconButton(
                    Icons.Rounded.PlayArrow, "Reproducir",
                    onClick = { if (tracks.isNotEmpty()) ctx.player.playTracks(tracks, 0, source) },
                    background = Color.White,
                    tint = Color.Black,
                    size = 56.dp,
                    iconSize = 32.dp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (share) {
        ShareBottomSheet(
            title = a.title,
            subtitle = a.artist,
            thumbnailUrl = a.thumbnail,
            shareLink = "https://music.youtube.com/browse/${a.id}",
            onDismiss = { share = false },
        )
    }
}

/** Ficha de lista de YouTube Music. */
@Composable
fun PlaylistScreen(id: String, ctx: ScreenContext) {
    val playlist = rememberLoad(id) { ctx.client.getPlaylist(id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        when (playlist) {
            Load.Loading -> {
                item { Column { TopSpacer(); ScreenTitle("", onBack = { ctx.navigator.back() }) } }
                loadingRows()
            }
            is Load.Failed -> {
                item { Column { TopSpacer(); ScreenTitle("Lista", onBack = { ctx.navigator.back() }) } }
                errorRow(playlist.message)
            }
            is Load.Done -> {
                val p = playlist.value
                item(key = "header") {
                    CollectionHeader(
                        title = p.title,
                        subtitle = listOfNotNull(p.author, "${p.tracks.size} canciones").joinToString(" • "),
                        thumbnail = p.thumbnail ?: p.tracks.firstOrNull()?.thumbnail,
                        round = false,
                        onBack = { ctx.navigator.back() },
                        onPlay = if (p.tracks.isEmpty()) null else ({ ctx.player.playTracks(p.tracks, 0, "playlist:${p.title}") }),
                        onShuffle = { ctx.player.playTracks(p.tracks.shuffled(), 0, "playlist:${p.title}") },
                        extraActions = { DownloadAllButton(p.tracks, ctx) },
                    )
                }
                trackList(p.tracks, ctx, "playlist:${p.title}", numbered = false)
            }
        }
    }
}

/** Ficha de artista: imagen a sangre, acciones, canciones más escuchadas y estanterías del cliente. */
@Composable
fun ArtistScreen(id: String, ctx: ScreenContext) {
    val artist = rememberLoad(id) { ctx.client.getArtist(id) }
    var showAllSongs by remember(id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            when (artist) {
                Load.Loading -> {
                    item { Column { TopSpacer(); ScreenTitle("", onBack = { ctx.navigator.back() }) } }
                    loadingRows()
                }
                is Load.Failed -> {
                    item { Column { TopSpacer(); ScreenTitle("Artista", onBack = { ctx.navigator.back() }) } }
                    errorRow(artist.message)
                }
                is Load.Done -> {
                    val a = artist.value
                    val source = "artist:${a.name}"
                    item(key = "header") { ArtistHeader(a, id, ctx) }
                    if (a.songs.isNotEmpty()) {
                        item(key = "songs:h") {
                            ArrowSectionHeader(
                                "Canciones más escuchadas",
                                onClick = if (a.songs.size > 5) ({ showAllSongs = !showAllSongs }) else null,
                            )
                        }
                        trackList(if (showAllSongs) a.songs else a.songs.take(5), ctx, source, numbered = false, allTracks = a.songs)
                    }
                    a.shelves.filter { it.items.isNotEmpty() }.forEach { artistShelf(it, ctx) }
                }
            }
        }
        if (showBar) {
            val name = (artist as? Load.Done)?.value?.name.orEmpty()
            Column(Modifier.fillMaxWidth().background(Color.Black)) {
                TopSpacer()
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { ctx.navigator.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White)
                    }
                    Text(name, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    val clipboard = LocalClipboardManager.current
                    val shareText = rememberShareText()
                    val link = "https://music.youtube.com/channel/$id"
                    androidx.compose.material3.IconButton(onClick = { clipboard.setText(AnnotatedString(link)); ctx.toast("Enlace copiado") }) {
                        Icon(Icons.Rounded.Link, "Copiar enlace", tint = Color.White)
                    }
                    androidx.compose.material3.IconButton(onClick = { shareText(link) }) {
                        Icon(Icons.Rounded.Share, "Compartir", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(a: Artist, id: String, ctx: ScreenContext) {
    val clipboard = LocalClipboardManager.current
    val shareText = rememberShareText()
    val link = "https://music.youtube.com/channel/$id"
    val source = "artist:${a.name}"
    var expanded by remember(id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            CoverImage(a.thumbnail, Modifier.fillMaxSize(), RectangleShape, sizePx = 1080)
            // Canvas del artista: trozo mudo del videoclip de su primera canción sobre la foto.
            com.dezzy.app.ui.player.canvas.ArtistCanvas(a.songs.firstOrNull(), Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black),
                ),
            )
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
                TopSpacer()
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(
                        Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onClick = { ctx.navigator.back() },
                        background = Color.Black.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(
                        Icons.Rounded.Link, "Copiar enlace",
                        onClick = { clipboard.setText(AnnotatedString(link)); ctx.toast("Enlace copiado") },
                        background = Color.Black.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.size(8.dp))
                    CircleIconButton(
                        Icons.Rounded.Share, "Compartir", onClick = { shareText(link) },
                        background = Color.Black.copy(alpha = 0.4f),
                    )
                }
            }
            Text(
                a.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            a.listeners?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
            }
            a.description?.takeIf { it.isNotBlank() }?.let { about ->
                Text(
                    about,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (about.length > 120) {
                    Text(
                        if (expanded) "Menos" else "Más",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { ctx.toast("Suscribirse a artistas aún no está disponible") },
                        shape = CircleShape,
                    ) {
                        Icon(if (a.subscribed == true) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (a.subscribed == true) "Suscrito" else "Subscribir")
                    }
                    if (a.songs.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { ctx.player.playRadio(a.songs.first(), "radio:${a.name}") }) {
                            Icon(Icons.Rounded.Sensors, "Radio")
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = {
                        if (a.songs.isNotEmpty()) ctx.player.playTracks(a.songs.shuffled(), 0, source)
                    }) { Icon(Icons.Rounded.Shuffle, "Aleatorio") }
                }
                CircleIconButton(
                    Icons.Rounded.PlayArrow, "Reproducir",
                    onClick = { if (a.songs.isNotEmpty()) ctx.player.playTracks(a.songs, 0, source) },
                    background = Color.White,
                    tint = Color.Black,
                    size = 56.dp,
                    iconSize = 32.dp,
                )
            }
        }
    }
}

/** Estantería del artista: título en negrita con «→» y carrusel de tarjetas (sin enlace propio: el cliente no da browseId). */
private fun LazyListScope.artistShelf(section: MusicSection, ctx: ScreenContext) {
    item(key = "h:artist:${section.title}") { ArrowSectionHeader(section.title) }
    item(key = "r:artist:${section.title}") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(section.items, key = { it.type.name + it.id }) {
                if (it.type == com.dezzy.app.core.model.MusicItemType.ALBUM) ArtistAlbumCard(it, onClick = { ctx.open(it) })
                else ItemCard(it, onClick = { ctx.open(it) })
            }
        }
    }
}

/** Tarjeta de álbum o sencillo en la ficha de artista: portada con ▶ encima, título y sólo el año, en negrita. */
@Composable
private fun ArtistAlbumCard(item: com.dezzy.app.core.model.MusicItem, onClick: () -> Unit) {
    val year = Regex("[12][0-9]{3}").findAll(item.subtitle).lastOrNull()?.value ?: item.subtitle
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(128.dp)) {
            CoverImage(item.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(10.dp), sizePx = 360)
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(year, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), maxLines = 1)
    }
}

/** Filas de pistas; tocar una reproduce la lista entera desde ahí. */
private fun LazyListScope.trackList(
    tracks: List<Track>,
    ctx: ScreenContext,
    source: String,
    numbered: Boolean,
    allTracks: List<Track> = tracks,
) {
    if (tracks.isEmpty()) {
        emptyRow("No hay canciones.")
        return
    }
    itemsIndexed(tracks, key = { i, t -> "t:$i:${t.videoId}" }) { i, t ->
        TrackRow(
            t,
            onClick = { ctx.player.playTracks(allTracks, i, source) },
            playing = t.videoId != null && t.videoId == ctx.playingId,
            number = if (numbered) i + 1 else null,
            showCover = !numbered,
            extraMenu = listOf(
                "Añadir a la cola" to {
                    ctx.player.addToQueue(t)
                    ctx.toast("Añadida a la cola")
                },
            ),
        )
    }
}
