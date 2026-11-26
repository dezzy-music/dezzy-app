/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.FollowState
import com.dezzy.app.feature.social.P2PIdentity
import com.dezzy.app.feature.social.P2PListening
import com.dezzy.app.feature.social.P2PMessaging
import com.dezzy.app.feature.social.P2PPost
import com.dezzy.app.feature.social.P2PPresence
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.P2PUser
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyExtendedFloatingActionButton
import com.dezzy.app.ui.components.glass.DezzyFilledTonalButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.components.glass.DezzySwitch
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

@Composable
fun P2PProfileScreen(ctx: ScreenContext, pubkey: String) {
    val social: P2PSocial = koinInject()
    val identity: P2PIdentity = koinInject()
    val idState by identity.state.collectAsState()
    val target = remember(pubkey) { social.parsePublicKey(pubkey) ?: pubkey.takeIf { HEX64.matches(it) }?.lowercase() }
    when {
        target == null -> Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding())
            IconButton(onClick = { ctx.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            P2PEmptyState(Icons.Rounded.PersonOff, "Perfil no válido", "No se reconoce a esta persona.")
        }
        idState.loaded && !idState.ready -> P2PWelcomeScreen(ctx)
        !idState.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> ProfileContent(ctx, target)
    }
}

@Composable
private fun ProfileContent(ctx: ScreenContext, pubkey: String) {
    val social: P2PSocial = koinInject()
    val messaging: P2PMessaging = koinInject()
    val presence: P2PPresence = koinInject()
    val user = rememberP2PUser(pubkey)
    val isMe = pubkey == social.me
    val following by social.following.collectAsState()
    val requests by social.pendingFollowRequests.collectAsState(emptyList())

    var followState by remember(pubkey) { mutableStateOf<FollowState?>(null) }
    var counts by remember(pubkey) { mutableStateOf<Pair<Int, Int>?>(null) }
    var canSee by remember(pubkey) { mutableStateOf(true) }
    var posts by remember(pubkey) { mutableStateOf<List<P2PPost>?>(null) }
    var listening by remember(pubkey) { mutableStateOf<P2PListening?>(null) }
    var editing by remember { mutableStateOf(false) }
    var composer by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<P2PPost?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(pubkey, following) { followState = runCatching { social.followState(pubkey) }.getOrNull() }
    LaunchedEffect(pubkey) {
        counts = runCatching { social.followCounts(pubkey) }.getOrNull()
        listening = runCatching { presence.listeningOf(listOf(pubkey))[pubkey] }.getOrNull()
    }
    LaunchedEffect(pubkey, reload, followState) {
        canSee = isMe || runCatching { social.canSee(pubkey) }.getOrDefault(true)
        posts = if (canSee) runCatching { social.userPosts(pubkey) }.getOrDefault(emptyList()) else emptyList()
    }
    val incoming = requests.filter { it.incoming && it.status == "pending" }
    val requesters = rememberP2PProfiles(incoming.map { it.pubkey })

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                Box {
                    P2PProfileBanner(user.banner, Modifier.fillMaxWidth().height(168.dp), video = user.bannerVideo)
                    IconButton(
                        onClick = { ctx.navigator.back() },
                        modifier = Modifier.statusBarsPadding().padding(4.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White) }
                    P2PAvatar(
                        user,
                        112.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp)
                            .offset(y = 40.dp)
                            .border(4.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
                    )
                }
                Spacer(Modifier.height(48.dp))
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.displayName, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (user.isPrivate) Icon(Icons.Rounded.Lock, "Cuenta privada", Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(user.fingerprint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (user.about.isNotBlank()) Text(user.about, fontSize = 15.sp)
                    counts?.let { (followers, followingCount) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("$followers seguidores", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$followingCount siguiendo", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isMe) {
                            DezzyButton(onClick = { editing = true }) {
                                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Editar perfil")
                            }
                            DezzyOutlinedButton(onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.SOCIAL)) }) {
                                Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ajustes")
                            }
                        } else {
                            when (followState) {
                                FollowState.FOLLOWING -> DezzyOutlinedButton(onClick = {
                                    ctx.scope.launch {
                                        if (runCatching { social.setFollowing(pubkey, false) }.getOrDefault(false)) followState = FollowState.NONE
                                        else ctx.toast("No se pudo dejar de seguir")
                                    }
                                }) { Text("Siguiendo") }
                                FollowState.REQUESTED -> DezzyOutlinedButton(onClick = {}, enabled = false) { Text("Solicitado") }
                                else -> DezzyButton(onClick = {
                                    ctx.scope.launch {
                                        if (runCatching { social.setFollowing(pubkey, true) }.getOrDefault(false)) {
                                            followState = runCatching { social.followState(pubkey) }.getOrNull()
                                        } else {
                                            ctx.toast("No se pudo seguir")
                                        }
                                    }
                                }) { Text("Seguir") }
                            }
                            DezzyFilledTonalButton(onClick = { ctx.navigator.push(Screen.P2PChat(pubkey)) }) { Text("Mensaje") }
                        }
                    }
                }
            }
            listening?.let { l ->
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Escuchando ahora", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                        l.song?.let { song -> P2PSongCard(song, Modifier.fillMaxWidth(), onClick = { playP2PSong(ctx, song) }) }
                            ?: Text(l.text, fontSize = 15.sp)
                    }
                }
            }
            if (isMe && incoming.isNotEmpty()) {
                item { P2PSectionTitle("Solicitudes para seguirte") }
                items(incoming, key = { "req-" + it.pubkey }) { r ->
                    val u = requesters[r.pubkey] ?: P2PUser(r.pubkey)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        P2PAvatar(u, 40.dp)
                        Text(u.displayName, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        DezzyOutlinedButton(onClick = { ctx.scope.launch { messaging.rejectFollower(r.pubkey) } }) { Text("Rechazar") }
                        Spacer(Modifier.width(8.dp))
                        DezzyButton(onClick = { ctx.scope.launch { messaging.approveFollower(r.pubkey) } }) { Text("Aprobar") }
                    }
                }
            }
            item { P2PSectionTitle("Publicaciones") }
            val list = posts
            when {
                !canSee -> item {
                    P2PEmptyState(Icons.Rounded.Lock, "Cuenta privada", "Sigue a ${user.displayName} y espera a que te apruebe para ver sus publicaciones.")
                }
                list == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                list.isEmpty() -> item {
                    P2PEmptyState(Icons.Rounded.Edit, "Sin publicaciones", if (isMe) "Cuenta lo que estás escuchando." else "Todavía no ha publicado nada.")
                }
                else -> items(list, key = { it.id }) { post ->
                    P2PPostCard(
                        ctx,
                        post,
                        onUpdate = { u -> posts = posts?.map { if (it.id == u.id) u else it } },
                        onDelete = { gone ->
                            ctx.scope.launch {
                                if (runCatching { social.deletePost(gone) }.getOrDefault(false)) posts = posts?.filter { it.id != gone.id }
                                else ctx.toast("No se pudo borrar")
                            }
                        },
                        onComments = { commentsFor = it },
                    )
                }
            }
        }
        if (isMe) {
            DezzyExtendedFloatingActionButton(
                text = { Text("Publicar") },
                icon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { composer = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
            )
        }
    }
    if (editing) EditProfileSheet(ctx, user) { editing = false }
    if (composer) P2PComposerSheet(ctx, onDismiss = { composer = false }, onPosted = { reload++ })
    commentsFor?.let { p ->
        P2PCommentsSheet(ctx, p, onDismiss = { commentsFor = null }, onCountChange = { n ->
            posts = posts?.map { if (it.id == p.id) it.copy(comments = n) else it }
        })
    }
}

@Composable
private fun EditProfileSheet(ctx: ScreenContext, user: P2PUser, onDismiss: () -> Unit) {
    val social: P2PSocial = koinInject()
    var name by remember { mutableStateOf(user.name) }
    var about by remember { mutableStateOf(user.about) }
    var picture by remember { mutableStateOf(user.picture) }
    var isPrivate by remember { mutableStateOf(user.isPrivate) }
    var banner by remember { mutableStateOf(user.banner) }
    var bannerVideo by remember { mutableStateOf(user.bannerVideo) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickAvatar = rememberMediaPicker(PickKind.IMAGE) { source ->
        busy = true
        ctx.scope.launch {
            val data = runCatching { social.avatarDataUri(source) }.getOrNull()
            if (data != null) picture = data else status = "No se pudo leer la foto."
            busy = false
        }
    }
    val pickBannerPhoto = rememberMediaPicker(PickKind.IMAGE) { source ->
        busy = true
        status = "Subiendo portada…"
        ctx.scope.launch {
            val bytes = runCatching { social.compressPhoto(source) }.getOrNull()
            val url = bytes?.let { runCatching { social.uploadPhoto(it) }.getOrNull() }
            if (url != null) { banner = url; bannerVideo = null; status = null } else status = "No se pudo subir la portada."
            busy = false
        }
    }
    val pickBannerVideo = rememberMediaPicker(PickKind.VIDEO) { source ->
        busy = true
        status = "Preparando el video…"
        ctx.scope.launch {
            val prepared = runCatching { social.prepareBannerVideo(source) }.getOrNull()?.getOrNull()
            if (prepared == null) {
                status = "El video no vale: dura hasta 15 s y pesa poco."
            } else {
                val posterUrl = runCatching { social.uploadPhoto(prepared.poster) }.getOrNull()
                val videoUrl = runCatching { social.uploadPhoto(prepared.video, prepared.mime) }.getOrNull()
                if (posterUrl != null && videoUrl != null) { banner = posterUrl; bannerVideo = videoUrl; status = null }
                else status = "No se pudo subir el video."
            }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Editar perfil", fontSize = 20.sp)
            P2PProfileBanner(banner, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)), video = null, fade = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                P2PBannerPreset.entries.forEach { p ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { banner = P2PSocial.BANNER_PRESET_PREFIX + p.id; bannerVideo = null },
                    ) { Text(p.label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DezzyOutlinedButton(onClick = pickBannerPhoto, enabled = !busy) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Portada")
                }
                DezzyOutlinedButton(onClick = pickBannerVideo, enabled = !busy) {
                    Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Video")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                P2PAvatar(user.copy(picture = picture, name = name), 72.dp)
                Spacer(Modifier.width(16.dp))
                DezzyOutlinedButton(onClick = pickAvatar, enabled = !busy) { Text("Cambiar foto") }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = about,
                onValueChange = { if (it.length <= 160) about = it },
                label = { Text("Sobre mí") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cuenta privada")
                    Text(
                        "Sólo los seguidores que apruebes verán tus publicaciones.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DezzySwitch(checked = isPrivate, onCheckedChange = { isPrivate = it })
            }
            status?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                DezzyOutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
                DezzyButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        ctx.scope.launch {
                            val ok = runCatching {
                                social.updateMyProfile(name, about, picture, isPrivate, banner, bannerVideo)
                            }.getOrDefault(false)
                            busy = false
                            if (ok) onDismiss() else status = "No se pudo guardar. Revisa tu conexión."
                        }
                    },
                ) { Text(if (busy) "Guardando…" else "Guardar") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
