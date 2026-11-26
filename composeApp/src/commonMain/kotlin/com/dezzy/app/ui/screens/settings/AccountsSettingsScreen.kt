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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.dezzy.app.source.youtube.YouTubeLibrarySync
import com.dezzy.app.ui.components.CoverImage
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Cuentas y servicios»: sesión de YouTube Music, Spotify y las integraciones que llegan después. */
@Composable
internal fun AccountsSettingsScreen(ctx: ScreenContext) {
    val accounts: AccountStore = koinInject()
    val sync: YouTubeLibrarySync = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val youtube = sessions[SessionProvider.YOUTUBE]
    val spotify = sessions[SessionProvider.SPOTIFY]
    var confirmSignOut by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    fun open(page: SettingsPage) = ctx.navigator.push(Screen.Settings(page))

    SettingsPage(title = "Cuentas y servicios", onBack = { ctx.navigator.back() }) {
        item(key = "youtube") {
            SettingsGroup(label = "YouTube Music") {
                if (youtube != null) {
                    AccountProfileRow(
                        name = youtube.name ?: "Cuenta de YouTube Music",
                        handle = youtube.handle,
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
                    SettingsDivider()
                    SettingsRow(
                        title = "Cerrar sesión",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = { confirmSignOut = true },
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
        item(key = "spotify") {
            SettingsGroup(label = "Spotify") {
                SettingsRow(
                    title = "Spotify",
                    subtitle = when {
                        spotify == null -> "Sin conectar"
                        !spotify.isValid -> "Vuelve a iniciar sesión"
                        else -> spotify.name?.let { "Conectado como $it" } ?: "Conectado"
                    },
                    icon = Icons.Rounded.MusicNote,
                    badge = spotify != null && !spotify.isValid,
                    onClick = { open(SettingsPage.SPOTIFY) },
                )
            }
        }
        item(key = "services") {
            SettingsGroup(label = "Servicios") {
                SettingsPendingRow("Last.fm", "6b", ctx, icon = Icons.Rounded.GraphicEq)
                SettingsDivider()
                SettingsPendingRow("ListenBrainz", "6b", ctx, icon = Icons.Rounded.Sensors)
                SettingsDivider()
                SettingsPendingRow("Discord", "6b", ctx, icon = Icons.Rounded.Hub)
            }
        }
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
                    if (handle.startsWith("@")) handle else "@$handle",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
