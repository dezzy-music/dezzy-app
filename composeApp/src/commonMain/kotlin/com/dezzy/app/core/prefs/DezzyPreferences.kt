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
        val KEY_AUTO_SKIP = booleanPreferencesKey("auto_skip")
        val KEY_INTERFACE_THEME = stringPreferencesKey("appInterfaceTheme")
        val KEY_GLASS_INTENSITY = stringPreferencesKey("liquidGlassIntensity")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_PINNED_PLAYLIST_1 = stringPreferencesKey("pinnedPlaylist1")
        val KEY_PINNED_PLAYLIST_2 = stringPreferencesKey("pinnedPlaylist2")
        val KEY_DJ_NAME = stringPreferencesKey("djName")
    }

    /** Estilo de interfaz: NORMAL, LIQUID_GLASS o NOTHING. */
    val interfaceTheme: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_INTERFACE_THEME] ?: "NORMAL"
    }

    suspend fun setInterfaceTheme(value: String) {
        dataStore.edit { prefs -> prefs[KEY_INTERFACE_THEME] = value }
    }

    /** Intensidad del cristal líquido: SUBTLE, PURE o INTENSE. */
    val glassIntensity: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GLASS_INTENSITY] ?: "PURE"
    }

    suspend fun setGlassIntensity(value: String) {
        dataStore.edit { prefs -> prefs[KEY_GLASS_INTENSITY] = value }
    }

    /** Negro puro (OLED) en modo oscuro. */
    val pureBlack: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PURE_BLACK] ?: false
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PURE_BLACK] = enabled }
    }

    /** Colores dinámicos del sistema (Material You). */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    /** Guardar la cola al cerrar y recuperarla al abrir (activado por defecto). */
    val persistentQueue: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PERSISTENT_QUEUE] ?: true
    }

    suspend fun setPersistentQueue(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PERSISTENT_QUEUE] = enabled }
    }

    /** Si una canción no se puede reproducir, pasar a la siguiente (activado por defecto). */
    val autoSkip: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SKIP] ?: true
    }

    suspend fun setAutoSkip(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_SKIP] = enabled }
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

    /** Listas fijadas en los huecos 3 y 4 de los accesos rápidos del Inicio. */
    fun pinnedPlaylist(slot: Int): Flow<String?> = dataStore.data.map { prefs ->
        prefs[if (slot == 1) KEY_PINNED_PLAYLIST_1 else KEY_PINNED_PLAYLIST_2]
    }

    suspend fun setPinnedPlaylist(slot: Int, playlistId: String?) {
        val key = if (slot == 1) KEY_PINNED_PLAYLIST_1 else KEY_PINNED_PLAYLIST_2
        dataStore.edit { prefs -> if (playlistId == null) prefs.remove(key) else prefs[key] = playlistId }
    }

    /** Nombre propio del DJ («DJ <nombre>»); vacío = «DJ Dezzy». */
    val djName: Flow<String> = dataStore.data.map { prefs -> prefs[KEY_DJ_NAME] ?: "" }
}
