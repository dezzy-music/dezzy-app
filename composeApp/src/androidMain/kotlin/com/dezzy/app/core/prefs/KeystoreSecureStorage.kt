/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.Properties
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

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Copia atómica de las sesiones (claves «account_*», ya cifradas) fuera de las
    // SharedPreferences, en una carpeta que no entra en las copias de seguridad.
    private val backupFile by lazy { AtomicFile(File(appContext.noBackupFilesDir, BACKUP_FILE)) }
    private val backupLock = Any()

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        restoreFromBackup()
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
        withContext(Dispatchers.IO) {
            val encrypted = encrypt(value)
            if (isAccountKey(key)) restoreFromBackup()
            prefs.edit().putString(key, encrypted).commit()
            if (isAccountKey(key)) writeBackup()
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            if (isAccountKey(key)) restoreFromBackup()
            prefs.edit().remove(key).commit()
            if (isAccountKey(key)) writeBackup()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit().clear().commit()
            writeBackup()
        }
    }

    override suspend fun contains(key: String): Boolean = withContext(Dispatchers.IO) {
        restoreFromBackup()
        prefs.contains(key)
    }

    private fun isAccountKey(key: String) = key.startsWith(ACCOUNT_PREFIX)

    /** Vuelca a la copia todas las claves «account_*» de las prefs (valores cifrados tal cual). */
    private fun writeBackup() = synchronized(backupLock) {
        val props = Properties()
        prefs.all.forEach { (k, v) -> if (isAccountKey(k) && v is String) props.setProperty(k, v) }
        var out: FileOutputStream? = null
        try {
            out = backupFile.startWrite()
            props.store(out, null)
            backupFile.finishWrite(out)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir la copia de sesiones", e)
            out?.let { backupFile.failWrite(it) }
        }
    }

    /** Si falta en las prefs alguna clave «account_*» que sí está en la copia, la repone. Nunca borra nada. */
    private fun restoreFromBackup() = synchronized(backupLock) {
        val props = try {
            Properties().also { p -> backupFile.openRead().use { p.load(it) } }
        } catch (e: FileNotFoundException) {
            return@synchronized
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la copia de sesiones", e)
            return@synchronized
        }
        var editor: android.content.SharedPreferences.Editor? = null
        for (name in props.stringPropertyNames()) {
            if (!isAccountKey(name) || prefs.contains(name)) continue
            val value = props.getProperty(name) ?: continue
            if (editor == null) editor = prefs.edit()
            editor.putString(name, value)
        }
        editor?.commit()
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
        const val ACCOUNT_PREFIX = "account_"
        const val BACKUP_FILE = "session_backup.properties"
        const val ALIAS = "dezzy_secure_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 300L
    }
}
