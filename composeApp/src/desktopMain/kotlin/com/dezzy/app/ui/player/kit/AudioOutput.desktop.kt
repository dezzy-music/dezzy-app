/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberAudioOutputControls(): AudioOutputControls = remember {
    AudioOutputControls(
        outputName = null,
        bluetooth = emptyList(),
        openSwitcher = {},
        openBluetoothSettings = {},
        supported = false,
    )
}
