/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.storage

/** En escritorio todavía no hay cachés que vaciar. */
class DesktopCacheCleaner : CacheCleaner {
    override suspend fun entries(): List<CacheEntry> = emptyList()
    override suspend fun clear(id: String) = Unit
}
