/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.ThinProgress
import com.dezzy.app.ui.theme.dezzyStyledSurface

/**
 * Mini reproductor en píldora sobre la barra inferior: carátula, título, artista, reproducir
 * y siguiente. Un toque abre el reproductor; deslizar a los lados cambia de canción.
 */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    player: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.queue.current?.track ?: return
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .dezzyStyledSurface(shape)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag < -120f) player.next() else if (drag > 120f) player.previous()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, amount -> drag += amount }
            },
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoverImage(track.thumbnail, Modifier.size(44.dp), RoundedCornerShape(8.dp), sizePx = 144)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artists,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = player::togglePlay) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (state.isPlaying) "Pausar" else "Reproducir",
                            )
                        }
                    }
                }
                IconButton(onClick = player::next) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
            }
            val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            ThinProgress(fraction, Modifier.padding(horizontal = 12.dp))
        }
    }
}
