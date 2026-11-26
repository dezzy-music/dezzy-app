/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.database.SqlMusicRepository
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.model.Track
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import org.koin.dsl.module

/** «Usar para búsquedas». */
val SpotifyUseSearchKey = booleanPreferencesKey("spotify_use_search")

/** «Usar para la pantalla de inicio». */
val SpotifyUseHomeKey = booleanPreferencesKey("spotify_use_home")

/** «Exclusivo de inicio»: sólo cuenta si [SpotifyUseHomeKey] está activo. */
val SpotifyHomeOnlyKey = booleanPreferencesKey("spotify_home_only")

/** «Sincronizar Me gusta en vivo». */
val SpotifyLiveLikeSyncKey = booleanPreferencesKey("spotify_live_like_sync")

/** Id de la canción más reciente de «Me gusta» ya procesada por la sincronización en vivo. */
val SpotifyLastLikeIdKey = stringPreferencesKey("spotify_last_like_id")

/**
 * Puente entre los ajustes de Spotify y las pantallas: búsqueda, inicio y «me gusta» en vivo.
 * Todo es tolerante a fallos: sin sesión, con el interruptor apagado o sin red devuelve vacío
 * y no hace ninguna llamada innecesaria.
 */
class SpotifyFeatures(
    private val client: SpotifyClient,
    private val matcher: SpotifyMatcher,
    private val accounts: AccountStore,
    private val prefs: DezzyPreferences,
    private val repo: MusicRepository,
) {
    private val syncMutex = Mutex()
    private var lastSyncAt = 0L

    /** Hay sesión de Spotify guardada y aceptada. */
    fun isActive(): Boolean = accounts.sessions.value[SessionProvider.SPOTIFY]?.isValid == true

    suspend fun searchEnabled(): Boolean = isActive() && prefs.value(SpotifyUseSearchKey, false).first()

    suspend fun homeEnabled(): Boolean = isActive() && prefs.value(SpotifyUseHomeKey, false).first()

    /** «Exclusivo de inicio»: sólo tiene efecto si el inicio de Spotify está activo. */
    suspend fun homeOnly(): Boolean = homeEnabled() && prefs.value(SpotifyHomeOnlyKey, false).first()

    suspend fun searchTracks(query: String, limit: Int = 10): List<SpotifyTrack> {
        if (!isActive() || query.isBlank()) return emptyList()
        return safe { client.searchTracks(query, limit) }
    }

    /** Ahora mismo no hay una consulta de «lo más escuchado» de la cuenta: devuelve vacío. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun topTracks(limit: Int = 20): List<SpotifyTrack> = emptyList()

    suspend fun recentLikes(limit: Int = 20): List<SpotifyTrack> {
        if (!isActive()) return emptyList()
        return safe { client.recentLikes(limit) }
    }

    /** Busca el equivalente en el catálogo de música de una canción de Spotify. */
    suspend fun resolve(track: SpotifyTrack): Track? = try {
        matcher.match(track)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * Si «Sincronizar Me gusta en vivo» está activo, añade a favoritos los «me gusta» nuevos de
     * Spotify (los que llegaron tras el último ya procesado). La primera vez sólo marca el punto de
     * partida: no trae lo anterior (para eso está la importación). Nunca quita favoritos.
     * Como mucho una vez cada 10 minutos.
     */
    suspend fun maybeAutoSyncLikes() {
        if (!isActive()) return
        if (!prefs.value(SpotifyLiveLikeSyncKey, false).first()) return
        if (!syncMutex.tryLock()) return
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - lastSyncAt < MIN_INTERVAL_MS) return
            val recent = safe { client.recentLikes(PAGE) }
            if (recent.isEmpty()) return
            lastSyncAt = now
            val lastId = prefs.value(SpotifyLastLikeIdKey, "").first()
            if (lastId.isNotEmpty()) {
                val fresh = recent.takeWhile { it.id != lastId }
                // Del más antiguo al más reciente para conservar el orden de los favoritos.
                for (t in fresh.asReversed()) {
                    val match = resolve(t) ?: continue
                    val id = match.videoId ?: continue
                    if (!repo.isFavorite(id)) repo.setFavorite(match, true)
                }
            }
            prefs.set(SpotifyLastLikeIdKey, recent.first().id)
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val PAGE = 30
        const val MIN_INTERVAL_MS = 10L * 60 * 1000
    }
}

val spotifyFeaturesModule = module {
    single { SpotifyFeatures(get(), get(), get(), get(), get<SqlMusicRepository>()) }
}
