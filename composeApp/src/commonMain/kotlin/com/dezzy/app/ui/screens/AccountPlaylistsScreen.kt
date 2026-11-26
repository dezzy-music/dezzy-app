/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.model.Playlist
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen

/** «Tus listas de YouTube Music»: las de la cuenta con sesión, en rejilla de dos. */
@Composable
fun AccountPlaylistsScreen(ctx: ScreenContext) {
    var version by remember { mutableIntStateOf(0) }
    val load = rememberLoad(version) { runCatching { ctx.client.getLibraryPlaylists() } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopSpacer()
            ScreenTitle("Tus listas de YouTube Music", onBack = { ctx.navigator.back() })
        }
        when (load) {
            is Load.Loading -> loadingRows()
            is Load.Failed -> {
                errorRow(load.message)
                item {
                    Button(onClick = { version++ }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Reintentar") }
                }
            }
            is Load.Done -> {
                val lists = load.value
                if (lists.isEmpty()) {
                    emptyRow("No encontramos listas en tu cuenta de YouTube Music.")
                } else {
                    items(lists.chunked(2)) { rowLists ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowLists.forEach { AccountPlaylistCard(it, Modifier.weight(1f)) { ctx.navigator.push(Screen.Playlist(it.id)) } }
                            if (rowLists.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun AccountPlaylistCard(playlist: Playlist, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).clickable(onClick = onClick)) {
        CoverImage(playlist.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), shape, sizePx = 480)
        Text(
            playlist.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val subtitle = playlist.author ?: if (playlist.trackCount > 0) "${playlist.trackCount} canciones" else null
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
