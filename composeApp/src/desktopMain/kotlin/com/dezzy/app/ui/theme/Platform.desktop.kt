/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

// En escritorio no hay lente óptica de Backdrop
actual val supportsBackdropLens: Boolean = false

actual val supportsHazeBlur: Boolean = true

// En escritorio no hay colores dinámicos del sistema
@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

actual fun Modifier.backdropLensSurface(
    backdrop: Any,
    shape: Shape,
    palette: LiquidGlassPalette,
    intensity: LiquidGlassIntensity,
    refractionScale: Float,
    enableShadow: Boolean,
    chromaticRim: Boolean,
): Modifier = this
