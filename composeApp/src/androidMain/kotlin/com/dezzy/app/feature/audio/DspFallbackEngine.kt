/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Motor DSP en Kotlin puro (64 bits TDF-II) que replica `dezzy_dsp.cpp`, de modo que los
 * buffers no directos y los tests de JVM ejecutan las mismas matemáticas de biquad + sub-bass
 * + virtualizer Mid/Side + limitador suave que el runtime nativo C++20.
 */
internal class DspFallbackEngine {

    private companion object {
        const val MAX_CHANNELS = 8
        const val LIMITER_THRESHOLD = 0.89125 // -1.0 dBFS
    }

    class BiquadState {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var active = false
        val z1 = DoubleArray(MAX_CHANNELS)
        val z2 = DoubleArray(MAX_CHANNELS)

        fun resetStates() {
            z1.fill(0.0)
            z2.fill(0.0)
        }

        fun processSample(x: Double, ch: Int): Double {
            val y = b0 * x + z1[ch]
            var nextZ1 = b1 * x - a1 * y + z2[ch]
            var nextZ2 = b2 * x - a2 * y
            if (abs(nextZ1) < 1e-24) nextZ1 = 0.0
            if (abs(nextZ2) < 1e-24) nextZ2 = 0.0
            z1[ch] = nextZ1
            z2[ch] = nextZ2
            return y
        }
    }

    private var vocalMix = 0.0
    private var vocalBassGuard = true
    private val vocalLowLp = BiquadState()
    private val vocalLowHp = BiquadState()
    private val vocalHighLp = BiquadState()
    private val vocalHighHp = BiquadState()
    private var vocalEnvM = 0.0
    private var vocalEnvS = 0.0
    private var vocalEnvCoeff = 0.002

    private var eqEnabled = false
    private var eqFilters = emptyArray<BiquadState>()
    private val bassShelf = BiquadState()
    private var virtualizerStrength = 0.0
    private var sideLpCoeff = 0.02
    private var sideLpState = 0.0
    private var targetOutputGain = 1.0
    private var currentOutputGain = 1.0
    private var gainSmoothCoeff = 0.005
    private var limiterEnabled = true
    private var limiterEnv = 0.0
    private var limiterAttackCoeff = 0.03
    private var limiterReleaseCoeff = 0.0004
    private var ditherState = 0x12345678

    fun resetStates() {
        vocalLowLp.resetStates()
        vocalLowHp.resetStates()
        vocalHighLp.resetStates()
        vocalHighHp.resetStates()
        vocalEnvM = 0.0
        vocalEnvS = 0.0
        for (f in eqFilters) f.resetStates()
        bassShelf.resetStates()
        sideLpState = 0.0
        limiterEnv = 0.0
        currentOutputGain = targetOutputGain
    }

    fun configure(sampleRate: Int, channels: Int, snapshot: AudioDspSnapshot) {
        val safeSr = max(8000, sampleRate).toDouble()
        @Suppress("UNUSED_VARIABLE")
        val safeCh = channels.coerceIn(1, MAX_CHANNELS)
        eqEnabled = snapshot.eqEnabled

        gainSmoothCoeff = 1.0 - exp(-1.0 / (0.005 * safeSr))
        limiterAttackCoeff = 1.0 - exp(-1.0 / (0.0008 * safeSr))
        limiterReleaseCoeff = 1.0 - exp(-1.0 / (0.065 * safeSr))
        sideLpCoeff = 1.0 - exp((-2.0 * Math.PI * 160.0) / safeSr)
        vocalEnvCoeff = 1.0 - exp(-1.0 / (0.012 * safeSr))

        vocalMix = snapshot.vocalMix.toDouble().coerceIn(-1.0, 1.0)
        vocalBassGuard = snapshot.vocalBassGuard
        if (abs(vocalMix) >= 0.01) {
            computeBiquad(vocalLowLp, FilterType.LOW_PASS, 190.0, 0.0, 0.7071, enabled = true, safeSr)
            computeBiquad(vocalLowHp, FilterType.HIGH_PASS, 190.0, 0.0, 0.7071, enabled = true, safeSr)
            computeBiquad(vocalHighLp, FilterType.LOW_PASS, 4600.0, 0.0, 0.7071, enabled = true, safeSr)
            computeBiquad(vocalHighHp, FilterType.HIGH_PASS, 4600.0, 0.0, 0.7071, enabled = true, safeSr)
        } else {
            vocalLowLp.active = false
            vocalLowHp.active = false
            vocalHighLp.active = false
            vocalHighHp.active = false
        }

        virtualizerStrength = snapshot.virtualizerStrength.toDouble().coerceIn(0.0, 1.0)
        val outDb = snapshot.outputGainDb.toDouble().coerceIn(-15.0, 15.0)
        targetOutputGain = 10.0.pow(outDb / 20.0)
        limiterEnabled = snapshot.limiterEnabled

        if (snapshot.bassBoostStrength > 0.005f) {
            computeBiquad(
                target = bassShelf,
                type = FilterType.LOW_SHELF,
                freqHz = 68.0,
                gainDb = snapshot.bassBoostStrength.toDouble().coerceIn(0.0, 1.0) * 9.0,
                q = 0.68,
                enabled = true,
                sampleRate = safeSr,
            )
        } else {
            bassShelf.active = false
        }

        val bands = snapshot.bands
        val newFilters = Array(bands.size) { idx ->
            val old = if (idx < eqFilters.size) eqFilters[idx] else BiquadState()
            val band = bands[idx]
            computeBiquad(
                target = old,
                type = band.type,
                freqHz = band.frequencyHz.toDouble(),
                gainDb = band.gainDb.toDouble(),
                q = band.qFactor.toDouble(),
                enabled = band.isEnabled,
                sampleRate = safeSr,
            )
            old
        }
        eqFilters = newFilters
    }

    fun processFloatArray(samples: FloatArray, frameCount: Int, channels: Int): Float {
        val ch = channels.coerceIn(1, MAX_CHANNELS)
        val scratch = DoubleArray(ch)
        for (f in 0 until frameCount) {
            val base = f * ch
            for (c in 0 until ch) {
                scratch[c] = samples[base + c].toDouble()
            }
            processFrame(scratch, ch)
            for (c in 0 until ch) {
                samples[base + c] = scratch[c].toFloat()
            }
        }
        return currentGainReductionDb()
    }

    fun processShortArray(samples: ShortArray, frameCount: Int, channels: Int): Float {
        val ch = channels.coerceIn(1, MAX_CHANNELS)
        val scratch = DoubleArray(ch)
        val invScale = 1.0 / 32768.0
        for (f in 0 until frameCount) {
            val base = f * ch
            for (c in 0 until ch) {
                scratch[c] = samples[base + c].toDouble() * invScale
            }
            processFrame(scratch, ch)
            for (c in 0 until ch) {
                val dither = nextTpdfDither()
                val quantized = kotlin.math.round(scratch[c] * 32767.0 + dither).toInt()
                samples[base + c] = quantized.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return currentGainReductionDb()
    }

    private fun applyVocalSeparator(chSamples: DoubleArray) {
        val mid = 0.5 * (chSamples[0] + chSamples[1])
        val side = 0.5 * (chSamples[0] - chSamples[1])

        val mLow = vocalLowLp.processSample(mid, 0)
        val sLow = vocalLowLp.processSample(side, 1)

        val mUpper = -vocalLowHp.processSample(mid, 0)
        val sUpper = -vocalLowHp.processSample(side, 1)

        val mVocal = vocalHighLp.processSample(mUpper, 0)
        val sVocal = vocalHighLp.processSample(sUpper, 1)

        val mAir = -vocalHighHp.processSample(mUpper, 0)
        val sAir = -vocalHighHp.processSample(sUpper, 1)

        vocalEnvM += vocalEnvCoeff * (abs(mVocal) - vocalEnvM)
        vocalEnvS += vocalEnvCoeff * (abs(sVocal) - vocalEnvS)
        if (vocalEnvM < 1e-24) vocalEnvM = 0.0
        if (vocalEnvS < 1e-24) vocalEnvS = 0.0

        val centerRatio = vocalEnvM / (vocalEnvM + vocalEnvS + 1e-6)

        val outM: Double
        val outS: Double
        if (vocalMix < 0.0) {
            val k = -vocalMix
            val lowKeep = if (vocalBassGuard) 1.0 else (1.0 - 0.55 * k)
            val mSuppress = 1.0 - k * (0.72 + 0.24 * centerRatio)
            outM = mLow * lowKeep + mVocal * mSuppress + mAir * (1.0 - 0.45 * k)
            outS = sLow * (1.0 - 0.10 * k) + sVocal * (1.0 + 0.16 * k) + sAir * (1.0 + 0.08 * k)
        } else {
            val k = vocalMix
            val sSuppress = 1.0 - 0.94 * k
            val vocalPresence = (1.0 + 0.20 * k) * (1.0 - 0.18 * k * (1.0 - centerRatio))
            outM = mLow * (1.0 - 0.94 * k) + mVocal * vocalPresence + mAir * (1.0 - 0.55 * k)
            outS = sLow * sSuppress + sVocal * (1.0 - 0.90 * k) + sAir * sSuppress
        }

        chSamples[0] = outM + outS
        chSamples[1] = outM - outS
    }

    private fun processFrame(chSamples: DoubleArray, ch: Int) {
        if (ch >= 2 && abs(vocalMix) >= 0.01) {
            applyVocalSeparator(chSamples)
        }

        if (eqEnabled) {
            for (filter in eqFilters) {
                if (!filter.active) continue
                for (c in 0 until ch) {
                    chSamples[c] = filter.processSample(chSamples[c], c)
                }
            }
        }

        if (bassShelf.active) {
            for (c in 0 until ch) {
                chSamples[c] = bassShelf.processSample(chSamples[c], c)
            }
        }

        if (ch >= 2 && virtualizerStrength > 0.001) {
            val mid = 0.5 * (chSamples[0] + chSamples[1])
            val side = 0.5 * (chSamples[0] - chSamples[1])
            sideLpState += sideLpCoeff * (side - sideLpState)
            if (abs(sideLpState) < 1e-24) sideLpState = 0.0
            val sideHigh = side - sideLpState
            val widenedSide = sideLpState * (1.0 - 0.18 * virtualizerStrength) +
                sideHigh * (1.0 + 0.58 * virtualizerStrength)
            val balancedMid = mid * (1.0 - 0.06 * virtualizerStrength)
            chSamples[0] = balancedMid + widenedSide
            chSamples[1] = balancedMid - widenedSide
        }

        currentOutputGain += gainSmoothCoeff * (targetOutputGain - currentOutputGain)
        var framePeak = 0.0
        for (c in 0 until ch) {
            chSamples[c] *= currentOutputGain
            framePeak = max(framePeak, abs(chSamples[c]))
        }

        if (limiterEnabled) {
            val envTarget = if (framePeak > LIMITER_THRESHOLD) framePeak else LIMITER_THRESHOLD
            val coeff = if (envTarget > limiterEnv) limiterAttackCoeff else limiterReleaseCoeff
            limiterEnv += coeff * (envTarget - limiterEnv)

            val gain = if (limiterEnv > LIMITER_THRESHOLD) {
                val excess = limiterEnv - LIMITER_THRESHOLD
                val compressed = LIMITER_THRESHOLD + excess / (1.0 + excess * 3.5)
                compressed / limiterEnv
            } else {
                1.0
            }

            for (c in 0 until ch) {
                var s = chSamples[c] * gain
                if (abs(s) > 0.96) {
                    val sign = if (s >= 0.0) 1.0 else -1.0
                    s = sign * (0.96 + 0.035 * tanh((abs(s) - 0.96) / 0.12))
                }
                chSamples[c] = s
            }
        }
    }

    private fun currentGainReductionDb(): Float {
        if (!limiterEnabled || limiterEnv <= LIMITER_THRESHOLD) return 0f
        val excess = limiterEnv - LIMITER_THRESHOLD
        val compressed = LIMITER_THRESHOLD + excess / (1.0 + excess * 3.5)
        val ratio = (compressed / limiterEnv).coerceIn(0.05, 1.0)
        return (-20.0 * kotlin.math.log10(ratio)).toFloat()
    }

    private fun nextTpdfDither(): Double {
        var s = ditherState
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        val u1 = ((s ushr 16) and 0xFFFF) / 65535.0
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        val u2 = ((s ushr 16) and 0xFFFF) / 65535.0
        ditherState = if (s == 0) 0x12345678 else s
        return (u1 - u2) * 0.5
    }

    internal fun computeBiquad(
        target: BiquadState,
        type: FilterType,
        freqHz: Double,
        gainDb: Double,
        q: Double,
        enabled: Boolean,
        sampleRate: Double,
    ) {
        if (!enabled || sampleRate <= 0.0) {
            target.active = false
            return
        }
        val safeSr = max(8000.0, sampleRate)
        val safeFreq = freqHz.coerceIn(12.0, safeSr * 0.485)
        val safeQ = q.coerceIn(0.05, 24.0)
        val safeGain = gainDb.coerceIn(-24.0, 24.0)

        val isShelfOrPeak = type == FilterType.PEAKING ||
            type == FilterType.LOW_SHELF ||
            type == FilterType.HIGH_SHELF
        if (isShelfOrPeak && abs(safeGain) < 0.01) {
            target.active = false
            return
        }

        val w0 = 2.0 * Math.PI * safeFreq / safeSr
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * safeQ)
        val a = 10.0.pow(safeGain / 40.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a0 = 1.0
        var a1 = 0.0
        var a2 = 0.0

        when (type) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
                b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
                a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
                a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
            }
            FilterType.HIGH_SHELF -> {
                b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
                b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
                a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
                a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
            }
            FilterType.LOW_PASS -> {
                b0 = (1.0 - cosW0) * 0.5
                b1 = 1.0 - cosW0
                b2 = (1.0 - cosW0) * 0.5
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1.0 + cosW0) * 0.5
                b1 = -(1.0 + cosW0)
                b2 = (1.0 + cosW0) * 0.5
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
        }

        if (abs(a0) < 1e-12 || !a0.isFinite()) {
            target.active = false
            return
        }

        val invA0 = 1.0 / a0
        val nb0 = b0 * invA0
        val nb1 = b1 * invA0
        val nb2 = b2 * invA0
        val na1 = a1 * invA0
        val na2 = a2 * invA0
        if (!nb0.isFinite() || !nb1.isFinite() || !nb2.isFinite() || !na1.isFinite() || !na2.isFinite()) {
            target.active = false
            return
        }

        target.b0 = nb0
        target.b1 = nb1
        target.b2 = nb2
        target.a1 = na1
        target.a2 = na2
        target.active = true
    }
}
