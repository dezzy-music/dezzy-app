/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.feature.social.nostr.Kinds
import com.dezzy.app.feature.social.nostr.NostrEvent
import com.dezzy.app.feature.social.nostr.RelayPool
import com.dezzy.app.feature.social.nostr.nostrFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * «Escuchando ahora» (estado de usuario NIP-38): un evento reemplazable que caduca solo
 * cuando termina la canción. Nadie guarda mi historial: sólo existe la última canción.
 */
class P2PPresence(
    private val identity: P2PIdentity,
    private val relays: RelayPool,
    private val settings: P2PSettings,
    private val social: () -> P2PSocial,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastVideoId: String? = null
    private var lastPublishAt = 0L
    private var cleared = true
    private var lastCell: String? = null

    /**
     * Llamado por el reproductor al cambiar de canción (o al pausar, con null).
     * [location] sólo llega si el usuario comparte su ubicación en el Mapa musical; se publica
     * difuminada a una celda de ~5 km (ver [P2PMap]).
     */
    fun onNowPlaying(song: P2PSong?, location: Pair<Double, Double>? = null) {
        scope.launch {
            val keys = identity.currentKeys() ?: return@launch
            val visible = settings.listeningVisibility.first() == ListeningVisibility.EVERYONE
            val effective = if (visible) song else null
            val cell = if (effective != null) location?.let { P2PMap.cellOf(it.first, it.second) } else null
            val nowMs = Clock.System.now().toEpochMilliseconds()
            if (effective == null) {
                if (cleared) return@launch
            } else if (effective.videoId == lastVideoId && cell == lastCell && nowMs - lastPublishAt < MIN_INTERVAL_MS) {
                return@launch
            }
            lastVideoId = effective?.videoId
            lastCell = cell
            lastPublishAt = nowMs
            cleared = effective == null
            val now = NostrEvent.now()
            val tags = buildList {
                add(listOf("d", "music"))
                add(listOf("t", P2PSocial.APP_TAG))
                if (effective != null) {
                    add(listOf("r", effective.url))
                    add(effective.toTag())
                    val remaining = if (effective.durationSec > 0) effective.durationSec.toLong() else 300L
                    add(listOf("expiration", (now + remaining + 30).toString()))
                    if (location != null) addAll(P2PMap.locationTags(location.first, location.second))
                }
            }
            val content = effective?.let { "${it.title} · ${it.artist}" }.orEmpty()
            runCatching { relays.publish(NostrEvent.sign(keys, Kinds.USER_STATUS, tags, content, now)) }
        }
    }

    /** Lo que escuchan ahora mismo las personas que sigo. */
    suspend fun friendsListening(): List<P2PListening> = withContext(Dispatchers.Default) {
        val following = social().following.value.toList()
        if (following.isEmpty()) return@withContext emptyList()
        val now = NostrEvent.now()
        val events = following.chunked(250).flatMap { chunk ->
            relays.query(
                listOf(
                    nostrFilter(
                        authors = chunk,
                        kinds = listOf(Kinds.USER_STATUS),
                        tags = mapOf("#d" to listOf("music")),
                        since = now - 2 * 60 * 60,
                    ),
                ),
                timeoutMs = 5000,
            )
        }
        val list = events.groupBy { it.pubkey }.mapNotNull { (_, group) ->
            val latest = group.maxBy { it.createdAt }
            val expires = latest.tag("expiration")?.toLongOrNull()
            if (latest.content.isBlank() || (expires != null && expires < now)) return@mapNotNull null
            P2PListening(latest.pubkey, P2PSong.fromTag(latest.fullTag(P2PSong.TAG)), latest.content, latest.createdAt)
        }.sortedByDescending { it.updatedAt }
        social().loadProfiles(list.map { it.pubkey })
        list
    }

    /** Lo que escucha una persona (para su perfil o para el radar «Cerca»). */
    suspend fun listeningOf(pubkeys: List<String>): Map<String, P2PListening> = withContext(Dispatchers.Default) {
        if (pubkeys.isEmpty()) return@withContext emptyMap()
        val now = NostrEvent.now()
        relays.query(
            listOf(
                nostrFilter(
                    authors = pubkeys,
                    kinds = listOf(Kinds.USER_STATUS),
                    tags = mapOf("#d" to listOf("music")),
                    since = now - 2 * 60 * 60,
                ),
            ),
            timeoutMs = 4000,
        ).groupBy { it.pubkey }.mapNotNull { (pubkey, group) ->
            val latest = group.maxBy { it.createdAt }
            val expires = latest.tag("expiration")?.toLongOrNull()
            if (latest.content.isBlank() || (expires != null && expires < now)) {
                null
            } else {
                pubkey to P2PListening(pubkey, P2PSong.fromTag(latest.fullTag(P2PSong.TAG)), latest.content, latest.createdAt)
            }
        }.toMap()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 20_000L
    }
}
