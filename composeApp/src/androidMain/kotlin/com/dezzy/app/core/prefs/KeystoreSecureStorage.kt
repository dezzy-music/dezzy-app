/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secretos cifrados con AES-256-GCM y una clave del Android Keystore (API 23+).
 * Se guarda base64(iv ‖ texto cifrado) en SharedPreferences privadas.
 *
 * Un fallo del Keystore puede ser pasajero (arranque, desbloqueo): nunca se
 * borra un valor porque no se haya podido descifrar; se reintenta y, si sigue
 * fallando, se devuelve null dejando el dato intacto (trampa conocida del plan).
 */
class KeystoreSecureStorage(context: Context) : SecureStorage {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(key, null) ?: return@withContext null
        for (attempt in 0 until ATTEMPTS) {
            val result = runCatching { decrypt(stored) }
            result.onSuccess { return@withContext it }
            Log.w(TAG, "No se pudo descifrar «$key» (intento ${attempt + 1})", result.exceptionOrNull())
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        null
    }

    override suspend fun set(key: String, value: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(key, encrypt(value)).commit() }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { prefs.edit().remove(key).commit() }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val body = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body).decodeToString()
    }

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "DezzySecure"
        const val PREFS = "dezzy_secure_v1"
        const val ALIAS = "dezzy_secure_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 300L
    }
}
