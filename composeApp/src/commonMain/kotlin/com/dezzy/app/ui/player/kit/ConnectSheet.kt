/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.connect.ConnectDevice
import com.dezzy.app.feature.connect.ConnectPlaybackBridge
import com.dezzy.app.feature.connect.ConnectPlaybackBridge.Role
import com.dezzy.app.feature.connect.ConnectService
import com.dezzy.app.feature.connect.DeviceKind
import org.koin.compose.koinInject

/**
 * Hoja «Conectar a un dispositivo»: tus Dezzy de la red (Dezzy Connect), la salida de audio del
 * sistema (Bluetooth, auriculares) y Escuchar juntos. Chromecast llega más tarde.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectDeviceBottomSheet(
    onDismissRequest: () -> Unit,
    onOpenListenTogether: (() -> Unit)? = null,
) {
    val service: ConnectService = koinInject()
    val bridge: ConnectPlaybackBridge = koinInject()
    val audio = rememberAudioOutputControls()
    val phone = rememberPhoneDeviceName()
    val state by service.state.collectAsState()
    val role by bridge.role.collectAsState()
    val members by bridge.members.collectAsState()
    val hub by bridge.hub.collectAsState()
    val controlAll by bridge.controlAll.collectAsState()
    val remote by bridge.remote.collectAsState()
    val offset by bridge.offset.collectAsState()
    val drift by bridge.drift.collectAsState()
    val busy by bridge.busy.collectAsState()
    val notice by bridge.notice.collectAsState()

    LaunchedEffect(Unit) {
        bridge.start()
        bridge.search()
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Conectar a un dispositivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (state.searching) {
                        Text("Buscando…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { bridge.search() }) { Icon(Icons.Rounded.Refresh, "Buscar dispositivos") }
                }
            }
            Spacer(Modifier.height(12.dp))

            notice?.let { text ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = { bridge.clearNotice() }) { Icon(Icons.Rounded.Close, "Cerrar aviso") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            // Sesión activa.
            if (role != Role.NONE) {
                SessionCard(
                    role = role,
                    describe = bridge.describe(),
                    nowTitle = remote?.queue?.getOrNull(remote?.index ?: -1)?.title,
                    nowArtists = remote?.queue?.getOrNull(remote?.index ?: -1)?.artists,
                    playing = remote?.playing == true,
                    volume = remote?.vol?.toFloat(),
                    controlAll = controlAll,
                    phoneName = phone,
                    offset = offset,
                    drift = drift,
                    hasMembers = members.isNotEmpty() || hub != null,
                    onToggle = bridge::remoteToggle,
                    onNext = bridge::remoteNext,
                    onPrev = bridge::remotePrev,
                    onVolume = { bridge.remoteVolume(it.toDouble()) },
                    onControlAll = bridge::setControlAll,
                    onOffset = { bridge.setOffset(it) },
                    onBringHere = bridge::bringHere,
                    onStop = bridge::stopHere,
                    onRelease = bridge::release,
                )
                members.filter { it.host && role != Role.FOLLOWER && role != Role.REMOTE }.forEach { m ->
                    MemberRow(
                        name = m.name,
                        kind = m.kind,
                        drift = m.drift,
                        offset = m.offset,
                        onOffset = { bridge.setOffset(it, m.id) },
                        onVolume = { bridge.setMemberVolume(m.id, it) },
                        volume = m.volume,
                        onDrop = { bridge.dropPeer(m.id) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }

            // Este dispositivo.
            val here = role == Role.NONE || role == Role.HOST || role == Role.FOLLOWER
            ConnectRow(
                icon = Icons.Rounded.Smartphone,
                title = state.self.name.ifBlank { phone },
                subtitle = if (here) "Reproduciendo aquí" else "Este dispositivo",
                trailing = if (here) Icons.Rounded.Check else null,
            )

            // Otros Dezzy del grupo.
            state.devices.forEach { d ->
                DeviceRow(
                    device = d,
                    inSession = d.id == hub?.id || members.any { it.id == d.id },
                    canShare = bridge.hasLivePlayback(),
                    onControl = { bridge.joinRemote(d.id) },
                    onPull = { bridge.pullFrom(d.id) },
                    onFollow = { bridge.followDevice(d.id) },
                    onMove = { bridge.moveTo(d.id) },
                    onAlso = { bridge.alsoHere(d.id) },
                    onForget = { bridge.forget(d.id) },
                )
            }
            if (state.devices.isEmpty()) {
                Text(
                    if (state.ready) {
                        "No hay otros Dezzy a la vista. Abre Dezzy en el otro dispositivo, en la misma red y con la misma cuenta, y pulsa buscar."
                    } else {
                        "Vincula tu cuenta de Dezzy para ver tus otros dispositivos aquí."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            // Salida de audio del sistema.
            if (audio.supported) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                ConnectRow(
                    icon = if (audio.outputName != null) Icons.Rounded.Headphones else Icons.Rounded.Speaker,
                    title = "Salida de audio",
                    subtitle = audio.outputName ?: "Altavoz del teléfono",
                    onClick = audio.openSwitcher,
                )
                audio.bluetooth.filter { it != audio.outputName }.forEach {
                    ConnectRow(icon = Icons.Rounded.Bluetooth, title = it, subtitle = "Bluetooth conectado", onClick = audio.openSwitcher)
                }
                ConnectRow(
                    icon = Icons.Rounded.Bluetooth,
                    title = "Más salidas de audio",
                    subtitle = "Abre los ajustes de Bluetooth",
                    onClick = audio.openBluetoothSettings,
                )
            }

            if (onOpenListenTogether != null) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                ConnectRow(
                    icon = Icons.Rounded.Groups,
                    title = "Escuchar juntos con amigos",
                    subtitle = "Escucha en sincronía con tus amigos",
                    onClick = onOpenListenTogether,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    role: Role,
    describe: String?,
    nowTitle: String?,
    nowArtists: String?,
    playing: Boolean,
    volume: Float?,
    controlAll: Boolean,
    phoneName: String,
    offset: Double,
    drift: Double?,
    hasMembers: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onVolume: (Float) -> Unit,
    onControlAll: (Boolean) -> Unit,
    onOffset: (Double) -> Unit,
    onBringHere: () -> Unit,
    onStop: () -> Unit,
    onRelease: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Escuchando en", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                describe ?: "Sesión de Dezzy Connect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (role == Role.REMOTE || role == Role.LEADER) {
                Spacer(Modifier.height(8.dp))
                Text(
                    nowTitle ?: "Esperando a que suene algo…",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (!nowArtists.isNullOrBlank()) {
                    Text(
                        nowArtists,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Rounded.SkipPrevious, "Anterior") }
                    IconButton(onClick = onToggle) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Reproducir")
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Siguiente") }
                }
                if (volume != null) {
                    var local by remember(volume) { mutableStateOf(volume) }
                    Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onVolume(local) })
                }
            }

            if ((role == Role.HOST || role == Role.LEADER) && hasMembers) {
                Spacer(Modifier.height(8.dp))
                Text("Quién puede controlar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = controlAll, onClick = { onControlAll(true) }, label = { Text("Todos") })
                    FilterChip(selected = !controlAll, onClick = { onControlAll(false) }, label = { Text("Sólo $phoneName") })
                }
            }

            if (role == Role.FOLLOWER) {
                Spacer(Modifier.height(8.dp))
                Text("Sincronía", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onOffset(offset - 20.0) }) { Icon(Icons.Rounded.Remove, "Adelantar") }
                    Text(
                        "${offset.toInt()} ms" + (drift?.let { "  ·  diferencia ${it.toInt()} ms" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    IconButton(onClick = { onOffset(offset + 20.0) }) { Icon(Icons.Rounded.Add, "Retrasar") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (role == Role.REMOTE || role == Role.LEADER) TextButton(onClick = onBringHere) { Text("Reproducir aquí") }
                when (role) {
                    Role.REMOTE -> TextButton(onClick = onRelease) { Text("Dejar de controlar") }
                    else -> TextButton(onClick = onStop) { Text("Dejar de escuchar") }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    name: String,
    kind: DeviceKind,
    drift: Double?,
    offset: Double,
    volume: Double?,
    onOffset: (Double) -> Unit,
    onVolume: (Double) -> Unit,
    onDrop: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (kind == DeviceKind.phone) Icons.Rounded.Smartphone else Icons.Rounded.Computer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    drift?.let { "Diferencia ${it.toInt()} ms" } ?: "Sincronizando…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onOffset(offset - 20.0) }) { Icon(Icons.Rounded.Remove, "Adelantar") }
            Text("${offset.toInt()} ms", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { onOffset(offset + 20.0) }) { Icon(Icons.Rounded.Add, "Retrasar") }
            IconButton(onClick = onDrop) { Icon(Icons.Rounded.Close, "Quitar de la sesión") }
        }
        if (volume != null) {
            var local by remember(volume) { mutableStateOf(volume.toFloat()) }
            Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onVolume(local.toDouble()) })
        }
    }
}

@Composable
private fun DeviceRow(
    device: ConnectDevice,
    inSession: Boolean,
    canShare: Boolean,
    onControl: () -> Unit,
    onPull: () -> Unit,
    onFollow: () -> Unit,
    onMove: () -> Unit,
    onAlso: () -> Unit,
    onForget: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val usable = device.available || device.connected
    val subtitle = when {
        device.now != null -> (if (device.now.playing) "Suena: " else "En pausa: ") + device.now.title
        device.connected -> "Conectado"
        device.available -> "Disponible"
        else -> "No disponible ahora"
    }
    Box {
        ConnectRow(
            icon = if (device.kind == DeviceKind.phone) Icons.Rounded.Smartphone else Icons.Rounded.Computer,
            title = device.name,
            subtitle = subtitle,
            trailing = if (inSession) Icons.Rounded.Check else Icons.Rounded.MoreVert,
            dim = !usable,
            onClick = { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Controlar") }, enabled = usable, onClick = { menu = false; onControl() })
            DropdownMenuItem(text = { Text("Reproducir aquí") }, enabled = usable, onClick = { menu = false; onPull() })
            DropdownMenuItem(text = { Text("Escuchar lo mismo") }, enabled = usable, onClick = { menu = false; onFollow() })
            DropdownMenuItem(text = { Text("Pasar la mía") }, enabled = usable && canShare, onClick = { menu = false; onMove() })
            DropdownMenuItem(text = { Text("Que suene también allí") }, enabled = usable && canShare, onClick = { menu = false; onAlso() })
            DropdownMenuItem(text = { Text("Olvidar") }, onClick = { menu = false; onForget() })
        }
    }
}

@Composable
private fun ConnectRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: ImageVector? = null,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dim) 0.5f else 1f),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) Icon(trailing, null, tint = MaterialTheme.colorScheme.primary)
    }
}
