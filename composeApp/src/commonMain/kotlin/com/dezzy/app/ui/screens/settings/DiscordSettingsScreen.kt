/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.discord.DiscordEnabledKey
import com.dezzy.app.feature.discord.DiscordPresence
import com.dezzy.app.feature.discord.DiscordShowCoverKey
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.login.PlatformTokenWebView
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Discord»: cuenta y opciones de lo que se muestra en el perfil. */
@Composable
internal fun DiscordSettingsScreen(ctx: ScreenContext) {
    val discord: DiscordPresence = koinInject()
    val state by discord.state.collectAsState()
    val enabled by ctx.prefs.value(DiscordEnabledKey, false).collectAsState(false)
    val showCover by ctx.prefs.value(DiscordShowCoverKey, true).collectAsState(true)
    var showLogin by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    if (showLogin) {
        PlatformBackHandler(enabled = true) { if (!validating) showLogin = false }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showLogin = false }, enabled = !validating) { Text("Cancelar") }
                Text(
                    if (validating) "Comprobando…" else "Iniciar sesión en Discord",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            PlatformTokenWebView(
                startUrl = "https://discord.com/login",
                headerUrlPrefix = "https://discord.com/api",
                onToken = { token ->
                    if (!validating) {
                        validating = true
                        ctx.scope.launch {
                            val result = discord.signIn(token)
                            validating = false
                            showLogin = false
                            result.onFailure { ctx.snackbar.showSnackbar("No se pudo iniciar sesión") }
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        return
    }

    SettingsPage(title = "Discord", onBack = { ctx.navigator.back() }) {
        item(key = "account") {
            SettingsGroup(label = "Cuenta") {
                when {
                    !state.signedIn -> SettingsRow(
                        title = "Sin cuenta",
                        subtitle = "Inicia sesión para mostrar lo que escuchas",
                        icon = Icons.Rounded.Person,
                    )
                    state.needsRelogin -> SettingsRow(
                        title = "Hay que volver a entrar",
                        subtitle = "Discord rechazó la sesión guardada",
                        icon = Icons.Rounded.Person,
                        badge = true,
                    )
                    else -> SettingsRow(
                        title = "Conectado como ${state.userName ?: "Discord"}",
                        subtitle = if (state.connected) "Conectado a Discord" else null,
                        icon = Icons.Rounded.Person,
                    )
                }
                SettingsDivider()
                SettingsRow(
                    title = "Iniciar sesión en Discord",
                    icon = Icons.AutoMirrored.Rounded.Login,
                    onClick = { showLogin = true },
                )
                if (state.signedIn) {
                    SettingsDivider()
                    SettingsRow(
                        title = "Cerrar sesión",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = { confirmSignOut = true },
                    )
                }
            }
        }
        item(key = "options") {
            SettingsGroup(label = "Opciones") {
                SettingsSwitchRow(
                    title = "Mostrar lo que escucho",
                    icon = Icons.Rounded.MusicNote,
                    checked = enabled,
                    onCheckedChange = { v -> ctx.scope.launch { ctx.prefs.set(DiscordEnabledKey, v) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Mostrar la carátula",
                    icon = Icons.Rounded.Image,
                    checked = showCover,
                    onCheckedChange = { v -> ctx.scope.launch { ctx.prefs.set(DiscordShowCoverKey, v) } },
                )
            }
        }
        item(key = "info") {
            SettingsGroup(label = "Información") {
                SettingsRow(
                    title = "Dezzy usa tu cuenta para mostrar lo que escuchas en tu perfil. " +
                        "Discord no lo admite oficialmente; úsalo bajo tu responsabilidad.",
                    icon = Icons.Rounded.Info,
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se borrará tu sesión de Discord de este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    ctx.scope.launch { discord.signOut() }
                }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
        )
    }
}
