/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Trae a la biblioteca local los «me gusta» de la cuenta de YouTube. Sólo añade favoritos:
 * nunca quita ninguno. [repo] es el repositorio local (sin devolver el cambio a YouTube).
 */
class YouTubeLibrarySync(
    private val client: YouTubeMusicClient,
    private val repo: MusicRepository,
    private val accounts: AccountStore,
    private val prefs: DezzyPreferences,
) {
    /** Devuelve cuántos favoritos nuevos se añadieron. */
    suspend fun syncLikes(): Int {
        if (accounts.account(SessionProvider.YOUTUBE) == null) return 0
        val liked = try {
            client.getLikedSongs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return 0
        }
        var added = 0
        // YouTube lista del más reciente al más antiguo: se añaden al revés para conservar el orden.
        for (track in liked.asReversed()) {
            val id = track.videoId ?: continue
            if (repo.isFavorite(id)) continue
            repo.setFavorite(track, true)
            added++
        }
        prefs.setYoutubeLastLikesSync(now())
        return added
    }

    /** Sincroniza si pasaron al menos 12 h desde la última vez. */
    suspend fun syncLikesIfDue() {
        if (accounts.account(SessionProvider.YOUTUBE) == null) return
        val last = prefs.youtubeLastLikesSync.first()
        if (now() - last >= INTERVAL_MS) syncLikes()
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}
