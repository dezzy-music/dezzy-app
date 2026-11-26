/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.resources.Res
import com.dezzy.app.resources.ndot_matrix
import com.dezzy.app.resources.space_grotesk
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val NothingVermilion = Color(0xFFD71920)
val NothingOledBlack = Color(0xFF000000)
val NothingCharcoal = Color(0xFF101114)
val NothingElevatedDark = Color(0xFF17191D)
val NothingBoneWhite = Color(0xFFF4F4F0)
val NothingPaperLight = Color(0xFFF2F2EC)
val NothingCardLight = Color(0xFFE7E6DF)
val NothingInkLight = Color(0xFF121316)

/** Fuente de puntos (matriz de puntos) de Nothing. */
@Composable
fun nothingDotFontFamily(): FontFamily = FontFamily(Font(Res.font.ndot_matrix))

/** Fuente de cuerpo de Nothing (Space Grotesk) con los pesos 400/500/600/700. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun nothingBodyFontFamily(): FontFamily = FontFamily(
    Font(
        Res.font.space_grotesk,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        Res.font.space_grotesk,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        Res.font.space_grotesk,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        Res.font.space_grotesk,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

@Composable
@ReadOnlyComposable
fun isNothingTheme(): Boolean = LocalAppInterfaceTheme.current == AppInterfaceTheme.NOTHING

@Composable
@ReadOnlyComposable
fun isCustomStyledTheme(): Boolean = LocalAppInterfaceTheme.current != AppInterfaceTheme.NORMAL

/**
 * Aplica de forma uniforme la superficie del estilo activo (LIQUID_GLASS o NOTHING) a botones,
 * chips, interruptores, filas de ajustes, tarjetas y elementos de navegación compartidos.
 */
fun Modifier.dezzyStyledSurface(
    shape: Shape,
    prominent: Boolean = false,
    tintColor: Color = Color.Unspecified,
    useBackdrop: Boolean = true,
    enableShadow: Boolean = true,
    refractionScale: Float = 1f,
    borderWidth: Dp = 1.05.dp,
    dashedWhenNothing: Boolean = false,
): Modifier = composed {
    when (LocalAppInterfaceTheme.current) {
        AppInterfaceTheme.LIQUID_GLASS -> this.liquidGlassSurface(
            shape = shape,
            prominent = prominent,
            tintColor = tintColor,
            useBackdrop = useBackdrop,
            enableShadow = enableShadow,
            refractionScale = refractionScale,
            borderWidth = borderWidth,
        )
        AppInterfaceTheme.NOTHING -> this.nothingSurface(
            shape = shape,
            prominent = prominent,
            dashed = dashedWhenNothing,
            borderWidth = 1.dp,
            tintColor = tintColor,
        )
        AppInterfaceTheme.NORMAL -> this
    }
}

fun buildNothingColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = NothingVermilion,
            onPrimary = Color.White,
            primaryContainer = NothingVermilion.copy(alpha = 0.20f),
            onPrimaryContainer = NothingBoneWhite,
            secondary = NothingBoneWhite,
            onSecondary = NothingOledBlack,
            secondaryContainer = Color(0xFF1E2025),
            onSecondaryContainer = NothingBoneWhite,
            tertiary = Color(0xFFFF5A5F),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFF281518),
            onTertiaryContainer = Color(0xFFFFDAD9),
            background = NothingOledBlack,
            onBackground = NothingBoneWhite,
            surface = NothingOledBlack,
            onSurface = NothingBoneWhite,
            surfaceVariant = NothingElevatedDark,
            onSurfaceVariant = Color(0xFFA0A3AA),
            surfaceContainerLowest = NothingOledBlack,
            surfaceContainerLow = NothingCharcoal,
            surfaceContainer = Color(0xFF141519),
            surfaceContainerHigh = NothingElevatedDark,
            surfaceContainerHighest = Color(0xFF202228),
            outline = Color.White.copy(alpha = 0.28f),
            outlineVariant = Color.White.copy(alpha = 0.16f),
            error = NothingVermilion,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = NothingVermilion,
            onPrimary = Color.White,
            primaryContainer = NothingVermilion.copy(alpha = 0.14f),
            onPrimaryContainer = NothingInkLight,
            secondary = NothingInkLight,
            onSecondary = NothingBoneWhite,
            secondaryContainer = Color(0xFFDDDBD2),
            onSecondaryContainer = NothingInkLight,
            tertiary = Color(0xFF9F1117),
            onTertiary = Color.White,
            background = NothingPaperLight,
            onBackground = NothingInkLight,
            surface = NothingPaperLight,
            onSurface = NothingInkLight,
            surfaceVariant = NothingCardLight,
            onSurfaceVariant = Color(0xFF4C4E54),
            surfaceContainerLowest = Color(0xFFFAF9F5),
            surfaceContainerLow = NothingCardLight,
            surfaceContainer = Color(0xFFE0DFD7),
            surfaceContainerHigh = Color(0xFFD8D7CF),
            surfaceContainerHighest = Color(0xFFCECDC4),
            outline = NothingInkLight.copy(alpha = 0.34f),
            outlineVariant = NothingInkLight.copy(alpha = 0.18f),
            error = NothingVermilion,
            onError = Color.White,
        )
    }

/** Tipografía de Nothing: puntos para títulos y etiquetas, Space Grotesk para el cuerpo. */
@Composable
fun nothingTypography(): Typography {
    val dot = nothingDotFontFamily()
    val body = nothingBodyFontFamily()
    return Typography(
        displayLarge = TextStyle(fontFamily = dot, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = 0.5.sp),
        displayMedium = TextStyle(fontFamily = dot, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = 0.4.sp),
        displaySmall = TextStyle(fontFamily = dot, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.3.sp),
        headlineLarge = TextStyle(fontFamily = dot, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.4.sp),
        headlineMedium = TextStyle(fontFamily = dot, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.4.sp),
        headlineSmall = TextStyle(fontFamily = dot, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        titleLarge = TextStyle(fontFamily = dot, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.4.sp),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        titleSmall = TextStyle(fontFamily = dot, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.15.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
        labelMedium = TextStyle(fontFamily = dot, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.6.sp),
        labelSmall = TextStyle(fontFamily = dot, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.7.sp),
    )
}

/**
 * Lienzo OLED auténtico de Nothing OS 3.0 con cuadrícula discreta de puntos de matriz.
 */
@Composable
fun NothingDotMatrixBackground(
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgColor = if (isDark) NothingOledBlack else NothingPaperLight
    val dotColor = if (isDark) Color.White.copy(alpha = 0.085f) else NothingInkLight.copy(alpha = 0.11f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = bgColor)
            val stepPx = 15.dp.toPx()
            val dotRadius = 1.dp.toPx()
            var y = stepPx * 0.5f
            while (y < size.height) {
                var x = stepPx * 0.5f
                while (x < size.width) {
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                    x += stepPx
                }
                y += stepPx
            }
        }
    }
}

/**
 * Superficie mate de hardware Nothing OS con borde nítido de 1dp (continuo o discontinuo
 * estilo PCB), rellenos prominentes en Bermellón / Blanco hueso y cero desenfoque turbio.
 */
fun Modifier.nothingSurface(
    shape: Shape,
    prominent: Boolean = false,
    dashed: Boolean = false,
    borderWidth: Dp = 1.dp,
    tintColor: Color = Color.Unspecified,
): Modifier = composed {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    val fillColor = when {
        tintColor != Color.Unspecified -> tintColor
        prominent -> NothingVermilion
        isDark -> NothingCharcoal
        else -> NothingCardLight
    }
    val borderColor = when {
        prominent && tintColor == Color.Unspecified -> NothingVermilion
        prominent -> Color.White.copy(alpha = 0.65f)
        isDark -> Color.White.copy(alpha = 0.18f)
        else -> NothingInkLight.copy(alpha = 0.22f)
    }

    val base = this
        .clip(shape)
        .background(color = fillColor, shape = shape)

    if (dashed) {
        base.drawWithContent {
            drawContent()
            val strokePx = borderWidth.toPx()
            val dashPx = 5.dp.toPx()
            val cornerPx = 22.dp.toPx().coerceAtMost(size.minDimension / 2f)
            drawRoundRect(
                color = borderColor,
                style = Stroke(
                    width = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx * 0.7f), 0f),
                ),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
            )
        }
    } else {
        base.border(width = borderWidth, color = borderColor, shape = shape)
    }
}

/**
 * Anillo concéntrico de puntos tipo vinilo / radar inspirado en los paneles de hardware
 * Nothing ("10 ENTERTAINMENT"): anillos de puntos blanco hueso / ceniza alrededor de un LED
 * central Bermellón.
 */
@Composable
fun NothingDotRing(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    ringCount: Int = 4,
    ledColor: Color = NothingVermilion,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dotBase = if (isDark) NothingBoneWhite else NothingInkLight
    val rotationDeg = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "nothingDotRing")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "nothingDotAngle",
        )
        angle
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = (size.minDimension / 2f) * 0.90f
        val minR = maxR * 0.28f

        for (rIdx in 0 until ringCount) {
            val fraction = if (ringCount <= 1) 1f else rIdx.toFloat() / (ringCount - 1)
            val radius = minR + (maxR - minR) * fraction
            val dotsInRing = 10 + rIdx * 6
            val dotRadius = (1.15f + fraction * 0.45f).dp.toPx().coerceAtMost(maxR * 0.09f)
            val alpha = if (isPlaying) 0.38f + fraction * 0.55f else 0.28f + fraction * 0.35f
            val phaseShift = rotationDeg * (if (rIdx % 2 == 0) 1f else -0.6f) * (PI.toFloat() / 180f)

            for (i in 0 until dotsInRing) {
                val theta = (2f * PI.toFloat() * i / dotsInRing) + phaseShift
                val px = cx + cos(theta) * radius
                val py = cy + sin(theta) * radius
                val highlightArc = isPlaying && rIdx == ringCount - 1 && (i % dotsInRing) < 3
                drawCircle(
                    color = if (highlightArc) ledColor.copy(alpha = 0.95f) else dotBase.copy(alpha = alpha),
                    radius = if (highlightArc) dotRadius * 1.25f else dotRadius,
                    center = Offset(px, py),
                )
            }
        }

        drawCircle(
            color = ledColor.copy(alpha = if (isPlaying) 0.28f else 0.16f),
            radius = (maxR * 0.22f).coerceAtLeast(5.dp.toPx()),
            center = Offset(cx, cy),
        )
        drawCircle(
            color = ledColor,
            radius = (maxR * 0.13f).coerceAtLeast(3.dp.toPx()),
            center = Offset(cx, cy),
        )
    }
}

/**
 * Barras discretas de espectro de ecualizador en matriz de puntos con indicadores de pico Bermellón.
 */
@Composable
fun NothingDotSpectrum(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    columns: Int = 14,
    rows: Int = 5,
    accentColor: Color = NothingVermilion,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dotColor = if (isDark) NothingBoneWhite else NothingInkLight
    val phase = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "nothingSpectrum")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI.toFloat()),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "nothingSpectrumPhase",
        )
        value
    } else {
        0.5f
    }

    Canvas(modifier = modifier) {
        val colStep = size.width / columns.coerceAtLeast(1)
        val rowStep = size.height / rows.coerceAtLeast(1)
        val dotR = (minOf(colStep, rowStep) * 0.24f).coerceIn(1.dp.toPx(), 2.6.dp.toPx())

        for (c in 0 until columns) {
            val wave = if (isPlaying) {
                0.35f + 0.65f * ((sin(phase + c * 0.72f) + 1f) * 0.5f)
            } else {
                0.24f + 0.22f * ((c % 3) / 2f)
            }
            val activeRows = (wave * rows).toInt().coerceIn(1, rows)
            val x = colStep * (c + 0.5f)
            for (r in 0 until rows) {
                val y = size.height - rowStep * (r + 0.5f)
                val lit = r < activeRows
                val isPeak = lit && r >= rows - 1 && c >= columns - 3
                val color = when {
                    isPeak && isPlaying -> accentColor
                    lit -> dotColor.copy(alpha = 0.85f)
                    else -> dotColor.copy(alpha = 0.14f)
                }
                drawCircle(color = color, radius = dotR, center = Offset(x, y))
            }
        }
    }
}

/**
 * Píldora de telemetría LED de hardware (p. ej. `CONNECTED ●` u `ONLINE ●`) inspirada en las
 * etiquetas de estado de Nothing.
 */
@Composable
fun NothingLedBadge(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val borderColor = if (active) {
        NothingVermilion.copy(alpha = 0.72f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        NothingInkLight.copy(alpha = 0.22f)
    }
    Row(
        modifier = modifier
            .nothingSurface(shape = CircleShape, prominent = false, borderWidth = 1.dp)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                if (isDark) NothingBoneWhite else NothingInkLight
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) NothingVermilion else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
        )
    }
}
