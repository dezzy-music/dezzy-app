/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.feature.social.data.P2PMessageRow
import com.dezzy.app.feature.social.data.P2PPrivateReactionRow
import com.dezzy.app.feature.social.data.P2PStore
import com.dezzy.app.feature.social.nostr.DEFAULT_RELAYS
import com.dezzy.app.feature.social.nostr.GiftWrap
import com.dezzy.app.feature.social.nostr.Kinds
import com.dezzy.app.feature.social.nostr.NostrEvent
import com.dezzy.app.feature.social.nostr.RelayPool
import com.dezzy.app.feature.social.nostr.nostrFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Mensajería cifrada de extremo a extremo (NIP-17): cada mensaje viaja en un sobre que sólo
 * puede abrir el destinatario. Se envía también una copia cifrada para mí, así el historial
 * se recupera al entrar con las 12 palabras en otro dispositivo.
 *
 * Por el mismo buzón llegan las publicaciones de cuentas privadas, sus me gusta/comentarios
 * y las solicitudes de seguimiento.
 */
class P2PMessaging(
    private val identity: P2PIdentity,
    private val relays: RelayPool,
    private val store: P2PStore,
    private val settings: P2PSettings,
    private val notifications: P2PNotifications,
    private val social: () -> P2PSocial,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handleLock = Mutex()
    private var inboxJob: Job? = null
    private val dmRelaysCache = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    // --- Buzón ------------------------------------------------------------------------------

    private fun inboxFilter(pubkey: String, since: Long?, limit: Int? = null): JsonObject =
        nostrFilter(kinds = listOf(Kinds.GIFT_WRAP), tags = mapOf("#p" to listOf(pubkey)), since = since, limit = limit)

    /** Escucha mi buzón cifrado en los relés (idempotente). */
    fun start() {
        if (inboxJob?.isActive == true) return
        val keys = identity.currentKeys() ?: return
        inboxJob = scope.launch {
            val lastSync = settings.lastInboxSync()
            val since = if (lastSync > 0) lastSync - CLOCK_MARGIN else null
            publishDmRelaysOnce()
            _syncing.value = true
            // Primero lo pendiente (consulta), luego en vivo (suscripción).
            runCatching {
                relays.query(listOf(inboxFilter(keys.publicKey, since, 1000)), timeoutMs = 10_000).forEach { handle(it) }
            }
            settings.setLastInboxSync(NostrEvent.now())
            _syncing.value = false
            relays.subscribe(listOf(inboxFilter(keys.publicKey, NostrEvent.now() - CLOCK_MARGIN))).collect { wrap ->
                handle(wrap)
                settings.setLastInboxSync(NostrEvent.now())
            }
        }
    }

    fun stop() {
        inboxJob?.cancel()
        inboxJob = null
    }

    /**
     * Revisa el buzón una sola vez (lo usa [P2PInboxWorker]).
     * Los sobres ya guardados se descartan, así que no hay avisos repetidos.
     */
    suspend fun syncNow() = withContext(Dispatchers.Default) {
        val keys = identity.currentKeys() ?: return@withContext
        val lastSync = settings.lastInboxSync()
        val since = if (lastSync > 0) lastSync - CLOCK_MARGIN else null
        try {
            relays.query(listOf(inboxFilter(keys.publicKey, since, 500)), timeoutMs = 15_000).forEach { handle(it) }
            settings.setLastInboxSync(NostrEvent.now())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red: se reintenta en la siguiente vuelta.
        }
    }

    /** ¿Merece aviso algo creado en [createdAt]? Nada del historial previo a la primera sincronización. */
    private suspend fun shouldNotify(createdAt: Long): Boolean {
        var from = settings.notifyFrom()
        if (from == 0L) {
            from = NostrEvent.now()
            settings.setNotifyFrom(from)
        }
        return createdAt >= from && createdAt >= NostrEvent.now() - NOTIFY_MAX_AGE
    }

    private suspend fun handle(wrap: NostrEvent) = handleLock.withLock {
        val keys = identity.currentKeys() ?: return@withLock
        val rumor = withContext(Dispatchers.Default) { GiftWrap.unwrap(wrap, keys) } ?: return@withLock
        val me = keys.publicKey
        val control = rumor.tagValues(CONTROL_TAG).firstOrNull()
        val app = social()
        when {
            control == "private_post" && rumor.kind == Kinds.TEXT_NOTE -> app.storePrivatePost(rumor)
            control == "private_reaction" || control == "private_comment" -> storePrivateReaction(rumor)
            control == "private_delete" -> {
                val target = rumor.tag("e") ?: return@withLock
                val stored = store.privatePost(target) ?: return@withLock
                if (stored.author == rumor.pubkey) store.deletePrivatePost(target)
            }
            control == "follow_request" && rumor.pubkey != me -> {
                val existing = store.followRequest(rumor.pubkey, incoming = true)
                if (existing?.status == "approved") return@withLock
                store.upsertFollowRequest(P2PFollowRequest(rumor.pubkey, true, "pending", rumor.createdAt))
                app.loadProfiles(listOf(rumor.pubkey))
                if (existing == null && shouldNotify(rumor.createdAt)) {
                    notifications.notifyFollowRequest(rumor.pubkey, app.user(rumor.pubkey).displayName)
                }
            }
            control == "follow_approved" && rumor.pubkey != me ->
                store.upsertFollowRequest(P2PFollowRequest(rumor.pubkey, false, "approved", rumor.createdAt))
            control == "follow_rejected" && rumor.pubkey != me ->
                store.upsertFollowRequest(P2PFollowRequest(rumor.pubkey, false, "rejected", rumor.createdAt))
            control == null && rumor.kind == Kinds.CHAT_MESSAGE -> {
                val fromMe = rumor.pubkey == me
                val peer = if (fromMe) rumor.tagValues("p").firstOrNull { it != me } ?: return@withLock else rumor.pubkey
                val song = P2PSong.fromTag(rumor.fullTag(P2PSong.TAG))
                val isNew = store.insertMessage(
                    P2PMessageRow(rumor.id, peer, fromMe, rumor.content, rumor.createdAt, song?.toJson(), rumor.tag(JUNTOS_TAG)),
                )
                if (!fromMe) {
                    app.loadProfiles(listOf(peer))
                    // Si ya estaba guardado (llegó por otro relé) no se avisa otra vez.
                    if (isNew && shouldNotify(rumor.createdAt)) {
                        notifications.notifyMessage(peer, app.user(peer).displayName, rumor.content, song?.title, rumor.createdAt)
                    }
                }
            }
            else -> Unit
        }
    }

    internal suspend fun storePrivateReaction(rumor: NostrEvent) {
        val postId = rumor.tagValues("e").firstOrNull() ?: return
        store.insertPrivateReaction(
            P2PPrivateReactionRow(
                id = rumor.id,
                postId = postId,
                author = rumor.pubkey,
                createdAt = rumor.createdAt,
                type = if (rumor.kind == Kinds.REACTION) "like" else "comment",
                content = rumor.content,
                replyTo = rumor.tags.firstOrNull { it.size >= 4 && it[0] == "e" && it[3] == "reply" }?.get(1),
            ),
        )
    }

    // --- Envío ------------------------------------------------------------------------------

    /** Relés donde cada persona dice recibir sus mensajes (NIP-17, kind 10050). */
    private suspend fun dmRelaysOf(pubkey: String): List<String> {
        dmRelaysCache.value[pubkey]?.let { return it }
        val list = runCatching {
            relays.query(
                listOf(nostrFilter(authors = listOf(pubkey), kinds = listOf(Kinds.DM_RELAYS), limit = 1)),
                timeoutMs = 4000,
            ).maxByOrNull { it.createdAt }
                ?.tags?.filter { it.firstOrNull() == "relay" }?.mapNotNull { it.getOrNull(1) }
                ?.filter { it.startsWith("wss://") }
                .orEmpty()
        }.getOrDefault(emptyList())
        val merged = (list.take(4) + DEFAULT_RELAYS).distinct()
        dmRelaysCache.update { it + (pubkey to merged) }
        return merged
    }

    private suspend fun publishDmRelaysOnce() {
        if (settings.dmRelaysPublished()) return
        val keys = identity.currentKeys() ?: return
        val event = NostrEvent.sign(keys, Kinds.DM_RELAYS, DEFAULT_RELAYS.map { listOf("relay", it) }, "")
        if (relays.publish(event) > 0) settings.setDmRelaysPublished(true)
    }

    /** Cifra [rumor] por separado para cada destinatario y lo publica en sus relés. */
    internal suspend fun sendWrapped(rumor: NostrEvent, recipients: Collection<String>): Int = withContext(Dispatchers.Default) {
        val keys = identity.currentKeys() ?: return@withContext 0
        var delivered = 0
        recipients.distinct().forEach { recipient ->
            val wrap = GiftWrap.wrap(rumor, keys, recipient)
            if (relays.publish(wrap, dmRelaysOf(recipient)) > 0) delivered++
        }
        delivered
    }

    /** Envía un mensaje (opcionalmente con canción o invitación a «Juntos»). */
    suspend fun sendMessage(peer: String, text: String, song: P2PSong? = null, juntosLink: String? = null): Boolean =
        withContext(Dispatchers.Default) {
            val keys = identity.currentKeys() ?: return@withContext false
            val clean = text.trim()
            if (clean.isEmpty() && song == null && juntosLink == null) return@withContext false
            val tags = buildList {
                add(listOf("p", peer))
                song?.let { add(it.toTag()) }
                juntosLink?.let { add(listOf(JUNTOS_TAG, it)) }
            }
            val rumor = NostrEvent.rumor(keys.publicKey, Kinds.CHAT_MESSAGE, tags, clean)
            store.insertMessage(P2PMessageRow(rumor.id, peer, true, clean, rumor.createdAt, song?.toJson(), juntosLink))
            markRead(peer)
            // Primero al destinatario; la copia para mí no bloquea el resultado.
            val ok = sendWrapped(rumor, listOf(peer)) > 0
            scope.launch { sendWrapped(rumor, listOf(keys.publicKey)) }
            ok
        }

    // --- Seguir cuentas privadas ------------------------------------------------------------

    private suspend fun sendControl(peer: String, control: String, text: String) {
        val keys = identity.currentKeys() ?: return
        val rumor = NostrEvent.rumor(
            keys.publicKey,
            Kinds.CHAT_MESSAGE,
            listOf(listOf("p", peer), listOf(CONTROL_TAG, control)),
            text,
        )
        sendWrapped(rumor, listOf(peer))
    }

    internal suspend fun requestFollow(peer: String) {
        store.upsertFollowRequest(P2PFollowRequest(peer, false, "pending", NostrEvent.now()))
        sendControl(peer, "follow_request", "Quiere seguirte en Dezzy")
    }

    suspend fun approveFollower(peer: String) {
        store.upsertFollowRequest(P2PFollowRequest(peer, true, "approved", NostrEvent.now()))
        sendControl(peer, "follow_approved", "Aceptó tu solicitud en Dezzy")
        social().shareRecentPrivatePosts(peer)
    }

    suspend fun rejectFollower(peer: String) {
        store.upsertFollowRequest(P2PFollowRequest(peer, true, "rejected", NostrEvent.now()))
        sendControl(peer, "follow_rejected", "")
    }

    // --- Lectura para la UI -----------------------------------------------------------------

    fun messages(peer: String): Flow<List<P2PMessage>> = store.messages(peer).map { list ->
        list.map { P2PMessage(it.id, it.fromMe, it.content, it.createdAt, P2PSong.fromJson(it.songJson), it.juntosLink) }
    }

    val conversations: Flow<List<P2PConversation>>
        get() = combine(store.lastMessages(), store.unreadCounts()) { last, unread ->
            val unreadMap = unread.associate { it.peer to it.unread }
            last.map { m ->
                val preview = when {
                    m.content.isNotBlank() -> m.content
                    m.songJson != null -> "Canción: " + (P2PSong.fromJson(m.songJson)?.title ?: "Canción")
                    m.juntosLink != null -> "Invitación a escuchar juntos"
                    else -> ""
                }
                P2PConversation(m.peer, preview, m.createdAt, m.fromMe, unreadMap[m.peer] ?: 0)
            }
        }

    val totalUnread: Flow<Int> get() = store.unreadCounts().map { list -> list.sumOf { it.unread } }

    suspend fun markRead(peer: String) {
        notifications.clearChat(peer)
        store.upsertChatState(peer, NostrEvent.now())
    }

    suspend fun deleteConversation(peer: String) = store.deleteConversation(peer)

    private companion object {
        const val NOTIFY_MAX_AGE = 7 * 24 * 60 * 60L
        const val JUNTOS_TAG = "dezzy_juntos"
        const val CONTROL_TAG = P2PSocial.APP_TAG

        /** Las fechas de los sobres se desordenan hasta 2 días: pedimos con margen. */
        const val CLOCK_MARGIN = 3 * 24 * 60 * 60L
    }
}
