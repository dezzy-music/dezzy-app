/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.runtime.Composable

/** Activa o quita FLAG_SECURE de la ventana (Android); en el escritorio no hace nada. No se quita al salir. */
@Composable
internal expect fun ApplyWindowSecure(enabled: Boolean)
