/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import com.dezzy.app.core.model.Track
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Lo que la interfaz ve del reproductor. */
data class PlaybackState(
    val queue: QueueState = QueueState(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    /** Hora (epoch ms) a la que se pausará, o null. */
    val sleepAtMillis: Long? = null,
    /** Pausar al acabar la canción actual. */
    val sleepAtEndOfSong: Boolean = false,
    /** Volumen elegido por quien escucha (0–1), aparte de la normalización y el fundido. */
    val volume: Float = 1f,
)

/** Mando del reproductor. Todas las llamadas desde el hilo principal. */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    fun playTracks(tracks: List<Track>, start: Int = 0, source: String? = null)
    fun playRadio(track: Track, source: String)
    fun playAt(index: Int)
    fun playNext(track: Track)
    fun addToQueue(track: Track)
    fun removeAt(index: Int)
    fun move(from: Int, to: Int)
    fun clearQueue()

    fun play()
    fun pause()
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()

    /** Pausa dentro de [minutes] minutos; null lo quita. */
    fun setSleepTimer(minutes: Int?)
    fun setSleepAtEndOfSong(enabled: Boolean)

    /** Volumen de la app entre 0 y 1. */
    fun setVolume(volume: Float)

    /** Sesión de audio del motor (para el espectro de los visualizadores); 0 si no hay. */
    val audioSessionId: Int get() = 0
}

/** Cuentas del reproductor, aparte para poder probarlas. Iguales que en Dezzy Desktop. */
object PlaybackMath {
    const val TICK_MS = 80L

    /** Sólo baja el volumen: 10^(−dB/20), entre 0,2 y 1. Sin dato, 1. */
    fun normalizationGain(loudnessDb: Double?): Float {
        if (loudnessDb == null || loudnessDb.isNaN()) return 1f
        return min(1.0, max(0.2, 10.0.pow(-loudnessDb / 20.0))).toFloat()
    }

    /**
     * Fundido lineal sin solapar: baja en los últimos [crossfadeMs] (sólo si la canción dura
     * más del triple) y sube en la primera mitad de ese tiempo.
     */
    fun crossfadeGain(positionMs: Long, durationMs: Long, crossfadeMs: Long): Float {
        if (crossfadeMs <= 0) return 1f
        // Sin duración conocida todavía (recién cargada) sólo cabe la subida.
        if (durationMs > 3 * crossfadeMs) {
            val left = durationMs - positionMs
            if (left < crossfadeMs) return (left.toFloat() / crossfadeMs).coerceIn(0f, 1f)
        }
        val fadeIn = crossfadeMs / 2
        if (fadeIn > 0 && positionMs < fadeIn) return (positionMs.toFloat() / fadeIn).coerceIn(0f, 1f)
        return 1f
    }
}

/**
 * Suma el tiempo escuchado de verdad: sólo cuenta saltos de menos de 2 s con la música
 * sonando (si el móvil se duerme no cuenta el hueco). Entrega tramos de al menos 5 s y
 * como mucho 3600 s.
 */
class ListenAccumulator {
    private var accumulatedMs = 0L
    private var lastTick: Long? = null
    private var segmentStart: Long? = null

    fun tick(nowMs: Long, playing: Boolean) {
        val last = lastTick
        lastTick = nowMs
        if (!playing || last == null) return
        val delta = nowMs - last
        if (delta in 1 until MAX_GAP_MS) {
            if (segmentStart == null) segmentStart = last
            accumulatedMs += delta
        }
    }

    /** Corta el reloj (pausa): el siguiente tick no suma el hueco. */
    fun pause() {
        lastTick = null
    }

    /** Si ya hay un tramo completo (3600 s) lo entrega sin esperar. */
    fun takeFull(): Segment? = if (accumulatedMs >= MAX_SECONDS * 1000) drain() else null

    /**
     * Entrega lo acumulado si llega a 5 s y empieza de cero. Con [keepShort] (una pausa) lo
     * que no llega a 5 s se guarda para seguir sumando; sin él (cambio de canción) se tira.
     */
    fun drain(keepShort: Boolean = false): Segment? {
        val start = segmentStart
        val seconds = min(MAX_SECONDS, accumulatedMs / 1000)
        if (keepShort && (start == null || seconds < MIN_SECONDS)) {
            lastTick = null
            return null
        }
        accumulatedMs = 0
        segmentStart = null
        lastTick = null
        return if (start != null && seconds >= MIN_SECONDS) Segment(start, seconds) else null
    }

    data class Segment(val startedAtMillis: Long, val seconds: Long)

    private companion object {
        const val MAX_GAP_MS = 2_000L
        const val MIN_SECONDS = 5L
        const val MAX_SECONDS = 3_600L
    }
}
