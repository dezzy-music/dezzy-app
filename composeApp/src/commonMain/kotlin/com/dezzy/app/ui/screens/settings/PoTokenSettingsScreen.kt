/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext

/** Generación de PoToken para los clientes web (el motor de reproducción lo decide al resolver). */
internal val PoTokenWebEnabledKey = booleanPreferencesKey("poTokenWebEnabled")

private val PoTokenClients = listOf("web", "mweb", "web_safari", "web_embedded", "web_creator", "web_music")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PoTokenSettingsScreen(ctx: ScreenContext) {
    var enabled by rememberPreference(PoTokenWebEnabledKey, true)
    val clipboard = LocalClipboardManager.current

    // El motor de reproducción guarda su sesión y PoToken en privado: aún no hay forma de leerlos desde aquí.
    val poTokenGvs: String? = null
    val poTokenPlayer: String? = null
    val visitorData: String? = null

    SettingsPage(title = "PO Token Generation", onBack = { ctx.navigator.back() }) {
        item(key = "po:web") {
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Web Client PO Token",
                    subtitle = "Enable PO token generation for web-based player clients",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        }
        item(key = "po:tokens") {
            SettingsGroup("Generated Tokens") {
                TokenRow("PO Token (GVS)", poTokenGvs) {
                    clipboard.setText(AnnotatedString(it)); ctx.toast("Copiado")
                }
                SettingsDivider()
                TokenRow("PO Token (Player)", poTokenPlayer) {
                    clipboard.setText(AnnotatedString(it)); ctx.toast("Copiado")
                }
                SettingsDivider()
                TokenRow("Visitor Data", visitorData) {
                    clipboard.setText(AnnotatedString(it)); ctx.toast("Copiado")
                }
            }
        }
        item(key = "po:clients") {
            SettingsGroup("Supported Clients") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PoTokenClients.forEach { name ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(name, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
        item(key = "po:settings") {
            SettingsGroup("Token Settings") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = { ctx.toast("Los tokens se regeneran solos al reproducir la siguiente canción") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Re-generate") }
                }
            }
        }
    }
}

@Composable
private fun TokenRow(title: String, value: String?, onCopy: (String) -> Unit) {
    SettingsRow(
        title = title,
        subtitle = value ?: "—",
        trailing = {
            IconButton(onClick = { if (value != null) onCopy(value) }, enabled = value != null) {
                Icon(Icons.Rounded.ContentCopy, "Copiar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
