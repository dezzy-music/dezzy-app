/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.source.spotify.SpotifyFeatures
import com.dezzy.app.source.spotify.SpotifyTrack
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Reproduce canciones de Spotify: busca su equivalente en el catálogo de música y lo pone a sonar.
 * [loadingId] es la canción que se está emparejando (para mostrar un indicador en su tarjeta).
 */
class SpotifyTrackPlayer internal constructor(
    private val loadingIdState: MutableState<String?>,
    private val onPlay: (SpotifyTrack) -> Unit,
) {
    val loadingId: String? get() = loadingIdState.value
    fun play(track: SpotifyTrack) = onPlay(track)
}

@Composable
fun rememberSpotifyTrackPlayer(ctx: ScreenContext, features: SpotifyFeatures): SpotifyTrackPlayer {
    val scope = rememberCoroutineScope()
    val loadingId = remember { mutableStateOf<String?>(null) }
    return remember(ctx, features) {
        SpotifyTrackPlayer(loadingId) { track ->
            if (loadingId.value != null) return@SpotifyTrackPlayer
            loadingId.value = track.id
            scope.launch {
                val found = try {
                    features.resolve(track)
                } catch (e: CancellationException) {
                    loadingId.value = null
                    throw e
                }
                loadingId.value = null
                if (found != null) {
                    ctx.player.playTracks(listOf(found), 0, "spotify")
                } else {
                    ctx.toast("No se encontró «${track.title}» en el catálogo de música")
                }
            }
        }
    }
}

private fun SpotifyTrack.artistNames() = artists.joinToString(", ")

/** Título + fila horizontal de portadas. No dibuja nada si [tracks] está vacía. */
@Composable
fun SpotifyTrackShelf(
    title: String,
    tracks: List<SpotifyTrack>,
    player: SpotifyTrackPlayer,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    if (tracks.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title, strapline = subtitle ?: "Spotify")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tracks, key = { it.id }) { track ->
                SpotifyTrackCard(track, loading = player.loadingId == track.id, onClick = { player.play(track) })
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun SpotifyTrackCard(track: SpotifyTrack, loading: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(bottom = 6.dp)) {
        SpotifyCover(track, loading, Modifier.size(140.dp), RoundedCornerShape(12.dp), sizePx = 420)
        Spacer(Modifier.size(6.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artistNames(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila vertical (para resultados de búsqueda). */
@Composable
fun SpotifyTrackRow(track: SpotifyTrack, player: SpotifyTrackPlayer, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable { player.play(track) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpotifyCover(track, player.loadingId == track.id, Modifier.size(48.dp), RoundedCornerShape(6.dp), sizePx = 144)
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                track.artistNames() + (track.album?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SpotifyCover(
    track: SpotifyTrack,
    loading: Boolean,
    modifier: Modifier,
    shape: RoundedCornerShape,
    sizePx: Int,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CoverImage(track.imageUrl, Modifier.fillMaxSize(), shape, sizePx = sizePx)
        if (loading) {
            Box(
                Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
}
