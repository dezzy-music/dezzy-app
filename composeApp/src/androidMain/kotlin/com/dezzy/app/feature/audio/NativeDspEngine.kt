/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import android.util.Log
import java.nio.ByteBuffer

/**
 * Puente JNI con el motor C++20 (`libdezzy_dsp.so`):
 *  - EQ paramétrico de 64 bits TDF-II, Sub-Bass, Virtualizer Mid/Side, preamp y limitador
 *    suave anti-clip (copia cero sobre DirectByteBuffer).
 *  - FFT de espectro de alta velocidad y proyección de la esfera 3D del visualizador.
 */
object NativeDspEngine {

    private const val TAG = "NativeDspEngine"

    private var isLoaded = false

    init {
        try {
            System.loadLibrary("dezzy_dsp")
            isLoaded = true
            Log.i(TAG, "libdezzy_dsp.so cargada en memoria")
        } catch (t: Throwable) {
            Log.w(TAG, "No se encontró la librería nativa o falló al cargar. Se usa el motor de respaldo en Kotlin.", t)
            isLoaded = false
        }
    }

    fun isNativeSupported(): Boolean = isLoaded

    fun createAudioDspContext(): Long =
        if (isLoaded) runCatching { createAudioDspContextNative() }.getOrDefault(0L) else 0L

    fun destroyAudioDspContext(ctxPtr: Long) {
        if (isLoaded && ctxPtr != 0L) {
            runCatching { destroyAudioDspContextNative(ctxPtr) }
        }
    }

    fun resetAudioDspContext(ctxPtr: Long) {
        if (isLoaded && ctxPtr != 0L) {
            runCatching { resetAudioDspContextNative(ctxPtr) }
        }
    }

    fun configureAudioDspContext(
        ctxPtr: Long,
        sampleRate: Int,
        channels: Int,
        bandCount: Int,
        types: IntArray,
        freqs: FloatArray,
        gains: FloatArray,
        qs: FloatArray,
        enabledMask: BooleanArray,
        eqEnabled: Boolean,
        bassStrength: Float,
        virtualizerStrength: Float,
        outputGainDb: Float,
        limiterEnabled: Boolean,
        vocalMix: Float,
        vocalBassGuard: Boolean,
    ) {
        if (!isLoaded || ctxPtr == 0L) return
        runCatching {
            configureAudioDspContextNative(
                ctxPtr = ctxPtr,
                sampleRate = sampleRate,
                channels = channels,
                bandCount = bandCount,
                typesArray = types,
                freqsArray = freqs,
                gainsArray = gains,
                qsArray = qs,
                enabledArray = enabledMask,
                eqEnabled = eqEnabled,
                bassStrength = bassStrength,
                virtualizerStrength = virtualizerStrength,
                outputGainDb = outputGainDb,
                limiterEnabled = limiterEnabled,
                vocalMix = vocalMix,
                vocalBassGuard = vocalBassGuard,
            )
        }
    }

    /**
     * Devuelve la reducción de ganancia en dB (>= 0), o -1f si el buffer no es directo / no hay nativo.
     */
    fun processAudioDirect(
        ctxPtr: Long,
        inBuffer: ByteBuffer,
        inOffsetBytes: Int,
        outBuffer: ByteBuffer,
        lengthBytes: Int,
        isFloat: Boolean,
    ): Float {
        if (!isLoaded || ctxPtr == 0L || !inBuffer.isDirect || !outBuffer.isDirect) return -1f
        return processAudioDirectNative(
            ctxPtr = ctxPtr,
            inDirectBuffer = inBuffer,
            inOffsetBytes = inOffsetBytes,
            outDirectBuffer = outBuffer,
            lengthBytes = lengthBytes,
            isFloat = isFloat,
        )
    }

    external fun createAudioDspContextNative(): Long

    external fun destroyAudioDspContextNative(ctxPtr: Long)

    external fun resetAudioDspContextNative(ctxPtr: Long)

    external fun configureAudioDspContextNative(
        ctxPtr: Long,
        sampleRate: Int,
        channels: Int,
        bandCount: Int,
        typesArray: IntArray,
        freqsArray: FloatArray,
        gainsArray: FloatArray,
        qsArray: FloatArray,
        enabledArray: BooleanArray,
        eqEnabled: Boolean,
        bassStrength: Float,
        virtualizerStrength: Float,
        outputGainDb: Float,
        limiterEnabled: Boolean,
        vocalMix: Float,
        vocalBassGuard: Boolean,
    )

    external fun processAudioDirectNative(
        ctxPtr: Long,
        inDirectBuffer: ByteBuffer,
        inOffsetBytes: Int,
        outDirectBuffer: ByteBuffer,
        lengthBytes: Int,
        isFloat: Boolean,
    ): Float

    external fun initSphereNative(pointCount: Int)

    external fun computeSpherePointsNative(
        outBuffer: FloatArray,
        timeSec: Float,
        bandsArray: FloatArray,
        loudness: Float,
        cx: Float,
        cy: Float,
        baseR: Float,
        orbAlpha: Float
    )

    external fun processFftNative(
        fftByteArray: ByteArray,
        inOutBands: FloatArray,
        prevLoudness: Float
    ): Float
}
