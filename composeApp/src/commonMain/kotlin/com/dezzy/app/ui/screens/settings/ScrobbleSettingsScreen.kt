/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.scrobble.LastFmClient
import com.dezzy.app.feature.scrobble.ListenBrainzClient
import com.dezzy.app.feature.scrobble.ScrobbleEnabledKey
import com.dezzy.app.feature.scrobble.ScrobbleLastFmNowPlayingKey
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val LASTFM_CREATE_APP_URL = "https://www.last.fm/api/account/create"
private const val LISTENBRAINZ_SETTINGS_URL = "https://listenbrainz.org/settings/"

/** «Scrobbling»: cuentas de Last.fm y ListenBrainz y sus opciones. */
@Composable
internal fun ScrobbleSettingsScreen(ctx: ScreenContext) {
    val lastFm: LastFmClient = koinInject()
    val listenBrainz: ListenBrainzClient = koinInject()
    val lastFmState by lastFm.state.collectAsState()
    val lbState by listenBrainz.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val enabled = rememberPreference(ScrobbleEnabledKey, true)
    val nowPlaying = rememberPreference(ScrobbleLastFmNowPlayingKey, true)

    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var lbToken by remember { mutableStateOf("") }
    var confirmLastFmOut by remember { mutableStateOf(false) }
    var confirmLbOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lastFm.load()
        listenBrainz.load()
    }

    fun connectLastFm() {
        ctx.scope.launch {
            val url = lastFm.startConnect()
            if (url != null) uriHandler.openUri(url)
        }
    }

    val session = lastFmState.session
    val app = lastFmState.app
    val account = lbState.account

    SettingsPage(title = "Scrobbling", onBack = { ctx.navigator.back() }) {
        item(key = "lastfm") {
            SettingsGroup(label = "Last.fm") {
                SettingsRow(
                    title = session?.name ?: "Last.fm",
                    subtitle = when {
                        session != null -> lastFmState.scrobbles?.let { "$it scrobbles" } ?: "Conectado"
                        app == null -> "Pega tu clave de API para poder conectar"
                        else -> "No conectado"
                    },
                    icon = Icons.Rounded.MusicNote,
                )
                if (session != null && lastFmState.needsReconnect) {
                    SettingsDivider()
                    SettingsRow(
                        title = "Hay que volver a conectar",
                        subtitle = "Last.fm rechazó la sesión guardada",
                        icon = Icons.AutoMirrored.Rounded.Login,
                        badge = true,
                        onClick = { connectLastFm() },
                    )
                }
                if (app == null) {
                    SettingsDivider()
                    FieldBlock {
                        Text(
                            "Last.fm pide una clave propia para cada persona. Créala gratis en la web de Last.fm y pega aquí la clave de API y el secreto compartido.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { uriHandler.openUri(LASTFM_CREATE_APP_URL) }) { Text("Crear clave de API") }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Clave de API") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = apiSecret,
                            onValueChange = { apiSecret = it },
                            label = { Text("Secreto compartido") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        lastFmState.error?.let { ErrorText(it) }
                        Button(
                            onClick = { ctx.scope.launch { if (lastFm.saveApp(apiKey, apiSecret)) { apiKey = ""; apiSecret = "" } } },
                            enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
                        ) { Text("Guardar clave") }
                    }
                } else if (session == null) {
                    SettingsDivider()
                    if (lastFmState.connecting) {
                        SettingsRow(
                            title = "Esperando autorización…",
                            subtitle = "Acepta el permiso en el navegador. Toca para cancelar.",
                            icon = Icons.Rounded.Sync,
                            onClick = { lastFm.cancelConnect() },
                        )
                    } else {
                        SettingsRow(
                            title = "Conectar",
                            subtitle = "Se abre Last.fm en el navegador para dar permiso",
                            icon = Icons.AutoMirrored.Rounded.Login,
                            onClick = { connectLastFm() },
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        title = "Cambiar clave de API",
                        subtitle = "Olvida la clave y el secreto guardados",
                        icon = Icons.Rounded.Info,
                        onClick = { ctx.scope.launch { lastFm.clearApp() } },
                    )
                    lastFmState.error?.let { FieldBlock { ErrorText(it) } }
                } else {
                    SettingsDivider()
                    SettingsRow(
                        title = "Desconectar",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = { confirmLastFmOut = true },
                    )
                }
            }
        }
        item(key = "listenbrainz") {
            SettingsGroup(label = "ListenBrainz") {
                SettingsRow(
                    title = account?.userName ?: "ListenBrainz",
                    subtitle = if (account != null) "Conectado" else "No conectado",
                    icon = Icons.Rounded.MusicNote,
                )
                SettingsDivider()
                if (account != null) {
                    if (lbState.needsReconnect) {
                        SettingsRow(
                            title = "Hay que volver a conectar",
                            subtitle = "ListenBrainz rechazó el token. Desconecta y pega uno nuevo.",
                            icon = Icons.AutoMirrored.Rounded.Login,
                            badge = true,
                        )
                        SettingsDivider()
                    }
                    SettingsRow(
                        title = "Desconectar",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = { confirmLbOut = true },
                    )
                } else {
                    FieldBlock {
                        TextButton(onClick = { uriHandler.openUri(LISTENBRAINZ_SETTINGS_URL) }) { Text("Abrir ajustes de ListenBrainz") }
                        OutlinedTextField(
                            value = lbToken,
                            onValueChange = { lbToken = it },
                            label = { Text("Token de usuario") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        lbState.error?.let { ErrorText(it) }
                        Button(
                            onClick = { ctx.scope.launch { listenBrainz.connect(lbToken); if (listenBrainz.state.value.account != null) lbToken = "" } },
                            enabled = lbToken.isNotBlank() && !lbState.busy,
                        ) { Text(if (lbState.busy) "Comprobando…" else "Conectar") }
                    }
                }
            }
        }
        item(key = "options") {
            SettingsGroup(label = "Opciones") {
                SettingsSwitchRow(
                    title = "Activar scrobbling",
                    subtitle = "Envía lo que escuchas a las cuentas conectadas",
                    icon = Icons.Rounded.MusicNote,
                    checked = enabled.value,
                    onCheckedChange = { enabled.value = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Avisar a Last.fm de lo que suena ahora",
                    subtitle = "Muestra la canción actual en tu perfil mientras suena",
                    icon = Icons.Rounded.Sync,
                    checked = nowPlaying.value,
                    onCheckedChange = { nowPlaying.value = it },
                    enabled = enabled.value,
                )
            }
        }
        item(key = "info") {
            SettingsGroup(label = "Información") {
                SettingsRow(
                    title = "Una canción cuenta cuando la escuchas la mitad o 4 minutos, y dura más de 30 segundos. Si no hay red, se reintenta más tarde.",
                    icon = Icons.Rounded.Info,
                )
            }
        }
    }

    if (confirmLastFmOut) {
        AlertDialog(
            onDismissRequest = { confirmLastFmOut = false },
            title = { Text("Desconectar Last.fm") },
            text = { Text("Dezzy dejará de enviar scrobbles a Last.fm. Tu clave de API se queda guardada.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLastFmOut = false
                    ctx.scope.launch { lastFm.disconnect() }
                }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmLastFmOut = false }) { Text("Cancelar") } },
        )
    }
    if (confirmLbOut) {
        AlertDialog(
            onDismissRequest = { confirmLbOut = false },
            title = { Text("Desconectar ListenBrainz") },
            text = { Text("Dezzy dejará de enviar escuchas a ListenBrainz y olvidará el token.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLbOut = false
                    ctx.scope.launch { listenBrainz.disconnect() }
                }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmLbOut = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FieldBlock(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
}
