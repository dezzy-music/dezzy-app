/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.sp

private val base = Typography()

/** Fuente de la app (familias del sistema, sin descargar nada). */
enum class AppFont(val label: String) {
    DEFAULT("Predeterminada"),
    SERIF("Serif"),
    MONOSPACE("Monoespaciada"),
    ROUNDED("Manuscrita"),
}

val AppFontKey = stringPreferencesKey("appFont")

private fun AppFont.family(): FontFamily? = when (this) {
    AppFont.DEFAULT -> null
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
    // Compose común no trae «sans-serif-rounded»; la familia más cercana disponible es la cursiva del sistema.
    AppFont.ROUNDED -> FontFamily.Cursive
}

/** Aplica la fuente elegida a todos los estilos de la tipografía. */
fun Typography.withAppFont(font: AppFont): Typography {
    val f = font.family() ?: return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = f),
        displayMedium = displayMedium.copy(fontFamily = f),
        displaySmall = displaySmall.copy(fontFamily = f),
        headlineLarge = headlineLarge.copy(fontFamily = f),
        headlineMedium = headlineMedium.copy(fontFamily = f),
        headlineSmall = headlineSmall.copy(fontFamily = f),
        titleLarge = titleLarge.copy(fontFamily = f),
        titleMedium = titleMedium.copy(fontFamily = f),
        titleSmall = titleSmall.copy(fontFamily = f),
        bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f),
        bodySmall = bodySmall.copy(fontFamily = f),
        labelLarge = labelLarge.copy(fontFamily = f),
        labelMedium = labelMedium.copy(fontFamily = f),
        labelSmall = labelSmall.copy(fontFamily = f),
    )
}

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
