/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.downloads.LosslessRef

/** Prefijo del `videoId` de las pistas lossless: `dzr:<idDeezer>`. */
const val LOSSLESS_ID_PREFIX = "dzr:"
const val LOSSLESS_PROVIDER = "dzr"

enum class LosslessQuality(val wire: String, val label: String) {
    FLAC("flac", "FLAC"),
    MP3_320("mp3_320", "MP3 320");

    companion object {
        fun fromWire(value: String?): LosslessQuality = entries.firstOrNull { it.wire == value } ?: FLAC
    }
}

data class LosslessTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val cover: String?,
    val durationSec: Int?,
    val isrc: String?,
)

data class LosslessAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String?,
    val trackCount: Int?,
    val year: String?,
)

fun LosslessTrack.toTrack(): Track = Track(
    videoId = "$LOSSLESS_ID_PREFIX$id",
    title = title,
    artists = artist,
    album = album,
    thumbnail = cover,
    durationSeconds = durationSec?.toLong(),
)

fun LosslessTrack.ref(q: LosslessQuality): LosslessRef =
    LosslessRef(provider = LOSSLESS_PROVIDER, trackId = id, isrc = isrc, quality = q.wire)

/** `true` si el `videoId` es de una pista lossless (`dzr:`). */
fun String?.isLosslessId(): Boolean = this != null && startsWith(LOSSLESS_ID_PREFIX)

val LosslessResolverUrlKey: Preferences.Key<String> = stringPreferencesKey("lossless_resolver_url")
val LosslessEnabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("lossless_enabled")
val LosslessWifiOnlyKey: Preferences.Key<Boolean> = booleanPreferencesKey("lossless_wifi_only")
val LosslessExportToPublicKey: Preferences.Key<Boolean> = booleanPreferencesKey("lossless_export_public")
val LosslessDefaultQualityKey: Preferences.Key<String> = stringPreferencesKey("lossless_quality")
val LosslessRecentSearchesKey: Preferences.Key<String> = stringPreferencesKey("lossless_recent")

const val LOSSLESS_ENABLED_DEFAULT = false
const val LOSSLESS_WIFI_ONLY_DEFAULT = true
const val LOSSLESS_EXPORT_PUBLIC_DEFAULT = true
const val LOSSLESS_QUALITY_DEFAULT = "flac"

const val LOSSLESS_NO_RESOLVER_MESSAGE = "Configura tu resolver en Ajustes › Descargas lossless"
