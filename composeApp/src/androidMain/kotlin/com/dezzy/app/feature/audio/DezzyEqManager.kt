/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Claves propias del motor de audio en DataStore. */
val ParametricEqBandsKey = stringPreferencesKey("parametric_eq_bands")
val ParametricEqEnabledKey = booleanPreferencesKey("parametric_eq_enabled")
val VocalSeparatorBassGuardKey = booleanPreferencesKey("vocal_separator_bass_guard")

/** «Motor de audio Hi-Res (32-bit float)»: apagado por defecto. Se aplica al crear el reproductor. */
val HiResAudioEnabledKey = booleanPreferencesKey("hires_audio_engine")

data class AudioDspSnapshot(
    val version: Long = 0L,
    val eqEnabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val bassBoostStrength: Float = 0f,
    val virtualizerStrength: Float = 0f,
    val outputGainDb: Float = 0f,
    val limiterEnabled: Boolean = true,
    /** -1f = 100% Instrumental (Karaoke), 0f = Original, +1f = 100% Solo Voz (Acapella) */
    val vocalMix: Float = 0f,
    val vocalBassGuard: Boolean = true,
) {
    fun hasActiveProcessing(): Boolean {
        if (abs(vocalMix) >= 0.01f) return true
        if (bassBoostStrength > 0.005f) return true
        if (virtualizerStrength > 0.005f) return true
        if (abs(outputGainDb) > 0.05f) return true
        if (!eqEnabled) return false
        return bands.any { band ->
            band.isEnabled && (
                band.type == FilterType.LOW_PASS ||
                    band.type == FilterType.HIGH_PASS ||
                    abs(band.gainDb) >= 0.05f
                )
        }
    }
}

object DezzyEqManager {
    val bands = MutableStateFlow(createDefaultBands())
    val isEqEnabled = MutableStateFlow(false)
    val limiterEnabled = MutableStateFlow(true)
    val limiterGainReductionDb = MutableStateFlow(0f)

    /** -1f (Solo Instrumental / Karaoke) .. 0f (Original) .. +1f (Solo Voz / Acapella) */
    val vocalMix = MutableStateFlow(0f)
    val vocalBassGuard = MutableStateFlow(true)

    /** Motor Hi-Res elegido por el usuario (cuenta al crear el reproductor). */
    val hiResEnabled = MutableStateFlow(false)

    private val versionCounter = AtomicLong(1L)

    @Volatile
    var snapshot: AudioDspSnapshot = AudioDspSnapshot(
        version = 1L,
        eqEnabled = false,
        bands = bands.value,
        limiterEnabled = true,
    )
        private set

    private val _snapshotFlow = MutableStateFlow(snapshot)

    /** Lo mismo que [snapshot], observable desde la interfaz. */
    val snapshotFlow: StateFlow<AudioDspSnapshot> = _snapshotFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var attached = false

    /** Engancha las preferencias y carga lo guardado. Sólo hace algo la primera vez. */
    fun initAndLoad(prefs: DezzyPreferences) {
        if (attached) return
        attached = true
        scope.launch {
            try {
                isEqEnabled.value = prefs.value(ParametricEqEnabledKey, false).first()
                vocalBassGuard.value = prefs.value(VocalSeparatorBassGuardKey, true).first()
                hiResEnabled.value = prefs.value(HiResAudioEnabledKey, false).first()

                val bandsJson = prefs.value(ParametricEqBandsKey, "").first()
                if (bandsJson.isNotEmpty()) {
                    try {
                        val loadedBands = Json.decodeFromString(ListSerializer(EqBand.serializer()), bandsJson)
                        if (loadedBands.isNotEmpty()) {
                            bands.value = loadedBands
                        }
                    } catch (_: Exception) {
                    }
                }
                publishSnapshot(
                    eqEnabled = isEqEnabled.value,
                    bandsList = bands.value,
                    bassBoostStrength = snapshot.bassBoostStrength,
                    virtualizerStrength = snapshot.virtualizerStrength,
                    outputGainDb = snapshot.outputGainDb,
                    limiter = limiterEnabled.value,
                    vMix = vocalMix.value,
                    vBassGuard = vocalBassGuard.value,
                )
            } catch (_: Exception) {
            }

            launch {
                bands.collectLatest { currentBands ->
                    try {
                        val json = Json.encodeToString(ListSerializer(EqBand.serializer()), currentBands)
                        prefs.set(ParametricEqBandsKey, json)
                    } catch (_: Exception) {
                    }
                }
            }
            launch {
                isEqEnabled.collectLatest { enabled ->
                    try {
                        prefs.set(ParametricEqEnabledKey, enabled)
                    } catch (_: Exception) {
                    }
                }
            }
            launch {
                vocalBassGuard.collectLatest { guard ->
                    try {
                        prefs.set(VocalSeparatorBassGuardKey, guard)
                    } catch (_: Exception) {
                    }
                }
            }
            launch {
                hiResEnabled.collectLatest { enabled ->
                    try {
                        prefs.set(HiResAudioEnabledKey, enabled)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /**
     * Lee de forma síncrona si el motor Hi-Res está elegido. Lo usa el reproductor al construir
     * ExoPlayer (antes de que termine la carga asíncrona de [initAndLoad]).
     */
    fun readHiResEnabledBlocking(prefs: DezzyPreferences): Boolean {
        val enabled = runCatching { runBlocking { prefs.value(HiResAudioEnabledKey, false).first() } }
            .getOrDefault(false)
        hiResEnabled.value = enabled
        return enabled
    }

    fun setVocalIsolation(mix: Float, bassGuard: Boolean = vocalBassGuard.value) {
        val clamped = mix.coerceIn(-1f, 1f).let { if (abs(it) < 0.02f) 0f else it }
        vocalMix.value = clamped
        vocalBassGuard.value = bassGuard
        val cur = snapshot
        publishSnapshot(
            eqEnabled = cur.eqEnabled,
            bandsList = cur.bands,
            bassBoostStrength = cur.bassBoostStrength,
            virtualizerStrength = cur.virtualizerStrength,
            outputGainDb = cur.outputGainDb,
            limiter = cur.limiterEnabled,
            vMix = clamped,
            vBassGuard = bassGuard,
        )
    }

    fun cycleVocalIsolationPreset() {
        val current = vocalMix.value
        val next = when {
            current > -0.3f && current < 0.3f -> -1f // Original -> Instrumental (Karaoke)
            current <= -0.3f -> 1f                   // Karaoke -> Solo Voz (Acapella)
            else -> 0f                               // Acapella -> Original
        }
        setVocalIsolation(next, vocalBassGuard.value)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        limiterEnabled.value = enabled
        val cur = snapshot
        publishSnapshot(
            eqEnabled = cur.eqEnabled,
            bandsList = cur.bands,
            bassBoostStrength = cur.bassBoostStrength,
            virtualizerStrength = cur.virtualizerStrength,
            outputGainDb = cur.outputGainDb,
            limiter = enabled,
            vMix = cur.vocalMix,
            vBassGuard = cur.vocalBassGuard,
        )
    }

    fun updateFromParametric(enabled: Boolean, newBands: List<EqBand>) {
        isEqEnabled.value = enabled
        val finalBands = if (newBands.isNotEmpty()) newBands.map { it.copy() } else bands.value
        if (newBands.isNotEmpty()) {
            bands.value = finalBands
        }
        val cur = snapshot
        publishSnapshot(
            eqEnabled = enabled,
            bandsList = finalBands,
            bassBoostStrength = cur.bassBoostStrength,
            virtualizerStrength = cur.virtualizerStrength,
            outputGainDb = cur.outputGainDb,
            limiter = limiterEnabled.value,
            vMix = cur.vocalMix,
            vBassGuard = cur.vocalBassGuard,
        )
    }

    fun updateFromSystemSettings(enabled: Boolean, levelsMb: List<Int>) {
        isEqEnabled.value = enabled
        val defaultFrequencies = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

        val newBands = levelsMb.mapIndexed { index, levelMb ->
            val freq = defaultFrequencies.getOrElse(index) { 1000f }
            val gainDb = (levelMb / 100f).coerceIn(-24f, 24f)
            val type = when {
                index == 0 -> FilterType.LOW_SHELF
                index == levelsMb.lastIndex -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }

            EqBand(
                id = index,
                type = type,
                frequencyHz = freq,
                gainDb = gainDb,
                qFactor = 0.707f,
            )
        }

        val finalBands = if (newBands.isNotEmpty()) newBands else bands.value
        if (newBands.isNotEmpty()) {
            bands.value = finalBands
        }
        val cur = snapshot
        publishSnapshot(
            eqEnabled = enabled,
            bandsList = finalBands,
            bassBoostStrength = cur.bassBoostStrength,
            virtualizerStrength = cur.virtualizerStrength,
            outputGainDb = cur.outputGainDb,
            limiter = limiterEnabled.value,
            vMix = cur.vocalMix,
            vBassGuard = cur.vocalBassGuard,
        )
    }

    fun updateEngineControls(
        eqEnabled: Boolean,
        newBands: List<EqBand>,
        bassBoostEnabled: Boolean,
        bassBoostStrength: Int,
        virtualizerEnabled: Boolean,
        virtualizerStrength: Int,
        outputGainEnabled: Boolean,
        outputGainMb: Int,
        limiter: Boolean = limiterEnabled.value,
    ) {
        isEqEnabled.value = eqEnabled
        limiterEnabled.value = limiter
        val finalBands = if (newBands.isNotEmpty()) newBands.map { it.copy() } else bands.value
        if (newBands.isNotEmpty()) {
            bands.value = finalBands
        }
        val bassNorm = if (bassBoostEnabled) (bassBoostStrength / 1000f).coerceIn(0f, 1f) else 0f
        val virtNorm = if (virtualizerEnabled) (virtualizerStrength / 1000f).coerceIn(0f, 1f) else 0f
        val gainDb = if (outputGainEnabled) (outputGainMb / 100f).coerceIn(-15f, 15f) else 0f
        val cur = snapshot

        publishSnapshot(
            eqEnabled = eqEnabled,
            bandsList = finalBands,
            bassBoostStrength = bassNorm,
            virtualizerStrength = virtNorm,
            outputGainDb = gainDb,
            limiter = limiter,
            vMix = cur.vocalMix,
            vBassGuard = cur.vocalBassGuard,
        )
    }

    fun reportLimiterGainReduction(grDb: Float) {
        val current = limiterGainReductionDb.value
        val next = grDb.coerceIn(0f, 24f)
        if (abs(current - next) >= 0.1f || (next == 0f && current != 0f)) {
            limiterGainReductionDb.value = next
        }
    }

    @Synchronized
    private fun publishSnapshot(
        eqEnabled: Boolean,
        bandsList: List<EqBand>,
        bassBoostStrength: Float,
        virtualizerStrength: Float,
        outputGainDb: Float,
        limiter: Boolean,
        vMix: Float,
        vBassGuard: Boolean,
    ) {
        val next = AudioDspSnapshot(
            version = versionCounter.incrementAndGet(),
            eqEnabled = eqEnabled,
            bands = bandsList.map { it.copy() },
            bassBoostStrength = bassBoostStrength,
            virtualizerStrength = virtualizerStrength,
            outputGainDb = outputGainDb,
            limiterEnabled = limiter,
            vocalMix = vMix,
            vocalBassGuard = vBassGuard,
        )
        snapshot = next
        _snapshotFlow.value = next
    }

    internal fun createDefaultBands(): List<EqBand> {
        val defaultFrequencies = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        return defaultFrequencies.mapIndexed { index, freq ->
            EqBand(
                id = index,
                type = when (index) {
                    0 -> FilterType.LOW_SHELF
                    defaultFrequencies.lastIndex -> FilterType.HIGH_SHELF
                    else -> FilterType.PEAKING
                },
                frequencyHz = freq,
                gainDb = 0f,
                qFactor = 0.707f,
            )
        }
    }
}
