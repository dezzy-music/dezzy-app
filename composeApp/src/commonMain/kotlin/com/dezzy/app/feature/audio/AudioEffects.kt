/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Frecuencias (Hz) de las 10 bandas del ecualizador gráfico. */
val EQ_BAND_FREQUENCIES: List<Float> = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_BAND_COUNT: Int = EQ_BAND_FREQUENCIES.size

/** Rango de cada fader: de -EQ_MAX_GAIN_DB a +EQ_MAX_GAIN_DB. */
const val EQ_MAX_GAIN_DB = 15f

/** Rango del preamp, en dB. */
const val PREAMP_MAX_DB = 15f

/** Etiqueta corta de una frecuencia: 31, 62, ... 1k, 2k ... 16k. */
fun eqBandLabel(hz: Float): String =
    if (hz >= 1000f) "${(hz / 1000f).toInt()}k" else "${hz.toInt()}"

/** Un preset del ecualizador: una ganancia (dB) por banda de [EQ_BAND_FREQUENCIES]. */
data class EqPreset(val name: String, val gainsDb: List<Float>)

val EQ_PRESETS: List<EqPreset> = listOf(
    EqPreset("Plano", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Bajos potenciados", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Agudos potenciados", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),
    EqPreset("Voz", listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
    EqPreset("Rock", listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
    EqPreset("Pop", listOf(-1f, 2f, 4f, 4f, 2f, 0f, -1f, -1f, -1f, -1f)),
    EqPreset("Jazz", listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f)),
    EqPreset("Clásica", listOf(4f, 3f, 2f, 2f, -1f, -1f, 0f, 2f, 3f, 4f)),
    EqPreset("Electrónica", listOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 1f, 4f, 5f)),
    EqPreset("Hip-hop", listOf(5f, 4f, 2f, 3f, -1f, -1f, 2f, -1f, 2f, 3f)),
)

/** Lo que la interfaz ve del motor de audio. */
data class AudioFxState(
    /** true si la librería C++ (`libdezzy_dsp.so`) cargó; si no, suena el motor de respaldo en Kotlin. */
    val nativeAvailable: Boolean = false,
    /** Motor Hi-Res (salida float de 32 bits con la cadena de procesadores de Dezzy). */
    val hiResEnabled: Boolean = false,
    /** false en plataformas sin procesado (escritorio): los ajustes se guardan pero no suenan. */
    val processingSupported: Boolean = false,
    val eqEnabled: Boolean = false,
    /** Una ganancia (dB) por cada frecuencia de [EQ_BAND_FREQUENCIES]. */
    val bandGainsDb: List<Float> = List(EQ_BAND_COUNT) { 0f },
    /** Sub-bass, de 0 a 1. */
    val bassStrength: Float = 0f,
    /** Virtualizer estéreo, de 0 a 1. */
    val virtualizerStrength: Float = 0f,
    /** -1 = sólo instrumental (karaoke), 0 = original, +1 = sólo voz (acapella). */
    val vocalMix: Float = 0f,
    val vocalBassGuard: Boolean = true,
    val preampDb: Float = 0f,
    val limiterEnabled: Boolean = true,
    /** Reducción actual del limitador, en dB (>= 0). */
    val limiterGainReductionDb: Float = 0f,
)

/**
 * Contrato común del motor de audio. En Android lo implementa [DezzyEqManager] (motor C++20 con
 * respaldo en Kotlin); en escritorio sólo guarda el estado y no procesa.
 */
interface AudioEffects {
    val state: StateFlow<AudioFxState>

    /** Cuenta la próxima vez que se crea el reproductor (en Android, al reiniciar la app). */
    fun setHiResEnabled(enabled: Boolean)
    fun setEqEnabled(enabled: Boolean)
    fun setBandGain(index: Int, gainDb: Float)
    fun applyPreset(preset: EqPreset)
    fun setBassBoost(strength: Float)
    fun setVirtualizer(strength: Float)
    fun setVocalMix(mix: Float)
    fun setVocalBassGuard(enabled: Boolean)
    fun setPreampDb(gainDb: Float)
    fun setLimiterEnabled(enabled: Boolean)

    /** Todo a su valor de fábrica (el interruptor Hi-Res no se toca). */
    fun reset()
}

/** Implementación de escritorio: guarda el estado en memoria y no procesa audio. */
class InMemoryAudioEffects : AudioEffects {
    private val _state = MutableStateFlow(AudioFxState())
    override val state: StateFlow<AudioFxState> = _state.asStateFlow()

    override fun setHiResEnabled(enabled: Boolean) = _state.update { it.copy(hiResEnabled = enabled) }

    override fun setEqEnabled(enabled: Boolean) = _state.update { it.copy(eqEnabled = enabled) }

    override fun setBandGain(index: Int, gainDb: Float) = _state.update { s ->
        if (index !in s.bandGainsDb.indices) s
        else s.copy(bandGainsDb = s.bandGainsDb.toMutableList().also { it[index] = gainDb.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) })
    }

    override fun applyPreset(preset: EqPreset) = _state.update {
        it.copy(eqEnabled = true, bandGainsDb = preset.gainsDb.map { g -> g.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) })
    }

    override fun setBassBoost(strength: Float) = _state.update { it.copy(bassStrength = strength.coerceIn(0f, 1f)) }

    override fun setVirtualizer(strength: Float) = _state.update { it.copy(virtualizerStrength = strength.coerceIn(0f, 1f)) }

    override fun setVocalMix(mix: Float) = _state.update { it.copy(vocalMix = mix.coerceIn(-1f, 1f)) }

    override fun setVocalBassGuard(enabled: Boolean) = _state.update { it.copy(vocalBassGuard = enabled) }

    override fun setPreampDb(gainDb: Float) = _state.update { it.copy(preampDb = gainDb.coerceIn(-PREAMP_MAX_DB, PREAMP_MAX_DB)) }

    override fun setLimiterEnabled(enabled: Boolean) = _state.update { it.copy(limiterEnabled = enabled) }

    override fun reset() = _state.update { AudioFxState(hiResEnabled = it.hiResEnabled) }
}

/** Crea el [AudioEffects] de la plataforma. */
expect fun createAudioEffects(prefs: DezzyPreferences): AudioEffects
