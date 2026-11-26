/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.feature.player.RepeatMode
import com.dezzy.app.ui.components.TrackRow

/** Hoja de la cola: la actual resaltada, tocar para saltar, subir/bajar y quitar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlaybackState, player: PlayerController, onDismiss: () -> Unit) {
    val queue = state.queue
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { if (queue.index > 0) list.scrollToItem((queue.index - 1).coerceAtLeast(0)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (queue.radio) "Radio · ${queue.items.size}" else "A continuación · ${queue.items.size}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = queue.shuffled,
                onClick = player::toggleShuffle,
                label = { Text("Aleatorio") },
                leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
            )
            FilterChip(
                selected = queue.repeat != RepeatMode.OFF,
                onClick = player::cycleRepeat,
                label = { Text(if (queue.repeat == RepeatMode.ONE) "Una" else "Repetir") },
                leadingIcon = { Icon(if (queue.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null) },
            )
        }
        TextButton(onClick = player::clearQueue, modifier = Modifier.padding(start = 8.dp)) { Text("Vaciar la cola") }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f), state = list) {
            itemsIndexed(queue.items, key = { _, item -> item.queueEntryId }) { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackRow(
                        track = item.track,
                        onClick = { player.playAt(index) },
                        playing = index == queue.index,
                        modifier = Modifier.weight(1f),
                    )
                    if (index != queue.index) {
                        IconButton(onClick = { player.move(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Subir")
                        }
                        IconButton(onClick = { player.move(index, index + 1) }, enabled = index < queue.items.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Bajar")
                        }
                        IconButton(onClick = { player.removeAt(index) }) { Icon(Icons.Rounded.Close, "Quitar") }
                    }
                }
            }
        }
    }
}
