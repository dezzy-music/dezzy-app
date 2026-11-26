/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caché de URLs de audio ya resueltas. Las URLs van ligadas a la IP que las
 * pidió: si al reproducir YouTube contesta 403, el reproductor vuelve a pedir
 * con `force = true` y la entrada se tira.
 */
interface StreamCache {
    suspend fun get(videoId: String, quality: AudioQuality, now: Long): StreamAudio?
    suspend fun put(quality: AudioQuality, audio: StreamAudio)
    suspend fun remove(videoId: String)
    suspend fun clear()
}

class InMemoryStreamCache : StreamCache {
    private val mutex = Mutex()
    private val entries = mutableMapOf<Pair<String, AudioQuality>, StreamAudio>()

    override suspend fun get(videoId: String, quality: AudioQuality, now: Long): StreamAudio? = mutex.withLock {
        entries[videoId to quality]?.takeIf { now < it.expiresAtMillis }
    }

    override suspend fun put(quality: AudioQuality, audio: StreamAudio) = mutex.withLock {
        entries[audio.videoId to quality] = audio
    }

    override suspend fun remove(videoId: String) = mutex.withLock {
        entries.keys.removeAll { it.first == videoId }
        Unit
    }

    override suspend fun clear() = mutex.withLock { entries.clear() }
}
