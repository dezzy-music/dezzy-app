/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.core

import com.dezzy.app.core.database.SqlStreamCache
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.source.stream.AudioQuality
import com.dezzy.app.source.stream.StreamAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamCacheTest {

    private val db = DezzyDatabase(createTestDriver())
    private var clock = 1_000L
    private val cache = SqlStreamCache(db, Dispatchers.Unconfined) { clock }

    private fun audio(id: String, expiresAt: Long) = StreamAudio(
        videoId = id,
        url = "https://rr1.googlevideo.com/videoplayback?id=$id",
        mimeType = "audio/webm",
        codec = "OPUS",
        bitrateKbps = 160,
        contentLength = 4_000_000,
        expiresAtMillis = expiresAt,
        clientName = "TV_SIMPLY",
    )

    @Test
    fun keepsPerQualityAndHonoursExpiry() = runTest {
        cache.put(AudioQuality.AUTO, audio("aaaaaaaaaaa", 10_000))
        assertEquals("aaaaaaaaaaa", cache.get("aaaaaaaaaaa", AudioQuality.AUTO, 5_000)?.videoId)
        assertNull(cache.get("aaaaaaaaaaa", AudioQuality.LOW, 5_000))
        assertNull(cache.get("aaaaaaaaaaa", AudioQuality.AUTO, 10_000))
    }

    @Test
    fun removeAndPurge() = runTest {
        cache.put(AudioQuality.AUTO, audio("aaaaaaaaaaa", 10_000))
        cache.remove("aaaaaaaaaaa")
        assertNull(cache.get("aaaaaaaaaaa", AudioQuality.AUTO, 0))

        cache.put(AudioQuality.AUTO, audio("bbbbbbbbbbb", 2_000))
        clock = 3_000
        cache.put(AudioQuality.AUTO, audio("ccccccccccc", 50_000))
        assertNull(db.dezzyQueries.getStream("bbbbbbbbbbb", "AUTO").executeAsOneOrNull(), "Lo caducado se purga al guardar")
    }
}
