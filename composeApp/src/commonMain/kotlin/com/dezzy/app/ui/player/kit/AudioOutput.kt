/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.Composable

/** Salida de audio del sistema (altavoz, auriculares, Bluetooth) para la hoja de dispositivos. */
class AudioOutputControls(
    /** Nombre de la salida externa activa (auriculares, Bluetooth…), o null si suena por el altavoz. */
    val outputName: String?,
    /** Salidas Bluetooth conectadas ahora mismo. */
    val bluetooth: List<String>,
    /** Abre el selector de salida del sistema. */
    val openSwitcher: () -> Unit,
    /** Abre los ajustes de Bluetooth. */
    val openBluetoothSettings: () -> Unit,
    /** Hay selector de salida del sistema en esta plataforma. */
    val supported: Boolean,
)

@Composable
expect fun rememberAudioOutputControls(): AudioOutputControls
