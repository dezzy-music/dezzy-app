/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.feature.social.crypto.Bip39
import com.dezzy.app.feature.social.crypto.hexToBytes
import com.dezzy.app.feature.social.crypto.platformNostrCrypto
import com.dezzy.app.feature.social.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vectores oficiales de BIP-340, NIP-44 v2 y NIP-06. */
class NostrCryptoTest {
    private val c = platformNostrCrypto()
    private fun key(last: Int) = ByteArray(32).also { it[31] = last.toByte() }

    @Test fun bip340() {
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", c.pubKeyXOnly(key(3)).toHex())
        val sig = ("e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215" +
            "25f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0").hexToBytes()
        assertTrue(c.verifySchnorr(sig, ByteArray(32), c.pubKeyXOnly(key(3))))
        val msg = c.sha256("hola".encodeToByteArray())
        assertTrue(c.verifySchnorr(c.signSchnorr(msg, key(7)), msg, c.pubKeyXOnly(key(7))))
    }

    @Test fun nip44() {
        val ck = c.nip44ConversationKey(key(1), c.pubKeyXOnly(key(2)))
        assertEquals("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d", ck.toHex())
        val payload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"
        assertEquals("a", c.nip44Decrypt(payload, ck))
        assertEquals("¡Hola, Dezzy! 🎵", c.nip44Decrypt(c.nip44Encrypt("¡Hola, Dezzy! 🎵", ck), ck))
    }

    @Test fun nip06() {
        val words = "leader monkey parrot ring guide accident before fence cannon height naive bean".split(" ")
        val priv = Bip39.nostrPrivateKey(c, words)
        assertEquals("7f7ff03d123792d6ac594bfa67bf6d0c0ab55b6b1fdb6249303fe861f1ccba9a", priv.toHex())
        assertEquals("17162c921dc4d2518f9a101db33695df1afb56ab82f5ff3e5da6eec3ca5cd917", c.pubKeyXOnly(priv).toHex())
    }
}
