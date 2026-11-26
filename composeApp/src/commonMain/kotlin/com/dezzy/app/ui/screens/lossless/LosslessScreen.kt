/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.lossless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.downloads.DownloadItem
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.downloads.DownloadStatus
import com.dezzy.app.feature.lossless.LosslessAlbum
import com.dezzy.app.feature.lossless.LosslessClient
import com.dezzy.app.feature.lossless.LosslessEnabledKey
import com.dezzy.app.feature.lossless.LosslessExportToPublicKey
import com.dezzy.app.feature.lossless.LosslessQuality
import com.dezzy.app.feature.lossless.LosslessResolverUrlKey
import com.dezzy.app.feature.lossless.LosslessTrack
import com.dezzy.app.feature.lossless.toTrack
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.loadingRows
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val SearchDebounceMs = 400L

/** Lo que hay que descargar desde la hoja: una pista o un álbum entero. */
private data class DownloadRequest(
    val tracks: List<LosslessTrack>,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

private fun formatDuration(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun LosslessQuality.isLossless() = this == LosslessQuality.FLAC

/** Estado de una descarga lossless en texto corto. */
internal fun losslessStatusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "En cola"
    DownloadStatus.RESOLVING -> "Preparando…"
    DownloadStatus.SAVING ->
        if (item.progress > 0f) "Descargando ${(item.progress * 100).toInt()} %" else "Guardando…"
    DownloadStatus.DONE -> "Descargado"
    DownloadStatus.ERROR -> item.error ?: "Error"
}

private val DownloadStatus.isActive: Boolean
    get() = this == DownloadStatus.QUEUED || this == DownloadStatus.RESOLVING || this == DownloadStatus.SAVING

/**
 * Búsqueda y descarga lossless (Deezer vía el resolver del usuario). Busca mientras se escribe,
 * por canciones o por álbumes; cada fila muestra el estado de su descarga.
 */
@Composable
internal fun LosslessScreen(ctx: ScreenContext) {
    val client: LosslessClient = koinInject()
    val downloadManager: DownloadManager = koinInject()
    val controller = remember { LosslessController(client, downloadManager, ctx.prefs, ctx.scope) }

    val enabled by remember { ctx.prefs.value(LosslessEnabledKey, false) }.collectAsState(null)
    val resolverUrl by remember { ctx.prefs.value(LosslessResolverUrlKey, "") }.collectAsState(null)

    if (enabled == null || resolverUrl == null) {
        Column(Modifier.fillMaxSize()) {
            TopSpacer()
            ScreenTitle("Descarga lossless", onBack = { ctx.navigator.back() })
        }
        return
    }
    if (enabled != true || resolverUrl.isNullOrBlank()) {
        LosslessNotConfigured(ctx, enabled == true)
        return
    }
    LosslessSearchContent(ctx, controller)
}

@Composable
private fun LosslessNotConfigured(ctx: ScreenContext, enabled: Boolean) {
    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle("Descarga lossless", onBack = { ctx.navigator.back() })
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = if (!enabled) "Las descargas lossless están desactivadas" else "Falta el servidor de descargas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Actívalas y pon la dirección de tu propio resolver en Ajustes para buscar y guardar en FLAC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.LOSSLESS)) }) { Text("Configurar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LosslessSearchContent(ctx: ScreenContext, controller: LosslessController) {
    val query by controller.query.collectAsState()
    val mode by controller.mode.collectAsState()
    val results by controller.results.collectAsState()
    val albums by controller.albums.collectAsState()
    val isSearching by controller.isSearching.collectAsState()
    val error by controller.error.collectAsState()
    val searchedQuery by controller.searchedQuery.collectAsState()
    val expandedAlbumId by controller.expandedAlbumId.collectAsState()
    val albumTracks by controller.albumTracks.collectAsState()
    val loadingAlbumId by controller.loadingAlbumId.collectAsState()
    val downloads by controller.downloadItems.collectAsState()
    val recents by controller.recents.collectAsState()
    val quality by controller.quality.collectAsState()
    val focusManager = LocalFocusManager.current
    val exportToPublic by remember { ctx.prefs.value(LosslessExportToPublicKey, true) }.collectAsState(true)

    var request by remember { mutableStateOf<DownloadRequest?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        when {
            q.isEmpty() -> controller.search() // limpia resultados
            q.length >= 2 && q != searchedQuery -> {
                delay(SearchDebounceMs)
                controller.search()
            }
        }
    }

    val activeCount = downloads.values.count { it.status.isActive }
    val trimmed = query.trim()
    val showResults = trimmed.length >= 2

    val onPlay: (LosslessTrack) -> Unit = { track ->
        controller.addRecent(searchedQuery)
        ctx.toast("Reproduciendo…")
        ctx.player.playTracks(listOf(track.toTrack()), 0, "lossless")
    }
    val onDownload: (LosslessTrack) -> Unit = { track ->
        request = DownloadRequest(listOf(track), track.title, track.artist, track.cover)
    }
    val onCancel: (LosslessTrack) -> Unit = { track ->
        controller.cancel(track)
        ctx.toast("Descarga parada")
    }
    val onRetry: (LosslessTrack) -> Unit = { track -> controller.retry(track) }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle(
            "Descarga lossless",
            onBack = { ctx.navigator.back() },
            actions = {
                IconButton(onClick = { ctx.navigator.push(Screen.LosslessDownloads) }) {
                    BadgedBox(badge = { if (activeCount > 0) Badge { Text(activeCount.toString()) } }) {
                        Icon(
                            Icons.Rounded.Downloading,
                            contentDescription = if (activeCount > 0) "Descargas, $activeCount en curso" else "Descargas",
                        )
                    }
                }
                IconButton(onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.LOSSLESS)) }) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Ajustes de descarga lossless")
                }
            },
        )

        LosslessSearchField(
            query = query,
            onQueryChange = controller::onQueryChange,
            onSearch = {
                controller.addRecent(query)
                focusManager.clearFocus()
                controller.search()
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DefaultQualityChip(quality = quality, onSelect = controller::setQuality)
            FilterChip(
                selected = mode == LosslessMode.TRACKS,
                onClick = { controller.onModeChange(LosslessMode.TRACKS) },
                label = { Text("Canciones") },
            )
            FilterChip(
                selected = mode == LosslessMode.ALBUMS,
                onClick = { controller.onModeChange(LosslessMode.ALBUMS) },
                label = { Text("Álbumes") },
            )
        }

        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (isSearching && showResults) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
            if (!showResults) {
                losslessEmptyState(
                    recents = recents,
                    downloads = downloads,
                    onRecentClick = { controller.onQueryChange(it) },
                    onClearRecents = controller::clearRecents,
                    onSeeDownloads = { ctx.navigator.push(Screen.LosslessDownloads) },
                )
            } else if (mode == LosslessMode.TRACKS) {
                if (results.isEmpty()) {
                    when {
                        isSearching || searchedQuery != trimmed -> loadingRows(6)
                        error != null -> item(key = "error") { NoResults(trimmed, error) }
                        else -> item(key = "empty") { NoResults(trimmed, null) }
                    }
                } else {
                    items(results, key = { it.id }) { track ->
                        LosslessResultRow(
                            track = track,
                            state = downloads[track.downloadId()],
                            quality = quality,
                            onPlayClick = { onPlay(track) },
                            onDownloadClick = { onDownload(track) },
                            onCancel = { onCancel(track) },
                            onRetry = { onRetry(track) },
                        )
                    }
                }
            } else {
                if (albums.isEmpty()) {
                    when {
                        isSearching || searchedQuery != trimmed -> loadingRows(6)
                        error != null -> item(key = "error") { NoResults(trimmed, error) }
                        else -> item(key = "empty") { NoResults(trimmed, null) }
                    }
                } else {
                    albums.forEach { album ->
                        val expanded = expandedAlbumId == album.id
                        val tracks = albumTracks[album.id].orEmpty()
                        item(key = "album:" + album.id) {
                            LosslessAlbumRow(album = album, expanded = expanded, onClick = { controller.toggleAlbum(album) })
                        }
                        if (expanded) {
                            when {
                                loadingAlbumId == album.id && tracks.isEmpty() -> item(key = "albumLoading:" + album.id) {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }

                                tracks.isEmpty() -> item(key = "albumEmpty:" + album.id) {
                                    Text(
                                        text = "No se pudieron cargar las canciones.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 88.dp, top = 4.dp, bottom = 8.dp),
                                    )
                                }

                                else -> {
                                    item(key = "albumAll:" + album.id) {
                                        FilledTonalButton(
                                            onClick = {
                                                request = DownloadRequest(
                                                    tracks = tracks,
                                                    title = album.title,
                                                    subtitle = "${album.artist} · ${tracks.size} canciones",
                                                    thumbnailUrl = album.cover,
                                                )
                                            },
                                            modifier = Modifier.padding(start = 76.dp, top = 2.dp, bottom = 4.dp),
                                        ) {
                                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Descargar álbum")
                                        }
                                    }
                                    items(tracks, key = { "albumTrack:" + album.id + ":" + it.id }) { track ->
                                        LosslessResultRow(
                                            track = track,
                                            state = downloads[track.downloadId()],
                                            quality = quality,
                                            showCover = false,
                                            onPlayClick = { onPlay(track) },
                                            onDownloadClick = { onDownload(track) },
                                            onCancel = { onCancel(track) },
                                            onRetry = { onRetry(track) },
                                            modifier = Modifier.padding(start = 60.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    request?.let { req ->
        LosslessDownloadSheet(
            request = req,
            defaultQuality = quality,
            exportToPublic = exportToPublic,
            onDismiss = { request = null },
            onConfirm = { q, remember ->
                if (remember) controller.setQuality(q)
                controller.addRecent(searchedQuery)
                if (req.tracks.size == 1) controller.download(req.tracks.first(), q) else controller.downloadAll(req.tracks, q)
                if (req.tracks.size > 1) ctx.toast("${req.tracks.size} canciones en cola")
                request = null
            },
        )
    }
}

@Composable
private fun LosslessSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Canción, artista o álbum") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Borrar búsqueda")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Chip «Deezer · FLAC ▾»: la calidad con la que se preselecciona cada descarga. Qobuz aparece como no disponible. */
@Composable
private fun DefaultQualityChip(quality: LosslessQuality, onSelect: (LosslessQuality) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("Deezer · ${quality.label}") },
            leadingIcon = if (quality.isLossless()) {
                { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp)) }
            } else null,
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
            modifier = Modifier.semantics {
                contentDescription = "Calidad predeterminada: Deezer, ${quality.label}. Cambiar"
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = "Deezer",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LosslessQuality.entries.forEach { q ->
                DropdownMenuItem(
                    text = { Text(q.label) },
                    onClick = {
                        onSelect(q)
                        open = false
                    },
                    trailingIcon = if (q == quality) {
                        { Icon(Icons.Rounded.Done, null) }
                    } else null,
                )
            }
            Text(
                text = "Qobuz · No disponible",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.losslessEmptyState(
    recents: List<String>,
    downloads: Map<String, DownloadItem>,
    onRecentClick: (String) -> Unit,
    onClearRecents: () -> Unit,
    onSeeDownloads: () -> Unit,
) {
    if (recents.isNotEmpty()) {
        item(key = "recentsHeader") {
            SectionHeader(title = "Búsquedas recientes", action = "Borrar", onAction = onClearRecents)
        }
        item(key = "recents") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                recents.forEach { q ->
                    AssistChip(
                        onClick = { onRecentClick(q) },
                        label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }

    val latest = downloads.values.sortedByDescending { it.addedAt }.take(5)
    if (latest.isNotEmpty()) {
        item(key = "latestHeader") {
            SectionHeader(title = "Últimas descargas", action = "Ver todas", onAction = onSeeDownloads)
        }
        items(latest, key = { "latest:" + it.id }) { LatestDownloadRow(it) }
    }

    if (recents.isEmpty() && latest.isEmpty()) {
        item(key = "hint") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Busca una canción o un álbum", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Toca una fila para escucharla en FLAC, o la flecha para guardarla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun LatestDownloadRow(item: DownloadItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        CoverImage(
            item.track.thumbnail,
            Modifier.size(44.dp),
            RoundedCornerShape(8.dp),
            sizePx = 132,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (item.status == DownloadStatus.DONE) "Descargado · Deezer" else losslessStatusText(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == DownloadStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun NoResults(query: String, error: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (error != null) "No se pudo buscar" else "Sin resultados para «$query»",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = error ?: "Prueba con otro nombre o revisa la conexión.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmallBadge(text: String, emphasized: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun LosslessResultRow(
    track: LosslessTrack,
    state: DownloadItem?,
    quality: LosslessQuality,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick, onClickLabel = "Escuchar en FLAC")
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCover) {
            CoverImage(track.cover, Modifier.size(52.dp), RoundedCornerShape(8.dp), sizePx = 156)
        }
        Column(Modifier.weight(1f).padding(start = if (showCover) 12.dp else 0.dp, end = 8.dp)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val subtitle = listOfNotNull(track.artist.ifBlank { null }, track.album?.takeIf { showCover }).joinToString(" · ")
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                SmallBadge(if (quality.isLossless()) "FLAC" else "MP3", emphasized = true)
                val duration = formatDuration(track.durationSec)
                if (duration.isNotEmpty()) {
                    Text(duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusLabel(state)
            }
        }
        DownloadStatusAction(state, onDownloadClick, onCancel, onRetry)
    }
}

@Composable
private fun StatusLabel(state: DownloadItem?) {
    if (state == null) return
    val color = when (state.status) {
        DownloadStatus.SAVING, DownloadStatus.DONE -> MaterialTheme.colorScheme.primary
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = if (state.status == DownloadStatus.SAVING && state.progress > 0f) {
        "${(state.progress * 100).toInt()} %"
    } else {
        losslessStatusText(state)
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Acción al final de la fila: descargar, anillo de progreso (tocar para parar), hecho o reintentar. */
@Composable
private fun DownloadStatusAction(
    state: DownloadItem?,
    onDownloadClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state == null -> IconButton(onClick = onDownloadClick) {
            Icon(Icons.Rounded.Download, contentDescription = "Descargar")
        }

        state.status.isActive -> IconButton(onClick = onCancel) {
            Box(contentAlignment = Alignment.Center) {
                if (state.status == DownloadStatus.SAVING && state.progress > 0f) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.5.dp)
                }
                Icon(Icons.Rounded.Close, contentDescription = "Parar descarga", modifier = Modifier.size(14.dp))
            }
        }

        state.status == DownloadStatus.DONE -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Done, contentDescription = "Descargado", tint = MaterialTheme.colorScheme.primary)
        }

        else -> IconButton(onClick = onRetry) {
            Icon(Icons.Rounded.Replay, contentDescription = "Reintentar descarga", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LosslessAlbumRow(album: LosslessAlbum, expanded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = if (expanded) "Ocultar canciones" else "Ver canciones")
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        CoverImage(album.cover, Modifier.size(60.dp), RoundedCornerShape(8.dp), sizePx = 180)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                album.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                listOfNotNull(
                    album.artist.takeIf { it.isNotBlank() },
                    album.trackCount?.takeIf { it > 0 }?.let { if (it == 1) "1 canción" else "$it canciones" },
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                SmallBadge("FLAC", emphasized = true)
            }
        }
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Hoja de descarga: proveedor (sólo Deezer; Qobuz no disponible), calidad, dónde se guarda y
 * «Recordar como predeterminada». Sirve para una pista o para un álbum entero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LosslessDownloadSheet(
    request: DownloadRequest,
    defaultQuality: LosslessQuality,
    exportToPublic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LosslessQuality, Boolean) -> Unit,
) {
    var quality by remember { mutableStateOf(defaultQuality) }
    var rememberChoice by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                CoverImage(request.thumbnailUrl, Modifier.size(56.dp), RoundedCornerShape(8.dp), sizePx = 168)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        request.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        request.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                "Proveedor",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
            ) {
                RadioButton(selected = true, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                Text("Deezer", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "Hasta FLAC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
            ) {
                RadioButton(selected = false, onClick = null, enabled = false, modifier = Modifier.padding(horizontal = 12.dp))
                Text(
                    "Qobuz",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "No disponible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }

            Text(
                "Calidad",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp).semantics { heading() },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LosslessQuality.entries.forEach { q ->
                    FilterChip(
                        selected = quality == q,
                        onClick = { quality = q },
                        label = { Text(q.label, maxLines = 1) },
                        leadingIcon = if (q.isLossless()) {
                            { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }

            Text(
                text = if (exportToPublic) {
                    "Se guarda en Música/Dezzy y aparece en tus Me gusta."
                } else {
                    "Se guarda en tu biblioteca y aparece en tus Me gusta."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .toggleable(value = rememberChoice, role = Role.Checkbox, onValueChange = { rememberChoice = it })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp),
            ) {
                Checkbox(checked = rememberChoice, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                Text("Recordar como predeterminada", style = MaterialTheme.typography.bodyLarge)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 10.dp),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(quality, rememberChoice) }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (quality.isLossless()) "Descargar FLAC" else "Descargar MP3",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
