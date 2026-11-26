/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** El escritorio de Compose es sólo de pruebas: no pinta el canvas. */
@Composable
actual fun CanvasVideo(
    url: String,
    loopStartMs: Long,
    loopLengthMs: Long,
    playing: Boolean,
    modifier: Modifier,
) = Unit
