/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dezzy.app.feature.social.P2PComment
import com.dezzy.app.feature.social.P2PPost
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.P2PSong
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.rememberShareText
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Una publicación: autor, texto, foto, canción y acciones (reaccionar, comentar, compartir). */
@Composable
internal fun P2PPostCard(
    ctx: ScreenContext,
    post: P2PPost,
    onUpdate: (P2PPost) -> Unit,
    onDelete: (P2PPost) -> Unit,
    onComments: (P2PPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val social: P2PSocial = koinInject()
    val author = rememberP2PUser(post.author)
    val share = rememberShareText()
    var menu by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mine = post.author == social.me

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            P2PAvatar(author, 40.dp, modifier = Modifier.clickable { ctx.navigator.push(Screen.P2PProfile(post.author)) })
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(author.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeAgo(post.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (post.isPrivate) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Lock, "Privada", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más opciones") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ver perfil") },
                        onClick = { menu = false; ctx.navigator.push(Screen.P2PProfile(post.author)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir texto") },
                        onClick = { menu = false; share(post.text) },
                    )
                    if (mine) {
                        DropdownMenuItem(text = { Text("Borrar publicación") }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        }
        if (post.text.isNotBlank()) {
            Text(post.text, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
        }
        post.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "Foto de la publicación",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewer = true },
            )
        }
        post.song?.let { song ->
            P2PSongCard(song, Modifier.padding(top = 10.dp).fillMaxWidth(), onClick = { playP2PSong(ctx, song) })
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ReactionAction(post) { emoji ->
                onUpdate(post.withMyReaction(emoji))
                ctx.scope.launch {
                    val result = social.react(post, emoji)
                    if (result != emoji) onUpdate(post.withMyReaction(result))
                }
            }
            PostAction(Icons.Rounded.ChatBubbleOutline, if (post.comments > 0) post.comments.toString() else "Comentar") {
                onComments(post)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { share(post.text) }) { Icon(Icons.Rounded.Share, "Compartir") }
        }
        if (post.topReactions.isNotEmpty()) {
            Text(
                post.topReactions.take(3).joinToString(" "),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    if (viewer && post.imageUrl != null) P2PPhotoViewer(listOf(post.imageUrl!!), 0) { viewer = false }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar publicación") },
            text = { Text("Se pedirá a los relés que la quiten. Esto no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(post) }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun PostAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Toque: pone o quita ❤️. Pulsación larga: elegir otro emoji. */
@Composable
internal fun ReactionAction(post: P2PPost, onReact: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val mine = post.myReaction
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .combinedClickable(
                    onClick = { onReact(if (mine != null) null else P2PSocial.DEFAULT_REACTION) },
                    onLongClick = { open = true },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mine != null && mine != P2PSocial.DEFAULT_REACTION) {
                Text(mine, fontSize = 18.sp)
            } else {
                Icon(
                    if (mine != null) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    if (mine != null) "Quitar reacción" else "Reaccionar",
                    Modifier.size(20.dp),
                    tint = if (mine != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (post.likes > 0) post.likes.toString() else "Me gusta",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(open, { open = false }) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                P2PSocial.REACTIONS.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { open = false; onReact(if (emoji == mine) null else emoji) }
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

/** Hoja de comentarios con respuestas (un nivel) y límite de 1000 caracteres. */
@Composable
internal fun P2PCommentsSheet(ctx: ScreenContext, post: P2PPost, onDismiss: () -> Unit, onCountChange: (Int) -> Unit) {
    val social: P2PSocial = koinInject()
    var comments by remember { mutableStateOf<List<P2PComment>?>(null) }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<P2PComment?>(null) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(post.id) {
        comments = runCatching { social.threadComments(social.comments(post)) }.getOrDefault(emptyList())
    }
    val profiles = rememberP2PProfiles(comments.orEmpty().map { it.author })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Text("Comentarios", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val list = comments
            when {
                list == null -> Text("Cargando…", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.isEmpty() -> Text(
                    "Aún no hay comentarios. Sé el primero.",
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                    items(list, key = { it.id }) { c ->
                        val user = profiles[c.author] ?: com.dezzy.app.feature.social.P2PUser(c.author)
                        Row(
                            Modifier.fillMaxWidth().padding(start = if (c.replyTo != null) 56.dp else 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            P2PAvatar(user, if (c.replyTo != null) 28.dp else 36.dp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        "  " + timeAgo(c.createdAt),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(c.text, fontSize = 15.sp)
                                Text(
                                    "Responder",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { replyTo = c }.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            replyTo?.let { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Respondiendo a ${profiles[r.author]?.displayName ?: "comentario"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { replyTo = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Cancelar respuesta", Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1000) text = it },
                    placeholder = { Text("Escribe un comentario") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        val body = text
                        val parent = replyTo
                        ctx.scope.launch {
                            val added = runCatching { social.addComment(post, body, parent) }.getOrNull()
                            sending = false
                            if (added != null) {
                                text = ""
                                replyTo = null
                                val updated = social.threadComments(comments.orEmpty() + added)
                                comments = updated
                                onCountChange(updated.size)
                            } else {
                                ctx.toast("No se pudo enviar el comentario")
                            }
                        }
                    },
                ) { Icon(Icons.Rounded.Send, "Enviar") }
            }
        }
    }
}

/** Hoja para publicar: texto (2000), canción que suena, foto y aviso si la cuenta es privada. */
@Composable
internal fun P2PComposerSheet(ctx: ScreenContext, onDismiss: () -> Unit, onPosted: () -> Unit) {
    val social: P2PSocial = koinInject()
    var text by remember { mutableStateOf("") }
    var attachSong by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<String?>(null) }
    var posting by remember { mutableStateOf(false) }
    val nowPlaying = ctx.nowPlayingSong()
    val pick = rememberMediaPicker(PickKind.IMAGE) { photo = it }
    val isPrivate = social.myProfile?.isPrivate == true
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nueva publicación", fontSize = 20.sp)
            if (isPrivate) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "Tu cuenta es privada: sólo la verán tus seguidores aprobados.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                placeholder = { Text("¿Qué estás escuchando o pensando?") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            if (attachSong && nowPlaying != null) P2PSongCard(nowPlaying, Modifier.fillMaxWidth())
            photo?.let { source ->
                Box {
                    AsyncImage(
                        model = source,
                        contentDescription = "Foto elegida",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    IconButton(onClick = { photo = null }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Rounded.Close, "Quitar foto")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (nowPlaying != null) {
                    com.dezzy.app.ui.components.glass.DezzyFilterChip(
                        selected = attachSong,
                        onClick = { attachSong = !attachSong },
                        label = { Text("Lo que suena", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = pick) { Icon(Icons.Rounded.AddPhotoAlternate, "Añadir foto") }
                Spacer(Modifier.weight(1f))
                com.dezzy.app.ui.components.glass.DezzyButton(
                    onClick = {
                        posting = true
                        val body = text.trim()
                        val song = if (attachSong) nowPlaying else null
                        val source = photo
                        ctx.scope.launch {
                            val bytes = source?.let { social.compressPhoto(it) }
                            val ok = if (source != null && bytes == null) false else runCatching { social.createPost(body, song, bytes) }.getOrDefault(false)
                            posting = false
                            if (ok) { onPosted(); onDismiss() } else ctx.toast("No se pudo publicar. Revisa tu conexión.")
                        }
                    },
                    enabled = !posting && (text.isNotBlank() || photo != null || (attachSong && nowPlaying != null)),
                ) { Text(if (posting) "Publicando…" else "Publicar") }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}
