/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.aod

import androidx.compose.runtime.Composable

/** Oculta las barras del sistema mientras [enabled] (modo inmersivo de la pantalla siempre activa). */
@Composable
expect fun ImmersiveSystemBars(enabled: Boolean)
