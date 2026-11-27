/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Letras e IA
val LyricsTranslateProviderKey = stringPreferencesKey("lyricsTranslateProvider")
val LyricsAutoTranslateKey = booleanPreferencesKey("lyricsAutoTranslate")
val LyricsTranslateOnlyIfDifferentKey = booleanPreferencesKey("lyricsTranslateOnlyIfDifferent")
val LyricsSmartEmphasisKey = booleanPreferencesKey("lyricsSmartEmphasis")
val LyricsProviderLrcLibKey = booleanPreferencesKey("lyricsProviderLrcLib")
val LyricsProviderKuGouKey = booleanPreferencesKey("lyricsProviderKuGou")
val LyricsProviderBetterLyricsKey = booleanPreferencesKey("lyricsProviderBetterLyrics")
val LyricsProviderSimpMusicKey = booleanPreferencesKey("lyricsProviderSimpMusic")
val LyricsPreferredProviderKey = stringPreferencesKey("lyricsPreferredProvider")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsPreloadQueueKey = booleanPreferencesKey("lyricsPreloadQueue")
val LyricsPreloadCountKey = intPreferencesKey("lyricsPreloadCount")

// Almacenamiento
val SmartTrimCacheKey = booleanPreferencesKey("smartTrimCache")
val SaveDownloadsToMusicKey = booleanPreferencesKey("saveDownloadsToMusic")
val SongCacheMaxMbKey = intPreferencesKey("songCacheMaxMb")
val ImageCacheMaxMbKey = intPreferencesKey("imageCacheMaxMb")
val CanvasCacheMaxMbKey = intPreferencesKey("canvasCacheMaxMb")

// Cuentas: ajustes de YouTube Music e inicio de sesión avanzado
val YtUseAccountForBrowseKey = booleanPreferencesKey("ytUseAccountForBrowse")
val YtSyncLibraryKey = booleanPreferencesKey("ytSyncLibrary")
/** Ids de las listas de la cuenta que se sincronizan, separados por comas. Vacío: todas. */
val YtSyncPlaylistsKey = stringPreferencesKey("ytSyncPlaylists")
val YtAdvancedCookieKey = stringPreferencesKey("ytAdvancedCookie")
val YtAdvancedVisitorDataKey = stringPreferencesKey("ytAdvancedVisitorData")
val YtAdvancedDataSyncIdKey = stringPreferencesKey("ytAdvancedDataSyncId")
