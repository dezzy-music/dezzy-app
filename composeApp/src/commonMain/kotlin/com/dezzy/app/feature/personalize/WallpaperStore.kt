/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart

/** Un fondo: imagen o vídeo, por su URI (`file://…`, `content://…` o `https://…`). */
@Immutable
data class WallpaperSlot(val uri: String, val isVideo: Boolean)

/**
 * Fondo de la app: uno para vertical y otro para horizontal (al girar el dispositivo), con
 * [dimPercent] de oscurecido (0..80) para que el contenido se lea.
 */
@Immutable
data class WallpaperState(
    val portrait: WallpaperSlot? = null,
    val landscape: WallpaperSlot? = null,
    val dimPercent: Int = DEFAULT_DIM,
) {
    fun slot(landscape: Boolean): WallpaperSlot? = if (landscape) this.landscape else portrait
    val isEmpty: Boolean get() = portrait == null && landscape == null

    companion object {
        const val DEFAULT_DIM = 35
        const val MAX_DIM = 80
    }
}

/** Preferencias del fondo de la app. Opus pone `AppWallpaper(...)` detrás del contenido y lo demás sale de aquí. */
class WallpaperStore(
    private val prefs: DezzyPreferences,
    private val files: WallpaperFiles = wallpaperFiles(),
) {
    private companion object {
        val PortraitUri = stringPreferencesKey("wallpaperPortraitUri")
        val PortraitVideo = booleanPreferencesKey("wallpaperPortraitVideo")
        val LandscapeUri = stringPreferencesKey("wallpaperLandscapeUri")
        val LandscapeVideo = booleanPreferencesKey("wallpaperLandscapeVideo")
        val Dim = intPreferencesKey("wallpaperDim")
        // Interruptores de Ajustes › Apariencia › Fondo de la app («Fondo con imagen» / «Fondo con video»).
        val ImageEnabled = booleanPreferencesKey("wallpaper_image_enabled")
        val VideoEnabled = booleanPreferencesKey("wallpaper_video_enabled")
        val FlagsMigrated = booleanPreferencesKey("wallpaper_flags_migrated")
    }

    val state: Flow<WallpaperState> = combine(
        prefs.value(PortraitUri, ""),
        prefs.value(PortraitVideo, false),
        prefs.value(LandscapeUri, ""),
        prefs.value(LandscapeVideo, false),
        prefs.value(Dim, WallpaperState.DEFAULT_DIM),
    ) { pUri, pVideo, lUri, lVideo, dim ->
        WallpaperState(
            portrait = pUri.takeIf { it.isNotEmpty() }?.let { WallpaperSlot(it, pVideo) },
            landscape = lUri.takeIf { it.isNotEmpty() }?.let { WallpaperSlot(it, lVideo) },
            dimPercent = dim.coerceIn(0, WallpaperState.MAX_DIM),
        )
    }.distinctUntilChanged()

    suspend fun current(): WallpaperState = state.first()

    /** El fondo que se pinta: sin las imágenes o vídeos cuyo interruptor está apagado. */
    val displayed: Flow<WallpaperState> = combine(
        state,
        prefs.value(ImageEnabled, false),
        prefs.value(VideoEnabled, false),
    ) { st, image, video ->
        fun WallpaperSlot?.allowed() = this?.takeIf { if (it.isVideo) video else image }
        st.copy(portrait = st.portrait.allowed(), landscape = st.landscape.allowed())
    }.distinctUntilChanged().onStart { migrateFlags() }

    /** Una sola vez: un fondo elegido antes de existir los interruptores enciende el suyo. */
    private suspend fun migrateFlags() {
        if (prefs.value(FlagsMigrated, false).first()) return
        val st = state.first()
        listOfNotNull(st.portrait, st.landscape).forEach { prefs.set(if (it.isVideo) VideoEnabled else ImageEnabled, true) }
        prefs.set(FlagsMigrated, true)
    }

    /** Pone [uri] como fondo de esa orientación; si el anterior lo había bajado Dezzy, lo borra. */
    suspend fun set(landscape: Boolean, uri: String, isVideo: Boolean) {
        val old = current().slot(landscape)
        prefs.set(if (landscape) LandscapeUri else PortraitUri, uri)
        prefs.set(if (landscape) LandscapeVideo else PortraitVideo, isVideo)
        // Elegir un fondo enciende su interruptor, para que se vea sin tener que ir a Ajustes.
        prefs.set(if (isVideo) VideoEnabled else ImageEnabled, true)
        if (old != null && old.uri != uri) files.deleteOwnFile(old.uri)
    }

    suspend fun clear(landscape: Boolean) {
        val old = current().slot(landscape)
        prefs.set(if (landscape) LandscapeUri else PortraitUri, "")
        prefs.set(if (landscape) LandscapeVideo else PortraitVideo, false)
        if (old != null) files.deleteOwnFile(old.uri)
    }

    suspend fun setDim(percent: Int) {
        prefs.set(Dim, percent.coerceIn(0, WallpaperState.MAX_DIM))
    }
}
