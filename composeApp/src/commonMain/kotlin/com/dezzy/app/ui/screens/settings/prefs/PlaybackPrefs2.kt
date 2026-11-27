/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Reproductor y audio
val PlaybackClientKey = stringPreferencesKey("playbackClient")
val MeteredNetworkKey = booleanPreferencesKey("treatNetworkAsMetered")
/** Segundos escuchados para que una canción cuente en el historial. */
val HistoryDurationKey = intPreferencesKey("historyDurationSeconds")
val AudioOffloadKey = booleanPreferencesKey("audioOffload")
val ProgressiveSeekKey = booleanPreferencesKey("progressiveSeek")
val CastVideoKey = booleanPreferencesKey("castVideo")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val KeepCpuAwakeKey = booleanPreferencesKey("keepCpuAwake")
val ArtistSeparatorsKey = stringPreferencesKey("artistSeparators")
val ExternalDownloaderKey = booleanPreferencesKey("externalDownloader")
val ExternalDownloaderPackageKey = stringPreferencesKey("externalDownloaderPackage")

// Contenido
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val EnableLrcLibKey get() = LyricsProviderLrcLibKey
val EnableKuGouKey get() = LyricsProviderKuGouKey
val EnableBetterLyricsKey get() = LyricsProviderBetterLyricsKey
val EnableSimpMusicKey get() = LyricsProviderSimpMusicKey
val PreferredLyricsProviderKey get() = LyricsPreferredProviderKey
val RomanizeJapaneseKey get() = LyricsRomanizeJapaneseKey
val RomanizeKoreanKey get() = LyricsRomanizeKoreanKey
val PreloadLyricsKey get() = LyricsPreloadQueueKey
val PreloadLyricsCountKey get() = LyricsPreloadCountKey

// Privacidad
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")
