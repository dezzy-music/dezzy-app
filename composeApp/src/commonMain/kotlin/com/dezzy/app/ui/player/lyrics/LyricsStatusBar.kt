/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.lyrics

import androidx.compose.runtime.Composable

/** Oculta la barra de estado mientras [hide] sea true y la restaura al salir de la composición. */
@Composable
expect fun HideStatusBarEffect(hide: Boolean)
