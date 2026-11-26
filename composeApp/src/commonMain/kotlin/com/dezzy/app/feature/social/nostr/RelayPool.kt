/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social.nostr

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Conexiones WebSocket a relés Nostr públicos.
 *
 * Los relés son servidores de terceros, gratuitos e intercambiables: sólo guardan y reenvían
 * eventos firmados. Dezzy no tiene servidor propio: si un relé cae, se usan los demás.
 *
 * Todo el estado vive en un único hilo lógico ([loop]); las llamadas públicas saltan a él.
 */
class RelayPool(http: HttpClient) {

    private val client: HttpClient = http.config {
        install(WebSockets) { pingIntervalMillis = 25_000 }
    }

    private val loop = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + loop)

    private val connections = HashMap<String, Relay>()
    private val subscriptions = HashMap<String, Sub>()
    private val okWaiters = HashMap<String, Channel<Boolean>>()
    private val verified = HashMap<String, Boolean>()

    private val _connected = MutableStateFlow(0)

    /** Cuántos relés hay conectados ahora mismo (para el chip «N relés conectados»). */
    val connectedCount: StateFlow<Int> = _connected.asStateFlow()

    private class Sub(
        val id: String,
        val filters: List<JsonObject>,
        val urls: Set<String>,
        val onEvent: (NostrEvent) -> Unit,
        val onEose: (String) -> Unit,
    ) {
        val seen = HashSet<String>()

        fun request(): String = buildJsonArray {
            add(JsonPrimitive("REQ"))
            add(JsonPrimitive(id))
            filters.forEach { add(it) }
        }.toString()
    }

    private class Relay(val url: String) {
        var session: DefaultClientWebSocketSession? = null
        var open = false
        var keep = false
        var job: Job? = null
        val queue = ArrayDeque<String>()
    }

    // --- estado (sólo se toca en [loop]) -----------------------------------

    private fun relay(url: String, keep: Boolean = false): Relay {
        val r = connections.getOrPut(url) { Relay(url) }
        if (keep) r.keep = true
        connect(r)
        return r
    }

    private fun needed(r: Relay): Boolean =
        r.keep || r.queue.isNotEmpty() || subscriptions.values.any { r.url in it.urls }

    private fun updateConnected() {
        _connected.value = connections.values.count { it.open }
    }

    private fun connect(r: Relay) {
        if (r.job?.isActive == true) return
        r.job = scope.launch {
            var attempts = 0
            while (isActive && needed(r)) {
                try {
                    client.webSocket(
                        r.url,
                        request = {
                            timeout {
                                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                                connectTimeoutMillis = 10_000
                            }
                        },
                    ) {
                        val session = this
                        withContext(loop) {
                            r.session = session
                            r.open = true
                            attempts = 0
                            updateConnected()
                            subscriptions.values.filter { r.url in it.urls }.forEach {
                                session.outgoing.trySend(Frame.Text(it.request()))
                            }
                            while (true) {
                                val next = r.queue.removeFirstOrNull() ?: break
                                session.outgoing.trySend(Frame.Text(next))
                            }
                        }
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                withContext(loop) { handleMessage(r, text) }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Relé inalcanzable: se reintenta con espera.
                }
                r.open = false
                r.session = null
                updateConnected()
                // Para las consultas en curso, un relé caído cuenta como «terminado».
                subscriptions.values.filter { r.url in it.urls }.forEach { it.onEose(r.url) }
                if (!needed(r)) break
                val wait = (1000L shl attempts.coerceAtMost(6)).coerceAtMost(60_000L)
                attempts++
                delay(wait)
            }
        }
    }

    private fun send(r: Relay, message: String) {
        val s = r.session
        if (r.open && s != null && s.outgoing.trySend(Frame.Text(message)).isSuccess) return
        r.queue.addLast(message)
        while (r.queue.size > 200) r.queue.removeFirst()
        connect(r)
    }

    private fun sendIfOpen(r: Relay, message: String) {
        if (r.open) r.session?.outgoing?.trySend(Frame.Text(message))
    }

    private suspend fun handleMessage(r: Relay, text: String) {
        try {
            val message = runCatching { NostrEvent.json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return
            when (message.getOrNull(0)?.jsonPrimitive?.content) {
                "EVENT" -> {
                    val subId = message.getOrNull(1)?.jsonPrimitive?.content ?: return
                    val sub = subscriptions[subId] ?: return
                    val obj = message.getOrNull(2) as? JsonObject ?: return
                    val event = NostrEvent.fromJson(obj) ?: return
                    if (!sub.seen.add(event.id)) return
                    if (!isAuthenticInternal(event)) return
                    if (subscriptions[subId] === sub) sub.onEvent(event)
                }
                "EOSE", "CLOSED" -> {
                    val sub = subscriptions[message.getOrNull(1)?.jsonPrimitive?.content ?: return] ?: return
                    sub.onEose(r.url)
                }
                "OK" -> {
                    val id = message.getOrNull(1)?.jsonPrimitive?.content ?: return
                    val accepted = message.getOrNull(2)?.jsonPrimitive?.booleanOrNull ?: false
                    okWaiters[id]?.trySend(accepted)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Mensaje raro de un relé: se ignora.
        }
    }

    /** Comprueba id y firma; recuerda los ya verificados para no repetir cálculos. */
    private suspend fun isAuthenticInternal(event: NostrEvent): Boolean {
        verified[event.id]?.let { return it }
        val ok = withContext(Dispatchers.Default) { event.verify() }
        if (verified.size > 8000) verified.clear()
        verified[event.id] = ok
        return ok
    }

    /** Comprueba id y firma de un evento (con caché). */
    suspend fun isAuthentic(event: NostrEvent): Boolean = withContext(loop) { isAuthenticInternal(event) }

    private fun open(
        filters: List<JsonObject>,
        relayUrls: Collection<String>,
        onEvent: (NostrEvent) -> Unit,
        onEose: (String) -> Unit,
    ): String {
        val id = Random.nextLong().toULong().toString(16).padStart(16, '0').take(16)
        val sub = Sub(id, filters, relayUrls.toSet(), onEvent, onEose)
        subscriptions[id] = sub
        sub.urls.forEach { url ->
            val r = relay(url)
            if (r.open) sendIfOpen(r, sub.request())
        }
        return id
    }

    private fun close(subId: String) {
        val sub = subscriptions.remove(subId) ?: return
        val message = JsonArray(listOf(JsonPrimitive("CLOSE"), JsonPrimitive(subId))).toString()
        sub.urls.forEach { url -> connections[url]?.let { sendIfOpen(it, message) } }
    }

    // --- API pública ---------------------------------------------------------

    /** Abre las conexiones por adelantado (por ejemplo al entrar en Social). */
    fun warmUp(urls: Collection<String> = DEFAULT_RELAYS) {
        scope.launch { urls.forEach { relay(it, keep = true) } }
    }

    /**
     * Consulta puntual: junta lo que respondan los relés hasta que todos terminan (EOSE),
     * hasta 1,5 s después del primero que termina, o hasta [timeoutMs].
     */
    suspend fun query(
        filters: List<JsonObject>,
        relays: List<String> = DEFAULT_RELAYS,
        timeoutMs: Long = 6000,
    ): List<NostrEvent> {
        if (filters.isEmpty() || relays.isEmpty()) return emptyList()
        val results = HashMap<String, NostrEvent>()
        val pending = HashSet<String>(relays)
        val done = CompletableDeferred<Unit>()
        val firstEose = CompletableDeferred<Unit>()
        val subId = withContext(loop) {
            open(
                filters = filters,
                relayUrls = relays,
                onEvent = { event -> results[event.id] = event },
                onEose = { url ->
                    if (pending.remove(url)) {
                        firstEose.complete(Unit)
                        if (pending.isEmpty()) done.complete(Unit)
                    }
                },
            )
        }
        val grace = scope.launch {
            firstEose.await()
            delay(1500)
            done.complete(Unit)
        }
        try {
            withTimeoutOrNull(timeoutMs) { done.await() }
        } finally {
            grace.cancel()
            withContext(NonCancellable + loop) { close(subId) }
        }
        return withContext(loop) { results.values.sortedByDescending { it.created_at } }
    }

    /** Suscripción en vivo: emite cada evento nuevo (sin duplicados por id) hasta que se deja de recolectar. */
    fun subscribe(filters: List<JsonObject>, relays: List<String> = DEFAULT_RELAYS): Flow<NostrEvent> = callbackFlow {
        val subId = withContext(loop) {
            open(filters, relays, onEvent = { event -> trySend(event) }, onEose = { })
        }
        awaitClose { scope.launch { close(subId) } }
    }

    /**
     * Publica un evento. Devuelve cuántos relés lo aceptaron antes de [timeoutMs]
     * (los que no estaban conectados lo reciben en cuanto conecten).
     */
    suspend fun publish(event: NostrEvent, relays: List<String> = DEFAULT_RELAYS, timeoutMs: Long = 6000): Int {
        val targets = relays.toSet()
        if (targets.isEmpty()) return 0
        val answers = Channel<Boolean>(Channel.UNLIMITED)
        val message = buildJsonArray {
            add(JsonPrimitive("EVENT"))
            add(event.toJsonObject())
        }.toString()
        withContext(loop) {
            okWaiters[event.id] = answers
            targets.forEach { send(relay(it), message) }
        }
        var accepted = 0
        var answered = 0
        try {
            withTimeoutOrNull(timeoutMs) {
                while (answered < targets.size) {
                    if (answers.receive()) accepted++
                    answered++
                    if (accepted >= 2) break
                }
            }
        } finally {
            withContext(NonCancellable + loop) { okWaiters.remove(event.id) }
        }
        return accepted
    }
}
