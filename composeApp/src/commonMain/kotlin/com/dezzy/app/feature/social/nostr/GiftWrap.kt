/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.nostr

import com.dezzy.app.feature.social.crypto.hexToBytes
import com.dezzy.app.feature.social.crypto.nostrCrypto

/**
 * Sobres cifrados de NIP-59 («gift wrap») usados por los mensajes privados de NIP-17.
 *
 * rumor (sin firmar) → seal kind 13 (firmado por el autor, cifrado al destinatario)
 * → gift wrap kind 1059 (firmado por una clave desechable, cifrado al destinatario).
 * El relé sólo ve una clave aleatoria escribiéndole al destinatario: no sabe quién habla
 * con quién, ni cuándo exactamente (las fechas se desordenan hasta 2 días).
 */
object GiftWrap {

    private const val TWO_DAYS = 2 * 24 * 60 * 60L

    private fun randomPastTimestamp(): Long {
        val b = nostrCrypto.randomBytes(4)
        val n = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
            ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
        return NostrEvent.now() - (n % TWO_DAYS)
    }

    /** Envuelve [rumor] (escrito por [sender]) para que sólo [recipientPubkey] lo pueda abrir. */
    fun wrap(rumor: NostrEvent, sender: NostrKeys, recipientPubkey: String): NostrEvent {
        val sealKey = nostrCrypto.nip44ConversationKey(sender.privateKey, recipientPubkey.hexToBytes())
        val seal = NostrEvent.sign(
            keys = sender,
            kind = Kinds.SEAL,
            tags = emptyList(),
            content = nostrCrypto.nip44Encrypt(rumor.toJson(includeSig = false), sealKey),
            createdAt = randomPastTimestamp(),
        )
        val ephemeral = NostrKeys.generate()
        val wrapKey = nostrCrypto.nip44ConversationKey(ephemeral.privateKey, recipientPubkey.hexToBytes())
        return NostrEvent.sign(
            keys = ephemeral,
            kind = Kinds.GIFT_WRAP,
            tags = listOf(listOf("p", recipientPubkey)),
            content = nostrCrypto.nip44Encrypt(seal.toJson(), wrapKey),
            createdAt = randomPastTimestamp(),
        )
    }

    /**
     * Abre un gift wrap dirigido a [me]. Devuelve el rumor si todo cuadra: firma del sello
     * válida y el autor del rumor es quien firmó el sello (nadie puede hacerse pasar por otro).
     */
    fun unwrap(wrap: NostrEvent, me: NostrKeys): NostrEvent? = runCatching {
        if (wrap.kind != Kinds.GIFT_WRAP) return null
        val wrapKey = nostrCrypto.nip44ConversationKey(me.privateKey, wrap.pubkey.hexToBytes())
        val seal = NostrEvent.fromJson(nostrCrypto.nip44Decrypt(wrap.content, wrapKey)) ?: return null
        if (seal.kind != Kinds.SEAL || !seal.verify()) return null
        val sealKey = nostrCrypto.nip44ConversationKey(me.privateKey, seal.pubkey.hexToBytes())
        val rumor = NostrEvent.fromJson(nostrCrypto.nip44Decrypt(seal.content, sealKey)) ?: return null
        if (rumor.pubkey != seal.pubkey || !rumor.hasValidId()) return null
        rumor
    }.getOrNull()
}
