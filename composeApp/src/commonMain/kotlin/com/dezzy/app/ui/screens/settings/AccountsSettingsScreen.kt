/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.feature.discord.DiscordPresence
import com.dezzy.app.feature.scrobble.LastFmClient
import com.dezzy.app.feature.scrobble.ListenBrainzClient
import com.dezzy.app.source.youtube.YouTubeLibrarySync
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.prefs.YtAdvancedCookieKey
import com.dezzy.app.ui.screens.settings.prefs.YtAdvancedDataSyncIdKey
import com.dezzy.app.ui.screens.settings.prefs.YtAdvancedVisitorDataKey
import com.dezzy.app.ui.screens.settings.prefs.YtSyncLibraryKey
import com.dezzy.app.ui.screens.settings.prefs.YtUseAccountForBrowseKey
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Cuentas y servicios»: sesión de YouTube Music, servicios conectados, extras y avanzado. */
@Composable
internal fun AccountsSettingsScreen(ctx: ScreenContext) {
    val accounts: AccountStore = koinInject()
    val sync: YouTubeLibrarySync = koinInject()
    val discord: DiscordPresence = koinInject()
    val lastFm: LastFmClient = koinInject()
    val listenBrainz: ListenBrainzClient = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val discordState by discord.state.collectAsState()
    val lastFmState by lastFm.state.collectAsState()
    val lbState by listenBrainz.state.collectAsState()
    val youtube = sessions[SessionProvider.YOUTUBE]
    val spotify = sessions[SessionProvider.SPOTIFY]
    var confirmSignOut by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var useForBrowse by rememberPreference(YtUseAccountForBrowseKey, true)
    var syncLibrary by rememberPreference(YtSyncLibraryKey, true)
    var cookie by rememberPreference(YtAdvancedCookieKey, "")
    var visitorData by rememberPreference(YtAdvancedVisitorDataKey, "")
    var dataSyncId by rememberPreference(YtAdvancedDataSyncIdKey, "")

    LaunchedEffect(lastFm, listenBrainz) {
        lastFm.load()
        listenBrainz.load()
    }

    fun open(page: SettingsPage) = ctx.navigator.push(Screen.Settings(page))

    SettingsPage(title = "Cuentas y servicios", onBack = { ctx.navigator.back() }) {
        item(key = "back") {
            SettingsGroup {
                SettingsRow(
                    title = "Ajustes",
                    subtitle = "Apariencia, reproducción, descargas y privacidad",
                    onClick = { ctx.navigator.back() },
                )
            }
        }
        item(key = "youtube") {
            SettingsGroup(label = "Tu cuenta") {
                if (youtube != null) {
                    AccountProfileRow(
                        name = youtube.name ?: "Cuenta de YouTube Music",
                        handle = youtube.handle ?: "YouTube Music",
                        avatarUrl = youtube.avatarUrl,
                    )
                    if (!youtube.isValid) {
                        SettingsDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { open(SettingsPage.YOUTUBE_LOGIN) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Vuelve a iniciar sesión",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Usar la cuenta para explorar",
                        subtitle = "Esto puede influir en el contenido que ves y, p…",
                        checked = useForBrowse,
                        onCheckedChange = { useForBrowse = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Sincronizar biblioteca",
                        subtitle = "Me gusta, playlists y suscripciones con YouT…",
                        checked = syncLibrary,
                        onCheckedChange = { syncLibrary = it },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Playlists a sincronizar",
                        subtitle = "Elige qué playlists se descargan a tu biblioteca",
                        icon = Icons.Rounded.MusicNote,
                        onClick = { ctx.navigator.push(Screen.AccountPlaylists) },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Sincronizar me gusta",
                        subtitle = if (syncing) "Sincronizando…" else "Añade a favoritos los me gusta de tu cuenta",
                        icon = Icons.Rounded.Favorite,
                        onClick = if (syncing) null else ({
                            syncing = true
                            ctx.scope.launch {
                                val added = runCatching { sync.syncLikes() }
                                syncing = false
                                added.fold(
                                    { ctx.toast("$it canciones añadidas") },
                                    { ctx.toast("No se pudo sincronizar. Inténtalo de nuevo.") },
                                )
                            }
                        }),
                    )
                } else {
                    SettingsRow(
                        title = "Iniciar sesión con Google",
                        subtitle = "Sincroniza tus listas y me gusta de YouTube Music",
                        icon = Icons.AutoMirrored.Rounded.Login,
                        onClick = { open(SettingsPage.YOUTUBE_LOGIN) },
                    )
                }
            }
        }
        item(key = "services") {
            SettingsGroup(label = "Servicios conectados") {
                SettingsRow(
                    title = "Spotify",
                    subtitle = when {
                        spotify == null -> "Importa playlists y tu biblioteca"
                        !spotify.isValid -> "Vuelve a iniciar sesión"
                        else -> "Importa playlists y tu biblioteca"
                    },
                    icon = Icons.Rounded.MusicNote,
                    badge = spotify != null && !spotify.isValid,
                    onClick = { open(SettingsPage.SPOTIFY) },
                    trailing = { ConnectChip(spotify != null) { open(SettingsPage.SPOTIFY) } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Discord",
                    subtitle = "Muestra lo que escuchas en tu per…",
                    icon = Icons.Rounded.Hub,
                    onClick = { open(SettingsPage.DISCORD) },
                    trailing = { ConnectChip(discordState.signedIn) { open(SettingsPage.DISCORD) } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Last.fm",
                    subtitle = "Registra tus escuchas (scrobbli…",
                    icon = Icons.Rounded.GraphicEq,
                    onClick = { open(SettingsPage.SCROBBLING) },
                    trailing = { ConnectChip(lastFmState.session != null) { open(SettingsPage.SCROBBLING) } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "ListenBrainz",
                    subtitle = "Registro abierto de escuchas",
                    icon = Icons.Rounded.Sensors,
                    onClick = { open(SettingsPage.SCROBBLING) },
                    trailing = { ConnectChip(lbState.account != null) { open(SettingsPage.SCROBBLING) } },
                )
            }
        }
        item(key = "more") {
            SettingsGroup(label = "Más") {
                SettingsRow(
                    title = "Vincular escritorio",
                    subtitle = "Pasa tus cuentas a Dezzy en el ordenador con un QR",
                    icon = Icons.Rounded.Sensors,
                    onClick = { open(SettingsPage.DESKTOP_LINK) },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Escuchar juntos",
                    subtitle = "Salas para escuchar en sincronía con amigos",
                    icon = Icons.Rounded.Hub,
                    onClick = { ctx.navigator.push(Screen.Together()) },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Descarga lossless",
                    subtitle = "FLAC y alta resolución",
                    icon = Icons.Rounded.MusicNote,
                    onClick = { open(SettingsPage.LOSSLESS) },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Todas las integraciones",
                    subtitle = "Discord, Last.fm, ListenBrainz y otras",
                    icon = Icons.Rounded.GraphicEq,
                    onClick = { open(SettingsPage.SCROBBLING) },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Red social",
                    icon = Icons.Rounded.Hub,
                    onClick = { open(SettingsPage.SOCIAL) },
                )
            }
        }
        item(key = "advanced") {
            SettingsGroup(label = "Avanzado") {
                SettingsRow(
                    title = "Inicio de sesión avanzado",
                    subtitle = "Editar cookie, visitor data y datos de la cuenta",
                    icon = Icons.AutoMirrored.Rounded.Login,
                    onClick = { showAdvanced = true },
                )
                if (youtube != null) {
                    SettingsDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { confirmSignOut = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            "Cerrar sesión de YouTube",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showAdvanced) {
        var c by remember { mutableStateOf(cookie) }
        var v by remember { mutableStateOf(visitorData) }
        var d by remember { mutableStateOf(dataSyncId) }
        AlertDialog(
            onDismissRequest = { showAdvanced = false },
            title = { Text("Inicio de sesión avanzado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(c, { c = it }, label = { Text("Cookie") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(v, { v = it }, label = { Text("Visitor data") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(d, { d = it }, label = { Text("Datos de la cuenta (data sync id)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cookie = c
                    visitorData = v
                    dataSyncId = d
                    showAdvanced = false
                    ctx.toast("Guardado")
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAdvanced = false }) { Text("Cancelar") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se cerrará tu sesión de YouTube Music en este dispositivo. Tu música guardada en Dezzy no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    ctx.scope.launch { accounts.signOut(SessionProvider.YOUTUBE) }
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
        )
    }
}

/** Botón [Conectado]/[Conectar] de la derecha de un servicio. */
@Composable
private fun ConnectChip(connected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            if (connected) "Conectado" else "Conectar",
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fila con el avatar de la cuenta, su nombre y @handle. */
@Composable
internal fun AccountProfileRow(name: String, handle: String?, avatarUrl: String?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverImage(avatarUrl, Modifier.size(48.dp), CircleShape, sizePx = 144)
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 1)
            if (!handle.isNullOrBlank()) {
                Text(
                    if (handle.startsWith("@")) handle else if (handle == "YouTube Music") handle else "@$handle",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
