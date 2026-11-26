/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.core.storage.CacheCleaner
import com.dezzy.app.core.storage.CacheEntry
import com.dezzy.app.feature.downloads.DownloadConcurrencyKey
import com.dezzy.app.feature.downloads.DownloadLimitGbKey
import com.dezzy.app.feature.downloads.DownloadManager
import com.dezzy.app.feature.downloads.DownloadQualityKey
import com.dezzy.app.feature.downloads.DownloadStatus
import com.dezzy.app.feature.downloads.DownloadWifiOnlyKey
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.runtime.LaunchedEffect

/** Tamaño legible con 1 decimal: KB, MB o GB. */
private fun readableSize(bytes: Long): String {
    fun one(value: Double): String {
        val tenths = (value * 10).toLong()
        return "${tenths / 10},${tenths % 10}"
    }
    return when {
        bytes >= 1L shl 30 -> "${one(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        bytes >= 1L shl 20 -> "${one(bytes / (1024.0 * 1024.0))} MB"
        else -> "${one(bytes / 1024.0)} KB"
    }
}

@Composable
internal fun StorageSettingsScreen(ctx: ScreenContext) {
    val cleaner = koinInject<CacheCleaner>()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CacheEntry>?>(null) }
    var toClear by remember { mutableStateOf<CacheEntry?>(null) }

    val dlState by koinInject<DownloadManager>().state.collectAsState()
    var dlQuality by rememberPreference(DownloadQualityKey, "auto")
    var concurrency by rememberPreference(DownloadConcurrencyKey, 2)
    var limitGb by rememberPreference(DownloadLimitGbKey, 0)
    var wifiOnly by rememberPreference(DownloadWifiOnlyKey, false)

    LaunchedEffect(cleaner) { entries = cleaner.entries() }

    SettingsPage(title = "Descargas y almacenamiento", onBack = { ctx.navigator.back() }) {
        item(key = "downloads") {
            SettingsGroup("Descargas") {
                val doneItems = dlState.items.filter { it.status == DownloadStatus.DONE }
                SettingsRow(
                    title = "Canciones descargadas",
                    subtitle = "${doneItems.size} ${if (doneItems.size == 1) "canción" else "canciones"} • ${readableSize(dlState.usedBytes)}",
                    icon = Icons.Rounded.Download,
                    onClick = { ctx.navigator.push(Screen.Downloads) },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Calidad de descarga",
                    icon = Icons.Rounded.HighQuality,
                    options = listOf("auto", "high", "low"),
                    selected = dlQuality,
                    label = { qualityLabel(it) },
                    onSelect = { dlQuality = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Descargas simultáneas",
                    icon = Icons.Rounded.Download,
                    options = listOf(1, 2, 3, 4),
                    selected = concurrency,
                    label = { "$it" },
                    onSelect = { concurrency = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Límite de espacio",
                    icon = Icons.Rounded.Storage,
                    options = listOf(0, 5, 10, 20, 40, 100),
                    selected = limitGb,
                    label = { if (it == 0) "Sin límite" else "$it GB" },
                    onSelect = { limitGb = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Sólo con Wi‑Fi",
                    subtitle = "Las descargas esperan si estás con datos móviles",
                    icon = Icons.Rounded.Wifi,
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Descargas lossless",
                    icon = Icons.Rounded.HighQuality,
                    onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.LOSSLESS)) },
                )
            }
        }
        item(key = "cache") {
            val list = entries
            SettingsGroup("Caché") {
                if (list.isNullOrEmpty()) {
                    Text(
                        if (list == null) "Calculando…" else "No hay cachés que vaciar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                } else {
                    list.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        SettingsRow(
                            title = entry.title,
                            subtitle = readableSize(entry.bytes),
                            icon = Icons.Rounded.CleaningServices,
                            onClick = { toClear = entry },
                        )
                    }
                }
            }
        }
    }

    toClear?.let { entry ->
        AlertDialog(
            onDismissRequest = { toClear = null },
            title = { Text("¿Vaciar «${entry.title}»?") },
            text = { Text("Se liberarán ${readableSize(entry.bytes)}.") },
            confirmButton = {
                TextButton(onClick = {
                    toClear = null
                    scope.launch {
                        cleaner.clear(entry.id)
                        entries = cleaner.entries()
                    }
                }) { Text("Vaciar") }
            },
            dismissButton = { TextButton(onClick = { toClear = null }) { Text("Cancelar") } },
        )
    }
}
