/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Anota en `dj_skip_event` las canciones que el oyente salta (cambia de canción tras oírla entre
 * 1 y 30 segundos, y dura más de un minuto): así el perfil de gustos deja de ofrecer lo que no quiere.
 * `start()` es idempotente; basta con llamarlo al arrancar la app.
 */
class DjSkipBridge(
    private val player: PlayerController,
    private val store: DjStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var entryId: String? = null
            var videoId: String? = null
            var artist: String? = null
            var position = 0L
            var duration = 0L
            player.state.collect { st ->
                val item = st.queue.current
                val id = item?.queueEntryId
                if (id != entryId) {
                    val skipped = entryId != null && videoId != null && position in MIN_LISTEN_MS..MAX_LISTEN_MS && duration > MIN_DURATION_MS
                    if (skipped) djCatching { store.recordSkip(videoId!!, artist) }
                    entryId = id
                    videoId = item?.track?.videoId
                    artist = item?.track?.artists
                    position = 0L
                    duration = 0L
                }
                if (item != null) {
                    position = st.positionMs
                    if (st.durationMs > 0) duration = st.durationMs
                    else item.track.durationSeconds?.let { duration = it * 1000L }
                }
            }
        }
    }

    private companion object {
        const val MIN_LISTEN_MS = 1_000L
        const val MAX_LISTEN_MS = 30_000L
        const val MIN_DURATION_MS = 60_000L
    }
}
