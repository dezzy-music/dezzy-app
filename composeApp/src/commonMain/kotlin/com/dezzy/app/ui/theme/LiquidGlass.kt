/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
@ReadOnlyComposable
fun isLiquidGlassTheme(): Boolean = LocalAppInterfaceTheme.current == AppInterfaceTheme.LIQUID_GLASS

/**
 * Fondo ambiental de malla luminosa que se coloca detrás de las pantallas cuando el cristal
 * líquido está activo. Da a cada superficie de cristal color y luz reales para refractar,
 * sin grabar la capa gráfica de forma circular.
 */
@Composable
fun AmbientLiquidGlassBackground(
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val primaryGlow = colorScheme.primary
    val tertiaryGlow = colorScheme.tertiary

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseColor = when {
                pureBlack -> Color(0xFF030408)
                isDark -> Color(0xFF060911)
                else -> Color(0xFFE9EFF8)
            }
            drawRect(color = baseColor)

            val alphaScale = when {
                pureBlack -> 0.55f
                isDark -> 1f
                else -> 0.85f
            }

            // Fuente de luz óptica cian/primaria arriba a la izquierda
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = (if (isDark) 0.30f else 0.34f) * alphaScale),
                        Color(0xFF24A8FF).copy(alpha = (if (isDark) 0.14f else 0.18f) * alphaScale),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.14f),
                    radius = size.maxDimension * 0.48f,
                ),
                center = Offset(size.width * 0.18f, size.height * 0.14f),
                radius = size.maxDimension * 0.48f,
            )

            // Resplandor cobalto/índigo arriba a la derecha
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4F7CFF).copy(alpha = (if (isDark) 0.26f else 0.28f) * alphaScale),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.28f),
                    radius = size.maxDimension * 0.44f,
                ),
                center = Offset(size.width * 0.88f, size.height * 0.28f),
                radius = size.maxDimension * 0.44f,
            )

            // Ancla cáustica esmeralda/terciaria abajo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryGlow.copy(alpha = (if (isDark) 0.24f else 0.26f) * alphaScale),
                        Color(0xFF28D68D).copy(alpha = (if (isDark) 0.12f else 0.16f) * alphaScale),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.68f, size.height * 0.78f),
                    radius = size.maxDimension * 0.50f,
                ),
                center = Offset(size.width * 0.68f, size.height * 0.78f),
                radius = size.maxDimension * 0.50f,
            )
        }
    }
}

/**
 * Calcula el tinte adaptativo de la superficie y los reflejos prismáticos de Fresnel del borde
 * para el cristal líquido estilo Apple iOS 26, calibrado para alta claridad en el centro
 * (75-92%) y refracción moldeada en los bordes.
 */
@Composable
fun rememberLiquidGlassPalette(
    prominent: Boolean = false,
    tintColor: Color = Color.Unspecified,
): LiquidGlassPalette {
    val colorScheme = MaterialTheme.colorScheme
    val intensity = LocalLiquidGlassIntensity.current
    val isDark = colorScheme.surface.luminance() < 0.5f

    val baseTint = when {
        tintColor != Color.Unspecified -> tintColor
        prominent -> colorScheme.primary
        else -> Color.White
    }

    // Relleno central ultra claro para que la distorsión y la vibrancia del fondo se vean, sin gris turbio
    val topFillAlpha = when {
        prominent -> (0.14f + intensity.surfaceAlpha * 0.48f).coerceAtMost(0.42f)
        isDark -> (0.04f + intensity.surfaceAlpha * 0.26f).coerceAtMost(0.16f)
        else -> (0.28f + intensity.surfaceAlpha * 0.45f).coerceAtMost(0.56f)
    }
    val bottomFillAlpha = when {
        prominent -> (0.05f + intensity.surfaceAlpha * 0.24f).coerceAtMost(0.24f)
        isDark -> (0.015f + intensity.surfaceAlpha * 0.08f).coerceAtMost(0.06f)
        else -> (0.12f + intensity.surfaceAlpha * 0.22f).coerceAtMost(0.28f)
    }

    val rimTopAlpha = if (isDark) {
        (0.32f + intensity.rimAlpha * 0.72f).coerceAtMost(0.92f)
    } else {
        (0.76f + intensity.rimAlpha * 0.24f).coerceAtMost(0.98f)
    }
    val rimBottomAlpha = if (isDark) {
        (0.14f + intensity.rimAlpha * 0.36f).coerceAtMost(0.52f)
    } else {
        (0.48f + intensity.rimAlpha * 0.32f).coerceAtMost(0.84f)
    }
    val chromaAlpha = if (isDark) {
        (0.24f + intensity.rimAlpha * 0.72f).coerceAtMost(0.86f)
    } else {
        0.18f
    }

    val contentColor = when {
        prominent && tintColor == Color.Unspecified && !isDark -> Color(0xFF0A0F19)
        prominent && tintColor != Color.Unspecified -> Color.White
        isDark -> Color(0xFFF6F8FF)
        else -> Color(0xFF0B0E17)
    }

    // Bisel prismático de Fresnel a 135 grados en oscuro; borde de estudio vertical en claro
    val borderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rimTopAlpha),
                Color(0xFF68DEFF).copy(alpha = chromaAlpha),
                Color.White.copy(alpha = 0.07f),
                Color(0xFFFFC778).copy(alpha = chromaAlpha * 0.50f),
                Color(0xFF8BE2FF).copy(alpha = rimBottomAlpha),
            ),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = rimTopAlpha),
                Color.White.copy(alpha = 0.28f),
                Color(0xFFE2ECFF).copy(alpha = rimBottomAlpha),
            ),
        )
    }

    return LiquidGlassPalette(
        baseTint = baseTint,
        topFill = baseTint.copy(alpha = topFillAlpha),
        bottomFill = baseTint.copy(alpha = bottomFillAlpha),
        borderBrush = borderBrush,
        specularAlpha = if (isDark) (0.14f + intensity.rimAlpha * 0.36f) else (0.28f + intensity.rimAlpha * 0.42f),
        causticAlpha = if (isDark) (0.12f + intensity.rimAlpha * 0.30f) else 0.45f,
        contentColor = contentColor,
        isDark = isDark,
    )
}

data class LiquidGlassPalette(
    val baseTint: Color,
    val topFill: Color,
    val bottomFill: Color,
    val borderBrush: Brush,
    val specularAlpha: Float,
    val causticAlpha: Float,
    val contentColor: Color,
    val isDark: Boolean,
)

/**
 * Modificador óptico de cristal líquido estilo Apple iOS 26 (Pro v2).
 *
 * - Nivel 1 (lente Backdrop disponible, Android 13+): refracción óptica real de menisco con
 *   aberración cromática, vibrancia, desenfoque, borde prismático de Fresnel y sombra cáustica.
 * - Nivel 2 (Haze disponible, Android 12+ y escritorio): desenfoque esmerilado del fondo con Haze
 *   más borde de bisel moldeado.
 * - Nivel 3 (sin soporte, diálogos o desenfoque desactivado): brillo de cristal translúcido
 *   moldeado (nunca un gris plano).
 */
fun Modifier.liquidGlassSurface(
    shape: Shape,
    prominent: Boolean = false,
    tintColor: Color = Color.Unspecified,
    useBackdrop: Boolean = true,
    enableShadow: Boolean = true,
    refractionScale: Float = 1f,
    borderWidth: Dp = 1.05.dp,
    chromaticRim: Boolean = true,
): Modifier = composed {
    val intensity = LocalLiquidGlassIntensity.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val hazeState = LocalLiquidGlassHazeState.current
    val disableBlur = LocalDisableBlur.current
    val palette = rememberLiquidGlassPalette(prominent = prominent, tintColor = tintColor)

    val canUseBackdrop = useBackdrop && backdrop != null && !disableBlur && supportsBackdropLens
    val canUseHaze = useBackdrop && !canUseBackdrop && hazeState != null && !disableBlur && supportsHazeBlur

    when {
        canUseBackdrop -> {
            this
                .backdropLensSurface(
                    backdrop = backdrop!!,
                    shape = shape,
                    palette = palette,
                    intensity = intensity,
                    refractionScale = refractionScale,
                    enableShadow = enableShadow,
                    chromaticRim = chromaticRim,
                )
                .border(width = borderWidth, brush = palette.borderBrush, shape = shape)
        }

        canUseHaze -> {
            val fallbackBase = if (prominent) palette.baseTint else MaterialTheme.colorScheme.surfaceContainer
            this
                .clip(shape)
                .hazeEffect(
                    state = hazeState!!,
                    style = HazeStyle(
                        backgroundColor = fallbackBase.copy(alpha = 0.25f),
                        tint = HazeTint(palette.topFill),
                        blurRadius = (intensity.blurDp * 0.72f).dp,
                        noiseFactor = 0.03f,
                        fallbackTint = HazeTint(fallbackBase.copy(alpha = 0.68f)),
                    ),
                )
                .drawMoldedSpecularOverlay(palette.specularAlpha, palette.causticAlpha, palette.isDark)
                .border(width = borderWidth, brush = palette.borderBrush, shape = shape)
        }

        else -> {
            val fallbackTint = when {
                prominent -> Brush.verticalGradient(
                    listOf(
                        palette.baseTint.copy(alpha = if (palette.isDark) 0.34f else 0.48f),
                        palette.baseTint.copy(alpha = if (palette.isDark) 0.18f else 0.26f),
                    ),
                )
                palette.isDark -> Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.13f + intensity.surfaceAlpha * 0.14f),
                        Color(0xFF141824).copy(alpha = 0.48f + intensity.surfaceAlpha * 0.22f),
                    ),
                )
                else -> Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.68f),
                        Color(0xFFDCE6F4).copy(alpha = 0.42f),
                    ),
                )
            }

            this
                .clip(shape)
                .background(brush = fallbackTint, shape = shape)
                .drawMoldedSpecularOverlay(palette.specularAlpha, palette.causticAlpha, palette.isDark)
                .border(width = borderWidth, brush = palette.borderBrush, shape = shape)
        }
    }
}

private fun Modifier.drawMoldedSpecularOverlay(
    specularAlpha: Float,
    causticAlpha: Float,
    isDark: Boolean,
): Modifier =
    drawBehind {
        // Brillo superior moldeado del menisco de Fresnel
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = specularAlpha * 0.58f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.42f,
            ),
            topLeft = Offset.Zero,
            size = size,
        )
        // Rebote cáustico interno inferior (trampa de luz)
        val causticColor = if (isDark) Color(0xFF7AD8FF) else Color.White
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    causticColor.copy(alpha = causticAlpha * 0.32f),
                ),
                startY = size.height * 0.64f,
                endY = size.height,
            ),
            topLeft = Offset.Zero,
            size = size,
        )
    }

/**
 * Aplastamiento elástico táctil para los controles interactivos de cristal líquido
 * (equivalente a `.interactive()` de Apple iOS 26).
 */
fun Modifier.liquidGlassPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.93f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "liquidGlassPress",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
