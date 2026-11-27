/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.musicmap

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.musicmap.MusicMapGenres
import com.dezzy.app.feature.musicmap.MusicMapStore
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.emptyRow
import com.dezzy.app.ui.screens.loadingRows
import org.koin.compose.koinInject

/** Qué géneros suenan en tu biblioteca: reparto por género y filtro para escuchar cada uno. */
@Composable
fun LibraryGenresScreen(ctx: ScreenContext) {
    val store = koinInject<MusicMapStore>()
    val classifier = koinInject<MusicMapGenres>()

    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    val genres = remember { mutableStateMapOf<String, String>() }
    var classifying by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val rows = store.libraryTracks(400)
        val list = rows.map { Track(it.videoId, it.title, it.artists, thumbnail = it.thumbnail) }
        tracks = list
        classifying = true
        try {
            classifier.classify(list.map { it.firstArtist() }.filter { it.isNotBlank() }.distinct().take(150)) { partial ->
                genres.putAll(partial)
            }
        } finally {
            classifying = false
        }
    }

    fun genreOf(t: Track): String? = genres[MusicMapGenres.key(t.firstArtist())]

    val shares by remember {
        derivedStateOf { tracks?.let { list -> genreShares(list) { genreOf(it) } }.orEmpty() }
    }
    val shown by remember {
        derivedStateOf {
            val list = tracks.orEmpty()
            val f = filter
            if (f == null) list else list.filter { matchesGenre(genreOf(it), f, shares) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopSpacer() }
        item { ScreenTitle("Géneros de tu biblioteca", onBack = { ctx.navigator.back() }) }
        val list = tracks
        if (list == null) {
            loadingRows()
        } else if (list.isEmpty()) {
            emptyRow("Aún no hay canciones en tu biblioteca. Dale a Me gusta o escucha música y vuelve.")
        } else {
            item(key = "shares") {
                GenreShareSection(
                    shares = shares,
                    loading = classifying,
                    selected = filter,
                    onSelect = { filter = it },
                    emptyText = "Aún no sabemos los géneros de tus canciones",
                    loadingText = "Analizando los géneros de tu biblioteca…",
                )
            }
            item(key = "play") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cancionesLabel(shown.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { ctx.player.playTracks(shown, 0, filter?.let { "Género: $it" } ?: "Tu biblioteca") },
                        enabled = shown.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Text("Reproducir")
                    }
                }
            }
            items(shown.size, key = { "${shown[it].videoId}_$it" }) { i ->
                val song = shown[i]
                TrackRow(
                    track = song,
                    onClick = { ctx.player.playTracks(shown, i, filter?.let { "Género: $it" } ?: "Tu biblioteca") },
                    playing = song.videoId != null && song.videoId == ctx.playingId,
                )
            }
        }
    }
}
