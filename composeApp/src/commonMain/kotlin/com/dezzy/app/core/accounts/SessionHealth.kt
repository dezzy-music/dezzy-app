/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.accounts

import com.dezzy.app.core.model.SessionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Salud de las sesiones. Una sesión sólo se da por perdida tras ≥3 rechazos de
 * autenticación (401/403) con ≥20 min entre el primero y el último; un fallo
 * pasajero (red, 5xx, 429) nunca la invalida, sólo alarga la espera para reintentar.
 */
class SessionHealth(private val now: () -> Long) {

    private data class State(
        val rejections: Map<SessionProvider, List<Long>> = emptyMap(),
        val transientFailures: Map<SessionProvider, Int> = emptyMap(),
    )

    private val state = MutableStateFlow(State())
    private val _needsRelogin = MutableStateFlow<Set<SessionProvider>>(emptySet())

    /** Proveedores cuya sesión ya no sirve y hay que volver a iniciar. */
    val needsRelogin: StateFlow<Set<SessionProvider>> = _needsRelogin.asStateFlow()

    fun onSuccess(provider: SessionProvider) {
        state.update { it.copy(rejections = it.rejections - provider, transientFailures = it.transientFailures - provider) }
        _needsRelogin.update { it - provider }
    }

    fun onRejected(provider: SessionProvider) {
        val t = now()
        val updated = state.updateAndGet { s ->
            s.copy(rejections = s.rejections + (provider to (s.rejections[provider].orEmpty() + t)))
        }
        val list = updated.rejections[provider].orEmpty()
        if (list.size >= MIN_REJECTIONS && list.last() - list.first() >= MIN_SPAN_MS) {
            _needsRelogin.update { it + provider }
        }
    }

    fun onTransientFailure(provider: SessionProvider) {
        state.update { s ->
            s.copy(transientFailures = s.transientFailures + (provider to ((s.transientFailures[provider] ?: 0) + 1)))
        }
    }

    /** Espera antes del siguiente reintento: 15 s, 1 min, 5 min y 15 min (tope). */
    fun nextRetryDelayMs(provider: SessionProvider): Long {
        val failures = state.value.transientFailures[provider] ?: 0
        return DELAYS_MS[failures.coerceIn(0, DELAYS_MS.lastIndex)]
    }

    private inline fun MutableStateFlow<State>.updateAndGet(transform: (State) -> State): State {
        while (true) {
            val prev = value
            val next = transform(prev)
            if (compareAndSet(prev, next)) return next
        }
    }

    private companion object {
        const val MIN_REJECTIONS = 3
        const val MIN_SPAN_MS = 20L * 60_000L
        val DELAYS_MS = longArrayOf(15_000L, 60_000L, 300_000L, 900_000L)
    }
}
