/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

/**
 * Notificaciones de la red social P2P: mensajes cifrados nuevos y solicitudes de seguimiento.
 * Una notificación por conversación (estilo mensajería), que se borra al abrir el chat.
 * El dibujo real lo hace cada plataforma ([P2PNotifier]); en el escritorio no avisa.
 */
class P2PNotifications(private val notifier: P2PNotifier) {

    /** Conversación abierta ahora mismo: sus mensajes no avisan. La UI la fija al abrir/cerrar un chat. */
    var activeChatPeer: String? = null

    fun notifyMessage(peer: String, name: String, text: String, songTitle: String?, createdAtSec: Long) {
        if (peer == activeChatPeer) return
        notifier.notifyMessage(peer, name, text, songTitle, createdAtSec)
    }

    fun notifyFollowRequest(peer: String, name: String) = notifier.notifyFollowRequest(peer, name)

    /** Al abrir la conversación: fuera el aviso y las líneas acumuladas. */
    fun clearChat(peer: String) = notifier.clearChat(peer)

    fun clearAll() = notifier.clearAll()

    companion object {
        /** Acción del intent con que la notificación abre un chat (extra [EXTRA_PEER] = clave hex). */
        const val ACTION_OPEN_CHAT = "com.dezzy.app.action.OPEN_P2P_CHAT"
        const val ACTION_OPEN_SOCIAL = "com.dezzy.app.action.OPEN_P2P_SOCIAL"
        const val EXTRA_PEER = "p2p_peer"
    }
}
