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
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Shuffle
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

    val favorites by ctx.repository.getFavorites().collectAsState(emptyList())
    val favoriteIds = remember(favorites) { favorites.mapNotNull { it.videoId }.toSet() }
    val similarSeeds = remember(history) {
        history.map { it.artists.substringBefore(",").substringBefore(" & ").trim() }
            .filter { it.isNotEmpty() }.distinct().take(3)
    }
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

    val ytPlaylists by produceState(emptyList<com.dezzy.app.core.model.Playlist>(), sessions[SessionProvider.YOUTUBE]?.isValid) {
        value = if (sessions[SessionProvider.YOUTUBE]?.isValid == true) runCatching { ctx.client.getLibraryPlaylists() }.getOrDefault(emptyList()) else emptyList()
    }

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
                onNotifications = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.NewReleases) },
                avatarUrl = avatarUrl,
                // Como en la app actual: el avatar abre Ajustes y no hay engranaje aparte.
                onAccount = { ctx.navigator.push(Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.ACCOUNTS)) },
                onSettings = { ctx.navigator.push(Screen.Settings()) },
            )
        }
        item(key = "chips") {
            val showChips by com.dezzy.app.ui.player.kit.rememberPreference(com.dezzy.app.ui.screens.settings.prefs.HomeCategoryChipsKey, true)
            if (showChips) HomeFilterChips(filter, spotifyVisible = spotifyOn) { f ->
                if (f == HomeFilter.COMMUNITY) ctx.navigator.select(Tab.TOGETHER) else filterState = f
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
            item(key = "qp:h") { SectionHeader("Selecciones rápidas") }
            when (quickPicks) {
                Load.Loading -> loadingRows(4)
                is Load.Failed -> errorRow(quickPicks.message)
                is Load.Done -> item(key = "qp") { QuickPicksCarousel(picks.take(10), ctx) }
            }
        }

        item(key = "tools") {
            DezzyToolsSection(
                surprise = surprise,
                onMore = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Experiences) },
                onSurprise = {
                    val pool = picks.ifEmpty { history }
                    pool.randomOrNull()?.let { t ->
                        surprise = t
                        ctx.player.playRadio(t, "radio:${t.title}")
                    } ?: ctx.toast("Escucha algo primero y te sorprendo")
                },
                onStats = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Stats) },
                onMap = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.MusicMap) },
                onYear = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.YearInMusic) },
            )
        }

        // «Tus mixes» se oculta mientras no haya géneros: el clasificador llega en la fase 10.

        if (history.size > 1) {
            val keep = history.drop(1).distinctBy { it.videoId }.take(20)
            item(key = "kl:h") { SectionHeader("Sigue escuchando") }
            item(key = "kl") { KeepListeningGrid(keep, ctx, favoriteIds) }
        }

        if (!exclusive && sessions[SessionProvider.YOUTUBE] != null && ytPlaylists.isNotEmpty()) {
            val account = sessions[SessionProvider.YOUTUBE]?.name
            item(key = "ytpl:h") {
                HomeAvatarHeader(
                    label = "Tus playlists de YouTube",
                    title = account ?: "YouTube",
                    thumbnail = avatarUrl,
                    onClick = { ctx.navigator.push(Screen.AccountPlaylists) },
                )
            }
            item(key = "ytpl") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ytPlaylists.take(12), key = { it.id }) { p ->
                        Column(Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable { ctx.navigator.push(Screen.Playlist(p.id)) }) {
                            CoverImage(p.thumbnail, Modifier.size(140.dp), RoundedCornerShape(12.dp), sizePx = 420)
                            Spacer(Modifier.height(6.dp))
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val info = when {
                                p.id == "LM" || p.id == "VLLM" -> "Lista creada automáticamente"
                                p.trackCount > 0 -> "${p.trackCount} ${if (p.trackCount == 1) "pista" else "pistas"}"
                                else -> null
                            }
                            info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                        }
                    }
                }
            }
        }

        // «Similar a <artista>»: hasta tres artistas recientes, cada uno con una estantería de su ficha.
        if (!exclusive) similarSeeds.forEach { name ->
            item(key = "sim:$name") { SimilarToSection(name, ctx) }
        }

        // Sin historial todavía: las estanterías de YouTube Music llenan el inicio.
        if (seedId == null && !exclusive) when (home) {
            null, Load.Loading -> loadingRows(4)
            is Load.Failed -> errorRow(home.message)
            is Load.Done -> home.value.forEach { carousel(it, ctx, "yt:") }
        }
    }

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
    val minutes = if (todayMinutes > 0) "$todayMinutes min hoy" else null
    val subtitle = if (streakDays > 0) {
        "$streakDays ${if (streakDays == 1) "día" else "días"} de racha" + (minutes?.let { ", $it" } ?: "")
    } else minutes ?: "¿Qué escuchamos hoy?"
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNotifications) { Icon(Icons.Rounded.Notifications, "Novedades") }
        Spacer(Modifier.size(4.dp))
        if (avatarUrl != null) {
            CoverImage(avatarUrl, Modifier.size(36.dp).clickable(onClick = onAccount), CircleShape, sizePx = 108, contentDescription = "Cuenta")
        } else {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onAccount),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Person, "Cuenta", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}

@Composable
private fun HomeFilterChips(selected: HomeFilter, spotifyVisible: Boolean, onSelect: (HomeFilter) -> Unit) {
    // «Spotify» sólo aparece con sesión y «Usar para la pantalla de inicio» activo.
    val visible = HomeFilter.entries.filter {
        (it != HomeFilter.SPOTIFY || spotifyVisible) &&
            (it != HomeFilter.COMMUNITY || com.dezzy.app.ui.screens.social.SocialVisibility.FRIENDS_AND_MESSAGES)
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(visible) { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                leadingIcon = if (f == selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                shape = RoundedCornerShape(8.dp),
                colors = com.dezzy.app.ui.components.dezzyFilterChipColors(),
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
        add { m -> DjTile(m, djName, playingNow, hour) { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Dj) } }
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
        Triple(Icons.Rounded.Shuffle, "Sorpréndeme", surprise?.let { "Te tocó: ${it.title}" } ?: "Una canción al azar de tu música") to onSurprise,
        Triple(Icons.Rounded.BarChart, "Estadísticas", "Tus artistas y minutos") to onStats,
        Triple(Icons.Outlined.Language, "Mapa musical", "Sonidos por país") to onMap,
        Triple(Icons.Rounded.AutoAwesome, "Tu año en música", "Tu resumen personal") to onYear,
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

/**
 * «Selecciones rápidas»: la tarjeta de delante es grande (título y artista encima) y las siguientes
 * son tiras estrechas; al arrastrar, la siguiente crece mientras la de delante se encoge.
 */
@Composable
private fun QuickPicksCarousel(tracks: List<Track>, ctx: ScreenContext) {
    if (tracks.isEmpty()) return
    val large = 244.dp
    val small = 40.dp
    val gap = 8.dp
    val start = 16.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stepPx = with(density) { (large + gap).toPx() }
    val maxPos = (tracks.size - 1).toFloat()
    var pos by remember(tracks) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var fling by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
        pos = (pos - delta / stepPx).coerceIn(0f, maxPos)
    }
    androidx.compose.ui.layout.Layout(
        content = {
            tracks.forEachIndexed { i, t ->
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier.clip(RoundedCornerShape(24.dp)).clickable { ctx.player.playTracks(tracks, i, "radio") },
                ) {
                    CoverImage(t.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), sizePx = 720)
                    if (maxWidth > 150.dp) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.55f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)),
                            ),
                        )
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                            Text(t.title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artists, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(276.dp)
            .clipToBounds()
            .draggable(
                    drag,
                    androidx.compose.foundation.gestures.Orientation.Horizontal,
                    onDragStarted = { fling?.cancel() },
                    onDragStopped = { v ->
                        val target = (pos - v / stepPx * 0.3f).let { kotlin.math.round(it) }.coerceIn(0f, maxPos)
                        fling = scope.launch {
                            androidx.compose.animation.core.animate(pos, target) { value, _ -> pos = value }
                        }
                    },
            ),
    ) { measurables, constraints ->
        val h = constraints.maxHeight
        val largePx = large.toPx()
        val smallPx = small.toPx()
        val gapPx = gap.toPx()
        val p = pos
        val placed = ArrayList<Pair<androidx.compose.ui.layout.Placeable, Int>>()
        var x = start.toPx()
        measurables.forEachIndexed { i, m ->
            val d = i - p
            val w = when {
                d <= -1f -> 0f
                d < 0f -> largePx * (1f + d)
                d < 1f -> largePx + (smallPx - largePx) * d
                else -> smallPx
            }
            if (w >= 1f && x < constraints.maxWidth) {
                val wi = w.toInt()
                placed += m.measure(androidx.compose.ui.unit.Constraints.fixed(wi, h)) to x.toInt()
                x += w + gapPx
            }
        }
        layout(constraints.maxWidth, h) { placed.forEach { (pl, px) -> pl.place(px, 0) } }
    }
}

/** «Sigue escuchando»: rejilla horizontal de dos filas, ▶ sobre la portada y ♥ si es favorita. */
@Composable
private fun KeepListeningGrid(tracks: List<Track>, ctx: ScreenContext, favoriteIds: Set<String>) {
    val side = 128.dp
    androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
        rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height((side + 52.dp) * 2 + 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(tracks.size, key = { i -> "$i:${tracks[i].videoId}" }) { i ->
            val t = tracks[i]
            Column(Modifier.width(side).clip(RoundedCornerShape(12.dp)).clickable { ctx.player.playTracks(tracks, i, "history") }) {
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    CoverImage(t.thumbnail, Modifier.size(side), RoundedCornerShape(10.dp), sizePx = 384)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t.videoId in favoriteIds) {
                        Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(t.artists, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Cabecera con imagen: etiqueta pequeña, nombre grande y → (playlists de la cuenta, «Similar a»). */
@Composable
private fun HomeAvatarHeader(label: String, title: String, thumbnail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            CoverImage(thumbnail, Modifier.size(48.dp), CircleShape, sizePx = 144)
        } else {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
    }
}

/** «Similar a <artista>»: cabecera con su foto y una estantería de su ficha (canciones, artistas o listas). */
@Composable
private fun SimilarToSection(name: String, ctx: ScreenContext) {
    val artist by produceState<com.dezzy.app.core.model.Artist?>(null, name) {
        value = runCatching {
            val id = ctx.client.findArtistId(name).getOrNull() ?: return@runCatching null
            ctx.client.getArtist(id).getOrNull()
        }.getOrNull()
    }
    val a = artist ?: return
    val shelves = a.shelves.filter { it.items.isNotEmpty() }
    if (shelves.isEmpty()) return
    val shelf = shelves[(a.id.hashCode() and Int.MAX_VALUE) % shelves.size]
    Column {
        HomeAvatarHeader("Similar a", a.name, a.thumbnail) { ctx.navigator.push(Screen.Artist(a.id)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shelf.items, key = { it.type.name + it.id }) { com.dezzy.app.ui.components.ItemCard(it, onClick = { ctx.open(it) }, size = 140.dp) }
        }
    }
}

@Composable
private fun TrackCarousel(tracks: List<Track>, ctx: ScreenContext, source: String, large: Boolean = false) {
    val side = if (large) 180.dp else 140.dp
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(tracks, key = { i, t -> "$i:${t.videoId}" }) { i, t ->
            Column(Modifier.width(side).clip(RoundedCornerShape(12.dp)).clickable { ctx.player.playTracks(tracks, i, source) }) {
                CoverImage(t.thumbnail, Modifier.size(side), RoundedCornerShape(if (large) 20.dp else 12.dp), sizePx = if (large) 540 else 420)
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (large) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

