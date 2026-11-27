/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.bubble

/**
 * Pide el permiso «Mostrar sobre otras apps» para la burbuja flotante. Lo rellena la actividad de
 * Android; en escritorio queda en null (allí no hay burbuja).
 */
object OverlayPermission {
    var request: (() -> Unit)? = null
}
