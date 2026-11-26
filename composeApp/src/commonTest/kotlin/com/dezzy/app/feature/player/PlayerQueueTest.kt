/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import com.dezzy.app.core.model.Track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerQueueTest {

    private fun t(id: String) = Track(videoId = id, title = "T$id", artists = "A")
    private fun tracks(n: Int) = (0 until n).map { t("v$it") }
    private fun q() = PlayerQueue(Random(42))
    private fun PlayerQueue.ids() = state.value.items.map { it.track.videoId }

    @Test
    fun loadVacioDevuelveFalse() {
        val q = q()
        assertFalse(q.load(emptyList()))
        assertTrue(q.state.value.items.isEmpty())
        assertEquals(-1, q.state.value.index)
    }

    @Test
    fun loadAcotaStart() {
        val q = q()
        assertTrue(q.load(tracks(3), start = 10))
        assertEquals(2, q.state.value.index)
        q.load(tracks(3), start = -5)
        assertEquals(0, q.state.value.index)
    }

    @Test
    fun playNextInsertaTrasLaActual() {
        val q = q()
        q.load(tracks(3), start = 1)
        q.playNext(t("X"))
        assertEquals(listOf("v0", "v1", "X", "v2"), q.ids())
        assertEquals(1, q.state.value.index)
    }

    @Test
    fun addToQueueDosVecesQuedaEnOrdenDeAñadido() {
        val q = q()
        q.load(tracks(3), start = 0)
        q.addToQueue(t("A"))
        q.addToQueue(t("B"))
        assertEquals(listOf("v0", "A", "B", "v1", "v2"), q.ids())
        assertEquals(0, q.state.value.index)
    }

    @Test
    fun removeAtNoQuitaLaActualYAjustaIndex() {
        val q = q()
        q.load(tracks(4), start = 2)
        q.removeAt(2)
        assertEquals(4, q.state.value.items.size)
        q.removeAt(0)
        assertEquals(listOf("v1", "v2", "v3"), q.ids())
        assertEquals(1, q.state.value.index)
        q.removeAt(2)
        assertEquals(1, q.state.value.index)
        q.removeAt(99)
        assertEquals(2, q.state.value.items.size)
    }

    @Test
    fun moveEnLosTresCasos() {
        val q = q()
        q.load(tracks(5), start = 2)
        q.move(2, 4) // la actual se mueve
        assertEquals(4, q.state.value.index)
        assertEquals("v2", q.state.value.current?.track?.videoId)

        q.load(tracks(5), start = 2)
        q.move(0, 3) // from < index, to >= index
        assertEquals(1, q.state.value.index)
        assertEquals("v2", q.state.value.current?.track?.videoId)

        q.load(tracks(5), start = 2)
        q.move(4, 1) // from > index, to <= index
        assertEquals(3, q.state.value.index)
        assertEquals("v2", q.state.value.current?.track?.videoId)

        q.move(0, 0)
        q.move(0, 99)
    }

    @Test
    fun toggleShuffleDejaActualEnCeroYConservaItems() {
        val q = q()
        q.load(tracks(10), start = 4)
        q.toggleShuffle()
        val s = q.state.value
        assertTrue(s.shuffled)
        assertEquals(0, s.index)
        assertEquals("v4", s.current?.track?.videoId)
        assertEquals(tracks(10).map { it.videoId }.toSet(), q.ids().toSet())
        assertEquals(10, s.items.size)

        q.toggleShuffle()
        val s2 = q.state.value
        assertFalse(s2.shuffled)
        assertEquals(tracks(10).map { it.videoId }, q.ids())
        assertEquals(4, s2.index)
    }

    @Test
    fun insertarConAleatorioYDesactivarConservaLaInsertada() {
        val q = q()
        q.load(tracks(6), start = 1)
        q.toggleShuffle()
        q.addToQueue(t("X"))
        assertTrue("X" in q.ids())
        q.toggleShuffle()
        assertTrue("X" in q.ids())
        assertEquals(7, q.state.value.items.size)
        assertEquals("v1", q.state.value.current?.track?.videoId)
    }

    @Test
    fun cycleRepeatRecorreElCiclo() {
        val q = q()
        assertEquals(RepeatMode.OFF, q.state.value.repeat)
        q.cycleRepeat()
        assertEquals(RepeatMode.ALL, q.state.value.repeat)
        q.cycleRepeat()
        assertEquals(RepeatMode.ONE, q.state.value.repeat)
        q.cycleRepeat()
        assertEquals(RepeatMode.OFF, q.state.value.repeat)
    }

    @Test
    fun onEndedEnElUltimoSegunRepeat() {
        val q = q()
        q.load(tracks(3), start = 2)
        assertEquals(EndedAction.Stop, q.onEnded())
        q.cycleRepeat() // ALL
        assertEquals(EndedAction.Load(0), q.onEnded())
        q.cycleRepeat() // ONE
        assertEquals(EndedAction.RepeatOne, q.onEnded())
        q.setIndex(0)
        q.cycleRepeat() // OFF
        assertEquals(EndedAction.Load(1), q.onEnded())
    }

    @Test
    fun nextIndexConAllDaLaVueltaYAfterErrorNo() {
        val q = q()
        q.load(tracks(3), start = 2)
        assertNull(q.nextIndex())
        q.cycleRepeat() // ALL
        assertEquals(0, q.nextIndex())
        assertNull(q.nextIndexAfterError())
        q.setIndex(0)
        assertEquals(1, q.nextIndexAfterError())
    }

    @Test
    fun previousSegunPosicionEIndice() {
        val q = q()
        q.load(tracks(5), start = 2)
        assertEquals(PreviousAction.Restart, q.previous(3500))
        assertEquals(PreviousAction.Load(1), q.previous(1000))
        q.setIndex(0)
        assertEquals(PreviousAction.Restart, q.previous(1000))
    }

    @Test
    fun appendRadioNoDuplica() {
        val q = q()
        q.loadRadio(t("a"), "radio")
        assertTrue(q.state.value.radio)
        assertTrue(q.needsRadioExtension())
        q.appendRadio(listOf(t("a"), t("b"), t("b"), t("c"), Track(null, "x", "y")))
        assertEquals(listOf("a", "b", "c"), q.ids())
    }

    @Test
    fun snapshotRestoreIdaYVuelta() {
        val q = q()
        q.load(tracks(400), start = 100)
        q.addToQueue(t("M"))
        val snap = q.snapshot(1234)
        assertEquals(300, snap.items.size)
        assertEquals(20, snap.index)
        assertEquals(1234, snap.positionMs)
        assertEquals("v80", snap.items.first().track.videoId)
        assertEquals(listOf(21), snap.manualIndices)

        val q2 = q()
        q2.restore(snap)
        assertEquals(300, q2.state.value.items.size)
        assertEquals(20, q2.state.value.index)
        assertEquals("v100", q2.state.value.current?.track?.videoId)
        // Los manuales se conservan: una nueva se añade detrás de la manual restaurada.
        q2.addToQueue(t("N"))
        assertEquals("M", q2.state.value.items[21].track.videoId)
        assertEquals("N", q2.state.value.items[22].track.videoId)
        val ids = q2.state.value.items.map { it.queueEntryId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun stateEmiteElEstadoNuevoTrasUnaOperacion() {
        val q = q()
        assertTrue(q.state.value.items.isEmpty())
        q.load(tracks(2))
        assertEquals(2, q.state.value.items.size)
        q.clear()
        assertEquals(1, q.state.value.items.size)
        assertEquals(0, q.state.value.index)
    }
}
