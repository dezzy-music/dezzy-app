/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.feature.player.RepeatMode
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.rememberPreference

/** Cola bloqueada: sin mover ni quitar canciones. */
private val QueueLockedKey = booleanPreferencesKey("queueLocked")

private val SegmentOff = Color(0xFF2B2B2B)
private val CardGrey = Color(0xFF1E1E1E)

/** Hoja de la cola: cabecera, bloqueo, aleatorio/repetir/infinito y la lista con el menú ⋮ de cada canción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlaybackState, player: PlayerController, onDismiss: () -> Unit) {
    val queue = state.queue
    val list = rememberLazyListState()
    val connection = LocalPlayerConnection.current
    var locked by rememberPreference(QueueLockedKey, false)
    var clearMenu by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (queue.index > 0) list.scrollToItem((queue.index - 1).coerceAtLeast(0)) }
    // Como en la app actual, la cola se abre a pantalla completa.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val current = queue.current?.track
        // Cabecera: portada, título, artista y me gusta.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverImage(current?.thumbnail, Modifier.size(56.dp), RoundedCornerShape(10.dp), sizePx = 168)
            Column(Modifier.weight(1f)) {
                Text(
                    current?.title ?: "Cola vacía",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    current?.artists.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connection != null && current != null) {
                val liked by connection.currentSongLiked.collectAsState()
                IconButton(onClick = connection::toggleLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (liked) "Quitar de me gusta" else "Me gusta",
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        // Tarjeta: bloquear, comentarios, menú y resumen.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp)).background(CardGrey).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { locked = !locked }) {
                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (locked) "Desbloquear la cola" else "Bloquear la cola")
            }
            IconButton(onClick = { commentsFor = queue.current?.track?.videoId }) {
                Icon(Icons.Rounded.ChatBubbleOutline, "Comentarios")
            }
            Box {
                IconButton(onClick = { clearMenu = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones") }
                DropdownMenu(expanded = clearMenu, onDismissRequest = { clearMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Vaciar la cola") },
                        enabled = !locked,
                        onClick = { clearMenu = false; player.clearQueue() },
                    )
                }
            }
            val total = queue.items.sumOf { it.track.durationSeconds ?: 0L }
            Text(
                "${queue.items.size} canciones • ${total / 3600}h ${(total % 3600) / 60}m ${total % 60}s",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        // Segmentado: aleatorio, repetir, infinito.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentButton(Icons.Rounded.Shuffle, "Aleatorio", queue.shuffled, player::toggleShuffle)
            SegmentButton(
                if (queue.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repetir",
                queue.repeat != RepeatMode.OFF,
                player::cycleRepeat,
            )
            SegmentButton(Icons.Rounded.AllInclusive, "Infinito", state.autoplay) { player.setAutoplay(!state.autoplay) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Continua reproduciendo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Reproducir automáticamente música similar",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f), state = list) {
            itemsIndexed(queue.items, key = { _, item -> item.queueEntryId }) { index, item ->
                val isCurrent = index == queue.index
                val rowMenu = if (locked || isCurrent) emptyList() else buildList<Pair<String, () -> Unit>> {
                    add("Quitar de la cola" to { player.removeAt(index) })
                    if (index > 0) add("Mover arriba" to { player.move(index, index - 1) })
                    if (index < queue.items.lastIndex) add("Mover abajo" to { player.move(index, index + 1) })
                }
                TrackRow(
                    track = item.track,
                    onClick = { player.playAt(index) },
                    queueCurrent = isCurrent,
                    // La actual va en una tarjeta clara redondeada, como en la app actual.
                    modifier = if (isCurrent) {
                        Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFD6D6D6).copy(alpha = 0.92f))
                    } else Modifier,
                    extraMenu = rowMenu,
                )
            }
        }
    }
    commentsFor?.let { id ->
        com.dezzy.app.ui.player.video.VideoCommentsSheet(id) { commentsFor = null }
    }
}

/** Botón ancho del segmentado: activo blanco con icono negro, inactivo gris oscuro. */
@Composable
private fun RowScope.SegmentButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) Color.White else SegmentOff)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (active) Color.Black else Color.White)
    }
}
