/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.search

import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.ui.screens.settings.prefs.PauseSearchHistoryKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Historial de búsqueda: JSON con las últimas consultas (la más reciente primero). */
val SearchHistoryKey = stringPreferencesKey("searchHistory")

private const val MaxSearchHistory = 30

fun parseSearchHistory(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { it.jsonPrimitive.contentOrNull }
    }.getOrDefault(emptyList())
}

private fun encode(list: List<String>): String = JsonArray(list.map { JsonPrimitive(it) }).toString()

/** Guarda una consulta al principio (sin duplicados, máx. 30). No hace nada si el historial está pausado. */
suspend fun addSearchHistory(prefs: DezzyPreferences, query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    if (prefs.value(PauseSearchHistoryKey, false).first()) return
    val current = parseSearchHistory(prefs.value(SearchHistoryKey, "").first())
    val next = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MaxSearchHistory)
    prefs.set(SearchHistoryKey, encode(next))
}

/** Borra una consulta concreta del historial. */
suspend fun removeSearchHistory(prefs: DezzyPreferences, query: String) {
    val current = parseSearchHistory(prefs.value(SearchHistoryKey, "").first())
    prefs.set(SearchHistoryKey, encode(current.filterNot { it == query }))
}

/** Vacía todo el historial de búsqueda (para Privacidad). */
suspend fun clearSearchHistory(prefs: DezzyPreferences) {
    prefs.set(SearchHistoryKey, "[]")
}
