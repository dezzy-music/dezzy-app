/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.serialization.Serializable

/**
 * Lo que habla con YouTube para sacar audio, igual que Dezzy Desktop:
 * sesión anónima con visitorData real, PoToken de BotGuard ligado a ese
 * visitorData y formatos de audio con la URL ya descifrada.
 *
 * En Android lo implementa una WebView oculta (youtubei.js + bgutils-js, MIT).
 */
interface StreamEngine {
    /** Crea (o rehace) la sesión anónima. Devuelve su visitorData. */
    suspend fun createSession(): String

    /** PoToken de BotGuard ligado a [binding] (el visitorData de la sesión). */
    suspend fun mintPoToken(binding: String): String

    /** Formatos de sólo audio de [client], con la URL descifrada y el `pot` ya puesto. */
    suspend fun audioFormats(videoId: String, client: String, poToken: String): List<EngineAudioFormat>

    /** Descarta el minter y los tokens; con [hard], también el motor entero (WebView nueva). */
    fun reset(hard: Boolean)
}

@Serializable
data class EngineAudioFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Long,
    val averageBitrate: Long,
    val contentLength: Long,
    val loudnessDb: Double? = null,
    val url: String,
)

class StreamEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
