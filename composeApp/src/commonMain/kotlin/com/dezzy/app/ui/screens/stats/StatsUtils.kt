/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.source.youtube.YouTubeEndpoints
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch

/** «2 h 5 min» o «35 min». */
internal fun formatMinutes(ms: Long): String {
    val total = ms / 60_000L
    val h = total / 60
    val m = total % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** Un nombre de artista (el historial sólo guarda el texto) → ficha del artista. */
internal fun openArtistByName(ctx: ScreenContext, name: String) {
    ctx.scope.launch {
        ctx.client.findArtistId(name)
            .onSuccess { id ->
                if (id != null) ctx.navigator.push(Screen.Artist(id)) else ctx.toast("No encontré a $name")
            }
            .onFailure { ctx.toast("No se pudo abrir al artista") }
    }
}

/** Un álbum del historial (sólo título y artista) → ficha del álbum. */
internal fun openAlbumByName(ctx: ScreenContext, album: String, artist: String?) {
    ctx.scope.launch {
        ctx.client.search("$album ${artist.orEmpty()}".trim(), YouTubeEndpoints.FILTER_ALBUMS)
            .onSuccess { result ->
                val found = result.albums.firstOrNull()
                if (found != null) ctx.navigator.push(Screen.Album(found.id)) else ctx.toast("No encontré el álbum")
            }
            .onFailure { ctx.toast("No se pudo abrir el álbum") }
    }
}

/** Cifra pequeña con su rótulo (racha, hoy, semana…). */
@Composable
internal fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
