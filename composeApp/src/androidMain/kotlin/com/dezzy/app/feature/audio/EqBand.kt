/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import kotlinx.serialization.Serializable

/**
 * Representa una banda del Ecualizador Paramétrico.
 * Sigue los estándares de DSP profesional.
 */
@Serializable
data class EqBand(
    val id: Int,
    val type: FilterType = FilterType.PEAKING,
    var frequencyHz: Float, // Ej: 60Hz, 1000Hz
    var gainDb: Float,      // Ej: -15.0dB a +15.0dB
    var qFactor: Float,     // Ej: 0.707 (Butterworth)
    var isEnabled: Boolean = true
)

@Serializable
enum class FilterType {
    PEAKING,
    LOW_SHELF,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS
}
