/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackMathTest {

    @Test
    fun normalizationOnlyAttenuates() {
        assertEquals(1f, PlaybackMath.normalizationGain(null))
        assertEquals(1f, PlaybackMath.normalizationGain(-6.0))
        assertEquals(0.5f, PlaybackMath.normalizationGain(6.0206), 0.001f)
        assertEquals(0.2f, PlaybackMath.normalizationGain(40.0))
    }

    @Test
    fun crossfadeFadesOutAndIn() {
        val cf = 6_000L
        assertEquals(1f, PlaybackMath.crossfadeGain(60_000, 200_000, 0))
        assertEquals(0.5f, PlaybackMath.crossfadeGain(197_000, 200_000, cf), 0.001f)
        assertEquals(1f, PlaybackMath.crossfadeGain(100_000, 200_000, cf))
        assertEquals(0f, PlaybackMath.crossfadeGain(0, 200_000, cf))
        assertEquals(0.5f, PlaybackMath.crossfadeGain(1_500, 200_000, cf), 0.001f)
        // Canción corta (menos del triple): no baja al final.
        assertEquals(1f, PlaybackMath.crossfadeGain(15_000, 17_000, cf))
        // Sin duración todavía: sólo la subida.
        assertEquals(0f, PlaybackMath.crossfadeGain(0, 0, cf))
    }

    @Test
    fun listenCountsOnlyRealPlayback() {
        val acc = ListenAccumulator()
        var t = 1_000_000L
        acc.tick(t, true)
        repeat(60) { t += 100; acc.tick(t, true) } // 6 s
        t += 30_000; acc.tick(t, true) // hueco: no cuenta
        val segment = acc.drain()!!
        assertEquals(6, segment.seconds)
        assertEquals(1_000_000L, segment.startedAtMillis)
    }

    @Test
    fun shortListenIsKeptOnPauseAndDroppedOnSongChange() {
        val acc = ListenAccumulator()
        var t = 0L
        acc.tick(t, true)
        repeat(30) { t += 100; acc.tick(t, true) } // 3 s
        assertNull(acc.drain(keepShort = true))
        t += 60_000; acc.tick(t, true)
        repeat(30) { t += 100; acc.tick(t, true) } // otros 3 s
        assertEquals(6, acc.drain(keepShort = true)?.seconds)
        repeat(30) { t += 100; acc.tick(t, true) }
        assertNull(acc.drain())
        assertNull(acc.drain(keepShort = true))
    }
}
