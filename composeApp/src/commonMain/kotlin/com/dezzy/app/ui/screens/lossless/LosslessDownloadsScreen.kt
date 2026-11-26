/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.lossless

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.downloads.DownloadItem
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.downloads.DownloadStatus
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import org.koin.compose.koinInject

/**
 * Cola de descargas lossless: estado de cada descarga (en cola, preparando, progreso, hecha, error).
 * Lista los [DownloadItem] con `lossless != null`.
 */
@Composable
internal fun LosslessDownloadsScreen(ctx: ScreenContext) {
    val manager: DownloadManager = koinInject()
    val state by manager.state.collectAsState()

    val ordered = state.items.filter { it.lossless != null }.sortedWith(
        compareBy(
            { statusOrder(it.status) },
            { it.track.title },
        ),
    )
    val hasFailed = ordered.any { it.status == DownloadStatus.ERROR }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle(
            "Cola de descargas",
            onBack = { ctx.navigator.back() },
            actions = {
                if (hasFailed) {
                    IconButton(onClick = { manager.retryFailed() }) {
                        Icon(Icons.Rounded.Replay, contentDescription = "Reintentar fallidas")
                    }
                }
            },
        )
        Box(Modifier.fillMaxSize()) {
            if (ordered.isEmpty()) {
                Text(
                    text = "No hay descargas todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                    items(ordered, key = { it.id }) { item ->
                        LosslessDownloadRow(
                            item = item,
                            onRetry = { manager.retry(item.id) },
                            onRemove = { manager.remove(item.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun statusOrder(status: DownloadStatus): Int = when (status) {
    DownloadStatus.SAVING, DownloadStatus.RESOLVING, DownloadStatus.QUEUED -> 0
    DownloadStatus.ERROR -> 1
    DownloadStatus.DONE -> 2
}

@Composable
private fun LosslessDownloadRow(item: DownloadItem, onRetry: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(item.track.thumbnail, Modifier.size(48.dp), RoundedCornerShape(6.dp), sizePx = 144)
        Column(Modifier.weight(1f)) {
            Text(
                item.track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Deezer · " + losslessStatusText(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == DownloadStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (item.status == DownloadStatus.SAVING && item.progress > 0f) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        when (item.status) {
            DownloadStatus.DONE -> Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            DownloadStatus.ERROR -> {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Replay, contentDescription = "Reintentar", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = "Quitar de la cola")
                }
            }
            else -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}
