/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

/** Estilo de interfaz de la app. */
enum class AppInterfaceTheme { NORMAL, LIQUID_GLASS, NOTHING }

/** Intensidad del cristal líquido: desenfoque, refracción y brillo del borde. */
enum class LiquidGlassIntensity(
    val blurDp: Float,
    val refractionDp: Float,
    val surfaceAlpha: Float,
    val rimAlpha: Float,
) {
    SUBTLE(blurDp = 14f, refractionDp = 8f, surfaceAlpha = 0.22f, rimAlpha = 0.28f),
    PURE(blurDp = 22f, refractionDp = 14f, surfaceAlpha = 0.30f, rimAlpha = 0.44f),
    INTENSE(blurDp = 32f, refractionDp = 20f, surfaceAlpha = 0.40f, rimAlpha = 0.62f),
}

/** Modo claro/oscuro elegido por el usuario. */
enum class DarkMode { SYSTEM, LIGHT, DARK }

val LocalAppInterfaceTheme = compositionLocalOf { AppInterfaceTheme.NORMAL }
val LocalLiquidGlassIntensity = compositionLocalOf { LiquidGlassIntensity.PURE }

/** En Android contendrá un com.kyant.backdrop.Backdrop; en otras plataformas queda en null. */
val LocalLiquidGlassBackdrop = compositionLocalOf<Any?> { null }
val LocalLiquidGlassHazeState = compositionLocalOf<HazeState?> { null }
val LocalDisableBlur = compositionLocalOf { false }
