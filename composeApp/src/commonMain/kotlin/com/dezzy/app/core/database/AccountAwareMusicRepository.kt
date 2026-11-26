/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.database

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.Track
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Repositorio que, además de guardar el favorito en local, lo refleja como «me gusta»
 * en la cuenta de YouTube si hay sesión. Lo de YouTube va aparte y sus fallos se ignoran.
 */
class AccountAwareMusicRepository(
    private val inner: MusicRepository,
    private val client: YouTubeMusicClient,
    private val accounts: AccountStore,
) : MusicRepository by inner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun setFavorite(track: Track, favorite: Boolean) {
        inner.setFavorite(track, favorite)
        val videoId = track.videoId ?: return
        scope.launch {
            try {
                if (accounts.account(SessionProvider.YOUTUBE) != null) {
                    client.setLiked(videoId, favorite)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sin red o sin permiso: el favorito local ya está guardado
            }
        }
    }
}
