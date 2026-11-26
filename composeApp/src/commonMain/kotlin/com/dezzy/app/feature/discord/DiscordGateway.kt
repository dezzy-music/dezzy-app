/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.discord

import com.dezzy.app.core.net.HttpClientFactory
import com.dezzy.app.core.net.createPlatformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Conexión al Gateway de Discord (v10) con la cuenta del usuario, sólo para publicar la presencia.
 * Una llamada a [runSession] es una conexión: acaba cuando el socket se cierra o falla.
 */
internal class DiscordGateway {
    /** Cliente propio: con WebSockets y sin tiempos de espera globales (cada petición REST pone el suyo). */
    val client: HttpClient = HttpClient(createPlatformHttpEngine()) {
        install(WebSockets)
        install(HttpTimeout)
        defaultRequest { header(HttpHeaders.UserAgent, HttpClientFactory.DEFAULT_USER_AGENT) }
    }

    /** Resultado de una conexión. */
    class Outcome(val invalidToken: Boolean, val reachedReady: Boolean)

    private val json = Json { ignoreUnknownKeys = true }
    private val socket = MutableStateFlow<DefaultClientWebSocketSession?>(null)
    private val ready = MutableStateFlow(false)

    /** true cuando el Gateway ha aceptado la sesión (READY o RESUMED). */
    val isReady: StateFlow<Boolean> get() = ready

    private var sessionId: String? = null
    private var resumeUrl: String? = null
    private var seq: Long? = null

    /** Cambia la presencia (op 3). Sin conexión lista no hace nada. */
    suspend fun sendPresence(activities: JsonArray) {
        val s = socket.value ?: return
        if (!ready.value) return
        try {
            s.send(Frame.Text(presencePayload(3, activities).toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // El socket se está cerrando: la reconexión volverá a mandar la presencia.
        }
    }

    /** Olvida la sesión guardada (para empezar de cero con otra cuenta). */
    fun forgetSession() {
        sessionId = null
        resumeUrl = null
        seq = null
    }

    suspend fun runSession(token: String, initialActivities: suspend () -> JsonArray): Outcome {
        var invalid = false
        var reached = false
        val url = (resumeUrl?.takeIf { sessionId != null } ?: DEFAULT_URL).trimEnd('/') + "/?v=10&encoding=json"
        try {
            client.webSocket(urlString = url) {
                socket.value = this
                var heartbeat: Job? = null
                var acked = true
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val msg = try {
                            json.parseToJsonElement(frame.readText()).jsonObject
                        } catch (_: Exception) {
                            continue
                        }
                        val op = msg["op"]?.jsonPrimitive?.longOrNull?.toInt()
                        msg["s"]?.jsonPrimitive?.longOrNull?.let { seq = it }
                        when (op) {
                            10 -> {
                                val interval = msg["d"]?.jsonObject?.get("heartbeat_interval")
                                    ?.jsonPrimitive?.longOrNull ?: 41_250L
                                heartbeat?.cancel()
                                acked = true
                                heartbeat = launch {
                                    delay((interval * Random.nextDouble()).toLong())
                                    while (isActive) {
                                        if (!acked) {
                                            close(CloseReason(4000, "heartbeat sin respuesta"))
                                            break
                                        }
                                        acked = false
                                        send(Frame.Text(heartbeatPayload().toString()))
                                        delay(interval)
                                    }
                                }
                                val sid = sessionId
                                val sq = seq
                                if (sid != null && sq != null) {
                                    send(Frame.Text(resumePayload(token, sid, sq).toString()))
                                } else {
                                    send(Frame.Text(identifyPayload(token, initialActivities()).toString()))
                                }
                            }
                            11 -> acked = true
                            1 -> send(Frame.Text(heartbeatPayload().toString()))
                            7 -> {
                                close(CloseReason(4000, "reconnect"))
                                break
                            }
                            9 -> {
                                val resumable = msg["d"]?.jsonPrimitive?.booleanOrNull == true
                                if (!resumable) forgetSession()
                                delay(1_000L + Random.nextLong(4_000L))
                                close(CloseReason(4000, "sesión inválida"))
                                break
                            }
                            0 -> when (msg["t"]?.jsonPrimitive?.contentOrNull) {
                                "READY" -> {
                                    val d = msg["d"]?.jsonObject
                                    sessionId = d?.get("session_id")?.jsonPrimitive?.contentOrNull
                                    resumeUrl = d?.get("resume_gateway_url")?.jsonPrimitive?.contentOrNull
                                    reached = true
                                    ready.value = true
                                }
                                "RESUMED" -> {
                                    reached = true
                                    ready.value = true
                                }
                            }
                        }
                    }
                } finally {
                    heartbeat?.cancel()
                    ready.value = false
                    socket.value = null
                }
                val code = closeReason.await()?.code?.toInt()
                when (code) {
                    4004 -> invalid = true
                    // Sesión no reanudable: empezar de nuevo.
                    4007, 4009 -> forgetSession()
                    // Fallos de configuración que no se arreglan reintentando igual: sesión nueva.
                    4010, 4011, 4012, 4013, 4014 -> forgetSession()
                }
            }
        } catch (e: CancellationException) {
            ready.value = false
            socket.value = null
            throw e
        } catch (_: Exception) {
            ready.value = false
            socket.value = null
        }
        return Outcome(invalidToken = invalid, reachedReady = reached)
    }

    private fun heartbeatPayload(): JsonObject = buildJsonObject {
        put("op", 1)
        val s = seq
        if (s != null) put("d", s) else put("d", JsonNull)
    }

    private fun resumePayload(token: String, sessionId: String, seq: Long): JsonObject = buildJsonObject {
        put("op", 6)
        put("d", buildJsonObject {
            put("token", token)
            put("session_id", sessionId)
            put("seq", seq)
        })
    }

    private fun identifyPayload(token: String, activities: JsonArray): JsonObject = buildJsonObject {
        put("op", 2)
        put("d", buildJsonObject {
            put("token", token)
            put("intents", 0)
            put("compress", false)
            put("properties", buildJsonObject {
                put("os", "Android")
                put("browser", "Discord Android")
                put("device", "Dezzy")
            })
            put("presence", presenceBody(activities))
        })
    }

    private fun presencePayload(op: Int, activities: JsonArray): JsonObject = buildJsonObject {
        put("op", op)
        put("d", presenceBody(activities))
    }

    private fun presenceBody(activities: JsonArray): JsonObject = buildJsonObject {
        put("since", JsonNull)
        put("activities", activities)
        put("status", JsonPrimitive("online"))
        put("afk", false)
    }

    companion object {
        const val DEFAULT_URL = "wss://gateway.discord.gg"
    }
}
