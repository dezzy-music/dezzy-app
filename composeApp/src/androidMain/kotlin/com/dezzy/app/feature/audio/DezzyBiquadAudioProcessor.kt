/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioProcessor DSP de Dezzy de alta precisión, movido de forma nativa por C++20
 * (`libdezzy_dsp.so`) con actualizaciones de configuración sin bloqueos y un respaldo
 * idéntico de 64 bits en Kotlin.
 *
 * Se encarga de:
 *  - EQ paramétrico multibanda (Biquad de 64 bits TDF-II)
 *  - Shelf analógico de sub-graves (68 Hz)
 *  - Virtualizer estéreo Mid/Side coherente en fase
 *  - Ganancia de salida suavizada (preamp)
 *  - Limitador anti-clip suave de pico real y dither TPDF de 16 bits
 */
@OptIn(UnstableApi::class)
class DezzyBiquadAudioProcessor : AudioProcessor {

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var buffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private var nativeCtxPtr: Long = 0L
    private var appliedVersion: Long = -1L
    private var currentSnapshot: AudioDspSnapshot = DezzyEqManager.snapshot

    private val fallbackEngine = DspFallbackEngine()
    private var scratchFloat = FloatArray(0)
    private var scratchShort = ShortArray(0)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        if (nativeCtxPtr == 0L && NativeDspEngine.isNativeSupported()) {
            nativeCtxPtr = NativeDspEngine.createAudioDspContext()
        }
        appliedVersion = -1L
        syncConfigIfNeeded()
        return outputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        syncConfigIfNeeded()

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        // Bypass bit-perfect rápido cuando no hay ninguna etapa DSP activa
        if (!currentSnapshot.hasActiveProcessing()) {
            buffer.put(inputBuffer)
            buffer.flip()
            outputBuffer = buffer
            DezzyEqManager.reportLimiterGainReduction(0f)
            return
        }

        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT

        // Ruta nativa C++20 de copia cero cuando el buffer de entrada es directo
        if (nativeCtxPtr != 0L && inputBuffer.isDirect) {
            val grDb = NativeDspEngine.processAudioDirect(
                ctxPtr = nativeCtxPtr,
                inBuffer = inputBuffer,
                inOffsetBytes = inputBuffer.position(),
                outBuffer = buffer,
                lengthBytes = remaining,
                isFloat = isFloat,
            )
            if (grDb >= 0f) {
                inputBuffer.position(inputBuffer.position() + remaining)
                buffer.limit(remaining)
                buffer.position(0)
                outputBuffer = buffer
                DezzyEqManager.reportLimiterGainReduction(grDb)
                return
            }
        }

        // Respaldo limpio en Kotlin 64-bit TDF-II (buffers no directos / tests JVM)
        processWithFallback(inputBuffer, remaining, isFloat)
    }

    private fun processWithFallback(inputBuffer: ByteBuffer, remaining: Int, isFloat: Boolean) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        if (isFloat) {
            val totalFloats = remaining / 4
            val frames = totalFloats / channels
            if (scratchFloat.size < totalFloats) {
                scratchFloat = FloatArray(totalFloats)
            }
            inputBuffer.asFloatBuffer().get(scratchFloat, 0, totalFloats)
            val grDb = fallbackEngine.processFloatArray(scratchFloat, frames, channels)
            buffer.asFloatBuffer().put(scratchFloat, 0, totalFloats)
            DezzyEqManager.reportLimiterGainReduction(grDb)
        } else {
            val totalShorts = remaining / 2
            val frames = totalShorts / channels
            if (scratchShort.size < totalShorts) {
                scratchShort = ShortArray(totalShorts)
            }
            inputBuffer.asShortBuffer().get(scratchShort, 0, totalShorts)
            val grDb = fallbackEngine.processShortArray(scratchShort, frames, channels)
            buffer.asShortBuffer().put(scratchShort, 0, totalShorts)
            DezzyEqManager.reportLimiterGainReduction(grDb)
        }

        inputBuffer.position(inputBuffer.position() + remaining)
        buffer.limit(remaining)
        buffer.position(0)
        outputBuffer = buffer
    }

    private fun syncConfigIfNeeded() {
        val latest = DezzyEqManager.snapshot
        if (latest.version == appliedVersion || inputAudioFormat == AudioFormat.NOT_SET) return

        appliedVersion = latest.version
        currentSnapshot = latest

        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val ch = inputAudioFormat.channelCount.coerceIn(1, 8)
        fallbackEngine.configure(sr, ch, latest)

        val ctx = nativeCtxPtr
        if (ctx != 0L) {
            val bands = latest.bands
            val count = bands.size.coerceAtMost(32)
            val types = IntArray(count) { bands[it].type.ordinal }
            val freqs = FloatArray(count) { bands[it].frequencyHz }
            val gains = FloatArray(count) { bands[it].gainDb }
            val qs = FloatArray(count) { bands[it].qFactor }
            val enabledMask = BooleanArray(count) { bands[it].isEnabled }

            NativeDspEngine.configureAudioDspContext(
                ctxPtr = ctx,
                sampleRate = sr,
                channels = ch,
                bandCount = count,
                types = types,
                freqs = freqs,
                gains = gains,
                qs = qs,
                enabledMask = enabledMask,
                eqEnabled = latest.eqEnabled,
                bassStrength = latest.bassBoostStrength,
                virtualizerStrength = latest.virtualizerStrength,
                outputGainDb = latest.outputGainDb,
                limiterEnabled = latest.limiterEnabled,
                vocalMix = latest.vocalMix,
                vocalBassGuard = latest.vocalBassGuard,
            )
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        fallbackEngine.resetStates()
        if (nativeCtxPtr != 0L) {
            NativeDspEngine.resetAudioDspContext(nativeCtxPtr)
        }
        DezzyEqManager.reportLimiterGainReduction(0f)
    }

    override fun reset() {
        flush()
        if (nativeCtxPtr != 0L) {
            NativeDspEngine.destroyAudioDspContext(nativeCtxPtr)
            nativeCtxPtr = 0L
        }
        appliedVersion = -1L
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }
}
