/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.video.VideoPipState
import com.dezzy.app.feature.video.VideoPlayerHandle
import com.dezzy.app.feature.video.VideoResizeMode
import com.dezzy.app.feature.video.VideoResizeModeKey
import com.dezzy.app.ui.components.glass.DezzyIconButton as IconButton
import com.dezzy.app.ui.components.glass.DezzyTextButton as TextButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.player.kit.AddToPlaylistDialog
import com.dezzy.app.ui.player.kit.DevicesButton
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.VideoFullscreenDialog
import com.dezzy.app.ui.player.kit.VideoSurface
import com.dezzy.app.ui.player.kit.makeTimeString
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val FeedBackground = Color(0xFF0C0C0F)

/**
 * "Solo videos": el video fijo arriba y debajo una lista de videos recomendados para la
 * canción actual (radio + búsqueda de videos), con filtros. Al bajar, el video pasa a una
 * ventanita flotante; al llegar al final se cargan más. Tocar un video lo reproduce (radio
 * en modo video) y la lista se recalcula para el nuevo.
 *
 * Búsqueda (boceto B, como YouTube): la lupa de arriba convierte la barra en un campo; debajo
 * salen búsquedas recientes y sugerencias, y al buscar, sólo videos. Mientras tanto el video
 * sigue sonando en la ventanita flotante.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoFeedScreen(
    playerConnection: PlayerConnection,
    videoPlayer: VideoPlayerHandle?,
    loading: Boolean,
    recommendations: VideoRecommendations,
    filter: VideoRecoFilter,
    artistName: String,
    onFilterChange: (VideoRecoFilter) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val resizeModePref by rememberEnumPreference(VideoResizeModeKey, VideoResizeMode.FIT)
    val playback = rememberVideoPlaybackState(videoPlayer)

    val listState = rememberLazyListState()
    var videoHeightPx by remember { mutableIntStateOf(0) }
    var floatingDismissed by remember { mutableStateOf(false) }
    val scrolledPastVideo by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                (videoHeightPx > 0 && listState.firstVisibleItemScrollOffset > videoHeightPx * 0.65f)
        }
    }

    // ── Búsqueda ─────────────────────────────────────────────────────────
    val searchResults = remember { VideoSearchResults(scope, playerConnection.client) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val (recentSearches, updateRecentSearches) = rememberVideoRecentSearches()
    // Resultados sólo mientras el campo coincide con lo buscado; al editar, vuelven las sugerencias.
    val showSearchResults = searchResults.query != null && searchQuery.trim() == searchResults.query
    val runSearch: (String) -> Unit = { q ->
        val text = q.trim()
        if (text.isNotEmpty()) {
            searchQuery = text
            updateRecentSearches(text)
            searchResults.search(text)
        }
    }
    val closeSearch: () -> Unit = {
        searchOpen = false
        searchQuery = ""
    }
    PlatformBackHandler(enabled = searchOpen) { closeSearch() }

    // ── Pantalla completa (horizontal, como en el modo video) ────────────
    var fullscreen by remember { mutableStateOf(false) }
    val inPip by VideoPipState.inPip.collectAsState()

    LaunchedEffect(scrolledPastVideo, searchOpen) {
        if (!scrolledPastVideo || searchOpen) floatingDismissed = false
    }
    val floatingShown = (scrolledPastVideo || searchOpen) && !floatingDismissed && !fullscreen

    // Carga más al acercarse al final.
    LaunchedEffect(listState, recommendations) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { recommendations.loadMore() }
    }

    // Al cambiar de video la lista se recalcula: vuelve arriba.
    LaunchedEffect(mediaMetadata?.id) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }

    var playlistTarget by remember { mutableStateOf<Track?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FeedBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Barra superior ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchOpen) {
                    IconButton(onClick = closeSearch) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Cerrar búsqueda",
                            tint = Color.White,
                        )
                    }
                    VideoSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = runSearch,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver al video",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "Solo videos",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar videos",
                            tint = Color.White,
                        )
                    }
                    DevicesButton(tint = Color.White)
                    IconButton(onClick = onQueueClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Cola de reproducción",
                            tint = Color.White,
                        )
                    }
                }
            }

            if (searchOpen) {
                if (showSearchResults) {
                    val resultsState = rememberLazyListState()
                    LaunchedEffect(resultsState, searchResults.query) {
                        snapshotFlow {
                            val info = resultsState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
                        }
                            .distinctUntilChanged()
                            .filter { it }
                            .collect { searchResults.loadMore() }
                    }
                    LazyColumn(
                        state = resultsState,
                        contentPadding = PaddingValues(bottom = 140.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        item(key = "results_label") {
                            Text(
                                text = "Resultados para «${searchResults.query.orEmpty()}»",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }
                        videoSearchStatus(searchResults)
                        if (!searchResults.loading) {
                            items(searchResults.items, key = { "q_" + it.videoId.orEmpty() }, contentType = { "video" }) { item ->
                                VideoFeedCard(
                                    item = item,
                                    onClick = {
                                        playRelatedVideo(playerConnection, item)
                                        closeSearch()
                                        scope.launch { listState.scrollToItem(0) }
                                    },
                                    onPlayNext = {
                                        playerConnection.controller.playNext(item)
                                        playerConnection.toast("Se reproducirá a continuación")
                                    },
                                    onAddToQueue = {
                                        playerConnection.controller.addToQueue(item)
                                        playerConnection.toast("Añadido a la cola")
                                    },
                                    onSave = { playlistTarget = item },
                                )
                            }
                        }
                        if (searchResults.loadingMore) {
                            item(key = "results_more") {
                                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                } else {
                    VideoSearchSuggestions(
                        client = playerConnection.client,
                        query = searchQuery,
                        recent = recentSearches,
                        onClearRecent = { updateRecentSearches(null) },
                        onSearch = runSearch,
                        onFill = { searchQuery = "$it " },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            } else
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // ── Reproductor ──────────────────────────────────────────
                item(key = "player", contentType = "player") {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                                .onSizeChanged { videoHeightPx = it.height }
                                .clickable(enabled = videoPlayer != null) {
                                    videoPlayer?.let { it.playWhenReady = !it.playWhenReady }
                                },
                        ) {
                            VideoSurface(
                                player = videoPlayer,
                                active = !floatingShown && !(fullscreen && !inPip),
                                resizeMode = resizeModePref,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (loading || playback.buffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                                )
                            }
                            FloatingWindowButton(
                                icon = Icons.Rounded.Fullscreen,
                                contentDescription = "Pantalla completa",
                                onClick = { fullscreen = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = mediaMetadata?.title.orEmpty(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = mediaMetadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { videoPlayer?.let { it.playWhenReady = !it.playWhenReady } },
                                enabled = videoPlayer != null,
                            ) {
                                Icon(
                                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = { playerConnection.seekToNext() }) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                // ── Filtros ──────────────────────────────────────────────
                stickyHeader(key = "filters", contentType = "filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FeedBackground)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoRecoFilter.entries.forEach { option ->
                            FeedFilterChip(
                                label = if (option == VideoRecoFilter.ARTIST && artistName.isNotBlank()) artistName else option.label,
                                selected = option == filter,
                                onClick = { onFilterChange(option) },
                            )
                        }
                    }
                }

                // ── Lista ────────────────────────────────────────────────
                when {
                    recommendations.loading -> items(3, key = { "placeholder$it" }, contentType = { "placeholder" }) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                            Box(
                                Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(0.7f)
                                    .height(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                        }
                    }

                    recommendations.items.isEmpty() -> item(key = "empty", contentType = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (recommendations.failed) "No se pudieron cargar los videos" else "No hay videos para este filtro",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (recommendations.failed) "Revisa la conexión e inténtalo de nuevo." else "Prueba con otro filtro.",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (recommendations.failed) {
                                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                                    Text("Reintentar", color = Color.White)
                                }
                            }
                        }
                    }

                    else -> items(recommendations.items, key = { it.videoId.orEmpty() }, contentType = { "video" }) { item ->
                        VideoFeedCard(
                            item = item,
                            onClick = {
                                playRelatedVideo(playerConnection, item)
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            onPlayNext = {
                                playerConnection.controller.playNext(item)
                                playerConnection.toast("Se reproducirá a continuación")
                            },
                            onAddToQueue = {
                                playerConnection.controller.addToQueue(item)
                                playerConnection.toast("Añadido a la cola")
                            },
                            onSave = { playlistTarget = item },
                        )
                    }
                }

                if (recommendations.loadingMore) {
                    item(key = "more", contentType = "more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        // ── Ventanita flotante ───────────────────────────────────────────
        AnimatedVisibility(
            visible = floatingShown,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 176.dp, height = 99.dp)
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable {
                        if (searchOpen) closeSearch() else scope.launch { listState.animateScrollToItem(0) }
                    },
            ) {
                VideoSurface(
                    player = videoPlayer,
                    active = floatingShown,
                    resizeMode = resizeModePref,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                ) {
                    FloatingWindowButton(
                        icon = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                        onClick = { videoPlayer?.let { it.playWhenReady = !it.playWhenReady } },
                    )
                    FloatingWindowButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cerrar ventana",
                        onClick = {
                            videoPlayer?.playWhenReady = false
                            floatingDismissed = true
                        },
                    )
                }
            }
        }
    }

    if (fullscreen && !inPip) {
        VideoFullscreenDialog(onDismiss = { fullscreen = false }) {
            VideoStage(
                player = videoPlayer,
                surfaceActive = true,
                loading = loading,
                playback = playback,
                resizeMode = resizeModePref,
                fullscreen = true,
                title = mediaMetadata?.title,
                onTogglePlay = { videoPlayer?.let { it.playWhenReady = !it.playWhenReady } },
                onSeekTo = { ms -> videoPlayer?.seekTo(ms.coerceAtLeast(0L)) },
                onPrevious = { playerConnection.seekToPrevious() },
                onNext = { playerConnection.seekToNext() },
                onCollapse = { fullscreen = false },
                onToggleFullscreen = { fullscreen = false },
                menuItems = { close ->
                    DropdownMenuItem(
                        text = { Text("Cola de reproducción") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        onClick = {
                            close()
                            fullscreen = false
                            onQueueClick()
                        },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    playlistTarget?.let { target ->
        AddToPlaylistDialog(
            isVisible = true,
            track = target,
            onDismiss = { playlistTarget = null },
        )
    }
}

@Composable
private fun FeedFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = label,
                color = if (selected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FloatingWindowButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Tarjeta grande 16:9 de la lista de "Solo videos". */
@Composable
private fun VideoFeedCard(
    item: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSave: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val artist = item.artists
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        VideoThumbnail(item = item, corner = 14.dp)
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val initial = (artist.firstOrNull() ?: item.title.firstOrNull() ?: '♪').uppercaseChar()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarColor(artist.ifBlank { item.title })),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initial.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val duration = item.durationSeconds?.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) }
                Text(
                    text = listOfNotNull(artist.takeIf { it.isNotBlank() }, duration).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opciones del video",
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Reproducir siguiente") },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                        onClick = { menuOpen = false; onPlayNext() },
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a la cola") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        onClick = { menuOpen = false; onAddToQueue() },
                    )
                    DropdownMenuItem(
                        text = { Text("Guardar en playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                        onClick = { menuOpen = false; onSave() },
                    )
                }
            }
        }
    }
}

private val AvatarPalette = listOf(
    Color(0xFF7C4DFF), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF3949AB),
    Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF00838F), Color(0xFF8E24AA),
)

private fun avatarColor(seed: String): Color = AvatarPalette[(seed.hashCode() and 0x7fffffff) % AvatarPalette.size]
