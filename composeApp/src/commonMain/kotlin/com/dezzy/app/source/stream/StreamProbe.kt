/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** Comprueba que una URL de audio sirve de verdad. */
fun interface StreamProbe {
    suspend fun works(url: String, contentLength: Long): Boolean
}

/**
 * Pide un solo byte pasado el primer MB: sin PoToken válido YouTube sirve el
 * principio y corta después, así que probar el byte 0 no demuestra nada.
 */
class HttpStreamProbe(private val http: HttpClient) : StreamProbe {
    override suspend fun works(url: String, contentLength: Long): Boolean {
        val start = if (contentLength > PROBE_OFFSET + 1) PROBE_OFFSET else 0L
        return try {
            val response = http.get(url) {
                header(HttpHeaders.Range, "bytes=$start-$start")
                timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS }
            }
            response.readRawBytes()
            response.status == HttpStatusCode.PartialContent || response.status == HttpStatusCode.OK
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val PROBE_OFFSET = 1_200_000L
        const val PROBE_TIMEOUT_MS = 6_000L
    }
}
