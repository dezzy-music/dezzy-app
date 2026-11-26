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
import androidx.datastore.preferences.core.longPreferencesKey
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
        val KEY_PAUSE_HISTORY = booleanPreferencesKey("pause_history")
        val KEY_CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val KEY_CONTENT_COUNTRY = stringPreferencesKey("content_country")
        val KEY_SPOTIFY_LIKED_AS_PLAYLIST = booleanPreferencesKey("spotify_liked_as_playlist")
        val KEY_SPOTIFY_AUTO_SYNC = booleanPreferencesKey("spotify_auto_sync")
        val KEY_SPOTIFY_LAST_SYNC = longPreferencesKey("spotify_last_sync")
        val KEY_YOUTUBE_LAST_LIKES_SYNC = longPreferencesKey("youtube_last_likes_sync")
        val KEY_WELCOME_LOGIN_SHOWN = booleanPreferencesKey("welcome_login_shown")
    }

    /** Idioma del contenido de YouTube Music (código `hl`). */
    val contentLanguage: Flow<String> = dataStore.data.map { prefs -> prefs[KEY_CONTENT_LANGUAGE] ?: "es" }

    suspend fun setContentLanguage(value: String) {
        dataStore.edit { prefs -> prefs[KEY_CONTENT_LANGUAGE] = value }
    }

    /** País del contenido de YouTube Music (código `gl`). */
    val contentCountry: Flow<String> = dataStore.data.map { prefs -> prefs[KEY_CONTENT_COUNTRY] ?: "US" }

    suspend fun setContentCountry(value: String) {
        dataStore.edit { prefs -> prefs[KEY_CONTENT_COUNTRY] = value }
    }

    /** Importar «Canciones que te gustan» de Spotify como una lista (en vez de como favoritos). */
    val spotifyLikedAsPlaylist: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_SPOTIFY_LIKED_AS_PLAYLIST] ?: true }

    suspend fun setSpotifyLikedAsPlaylist(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SPOTIFY_LIKED_AS_PLAYLIST] = value }
    }

    val spotifyAutoSync: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_SPOTIFY_AUTO_SYNC] ?: true }

    suspend fun setSpotifyAutoSync(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SPOTIFY_AUTO_SYNC] = value }
    }

    /** Última sincronización de Spotify (epoch ms; 0 = nunca). */
    val spotifyLastSync: Flow<Long> = dataStore.data.map { prefs -> prefs[KEY_SPOTIFY_LAST_SYNC] ?: 0L }

    suspend fun setSpotifyLastSync(value: Long) {
        dataStore.edit { prefs -> prefs[KEY_SPOTIFY_LAST_SYNC] = value }
    }

    /** Última sincronización de los «me gusta» de YouTube (epoch ms; 0 = nunca). */
    val youtubeLastLikesSync: Flow<Long> = dataStore.data.map { prefs -> prefs[KEY_YOUTUBE_LAST_LIKES_SYNC] ?: 0L }

    suspend fun setYoutubeLastLikesSync(value: Long) {
        dataStore.edit { prefs -> prefs[KEY_YOUTUBE_LAST_LIKES_SYNC] = value }
    }

    /** Si ya se mostró la invitación a iniciar sesión de la primera vez. */
    val welcomeLoginShown: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_WELCOME_LOGIN_SHOWN] ?: false }

    suspend fun setWelcomeLoginShown(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_WELCOME_LOGIN_SHOWN] = value }
    }

    /** Si está activo, lo que se escucha no se guarda en el historial. */
    val pauseHistory: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PAUSE_HISTORY] ?: false
    }

    suspend fun setPauseHistory(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PAUSE_HISTORY] = enabled }
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

    /** Lectura genérica para los ajustes de cada estilo de reproductor. */
    fun <T> value(key: Preferences.Key<T>, default: T): Flow<T> = dataStore.data.map { prefs -> prefs[key] ?: default }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }
}
