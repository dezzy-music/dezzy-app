/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Navegador embebido para iniciar sesión. Carga [startUrl] y, cuando la cabecera de cookies de
 * [cookieUrl] cumple [isLoggedIn], entrega esa cabecera una sola vez por [onCaptured].
 */
@Composable
expect fun PlatformLoginWebView(
    startUrl: String,
    cookieUrl: String,
    isLoggedIn: (cookieHeader: String) -> Boolean,
    onCaptured: (cookieHeader: String) -> Unit,
    modifier: Modifier = Modifier,
)
