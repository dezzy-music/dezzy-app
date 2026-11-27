/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Track
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.Load
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.emptyRow
import com.dezzy.app.ui.screens.errorRow
import com.dezzy.app.ui.screens.loadingRows
import com.dezzy.app.ui.screens.rememberLoad
import kotlinx.coroutines.flow.first

/** Filtro de tipo de los resultados: todo o sólo vídeos. */
@Composable
internal fun SearchTypeChips(videosOnly: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(
            selected = !videosOnly,
            onClick = { onChange(false) },
            label = { Text("Todo", maxLines = 1) },
            leadingIcon = if (!videosOnly) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
        )
        FilterChip(
            selected = videosOnly,
            onClick = { onChange(true) },
            label = { Text("Vídeos", maxLines = 1) },
            leadingIcon = {
                Icon(
                    if (videosOnly) Icons.Rounded.Check else Icons.Rounded.SlowMotionVideo,
                    null,
                    Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

/** Resultados del filtro «Vídeos»: búsqueda sólo de vídeos (primera página). */
internal fun LazyListScope.videoSearchResults(ctx: ScreenContext, query: String, result: Load<List<Track>>) {
    when (result) {
        Load.Loading -> loadingRows()
        is Load.Failed -> errorRow(result.message)
        is Load.Done -> {
            val videos = result.value
            if (videos.isEmpty()) {
                emptyRow("Sin vídeos para «$query»")
            } else {
                trackList(videos, ctx, "video:search")
            }
        }
    }
}

/** Carga de la búsqueda de vídeos; llamar desde composición. */
@Composable
internal fun rememberVideoSearch(ctx: ScreenContext, query: String, enabled: Boolean): Load<List<Track>> =
    rememberLoad(if (enabled) query else null) {
        if (!enabled) Result.success(emptyList())
        else ctx.client.searchVideos(query).map { it.items }
    }

private fun LazyListScope.trackList(tracks: List<Track>, ctx: ScreenContext, source: String) {
    items(tracks.size, key = { "$source:$it:${tracks[it].videoId}" }) { i ->
        val t = tracks[i]
        TrackRow(
            t,
            onClick = { ctx.player.playTracks(tracks, i, source) },
            playing = t.videoId == ctx.playingId,
        )
    }
}

/** «Los más vistos»: top de vídeos musicales del país elegido; si no hay chart, búsqueda de vídeos. */
@Composable
fun TopVideosScreen(ctx: ScreenContext, onBack: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onBack)
    val videos = rememberLoad("top_videos") {
        val country = ctx.prefs.contentCountry.first()
        val chart = ctx.client.getChartTracks(country, 50).getOrNull().orEmpty()
        if (chart.isNotEmpty()) Result.success(chart)
        else ctx.client.searchVideos("top music videos").map { it.items }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopSpacer() }
        item { ScreenTitle("Los más vistos", onBack = onBack) }
        when (val v = videos) {
            Load.Loading -> loadingRows()
            is Load.Failed -> errorRow(v.message)
            is Load.Done -> if (v.value.isEmpty()) emptyRow("No hay vídeos disponibles ahora") else trackList(v.value, ctx, "video:top")
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
