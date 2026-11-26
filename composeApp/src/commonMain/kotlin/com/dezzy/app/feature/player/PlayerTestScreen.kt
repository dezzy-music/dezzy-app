/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.launch

/**
 * Pantalla provisional de la fase 4 para probar el reproductor en el móvil: buscar,
 * tocar una canción y manejar la cola. La interfaz de verdad llega en la fase 7.
 */
@Composable
fun PlayerTestScreen(player: PlayerController, client: YouTubeMusicClient) {
    val state by player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Buscar") })
            Button(onClick = {
                scope.launch {
                    message = "Buscando…"
                    client.search(query).onSuccess {
                        results = it.songs
                        message = if (it.songs.isEmpty()) "Sin canciones" else null
                    }.onFailure { message = it.message }
                }
            }) { Text("Ir") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(results) { index, track ->
                Column(
                    Modifier.fillMaxWidth().clickable { player.playTracks(results, index, "search:$query") }.padding(vertical = 8.dp),
                ) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val current = state.queue.current?.track
        Text(current?.let { "${it.title} · ${it.artists}" } ?: "Nada sonando", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${clock(state.positionMs)} / ${clock(state.durationMs)}" +
                (if (state.isBuffering) " · cargando" else "") +
                " · ${state.queue.index + 1}/${state.queue.items.size}",
            style = MaterialTheme.typography.bodySmall,
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = player::previous) { Text("⏮") }
            TextButton(onClick = player::togglePlay) { Text(if (state.isPlaying) "⏸" else "▶") }
            TextButton(onClick = player::next) { Text("⏭") }
            TextButton(onClick = player::toggleShuffle) { Text(if (state.queue.shuffled) "🔀 sí" else "🔀 no") }
            TextButton(onClick = player::cycleRepeat) { Text("🔁 ${state.queue.repeat.name}") }
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
