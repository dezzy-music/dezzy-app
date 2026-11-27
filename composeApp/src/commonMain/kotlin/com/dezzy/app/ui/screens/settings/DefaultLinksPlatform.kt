/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.runtime.Composable

/**
 * Devuelve una función que abre los ajustes de «Abrir enlaces predeterminados» del sistema para la app,
 * o null si la plataforma no lo soporta (escritorio: la fila se oculta).
 */
@Composable
expect fun rememberOpenDefaultLinksSettings(): (() -> Unit)?
