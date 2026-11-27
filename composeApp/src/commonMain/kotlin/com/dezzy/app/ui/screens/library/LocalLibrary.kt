/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.library

import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.model.MusicItem
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Artistas a los que te suscribiste en este dispositivo (JSON de MusicItem, el más reciente primero). */
val SubscribedArtistsKey = stringPreferencesKey("localSubscribedArtists")

/** Álbumes guardados en la biblioteca con el ♥ (JSON de MusicItem, el más reciente primero). */
val SavedAlbumsKey = stringPreferencesKey("localSavedAlbums")

private val json = Json { ignoreUnknownKeys = true }
private val serializer = ListSerializer(MusicItem.serializer())

fun parseLocalItems(raw: String): List<MusicItem> =
    if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

fun localItems(prefs: DezzyPreferences, key: androidx.datastore.preferences.core.Preferences.Key<String>): Flow<List<MusicItem>> =
    prefs.value(key, "").map { parseLocalItems(it) }

/** Añade o quita [item] de la lista local; devuelve true si ha quedado guardado. */
suspend fun toggleLocalItem(
    prefs: DezzyPreferences,
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    item: MusicItem,
): Boolean {
    val current = parseLocalItems(prefs.value(key, "").first())
    val present = current.any { it.id == item.id }
    val next = if (present) current.filterNot { it.id == item.id } else listOf(item) + current
    prefs.set(key, json.encodeToString(serializer, next))
    return !present
}

/** Añade al final de la lista local los [items] que aún no estaban; devuelve cuántos se añadieron. */
suspend fun mergeLocalItems(
    prefs: DezzyPreferences,
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    items: List<MusicItem>,
): Int {
    val current = parseLocalItems(prefs.value(key, "").first())
    val known = current.map { it.id }.toHashSet()
    val fresh = items.filter { known.add(it.id) }
    if (fresh.isNotEmpty()) prefs.set(key, json.encodeToString(serializer, current + fresh))
    return fresh.size
}

/** Orden de la rejilla de listas en Biblioteca (0 personalizado, 1 nombre, 2 más canciones). */
val LibrarySortKey = androidx.datastore.preferences.core.intPreferencesKey("librarySort")

/** Vista de Biblioteca: true rejilla, false lista. */
val LibraryGridViewKey = androidx.datastore.preferences.core.booleanPreferencesKey("libraryGridView")
