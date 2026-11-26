/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.accounts.SessionHealth
import com.dezzy.app.core.accounts.StoredAccount
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.prefs.InMemorySecureStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeAuthTest {
    private val cookie = "VISITOR_INFO1_LIVE=abc123; SAPISID=FakeSapisid/ABCdef123; HSID=zzz"

    private fun auth(): Pair<YouTubeAuth, AccountStore> {
        val accounts = AccountStore(InMemorySecureStorage(), SessionHealth(now = { 0L }))
        return YouTubeAuth(accounts, nowSeconds = { 1_700_000_000L }) to accounts
    }

    @Test
    fun withoutSessionHeadersAreEmpty() = runTest {
        assertTrue(auth().first.headers().isEmpty())
    }

    @Test
    fun authorizationHasSapisidHashFormat() = runTest {
        val (auth, accounts) = auth()
        accounts.save(StoredAccount(SessionProvider.YOUTUBE, cookie, connectedAtEpochMs = 1L))
        val headers = auth.headers()
        assertEquals(cookie, headers["Cookie"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("https://music.youtube.com", headers["X-Origin"])
        assertTrue(Regex("^SAPISIDHASH 1700000000_[0-9a-f]{40}$").matches(headers.getValue("Authorization")))
    }

    @Test
    fun fallsBackToSecure3PAPISID() {
        assertEquals("tres", YouTubeAuth.sapisidOf("a=1; __Secure-3PAPISID=tres"))
        assertNull(YouTubeAuth.sapisidOf("a=1; b=2"))
    }

    @Test
    fun brandAccountIdComesFromDataSyncId() = runTest {
        val (auth, accounts) = auth()
        accounts.save(StoredAccount(SessionProvider.YOUTUBE, cookie, dataSyncId = "1234||5678", connectedAtEpochMs = 1L))
        assertEquals("1234", auth.onBehalfOfUser())
        accounts.save(StoredAccount(SessionProvider.YOUTUBE, cookie, dataSyncId = "5678||", connectedAtEpochMs = 1L))
        assertNull(auth.onBehalfOfUser())
    }
}
