/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.prefs

/** Almacén de secretos (cookies de sesión, claves BYOK). Cada plataforma lo cifra a su manera. */
interface SecureStorage {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

/** Sólo para pruebas y para el objetivo JVM de escritorio, que no se distribuye. */
class InMemorySecureStorage : SecureStorage {
    private val map = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = map[key]
    override suspend fun set(key: String, value: String) { map[key] = value }
    override suspend fun remove(key: String) { map.remove(key) }
    override suspend fun clear() { map.clear() }
}
