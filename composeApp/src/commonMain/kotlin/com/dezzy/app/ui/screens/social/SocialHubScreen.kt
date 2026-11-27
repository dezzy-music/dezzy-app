/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.P2PConversation
import com.dezzy.app.feature.social.P2PIdentity
import com.dezzy.app.feature.social.P2PListening
import com.dezzy.app.feature.social.P2PMap
import com.dezzy.app.feature.social.P2PMapListener
import com.dezzy.app.feature.social.P2PMessaging
import com.dezzy.app.feature.social.P2PPost
import com.dezzy.app.feature.social.P2PPresence
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.P2PUser
import com.dezzy.app.ui.components.dezzyFilterChipColors
import com.dezzy.app.ui.navigation.Tab
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyExtendedFloatingActionButton
import com.dezzy.app.ui.components.glass.DezzyFilterChip
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.components.glass.DezzySingleChoiceSegmentedRow
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class SocialTab(val label: String) {
    ROOMS("Salas"),
    FRIENDS("Amigos"),
    MAP("Mapa"),
    NEARBY("Cerca"),
    COMMUNITY("Comunidad"),
}

/** Pestaña Social: amigos y chats, mapa, radar «Cerca» y comunidad. Sin cuenta, da la bienvenida. */
@Composable
fun SocialHubScreen(ctx: ScreenContext) {
    val identity: P2PIdentity = koinInject()
    val state by identity.state.collectAsState()
    // Mientras dura la bienvenida (crear cuenta + copia de las 12 palabras) no se cambia a la pantalla principal.
    var welcoming by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { identity.load() } }
    when {
        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        // Como en la app actual, «Juntos» se abre sin cuenta; el alta sólo aparece en las secciones sociales.
        else -> SocialHome(ctx) { modifier ->
            if (!state.ready || welcoming) {
                Box(modifier) {
                    P2PWelcomeScreen(
                        ctx,
                        embedded = true,
                        onBusy = { welcoming = true },
                        onDone = { welcoming = false },
                    )
                }
                true
            } else false
        }
    }
}

@Composable
private fun SocialHome(
    ctx: ScreenContext,
    /** Si hace falta cuenta, dibuja el alta en [Modifier] y devuelve true. */
    accountGate: @Composable (Modifier) -> Boolean,
) {
    val social: P2PSocial = koinInject()
    var tab by remember { mutableStateOf(SocialTab.ROOMS) }
    var roomsVersion by remember { mutableStateOf(0) }
    var addFriend by remember { mutableStateOf(false) }
    var composer by remember { mutableStateOf(false) }
    var feedVersion by remember { mutableStateOf(0) }
    val me = social.me
    val mine = if (me != null) rememberP2PUser(me) else null

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            com.dezzy.app.ui.components.DezzyTopBar(ctx)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (tab == SocialTab.ROOMS) ctx.navigator.select(Tab.HOME) else tab = SocialTab.ROOMS }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (tab == SocialTab.ROOMS) "Juntos" else "Social",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tab == SocialTab.ROOMS) Text(
                        "Escucha en sincronía con otras personas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { if (mine != null) addFriend = true else tab = SocialTab.FRIENDS }) { Icon(Icons.Rounded.Search, "Buscar personas") }
                IconButton(onClick = { roomsVersion++ }) { Icon(Icons.Rounded.Refresh, "Actualizar") }
                IconButton(onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.SOCIAL)) }) {
                    Icon(Icons.Rounded.Settings, "Ajustes de la red social")
                }
                if (mine != null) {
                    P2PAvatar(
                        mine,
                        36.dp,
                        modifier = Modifier.padding(horizontal = 8.dp).clickable { ctx.navigator.push(Screen.P2PProfile(mine.pubkey)) },
                    )
                }
            }
            DezzySingleChoiceSegmentedRow(
                items = SocialTab.entries,
                selected = tab,
                onSelect = { tab = it },
                label = { it.label },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab != SocialTab.ROOMS && accountGate(Modifier.fillMaxSize())) return@Box
                when (tab) {
                    SocialTab.ROOMS -> RoomsTab(ctx, roomsVersion)
                    SocialTab.FRIENDS -> FriendsTab(ctx) { addFriend = true }
                    SocialTab.MAP -> MapTab(ctx)
                    SocialTab.NEARBY -> NearbyTab(ctx)
                    SocialTab.COMMUNITY -> CommunityTab(ctx, feedVersion) { composer = true }
                }
            }
        }
        if (mine != null) when (tab) {
            SocialTab.FRIENDS -> DezzyExtendedFloatingActionButton(
                text = { Text("Añadir amigo") },
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                onClick = { addFriend = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
            )
            SocialTab.COMMUNITY -> DezzyExtendedFloatingActionButton(
                text = { Text("Publicar") },
                icon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { composer = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
            )
            else -> Unit
        }
    }
    if (addFriend) P2PAddFriendSheet(ctx) { addFriend = false }
    if (composer) P2PComposerSheet(ctx, onDismiss = { composer = false }, onPosted = { feedVersion++ })
}

// ---------------------------------------------------------------- Salas

/** «Juntos»: crear sala, unirse con código y salas públicas (aún sin listado público: estado vacío). */
@Composable
private fun RoomsTab(ctx: ScreenContext, @Suppress("UNUSED_PARAMETER") version: Int) {
    val genres = listOf("Todos", "Lofi", "Pop", "Hip-Hop", "Rock", "Electrónica")
    var genre by remember { mutableStateOf("Todos") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoomTile(
                    Modifier.weight(1f), "Crear sala", "Tú eliges la música", Icons.Rounded.Add,
                    container = MaterialTheme.colorScheme.onSurface, content = MaterialTheme.colorScheme.surface,
                ) { ctx.navigator.push(Screen.Together()) }
                RoomTile(
                    Modifier.weight(1f), "Unirse con código", "Código de 6 caracteres o QR", Icons.Rounded.QrCode2,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurface,
                ) { ctx.navigator.push(Screen.Together()) }
            }
        }
        item {
            Text(
                "Salas públicas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres) { g ->
                    DezzyFilterChip(
                        selected = g == genre,
                        onClick = { genre = g },
                        label = { Text(g) },
                        leadingIcon = {
                            Icon(
                                when (g) {
                                    "Todos" -> Icons.Rounded.Public
                                    "Lofi" -> Icons.Rounded.Headphones
                                    "Pop" -> Icons.Rounded.StarOutline
                                    "Hip-Hop" -> Icons.Rounded.Mic
                                    else -> Icons.Rounded.MusicNote
                                },
                                null,
                                Modifier.size(18.dp),
                            )
                        },
                        colors = dezzyFilterChipColors(),
                    )
                }
            }
        }
        item {
            P2PEmptyState(
                Icons.Rounded.Public,
                "No hay salas públicas activas",
                "¡Sé el primero! Crea una sala pública y comparte tu música con el mundo.",
                action = {
                    DezzyButton(onClick = { ctx.navigator.push(Screen.Together()) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text("Crear sala pública", modifier = Modifier.padding(start = 6.dp))
                    }
                },
            )
        }
    }
}

@Composable
private fun RoomTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), color = container, contentColor = content) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
            Text(subtitle, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Amigos

@Composable
private fun FriendsTab(ctx: ScreenContext, onAddFriend: () -> Unit) {
    val social: P2PSocial = koinInject()
    val messaging: P2PMessaging = koinInject()
    val presence: P2PPresence = koinInject()
    val conversations by messaging.conversations.collectAsState(emptyList())
    val following by social.following.collectAsState()
    val requests by social.pendingFollowRequests.collectAsState(emptyList())
    var listening by remember { mutableStateOf<List<P2PListening>>(emptyList()) }

    LaunchedEffect(following) {
        while (true) {
            listening = runCatching { presence.friendsListening() }.getOrDefault(listening)
            delay(45_000)
        }
    }
    val keys = (conversations.map { it.peer } + following + requests.map { it.pubkey } + listening.map { it.pubkey }).distinct()
    val profiles = rememberP2PProfiles(keys)
    fun user(pk: String) = profiles[pk] ?: P2PUser(pk)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item { EncryptedChip(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        val incoming = requests.filter { it.incoming && it.status == "pending" }
        if (incoming.isNotEmpty()) {
            item { P2PSectionTitle("Solicitudes para seguirte") }
            items(incoming, key = { "req-" + it.pubkey }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    P2PAvatar(user(r.pubkey), 44.dp, modifier = Modifier.clickable { ctx.navigator.push(Screen.P2PProfile(r.pubkey)) })
                    Text(
                        user(r.pubkey).displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    DezzyOutlinedButton(onClick = { ctx.scope.launch { messaging.rejectFollower(r.pubkey) } }) { Text("Rechazar") }
                    Box(Modifier.width(8.dp))
                    DezzyButton(onClick = { ctx.scope.launch { messaging.approveFollower(r.pubkey) } }) { Text("Aprobar") }
                }
            }
        }
        if (listening.isNotEmpty()) {
            item { P2PSectionTitle("Escuchando ahora") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(listening, key = { it.pubkey }) { l ->
                        ListeningCard(ctx, user(l.pubkey), l)
                    }
                }
            }
        }
        if (conversations.isNotEmpty()) {
            item { P2PSectionTitle("Chats") }
            items(conversations, key = { "chat-" + it.peer }) { c ->
                ConversationRow(user(c.peer), c) { ctx.navigator.push(Screen.P2PChat(c.peer)) }
            }
        }
        val others = following.filter { f -> conversations.none { it.peer == f } }
        if (others.isNotEmpty()) {
            item { P2PSectionTitle("Personas que sigues") }
            items(others, key = { "fol-" + it }) { pk ->
                Row(
                    Modifier.fillMaxWidth().clickable { ctx.navigator.push(Screen.P2PProfile(pk)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    P2PAvatar(user(pk), 44.dp)
                    Text(user(pk).displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        if (conversations.isEmpty() && following.isEmpty() && incoming.isEmpty()) {
            item {
                P2PEmptyState(
                    Icons.Rounded.Groups,
                    "Aún no tienes amigos aquí",
                    "Busca a alguien por su nombre o comparte tu enlace para empezar a hablar y ver qué escucha.",
                    action = { DezzyButton(onClick = onAddFriend) { Text("Añadir amigo") } },
                )
            }
        }
    }
}

@Composable
private fun ListeningCard(ctx: ScreenContext, user: P2PUser, l: P2PListening) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .width(140.dp)
                .clickable { l.song?.let { playP2PSong(ctx, it) } ?: ctx.navigator.push(Screen.P2PProfile(user.pubkey)) }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            P2PAvatar(user, 56.dp, ring = true)
            Text(user.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(
                l.song?.title ?: l.text.ifBlank { "Escuchando" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConversationRow(user: P2PUser, c: P2PConversation, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        P2PAvatar(user, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(user.displayName, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (c.lastFromMe) "Tú: " else "") + c.lastText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chatTime(c.lastAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.unread > 0) Badge(Modifier.padding(top = 4.dp)) { Text(c.unread.toString()) }
        }
    }
}

// ---------------------------------------------------------------- Mapa

/** Sin mapa propio todavía: lista de quien escucha música en el mundo ahora mismo. */
@Composable
private fun MapTab(ctx: ScreenContext) {
    val map: P2PMap = koinInject()
    var listeners by remember { mutableStateOf<List<P2PMapListener>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            listeners = runCatching { map.listeners() }.getOrDefault(listeners ?: emptyList())
            delay(60_000)
        }
    }
    val list = listeners
    val profiles = rememberP2PProfiles(list.orEmpty().map { it.pubkey })
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> P2PEmptyState(Icons.Rounded.Explore, "Nadie en el mapa", "Aquí aparece quien comparte lo que escucha y su zona aproximada.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            item { P2PSectionTitle("Escuchando en el mundo") }
            items(list, key = { it.pubkey }) { l ->
                val user = profiles[l.pubkey] ?: P2PUser(l.pubkey)
                Row(
                    Modifier.fillMaxWidth().clickable { ctx.navigator.push(Screen.P2PProfile(l.pubkey)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    P2PAvatar(user, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(user.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            l.song?.let { "${it.title} · ${it.artist}" } ?: l.status,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Comunidad

@Composable
private fun CommunityTab(ctx: ScreenContext, feedVersion: Int, onCompose: () -> Unit) {
    val social: P2PSocial = koinInject()
    var mode by remember { mutableStateOf(P2PSocial.FeedMode.FOLLOWING) }
    var posts by remember { mutableStateOf<List<P2PPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<P2PPost?>(null) }
    val listState = rememberLazyListState()

    suspend fun reload() {
        posts = runCatching { social.feed(mode) }.getOrDefault(emptyList())
        endReached = posts.size < 5
    }
    LaunchedEffect(mode, feedVersion) {
        loading = true
        reload()
        loading = false
    }
    LaunchedEffect(listState, mode) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 && info.totalItemsCount > 0
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && !loadingMore && !endReached && posts.isNotEmpty()) {
                loadingMore = true
                val more = runCatching { social.feed(mode, until = posts.last().createdAt - 1) }.getOrDefault(emptyList())
                val fresh = more.filter { m -> posts.none { it.id == m.id } }
                if (fresh.isEmpty()) endReached = true else posts = posts + fresh
                loadingMore = false
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { ctx.scope.launch { refreshing = true; reload(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DezzyFilterChip(mode == P2PSocial.FeedMode.FOLLOWING, { mode = P2PSocial.FeedMode.FOLLOWING }, { Text("Siguiendo") })
                    DezzyFilterChip(mode == P2PSocial.FeedMode.DISCOVER, { mode = P2PSocial.FeedMode.DISCOVER }, { Text("Descubrir") })
                }
            }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onCompose),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        "¿Qué estás escuchando?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            when {
                loading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                posts.isEmpty() -> item {
                    P2PEmptyState(
                        Icons.Rounded.Groups,
                        if (mode == P2PSocial.FeedMode.FOLLOWING) "Todavía no hay publicaciones" else "Nada que descubrir aún",
                        if (mode == P2PSocial.FeedMode.FOLLOWING) "Sigue a gente o publica lo primero tú." else "Vuelve a intentarlo en un rato.",
                    )
                }
                else -> items(posts, key = { it.id }) { post ->
                    P2PPostCard(
                        ctx,
                        post,
                        onUpdate = { updated -> posts = posts.map { if (it.id == updated.id) updated else it } },
                        onDelete = { gone ->
                            ctx.scope.launch {
                                if (runCatching { social.deletePost(gone) }.getOrDefault(false)) posts = posts.filter { it.id != gone.id }
                                else ctx.toast("No se pudo borrar")
                            }
                        },
                        onComments = { commentsFor = it },
                    )
                }
            }
            if (loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
        }
    }
    commentsFor?.let { p ->
        P2PCommentsSheet(ctx, p, onDismiss = { commentsFor = null }, onCountChange = { n ->
            posts = posts.map { if (it.id == p.id) it.copy(comments = n) else it }
        })
    }
}
