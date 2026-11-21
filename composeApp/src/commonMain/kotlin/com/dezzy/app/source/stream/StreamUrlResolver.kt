/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import com.dezzy.app.source.youtube.YouTubeEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StreamUrlResolver(
    private val httpClient: HttpClient,
    private val poTokenProvider: PlatformPoTokenProvider
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, StreamAudio>()
    private var lastWinner: StreamClientType = StreamClientType.IOS

    companion object {
        private const val EXPIRATION_BUFFER_MS = 5 * 60 * 1000L
    }

    suspend fun resolveStream(
        videoId: String,
        priority: StreamPriority = StreamPriority.PLAYBACK,
        quality: AudioQuality = AudioQuality.AUTO,
        force: Boolean = false
    ): Result<StreamAudio> = runCatching {
        require(videoId.isNotBlank()) { "El videoId no puede estar vacío" }

        val now = Clock.System.now().toEpochMilliseconds()

        if (!force) {
            mutex.withLock {
                val cached = cache[videoId]
                if (cached != null && cached.expiresAtMillis > (now + EXPIRATION_BUFFER_MS)) {
                    return@runCatching cached
                }
            }
        }

        mutex.withLock {
            val cached = cache[videoId]
            if (!force && cached != null && cached.expiresAtMillis > (now + EXPIRATION_BUFFER_MS)) {
                return@withLock cached
            }
            val resolved = resolveInternal(videoId, quality)
            cache[videoId] = resolved
            resolved
        }
    }

    private suspend fun resolveInternal(videoId: String, quality: AudioQuality): StreamAudio {
        val poToken = runCatching { poTokenProvider.getPoToken(videoId) }.getOrDefault(
            PoToken.createColdStartToken(videoId)
        )

        // 1. Probar primero el cliente favorito (por defecto IOS)
        val firstClient = lastWinner
        val firstAttempt = tryResolveClient(firstClient, videoId, poToken, quality)
        if (firstAttempt != null) {
            return firstAttempt
        }

        // 2. Si falló, probar los demás clientes en orden de preferencia
        val remainingClients = StreamClientType.entries.filter { it != firstClient }
        val errors = mutableListOf<String>()
        errors.add("${firstClient.clientName}: sin audio válido")

        for (client in remainingClients) {
            val audio = tryResolveClient(client, videoId, poToken, quality)
            if (audio != null) {
                lastWinner = client
                return audio
            }
            errors.add("${client.clientName}: sin audio válido")
        }

        throw IllegalStateException("No se pudo resolver el audio para $videoId: ${errors.joinToString("; ")}")
    }

    private suspend fun tryResolveClient(
        clientType: StreamClientType,
        videoId: String,
        poToken: String,
        quality: AudioQuality
    ): StreamAudio? {
        return try {
            val payload = clientType.buildPlayerPayload(
                videoId = videoId,
                poToken = poToken
            )

            val endpoint = if (clientType == StreamClientType.TV_SIMPLY) {
                YouTubeEndpoints.YT_PLAYER_URL
            } else {
                YouTubeEndpoints.PLAYER_URL
            }

            val response: JsonObject = httpClient.post(endpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Origin, clientType.origin)
                header(HttpHeaders.Referrer, clientType.referer)
                header(HttpHeaders.UserAgent, clientType.userAgent)
                setBody(payload)
            }.body()

            val status = response["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
            if (status != "OK") return null

            val streamingData = response["streamingData"]?.jsonObject ?: return null
            val rawFormats = streamingData["adaptiveFormats"]?.jsonArray ?: return null

            val audioFormats = rawFormats.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val mime = obj["mimeType"]?.jsonPrimitive?.content ?: ""
                if (!mime.startsWith("audio/")) return@mapNotNull null

                val itag = obj["itag"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val bitrate = obj["bitrate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val avgBitrate = obj["averageBitrate"]?.jsonPrimitive?.content?.toIntOrNull() ?: bitrate
                val contentLength = obj["contentLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val loudnessDb = obj["loudnessDb"]?.jsonPrimitive?.content?.toDoubleOrNull()
                val rawUrl = obj["url"]?.jsonPrimitive?.content

                if (rawUrl != null) {
                    AudioFormatCandidate(
                        itag = itag,
                        mimeType = mime,
                        bitrate = bitrate,
                        averageBitrate = avgBitrate,
                        contentLength = contentLength,
                        loudnessDb = loudnessDb,
                        url = rawUrl
                    )
                } else null
            }

            if (audioFormats.isEmpty()) return null

            val chosenFormat = selectFormat(audioFormats, quality) ?: return null
            var finalUrl = chosenFormat.url
            if (!finalUrl.contains("pot=")) {
                finalUrl += if (finalUrl.contains("?")) "&pot=$poToken" else "?pot=$poToken"
            }

            // Validar con sonda HTTP 206 (Range request)
            if (!probeStream(finalUrl, clientType.userAgent)) {
                return null
            }

            val expiresAt = extractExpiry(finalUrl)
            val codec = extractCodec(chosenFormat.mimeType)

            StreamAudio(
                videoId = videoId,
                url = finalUrl,
                mimeType = chosenFormat.mimeType.split(";")[0].trim(),
                codec = codec,
                bitrateKbps = chosenFormat.averageBitrate / 1000,
                contentLength = chosenFormat.contentLength,
                expiresAtMillis = expiresAt,
                clientName = clientType.clientName,
                loudnessDb = chosenFormat.loudnessDb
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun selectFormat(formats: List<AudioFormatCandidate>, quality: AudioQuality): AudioFormatCandidate? {
        val sorted = formats.sortedByDescending { it.averageBitrate }
        return when (quality) {
            AudioQuality.LOW -> sorted.lastOrNull()
            AudioQuality.HIGH -> sorted.firstOrNull()
            AudioQuality.AUTO -> sorted.firstOrNull { it.averageBitrate in 100_000..180_000 } ?: sorted.firstOrNull()
        }
    }

    private suspend fun probeStream(url: String, userAgent: String): Boolean {
        return try {
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Range, "bytes=0-1024")
            }
            response.status == HttpStatusCode.PartialContent || response.status == HttpStatusCode.OK
        } catch (_: Throwable) {
            false
        }
    }

    private fun extractExpiry(url: String): Long {
        val regex = "[?&]expire=(\\d+)".toRegex()
        val match = regex.find(url)
        val expireSecs = match?.groupValues?.getOrNull(1)?.toLongOrNull()
        val now = Clock.System.now().toEpochMilliseconds()
        return if (expireSecs != null && expireSecs > 0) {
            expireSecs * 1000L - EXPIRATION_BUFFER_MS
        } else {
            now + 3600 * 1000L
        }
    }

    private fun extractCodec(mimeType: String): String {
        val regex = "codecs=\"?([a-zA-Z0-9.]+)\"?".toRegex()
        val match = regex.find(mimeType)?.groupValues?.getOrNull(1)
        return when {
            match != null && match.startsWith("mp4a") -> "AAC"
            match != null -> match.uppercase()
            mimeType.contains("webm") -> "OPUS"
            mimeType.contains("mp4") -> "AAC"
            else -> "UNKNOWN"
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private data class AudioFormatCandidate(
        val itag: Int,
        val mimeType: String,
        val bitrate: Int,
        val averageBitrate: Int,
        val contentLength: Long,
        val loudnessDb: Double?,
        val url: String
    )
}
