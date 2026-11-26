/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.crypto

import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun platformNostrCrypto(): NostrCrypto = JvmNostrCrypto

/**
 * Implementación de [NostrCrypto] con la JVM y BouncyCastle (aritmética de secp256k1).
 * El esquema BIP-340 y NIP-44 v2 están escritos aquí; la curva la hace BouncyCastle.
 */
internal object JvmNostrCrypto : NostrCrypto {

    private val random: SecureRandom by lazy { SecureRandom() }
    private val params = CustomNamedCurves.getByName("secp256k1")
    private val curve = params.curve
    private val g: ECPoint = params.g
    private val order: BigInteger = params.n
    private val fieldPrime: BigInteger = curve.field.characteristic
    private val nip44Salt = "nip44-v2".encodeToByteArray()

    // --- básicos ---------------------------------------------------------

    override fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    override fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    private fun hmac(algorithm: String, key: ByteArray, vararg data: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, algorithm))
        data.forEach { mac.update(it) }
        return mac.doFinal()
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = hmac("HmacSHA256", key, data)

    override fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray = hmac("HmacSHA512", key, data)

    /** PBKDF2-HMAC-SHA512 a mano: BIP-39 y el código de vincular exigen los bytes exactos de la contraseña. */
    override fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA512"))
        val blocks = (bytes + 63) / 64
        val out = ByteArray(blocks * 64)
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte(),
                ),
            )
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * 64, 64)
        }
        return out.copyOf(bytes)
    }

    override fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plain)
    }

    override fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    // --- secp256k1 / BIP-340 ---------------------------------------------

    override fun isValidPrivateKey(key: ByteArray): Boolean {
        if (key.size != 32) return false
        val d = BigInteger(1, key)
        return d.signum() > 0 && d < order
    }

    override fun pubKeyXOnly(priv: ByteArray): ByteArray =
        g.multiply(BigInteger(1, priv)).normalize().affineXCoord.encoded

    override fun compressedPubKey(priv: ByteArray): ByteArray =
        g.multiply(BigInteger(1, priv)).normalize().getEncoded(true)

    override fun addModOrder(a: ByteArray, b: ByteArray): ByteArray =
        to32(BigInteger(1, a).add(BigInteger(1, b)).mod(order))

    private fun to32(value: BigInteger): ByteArray = BigIntegers.asUnsignedByteArray(32, value)

    /** Punto con la y par cuya x es [x] (lift_x de BIP-340), o null si no está en la curva. */
    private fun liftX(x: ByteArray): ECPoint? {
        if (x.size != 32 || BigInteger(1, x) >= fieldPrime) return null
        return runCatching { curve.decodePoint(byteArrayOf(0x02) + x).normalize() }.getOrNull()
    }

    private fun taggedHash(tag: String, vararg data: ByteArray): ByteArray {
        val tagHash = sha256(tag.encodeToByteArray())
        return sha256(tagHash, tagHash, *data)
    }

    private fun ECPoint.hasEvenY(): Boolean = !affineYCoord.toBigInteger().testBit(0)

    override fun signSchnorr(msg32: ByteArray, priv: ByteArray): ByteArray {
        require(msg32.size == 32) { "el mensaje debe medir 32 bytes" }
        var d = BigInteger(1, priv)
        require(d.signum() > 0 && d < order) { "clave privada inválida" }
        val p = g.multiply(d).normalize()
        if (!p.hasEvenY()) d = order.subtract(d)
        val px = p.affineXCoord.encoded

        val dBytes = to32(d)
        val auxHash = taggedHash("BIP0340/aux", randomBytes(32))
        val t = ByteArray(32) { i -> (dBytes[i].toInt() xor auxHash[i].toInt()).toByte() }
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", t, px, msg32)).mod(order)
        check(k0.signum() != 0) { "nonce nulo" }
        val r = g.multiply(k0).normalize()
        val k = if (r.hasEvenY()) k0 else order.subtract(k0)
        val rx = r.affineXCoord.encoded
        val e = BigInteger(1, taggedHash("BIP0340/challenge", rx, px, msg32)).mod(order)
        val s = k.add(e.multiply(d)).mod(order)
        return rx + to32(s)
    }

    override fun verifySchnorr(sig: ByteArray, msg32: ByteArray, pubX: ByteArray): Boolean {
        if (msg32.size != 32 || sig.size != 64) return false
        return runCatching {
            val p = liftX(pubX) ?: return@runCatching false
            val rBytes = sig.copyOfRange(0, 32)
            val r = BigInteger(1, rBytes)
            val s = BigInteger(1, sig.copyOfRange(32, 64))
            if (r >= fieldPrime || s >= order) return@runCatching false
            val e = BigInteger(1, taggedHash("BIP0340/challenge", rBytes, pubX, msg32)).mod(order)
            // R = s·G − e·P
            val point = ECAlgorithms.sumOfTwoMultiplies(g, s, p, order.subtract(e)).normalize()
            !point.isInfinity && point.hasEvenY() && point.affineXCoord.toBigInteger() == r
        }.getOrDefault(false)
    }

    // --- NIP-44 v2 ---------------------------------------------------------

    override fun nip44ConversationKey(priv: ByteArray, pubX: ByteArray): ByteArray {
        val p = requireNotNull(liftX(pubX)) { "clave pública inválida" }
        val shared = p.multiply(BigInteger(1, priv)).normalize().affineXCoord.encoded
        return hmac("HmacSHA256", nip44Salt, shared)
    }

    override fun nip44Encrypt(plain: String, conversationKey: ByteArray): String {
        val nonce = randomBytes(32)
        val (chachaKey, chachaNonce, hmacKey) = messageKeys(conversationKey, nonce)
        val ciphertext = chacha20(chachaKey, chachaNonce, pad(plain))
        val mac = hmac("HmacSHA256", hmacKey, nonce, ciphertext)
        return Base64.getEncoder().encodeToString(byteArrayOf(2) + nonce + ciphertext + mac)
    }

    override fun nip44Decrypt(payload: String, conversationKey: ByteArray): String {
        require(payload.isNotEmpty() && payload[0] != '#') { "versión de cifrado no soportada" }
        require(payload.length in 132..87472) { "longitud de mensaje inválida" }
        val data = Base64.getDecoder().decode(payload)
        require(data.size in 99..65603) { "longitud de datos inválida" }
        require(data[0].toInt() == 2) { "versión ${data[0]} no soportada" }
        val nonce = data.copyOfRange(1, 33)
        val ciphertext = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val (chachaKey, chachaNonce, hmacKey) = messageKeys(conversationKey, nonce)
        val expected = hmac("HmacSHA256", hmacKey, nonce, ciphertext)
        require(constantTimeEquals(expected, mac)) { "MAC inválido" }
        return unpad(chacha20(chachaKey, chachaNonce, ciphertext))
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            previous = hmac("HmacSHA256", prk, previous, info, byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, length - written)
            System.arraycopy(previous, 0, out, written, n)
            written += n
            counter++
        }
        return out
    }

    private fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        require(conversationKey.size == 32 && nonce.size == 32)
        val keys = hkdfExpand(conversationKey, nonce, 76)
        return Triple(keys.copyOfRange(0, 32), keys.copyOfRange(32, 44), keys.copyOfRange(44, 76))
    }

    private fun calcPaddedLength(unpaddedLength: Int): Int {
        if (unpaddedLength <= 32) return 32
        val nextPower = 1 shl (31 - Integer.numberOfLeadingZeros(unpaddedLength - 1) + 1)
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((unpaddedLength - 1) / chunk + 1)
    }

    private fun pad(plaintext: String): ByteArray {
        val bytes = plaintext.encodeToByteArray()
        require(bytes.size in 1..65535) { "mensaje vacío o demasiado largo" }
        val out = ByteArray(2 + calcPaddedLength(bytes.size))
        out[0] = (bytes.size ushr 8).toByte()
        out[1] = bytes.size.toByte()
        System.arraycopy(bytes, 0, out, 2, bytes.size)
        return out
    }

    private fun unpad(padded: ByteArray): String {
        val length = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        require(length > 0 && padded.size == 2 + calcPaddedLength(length)) { "relleno inválido" }
        return String(padded, 2, length, Charsets.UTF_8)
    }

    /** ChaCha20 (RFC 8439) con contador inicial 0, tal como lo usa NIP-44. */
    private fun chacha20(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = le32(key, i * 4)
        state[12] = 0
        for (i in 0 until 3) state[13 + i] = le32(nonce, i * 4)

        val out = ByteArray(input.size)
        val block = ByteArray(64)
        val working = IntArray(16)
        var offset = 0
        while (offset < input.size) {
            state.copyInto(working)
            repeat(10) {
                quarter(working, 0, 4, 8, 12)
                quarter(working, 1, 5, 9, 13)
                quarter(working, 2, 6, 10, 14)
                quarter(working, 3, 7, 11, 15)
                quarter(working, 0, 5, 10, 15)
                quarter(working, 1, 6, 11, 12)
                quarter(working, 2, 7, 8, 13)
                quarter(working, 3, 4, 9, 14)
            }
            for (i in 0 until 16) {
                val v = working[i] + state[i]
                block[i * 4] = v.toByte()
                block[i * 4 + 1] = (v ushr 8).toByte()
                block[i * 4 + 2] = (v ushr 16).toByte()
                block[i * 4 + 3] = (v ushr 24).toByte()
            }
            val n = minOf(64, input.size - offset)
            for (i in 0 until n) out[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
            offset += n
            state[12]++
        }
        return out
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
    }
}
