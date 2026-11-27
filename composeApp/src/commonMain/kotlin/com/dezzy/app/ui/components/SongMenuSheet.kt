/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.personalize.SongMods
import com.dezzy.app.feature.personalize.withMods
import com.dezzy.app.ui.player.kit.AddToPlaylistDialog
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.ShareBottomSheet
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.personalize.SongModDialog
import com.dezzy.app.ui.screens.stats.openArtistByName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** El contexto de pantalla (navegación, aviso corto, reproductor) para quien esté dentro de una pantalla. */
val LocalScreenContext = staticCompositionLocalOf<ScreenContext?> { null }

/**
 * Menú «⋮» de una canción: cabecera con «me gusta», tres losetas rápidas y la lista de acciones.
 * [extraItems] se añade tras «Detalles» y antes de «Personalizar canción».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    track: Track,
    onDismiss: () -> Unit,
    extraItems: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val ctx = LocalScreenContext.current
    val connection = LocalPlayerConnection.current
    val repository: MusicRepository = koinInject()
    val downloads: DownloadManager = koinInject()
    val songMods: SongMods = koinInject()
    val mods by songMods.all.collectAsState(emptyMap())
    val scope = rememberCoroutineScope()
    val original = track
    val shown = remember(original, mods) { original.withMods(mods) }
    val controller = ctx?.player ?: connection?.controller
    val toast: (String) -> Unit = { msg -> ctx?.toast(msg) ?: connection?.toast?.invoke(msg) }

    var addToPlaylist by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var modDialog by remember { mutableStateOf(false) }

    val vid = original.videoId
    val liked by remember(vid) { repository.getFavorites() }.collectAsState(emptyList())
    val isLiked = vid != null && liked.any { it.videoId == vid }
    val dlStatus by remember(vid) { vid?.let { downloads.status(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            // Cabecera
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(shown.thumbnail, Modifier.size(48.dp), RoundedCornerShape(8.dp), sizePx = 144)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(shown.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        shown.artists,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { scope.launch { repository.setFavorite(original, !isLiked) } }) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isLiked) "Quitar de me gusta" else "Me gusta",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // Losetas rápidas
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MenuTile(Icons.Rounded.SkipNext, "Reproducir siguiente", Modifier.weight(1f)) {
                    controller?.playNext(original)
                    toast("Se reproducirá a continuación")
                    onDismiss()
                }
                MenuTile(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a lista", Modifier.weight(1f)) { addToPlaylist = true }
                MenuTile(Icons.Rounded.Share, "Compartir", Modifier.weight(1f)) { share = true }
            }

            // Lista
            Column(Modifier.padding(horizontal = 8.dp)) {
                MenuRow(Icons.Rounded.Radio, "Empezar radio") {
                    controller?.playRadio(original, "radio:${original.title}")
                    onDismiss()
                }
                MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Añadir a la cola") {
                    controller?.addToQueue(original)
                    toast("Añadida a la cola")
                    onDismiss()
                }
                // No hay una biblioteca aparte de «Me gusta»: se guarda ahí.
                MenuRow(Icons.Rounded.LibraryAdd, "Añadir a biblioteca") {
                    scope.launch { repository.setFavorite(original, true) }
                    toast("Añadida a la biblioteca")
                    onDismiss()
                }
                if (vid != null) {
                    if (dlStatus != null) {
                        MenuRow(Icons.Rounded.Download, "Quitar descarga") {
                            downloads.remove(vid)
                            toast("Descarga quitada")
                            onDismiss()
                        }
                    } else {
                        MenuRow(Icons.Rounded.Download, "Descargar") {
                            downloads.enqueue(listOf(original))
                            toast("“${shown.title}” a descargas")
                            onDismiss()
                        }
                    }
                }
                val artistName = shown.artists.substringBefore(",").trim()
                if (ctx != null && artistName.isNotEmpty()) {
                    MenuRow(Icons.Rounded.Person, "Ver artista", artistName) {
                        onDismiss()
                        openArtistByName(ctx, artistName)
                    }
                }
                MenuRow(Icons.Rounded.Info, "Detalles") { details = true }
                extraItems.forEach { (label, action) ->
                    MenuRow(null, label) { onDismiss(); action() }
                }
                MenuRow(Icons.Rounded.Edit, "Personalizar canción") { modDialog = true }
            }
        }
    }

    AddToPlaylistDialog(
        isVisible = addToPlaylist,
        track = original,
        onDismiss = { addToPlaylist = false; onDismiss() },
        onAddComplete = { names -> toast("Añadida a ${names.firstOrNull().orEmpty()}") },
    )
    if (share && vid != null) {
        ShareBottomSheet(
            title = shown.title,
            subtitle = shown.artists,
            thumbnailUrl = shown.thumbnail,
            shareLink = "https://music.youtube.com/watch?v=$vid",
            mediaId = vid,
            onDismiss = { share = false; onDismiss() },
        )
    } else if (share) {
        share = false
        toast("Esta canción no tiene enlace")
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false; onDismiss() },
            title = { Text("Detalles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailLine("Título", shown.title)
                    DetailLine("Artistas", shown.artists)
                    vid?.let { DetailLine("ID", it) }
                    original.durationSeconds?.takeIf { it > 0 }?.let { DetailLine("Duración", formatClock(it.toLong())) }
                }
            },
            confirmButton = { TextButton(onClick = { details = false; onDismiss() }) { Text("Cerrar") } },
        )
    }
    if (modDialog) SongModDialog(original, onDismiss = { modDialog = false; onDismiss() })
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
private fun MenuRow(icon: ImageVector?, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
