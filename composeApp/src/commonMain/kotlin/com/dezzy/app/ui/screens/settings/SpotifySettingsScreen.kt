/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Integración de Spotify»: cuenta, importación de la biblioteca y opciones. */
@Composable
internal fun SpotifySettingsScreen(ctx: ScreenContext) {
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val session = sessions[SessionProvider.SPOTIFY]
    val autoSync by ctx.prefs.spotifyAutoSync.collectAsState(true)
    val likedAsPlaylist by ctx.prefs.spotifyLikedAsPlaylist.collectAsState(true)
    var confirmSignOut by remember { mutableStateOf(false) }

    fun open(page: SettingsPage) = ctx.navigator.push(Screen.Settings(page))

    SettingsPage(title = "Integración de Spotify", onBack = { ctx.navigator.back() }) {
        item(key = "account") {
            SettingsGroup(label = "Cuenta") {
                if (session != null) {
                    AccountProfileRow(
                        name = session.name ?: "Cuenta de Spotify",
                        handle = session.handle,
                        avatarUrl = session.avatarUrl,
                    )
                    if (!session.isValid) {
                        SettingsDivider()
                        SettingsRow(
                            title = "Vuelve a iniciar sesión",
                            subtitle = "Spotify rechazó la sesión guardada",
                            icon = Icons.AutoMirrored.Rounded.Login,
                            badge = true,
                            onClick = { open(SettingsPage.SPOTIFY_LOGIN) },
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        title = "Cerrar sesión",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = { confirmSignOut = true },
                    )
                } else {
                    SettingsRow(
                        title = "No has iniciado sesión",
                        subtitle = "Inicia sesión para importar tu biblioteca",
                        icon = Icons.AutoMirrored.Rounded.Login,
                        onClick = { open(SettingsPage.SPOTIFY_LOGIN) },
                    )
                }
            }
        }
        if (session != null) {
            item(key = "import") {
                SettingsGroup(label = "Biblioteca") {
                    SettingsRow(
                        title = "Importar tu biblioteca",
                        subtitle = "Elige qué listas de Spotify traer a Dezzy",
                        icon = Icons.Rounded.LibraryMusic,
                        onClick = { open(SettingsPage.SPOTIFY_IMPORT) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Mantener sincronizado",
                        subtitle = "Actualiza cada día las listas que ya importaste",
                        icon = Icons.Rounded.Sync,
                        checked = autoSync,
                        onCheckedChange = { v -> ctx.scope.launch { ctx.prefs.setSpotifyAutoSync(v) } },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Me gusta como lista aparte",
                        subtitle = if (likedAsPlaylist) {
                            "Tus me gusta de Spotify se guardan en una lista aparte; tus me gusta de la app quedan intactos."
                        } else {
                            "Además de la lista, cada canción se marca como me gusta en la app."
                        },
                        icon = Icons.Rounded.Favorite,
                        checked = likedAsPlaylist,
                        onCheckedChange = { v -> ctx.scope.launch { ctx.prefs.setSpotifyLikedAsPlaylist(v) } },
                    )
                }
            }
            item(key = "options") {
                SettingsGroup(label = "Opciones") {
                    SettingsPendingRow("Usar para búsquedas", "6b", ctx, icon = Icons.Rounded.Search)
                    SettingsDivider()
                    SettingsPendingRow("Usar para la pantalla de inicio", "6b", ctx, icon = Icons.Rounded.Home)
                    SettingsDivider()
                    SettingsPendingRow("Exclusivo de inicio", "6b", ctx, icon = Icons.Rounded.Home)
                    SettingsDivider()
                    SettingsPendingRow("Sincronizar Me Gusta en vivo", "6b", ctx, icon = Icons.Rounded.Sync)
                    SettingsDivider()
                    SettingsPendingRow(
                        "Descargar para escuchar sin conexión", "8", ctx,
                        subtitle = "Descarga el audio de las listas importadas. Llega en la fase 8",
                        icon = Icons.Rounded.Download,
                    )
                }
            }
        }
        item(key = "info") {
            SettingsGroup(label = "Información") {
                SettingsRow(
                    title = "Tus pistas de Spotify se mapean con la mejor coincidencia en YouTube Music.",
                    icon = Icons.Rounded.Info,
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se cerrará tu sesión de Spotify. Las listas ya importadas se quedan en Dezzy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    ctx.scope.launch { accounts.signOut(SessionProvider.SPOTIFY) }
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
        )
    }
}
