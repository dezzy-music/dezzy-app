/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.feature.social.P2PFollowRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class P2PMessageRow(
    val id: String,
    val peer: String,
    val fromMe: Boolean,
    val content: String,
    val createdAt: Long,
    /** Canción adjunta como JSON (ver `P2PSong.toJson`). */
    val songJson: String?,
    /** Enlace de invitación a una sala «Juntos». */
    val juntosLink: String?,
)

class P2PProfileRow(
    val pubkey: String,
    val name: String,
    val about: String,
    val picture: String?,
    val isPrivate: Boolean,
    val updatedAt: Long,
    val banner: String?,
    val bannerVideo: String?,
)

class P2PPrivatePostRow(val id: String, val author: String, val createdAt: Long, val json: String)

class P2PPrivateReactionRow(
    val id: String,
    val postId: String,
    val author: String,
    val createdAt: Long,
    /** "like" o "comment". */
    val type: String,
    val content: String,
    val replyTo: String?,
)

class PeerUnread(val peer: String, val unread: Int)

/**
 * Copia local de la red social P2P (tablas `p2p_*` de Dezzy.sq). Vive sólo en el almacenamiento
 * privado de la app y se reconstruye desde los relés si se borra.
 *
 * Las tablas se crean también aquí (IF NOT EXISTS) por si la base ya existía antes de la fase 9:
 * el esquema de SQLDelight sólo se ejecuta al crear la base.
 */
class P2PStore(db: DezzyDatabase, driver: SqlDriver) {

    private val q = db.dezzyQueries

    init {
        for (sql in SCHEMA) runCatching { driver.execute(null, sql, 0) }
    }

    // --- mensajes -------------------------------------------------------------

    /** true si el mensaje era nuevo (no estaba guardado). */
    suspend fun insertMessage(m: P2PMessageRow): Boolean = withContext(Dispatchers.Default) {
        val existed = q.p2pMessageExists(m.id).executeAsOne() > 0L
        if (!existed) q.p2pInsertMessage(m.id, m.peer, m.fromMe, m.content, m.createdAt, m.songJson, m.juntosLink)
        !existed
    }

    fun messages(peer: String): Flow<List<P2PMessageRow>> =
        q.p2pMessages(peer) { id, p, fromMe, content, createdAt, songJson, juntos ->
            P2PMessageRow(id, p, fromMe, content, createdAt, songJson, juntos)
        }.asFlow().mapToList(Dispatchers.Default)

    fun lastMessages(): Flow<List<P2PMessageRow>> =
        q.p2pLastMessages { id, p, fromMe, content, createdAt, songJson, juntos ->
            P2PMessageRow(id, p, fromMe, content, createdAt, songJson, juntos)
        }.asFlow().mapToList(Dispatchers.Default)

    fun unreadCounts(): Flow<List<PeerUnread>> =
        q.p2pUnreadCounts { peer, unread -> PeerUnread(peer, unread.toInt()) }
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun upsertChatState(peer: String, lastReadAt: Long) = withContext(Dispatchers.Default) {
        q.p2pUpsertChatState(peer, lastReadAt)
    }

    suspend fun deleteConversation(peer: String) = withContext(Dispatchers.Default) {
        q.p2pDeleteConversation(peer)
    }

    // --- perfiles -------------------------------------------------------------

    suspend fun upsertProfile(p: P2PProfileRow) = withContext(Dispatchers.Default) {
        q.p2pUpsertProfile(p.pubkey, p.name, p.about, p.picture, p.isPrivate, p.updatedAt, p.banner, p.bannerVideo)
    }

    suspend fun profiles(pubkeys: List<String>): List<P2PProfileRow> = withContext(Dispatchers.Default) {
        if (pubkeys.isEmpty()) return@withContext emptyList()
        pubkeys.chunked(500).flatMap { chunk ->
            q.p2pProfiles(chunk) { pubkey, name, about, picture, isPrivate, updatedAt, banner, bannerVideo ->
                P2PProfileRow(pubkey, name, about, picture, isPrivate, updatedAt, banner, bannerVideo)
            }.executeAsList()
        }
    }

    // --- publicaciones y reacciones privadas -------------------------------

    suspend fun insertPrivatePost(post: P2PPrivatePostRow) = withContext(Dispatchers.Default) {
        q.p2pInsertPrivatePost(post.id, post.author, post.createdAt, post.json)
    }

    suspend fun privatePosts(authors: List<String>, limit: Int): List<P2PPrivatePostRow> =
        withContext(Dispatchers.Default) {
            if (authors.isEmpty()) return@withContext emptyList()
            q.p2pPrivatePosts(authors, limit.toLong()) { id, author, createdAt, json ->
                P2PPrivatePostRow(id, author, createdAt, json)
            }.executeAsList()
        }

    suspend fun privatePostsOf(author: String, limit: Int): List<P2PPrivatePostRow> =
        withContext(Dispatchers.Default) {
            q.p2pPrivatePostsOf(author, limit.toLong()) { id, a, createdAt, json ->
                P2PPrivatePostRow(id, a, createdAt, json)
            }.executeAsList()
        }

    suspend fun privatePost(id: String): P2PPrivatePostRow? = withContext(Dispatchers.Default) {
        q.p2pPrivatePost(id) { i, author, createdAt, json -> P2PPrivatePostRow(i, author, createdAt, json) }
            .executeAsOneOrNull()
    }

    suspend fun deletePrivatePost(id: String) = withContext(Dispatchers.Default) {
        q.p2pDeletePrivatePost(id)
    }

    suspend fun insertPrivateReaction(r: P2PPrivateReactionRow) = withContext(Dispatchers.Default) {
        q.p2pInsertPrivateReaction(r.id, r.postId, r.author, r.createdAt, r.type, r.content, r.replyTo)
    }

    suspend fun privateReactions(postIds: List<String>): List<P2PPrivateReactionRow> =
        withContext(Dispatchers.Default) {
            if (postIds.isEmpty()) return@withContext emptyList()
            postIds.chunked(500).flatMap { chunk ->
                q.p2pPrivateReactions(chunk) { id, postId, author, createdAt, type, content, replyTo ->
                    P2PPrivateReactionRow(id, postId, author, createdAt, type, content, replyTo)
                }.executeAsList()
            }
        }

    // --- solicitudes de seguimiento -------------------------------------------

    suspend fun upsertFollowRequest(r: P2PFollowRequest) = withContext(Dispatchers.Default) {
        q.p2pUpsertFollowRequest(r.pubkey, r.incoming, r.status, r.createdAt)
    }

    fun pendingIncomingRequests(): Flow<List<P2PFollowRequest>> =
        q.p2pPendingIncomingRequests { pubkey, incoming, status, createdAt ->
            P2PFollowRequest(pubkey, incoming, status, createdAt)
        }.asFlow().mapToList(Dispatchers.Default)

    suspend fun approvedFollowers(): List<String> = withContext(Dispatchers.Default) {
        q.p2pApprovedFollowers().executeAsList()
    }

    suspend fun followRequest(pubkey: String, incoming: Boolean): P2PFollowRequest? =
        withContext(Dispatchers.Default) {
            q.p2pFollowRequest(pubkey, incoming) { p, inc, status, createdAt ->
                P2PFollowRequest(p, inc, status, createdAt)
            }.executeAsOneOrNull()
        }

    /** Borra todo lo social de este dispositivo (cerrar sesión). */
    suspend fun clearAll() = withContext(Dispatchers.Default) {
        q.transaction {
            q.p2pClearMessages()
            q.p2pClearPrivatePosts()
            q.p2pClearPrivateReactions()
            q.p2pClearFollowRequests()
            q.p2pClearChatState()
        }
    }

    private companion object {
        val SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS p2p_message (
                id TEXT NOT NULL PRIMARY KEY, peer TEXT NOT NULL, from_me INTEGER NOT NULL,
                content TEXT NOT NULL, created_at INTEGER NOT NULL, song_json TEXT, juntos_link TEXT)""",
            "CREATE INDEX IF NOT EXISTS p2p_message_peer ON p2p_message(peer)",
            "CREATE INDEX IF NOT EXISTS p2p_message_created ON p2p_message(created_at)",
            """CREATE TABLE IF NOT EXISTS p2p_profile (
                pubkey TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, about TEXT NOT NULL, picture TEXT,
                is_private INTEGER NOT NULL, updated_at INTEGER NOT NULL, banner TEXT, banner_video TEXT)""",
            """CREATE TABLE IF NOT EXISTS p2p_private_post (
                id TEXT NOT NULL PRIMARY KEY, author TEXT NOT NULL, created_at INTEGER NOT NULL, json TEXT NOT NULL)""",
            "CREATE INDEX IF NOT EXISTS p2p_private_post_author ON p2p_private_post(author)",
            """CREATE TABLE IF NOT EXISTS p2p_private_reaction (
                id TEXT NOT NULL PRIMARY KEY, post_id TEXT NOT NULL, author TEXT NOT NULL, created_at INTEGER NOT NULL,
                type TEXT NOT NULL, content TEXT NOT NULL, reply_to TEXT)""",
            "CREATE INDEX IF NOT EXISTS p2p_private_reaction_post ON p2p_private_reaction(post_id)",
            """CREATE TABLE IF NOT EXISTS p2p_follow_request (
                pubkey TEXT NOT NULL, incoming INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL,
                PRIMARY KEY (pubkey, incoming))""",
            "CREATE TABLE IF NOT EXISTS p2p_chat_state (peer TEXT NOT NULL PRIMARY KEY, last_read_at INTEGER NOT NULL)",
        )
    }
}
