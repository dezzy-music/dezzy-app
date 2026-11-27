/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.feature.musicmap.MusicMapLocation
import com.dezzy.app.feature.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Publica «Escuchando ahora» (NIP-38) al cambiar de canción o de play/pausa. */
@OptIn(FlowPreview::class)
class P2PNowPlayingBridge(
    private val player: PlayerController,
    private val presence: P2PPresence,
    private val musicMapLocation: MusicMapLocation,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            player.state
                .map { st ->
                    val t = st.queue.current?.track
                    val id = t?.videoId
                    if (t == null || id.isNullOrBlank() || !st.isPlaying || id.startsWith("dzr:")) {
                        null
                    } else {
                        P2PSong(id, t.title, t.artists, t.thumbnail, (t.durationSeconds ?: 0L).toInt())
                    }
                }
                .distinctUntilChanged()
                .debounce(2_000)
                .collect { presence.onNowPlaying(it, musicMapLocation.sharedLocation()) }
        }
    }
}
