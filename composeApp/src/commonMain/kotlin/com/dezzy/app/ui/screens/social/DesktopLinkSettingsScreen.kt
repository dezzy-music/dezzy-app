/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.feature.social.DesktopLink
import com.dezzy.app.feature.social.DesktopLinkState
import com.dezzy.app.feature.social.P2PIdentity
import com.dezzy.app.ui.components.QrScanner
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.SettingsDivider
import com.dezzy.app.ui.screens.settings.SettingsGroup
import com.dezzy.app.ui.screens.settings.SettingsPage
import com.dezzy.app.ui.screens.settings.SettingsRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** «Vincular con el ordenador»: pasa las sesiones de este dispositivo a Dezzy de escritorio. */
@Composable
fun DesktopLinkSettingsScreen(ctx: ScreenContext) {
    val link: DesktopLink = koinInject()
    val accounts: AccountStore = koinInject()
    val identity: P2PIdentity = koinInject()
    val state by link.state.collectAsState()
    val idState by identity.state.collectAsState()
    var code by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var youtube by remember { mutableStateOf<String?>(null) }
    var spotify by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { identity.load() }
        youtube = runCatching { accounts.account(SessionProvider.YOUTUBE) }.getOrNull()?.let { it.name ?: it.handle ?: "Con sesión" }
        spotify = runCatching { accounts.account(SessionProvider.SPOTIFY) }.getOrNull()?.let { it.name ?: "Con sesión" }
    }

    fun start(raw: String) {
        if (state is DesktopLinkState.Searching || state is DesktopLinkState.Sending) return
        ctx.scope.launch { link.link(raw) }
    }

    if (scanning) {
        PlatformBackHandler(enabled = true) { scanning = false }
        QrScanner(
            onResult = { text ->
                scanning = false
                code = link.parseCode(text)?.chunked(4)?.joinToString("-") ?: text
                start(text)
            },
            onClose = { scanning = false },
        )
        return
    }
    val busy = state is DesktopLinkState.Searching || state is DesktopLinkState.Sending

    SettingsPage("Vincular con el ordenador", onBack = { link.cancel(); ctx.navigator.back() }) {
        item(key = "intro") {
            Text(
                "Abre Dezzy en el ordenador, elige «Vincular con el móvil» y escanea el código QR o escribe el código que verás allí.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        item(key = "shared") {
            SettingsGroup("Se pasará al ordenador") {
                SettingsRow("YouTube Music", youtube ?: "Sin sesión iniciada", Icons.Rounded.LibraryMusic)
                SettingsDivider()
                SettingsRow("Spotify", spotify ?: "Sin sesión iniciada", Icons.Rounded.MusicNote)
                SettingsDivider()
                SettingsRow(
                    "Red social",
                    if (idState.ready) "Tu cuenta y tus chats" else "Aún no tienes cuenta",
                    Icons.Rounded.Groups,
                )
            }
        }
        if (state is DesktopLinkState.Linked) {
            item(key = "done") {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Ordenador vinculado", fontSize = 20.sp)
                    DezzyOutlinedButton(onClick = { link.cancel(); code = "" }) { Text("Vincular otro ordenador") }
                }
            }
        } else {
            item(key = "scan") {
                SettingsGroup("Con la cámara") {
                    SettingsRow(
                        "Escanear el código QR",
                        "Apunta la cámara a la pantalla del ordenador",
                        Icons.Rounded.QrCodeScanner,
                        onClick = if (busy) null else ({ scanning = true }),
                    )
                }
            }
            item(key = "code") {
                SettingsGroup("Con el código") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { input ->
                                val clean = input.uppercase().filter { it.isLetterOrDigit() }.take(8)
                                code = if (clean.length > 4) clean.take(4) + "-" + clean.drop(4) else clean
                            },
                            label = { Text("Código (XXXX-XXXX)") },
                            singleLine = true,
                            enabled = !busy,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DezzyButton(
                            onClick = { start(code) },
                            enabled = !busy && link.parseCode(code) != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Vincular con el código") }
                    }
                }
            }
            item(key = "status") {
                when (val s = state) {
                    DesktopLinkState.Searching -> StatusLine("Buscando el ordenador…", spinner = true)
                    DesktopLinkState.Sending -> StatusLine("Pasando tus cuentas…", spinner = true)
                    is DesktopLinkState.Failed -> StatusLine(s.message, error = true)
                    else -> Unit
                }
            }
        }
        item(key = "footer") {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Computer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Las sesiones viajan cifradas de extremo a extremo y sólo las lee el ordenador que muestra el código.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, spinner: Boolean = false, error: Boolean = false) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (spinner) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text,
            fontSize = 14.sp,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (spinner) 12.dp else 0.dp),
        )
    }
}
