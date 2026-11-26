/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
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
import com.dezzy.app.feature.social.ListeningVisibility
import com.dezzy.app.feature.social.P2PIdentity
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.settings.SettingsChoiceRow
import com.dezzy.app.ui.screens.settings.SettingsDivider
import com.dezzy.app.ui.screens.settings.SettingsGroup
import com.dezzy.app.ui.screens.settings.SettingsPage
import com.dezzy.app.ui.screens.settings.SettingsRow
import com.dezzy.app.ui.screens.settings.SettingsSwitchRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Red social»: quién ve lo que escuchas, radar, copia de las 12 palabras y cerrar sesión. */
@Composable
fun SocialSettingsScreen(ctx: ScreenContext) {
    val social: P2PSocial = koinInject()
    val identity: P2PIdentity = koinInject()
    val idState by identity.state.collectAsState()
    val visibility by social.listeningVisibility.collectAsState()
    val nearby by social.nearbyVisible.collectAsState()
    var words by remember { mutableStateOf<List<String>?>(null) }
    var showWords by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { identity.load() } }

    if (showWords) {
        PlatformBackHandler(enabled = true) { showWords = false }
        Column(Modifier.fillMaxSize()) {
            TopSpacer()
            ScreenTitle("Tus 12 palabras", onBack = { showWords = false })
            val list = words
            if (list != null) {
                P2PBackupWordsContent(list)
            } else {
                Text("Esta cuenta se restauró con una clave nsec y no tiene palabras.", Modifier.padding(24.dp))
            }
        }
        return
    }

    SettingsPage("Red social", onBack = { ctx.navigator.back() }) {
        item(key = "privacy") {
            SettingsGroup("Privacidad") {
                SettingsChoiceRow(
                    title = "Quién ve lo que escucho",
                    icon = Icons.Rounded.Headphones,
                    options = ListeningVisibility.entries,
                    selected = visibility,
                    label = { if (it == ListeningVisibility.EVERYONE) "Todos" else "Nadie" },
                    onSelect = { social.setListeningVisibility(it) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Visible en el radar",
                    subtitle = "Los que tengas cerca con Dezzy pueden verte en «Cerca».",
                    icon = Icons.Rounded.Radar,
                    checked = nearby,
                    onCheckedChange = { social.setNearbyVisible(it) },
                )
            }
        }
        item(key = "account") {
            SettingsGroup("Cuenta") {
                if (idState.ready) {
                    SettingsRow(
                        title = "Copia de seguridad",
                        subtitle = "Ver mis 12 palabras de recuperación",
                        icon = Icons.Rounded.VpnKey,
                        onClick = {
                            ctx.scope.launch {
                                words = runCatching { identity.words() }.getOrNull()
                                showWords = true
                            }
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Cerrar sesión",
                        subtitle = "Quita la cuenta y los chats de este dispositivo",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = { confirmSignOut = true },
                    )
                } else {
                    SettingsRow(
                        title = "Crear o restaurar cuenta",
                        subtitle = "Aún no tienes cuenta en la red social",
                        icon = Icons.Rounded.PersonAdd,
                        onClick = { ctx.navigator.push(Screen.P2PWelcome) },
                    )
                }
            }
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Cerrar sesión") },
            text = {
                Text("Se borrarán de este dispositivo tu cuenta y tus mensajes. Sin tus 12 palabras no podrás recuperarla.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    ctx.scope.launch {
                        runCatching { social.signOut() }
                        ctx.navigator.back()
                    }
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
        )
    }
}
