/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.P2PMessage
import com.dezzy.app.feature.social.P2PMessaging
import com.dezzy.app.feature.social.P2PNotifications
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.crypto.NostrCrypto
import com.dezzy.app.ui.components.glass.DezzyFilledIconButton
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Pasa 32 bytes a su valor en decimal (división repetida entre 10, sin BigInteger). */
private fun bytesToDecimal(bytes: ByteArray): String {
    val n = IntArray(bytes.size) { bytes[it].toInt() and 0xff }
    val out = StringBuilder()
    while (n.any { it != 0 }) {
        var rem = 0
        for (i in n.indices) {
            val cur = rem * 256 + n[i]
            n[i] = cur / 10
            rem = cur % 10
        }
        out.append(rem)
    }
    return out.reverse().toString()
}

/** Código de seguridad: igual en los dos móviles, para comparar a viva voz que nadie se interpone. */
private fun safetyCode(crypto: NostrCrypto, a: String, b: String): String {
    val joined = listOf(a, b).sorted().joinToString("")
    val digest = crypto.sha256(joined.encodeToByteArray())
    return bytesToDecimal(digest).padStart(30, '0').take(30).chunked(5).joinToString(" ")
}

private val HEX64 = Regex("^[0-9a-f]{64}$")

@Composable
fun P2PChatScreen(ctx: ScreenContext, pubkey: String) {
    if (!HEX64.matches(pubkey)) {
        Column(Modifier.fillMaxSize()) {
            TopSpacer()
            IconButton(onClick = { ctx.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            P2PEmptyState(Icons.Rounded.PersonOff, "Chat no válido", "No se reconoce a esta persona.")
        }
        return
    }
    val social: P2PSocial = koinInject()
    val messaging: P2PMessaging = koinInject()
    val notifications: P2PNotifications = koinInject()
    val crypto: NostrCrypto = koinInject()
    val user = rememberP2PUser(pubkey)
    val messages by messaging.messages(pubkey).collectAsState(emptyList())
    var text by remember { mutableStateOf("") }
    var attachSong by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val nowPlaying = ctx.nowPlayingSong()

    DisposableEffect(pubkey) {
        notifications.activeChatPeer = pubkey
        notifications.clearChat(pubkey)
        onDispose { if (notifications.activeChatPeer == pubkey) notifications.activeChatPeer = null }
    }
    LaunchedEffect(messages.size) {
        runCatching { messaging.markRead(pubkey) }
        notifications.clearChat(pubkey)
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopSpacer()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ctx.navigator.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            Row(
                Modifier.weight(1f).clickable { ctx.navigator.push(Screen.P2PProfile(pubkey)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                P2PAvatar(user, 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(user.displayName, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Cifrado de extremo a extremo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { ctx.pending("Escuchar juntos", "9b") }) { Icon(Icons.Rounded.Headphones, "Escuchar juntos") }
        }
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { showCode = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Los mensajes están cifrados. Toca para verificar el código de seguridad.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(messages.asReversed(), key = { it.id }) { m -> MessageBubble(ctx, m) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (nowPlaying != null) {
                IconButton(onClick = { attachSong = !attachSong }) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        if (attachSong) "Quitar canción" else "Adjuntar lo que suena",
                        tint = if (attachSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 4000) text = it },
                placeholder = { Text(if (attachSong && nowPlaying != null) "Añade un mensaje a «${nowPlaying.title}»" else "Mensaje") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DezzyFilledIconButton(
                enabled = !sending && (text.isNotBlank() || (attachSong && nowPlaying != null)),
                onClick = {
                    val body = text.trim()
                    val song = if (attachSong) nowPlaying else null
                    sending = true
                    ctx.scope.launch {
                        val ok = runCatching { messaging.sendMessage(pubkey, body, song) }.getOrDefault(false)
                        sending = false
                        if (ok) { text = ""; attachSong = false } else ctx.toast("No se pudo enviar el mensaje")
                    }
                },
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Enviar") }
        }
    }
    if (showCode) {
        val me = social.me
        AlertDialog(
            onDismissRequest = { showCode = false },
            title = { Text("Código de seguridad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (me != null) safetyCode(crypto, me, pubkey) else "—",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                    )
                    Text(
                        "Compáralo con el de ${user.displayName} en persona o por otra vía. Si coincide, nadie está espiando la conversación.",
                        fontSize = 14.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showCode = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun MessageBubble(ctx: ScreenContext, m: P2PMessage) {
    val mine = m.fromMe
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                m.song?.let { song ->
                    P2PSongCard(song, Modifier.padding(bottom = 6.dp).fillMaxWidth(), onClick = { playP2PSong(ctx, song) })
                }
                if (m.juntosLink != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                        Row(
                            Modifier.clickable { ctx.pending("Escuchar juntos", "9b") }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Headphones, null, Modifier.size(20.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("Escuchar juntos", fontWeight = FontWeight.Medium)
                                Text("¿Te unes?", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (m.text.isNotBlank()) Text(m.text, fontSize = 16.sp)
                Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(messageTime(m.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mine) {
                        Icon(
                            Icons.Rounded.Check,
                            "Enviado",
                            Modifier.padding(start = 4.dp).size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
