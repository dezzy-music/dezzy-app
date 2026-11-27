/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import kotlinx.coroutines.flow.MutableStateFlow

/** Enlace «dezzy://together?code=XXXX» recibido desde fuera; AppShell lo consume y abre la sala. */
object TogetherDeepLinks {
    val pending = MutableStateFlow<String?>(null)

    /** Saca el código de un enlace o lo devuelve tal cual si ya es un código. */
    fun codeOf(link: String): String? {
        val raw = link.trim()
        if (raw.isEmpty()) return null
        val code = if (raw.contains("code=")) raw.substringAfter("code=").substringBefore('&') else raw
        return code.trim().uppercase().takeIf { it.isNotEmpty() && it.length <= 16 }
    }
}
