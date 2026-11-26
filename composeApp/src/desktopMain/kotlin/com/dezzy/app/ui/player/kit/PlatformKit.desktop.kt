/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.Composable

// El escritorio sólo corre las pruebas: sin carátulas, espectro ni sistema.

actual suspend fun loadArtPalette(url: String?, maxColors: Int): ArtPalette? = null

internal actual fun createSpectrumSource(audioSessionId: Int, onFft: (ByteArray) -> Unit): SpectrumSource? = null

@Composable
actual fun rememberRecordAudioPermission(): RecordAudioPermission = RecordAudioPermission(false) {}

@Composable
actual fun rememberPowerSaveMode(): Boolean = false

@Composable
actual fun rememberBatteryLevel(): Int? = null

@Composable
actual fun rememberShareText(): (String) -> Unit = {}

@Composable
actual fun rememberPhoneDeviceName(): String = "Este equipo"
