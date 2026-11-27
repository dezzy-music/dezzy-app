/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.accounts.StoredAccount
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Cierra el inicio de sesión de YouTube Music con la cookie capturada en la WebView. */
class YouTubeLogin(
    private val accounts: AccountStore,
    private val client: YouTubeMusicClient,
    private val sync: YouTubeLibrarySync,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun complete(cookieHeader: String): Result<UserSession> {
        if (YouTubeAuth.sapisidOf(cookieHeader) == null) {
            return Result.failure(IllegalArgumentException("La cookie no tiene SAPISID: no has iniciado sesión"))
        }
        return try {
            val connectedAt = Clock.System.now().toEpochMilliseconds()
            accounts.save(StoredAccount(SessionProvider.YOUTUBE, cookieHeader, connectedAtEpochMs = connectedAt))
            // Si el perfil falla, la sesión queda guardada igualmente.
            client.getAccountInfo()?.let { info ->
                accounts.updateProfile(SessionProvider.YOUTUBE, info.name, info.handle, info.avatarUrl, info.dataSyncId)
            }
            // Todo de golpe al iniciar sesión: me gusta, listas, artistas y álbumes.
            scope.launch { runCatching { sync.syncAll() } }
            val stored = accounts.account(SessionProvider.YOUTUBE)
            Result.success(
                UserSession(
                    provider = SessionProvider.YOUTUBE,
                    name = stored?.name,
                    handle = stored?.handle,
                    avatarUrl = stored?.avatarUrl,
                    connectedAtEpochMs = connectedAt,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
