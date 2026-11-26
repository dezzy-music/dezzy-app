/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.core.model.MusicSection
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.source.spotify.SpotifyFeatures
import com.dezzy.app.source.spotify.SpotifyHomeOnlyKey
import com.dezzy.app.source.spotify.SpotifyTrack
import com.dezzy.app.source.spotify.SpotifyUseHomeKey
import com.dezzy.app.ui.components.SectionHeader
import com.dezzy.app.ui.components.SpotifyTrackShelf
import com.dezzy.app.ui.components.TrackRow
import com.dezzy.app.ui.components.rememberSpotifyTrackPlayer
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.navigation.Tab
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val DAY_MS = 86_400_000L

private enum class HomeFilter(val label: String) { FOR_YOU("Para ti"), MUSIC("Música"), SPOTIFY("Spotify"), COMMUNITY("Comunidad") }

/**
 * Inicio Dezzy (boceto fase 1 A): saludo con racha, filtros, rejilla de saludo con DJ y listas
 * fijadas, «Sigue donde ibas», selección rápida, herramientas, más escuchadas, seguir escuchando
 * y favoritos olvidados. El filtro «Música» muestra las estanterías de YouTube Music.
 */
@Composable
fun HomeScreen(ctx: ScreenContext, state: PlaybackState) {
    val history by ctx.repository.getHistory(50).collectAsState(emptyList())
    val now = remember { Clock.System.now() }
    val nowMs = now.toEpochMilliseconds()
    val zone = remember { TimeZone.currentSystemDefault() }
    val offsetMs = remember(now) { zone.offsetAt(now).totalSeconds * 1000L }
    val hour = remember(now) { now.toLocalDateTime(zone).hour }

    val mostPlayed by remember { ctx.repository.getMostPlayed(nowMs - 30 * DAY_MS) }.collectAsState(emptyList())
    val forgotten by remember { ctx.repository.getForgottenFavorites(nowMs - 30 * DAY_MS) }.collectAsState(emptyList())
    val playlists by ctx.repository.getPlaylists().collectAsState(emptyList())
    val pinned1 by remember { ctx.prefs.pinnedPlaylist(1) }.collectAsState(null)
    val pinned2 by remember { ctx.prefs.pinnedPlaylist(2) }.collectAsState(null)
    val djName by ctx.prefs.djName.collectAsState("")
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val avatarUrl = sessions[SessionProvider.YOUTUBE]?.avatarUrl

    val streak by produceState(0, history.size) { value = ctx.repository.listenStreakDays(offsetMs) }
    val todaySeconds by produceState(0L, history.size) { value = ctx.repository.todayListenSeconds(offsetMs) }

    val seedId = history.firstOrNull()?.videoId
    val quickPicks = rememberLoad(seedId) {
        if (seedId == null) Result.success(emptyList()) else ctx.client.getUpNext(seedId)
    }
    val features: SpotifyFeatures = koinInject()
    val useSpotifyHome by remember { ctx.prefs.value(SpotifyUseHomeKey, false) }.collectAsState(false)
    val spotifyHomeOnly by remember { ctx.prefs.value(SpotifyHomeOnlyKey, false) }.collectAsState(false)
    val spotifyOn = useSpotifyHome && sessions[SessionProvider.SPOTIFY]?.isValid == true
    val exclusive = spotifyOn && spotifyHomeOnly
    // null = cargando; falla en silencio (lista vacía) si no hay red o la sesión no vale.
    val spotifyLikes by produceState<List<SpotifyTrack>?>(null, spotifyOn) {
        value = if (spotifyOn) features.recentLikes(20) else emptyList()
    }
    val spotifyPlayer = rememberSpotifyTrackPlayer(ctx, features)

    var filterState by rememberSaveable { mutableStateOf(HomeFilter.FOR_YOU) }
    val filter = if (filterState == HomeFilter.SPOTIFY && !spotifyOn) HomeFilter.FOR_YOU else filterState
    val home = if (filter == HomeFilter.MUSIC || (seedId == null && !exclusive)) rememberLoad("home") { ctx.client.getHome() } else null

    var showMore by remember { mutableStateOf(false) }
    var pinSlot by remember { mutableIntStateOf(0) }
    var surprise by remember { mutableStateOf<Track?>(null) }

    val picks = (quickPicks as? Load.Done)?.value.orEmpty()
    val greetingTracks = (picks + forgotten).distinctBy { it.videoId }.take(2)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "top") {
            TopSpacer()
            DezzyHomeTopBar(
                hour = hour,
                streakDays = streak,
                todayMinutes = (todaySeconds / 60).toInt(),
                onNotifications = { ctx.pending("Novedades y lanzamientos", "10") },
                avatarUrl = avatarUrl,
                onAccount = { ctx.navigator.push(Screen.Settings(SettingsPage.ACCOUNTS)) },
                onSettings = { ctx.navigator.push(Screen.Settings()) },
            )
        }
        item(key = "chips") {
            HomeFilterChips(filter, spotifyVisible = spotifyOn) { f ->
                if (f == HomeFilter.COMMUNITY) ctx.pending("Comunidad", "9") else filterState = f
            }
        }

        if (filter == HomeFilter.SPOTIFY) {
            val likes = spotifyLikes
            when {
                likes == null -> loadingRows(4)
                likes.isEmpty() -> emptyRow("No he podido traer canciones de tu Spotify ahora mismo")
                else -> item(key = "sp:likes") { SpotifyTrackShelf("Tus me gusta recientes", likes, spotifyPlayer) }
            }
            return@LazyColumn
        }

        if (filter == HomeFilter.MUSIC) {
            when (home) {
                null, Load.Loading -> loadingRows()
                is Load.Failed -> errorRow(home.message)
                is Load.Done -> home.value.forEach { carousel(it, ctx, "yt:") }
            }
            return@LazyColumn
        }

        item(key = "grid") {
            SpotifyGreetingGrid(
                ctx = ctx,
                djName = djName,
                playingNow = state.isPlaying && state.queue.source?.startsWith("dj") == true,
                hour = hour,
                pinned = listOf(pinned1, pinned2).map { id -> id?.let { p -> playlists.firstOrNull { it.id == p } } },
                tracks = greetingTracks,
                onAddPinned = { pinSlot = it },
            )
        }

        history.firstOrNull()?.takeIf { it.videoId != ctx.playingId }?.let { last ->
            item(key = "resume") { ResumeCard(ctx, last, nowMs) }
        }

        if (spotifyOn) {
            spotifyLikes?.takeIf { it.isNotEmpty() }?.let { likes ->
                item(key = "sp:likes") { SpotifyTrackShelf("Tus me gusta recientes", likes, spotifyPlayer) }
            }
        }

        // «Exclusivo de inicio»: en esta parte sólo van las estanterías de Spotify.
        if (!exclusive && (picks.isNotEmpty() || quickPicks is Load.Loading && seedId != null)) {
            item(key = "qp:h") { SectionHeader("Selección rápida", strapline = "Empieza una radio") }
            when (quickPicks) {
                Load.Loading -> loadingRows(4)
                is Load.Failed -> errorRow(quickPicks.message)
                is Load.Done -> itemsIndexed(picks.take(8), key = { i, t -> "qp:$i:${t.videoId}" }) { i, t ->
                    TrackRow(t, onClick = { ctx.player.playTracks(picks, i, "radio:${t.title}") }, playing = t.videoId == ctx.playingId)
                }
            }
        }

        item(key = "tools") {
            DezzyToolsSection(
                surprise = surprise,
                onMore = { showMore = true },
                onSurprise = {
                    val pool = picks.ifEmpty { history }
                    pool.randomOrNull()?.let { t ->
                        surprise = t
                        ctx.player.playRadio(t, "radio:${t.title}")
                    } ?: ctx.toast("Escucha algo primero y te sorprendo")
                },
                onStats = { ctx.pending("Estadísticas", "10") },
                onMap = { ctx.pending("Mapa musical", "10") },
                onYear = { ctx.pending("Tu año en música", "10") },
            )
        }

        // «Tus mixes» se oculta mientras no haya géneros: el clasificador llega en la fase 10.

        if (mostPlayed.isNotEmpty()) {
            item(key = "sd:h") { SectionHeader("Marcación rápida", strapline = "Lo que más escuchas") }
            item(key = "sd") { TrackCarousel(mostPlayed, ctx, "speed") }
        }

        if (history.size > 1) {
            val keep = history.drop(1).distinctBy { it.videoId }.take(20)
            item(key = "kl:h") { SectionHeader("Seguir escuchando") }
            item(key = "kl") { TrackCarousel(keep, ctx, "history") }
        }

        if (forgotten.isNotEmpty()) {
            item(key = "ff:h") { SectionHeader("Favoritos olvidados", strapline = "Hace tiempo que no suenan") }
            item(key = "ff") { TrackCarousel(forgotten, ctx, "forgotten") }
        }

        // Sin historial todavía: las estanterías de YouTube Music llenan el inicio.
        if (seedId == null && !exclusive) when (home) {
            null, Load.Loading -> loadingRows(4)
            is Load.Failed -> errorRow(home.message)
            is Load.Done -> home.value.forEach { carousel(it, ctx, "yt:") }
        }
    }

    if (showMore) MoreFromDezzySheet(ctx) { showMore = false }
    if (pinSlot != 0) PinnedPlaylistDialog(ctx, pinSlot) { pinSlot = 0 }
}

// ── Cabecera ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DezzyHomeTopBar(hour: Int, streakDays: Int, todayMinutes: Int, avatarUrl: String?, onNotifications: () -> Unit, onAccount: () -> Unit, onSettings: () -> Unit) {
    val greeting = when {
        hour < 12 -> "Buenos días"
        hour < 19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val subtitle = if (streakDays > 0 || todayMinutes > 0) {
        "$streakDays ${if (streakDays == 1) "día" else "días"} de racha, $todayMinutes min hoy"
    } else "¿Qué escuchamos hoy?"
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNotifications) { Icon(Icons.Rounded.Notifications, "Novedades") }
        if (avatarUrl != null) {
            CoverImage(avatarUrl, Modifier.size(36.dp).clickable(onClick = onAccount), CircleShape, sizePx = 108, contentDescription = "Cuenta")
        } else {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onAccount),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Person, "Cuenta", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") }
    }
}

@Composable
private fun HomeFilterChips(selected: HomeFilter, spotifyVisible: Boolean, onSelect: (HomeFilter) -> Unit) {
    // «Spotify» sólo aparece con sesión y «Usar para la pantalla de inicio» activo.
    val visible = HomeFilter.entries.filter { it != HomeFilter.SPOTIFY || spotifyVisible }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(visible) { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                leadingIcon = if (f == selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

// ── Rejilla de saludo ───────────────────────────────────────────────────────────────────────

@Composable
private fun SpotifyGreetingGrid(
    ctx: ScreenContext,
    djName: String,
    playingNow: Boolean,
    hour: Int,
    pinned: List<com.dezzy.app.core.model.Playlist?>,
    tracks: List<Track>,
    onAddPinned: (slot: Int) -> Unit,
) {
    val tiles: List<@Composable (Modifier) -> Unit> = buildList {
        add { m ->
            GreetingTile(m, "Tus me gusta", onClick = { ctx.navigator.push(Screen.Liked) }) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        add { m -> DjTile(m, djName, playingNow, hour) { ctx.pending("DJ Dezzy", "10") } }
        pinned.forEachIndexed { i, p ->
            val slot = i + 1
            if (p != null) add { m ->
                GreetingTile(
                    m, p.title,
                    onClick = { ctx.navigator.push(Screen.LocalPlaylist(p.id)) },
                    onLongClick = { ctx.scope.launch { ctx.prefs.setPinnedPlaylist(slot, null) } },
                ) { CoverImage(p.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), sizePx = 168) }
            } else add { m ->
                GreetingTile(m, "Añadir Playlist", onClick = { onAddPinned(slot) }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null)
                    }
                }
            }
        }
        tracks.forEach { t ->
            add { m ->
                GreetingTile(m, t.title, onClick = { ctx.player.playRadio(t, "radio:${t.title}") }) {
                    CoverImage(t.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), sizePx = 168)
                }
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GreetingTile(
    modifier: Modifier,
    title: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    cover: @Composable () -> Unit,
) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp)) { cover() }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun DjTile(modifier: Modifier, djName: String, playingNow: Boolean, hour: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val base = colors.surfaceContainerHigh
    val subtitle = if (playingNow) "Sonando ahora" else when (hour) {
        in 6..11 -> "Mezcla de mañana"
        in 12..18 -> "Mezcla de tarde"
        in 19..23 -> "Mezcla de noche"
        else -> "Mezcla de madrugada"
    }
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = 0.45f).compositeOver(base), colors.primary.copy(alpha = 0.12f).compositeOver(base))))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DjEqualizerBars(Modifier.size(width = 20.dp, height = 22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(if (djName.isBlank()) "DJ Dezzy" else "DJ $djName", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun DjEqualizerBars(modifier: Modifier) {
    val transition = rememberInfiniteTransition()
    val bars = listOf(420, 560, 360).map { ms ->
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse))
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { h ->
            Box(Modifier.weight(1f).fillMaxHeight(h.value).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}

// ── Sigue donde ibas ────────────────────────────────────────────────────────────────────────

@Composable
private fun ResumeCard(ctx: ScreenContext, track: Track, nowMs: Long) {
    val videoId = track.videoId ?: return
    val listened by produceState(0L, videoId) { value = ctx.repository.listenedSecondsSince(videoId, nowMs - DAY_MS) }
    val duration = track.durationSeconds ?: 0L
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Sigue donde ibas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(track.thumbnail, Modifier.size(64.dp), RoundedCornerShape(10.dp), sizePx = 192)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (duration > 0 && listened > 0) {
                val shown = listened.coerceAtMost(duration)
                LinearProgressIndicator(progress = { shown.toFloat() / duration }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Escuchaste ${formatClock(shown)} de ${formatClock(duration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Tu última escucha", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { ctx.player.playRadio(track, "radio:${track.title}") }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Reanudar")
            }
        }
    }
}

private fun formatClock(seconds: Long) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

// ── Herramientas Dezzy ──────────────────────────────────────────────────────────────────────

@Composable
private fun DezzyToolsSection(
    surprise: Track?,
    onMore: () -> Unit,
    onSurprise: () -> Unit,
    onStats: () -> Unit,
    onMap: () -> Unit,
    onYear: () -> Unit,
) {
    SectionHeader("Herramientas Dezzy", action = "Más", onAction = onMore)
    val tools = listOf(
        Triple(Icons.Rounded.Casino, "Sorpréndeme", surprise?.let { "Te tocó: ${it.title}" } ?: "Una canción al azar de tu música") to onSurprise,
        Triple(Icons.Rounded.Insights, "Estadísticas", "Tus artistas y minutos") to onStats,
        Triple(Icons.Rounded.Public, "Mapa musical", "Sonidos por país") to onMap,
        Triple(Icons.Rounded.Today, "Tu año en música", "Tu resumen personal") to onYear,
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (info, action) -> ToolTile(info.first, info.second, info.third, action, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 112.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Carrusel de canciones ───────────────────────────────────────────────────────────────────

@Composable
private fun TrackCarousel(tracks: List<Track>, ctx: ScreenContext, source: String) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(tracks, key = { i, t -> "$i:${t.videoId}" }) { i, t ->
            Column(Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable { ctx.player.playTracks(tracks, i, source) }) {
                CoverImage(t.thumbnail, Modifier.size(140.dp), RoundedCornerShape(12.dp), sizePx = 420)
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── «Más de Dezzy» ──────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreFromDezzySheet(ctx: ScreenContext, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Primero se cierra la hoja y después se navega.
    fun go(action: () -> Unit) {
        ctx.scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); action() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            MenuRow(Icons.Rounded.Person, "Tu Cuenta y Espacio") { go { ctx.navigator.push(Screen.Settings(SettingsPage.ACCOUNTS)) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MenuGroup("Tu Actividad")
            MenuRow(Icons.Rounded.NewReleases, "Novedades y Lanzamientos") { go { ctx.pending("Novedades y lanzamientos", "10") } }
            MenuRow(Icons.Rounded.History, "Historial de Reproducción") { go { ctx.navigator.push(Screen.History) } }
            MenuRow(Icons.Rounded.BarChart, "Estadísticas de Escucha") { go { ctx.pending("Estadísticas", "10") } }
            MenuRow(Icons.Rounded.Download, "Mis Descargas") { go { ctx.navigator.push(Screen.Downloads) } }
            MenuGroup("Descubrimiento y Más")
            MenuRow(Icons.Rounded.AutoAwesome, "DJ Dezzy AI") { go { ctx.pending("DJ Dezzy", "10") } }
            MenuRow(Icons.Rounded.Groups, "Escuchar Juntos") { go { ctx.navigator.select(Tab.TOGETHER) } }
            MenuRow(Icons.Rounded.Leaderboard, "Listas de Éxitos") { go { ctx.pending("Listas de éxitos", "10") } }
            MenuRow(Icons.Rounded.Map, "Mapa Musical") { go { ctx.pending("Mapa musical", "10") } }
            MenuRow(Icons.Rounded.Today, "Resumen del Año") { go { ctx.pending("Tu año en música", "10") } }
            MenuRow(Icons.Rounded.GraphicEq, "Reconocer Música") { go { ctx.pending("Reconocer música", "10") } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MenuRow(Icons.Rounded.Settings, "Ajustes") { go { ctx.navigator.push(Screen.Settings()) } }
        }
    }
}

@Composable
private fun MenuGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
