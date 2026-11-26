/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.feature.player.RepeatMode
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.PlayerProgressBar

/**
 * Reproductor expandido provisional de la fase 5a. En la 5c lo sustituye el enrutador de los
 * 13 estilos (Clásico, Cristal, Papel, Póster, Tipografía viva, Aurora, Apple Music, Spotify,
 * iPod, vinilo, Deezer, Dezzy y coche) con el modo vídeo.
 */
@Composable
fun ExpandedPlayer(state: PlaybackState, player: PlayerController, onCollapse: () -> Unit) {
    val track = state.queue.current?.track ?: return
    var showQueue by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.surface).safeDrawingPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar") }
            Text(
                state.queue.source?.substringAfter(':')?.ifBlank { null } ?: "Reproduciendo",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Cola") }
        }
        Spacer(Modifier.weight(1f))
        CoverImage(track.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(20.dp), sizePx = 1080)
        Spacer(Modifier.height(28.dp))
        Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(track.artists, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        PlayerProgressBar(state.positionMs, state.durationMs, player::seekTo, Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = if (state.queue.shuffled) colors.primary else colors.onSurfaceVariant)
            }
            IconButton(onClick = player::previous, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(36.dp)) }
            FilledIconButton(
                onClick = player::togglePlay,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary),
            ) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pausar" else "Reproducir", Modifier.size(40.dp))
            }
            IconButton(onClick = player::next, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(36.dp)) }
            IconButton(onClick = player::cycleRepeat) {
                Icon(
                    if (state.queue.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repetir",
                    tint = if (state.queue.repeat != RepeatMode.OFF) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
    if (showQueue) QueueSheet(state, player) { showQueue = false }
}
