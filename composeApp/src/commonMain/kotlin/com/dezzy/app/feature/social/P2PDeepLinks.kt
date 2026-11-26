/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import kotlinx.coroutines.flow.MutableStateFlow

/** Lo que pide abrir una notificación: clave del chat, "" = la pestaña Juntos. La consume AppShell. */
object P2PDeepLinks {
    val pending = MutableStateFlow<String?>(null)
}
