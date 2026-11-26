/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.accounts

import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.UserSession
import com.dezzy.app.core.prefs.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Cuentas conectadas, guardadas como JSON en el almacén seguro (`account_youtube`, `account_spotify`).
 * Nunca se borra nada por un fallo de lectura: sólo [signOut] (acción del usuario) quita una cuenta.
 */
class AccountStore(
    private val storage: SecureStorage,
    val health: SessionHealth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val known = MutableStateFlow<Map<SessionProvider, StoredAccount>>(emptyMap())

    /** Sesiones conocidas; `isValid` es falso si el proveedor está en `needsRelogin`. */
    val sessions: StateFlow<Map<SessionProvider, UserSession>> =
        combine(known, health.needsRelogin) { accounts, relogin ->
            accounts.mapValues { (provider, a) ->
                UserSession(
                    provider = provider,
                    name = a.name,
                    handle = a.handle,
                    avatarUrl = a.avatarUrl,
                    connectedAtEpochMs = a.connectedAtEpochMs,
                    isValid = provider !in relogin,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            for (provider in SessionProvider.entries) account(provider)
        }
    }

    /** null = no hay cuenta o no se puede leer ahora (en ese caso no se borra nada). */
    suspend fun account(provider: SessionProvider): StoredAccount? {
        known.value[provider]?.let { return it }
        return mutex.withLock { known.value[provider] ?: readLocked(provider) }
    }

    suspend fun secret(provider: SessionProvider): String? = account(provider)?.secret

    suspend fun save(account: StoredAccount) {
        mutex.withLock { saveLocked(account) }
        health.onSuccess(account.provider)
    }

    suspend fun updateProfile(
        provider: SessionProvider,
        name: String?,
        handle: String?,
        avatarUrl: String?,
        dataSyncId: String? = null,
    ) {
        mutex.withLock {
            val current = known.value[provider] ?: readLocked(provider) ?: return
            saveLocked(
                current.copy(
                    name = name ?: current.name,
                    handle = handle ?: current.handle,
                    avatarUrl = avatarUrl ?: current.avatarUrl,
                    dataSyncId = dataSyncId ?: current.dataSyncId,
                )
            )
        }
    }

    /** Sólo por acción del usuario. */
    suspend fun signOut(provider: SessionProvider) {
        mutex.withLock {
            storage.remove(keyOf(provider))
            known.update { it - provider }
        }
        health.onSuccess(provider)
    }

    private suspend fun saveLocked(account: StoredAccount) {
        storage.set(keyOf(account.provider), json.encodeToString(StoredAccount.serializer(), account))
        known.update { it + (account.provider to account) }
    }

    /**
     * Lee la cuenta del almacén. Si existe pero no se puede descifrar ahora (Keystore pasajero)
     * reintenta con espera; si sigue sin poder devuelve null sin tocar nada.
     */
    private suspend fun readLocked(provider: SessionProvider): StoredAccount? {
        val key = keyOf(provider)
        if (!storage.contains(key)) return null
        var attempt = 0
        while (true) {
            val raw = storage.get(key)
            if (raw != null) {
                val parsed = runCatching { json.decodeFromString(StoredAccount.serializer(), raw) }.getOrNull()
                if (parsed != null) known.update { it + (provider to parsed) }
                return parsed
            }
            if (attempt >= RETRY_WAITS_MS.size) return null
            delay(RETRY_WAITS_MS[attempt])
            attempt++
        }
    }

    private fun keyOf(provider: SessionProvider) = when (provider) {
        SessionProvider.YOUTUBE -> "account_youtube"
        SessionProvider.SPOTIFY -> "account_spotify"
    }

    private companion object {
        val RETRY_WAITS_MS = longArrayOf(400, 800, 1200, 1600)
    }
}
