/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pastilla «Canción / Video» al estilo de YouTube Music, sobre la carátula. «Video» pasa al
 * videoclip con el modo vídeo; «Canción» vuelve al audio.
 */
@Composable
fun SongVideoToggle(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    /** Canvas de la canción actual; sin uso, se conserva por los sitios que lo pasan. */
    canvas: CanvasArtwork? = null,
) {
    val videoModeActive by playerConnection.videoModeActive.collectAsState()
    val videoModeLoading by playerConnection.videoModeLoading.collectAsState()
    val songSelected = !videoModeActive && !videoModeLoading

    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), modifier = modifier) {
        Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { if (!songSelected) playerConnection.disableVideoMode() },
                shape = CircleShape,
                color = if (songSelected) Color.White else Color.Transparent,
            ) {
                Text(
                    text = "Canción",
                    color = if (songSelected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                )
            }
            Surface(
                onClick = { if (!videoModeLoading && songSelected) playerConnection.enableVideoMode() },
                shape = CircleShape,
                color = if (!songSelected) Color.White else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)) {
                    if (videoModeLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = if (!songSelected) Color.Black else Color.White,
                        )
                    } else {
                        Text(
                            text = "Video",
                            color = if (!songSelected) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
