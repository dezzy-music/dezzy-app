/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

actual fun createAudioEffects(prefs: DezzyPreferences): AudioEffects = AndroidAudioEffects(prefs)

/** [AudioEffects] sobre [DezzyEqManager]: el motor C++20 (o su respaldo en Kotlin) en la cadena Hi-Res. */
internal class AndroidAudioEffects(private val prefs: DezzyPreferences) : AudioEffects {

    private val manager = DezzyEqManager
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        manager.initAndLoad(prefs)
    }

    override val state: StateFlow<AudioFxState> = combine(
        manager.snapshotFlow,
        manager.limiterGainReductionDb,
        manager.hiResEnabled,
    ) { snap, gr, hiRes ->
        AudioFxState(
            nativeAvailable = NativeDspEngine.isNativeSupported(),
            hiResEnabled = hiRes,
            processingSupported = true,
            eqEnabled = snap.eqEnabled,
            bandGainsDb = EQ_BAND_FREQUENCIES.indices.map { i -> snap.bands.getOrNull(i)?.gainDb ?: 0f },
            bassStrength = snap.bassBoostStrength,
            virtualizerStrength = snap.virtualizerStrength,
            vocalMix = snap.vocalMix,
            vocalBassGuard = snap.vocalBassGuard,
            preampDb = snap.outputGainDb,
            limiterEnabled = snap.limiterEnabled,
            limiterGainReductionDb = gr,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AudioFxState(nativeAvailable = NativeDspEngine.isNativeSupported()))

    /** Reaplica todos los mandos con un cambio puntual (updateEngineControls recibe los 9 de golpe). */
    private fun push(
        eq: Boolean = manager.snapshot.eqEnabled,
        bands: List<EqBand> = manager.snapshot.bands,
        bass: Float = manager.snapshot.bassBoostStrength,
        virt: Float = manager.snapshot.virtualizerStrength,
        gainDb: Float = manager.snapshot.outputGainDb,
        limiter: Boolean = manager.snapshot.limiterEnabled,
    ) {
        manager.updateEngineControls(
            eqEnabled = eq,
            newBands = bands,
            bassBoostEnabled = bass > 0f,
            bassBoostStrength = (bass * 1000f).roundToInt(),
            virtualizerEnabled = virt > 0f,
            virtualizerStrength = (virt * 1000f).roundToInt(),
            outputGainEnabled = gainDb != 0f,
            outputGainMb = (gainDb * 100f).roundToInt(),
            limiter = limiter,
        )
    }

    override fun setHiResEnabled(enabled: Boolean) {
        manager.hiResEnabled.value = enabled
    }

    override fun setEqEnabled(enabled: Boolean) = push(eq = enabled)

    override fun setBandGain(index: Int, gainDb: Float) {
        val current = manager.snapshot.bands
        if (index !in current.indices) return
        val next = current.mapIndexed { i, band ->
            if (i == index) band.copy(gainDb = gainDb.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)) else band
        }
        push(bands = next)
    }

    override fun applyPreset(preset: EqPreset) {
        val next = manager.snapshot.bands.mapIndexed { i, band ->
            band.copy(gainDb = (preset.gainsDb.getOrNull(i) ?: 0f).coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB))
        }
        push(eq = true, bands = next)
    }

    override fun setBassBoost(strength: Float) = push(bass =strength.coerceIn(0f, 1f))

    override fun setVirtualizer(strength: Float) = push(virt =strength.coerceIn(0f, 1f))

    override fun setVocalMix(mix: Float) = manager.setVocalIsolation(mix)

    override fun setVocalBassGuard(enabled: Boolean) = manager.setVocalIsolation(manager.vocalMix.value, enabled)

    override fun setPreampDb(gainDb: Float) = push(gainDb =gainDb.coerceIn(-PREAMP_MAX_DB, PREAMP_MAX_DB))

    override fun setLimiterEnabled(enabled: Boolean) = manager.setLimiterEnabled(enabled)

    override fun reset() {
        push(eq = false, bands = manager.createDefaultBands(), bass = 0f, virt = 0f, gainDb = 0f, limiter = true)
        manager.setVocalIsolation(0f, true)
    }
}
