/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.P2PMessaging
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.feature.social.P2PSong
import com.dezzy.app.ui.components.glass.DezzyFilledTonalButton
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class SendState { SENDING, SENT, FAILED }

/** Hoja para mandar una canción a amigos: chats recientes y gente que sigues, con una nota opcional. */
@Composable
internal fun P2PShareSongSheet(ctx: ScreenContext, song: P2PSong, onDismiss: () -> Unit) {
    val social: P2PSocial = koinInject()
    val messaging: P2PMessaging = koinInject()
    val conversations by messaging.conversations.collectAsState(emptyList())
    val following by social.following.collectAsState()
    val peers = remember(conversations, following) {
        (conversations.map { it.peer } + following).distinct().filter { it != social.me }
    }
    val profiles = rememberP2PProfiles(peers)
    var note by remember { mutableStateOf("") }
    val states = remember { mutableStateMapOf<String, SendState>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enviar canción", fontSize = 20.sp)
            P2PSongCard(song, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                placeholder = { Text("Añade una nota (opcional)") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (peers.isEmpty()) {
                Text("Aún no tienes amigos con quien compartirla.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(peers, key = { it }) { pk ->
                        val user = profiles[pk] ?: com.dezzy.app.feature.social.P2PUser(pk)
                        val state = states[pk]
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            P2PAvatar(user, 40.dp)
                            Text(
                                user.displayName,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            DezzyFilledTonalButton(
                                enabled = state == null || state == SendState.FAILED,
                                onClick = {
                                    states[pk] = SendState.SENDING
                                    ctx.scope.launch {
                                        val ok = runCatching { messaging.sendMessage(pk, note.trim(), song) }.getOrDefault(false)
                                        states[pk] = if (ok) SendState.SENT else SendState.FAILED
                                    }
                                },
                            ) {
                                Text(
                                    when (state) {
                                        SendState.SENDING -> "Enviando…"
                                        SendState.SENT -> "Enviado"
                                        SendState.FAILED -> "Reintentar"
                                        null -> "Enviar"
                                    },
                                )
                            }
                        }
                    }
                }
            }
            DezzyFilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
            Row(Modifier.padding(4.dp)) {}
        }
    }
}
