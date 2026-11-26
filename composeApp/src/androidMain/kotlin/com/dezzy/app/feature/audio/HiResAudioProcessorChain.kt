/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Cadena de procesado del «Motor de audio Hi-Res» (Ajustes > Reproductor y audio).
 *
 * Con la salida float activada, ExoPlayer entrega el audio a los procesadores
 * en PCM float de 32 bits. La cadena por defecto de Media3 incluye
 * SilenceSkippingAudioProcessor, que solo acepta PCM de 16 bits y haría fallar
 * la reproducción; esta cadena usa procesadores nativos en float de 32/64 bits:
 * DezzyBiquadAudioProcessor (EQ paramétrico C++20, Sub-Bass, Virtualizer M/S,
 * Ganancia y Limitador Anti-Clip) y Sonic (velocidad / tono).
 *
 * @param includeSonic cuando es false, Sonic no entra en la cadena.
 */
@OptIn(UnstableApi::class)
class HiResAudioProcessorChain(
    private val includeSonic: Boolean = true,
) : DefaultAudioSink.AudioProcessorChain {

    private val sonic = SonicAudioProcessor()
    private val dezzyDsp = DezzyBiquadAudioProcessor()
    private val processors = if (includeSonic) {
        arrayOf<AudioProcessor>(dezzyDsp, sonic)
    } else {
        arrayOf<AudioProcessor>(dezzyDsp)
    }

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        if (includeSonic) {
            sonic.setSpeed(playbackParameters.speed)
            sonic.setPitch(playbackParameters.pitch)
        }
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long =
        if (includeSonic && sonic.isActive) sonic.getMediaDuration(playoutDuration) else playoutDuration

    override fun getSkippedOutputFrameCount(): Long = 0L
}
