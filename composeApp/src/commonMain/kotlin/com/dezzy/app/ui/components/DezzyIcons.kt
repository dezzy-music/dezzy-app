/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Iconos propios de Dezzy que no están en Material. */
object DezzyIcons {
    private fun shelf(name: String, width: Float): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
            ) {
                // Dos lomos de pie y uno inclinado: «| | \».
                moveTo(5f, 4f); lineTo(5f, 20f)
                moveTo(10f, 4f); lineTo(10f, 20f)
                moveTo(14.5f, 4.8f); lineTo(19.5f, 19.4f)
            }
        }.build()

    /** «Tu biblioteca» sin seleccionar. */
    val Library: ImageVector by lazy { shelf("DezzyLibrary", 2f) }

    /** «Tu biblioteca» seleccionada (trazo más grueso). */
    val LibraryFilled: ImageVector by lazy { shelf("DezzyLibraryFilled", 2.8f) }
}
