/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.core.model.MusicItemType
import com.dezzy.app.ui.screens.library.SavedAlbumsKey
import com.dezzy.app.ui.screens.library.SubscribedArtistsKey
import com.dezzy.app.ui.screens.library.mergeLocalItems
import com.dezzy.app.ui.screens.settings.prefs.YtSyncLibraryKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
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

    private val allMutex = Mutex()
    private val _syncing = MutableStateFlow(false)

    /** true mientras corre la sincronización completa. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /**
     * Sincronización completa de la cuenta, de una vez (como la app actual al iniciar sesión):
     * «me gusta», todas las listas de la biblioteca, artistas suscritos y álbumes guardados.
     * Sin «Sincronizar biblioteca» activo sólo trae los «me gusta». Nunca borra nada local.
     */
    suspend fun syncAll() {
        if (accounts.account(SessionProvider.YOUTUBE) == null) return
        if (!allMutex.tryLock()) return
        _syncing.value = true
        try {
            syncLikes()
            if (!prefs.value(YtSyncLibraryKey, true).first()) return
            val lists = safe { client.getLibraryPlaylists() }
            var written = 0
            for (list in lists) {
                val tracks = safe { client.getPlaylist(list.id).getOrThrow().tracks }
                    .filter { it.videoId != null }
                    .distinctBy { it.videoId }
                // Una lista que llega vacía (fallo de red) no pisa la que ya había.
                if (tracks.isNotEmpty()) {
                    repo.replaceImportedPlaylist(SOURCE, list.id, list.title, list.thumbnail, tracks)
                    written++
                }
            }
            val artists = safe { client.getLibraryItems(BROWSE_ARTISTS) }.filter { it.type == MusicItemType.ARTIST }
            if (artists.isNotEmpty()) mergeLocalItems(prefs, SubscribedArtistsKey, artists)
            val albums = safe { client.getLibraryItems(BROWSE_ALBUMS) }.filter { it.type == MusicItemType.ALBUM }
            if (albums.isNotEmpty()) mergeLocalItems(prefs, SavedAlbumsKey, albums)
            // Sólo se da por hecha la primera sincronización completa si llegó algo.
            if (written > 0 || lists.isEmpty() && artists.isNotEmpty()) prefs.set(FULL_SYNC_DONE, true)
        } finally {
            _syncing.value = false
            allMutex.unlock()
        }
    }

    /** Al arrancar: sincronización completa si pasaron al menos 12 h desde la última. */
    suspend fun syncLikesIfDue() {
        if (accounts.account(SessionProvider.YOUTUBE) == null) return
        val last = prefs.youtubeLastLikesSync.first()
        // Con sesión ya iniciada pero sin una sincronización completa todavía: se hace ya.
        val fullDone = prefs.value(FULL_SYNC_DONE, false).first()
        if (!fullDone || now() - last >= INTERVAL_MS) syncAll()
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val INTERVAL_MS = 12L * 60 * 60 * 1000
        const val SOURCE = "youtube"
        val FULL_SYNC_DONE = androidx.datastore.preferences.core.booleanPreferencesKey("youtubeFullLibrarySyncDone")
        const val BROWSE_ARTISTS = "FEmusic_library_corpus_artists"
        const val BROWSE_ALBUMS = "FEmusic_liked_albums"
    }
}
