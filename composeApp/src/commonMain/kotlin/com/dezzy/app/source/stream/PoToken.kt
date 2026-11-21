/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

object PoToken {

    @OptIn(ExperimentalEncodingApi::class)
    fun createColdStartToken(
        contentBinding: String,
        clientState: Int = 1,
        timestampSeconds: Long? = null,
        forcedKeys: ByteArray? = null
    ): String {
        val bindingBytes = contentBinding.encodeToByteArray()
        val ts = timestampSeconds ?: (Clock.System.now().toEpochMilliseconds() / 1000L)
        val keys = forcedKeys ?: byteArrayOf(
            Random.nextInt(256).toByte(),
            Random.nextInt(256).toByte()
        )

        val header = ByteArray(8)
        header[0] = keys[0]
        header[1] = keys[1]
        header[2] = 0.toByte()
        header[3] = clientState.toByte()
        header[4] = ((ts ushr 24) and 0xFF).toByte()
        header[5] = ((ts ushr 16) and 0xFF).toByte()
        header[6] = ((ts ushr 8) and 0xFF).toByte()
        header[7] = (ts and 0xFF).toByte()

        val totalPayloadLen = header.size + bindingBytes.size
        val packet = ByteArray(2 + totalPayloadLen)
        packet[0] = 34.toByte() // 0x22
        packet[1] = totalPayloadLen.toByte()

        header.copyInto(packet, destinationOffset = 2)
        bindingBytes.copyInto(packet, destinationOffset = 2 + header.size)

        // El payload empieza en el offset 2 del paquete
        val keyLen = keys.size
        for (i in keyLen until totalPayloadLen) {
            val keyByte = packet[2 + (i % keyLen)]
            packet[2 + i] = (packet[2 + i].toInt() xor keyByte.toInt()).toByte()
        }

        return Base64.UrlSafe.encode(packet).trimEnd('=')
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeColdStartToken(token: String): DecodedPoToken {
        val padded = when (token.length % 4) {
            2 -> "$token=="
            3 -> "$token="
            else -> token
        }
        val packet = Base64.UrlSafe.decode(padded)
        val payloadLen = packet[1].toInt() and 0xFF
        require(packet.size == 2 + payloadLen) { "Longitud de paquete inválida: ${packet.size} != ${2 + payloadLen}" }

        // Invertir XOR
        val keyLen = 2
        for (i in keyLen until payloadLen) {
            val keyByte = packet[2 + (i % keyLen)]
            packet[2 + i] = (packet[2 + i].toInt() xor keyByte.toInt()).toByte()
        }

        val key0 = packet[2].toInt() and 0xFF
        val key1 = packet[3].toInt() and 0xFF
        val clientState = packet[5].toInt() and 0xFF
        val ts = ((packet[6].toLong() and 0xFF) shl 24) or
            ((packet[7].toLong() and 0xFF) shl 16) or
            ((packet[8].toLong() and 0xFF) shl 8) or
            (packet[9].toLong() and 0xFF)

        val bindingBytes = packet.copyOfRange(10, 2 + payloadLen)
        val contentBinding = bindingBytes.decodeToString()

        return DecodedPoToken(
            contentBinding = contentBinding,
            timestampSeconds = ts,
            clientState = clientState,
            keys = listOf(key0, key1)
        )
    }
}

expect class PlatformPoTokenProvider() {
    suspend fun getPoToken(binding: String): String
}
