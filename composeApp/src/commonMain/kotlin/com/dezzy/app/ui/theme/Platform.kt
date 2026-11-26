/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** True si la plataforma puede usar la lente óptica de Backdrop (Android 13+). */
expect val supportsBackdropLens: Boolean

/** True si la plataforma puede usar el desenfoque de Haze (Android 12+ y escritorio). */
expect val supportsHazeBlur: Boolean

/** Esquema de color dinámico del sistema (Material You), o null si no hay. */
@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?

/**
 * Superficie de cristal líquido con lente óptica real (solo Android). En el resto
 * de plataformas devuelve el modificador sin cambios. [backdrop] debe ser un
 * com.kyant.backdrop.Backdrop en Android.
 */
expect fun Modifier.backdropLensSurface(
    backdrop: Any,
    shape: Shape,
    palette: LiquidGlassPalette,
    intensity: LiquidGlassIntensity,
    refractionScale: Float,
    enableShadow: Boolean,
    chromaticRim: Boolean,
): Modifier
