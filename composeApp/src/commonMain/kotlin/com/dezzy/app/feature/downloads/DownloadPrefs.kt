/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.source.stream.AudioQuality

/** Descargas a la vez (1..4). */
val DownloadConcurrencyKey = intPreferencesKey("download_concurrency")

/** Límite de espacio de las descargas en GB (0 = sin límite). */
val DownloadLimitGbKey = intPreferencesKey("download_limit_gb")

/** Descargar sólo con Wi-Fi (o red no medida). */
val DownloadWifiOnlyKey = booleanPreferencesKey("download_wifi_only")

/** Calidad de las descargas: "auto" | "high" | "low". */
val DownloadQualityKey = stringPreferencesKey("download_quality")

/** Calidad de reproducción con datos móviles: "auto" | "high" | "low". */
val MobileDataQualityKey = stringPreferencesKey("mobile_data_quality")

const val DOWNLOAD_CONCURRENCY_DEFAULT = 2
const val DOWNLOAD_LIMIT_GB_DEFAULT = 0
const val DOWNLOAD_WIFI_ONLY_DEFAULT = false
const val DOWNLOAD_QUALITY_DEFAULT = "auto"
const val MOBILE_DATA_QUALITY_DEFAULT = "auto"

/** "auto" | "high" | "low" (cualquier otra cosa = auto) → [AudioQuality]. */
fun audioQualityOf(key: String): AudioQuality = when (key.lowercase()) {
    "high" -> AudioQuality.HIGH
    "low" -> AudioQuality.LOW
    else -> AudioQuality.AUTO
}
