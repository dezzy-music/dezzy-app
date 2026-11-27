/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Album
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.stats.openArtistByName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Menú «⋮» de un álbum: cabecera con «me gusta», losetas rápidas y acciones sobre todo el disco. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumMenuSheet(
    album: Album,
    tracks: List<Track>,
    ctx: ScreenContext,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
) {
    val repository: MusicRepository = koinInject()
    val downloads: DownloadManager = koinInject()
    val scope = rememberCoroutineScope()
    val favorites by remember { repository.getFavorites() }.collectAsState(emptyList())
    val favIds = remember(favorites) { favorites.mapNotNullTo(HashSet()) { it.videoId } }
    val allLiked = tracks.isNotEmpty() && tracks.all { it.videoId != null && it.videoId in favIds }
    var share by remember { mutableStateOf(false) }
    var addToList by remember { mutableStateOf(false) }
    val source = "album:${album.title}"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(album.thumbnail, Modifier.size(48.dp), RoundedCornerShape(8.dp), sizePx = 144)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${album.artist} • ${tracks.size} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    scope.launch { tracks.forEach { repository.setFavorite(it, !allLiked) } }
                    ctx.toast(if (allLiked) "Quitado de Me gusta" else "Álbum añadido a Me gusta")
                }) {
                    Icon(
                        if (allLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (allLiked) "Quitar de me gusta" else "Me gusta",
                        tint = if (allLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MenuTile(Icons.Rounded.PlayArrow, "Reproducir", Modifier.weight(1f)) {
                    if (tracks.isNotEmpty()) ctx.player.playTracks(tracks, 0, source)
                    onDismiss()
                }
                MenuTile(Icons.Rounded.Shuffle, "Aleatorio", Modifier.weight(1f)) {
                    if (tracks.isNotEmpty()) ctx.player.playTracks(tracks.shuffled(), 0, source)
                    onDismiss()
                }
                MenuTile(Icons.Rounded.Share, "Compartir", Modifier.weight(1f)) { share = true }
            }

            Column(Modifier.padding(horizontal = 8.dp)) {
                MenuRow(Icons.Rounded.SkipNext, "Reproducir siguiente") {
                    tracks.asReversed().forEach { ctx.player.playNext(it) }
                    ctx.toast("Se reproducirá a continuación")
                    onDismiss()
                }
                MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Añadir a la cola") {
                    tracks.forEach { ctx.player.addToQueue(it) }
                    ctx.toast("Álbum añadido a la cola")
                    onDismiss()
                }
                MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a lista de reproducción") { addToList = true }
                MenuRow(Icons.Rounded.Download, "Descargar") {
                    downloads.enqueue(tracks)
                    ctx.toast("${tracks.size} canciones a descargas")
                    onDismiss()
                }
                if (album.artist.isNotBlank()) {
                    MenuRow(Icons.Rounded.Person, "Ver artista", album.artist) {
                        onDismiss()
                        openArtistByName(ctx, album.artist.substringBefore(",").trim())
                    }
                }
                MenuRow(Icons.Rounded.Refresh, "Volver a buscar") {
                    onReload()
                    onDismiss()
                }
            }
        }
    }

    if (share) {
        ShareBottomSheet(
            title = album.title,
            subtitle = album.artist,
            thumbnailUrl = album.thumbnail,
            shareLink = "https://music.youtube.com/browse/${album.id}",
            onDismiss = { share = false; onDismiss() },
        )
    }
    if (addToList) {
        val playlists by remember { repository.getPlaylists() }.collectAsState(emptyList())
        AlertDialog(
            onDismissRequest = { addToList = false; onDismiss() },
            title = { Text("Añadir a una lista") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (playlists.isEmpty()) Text("Aún no tienes listas propias.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    playlists.forEach { p ->
                        Text(
                            p.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch { tracks.forEach { repository.addTrackToPlaylist(p.id, it) } }
                                ctx.toast("Álbum añadido a ${p.title}")
                                addToList = false
                                onDismiss()
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addToList = false; onDismiss() }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun MenuTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
