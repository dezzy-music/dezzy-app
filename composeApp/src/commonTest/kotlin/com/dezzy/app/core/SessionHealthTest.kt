/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import com.dezzy.app.core.accounts.SessionHealth
import com.dezzy.app.core.model.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHealthTest {
    private var clock = 0L
    private val health = SessionHealth(now = { clock })
    private val yt = SessionProvider.YOUTUBE

    private fun minutes(m: Long) = m * 60_000L

    @Test
    fun threeRejectionsInTenMinutesDoNotAskToRelogin() {
        health.onRejected(yt)
        clock = minutes(5)
        health.onRejected(yt)
        clock = minutes(10)
        health.onRejected(yt)
        assertFalse(yt in health.needsRelogin.value)
    }

    @Test
    fun threeRejectionsOverTwentyFiveMinutesAskToRelogin() {
        health.onRejected(yt)
        clock = minutes(12)
        health.onRejected(yt)
        clock = minutes(25)
        health.onRejected(yt)
        assertTrue(yt in health.needsRelogin.value)
        assertFalse(SessionProvider.SPOTIFY in health.needsRelogin.value)
    }

    @Test
    fun successClearsEverything() {
        health.onRejected(yt)
        clock = minutes(12)
        health.onRejected(yt)
        clock = minutes(25)
        health.onRejected(yt)
        health.onTransientFailure(yt)
        health.onSuccess(yt)
        assertFalse(yt in health.needsRelogin.value)
        assertEquals(15_000L, health.nextRetryDelayMs(yt))
        // tras el éxito hay que volver a acumular rechazos desde cero
        clock = minutes(26)
        health.onRejected(yt)
        assertFalse(yt in health.needsRelogin.value)
    }

    @Test
    fun retryDelaysGrowAndCap() {
        val expected = listOf(15_000L, 60_000L, 300_000L, 900_000L, 900_000L)
        for (delay in expected) {
            assertEquals(delay, health.nextRetryDelayMs(yt))
            health.onTransientFailure(yt)
        }
    }
}
