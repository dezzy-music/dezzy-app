/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.canvas.CANVAS_ARTIST_DEFAULT
import com.dezzy.app.feature.canvas.CanvasArtistEnabledKey
import com.dezzy.app.feature.canvas.CanvasResolver
import com.dezzy.app.ui.player.kit.CanvasArtwork
import com.dezzy.app.ui.player.kit.rememberPreference
import org.koin.compose.koinInject

/** Altura del vídeo de la cabecera del artista (un poco más nítido que el canvas de la canción). */
private const val ARTIST_CANVAS_QUALITY = 480

/**
 * Canvas del artista: trozo del videoclip de su canción más destacada ([topSong], la primera de su
 * página), mudo y en bucle, que se mueve siempre aunque no suene nada. No pinta nada si el ajuste
 * «Canvas del artista» está apagado o si la canción no tiene videoclip; ponlo sobre la foto del artista.
 */
@Composable
fun ArtistCanvas(topSong: Track?, modifier: Modifier = Modifier) {
    val resolver: CanvasResolver = koinInject()
    val enabled by rememberPreference(CanvasArtistEnabledKey, CANVAS_ARTIST_DEFAULT)
    var canvas by remember(topSong?.videoId, enabled) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(topSong?.videoId, enabled) {
        canvas = null
        if (!enabled || topSong == null) return@LaunchedEffect
        canvas = resolver.forTrack(topSong, ARTIST_CANVAS_QUALITY)
    }
    val ready = canvas ?: return
    // Sólo el videoclip: la portada animada de una canción no sirve de foto de artista.
    val url = ready.videoUrl ?: return
    CanvasVideo(
        url = url,
        loopStartMs = ready.loopStartMs,
        loopLengthMs = ready.loopLengthMs,
        playing = true,
        modifier = modifier,
    )
}
