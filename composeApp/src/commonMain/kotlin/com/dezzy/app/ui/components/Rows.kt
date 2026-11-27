/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.downloads.DownloadStatus
import com.dezzy.app.feature.personalize.SongMods
import com.dezzy.app.feature.personalize.withMods
import org.koin.compose.koinInject

/** «m:ss» o «h:mm:ss». */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}

/** Título de sección (22 sp) con acción opcional a la derecha («Ver todo»). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    strapline: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            strapline?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Fila de canción: carátula, título, artista · duración y menú. */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    number: Int? = null,
    showCover: Boolean = true,
    onMore: (() -> Unit)? = null,
    extraMenu: List<Pair<String, () -> Unit>> = emptyList(),
    /** Canción actual en la cola: ▶ sobre la carátula y título tenue (va sobre una tarjeta clara). */
    queueCurrent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val songMods: SongMods = koinInject()
    val mods by songMods.all.collectAsState(emptyMap())
    val original = track
    @Suppress("NAME_SHADOWING")
    val track = remember(original, mods) { original.withMods(mods) }
    var menuOpen by remember { mutableStateOf(false) }
    // Deslizar: izquierda = añadir a la cola, derecha = reproducir a continuación (ajuste SwipeSongActionsKey).
    val swipeEnabled by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.SwipeSongActionsKey, true)
    val swipeCtx = LocalScreenContext.current
    val swipeConn = com.dezzy.app.ui.player.kit.LocalPlayerConnection.current
    val swipeController = swipeCtx?.player ?: swipeConn?.controller
    val swipeToast: (String) -> Unit = { msg -> swipeCtx?.toast(msg) ?: swipeConn?.toast?.invoke(msg) }
    val useSwipe = swipeEnabled && !queueCurrent && swipeController != null && original.videoId != null
    val swipeState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> {
                    swipeController?.addToQueue(original); swipeToast("Añadida a la cola")
                }
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> {
                    swipeController?.playNext(original); swipeToast("Se reproducirá a continuación")
                }
                else -> Unit
            }
            // false: la fila vuelve a su sitio tras la acción.
            false
        },
    )
    val rowBody: @Composable (Modifier) -> Unit = { rowModifier ->
    Row(
        rowModifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (playing) Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary)
                else Text("$number", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (showCover) {
            Box {
                CoverImage(track.thumbnail, Modifier.size(48.dp), RoundedCornerShape(6.dp), sizePx = 144)
                if (queueCurrent) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White) }
                } else if (playing && number == null) {
                    Box(Modifier.size(48.dp).padding(14.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    queueCurrent -> Color.White.copy(alpha = 0.6f)
                    playing -> colors.primary
                    else -> colors.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                track.artists.ifBlank { null },
                track.durationSeconds?.takeIf { it > 0 }?.let(::formatClock),
            ).joinToString(" • ")
            val downloaded = track.videoId?.let { id ->
                val manager = koinInject<DownloadManager>()
                val status by remember(id) { manager.status(id) }.collectAsState(null)
                status == DownloadStatus.DONE
            } == true
            if (meta.isNotEmpty() || downloaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (downloaded) {
                        Icon(Icons.Rounded.DownloadDone, "Descargada", Modifier.size(14.dp), tint = colors.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(meta, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "Más opciones", tint = colors.onSurfaceVariant) }
        } else {
            // Menú «⋮» común: hoja de canción (SongMenuSheet).
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones", tint = colors.onSurfaceVariant) }
        }
    }
    }
    if (useSwipe) {
        androidx.compose.material3.SwipeToDismissBox(
            state = swipeState,
            modifier = modifier,
            backgroundContent = {
                val toStart = swipeState.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
                val active = swipeState.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled
                if (active) {
                    Box(
                        Modifier.fillMaxSize().background(colors.primaryContainer).padding(horizontal = 24.dp),
                        contentAlignment = if (toStart) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Icon(
                            if (toStart) Icons.AutoMirrored.Rounded.PlaylistPlay else Icons.AutoMirrored.Rounded.QueueMusic,
                            if (toStart) "Reproducir a continuación" else "Añadir a la cola",
                            tint = colors.onPrimaryContainer,
                        )
                    }
                }
            },
        ) { rowBody(Modifier) }
    } else rowBody(modifier)
    if (menuOpen) SongMenuSheet(original, onDismiss = { menuOpen = false }, extraItems = extraMenu)
}

/** Tarjeta de álbum, playlist o artista (redonda) para los carruseles. */
@Composable
fun ItemCard(item: MusicItem, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 148.dp) {
    val round = item.type == MusicItemType.ARTIST
    Column(modifier.width(size).clickable(onClick = onClick)) {
        CoverImage(
            item.thumbnail,
            Modifier.size(size),
            if (round) CircleShape else RoundedCornerShape(12.dp),
            sizePx = 360,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Esqueleto de una fila de canción. */
@Composable
fun TrackRowPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerBox(Modifier.size(48.dp), RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp), RoundedCornerShape(4.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.45f).height(12.dp), RoundedCornerShape(4.dp))
        }
    }
}
