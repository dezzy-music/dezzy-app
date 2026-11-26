/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.SectionHeader
import com.dezzy.app.ui.components.TrackRow

/** Ficha de álbum (boceto fase 4 A): cabecera, Reproducir / Aleatorio y pistas numeradas. */
@Composable
fun AlbumScreen(id: String, ctx: ScreenContext) {
    val album = rememberLoad(id) { ctx.client.getAlbum(id) }
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
                val tracks = a.tracks.map { if (it.thumbnail == null) it.copy(thumbnail = a.thumbnail, album = it.album ?: a.title) else it }
                item(key = "header") {
                    CollectionHeader(
                        title = a.title,
                        subtitle = listOfNotNull(a.artist, a.year, "${tracks.size} canciones").joinToString(" • "),
                        thumbnail = a.thumbnail,
                        round = false,
                        onBack = { ctx.navigator.back() },
                        onPlay = if (tracks.isEmpty()) null else ({ ctx.player.playTracks(tracks, 0, "album:${a.title}") }),
                        onShuffle = { ctx.player.playTracks(tracks.shuffled(), 0, "album:${a.title}") },
                    )
                }
                trackList(tracks, ctx, "album:${a.title}", numbered = true)
            }
        }
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
                    )
                }
                trackList(p.tracks, ctx, "playlist:${p.title}", numbered = false)
            }
        }
    }
}

/** Ficha de artista: canciones populares, estanterías (álbumes, sencillos…) y «Acerca de». */
@Composable
fun ArtistScreen(id: String, ctx: ScreenContext) {
    val artist = rememberLoad(id) { ctx.client.getArtist(id) }
    var showAllSongs by remember(id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                item(key = "header") {
                    CollectionHeader(
                        title = a.name,
                        subtitle = a.listeners,
                        thumbnail = a.thumbnail,
                        round = true,
                        onBack = { ctx.navigator.back() },
                        onPlay = if (a.songs.isEmpty()) null else ({ ctx.player.playTracks(a.songs, 0, source) }),
                        onShuffle = { ctx.player.playTracks(a.songs.shuffled(), 0, source) },
                        thumbnailContent = {
                            // Canvas del artista: trozo mudo del videoclip de su primera canción sobre la foto.
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(200.dp).clip(androidx.compose.foundation.shape.CircleShape),
                            ) {
                                CoverImage(a.thumbnail, Modifier.matchParentSize(), androidx.compose.foundation.shape.CircleShape, sizePx = 720)
                                com.dezzy.app.ui.player.canvas.ArtistCanvas(a.songs.firstOrNull(), Modifier.matchParentSize())
                            }
                        },
                    )
                }
                if (a.songs.isNotEmpty()) {
                    item(key = "songs:h") {
                        SectionHeader(
                            "Canciones",
                            action = if (a.songs.size > 5) (if (showAllSongs) "Menos" else "Ver todo") else null,
                            onAction = { showAllSongs = !showAllSongs },
                        )
                    }
                    trackList(if (showAllSongs) a.songs else a.songs.take(5), ctx, source, numbered = false, allTracks = a.songs)
                }
                a.shelves.filter { it.items.isNotEmpty() }.forEach { carousel(it, ctx, "artist:") }
                a.description?.takeIf { it.isNotBlank() }?.let { about ->
                    item(key = "about") { AboutArtist(about) }
                }
            }
        }
    }
}

@Composable
private fun AboutArtist(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("Acerca de", modifier = Modifier.padding(horizontal = 0.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ver menos" else "Ver más") }
    }
}

/** Filas de pistas; tocar una reproduce la lista entera desde ahí. */
private fun androidx.compose.foundation.lazy.LazyListScope.trackList(
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
            onMore = {
                ctx.player.addToQueue(t)
                ctx.toast("Añadida a la cola")
            },
        )
    }
}
