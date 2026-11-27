/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Cambios propios sobre una canción (no destructivos: el original no se toca). Null = el del original. */
@Serializable
data class SongMod(
    val title: String? = null,
    /** URL (`https://…`) o archivo propio (`file://…`) de la carátula. */
    val cover: String? = null,
) {
    val isEmpty: Boolean get() = title.isNullOrBlank() && cover.isNullOrBlank()
}

/** Clave estable de una canción para sus mods: el videoId, o título+artistas si no tiene. */
fun Track.modKey(): String = videoId ?: "t:$title|$artists"

/**
 * Almacén de los mods de canción («Personalizar canción»), en las preferencias de la app como un
 * mapa JSON. Para ver los cambios en reproductor y listas, pasa cada pista por [applyTo] / [withMod].
 */
class SongMods(
    private val prefs: DezzyPreferences,
    private val files: WallpaperFiles = wallpaperFiles(),
) {
    private companion object {
        val Key = stringPreferencesKey("songMods")
        val serializer = MapSerializer(String.serializer(), SongMod.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }

    /** Todos los mods por clave de canción ([modKey]). */
    val all: Flow<Map<String, SongMod>> = prefs.value(Key, "").map { decode(it) }.distinctUntilChanged()

    private fun decode(raw: String): Map<String, SongMod> =
        if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())

    suspend fun get(track: Track): SongMod? = all.first()[track.modKey()]

    /** Guarda el mod de la canción; vacío o null lo quita (revertir al original). */
    suspend fun save(track: Track, mod: SongMod?) {
        val map = all.first().toMutableMap()
        val key = track.modKey()
        val old = map[key]
        if (mod == null || mod.isEmpty) map.remove(key) else map[key] = mod
        prefs.set(Key, json.encodeToString(serializer, map))
        val oldCover = old?.cover
        if (oldCover != null && oldCover != mod?.cover) files.deleteOwnFile(oldCover)
    }

    suspend fun reset(track: Track) = save(track, null)

    /** La canción con sus cambios aplicados (título y carátula); sin mod, la misma. */
    fun applyTo(track: Track, mods: Map<String, SongMod>): Track = track.withMod(mods[track.modKey()])
}

/** Aplica un mod a la pista: título y carátula propios si los hay. */
fun Track.withMod(mod: SongMod?): Track {
    if (mod == null || mod.isEmpty) return this
    return copy(
        title = mod.title?.takeIf { it.isNotBlank() } ?: title,
        thumbnail = mod.cover?.takeIf { it.isNotBlank() } ?: thumbnail,
    )
}

/** Atajo para listas: `tracks.map { it.withMods(mods) }`. */
fun Track.withMods(mods: Map<String, SongMod>): Track = withMod(mods[modKey()])
