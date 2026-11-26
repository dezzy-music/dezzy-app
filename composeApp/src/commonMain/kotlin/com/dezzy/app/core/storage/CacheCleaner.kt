/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.storage

/** Una caché que se puede vaciar desde Ajustes. */
data class CacheEntry(val id: String, val title: String, val bytes: Long)

/** Lista y vacía las cachés de la plataforma. */
interface CacheCleaner {
    suspend fun entries(): List<CacheEntry>
    suspend fun clear(id: String)
}
