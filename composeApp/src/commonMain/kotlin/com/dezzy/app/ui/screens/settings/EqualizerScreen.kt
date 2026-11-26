/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.audio.AudioEffects
import com.dezzy.app.feature.audio.AudioFxState
import com.dezzy.app.feature.audio.EQ_BAND_FREQUENCIES
import com.dezzy.app.feature.audio.EQ_PRESETS
import com.dezzy.app.feature.audio.PREAMP_MAX_DB
import com.dezzy.app.feature.audio.eqBandLabel
import com.dezzy.app.ui.screens.ScreenContext
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

private fun formatDb(value: Float): String {
    val tenths = (value * 10f).roundToInt()
    val sign = if (tenths > 0) "+" else if (tenths < 0) "-" else ""
    val magnitude = abs(tenths)
    return "$sign${magnitude / 10},${magnitude % 10} dB"
}

private fun vocalMixLabel(mix: Float): String {
    val percent = (abs(mix) * 100f).roundToInt()
    return when {
        abs(mix) < 0.02f -> "Original"
        mix < 0f -> "Instrumental $percent %"
        else -> "Solo voz $percent %"
    }
}

private fun hiResSubtitle(state: AudioFxState): String? = when {
    !state.processingSupported -> "El procesado de audio sólo funciona en Android"
    !state.nativeAvailable -> "Usando el motor de respaldo"
    else -> null
}

@Composable
internal fun EqualizerScreen(ctx: ScreenContext) {
    val fx: AudioEffects = koinInject()
    val state by fx.state.collectAsState()

    SettingsPage(title = "Ecualizador", onBack = { ctx.navigator.back() }) {
        item(key = "engine") {
            SettingsGroup("Motor") {
                SettingsSwitchRow(
                    title = "Motor de audio Hi-Res (32-bit float)",
                    subtitle = (hiResSubtitle(state) ?: "Se aplica al volver a abrir la app"),
                    icon = Icons.Rounded.HighQuality,
                    checked = state.hiResEnabled,
                    onCheckedChange = fx::setHiResEnabled,
                    enabled = state.processingSupported,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Ecualizador",
                    subtitle = if (state.processingSupported && !state.hiResEnabled) {
                        "Activa el motor Hi-Res para oír los cambios"
                    } else null,
                    icon = Icons.Rounded.Equalizer,
                    checked = state.eqEnabled,
                    onCheckedChange = fx::setEqEnabled,
                )
            }
        }
        item(key = "presets") {
            SettingsGroup("Presets") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EQ_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = state.eqEnabled && state.bandGainsDb == preset.gainsDb,
                            onClick = { fx.applyPreset(preset) },
                            label = { Text(preset.name) },
                        )
                    }
                }
            }
        }
        item(key = "bands") {
            SettingsGroup("Bandas") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(280.dp)
                        .padding(horizontal = 8.dp),
                ) {
                    EQ_BAND_FREQUENCIES.forEachIndexed { index, hz ->
                        GraphicEqFader(
                            label = eqBandLabel(hz),
                            gainDb = state.bandGainsDb.getOrElse(index) { 0f },
                            onGainChange = { fx.setBandGain(index, it) },
                        )
                    }
                }
            }
        }
        item(key = "effects") {
            SettingsGroup("Efectos") {
                SettingsSliderRow(
                    title = "Sub-bass",
                    icon = Icons.Rounded.Speaker,
                    value = state.bassStrength,
                    valueRange = 0f..1f,
                    steps = 0,
                    valueLabel = { "${(it * 100f).roundToInt()} %" },
                    onValueChangeFinished = fx::setBassBoost,
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Virtualizer estéreo",
                    icon = Icons.Rounded.SurroundSound,
                    value = state.virtualizerStrength,
                    valueRange = 0f..1f,
                    steps = 0,
                    valueLabel = { "${(it * 100f).roundToInt()} %" },
                    onValueChangeFinished = fx::setVirtualizer,
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Preamp",
                    icon = Icons.Rounded.Tune,
                    value = state.preampDb,
                    valueRange = -PREAMP_MAX_DB..PREAMP_MAX_DB,
                    steps = 0,
                    valueLabel = ::formatDb,
                    onValueChangeFinished = fx::setPreampDb,
                )
            }
        }
        item(key = "vocal") {
            SettingsGroup("Separador vocal") {
                SettingsSliderRow(
                    title = "Instrumental ↔ Solo voz",
                    icon = Icons.Rounded.Mic,
                    value = state.vocalMix,
                    valueRange = -1f..1f,
                    steps = 0,
                    valueLabel = ::vocalMixLabel,
                    onValueChangeFinished = fx::setVocalMix,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Proteger los graves",
                    subtitle = "Mantiene el bombo y el bajo en modo instrumental",
                    checked = state.vocalBassGuard,
                    onCheckedChange = fx::setVocalBassGuard,
                )
            }
        }
        item(key = "limiter") {
            SettingsGroup("Salida") {
                SettingsSwitchRow(
                    title = "Limitador anti-clip",
                    subtitle = if (state.limiterGainReductionDb >= 0.1f) {
                        "Reducción: ${formatDb(-state.limiterGainReductionDb)}"
                    } else null,
                    icon = Icons.Rounded.Compress,
                    checked = state.limiterEnabled,
                    onCheckedChange = fx::setLimiterEnabled,
                )
                SettingsDivider()
                SettingsRow(
                    title = "Restablecer",
                    subtitle = "Ecualizador y efectos a su valor de fábrica",
                    icon = Icons.Rounded.RestartAlt,
                    onClick = fx::reset,
                )
            }
        }
    }
}
