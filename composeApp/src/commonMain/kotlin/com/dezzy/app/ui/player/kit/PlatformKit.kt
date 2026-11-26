/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ── Colores de la carátula ───────────────────────────────────────────────────────────────────

data class Swatch(val rgb: Int, val population: Int)

/** Paleta sacada de la carátula (vibrante, apagado, claro, oscuro y dominante). */
class ArtPalette(
    val vibrantSwatch: Swatch?,
    val darkVibrantSwatch: Swatch?,
    val lightVibrantSwatch: Swatch?,
    val mutedSwatch: Swatch?,
    val darkMutedSwatch: Swatch?,
    val lightMutedSwatch: Swatch?,
    val dominantSwatch: Swatch?,
    val swatches: List<Swatch>,
) {
    fun getDominantColor(default: Int) = dominantSwatch?.rgb ?: default
    fun getVibrantColor(default: Int) = vibrantSwatch?.rgb ?: default
    fun getDarkVibrantColor(default: Int) = darkVibrantSwatch?.rgb ?: default
    fun getLightVibrantColor(default: Int) = lightVibrantSwatch?.rgb ?: default
    fun getMutedColor(default: Int) = mutedSwatch?.rgb ?: default
    fun getDarkMutedColor(default: Int) = darkMutedSwatch?.rgb ?: default
    fun getLightMutedColor(default: Int) = lightMutedSwatch?.rgb ?: default
}

/** Carga la carátula y saca su paleta; null si no se pudo (o en escritorio). */
expect suspend fun loadArtPalette(url: String?, maxColors: Int = 16): ArtPalette?

// ── Espectro de audio ────────────────────────────────────────────────────────────────────────

/** Fuente de FFT de la plataforma (Visualizer de Android sobre la sesión del reproductor). */
interface SpectrumSource {
    fun release()
}

/** Crea la fuente; [onFft] recibe el FFT en el formato de Android (re0, reN/2, re1, im1, …). */
internal expect fun createSpectrumSource(audioSessionId: Int, onFft: (ByteArray) -> Unit): SpectrumSource?

/**
 * Bandas del espectro en vivo (0–1) para los visualizadores. Bandas logarítmicas, con subida
 * rápida y caída suave para que se vean como un ecualizador real.
 */
class AudioSpectrumEngine {
    companion object {
        const val BANDS = 32
        private const val ATTACK = 0.6f
        private const val RELEASE = 0.15f
    }

    val bands = FloatArray(BANDS)
    var loudness = 0f
        private set
    private var source: SpectrumSource? = null
    private var edges: IntArray? = null

    fun start(audioSessionId: Int): Boolean {
        release()
        if (audioSessionId <= 0) return false
        source = runCatching { createSpectrumSource(audioSessionId, ::process) }.getOrNull()
        return source != null
    }

    fun release() {
        source?.release()
        source = null
        bands.fill(0f)
        loudness = 0f
    }

    private fun process(fft: ByteArray) {
        val bins = fft.size / 2
        if (bins < BANDS + 2) return
        val e = edges?.takeIf { it.size == BANDS + 1 && it.last() <= bins } ?: logEdges(bins).also { edges = it }
        var sum = 0f
        for (b in 0 until BANDS) {
            var peak = 0f
            for (k in e[b] until max(e[b + 1], e[b] + 1)) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                peak = max(peak, sqrt(re * re + im * im))
            }
            val target = (ln(1f + peak) / ln(129f)).coerceIn(0f, 1f)
            val k = if (target > bands[b]) ATTACK else RELEASE
            bands[b] += (target - bands[b]) * k
            sum += bands[b]
        }
        loudness = sum / BANDS
    }

    /** Límites de bandas en escala logarítmica entre el bin 1 y el último. */
    private fun logEdges(bins: Int): IntArray {
        val last = bins - 1
        return IntArray(BANDS + 1) { i ->
            val f = (last.toFloat()).pow(i / BANDS.toFloat())
            f.toInt().coerceIn(1, last)
        }.also { arr -> for (i in 1 until arr.size) if (arr[i] <= arr[i - 1]) arr[i] = (arr[i - 1] + 1).coerceAtMost(last) }
    }
}

// ── Permisos y sistema ───────────────────────────────────────────────────────────────────────

/** Permiso de micrófono (Android lo exige para leer el espectro del reproductor). */
class RecordAudioPermission(val granted: Boolean, val request: () -> Unit)

@Composable
expect fun rememberRecordAudioPermission(): RecordAudioPermission

/** Ahorro de batería activo (los visualizadores bajan a ~30 fps). */
@Composable
expect fun rememberPowerSaveMode(): Boolean

/** Nivel de batería 0–100, o null si no se sabe. */
@Composable
expect fun rememberBatteryLevel(): Int?

/** Abre la hoja de compartir del sistema con un texto. */
@Composable
expect fun rememberShareText(): (String) -> Unit

/** Pantalla más ancha que alta. */
@Composable
fun isLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

/** Nombre de este dispositivo tal como lo ve el usuario («Pixel 8 de …»). */
@Composable
expect fun rememberPhoneDeviceName(): String
