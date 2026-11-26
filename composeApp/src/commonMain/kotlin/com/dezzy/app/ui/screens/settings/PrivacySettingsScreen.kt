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
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch

@Composable
internal fun PrivacySettingsScreen(ctx: ScreenContext) {
    val pauseHistory by ctx.prefs.pauseHistory.collectAsState(false)
    var confirmClear by remember { mutableStateOf(false) }

    SettingsPage(title = "Privacidad", onBack = { ctx.navigator.back() }) {
        item(key = "history") {
            SettingsGroup("Historial") {
                SettingsSwitchRow(
                    title = "Pausar el historial",
                    subtitle = "Lo que escuches no se guarda en el historial",
                    icon = Icons.Rounded.History,
                    checked = pauseHistory,
                    onCheckedChange = { ctx.scope.launch { ctx.prefs.setPauseHistory(it) } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Borrar el historial",
                    icon = Icons.Rounded.DeleteSweep,
                    onClick = { confirmClear = true },
                )
            }
        }
        item(key = "stats") {
            SettingsGroup("Datos") {
                SettingsPendingRow("Escuchas para estadísticas", "10", ctx, icon = Icons.Rounded.Insights)
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
