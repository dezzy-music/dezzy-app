/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dezzy.app.ui.player.kit.ShowCodecOnPlayerKey
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext

internal val DebugUiKey = booleanPreferencesKey("debugUi")
internal val NerdStatsKey = booleanPreferencesKey("nerdStats")

@Composable
internal fun ExperimentsSettingsScreen(ctx: ScreenContext) {
    var debugUi by rememberPreference(DebugUiKey, false)
    var nerdStats by rememberPreference(NerdStatsKey, false)
    var showCodec by rememberPreference(ShowCodecOnPlayerKey, false)

    var logTab by remember { mutableStateOf(0) } // 0 = Discord, 1 = Todo
    var filter by remember { mutableStateOf("") }
    // Aún no hay registrador de eventos en la app: la lista queda vacía.
    val logs: List<String> = emptyList()
    val shown = logs.filter { filter.isBlank() || it.contains(filter, ignoreCase = true) }

    val state by ctx.player.state.collectAsState()
    val track = state.queue.current?.track

    SettingsPage(title = "Ajustes Experimentales", onBack = { ctx.navigator.back() }) {
        item(key = "exp:features") {
            SettingsGroup("Funciones experimentales") {
                SettingsSwitchRow(
                    title = "Mostrar interfaz de usuario de depuración",
                    subtitle = "Activar líneas de depuración…",
                    checked = debugUi,
                    onCheckedChange = { debugUi = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Mostrar estadísticas para nerds",
                    subtitle = "Mostrar estadísticas en tiempo real (codec, bitrate, buffer)",
                    checked = nerdStats,
                    onCheckedChange = { nerdStats = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Mostrar el codec en el reproductor",
                    checked = showCodec,
                    onCheckedChange = { showCodec = it },
                )
            }
        }
        item(key = "exp:discord") {
            SettingsGroup("Integración con Discord") {
                SettingsRow("Gestor de presencia: detenido")
                SettingsDivider()
                SettingsRow("Último inicio", "—")
                SettingsDivider()
                SettingsRow("Último fin", "—")
            }
        }
        item(key = "exp:logs") {
            SettingsGroup("Registros de depuración") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Discord", "Todo").forEachIndexed { i, name ->
                            Surface(
                                onClick = { logTab = i },
                                shape = RoundedCornerShape(50),
                                color = if (logTab == i) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) { Text(name, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
                        }
                    }
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text("Filtrar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (shown.isEmpty()) {
                        Text("Sin registros", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                            modifier = Modifier.heightIn(min = 48.dp))
                    } else {
                        shown.forEach { Text(it, fontSize = 12.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { filter = "" }) { Text("Limpiar") }
                        Button(onClick = { ctx.toast("No hay registros que compartir") }) { Text("Compartir") }
                    }
                }
            }
        }
        item(key = "exp:nerds") {
            SettingsGroup("Estadísticas para Nerds") {
                NerdRow("Pista", track?.let { "${it.title} · ${it.artists}" })
                SettingsDivider()
                NerdRow("Codec", null)
                SettingsDivider()
                NerdRow("Tasa de bits", null)
                SettingsDivider()
                NerdRow("Tasa de muestreo", null)
                SettingsDivider()
                NerdRow("Duración del contenido", state.durationMs.takeIf { it > 0 }?.let { formatMs(it) })
                SettingsDivider()
                NerdRow("Salud del búfer", null)
                SettingsDivider()
                NerdRow("Estado", when {
                    track == null -> null
                    state.error != null -> "Error"
                    state.isBuffering -> "Cargando"
                    state.isPlaying -> "Reproduciendo"
                    else -> "En pausa"
                })
                SettingsDivider()
                NerdRow("Velocidad", if (track != null) "${state.speed}x" else null)
                SettingsDivider()
                NerdRow("Identificador de medios", track?.videoId)
            }
        }
    }
}

@Composable
private fun NerdRow(title: String, value: String?) {
    SettingsRow(title = title, subtitle = value ?: "—")
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
