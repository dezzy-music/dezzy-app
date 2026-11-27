/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dezzy.app.feature.player.SaveListenHistoryKey
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.prefs.DisableScreenshotKey
import com.dezzy.app.ui.screens.settings.prefs.PauseSearchHistoryKey
import kotlinx.coroutines.launch

@Composable
internal fun PrivacySettingsScreen(ctx: ScreenContext) {
    val pauseHistory by ctx.prefs.pauseHistory.collectAsState(false)
    var confirmClear by remember { mutableStateOf(false) }
    var saveListen by rememberPreference(SaveListenHistoryKey, true)
    var pauseSearch by rememberPreference(PauseSearchHistoryKey, false)
    var noScreenshots by rememberPreference(DisableScreenshotKey, false)

    SettingsPage(title = "Privacidad", onBack = { ctx.navigator.back() }) {
        item(key = "history") {
            SettingsGroup("Historial escuchado") {
                SettingsSwitchRow(
                    title = "Pausar historial escuchado",
                    icon = Icons.Rounded.History,
                    checked = pauseHistory,
                    onCheckedChange = { ctx.scope.launch { ctx.prefs.setPauseHistory(it) } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Eliminar historial escuchado",
                    icon = Icons.Rounded.DeleteSweep,
                    onClick = { confirmClear = true },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Guardar historial de escucha",
                    subtitle = "Escuchas y tiempo escuchado para las estadísticas",
                    icon = Icons.Rounded.Insights,
                    checked = saveListen,
                    onCheckedChange = { saveListen = it },
                )
            }
        }
        item(key = "search-history") {
            SettingsGroup("Historial de búsqueda") {
                SettingsSwitchRow(
                    title = "Pausar historial de búsqueda",
                    checked = pauseSearch,
                    onCheckedChange = { pauseSearch = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Eliminar historial de búsqueda",
                    onClick = { ctx.scope.launch { com.dezzy.app.ui.screens.search.clearSearchHistory(ctx.prefs); ctx.toast("Historial de búsqueda eliminado") } },
                )
            }
        }
        item(key = "misc") {
            SettingsGroup("Varios") {
                SettingsSwitchRow(
                    title = "Desactivar capturas de pantalla",
                    subtitle = "Cuando esta opción este activa, las capturas de pantalla y la vista en recientes se desactivarán.",
                    checked = noScreenshots,
                    onCheckedChange = { noScreenshots = it },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    ctx.scope.launch {
                        ctx.repository.clearHistory()
                        ctx.toast("Historial borrado")
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
