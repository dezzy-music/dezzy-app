/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.lossless.LosslessClient
import com.dezzy.app.feature.lossless.LosslessDefaultQualityKey
import com.dezzy.app.feature.lossless.LosslessEnabledKey
import com.dezzy.app.feature.lossless.LosslessExportToPublicKey
import com.dezzy.app.feature.lossless.LosslessQuality
import com.dezzy.app.feature.lossless.LosslessResolverUrlKey
import com.dezzy.app.feature.lossless.LosslessWifiOnlyKey
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Ajustes de las descargas lossless. La app no trae ninguna dirección: la pones tú, apuntando a un
 * resolver que controles (tu ARL de Deezer vive en ese servidor, nunca en la app).
 */
@Composable
internal fun LosslessSettingsScreen(ctx: ScreenContext) {
    val client: LosslessClient = koinInject()
    var enabled by rememberPreference(LosslessEnabledKey, false)
    var resolverUrl by rememberPreference(LosslessResolverUrlKey, "")
    var wifiOnly by rememberPreference(LosslessWifiOnlyKey, true)
    var exportToPublic by rememberPreference(LosslessExportToPublicKey, true)
    var qualityWire by rememberPreference(LosslessDefaultQualityKey, LosslessQuality.FLAC.wire)
    val quality = LosslessQuality.entries.firstOrNull { it.wire == qualityWire } ?: LosslessQuality.FLAC

    var editingUrl by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    SettingsPage(title = "Descarga lossless", onBack = { ctx.navigator.back() }) {
        item(key = "general") {
            SettingsGroup("General") {
                SettingsSwitchRow(
                    title = "Habilitar descargas lossless",
                    subtitle = "Buscar y descargar pistas en FLAC como archivos locales",
                    icon = Icons.Rounded.GraphicEq,
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Buscar y descargar",
                    subtitle = "Abre el buscador de canciones",
                    icon = Icons.Rounded.Search,
                    onClick = if (enabled) ({ ctx.navigator.push(Screen.Lossless) }) else null,
                )
                SettingsDivider()
                SettingsRow(
                    title = "Cola de descargas",
                    subtitle = "Ver el progreso de las descargas lossless",
                    icon = Icons.Rounded.Downloading,
                    onClick = if (enabled) ({ ctx.navigator.push(Screen.LosslessDownloads) }) else null,
                )
            }
        }
        item(key = "resolver") {
            SettingsGroup("Resolver") {
                SettingsRow(
                    title = "URL del resolver",
                    subtitle = resolverUrl.ifBlank { "Sin configurar" },
                    icon = Icons.Rounded.Link,
                    onClick = { editingUrl = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Probar conexión",
                    subtitle = when {
                        testing -> "Probando…"
                        else -> testResult ?: "Comprueba que el resolver responde"
                    },
                    icon = Icons.Rounded.NetworkCheck,
                    onClick = if (testing) null else ({
                        testing = true
                        testResult = null
                        ctx.scope.launch {
                            val result = client.health()
                            testResult = result.fold(
                                onSuccess = { "Conectado: $it" },
                                onFailure = { "Sin conexión: ${it.message ?: "error"}" },
                            )
                            testing = false
                        }
                    }),
                    trailing = if (testing) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                )
            }
        }
        item(key = "download") {
            SettingsGroup("Descarga") {
                SettingsRow(
                    title = "Proveedor",
                    subtitle = "Deezer",
                    icon = Icons.Rounded.Download,
                )
                SettingsDivider()
                SettingsRow(
                    title = "Qobuz",
                    subtitle = "No disponible",
                    icon = Icons.Rounded.Download,
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Calidad por defecto",
                    icon = Icons.Rounded.GraphicEq,
                    options = LosslessQuality.entries.toList(),
                    selected = quality,
                    label = { it.label },
                    onSelect = { qualityWire = it.wire },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Sólo por Wi‑Fi",
                    subtitle = "No descargar con datos móviles",
                    icon = Icons.Rounded.Wifi,
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Copiar a Música/Dezzy",
                    subtitle = "Guarda también una copia en la carpeta pública, visible en otras apps",
                    icon = Icons.Rounded.FolderOpen,
                    checked = exportToPublic,
                    onCheckedChange = { exportToPublic = it },
                )
            }
        }
        item(key = "info") {
            Text(
                "Dezzy no guarda cuentas de streaming ni trae ningún servidor propio. Apunta esta URL a un " +
                    "resolver que tú controles; devolverá el archivo lossless. Sin resolver no se puede " +
                    "descargar ni reproducir en FLAC.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }

    if (editingUrl) {
        var draft by remember { mutableStateOf(resolverUrl) }
        val valid = draft.isBlank() || draft.trim().startsWith("http")
        AlertDialog(
            onDismissRequest = { editingUrl = false },
            title = { Text("URL del resolver") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    isError = !valid,
                    placeholder = { Text("https://…") },
                    supportingText = if (valid) null else ({ Text("Debe empezar por http") }),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        resolverUrl = draft.trim().trimEnd('/')
                        testResult = null
                        editingUrl = false
                    },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingUrl = false }) { Text("Cancelar") } },
        )
    }
}
