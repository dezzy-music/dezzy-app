/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.math.roundToInt

/**
 * Resuelve la URL de audio de una canción. Mismo método que Dezzy Desktop:
 *
 * - Una sesión anónima (6 h) y un PoToken real de BotGuard ligado a su visitorData.
 * - Carrera de clientes: el que ganó la última vez sale primero con 1,5 s de ventaja;
 *   si falla o tarda, salen los demás a la vez y gana el primero con audio válido.
 * - Sonda de un byte pasado el primer MB; un cliente+token que ya pasó la sonda se
 *   da por bueno 15 min.
 * - Peticiones duplicadas de la misma canción esperan a la misma resolución.
 * - Si nada sirve, se rehacen sesión y token (o BotGuard entero) y se reintenta una vez.
 * - Plazo total de 35 s: si algo se cuelga, la siguiente petición empieza de cero.
 *
 * La reproducción nunca espera detrás de precargas o descargas: éstas van con dos
 * plazas como mucho cada una.
 */
class StreamUrlResolver(
    private val engine: StreamEngine,
    private val probe: StreamProbe,
    private val cache: StreamCache = InMemoryStreamCache(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val sessionMutex = Mutex()
    private var session: Session? = null

    private val stateMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<StreamAudio>>()
    private val probeTrust = mutableMapOf<String, Long>()
    private var lastWinner: String? = null

    private val preloadSlots = Semaphore(BACKGROUND_SLOTS)
    private val downloadSlots = Semaphore(BACKGROUND_SLOTS)

    suspend fun resolveStream(
        videoId: String,
        priority: StreamPriority = StreamPriority.PLAYBACK,
        quality: AudioQuality = AudioQuality.AUTO,
        force: Boolean = false,
    ): Result<StreamAudio> = try {
        require(VIDEO_ID.matches(videoId)) { "videoId no válido: $videoId" }
        Result.success(resolve(videoId, priority, quality, force))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** Resuelve en segundo plano para que la siguiente canción arranque al instante. */
    fun prefetch(videoId: String, quality: AudioQuality = AudioQuality.AUTO) {
        scope.launch { resolveStream(videoId, StreamPriority.PRELOAD, quality) }
    }

    /** Tira lo resuelto de una canción (p. ej. si su URL dio 403 al reproducir). */
    suspend fun invalidate(videoId: String) {
        cache.remove(videoId)
        stateMutex.withLock { probeTrust.clear() }
    }

    suspend fun clearCache() {
        cache.clear()
        stateMutex.withLock { probeTrust.clear() }
    }

    /** Tira la sesión anónima (visitorData) y el PoToken: el siguiente tema crea otros nuevos. */
    suspend fun resetSession() = resetStreaming(hard = false)

    private suspend fun resolve(videoId: String, priority: StreamPriority, quality: AudioQuality, force: Boolean): StreamAudio {
        if (force) invalidate(videoId) else cache.get(videoId, quality, now())?.let { return it }

        val key = "$videoId|${quality.name}"
        val pending = stateMutex.withLock {
            inFlight[key] ?: scope.async {
                try {
                    val audio = gate(priority) {
                        timed(RESOLVE_DEADLINE_MS, "resolver la canción") { resolveFresh(videoId, quality, retried = false) }
                    }
                    cache.put(quality, audio)
                    audio
                } finally {
                    withContext(NonCancellable) { stateMutex.withLock { inFlight.remove(key) } }
                }
            }.also { inFlight[key] = it }
        }
        return pending.await()
    }

    private suspend fun <T> gate(priority: StreamPriority, block: suspend () -> T): T = when (priority) {
        StreamPriority.PLAYBACK -> block()
        StreamPriority.PRELOAD -> preloadSlots.withPermit { block() }
        StreamPriority.DOWNLOAD -> downloadSlots.withPermit { block() }
    }

    // Sesión y token ---------------------------------------------------------

    private class Session(val visitorData: String, val createdAt: Long)

    private suspend fun visitorData(): String = sessionMutex.withLock {
        val current = session
        if (current != null && now() - current.createdAt < SESSION_TTL_MS) return@withLock current.visitorData
        val visitorData = timed(SESSION_TIMEOUT_MS, "crear la sesión") { engine.createSession() }
        session = Session(visitorData, now())
        stateMutex.withLock { probeTrust.clear() }
        visitorData
    }

    /** Sesión + PoToken; si fallan (motor muerto, red caída), se rehacen una vez desde cero. */
    private suspend fun sessionAndToken(): String {
        suspend fun get(): String = engine.mintPoToken(visitorData())
        return try {
            get()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            resetStreaming(hard = true)
            get()
        }
    }

    private suspend fun resetStreaming(hard: Boolean) {
        stateMutex.withLock { probeTrust.clear() }
        sessionMutex.withLock { session = null }
        engine.reset(hard)
    }

    // Resolución -------------------------------------------------------------

    private suspend fun resolveFresh(videoId: String, quality: AudioQuality, retried: Boolean): StreamAudio {
        val pot = sessionAndToken()
        val errors = mutableListOf<String>()
        val clients = stateMutex.withLock { lastWinner }
            ?.let { winner -> listOf(winner) + CLIENTS.filter { it != winner } }
            ?: CLIENTS
        val winner = race(clients, errors) { client -> tryClient(videoId, client, pot, quality) }
        if (winner != null) {
            stateMutex.withLock { lastWinner = winner.clientName }
            return winner
        }
        if (!retried) {
            // Token o sesión caducados (o BotGuard colgado): se rehacen una vez.
            resetStreaming(hard = errors.any { STUCK.containsMatchIn(it) })
            return resolveFresh(videoId, quality, retried = true)
        }
        throw StreamEngineException("No se pudo obtener el audio (${errors.joinToString("; ")})")
    }

    private class Outcome(val audio: StreamAudio?, val error: String?)

    /**
     * El primer cliente sale solo; si falla o pasan [HEAD_START_MS], salen todos los demás.
     * Gana el primero que devuelve audio y los lentos se cancelan.
     */
    private suspend fun race(
        clients: List<String>,
        errors: MutableList<String>,
        attempt: suspend (String) -> StreamAudio?,
    ): StreamAudio? = coroutineScope {
        val results = Channel<Outcome>(Channel.UNLIMITED)
        var running = 0
        var restStarted = false

        fun start(client: String) {
            running++
            launch {
                val outcome = try {
                    val audio = attempt(client)
                    Outcome(audio, if (audio == null) "$client: sin audio válido" else null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Outcome(null, "$client: ${e.message ?: e::class.simpleName}")
                }
                results.send(outcome)
            }
        }

        fun startRest() {
            if (restStarted) return
            restStarted = true
            clients.drop(1).forEach(::start)
        }

        start(clients.first())
        var winner: StreamAudio? = null
        while (running > 0) {
            val outcome = if (restStarted) {
                results.receive()
            } else {
                withTimeoutOrNull(HEAD_START_MS) { results.receive() }
            }
            if (outcome == null) {
                // Pasó la ventaja del primero sin respuesta: salen los demás.
                startRest()
                continue
            }
            running--
            outcome.error?.let { errors += it }
            if (outcome.audio != null) {
                winner = outcome.audio
                break
            }
            startRest()
        }
        coroutineContext.cancelChildren()
        winner
    }

    private suspend fun tryClient(videoId: String, client: String, pot: String, quality: AudioQuality): StreamAudio? {
        val formats = timed(INFO_TIMEOUT_MS, client) { engine.audioFormats(videoId, client, pot) }
            .sortedByDescending { it.bitrate }
        val format = pick(formats, quality) ?: return null

        val trustKey = "$client|$pot"
        val trusted = stateMutex.withLock { (probeTrust[trustKey] ?: 0L) > now() }
        if (!trusted) {
            if (!probe.works(format.url, format.contentLength)) return null
            stateMutex.withLock { probeTrust[trustKey] = now() + PROBE_TRUST_MS }
        }

        val codec = CODEC.find(format.mimeType)?.groupValues?.get(1)
            ?: format.mimeType.substringAfter('/', "").substringBefore(';')
        return StreamAudio(
            videoId = videoId,
            url = format.url,
            mimeType = format.mimeType.substringBefore(';').trim().ifEmpty { "audio/webm" },
            codec = (if (codec.startsWith("mp4a")) "AAC" else codec).uppercase(),
            bitrateKbps = (format.averageBitrate / 1000.0).roundToInt(),
            contentLength = format.contentLength,
            expiresAtMillis = expiryOf(format.url),
            clientName = client,
            loudnessDb = format.loudnessDb,
        )
    }

    /**
     * Automática: el de más bitrate. Baja: el más ligero. Alta: el mejor por debajo de
     * 200 kbps de media (el pico del mejor Opus pasa de 200 y hacía caer a uno peor).
     */
    private fun pick(sorted: List<EngineAudioFormat>, quality: AudioQuality): EngineAudioFormat? = when (quality) {
        AudioQuality.AUTO -> sorted.firstOrNull()
        AudioQuality.LOW -> sorted.lastOrNull()
        AudioQuality.HIGH -> sorted.firstOrNull { it.averageBitrate <= 200_000 } ?: sorted.firstOrNull()
    }

    private fun expiryOf(url: String): Long {
        val expire = EXPIRE.find(url)?.groupValues?.get(1)?.toLongOrNull()
        return if (expire != null && expire > 0) expire * 1000 - EXPIRY_MARGIN_MS else now() + 60 * 60 * 1000L
    }

    private companion object {
        val CLIENTS = listOf("TV_SIMPLY", "YTMUSIC", "MWEB", "IOS")
        val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        val STUCK = Regex("BotGuard|tiempo agotado|cerrado", RegexOption.IGNORE_CASE)
        val CODEC = Regex("codecs=\"?([a-z0-9]+)", RegexOption.IGNORE_CASE)
        val EXPIRE = Regex("[?&]expire=(\\d+)")

        const val SESSION_TTL_MS = 6 * 60 * 60 * 1000L
        const val SESSION_TIMEOUT_MS = 12_000L
        const val INFO_TIMEOUT_MS = 8_000L
        const val RESOLVE_DEADLINE_MS = 35_000L
        const val HEAD_START_MS = 1_500L
        const val PROBE_TRUST_MS = 15 * 60 * 1000L
        const val EXPIRY_MARGIN_MS = 5 * 60 * 1000L
        const val BACKGROUND_SLOTS = 2
    }
}

/** Como withTimeout, pero falla con un error normal (no con una cancelación) que dice dónde. */
internal suspend fun <T : Any> timed(ms: Long, label: String, block: suspend () -> T): T =
    withTimeoutOrNull(ms) { block() } ?: throw StreamEngineException("tiempo agotado en $label")
