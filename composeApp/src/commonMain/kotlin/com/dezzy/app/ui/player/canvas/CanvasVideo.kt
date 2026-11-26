/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Vídeo mudo en bucle que rellena su caja (recorte CROP). Repite el trozo que empieza en
 * [loopStartMs] y dura [loopLengthMs] (0 = hasta el final del vídeo). Pausa cuando [playing] es false.
 */
@Composable
expect fun CanvasVideo(
    url: String,
    loopStartMs: Long,
    loopLengthMs: Long,
    playing: Boolean,
    modifier: Modifier,
)
