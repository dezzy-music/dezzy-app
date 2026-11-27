/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.together

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.P2PUser
import com.dezzy.app.feature.together.RoomUser
import com.dezzy.app.feature.together.TogetherClient
import com.dezzy.app.feature.together.TogetherState
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.social.P2PAvatar
import com.dezzy.app.ui.screens.social.P2PEmptyState
import com.dezzy.app.ui.screens.social.P2PSectionTitle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Clock

/** Desfase con signo en segundos con un decimal: «+0,4 s». */
private fun fmtOffset(diffMs: Long): String {
    val tenths = kotlin.math.abs(diffMs) / 100
    return (if (diffMs < 0) "-" else "+") + "${tenths / 10},${tenths % 10} s"
}

/** Panel del anfitrión: qué escucha cada oyente, en qué punto va y cuánto se desvía de la sala. */
@Composable
fun TogetherDashboardScreen(ctx: ScreenContext) {
    val client: TogetherClient = koinInject()
    val state by client.state.collectAsState()
    val s = state as? TogetherState.InRoom
    val isHost = s?.isHost == true
    LaunchedEffect(s == null) { if (s == null) ctx.navigator.back() }

    // Pide el informe de los oyentes al abrir y cada ~5 s mientras sigas siendo anfitrión.
    LaunchedEffect(isHost) {
        if (isHost) {
            while (true) {
                client.requestDashboard()
                delay(5_000)
            }
        }
    }
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    if (s == null) return

    val room = s.room
    val track = room.track
    val hostPos = if (track == null) 0L else if (room.playing) room.position + (now - room.at) else room.position
    val others = room.users.filter { it.id != s.me }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            TopSpacer()
            ScreenTitle("Panel del anfitrión", onBack = { ctx.navigator.back() })
            Text(
                if (track != null) "Suena: ${track.title} · ${fmtMs(hostPos)}" else "Todavía no suena nada",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (!isHost) {
            item {
                P2PEmptyState(Icons.Rounded.Star, "Ya no eres el anfitrión", "Sólo el anfitrión ve el panel de oyentes.")
            }
        } else if (others.isEmpty()) {
            item {
                P2PEmptyState(Icons.Rounded.Groups, "Aún no hay oyentes", "Comparte el código de la sala para que se unan.")
            }
        } else {
            item { P2PSectionTitle("Oyentes · ${others.size}") }
            items(others, key = { it.id }) { u -> ListenerRow(client, s, u, track?.id, hostPos, now) }
        }
    }
}

@Composable
private fun ListenerRow(client: TogetherClient, s: TogetherState.InRoom, u: RoomUser, hostTrackId: String?, hostPos: Long, now: Long) {
    val info = s.listeners[u.id]
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            P2PAvatar(P2PUser(pubkey = u.id, name = u.name), 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(u.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !u.connected -> "Desconectado"
                        info == null -> "Sin datos todavía"
                        info.buffering -> "En búfer"
                        info.playing -> "Reproduciendo"
                        else -> "En pausa"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { client.transferHost(u.id) }) { Icon(Icons.Rounded.Star, "Pasar el anfitrión a ${u.name}") }
            IconButton(onClick = { client.kick(u.id) }) { Icon(Icons.Rounded.PersonRemove, "Expulsar a ${u.name}") }
        }
        if (info != null && u.connected) {
            val sameTrack = hostTrackId != null && info.trackId == hostTrackId
            val pos = if (info.playing && !info.buffering) info.positionMs + (now - info.at).coerceAtLeast(0) else info.positionMs
            Row(Modifier.padding(start = 52.dp, top = 2.dp)) {
                Text(
                    when {
                        info.trackId == null -> "Sin canción"
                        sameTrack -> "Misma canción · ${fmtMs(pos)} · ${fmtOffset(pos - hostPos)} respecto a ti"
                        else -> "Otra canción · ${fmtMs(pos)}"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
