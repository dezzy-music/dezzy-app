/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.crypto

/**
 * Frases de respaldo de 12 palabras (BIP-39, lista oficial en inglés) y derivación de la
 * clave Nostr según NIP-06 (ruta m/44'/1237'/0'/0/0). Así las mismas 12 palabras abren la
 * cuenta en Dezzy y en cualquier otra app Nostr compatible.
 *
 * Las palabras son ASCII, así que la normalización NFKD del estándar no cambia nada.
 */
object Bip39 {

    /** Genera 12 palabras nuevas (128 bits de entropía + 4 de control). */
    fun generate(crypto: NostrCrypto = nostrCrypto): List<String> = fromEntropy(crypto, crypto.randomBytes(16))

    fun fromEntropy(crypto: NostrCrypto, entropy: ByteArray): List<String> {
        require(entropy.size == 16)
        val list = BIP39_ENGLISH
        val checksum = crypto.sha256(entropy)[0].toInt() and 0xF0
        val bits = BooleanArray(132)
        for (i in 0 until 128) bits[i] = (entropy[i / 8].toInt() shr (7 - i % 8)) and 1 == 1
        for (i in 0 until 4) bits[128 + i] = (checksum shr (7 - i)) and 1 == 1
        return List(12) { w ->
            var index = 0
            for (b in 0 until 11) index = (index shl 1) or (if (bits[w * 11 + b]) 1 else 0)
            list[index]
        }
    }

    /** Normaliza lo que escribe el usuario: minúsculas, sin espacios dobles ni comas. */
    fun normalize(input: String): List<String> =
        input.trim().lowercase().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }

    /** true si son 12 palabras de la lista y la suma de control cuadra. */
    fun isValid(crypto: NostrCrypto, mnemonic: List<String>): Boolean {
        if (mnemonic.size != 12) return false
        val list = BIP39_ENGLISH
        val indices = mnemonic.map { list.binarySearch(it) }
        if (indices.any { it < 0 }) return false
        val bits = BooleanArray(132)
        indices.forEachIndexed { w, index ->
            for (b in 0 until 11) bits[w * 11 + b] = (index shr (10 - b)) and 1 == 1
        }
        val entropy = ByteArray(16)
        for (i in 0 until 128) if (bits[i]) entropy[i / 8] = (entropy[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
        val checksum = crypto.sha256(entropy)[0].toInt() and 0xF0
        for (i in 0 until 4) {
            val expected = (checksum shr (7 - i)) and 1 == 1
            if (bits[128 + i] != expected) return false
        }
        return true
    }

    /** Semilla BIP-39 (sin contraseña adicional). */
    fun seed(crypto: NostrCrypto, mnemonic: List<String>): ByteArray =
        crypto.pbkdf2Sha512(
            mnemonic.joinToString(" ").encodeToByteArray(),
            "mnemonic".encodeToByteArray(),
            2048,
            64,
        )

    /** Clave privada Nostr derivada según NIP-06: m/44'/1237'/0'/0/0. */
    fun nostrPrivateKey(crypto: NostrCrypto, mnemonic: List<String>): ByteArray {
        val master = crypto.hmacSha512("Bitcoin seed".encodeToByteArray(), seed(crypto, mnemonic))
        var key = master.copyOfRange(0, 32)
        var chain = master.copyOfRange(32, 64)
        val hardened = 0x80000000.toInt()
        val path = intArrayOf(44 or hardened, 1237 or hardened, 0 or hardened, 0, 0)
        for (index in path) {
            val data = if (index and hardened != 0) {
                byteArrayOf(0) + key + ser32(index)
            } else {
                crypto.compressedPubKey(key) + ser32(index)
            }
            val i = crypto.hmacSha512(chain, data)
            key = crypto.addModOrder(i.copyOfRange(0, 32), key)
            chain = i.copyOfRange(32, 64)
        }
        check(crypto.isValidPrivateKey(key)) { "derivación inválida" }
        return key
    }

    private fun ser32(i: Int): ByteArray =
        byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte())
}
