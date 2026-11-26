/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

// La lente óptica de Backdrop necesita Android 13 (API 33)
actual val supportsBackdropLens: Boolean = Build.VERSION.SDK_INT >= 33

// El desenfoque de Haze necesita Android 12 (API 31)
actual val supportsHazeBlur: Boolean = Build.VERSION.SDK_INT >= 31

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < 31) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

actual fun Modifier.backdropLensSurface(
    backdrop: Any,
    shape: Shape,
    palette: LiquidGlassPalette,
    intensity: LiquidGlassIntensity,
    refractionScale: Float,
    enableShadow: Boolean,
    chromaticRim: Boolean,
): Modifier {
    // Desenfoque suave (0.62x) para que los colores y formas de fondo se curven visiblemente por el menisco
    val blurDp = (intensity.blurDp * 0.62f * refractionScale).coerceAtLeast(6f).dp
    val refractionHeightDp = (intensity.refractionDp * 1.15f * refractionScale).dp
    val refractionAmountDp = (intensity.refractionDp * 1.85f * refractionScale).dp
    val surfaceBrush = Brush.verticalGradient(listOf(palette.topFill, palette.bottomFill))

    return this.drawBackdrop(
        backdrop = backdrop as Backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurDp.toPx(), TileMode.Clamp)
            lens(
                refractionHeight = refractionHeightDp.toPx(),
                refractionAmount = refractionAmountDp.toPx(),
                depthEffect = true,
                chromaticAberration = chromaticRim,
            )
        },
        highlight = {
            Highlight.Default.copy(alpha = (intensity.rimAlpha + 0.28f).coerceAtMost(0.95f))
        },
        shadow = if (enableShadow) {
            { Shadow.Default.copy(alpha = if (palette.isDark) 0.34f else 0.18f) }
        } else {
            null
        },
        onDrawSurface = {
            drawRect(brush = surfaceBrush)
            // Destello superior sutil del menisco + rebote cáustico inferior
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = palette.specularAlpha * 0.45f),
                        Color.Transparent,
                        Color(0xFF7AD8FF).copy(alpha = palette.causticAlpha * 0.22f),
                    ),
                ),
            )
        },
    )
}
