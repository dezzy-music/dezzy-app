/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DezzyPreferences(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val KEY_NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PERSISTENT_QUEUE = booleanPreferencesKey("persistent_queue")
    }

    val audioQuality: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_QUALITY] ?: "auto"
    }

    suspend fun setAudioQuality(quality: String) {
        dataStore.edit { prefs ->
            prefs[KEY_AUDIO_QUALITY] = quality
        }
    }

    val normalizeVolume: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NORMALIZE_VOLUME] ?: false
    }

    suspend fun setNormalizeVolume(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NORMALIZE_VOLUME] = enabled
        }
    }

    val crossfadeSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CROSSFADE_SECONDS] ?: 0
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_SECONDS] = seconds
        }
    }

    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "dark"
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }
}
