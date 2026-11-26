/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.serialization.Serializable

@Serializable
enum class AudioQuality {
    AUTO,
    LOW,
    HIGH
}

@Serializable
enum class StreamPriority {
    PLAYBACK,
    PRELOAD,
    DOWNLOAD
}

@Serializable
data class StreamAudio(
    val videoId: String,
    val url: String,
    val mimeType: String,
    val codec: String,
    val bitrateKbps: Int,
    val contentLength: Long,
    val expiresAtMillis: Long,
    val clientName: String,
    val loudnessDb: Double? = null
)
