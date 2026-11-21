/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.prefs

import android.content.Context
import android.content.SharedPreferences

class AndroidSecureStorage(context: Context) : SecureStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences("dezzy_secure_prefs", Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? = prefs.getString(key, null)

    override suspend fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override suspend fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private var instance: AndroidSecureStorage? = null
        private val fallbackStorage = object : SecureStorage {
            private val map = mutableMapOf<String, String>()
            override suspend fun get(key: String): String? = map[key]
            override suspend fun set(key: String, value: String) { map[key] = value }
            override suspend fun remove(key: String) { map.remove(key) }
            override suspend fun clear() { map.clear() }
        }

        fun init(context: Context): AndroidSecureStorage {
            val s = AndroidSecureStorage(context.applicationContext)
            instance = s
            return s
        }

        fun getInstance(): SecureStorage {
            return instance ?: fallbackStorage
        }
    }
}

actual fun createPlatformSecureStorage(): SecureStorage = AndroidSecureStorage.getInstance()
