/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Revisa el buzón cifrado cada ~15 min mientras el proceso esté vivo (sin WorkManager).
 * Con la app abierta el buzón ya va en vivo (relés) y esto sólo confirma que no falte nada;
 * los duplicados se descartan en la base de datos.
 */
class P2PInboxWorker(
    private val identity: P2PIdentity,
    private val messaging: P2PMessaging,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                if (identity.currentKeys() != null) withTimeoutOrNull(45_000) { messaging.syncNow() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val INTERVAL_MS = 15 * 60 * 1000L
    }
}
