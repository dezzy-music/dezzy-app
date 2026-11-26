/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.feature.connect.ConnectService
import com.dezzy.app.feature.social.crypto.NostrCrypto
import com.dezzy.app.feature.social.crypto.constantTimeEquals
import com.dezzy.app.feature.social.crypto.hexToBytes
import com.dezzy.app.feature.social.crypto.toHex
import com.dezzy.app.feature.social.nostr.Kinds
import com.dezzy.app.feature.social.nostr.NostrEvent
import com.dezzy.app.feature.social.nostr.NostrKeys
import com.dezzy.app.feature.social.nostr.RelayPool
import com.dezzy.app.feature.social.nostr.nostrFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.coroutineContext

/** Estado del enlace con el ordenador (para la hoja «Vincular con el ordenador»). */
sealed interface DesktopLinkState {
    data object Idle : DesktopLinkState

    /** Buscando al ordenador que muestra el código. */
    data object Searching : DesktopLinkState

    /** Encontrado: enviando las sesiones. */
    data object Sending : DesktopLinkState

    data object Linked : DesktopLinkState

    data class Failed(val message: String) : DesktopLinkState
}

/**
 * Pasa las sesiones de este dispositivo (YouTube Music, Spotify, identidad social y clave de grupo
 * de Dezzy Connect) a Dezzy en el ordenador, que nunca inicia sesión por su cuenta.
 * Protocolo «dezzy-link-v1»; la otra mitad vive en dezzy-desktop (`src/main/link.ts`) y las dos
 * deben cambiar a la vez.
 *
 * 1. El ordenador muestra un código de 8 caracteres (base32 de Crockford) y un QR con
 *    `dezzy://link?c=CODIGO`. PBKDF2-HMAC-SHA512 (sal «dezzy-link-v1», 100 000 iteraciones,
 *    64 bytes) da `lookup` = hex de los bytes 0..15 y `auth` = bytes 32..63.
 * 2. Cada lado usa una clave Nostr efímera al azar.
 * 3. Eventos de tipo 21337 (efímero): hola (["t", lookup]), oferta, sesiones y acuse; las mac
 *    (HMAC-SHA256 con `auth`) prueban que el otro lado conoce el código.
 */
class DesktopLink(
    private val identity: P2PIdentity,
    private val accounts: AccountStore,
    private val relays: RelayPool,
    private val crypto: NostrCrypto,
    private val connect: ConnectService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private var running: Deferred<Result<String>>? = null

    private val _state = MutableStateFlow<DesktopLinkState>(DesktopLinkState.Idle)
    val state: StateFlow<DesktopLinkState> = _state.asStateFlow()

    /** Lo que se le pasa al ordenador. Sólo vive en memoria mientras se envía. */
    private class Sessions(
        val device: String,
        val youtubeCookie: String?,
        val youtubeName: String?,
        val youtubeHandle: String?,
        val spotifySpDc: String?,
        val socialKey: String?,
        val connectKey: String?,
    )

    /**
     * Acepta el código tal cual lo escriba la persona (minúsculas, guion, O por 0, I o L por 1)
     * o el contenido entero del QR (`dezzy://link?c=…`). Devuelve null si no es un código válido.
     */
    fun parseCode(input: String): String? {
        val raw = Regex("""[?&]c=([^&\s]+)""").find(input)?.groupValues?.get(1) ?: input
        val code = raw.uppercase()
            .replace(Regex("""[\s-]"""), "")
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
        return code.takeIf { it.length == 8 && it.all { c -> c in ALPHABET } }
    }

    /**
     * Envía las sesiones al ordenador que muestra [code] (o el texto del QR). En caso de éxito
     * devuelve un mensaje para mostrar; si falla, la excepción lleva el motivo en español.
     */
    suspend fun link(code: String): Result<String> {
        val parsed = parseCode(code)
            ?: return Result.failure<String>(IllegalArgumentException("El código no es válido. Son 8 letras y números.")).also {
                _state.value = DesktopLinkState.Failed("El código no es válido. Son 8 letras y números.")
            }
        running?.cancel()
        val job = scope.async { run(parsed) }
        running = job
        return try {
            job.await()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            _state.value = DesktopLinkState.Idle
            Result.failure(IllegalStateException("Cancelado."))
        } finally {
            if (running === job) running = null
        }
    }

    /** Cancela el enlace en curso. */
    fun cancel() {
        running?.cancel()
        running = null
        _state.value = DesktopLinkState.Idle
    }

    private suspend fun run(code: String): Result<String> {
        _state.value = DesktopLinkState.Searching
        val sessions = currentSessions()
        relays.warmUp()
        val seed = crypto.pbkdf2Sha512(code.encodeToByteArray(), SALT.encodeToByteArray(), ITERATIONS, 64)
        val lookup = seed.copyOfRange(0, 16).toHex()
        val auth = seed.copyOfRange(32, 64)
        seed.fill(0)
        val keys = NostrKeys.generate()
        return try {
            val outcome = withTimeoutOrNull(TIMEOUT_MS) { exchange(keys, auth, lookup, sessions) }
                ?: Failure("El ordenador no responde. Revisa el código y que Dezzy siga abierto en él.")
            when (outcome) {
                is Failure -> {
                    _state.value = DesktopLinkState.Failed(outcome.message)
                    Result.failure(IllegalStateException(outcome.message))
                }
                else -> {
                    _state.value = DesktopLinkState.Linked
                    Result.success("Sesiones enviadas al ordenador.")
                }
            }
        } finally {
            auth.fill(0)
            keys.privateKey.fill(0)
        }
    }

    private suspend fun currentSessions(): Sessions {
        runCatching { identity.load() }
        val yt = runCatching { accounts.account(SessionProvider.YOUTUBE) }.getOrNull()
        val sp = runCatching { accounts.account(SessionProvider.SPOTIFY) }.getOrNull()
        return Sessions(
            device = p2pDeviceName(),
            youtubeCookie = yt?.secret?.takeIf { "SAPISID" in it },
            youtubeName = yt?.name?.takeIf { it.isNotBlank() },
            youtubeHandle = yt?.handle?.takeIf { it.isNotBlank() },
            spotifySpDc = sp?.secret?.takeIf { it.isNotBlank() },
            socialKey = identity.currentKeys()?.privateKey?.toHex(),
            connectKey = runCatching { connect.groupKeyHex() }.getOrNull(),
        )
    }

    private fun mac(auth: ByteArray, label: String, a: String, b: String): String =
        crypto.hmacSha256(auth, "$label|$a|$b".encodeToByteArray()).toHex()

    private fun sameMac(received: String?, expected: String): Boolean {
        if (received == null || received.length != expected.length) return false
        val bytes = runCatching { received.hexToBytes() }.getOrNull() ?: return false
        return constantTimeEquals(bytes, expected.hexToBytes())
    }

    private fun seal(keys: NostrKeys, peer: String, payload: JsonObject): NostrEvent {
        val key = crypto.nip44ConversationKey(keys.privateKey, peer.hexToBytes())
        return NostrEvent.sign(keys, Kinds.LINK, listOf(listOf("p", peer)), crypto.nip44Encrypt(payload.toString(), key))
    }

    private fun open(keys: NostrKeys, event: NostrEvent): JsonObject? {
        if (event.content.isEmpty() || event.content.length > 96 * 1024) return null
        return runCatching {
            val key = crypto.nip44ConversationKey(keys.privateKey, event.pubkey.hexToBytes())
            json.parseToJsonElement(crypto.nip44Decrypt(event.content, key)).jsonObject
        }.getOrNull()
    }

    private fun JsonObject.str(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private class Failure(val message: String)

    private object Success

    private suspend fun exchange(keys: NostrKeys, auth: ByteArray, lookup: String, sessions: Sessions): Any = coroutineScope {
        val incoming = Channel<NostrEvent>(Channel.UNLIMITED)
        val filter = nostrFilter(
            kinds = listOf(Kinds.LINK),
            tags = mapOf("#p" to listOf(keys.publicKey)),
            since = NostrEvent.now() - 30,
        )
        val listener = launch { relays.subscribe(listOf(filter)).collect { incoming.send(it) } }
        // Da tiempo a que los relés registren la suscripción antes de saludar.
        delay(700)
        launch { relays.publish(NostrEvent.sign(keys, Kinds.LINK, listOf(listOf("t", lookup)), "")) }

        var desktop: String? = null
        var outcome: Any? = null
        for (event in incoming) {
            val data = open(keys, event) ?: continue
            when (data.str("type")) {
                "offer" -> {
                    if (desktop != null || !sameMac(data.str("mac"), mac(auth, "offer", event.pubkey, keys.publicKey))) continue
                    desktop = event.pubkey
                    _state.value = DesktopLinkState.Sending
                    val payload = buildJsonObject {
                        put("v", 1)
                        put("type", "sessions")
                        put("mac", mac(auth, "sessions", keys.publicKey, event.pubkey))
                        put("device", sessions.device)
                        sessions.youtubeCookie?.let { cookie ->
                            putJsonObject("youtube") {
                                put("cookie", cookie)
                                sessions.youtubeName?.let { put("name", it) }
                                sessions.youtubeHandle?.let { put("handle", it) }
                            }
                        }
                        sessions.spotifySpDc?.let { spDc -> putJsonObject("spotify") { put("spDc", spDc) } }
                        sessions.socialKey?.let { sk -> putJsonObject("social") { put("sk", sk) } }
                        sessions.connectKey?.let { key -> putJsonObject("connect") { put("key", key) } }
                    }
                    launch { relays.publish(seal(keys, event.pubkey, payload)) }
                }
                "ack" -> {
                    if (event.pubkey != desktop) continue
                    outcome = if (data["ok"]?.jsonPrimitive?.booleanOrNull == true) {
                        Success
                    } else {
                        Failure(data.str("message") ?: "El ordenador no pudo guardar las sesiones.")
                    }
                    break
                }
            }
        }
        listener.cancel()
        incoming.close()
        coroutineContext.cancelChildren()
        outcome ?: Failure("Se cortó la conexión con el ordenador.")
    }

    private companion object {
        const val SALT = "dezzy-link-v1"
        const val ITERATIONS = 100_000
        const val TIMEOUT_MS = 40_000L
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}
