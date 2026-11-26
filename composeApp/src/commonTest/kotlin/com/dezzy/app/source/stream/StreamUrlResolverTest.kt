/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamUrlResolverTest {

    private class FakeEngine : StreamEngine {
        var sessions = 0
        val calls = mutableListOf<String>()
        val resets = mutableListOf<Boolean>()
        var behavior: suspend (videoId: String, client: String) -> List<EngineAudioFormat> = { v, _ -> formats(v) }

        override suspend fun createSession(): String = "visitor${++sessions}"
        override suspend fun mintPoToken(binding: String): String = "pot-$binding"
        override suspend fun audioFormats(videoId: String, client: String, poToken: String): List<EngineAudioFormat> {
            calls += client
            return behavior(videoId, client)
        }
        override fun reset(hard: Boolean) {
            resets += hard
        }
    }

    private class Harness(scope: TestScope, probeOk: Boolean = true, val cache: StreamCache = InMemoryStreamCache()) {
        val engine = FakeEngine()
        var probes = 0
        val resolver = StreamUrlResolver(
            engine = engine,
            probe = StreamProbe { _, _ -> probes++; probeOk },
            cache = cache,
            dispatcher = StandardTestDispatcher(scope.testScheduler),
            now = { scope.testScheduler.currentTime },
        )
    }

    @Test
    fun picksFormatByQuality() = runTest {
        val h = Harness(this)
        val auto = h.resolver.resolveStream(SONG_A).getOrThrow()
        assertTrue(auto.url.contains("itag=251"), auto.url)
        assertEquals("OPUS", auto.codec)
        assertEquals("audio/webm", auto.mimeType)
        assertEquals(210, auto.bitrateKbps)
        assertEquals("TV_SIMPLY", auto.clientName)

        val high = h.resolver.resolveStream(SONG_A, quality = AudioQuality.HIGH).getOrThrow()
        assertEquals("AAC", high.codec)
        val low = h.resolver.resolveStream(SONG_A, quality = AudioQuality.LOW).getOrThrow()
        assertEquals(50, low.bitrateKbps)
    }

    @Test
    fun concurrentRequestsShareOneResolution() = runTest {
        val h = Harness(this)
        h.engine.behavior = { v, _ -> delay(100); formats(v) }
        val first = async { h.resolver.resolveStream(SONG_A) }
        val second = async { h.resolver.resolveStream(SONG_A, priority = StreamPriority.PRELOAD) }
        assertEquals(first.await().getOrThrow().url, second.await().getOrThrow().url)
        assertEquals(listOf("TV_SIMPLY"), h.engine.calls)
    }

    @Test
    fun cachedResultSurvivesNewResolver() = runTest {
        val cache = InMemoryStreamCache()
        Harness(this, cache = cache).resolver.resolveStream(SONG_A).getOrThrow()
        val again = Harness(this, cache = cache)
        again.resolver.resolveStream(SONG_A).getOrThrow()
        assertTrue(again.engine.calls.isEmpty(), "Debe salir de la caché sin tocar el motor")
    }

    @Test
    fun forceResolvesAgain() = runTest {
        val h = Harness(this)
        h.resolver.resolveStream(SONG_A).getOrThrow()
        h.resolver.resolveStream(SONG_A, force = true).getOrThrow()
        assertEquals(2, h.engine.calls.size)
    }

    @Test
    fun slowFirstClientLosesAfterHeadStart() = runTest {
        val h = Harness(this)
        h.engine.behavior = { v, c -> if (c == "TV_SIMPLY") delay(5_000); formats(v) }
        val audio = h.resolver.resolveStream(SONG_A).getOrThrow()
        assertEquals("YTMUSIC", audio.clientName)
        assertTrue(testScheduler.currentTime < 5_000, "No debe esperar al cliente lento: ${testScheduler.currentTime}")
    }

    @Test
    fun lastWinnerGoesFirstNextTime() = runTest {
        val h = Harness(this)
        h.engine.behavior = { v, c -> if (c == "TV_SIMPLY") error("LOGIN_REQUIRED") else formats(v) }
        assertEquals("YTMUSIC", h.resolver.resolveStream(SONG_A).getOrThrow().clientName)
        h.engine.calls.clear()
        h.resolver.resolveStream(SONG_B).getOrThrow()
        assertEquals(listOf("YTMUSIC"), h.engine.calls)
    }

    @Test
    fun retriesOnceWithFreshSession() = runTest {
        val h = Harness(this)
        h.engine.behavior = { v, _ -> if (h.engine.resets.isEmpty()) error("UNPLAYABLE") else formats(v) }
        assertTrue(h.resolver.resolveStream(SONG_A).isSuccess)
        assertEquals(listOf(false), h.engine.resets)
        assertEquals(2, h.engine.sessions)
    }

    @Test
    fun stuckBotGuardGetsHardReset() = runTest {
        val h = Harness(this)
        h.engine.behavior = { v, _ -> if (h.engine.resets.isEmpty()) error("BotGuard cerrado") else formats(v) }
        assertTrue(h.resolver.resolveStream(SONG_A).isSuccess)
        assertEquals(listOf(true), h.engine.resets)
    }

    @Test
    fun failsWhenProbeNeverPasses() = runTest {
        val h = Harness(this, probeOk = false)
        val result = h.resolver.resolveStream(SONG_A)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("sin audio válido"))
    }

    @Test
    fun probedClientIsTrustedForNextSongs() = runTest {
        val h = Harness(this)
        h.resolver.resolveStream(SONG_A).getOrThrow()
        h.resolver.resolveStream(SONG_B).getOrThrow()
        assertEquals(1, h.probes)
    }

    @Test
    fun rejectsInvalidVideoId() = runTest {
        assertTrue(Harness(this).resolver.resolveStream("no válido").isFailure)
    }

    private companion object {
        const val SONG_A = "BSTsnWoslP4"
        const val SONG_B = "fJ9rUzIMcZQ"

        fun format(videoId: String, itag: Int, mime: String, bitrate: Long, average: Long) = EngineAudioFormat(
            itag = itag,
            mimeType = mime,
            bitrate = bitrate,
            averageBitrate = average,
            contentLength = 4_000_000,
            url = "https://rr1.googlevideo.com/videoplayback?id=$videoId&itag=$itag&expire=9999999999&pot=x",
        )

        fun formats(videoId: String) = listOf(
            format(videoId, 140, "audio/mp4; codecs=\"mp4a.40.2\"", 130_000, 129_000),
            format(videoId, 251, "audio/webm; codecs=\"opus\"", 230_000, 210_000),
            format(videoId, 249, "audio/webm; codecs=\"opus\"", 60_000, 50_000),
        )
    }
}
