/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createPlatformHttpEngine(): HttpClientEngine

class HttpClientFactory {
    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        const val DEFAULT_TIMEOUT_MS = 8000L

        fun create(
            userAgent: String = DEFAULT_USER_AGENT,
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            engine: HttpClientEngine = createPlatformHttpEngine()
        ): HttpClient {
            return HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = false
                        encodeDefaults = true
                    })
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = timeoutMs
                    connectTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }

                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    exponentialDelay()
                }

                defaultRequest {
                    header(HttpHeaders.UserAgent, userAgent)
                }
            }
        }
    }
}
