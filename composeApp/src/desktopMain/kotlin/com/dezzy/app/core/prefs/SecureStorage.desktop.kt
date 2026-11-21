/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.prefs

class DesktopSecureStorage : SecureStorage {
    private val memoryStore = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = memoryStore[key]

    override suspend fun set(key: String, value: String) {
        memoryStore[key] = value
    }

    override suspend fun remove(key: String) {
        memoryStore.remove(key)
    }

    override suspend fun clear() {
        memoryStore.clear()
    }
}

actual fun createPlatformSecureStorage(): SecureStorage = DesktopSecureStorage()
