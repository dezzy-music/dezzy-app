/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.core.prefs.SecureStorage
import com.dezzy.app.feature.social.crypto.Bech32
import com.dezzy.app.feature.social.crypto.Bip39
import com.dezzy.app.feature.social.crypto.NostrCrypto
import com.dezzy.app.feature.social.crypto.hexToBytes
import com.dezzy.app.feature.social.crypto.toHex
import com.dezzy.app.feature.social.nostr.NostrEvent
import com.dezzy.app.feature.social.nostr.NostrKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Estado de la identidad social. [ready] = hay una cuenta en este dispositivo.
 * [loaded] = ya se intentó leer el almacén (hasta entonces [ready] puede ser falso sin ser cierto).
 */
data class P2PIdentityState(
    val ready: Boolean = false,
    val pubkeyHex: String? = null,
    val npub: String? = null,
    val loaded: Boolean = false,
)

@Serializable
private class StoredIdentity(
    val priv: String,
    val words: String? = null,
    val backedUp: Boolean = false,
)

/**
 * Identidad de la red social: un par de claves que vive sólo en este dispositivo.
 *
 * Las 12 palabras (o la clave) se guardan en el [SecureStorage] (clave `account_p2p_identity`), que
 * cada plataforma cifra a su manera. No hay correo ni contraseña ni servidor.
 */
class P2PIdentity(
    private val storage: SecureStorage,
    private val crypto: NostrCrypto,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(P2PIdentityState())

    val state: StateFlow<P2PIdentityState> = _state.asStateFlow()

    @Volatile
    private var keys: NostrKeys? = null

    @Volatile
    private var stored: StoredIdentity? = null

    /** Claves activas (para firmar y cifrar dentro del módulo). */
    internal fun currentKeys(): NostrKeys? = keys

    /** Carga la identidad guardada (idempotente). Nunca borra nada si el almacén falla de forma pasajera. */
    suspend fun load() {
        if (_state.value.loaded) return
        mutex.withLock {
            if (_state.value.loaded) return
            withContext(Dispatchers.Default) {
                var attempt = 0
                while (true) {
                    val present = runCatching { storage.contains(KEY) }.getOrDefault(false)
                    if (!present) break
                    val raw = runCatching { storage.get(KEY) }.getOrNull()
                    if (raw != null) {
                        val parsed = runCatching { json.decodeFromString(StoredIdentity.serializer(), raw) }.getOrNull()
                        if (parsed != null) activate(parsed)
                        break
                    }
                    if (attempt >= RETRY_WAITS_MS.size) {
                        // Existe pero no se puede leer ahora: se deja tal cual y se podrá reintentar.
                        return@withContext
                    }
                    delay(RETRY_WAITS_MS[attempt])
                    attempt++
                }
                _state.value = _state.value.copy(loaded = true)
            }
        }
    }

    private fun activate(s: StoredIdentity) {
        val k = NostrKeys(s.priv.hexToBytes())
        keys = k
        stored = s
        _state.value = P2PIdentityState(
            ready = true,
            pubkeyHex = k.publicKey,
            npub = Bech32.npub(k.publicKey),
            loaded = true,
        )
    }

    private suspend fun save(s: StoredIdentity) {
        storage.set(KEY, json.encodeToString(StoredIdentity.serializer(), s))
        activate(s)
    }

    /** Crea una cuenta nueva y devuelve sus 12 palabras de respaldo (la interfaz las muestra). */
    suspend fun create(): List<String> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val words = Bip39.generate(crypto)
            val priv = Bip39.nostrPrivateKey(crypto, words)
            save(StoredIdentity(priv = priv.toHex(), words = words.joinToString(" "), backedUp = false))
            words
        }
    }

    /** Recupera la cuenta con las 12 palabras. */
    suspend fun restore(words: List<String>): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val clean = Bip39.normalize(words.joinToString(" "))
                if (!Bip39.isValid(crypto, clean)) {
                    return@withContext Result.failure(IllegalArgumentException("Las palabras no son válidas. Revisa que sean 12 y estén bien escritas."))
                }
                val priv = Bip39.nostrPrivateKey(crypto, clean)
                save(StoredIdentity(priv = priv.toHex(), words = clean.joinToString(" "), backedUp = true))
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Recupera la cuenta con una clave `nsec1…` (o 64 caracteres hex). */
    suspend fun restoreNsec(nsec: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val text = nsec.trim()
                val key = Bech32.parsePrivateKey(text)
                    ?: text.lowercase().takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
                        ?.hexToBytes()?.takeIf { crypto.isValidPrivateKey(it) }
                    ?: return@withContext Result.failure(IllegalArgumentException("La clave no es válida."))
                save(StoredIdentity(priv = key.toHex(), words = null, backedUp = true))
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Las 12 palabras para mostrarlas otra vez (null si la cuenta se importó con nsec). */
    suspend fun words(): List<String>? {
        load()
        return stored?.words?.let { Bip39.normalize(it) }
    }

    /** La clave privada en formato `nsec1…`, para copia de seguridad. */
    suspend fun nsec(): String? {
        load()
        return keys?.let { Bech32.nsec(it.privateKey) }
    }

    suspend fun isBackedUp(): Boolean {
        load()
        return stored?.backedUp ?: false
    }

    suspend fun markBackedUp() {
        val current = stored ?: return
        mutex.withLock {
            val updated = StoredIdentity(current.priv, current.words, backedUp = true)
            storage.set(KEY, json.encodeToString(StoredIdentity.serializer(), updated))
            stored = updated
        }
    }

    /** Borra la cuenta de este dispositivo. Sin las 12 palabras no se puede recuperar. */
    suspend fun signOut() {
        mutex.withLock {
            storage.remove(KEY)
            keys?.privateKey?.fill(0)
            keys = null
            stored = null
            _state.value = P2PIdentityState(loaded = true)
        }
    }

    /** Firma un evento con la identidad. Falla (IllegalStateException) si no hay cuenta. */
    fun sign(kind: Int, content: String, tags: List<List<String>> = emptyList()): NostrEvent {
        val k = checkNotNull(keys) { "No hay identidad social en este dispositivo" }
        return NostrEvent.sign(k, kind, tags, content)
    }

    private companion object {
        const val KEY = "account_p2p_identity"
        val RETRY_WAITS_MS = longArrayOf(400, 800, 1200, 1600)
    }
}
