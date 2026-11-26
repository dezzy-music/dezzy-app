/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Navegador embebido que carga [startUrl] y, cuando la propia página hace una petición a una URL que empieza
 * por [headerUrlPrefix] con cabecera `Authorization` (que no sea «Bearer …»), entrega su valor una sola vez
 * por [onToken] (en el hilo principal).
 */
@Composable
expect fun PlatformTokenWebView(
    startUrl: String,
    headerUrlPrefix: String,
    onToken: (String) -> Unit,
    modifier: Modifier = Modifier,
)
