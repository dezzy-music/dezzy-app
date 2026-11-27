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
import kotlinx.coroutines.flow.first
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

    /** «Auto descargar al dar me gusta». Se buscan en Koin al usarse para no crear un ciclo de dependencias. */
    private suspend fun autoDownloadLiked(track: Track) {
        val koin = org.koin.mp.KoinPlatform.getKoin()
        val prefs = koin.getOrNull<com.dezzy.app.core.prefs.DezzyPreferences>() ?: return
        val enabled = runCatching {
            prefs.value(com.dezzy.app.ui.screens.settings.prefs.AutoDownloadOnLikeKey, false).first()
        }.getOrDefault(false)
        if (!enabled) return
        koin.getOrNull<com.dezzy.app.feature.downloads.DownloadManager>()?.enqueue(listOf(track))
    }

    override suspend fun setFavorite(track: Track, favorite: Boolean) {
        inner.setFavorite(track, favorite)
        if (favorite) scope.launch { autoDownloadLiked(track) }
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
