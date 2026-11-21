/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import com.dezzy.app.core.prefs.createPlatformSecureStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecureStorageTest {
    private val storage = createPlatformSecureStorage()

    @Test
    fun testSecureStorageOperations() = runTest {
        storage.set("test_key", "secret_value_123")
        assertEquals("secret_value_123", storage.get("test_key"))

        storage.remove("test_key")
        assertNull(storage.get("test_key"))

        storage.set("k1", "v1")
        storage.set("k2", "v2")
        storage.clear()
        assertNull(storage.get("k1"))
        assertNull(storage.get("k2"))
    }
}
