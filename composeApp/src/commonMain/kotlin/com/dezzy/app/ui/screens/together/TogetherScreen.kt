/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.together

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.P2PUser
import com.dezzy.app.feature.together.PublicRoom
import com.dezzy.app.feature.together.RoomSuggestion
import com.dezzy.app.feature.together.RoomTheme
import com.dezzy.app.feature.together.TogetherClient
import com.dezzy.app.feature.together.TogetherSession
import com.dezzy.app.feature.together.TogetherState
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.components.QrScanner
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyFilledTonalButton
import com.dezzy.app.ui.components.glass.DezzyFilterChip
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.components.qrMatrix
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.rememberShareText
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.social.P2PAvatar
import com.dezzy.app.ui.screens.social.P2PEmptyState
import com.dezzy.app.ui.screens.social.P2PSectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal fun themeLabel(t: RoomTheme): String = when (t) {
    RoomTheme.MIXED -> "Variado"
    RoomTheme.REGGAETON -> "Reguetón"
    RoomTheme.POP -> "Pop"
    RoomTheme.LATIN -> "Latino"
    RoomTheme.LOFI -> "Lo-fi"
    RoomTheme.ROCK -> "Rock"
    RoomTheme.HIP_HOP -> "Hip hop"
    RoomTheme.RNB -> "R&B"
    RoomTheme.ELECTRONIC -> "Electrónica"
    RoomTheme.KPOP -> "K-pop"
    RoomTheme.JAZZ -> "Jazz"
    RoomTheme.CLASSICAL -> "Clásica"
}

/** mm:ss a partir de milisegundos. */
internal fun fmtMs(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Saca el código (4-12 letras/dígitos en mayúscula) de un enlace «dezzy://together?code=XXXX» o de un código suelto. */
internal fun cleanRoomCode(raw: String): String =
    raw.substringAfter("code=", raw).substringBefore("&").filter { it.isLetterOrDigit() }.uppercase().take(12)

private val CODE_OK = Regex("^[A-Z0-9]{4,12}$")

/** «Escuchar juntos»: crear sala, unirse por código/QR, salas públicas y, dentro, la sala. */
@Composable
fun TogetherScreen(ctx: ScreenContext, joinCode: String?) {
    val client: TogetherClient = koinInject()
    val session: TogetherSession = koinInject()
    val state by client.state.collectAsState()
    LaunchedEffect(Unit) { session.start() }

    when (val s = state) {
        is TogetherState.Idle -> Lobby(ctx, client, session, joinCode, s.error)
        TogetherState.Connecting -> Column(Modifier.fillMaxSize()) {
            TopSpacer()
            ScreenTitle("Escuchar juntos", onBack = { ctx.navigator.back() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Conectando con la sala…")
                }
            }
        }
        is TogetherState.Waiting -> Column(Modifier.fillMaxSize()) {
            TopSpacer()
            ScreenTitle("Escuchar juntos", onBack = { ctx.navigator.back() })
            P2PEmptyState(
                Icons.Rounded.Groups,
                "Esperando a que el anfitrión te acepte",
                "Sala ${s.code}",
                action = { DezzyOutlinedButton(onClick = { client.leave() }) { Text("Cancelar") } },
            )
        }
        is TogetherState.InRoom -> RoomView(ctx, client, s)
    }
}

// ---------------------------------------------------------------- vestíbulo

@Composable
private fun Lobby(ctx: ScreenContext, client: TogetherClient, session: TogetherSession, joinCode: String?, error: String?) {
    var name by remember { mutableStateOf("") }
    var nameLoaded by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf(joinCode?.let { cleanRoomCode(it) } ?: "") }
    var isPublic by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(RoomTheme.MIXED) }
    var scanning by remember { mutableStateOf(false) }
    var autoDone by remember { mutableStateOf(false) }
    var rooms by remember { mutableStateOf<List<PublicRoom>?>(null) }
    var roomsAnswered by remember { mutableStateOf(false) }
    val validName = name.trim().isNotEmpty()

    LaunchedEffect(Unit) {
        name = session.savedName()
        nameLoaded = true
    }
    LaunchedEffect(Unit) {
        while (true) {
            rooms = runCatching { client.publicRooms() }.getOrNull()
            roomsAnswered = true
            delay(15_000)
        }
    }

    fun join(raw: String) {
        val c = cleanRoomCode(raw)
        if (!validName) {
            ctx.toast("Escribe tu nombre para entrar")
            return
        }
        if (!CODE_OK.matches(c)) {
            ctx.toast("Ese código no parece de una sala")
            return
        }
        ctx.scope.launch {
            session.saveName(name)
            client.joinRoom(c, name.trim())
        }
    }

    // Enlace recibido: si ya hay nombre guardado, se entra solo; si no, se deja el código puesto.
    LaunchedEffect(nameLoaded, joinCode) {
        if (nameLoaded && joinCode != null && !autoDone) {
            autoDone = true
            if (validName) join(joinCode)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                TopSpacer()
                ScreenTitle("Escuchar juntos", onBack = { ctx.navigator.back() })
                Text(
                    "La misma canción, al mismo segundo. Tú apruebas quién entra.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (error != null) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Tu nombre en la sala") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { P2PSectionTitle("Crear una sala") }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Sala pública", fontWeight = FontWeight.Medium)
                                Text(
                                    "Aparece en «Salas públicas» del móvil y del ordenador",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                        }
                        Text("Género", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(RoomTheme.entries.toList(), key = { it.name }) { t ->
                                DezzyFilterChip(theme == t, { theme = t }, { Text(themeLabel(t)) })
                            }
                        }
                        DezzyButton(
                            onClick = {
                                ctx.scope.launch {
                                    session.saveName(name)
                                    client.createRoom(name.trim(), isPublic, theme)
                                }
                            },
                            enabled = validName,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Crear sala") }
                    }
                }
            }
            item { P2PSectionTitle("Unirse con un código") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) },
                        label = { Text("Código de la sala") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DezzyFilledTonalButton(
                            onClick = { join(code) },
                            enabled = validName && CODE_OK.matches(code),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Entrar")
                        }
                        DezzyOutlinedButton(onClick = { scanning = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear QR")
                        }
                    }
                }
            }
            item { P2PSectionTitle("Salas públicas ahora") }
            val list = rooms
            when {
                !roomsAnswered -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                }
                list == null -> item {
                    Text(
                        "El servidor no responde ahora mismo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                list.isEmpty() -> item {
                    Text(
                        "Ahora mismo no hay salas públicas. Crea una y márcala como pública.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                else -> items(list, key = { it.code }) { r -> PublicRoomRow(r, enabled = validName) { join(r.code) } }
            }
        }
        if (scanning) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                QrScanner(
                    onResult = { text ->
                        scanning = false
                        val c = cleanRoomCode(text)
                        code = c
                        join(c)
                    },
                    onClose = { scanning = false },
                )
            }
        }
    }
}

@Composable
private fun PublicRoomRow(r: PublicRoom, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(r.thumbnail, Modifier.size(52.dp), RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Sala de ${r.hostName}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                r.trackTitle?.let { t -> (r.trackArtist?.let { "$it · " } ?: "") + t } ?: "Aún no suena nada",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(themeLabel(r.theme), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text("${r.participants}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.Groups, null, Modifier.padding(start = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- en la sala

@Composable
private fun RoomView(ctx: ScreenContext, client: TogetherClient, s: TogetherState.InRoom) {
    val room = s.room
    val host = room.users.firstOrNull { it.id == room.hostId }
    val share = rememberShareText()
    val link = remember(room.code) { client.roomLink(room.code) }
    val track = room.track

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            TopSpacer()
            ScreenTitle("Sala de ${host?.name ?: "Dezzy"}", onBack = { ctx.navigator.back() })
            Text(
                (if (room.settings.isPublic) "Sala pública" else "Sala privada") +
                    (if (room.settings.djModeEnabled) " · Modo DJ" else "") +
                    " · ${room.users.size} ${if (room.users.size == 1) "persona" else "personas"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        // Código, QR y compartir
        item {
            Surface(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoomQr(link, Modifier.size(120.dp))
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Código de la sala", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(room.code, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        DezzyFilledTonalButton(onClick = { share("Escucha música conmigo en Dezzy: $link (código ${room.code})") }) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Compartir")
                        }
                    }
                }
            }
        }
        // Canción actual
        item { P2PSectionTitle("Suena ahora") }
        item {
            if (track == null) {
                Text(
                    if (s.isHost) "Pon cualquier canción en Dezzy y sonará en la sala." else "Cuando el anfitrión ponga una canción, sonará aquí.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            } else {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(track.thumbnail, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(track.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${track.artist} · la puso ${track.suggestedBy ?: host?.name ?: "el anfitrión"}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(if (room.playing) "Sonando" else "En pausa", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        // Solicitudes pendientes (sólo anfitrión)
        if (s.isHost && s.pending.isNotEmpty()) {
            item { P2PSectionTitle("Quieren entrar") }
            items(s.pending, key = { "p-" + it.first }) { (id, pname) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    P2PAvatar(P2PUser(pubkey = id, name = pname), 40.dp)
                    Text(pname, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    IconButton(onClick = { client.approve(id, false) }) { Icon(Icons.Rounded.Close, "Rechazar a $pname") }
                    DezzyButton(onClick = { client.approve(id, true) }) { Text("Aceptar") }
                }
            }
        }
        // Oyentes
        item { P2PSectionTitle("En la sala · ${room.users.size}") }
        items(room.users, key = { "u-" + it.id }) { u ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                P2PAvatar(P2PUser(pubkey = u.id, name = u.name), 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(u.name + if (u.id == s.me) " (tú)" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (u.isHost) "Anfitrión" else "Invitado") + if (!u.connected) " · desconectado" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (u.isHost) Icon(Icons.Rounded.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        // Propuestas
        val suggestions = s.suggestions.sortedByDescending { it.votes }
        if (suggestions.isNotEmpty()) {
            item { P2PSectionTitle("Votaciones") }
            items(suggestions, key = { "s-" + it.id }) { x -> SuggestionRow(client, s, x) }
        }
        // Acciones
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DezzyFilledTonalButton(onClick = { ctx.navigator.push(Screen.TogetherChat) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Chat, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Chat de la sala" + if (s.chat.isNotEmpty()) " (${s.chat.size})" else "")
                }
                if (s.isHost) {
                    DezzyFilledTonalButton(onClick = { ctx.navigator.push(Screen.TogetherDashboard) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Dashboard, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Panel del anfitrión")
                    }
                }
                DezzyOutlinedButton(onClick = { client.leave() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Salir de la sala")
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(client: TogetherClient, s: TogetherState.InRoom, x: RoomSuggestion) {
    val mine = x.voters.contains(s.me)
    val auto = s.room.settings.autoQueueVotes
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(x.track.thumbnail, Modifier.size(44.dp), RoundedCornerShape(8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(x.track.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${x.track.artist} · propuesta por ${if (x.fromId == s.me) "ti" else x.fromName}" +
                    if (auto > 0) " · ${x.votes}/$auto" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { client.voteSuggestion(x.id, !mine) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ThumbUp,
                    if (mine) "Quitar tu voto" else "Votar",
                    Modifier.size(20.dp),
                    tint = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("${x.votes}", fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
        if (s.isHost) {
            IconButton(onClick = { client.decideSuggestion(x.id, true) }) { Icon(Icons.Rounded.Check, "Añadir a la cola") }
            IconButton(onClick = { client.decideSuggestion(x.id, false) }) { Icon(Icons.Rounded.Close, "Descartar") }
        }
    }
}

/** QR del enlace de la sala, pintado con Canvas (fondo blanco y margen para que lo lea cualquier cámara). */
@Composable
private fun RoomQr(link: String, modifier: Modifier = Modifier) {
    val matrix = remember(link) { runCatching { qrMatrix(link) }.getOrNull() }
    Box(modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(6.dp)) {
        if (matrix != null && matrix.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val n = matrix.size
                val cell = size.minDimension / n
                for (y in 0 until n) {
                    for (x in 0 until matrix[y].size) {
                        if (matrix[y][x]) {
                            drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
            }
        } else {
            Text("QR no disponible", color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
        }
    }
}
