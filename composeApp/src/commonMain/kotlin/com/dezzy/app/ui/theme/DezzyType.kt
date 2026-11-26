/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

/** Tipografía normal de Dezzy (fuente por defecto del sistema). */
val DezzyTypography = base.copy(
    // Títulos de pantalla
    headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    // Títulos de sección
    titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    // Filas
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    // Navegación
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)
