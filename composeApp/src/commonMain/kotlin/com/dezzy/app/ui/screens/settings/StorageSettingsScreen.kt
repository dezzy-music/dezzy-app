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
import com.dezzy.app.ui.screens.settings.prefs.CanvasCacheMaxMbKey
import com.dezzy.app.ui.screens.settings.prefs.ImageCacheMaxMbKey
import com.dezzy.app.ui.screens.settings.prefs.SaveDownloadsToMusicKey
import com.dezzy.app.ui.screens.settings.prefs.SmartTrimCacheKey
import com.dezzy.app.ui.screens.settings.prefs.SongCacheMaxMbKey
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

    var smartTrim by rememberPreference(SmartTrimCacheKey, false)
    var saveToMusic by rememberPreference(SaveDownloadsToMusicKey, false)
    var songCacheMb by rememberPreference(SongCacheMaxMbKey, 0)
    var imageCacheMb by rememberPreference(ImageCacheMaxMbKey, 0)
    var canvasCacheMb by rememberPreference(CanvasCacheMaxMbKey, 0)
    val downloads = koinInject<DownloadManager>()
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    val cacheSizes = listOf(0, 128, 256, 512, 1024, 2048)
    val cacheLabel: (Int) -> String = { if (it == 0) "Ilimitado" else if (it >= 1024) "${it / 1024} GB" else "$it MB" }

    SettingsPage(title = "Almacenamiento", onBack = { ctx.navigator.back() }) {
        item(key = "smart-trim") {
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Cortador inteligente",
                    subtitle = "El cortador inteligente administra dinámicamente las canciones e imágenes en cache, eliminando viejas entradas y guardando espacio en disco.",
                    icon = Icons.Rounded.CleaningServices,
                    checked = smartTrim,
                    onCheckedChange = { smartTrim = it },
                )
            }
        }
        item(key = "downloads") {
            val doneItems = dlState.items.filter { it.status == DownloadStatus.DONE }
            SettingsGroup("Canciones descargadas · ${readableSize(dlState.usedBytes)} usados") {
                SettingsRow(
                    title = "Canciones descargadas",
                    subtitle = "${doneItems.size} ${if (doneItems.size == 1) "canción" else "canciones"} • ${readableSize(dlState.usedBytes)}",
                    icon = Icons.Rounded.Download,
                    onClick = { ctx.navigator.push(Screen.Downloads) },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Simultaneous downloads",
                    icon = Icons.Rounded.Download,
                    options = listOf(1, 2, 3, 4),
                    selected = concurrency,
                    label = { "$it" },
                    onSelect = { concurrency = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Save downloads to Music folder",
                    subtitle = "After downloading, copy the file to Music/Dezzy on your device storage",
                    icon = Icons.Rounded.Storage,
                    checked = saveToMusic,
                    onCheckedChange = { saveToMusic = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Eliminar todas las descargas",
                    icon = Icons.Rounded.CleaningServices,
                    onClick = { confirmDeleteDownloads = true },
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
        item(key = "song-cache") {
            SettingsGroup("Cache de canción · 0 KB usados") {
                SettingsChoiceRow(
                    title = "Tamaño máximo de cache",
                    icon = Icons.Rounded.Storage,
                    options = cacheSizes,
                    selected = songCacheMb,
                    label = cacheLabel,
                    onSelect = { songCacheMb = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Eliminar cache de canciones",
                    icon = Icons.Rounded.CleaningServices,
                    onClick = { ctx.toast("La caché de canciones está vacía") },
                )
            }
        }
        item(key = "image-cache") {
            val bytes = entries?.firstOrNull { it.id == "images" }?.bytes ?: 0L
            SettingsGroup("Cache de imagen · ${readableSize(bytes)} usados") {
                SettingsChoiceRow(
                    title = "Tamaño máximo de cache",
                    icon = Icons.Rounded.Storage,
                    options = cacheSizes,
                    selected = imageCacheMb,
                    label = cacheLabel,
                    onSelect = { imageCacheMb = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Eliminar cache de imágenes",
                    icon = Icons.Rounded.CleaningServices,
                    onClick = { scope.launch { cleaner.clear("images"); entries = cleaner.entries() } },
                )
            }
        }
        item(key = "canvas-cache") {
            val bytes = entries?.firstOrNull { it.id == "video" }?.bytes ?: 0L
            SettingsGroup("Cache del Canvas · ${readableSize(bytes)} usados") {
                SettingsChoiceRow(
                    title = "Tamaño máximo de cache",
                    icon = Icons.Rounded.Storage,
                    options = cacheSizes,
                    selected = canvasCacheMb,
                    label = cacheLabel,
                    onSelect = { canvasCacheMb = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Limpiar el cache del canvas",
                    icon = Icons.Rounded.CleaningServices,
                    onClick = { scope.launch { cleaner.clear("video"); entries = cleaner.entries() } },
                )
            }
        }
        item(key = "cache") {
            val list = entries
            SettingsGroup("Otras cachés") {
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

    if (confirmDeleteDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDownloads = false },
            title = { Text("Eliminar todas las descargas") },
            text = { Text("Se borrarán todas las canciones descargadas de este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteDownloads = false
                    dlState.items.map { it.id }.forEach { downloads.remove(it) }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDownloads = false }) { Text("Cancelar") } },
        )
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
