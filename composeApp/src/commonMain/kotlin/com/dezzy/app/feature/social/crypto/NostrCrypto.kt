/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.crypto

/**
 * Criptografía que necesita la red social (Nostr): hashes, firmas Schnorr BIP-340 sobre secp256k1,
 * cifrado NIP-44 v2 y derivación de claves. La implementación (BouncyCastle) vive en `jvmShared`.
 */
interface NostrCrypto {
    fun randomBytes(n: Int): ByteArray
    fun sha256(data: ByteArray): ByteArray
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    fun pubKeyXOnly(priv: ByteArray): ByteArray
    fun signSchnorr(msg32: ByteArray, priv: ByteArray): ByteArray
    fun verifySchnorr(sig: ByteArray, msg32: ByteArray, pubX: ByteArray): Boolean
    fun nip44ConversationKey(priv: ByteArray, pubX: ByteArray): ByteArray
    fun nip44Encrypt(plain: String, conversationKey: ByteArray): String
    fun nip44Decrypt(payload: String, conversationKey: ByteArray): String
    fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray

    // Extras que necesita la derivación NIP-06 (BIP-32) y la creación de claves.
    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray
    fun compressedPubKey(priv: ByteArray): ByteArray
    fun addModOrder(a: ByteArray, b: ByteArray): ByteArray
    fun isValidPrivateKey(key: ByteArray): Boolean
}

expect fun platformNostrCrypto(): NostrCrypto

/** Instancia única compartida por el resto del paquete. */
internal val nostrCrypto: NostrCrypto by lazy { platformNostrCrypto() }

private const val HEX_CHARS = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX_CHARS[v ushr 4]).append(HEX_CHARS[v and 0x0F])
    }
    return out.toString()
}

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex de longitud impar" }
    return ByteArray(length / 2) { i ->
        val hi = HEX_CHARS.indexOf(this[i * 2].lowercaseChar())
        val lo = HEX_CHARS.indexOf(this[i * 2 + 1].lowercaseChar())
        require(hi >= 0 && lo >= 0) { "hex inválido" }
        ((hi shl 4) or lo).toByte()
    }
}

internal fun String.isHex64(): Boolean = length == 64 && all { it in '0'..'9' || it in 'a'..'f' }

/** Comparación en tiempo constante (para MAC). */
internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
}
