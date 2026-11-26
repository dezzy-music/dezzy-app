/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.crypto.sha1Hex
import com.dezzy.app.core.model.SessionProvider
import kotlin.time.Clock

data class AccountInfo(val name: String?, val handle: String?, val avatarUrl: String?, val dataSyncId: String?)

/**
 * Cabeceras de la sesión de YouTube Music. Sólo para el catálogo y la biblioteca
 * (YouTubeMusicClient): las peticiones de audio nunca llevan cookie.
 */
class YouTubeAuth(
    private val accounts: AccountStore,
    private val nowSeconds: () -> Long = { Clock.System.now().toEpochMilliseconds() / 1000 },
) {
    /** Vacío sin sesión: Cookie, Authorization (SAPISIDHASH), X-Goog-AuthUser y X-Origin. */
    suspend fun headers(): Map<String, String> {
        val cookie = accounts.secret(SessionProvider.YOUTUBE) ?: return emptyMap()
        val sapisid = sapisidOf(cookie) ?: return emptyMap()
        val ts = nowSeconds()
        val hash = sha1Hex("$ts $sapisid $ORIGIN")
        return mapOf(
            "Cookie" to cookie,
            "Authorization" to "SAPISIDHASH ${ts}_$hash",
            "X-Goog-AuthUser" to "0",
            "X-Origin" to ORIGIN,
        )
    }

    /** Id de la cuenta de marca (parte antes de `||` del dataSyncId), o null si es la cuenta personal. */
    suspend fun onBehalfOfUser(): String? {
        val id = accounts.account(SessionProvider.YOUTUBE)?.dataSyncId ?: return null
        val parts = id.split("||", limit = 2)
        if (parts.size < 2 || parts[1].isBlank()) return null
        return parts[0].ifBlank { null }
    }

    fun reportRejected() = accounts.health.onRejected(SessionProvider.YOUTUBE)

    fun reportTransient() = accounts.health.onTransientFailure(SessionProvider.YOUTUBE)

    fun reportSuccess() =accounts.health.onSuccess(SessionProvider.YOUTUBE)

    companion object {
        private const val ORIGIN = "https://music.youtube.com"

        /** `SAPISID`, o `__Secure-3PAPISID` si no hay. */
        fun sapisidOf(cookieHeader: String): String? {
            val map = cookieHeader.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            return (map["SAPISID"] ?: map["__Secure-3PAPISID"])?.takeIf { it.isNotEmpty() }
        }
    }
}
