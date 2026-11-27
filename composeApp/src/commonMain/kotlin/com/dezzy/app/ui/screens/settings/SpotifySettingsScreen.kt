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
import androidx.compose.runtime.setValue
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.ui.screens.downloads.enqueueSpotifyImports
import com.dezzy.app.source.spotify.SpotifyHomeOnlyKey
import com.dezzy.app.source.spotify.SpotifyLiveLikeSyncKey
import com.dezzy.app.source.spotify.SpotifyUseHomeKey
import com.dezzy.app.source.spotify.SpotifyUseSearchKey
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Integración de Spotify»: cuenta, importación de la biblioteca y opciones. */
@Composable
internal fun SpotifySettingsScreen(ctx: ScreenContext) {
    val accounts: AccountStore = koinInject()
    val downloads: DownloadManager = koinInject()
    val sessions by accounts.sessions.collectAsState()
    val session = sessions[SessionProvider.SPOTIFY]
    val autoSync by ctx.prefs.spotifyAutoSync.collectAsState(true)
    val likedAsPlaylist by ctx.prefs.spotifyLikedAsPlaylist.collectAsState(true)
    var confirmSignOut by remember { mutableStateOf(false) }
    var useSearch by rememberPreference(SpotifyUseSearchKey, false)
    var useHome by rememberPreference(SpotifyUseHomeKey, false)
    var homeOnly by rememberPreference(SpotifyHomeOnlyKey, false)
    var liveLikes by rememberPreference(SpotifyLiveLikeSyncKey, false)

    fun open(page: SettingsPage) = ctx.navigator.push(Screen.Settings(page))

    val spotifySyncing by org.koin.compose.koinInject<com.dezzy.app.source.spotify.SpotifyImporter>().syncing.collectAsState()
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
                        subtitle = if (spotifySyncing) "Sincronizando tu biblioteca en segundo plano…" else "Elige qué listas de Spotify traer a Dezzy",
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
                    SettingsSwitchRow(
                        title = "Usar para búsquedas",
                        subtitle = "Añade resultados de tu Spotify a la búsqueda",
                        icon = Icons.Rounded.Search,
                        checked = useSearch,
                        onCheckedChange = { useSearch = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Usar para la pantalla de inicio",
                        subtitle = "Muestra tus me gusta recientes de Spotify en Inicio",
                        icon = Icons.Rounded.Home,
                        checked = useHome,
                        onCheckedChange = { useHome = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Exclusivo de inicio",
                        subtitle = "En Inicio, sólo estanterías de Spotify en lugar de las del catálogo",
                        icon = Icons.Rounded.Home,
                        checked = homeOnly && useHome,
                        onCheckedChange = { homeOnly = it },
                        enabled = useHome,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Sincronizar Me gusta en vivo",
                        subtitle = "Los me gusta nuevos de Spotify se añaden a tus favoritos",
                        icon = Icons.Rounded.Sync,
                        checked = liveLikes,
                        onCheckedChange = { liveLikes = it },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Descargar para escuchar sin conexión",
                        subtitle = "Descarga el audio de las listas importadas",
                        icon = Icons.Rounded.Download,
                        onClick = {
                            ctx.scope.launch {
                                val n = enqueueSpotifyImports(ctx, downloads)
                                ctx.toast(if (n == 0) "No hay canciones importadas que descargar" else "$n canciones a descargas")
                            }
                        },
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
