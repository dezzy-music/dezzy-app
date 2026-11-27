/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import com.dezzy.app.feature.personalize.AppIconFinish
import com.dezzy.app.feature.personalize.AppIconShape
import com.dezzy.app.feature.personalize.AppIconStore
import com.dezzy.app.feature.personalize.AppIconStyle
import org.koin.compose.koinInject

// Trazados del icono sobre un lienzo de 512 (los mismos de los vectores del launcher).
private val RING: Path by lazy { PathParser().parsePathString("M110,300a110,110 0 1,0 220,0a110,110 0 1,0 -220,0").toPath() }
private val STEM: Path by lazy {
    PathParser().parsePathString("M328,130a30,30 0 0,1 30,30v220a30,30 0 0,1 -60,0v-220a30,30 0 0,1 30,-30z").toPath()
}
private val PLAY: Path by lazy { PathParser().parsePathString("M188,250L292,300L188,350Z").toPath() }
private const val STAR_OUT_D = "M233,840L298,559L80,370L368,345L480,80L592,345L880,370L662,559L727,840L480,691L233,840Z"
private const val STAR_IN_D = "M354,673L480,597L606,674L573,530L684,434L538,421L480,285L422,420L276,433L387,530L354,673Z"
private val STAR_RING: Path by lazy {
    PathParser().parsePathString("$STAR_OUT_D $STAR_IN_D").toPath().apply { fillType = PathFillType.EvenOdd }
}
private val STAR_IN: Path by lazy { PathParser().parsePathString(STAR_IN_D).toPath() }

/** Caja de la figura dentro del lienzo de 512 (para dibujar sólo la marca, sin fondo). */
private fun glyphBounds(shape: AppIconShape) = when (shape) {
    AppIconShape.D_PLAY -> Rect(82f, 130f, 358f, 438f)
    AppIconShape.STAR -> Rect(106f, 106f, 406f, 391f)
}

private fun DrawScope.drawIconBackground(style: AppIconStyle, s: Float, corner: Float) {
    val r = s * corner
    val radius = CornerRadius(r, r)
    when (style.finish) {
        AppIconFinish.LIQUID_GLASS_DARK -> {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF1D2234.toInt()), Color(0xFF07080D.toInt())),
                    start = Offset.Zero,
                    end = Offset(s, s),
                ),
                size = Size(s, s),
                cornerRadius = radius,
            )
            // Reflejo especular de arriba
            drawOval(Color(0x1EFFFFFF), topLeft = Offset(s * 0.14f, s * 0.06f), size = Size(s * 0.72f, s * 0.28f))
            // Borde metálico
            val w = (s * 0.025f).coerceAtLeast(1.5f)
            val inset = w / 2f
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0x99FFFFFF.toInt()), Color(0x4438BDF8)),
                    start = Offset.Zero,
                    end = Offset(s, s),
                ),
                topLeft = Offset(inset, inset),
                size = Size(s - 2 * inset, s - 2 * inset),
                cornerRadius = radius,
                style = Stroke(width = w),
            )
        }
        AppIconFinish.NOTHING_DOTS -> {
            drawRoundRect(Color(style.bg), size = Size(s, s), cornerRadius = radius)
            val isLight = Color(style.bg).luminance() > 0.5f
            val w = (s * 0.02f).coerceAtLeast(1.2f)
            val inset = w / 2f
            drawRoundRect(
                color = if (isLight) Color(0x2E121316) else Color(0x3DFFFFFF),
                topLeft = Offset(inset, inset),
                size = Size(s - 2 * inset, s - 2 * inset),
                cornerRadius = radius,
                style = Stroke(width = w),
            )
            // Indicador LED bermellón
            drawCircle(Color(style.ac), radius = (s * 0.036f).coerceAtLeast(2f), center = Offset(s * 0.78f, s * 0.22f))
        }
        AppIconFinish.SOLID -> drawRoundRect(Color(style.bg), size = Size(s, s), cornerRadius = radius)
    }
}

private fun DrawScope.drawIconGlyph(style: AppIconStyle, mono: Color?) {
    val fg = mono ?: Color(style.fg)
    val ac = mono ?: Color(style.ac)
    val nothingDots = mono == null && style.finish == AppIconFinish.NOTHING_DOTS
    when (style.shape) {
        AppIconShape.D_PLAY -> {
            val ring = if (nothingDots) {
                Stroke(width = 52f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 38f), 0f))
            } else {
                Stroke(width = 56f)
            }
            drawPath(RING, fg, style = ring)
            drawPath(STEM, fg)
            drawPath(PLAY, ac)
        }
        AppIconShape.STAR -> translate(76f, 76f) {
            scale(0.375f, 0.375f, pivot = Offset.Zero) {
                if (nothingDots) {
                    drawPath(STAR_RING, fg.copy(alpha = 44f / 255f))
                    drawPath(
                        STAR_RING,
                        fg,
                        style = Stroke(width = 28f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 52f), 0f)),
                    )
                    drawPath(STAR_IN, ac)
                } else {
                    drawPath(STAR_RING, fg)
                    if (mono == null) drawPath(STAR_IN, ac)
                }
            }
        }
    }
}

/**
 * Dibuja el icono en un cuadrado centrado de `size.minDimension`.
 * [background] true: icono completo con fondo; false: sólo la figura, ajustada al cuadrado.
 * [corner] radio de las esquinas del fondo como fracción del tamaño (0.5 = círculo).
 * [mono] si no es null, toda la figura se pinta de ese color (para tintar).
 */
internal fun DrawScope.drawAppIcon(style: AppIconStyle, background: Boolean, corner: Float, mono: Color?) {
    val s = size.minDimension
    if (s <= 0f) return
    translate((size.width - s) / 2f, (size.height - s) / 2f) {
        if (background) {
            drawIconBackground(style, s, corner)
            scale(s / 512f, s / 512f, pivot = Offset.Zero) { drawIconGlyph(style, mono) }
        } else {
            val b = glyphBounds(style.shape)
            val k = s / maxOf(b.width, b.height)
            translate((s - b.width * k) / 2f, (s - b.height * k) / 2f) {
                scale(k, k, pivot = Offset.Zero) {
                    translate(-b.left, -b.top) { drawIconGlyph(style, mono) }
                }
            }
        }
    }
}

/** Estilo del icono, reactivo: cualquier cambio en Ajustes repinta todos los logos al momento. */
@Composable
fun rememberAppIconStyle(): AppIconStyle {
    val store: AppIconStore = koinInject()
    val flow = remember(store) { store.style }
    val style by flow.collectAsState(initial = AppIconStyle.Default)
    return style
}

/** El icono completo de Dezzy (fondo + figura) con los colores elegidos en Ajustes. */
@Composable
fun DezzyAppIcon(
    modifier: Modifier = Modifier,
    style: AppIconStyle = rememberAppIconStyle(),
    corner: Float = 0.22f,
) {
    Canvas(modifier) { drawAppIcon(style, true, corner, null) }
}

/**
 * Sólo la marca (sin fondo) con la forma elegida. Con [tint] se pinta de un color, como un
 * icono normal; sin él, usa los colores de figura y acento del usuario.
 */
@Composable
fun DezzyLogoMark(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    style: AppIconStyle = rememberAppIconStyle(),
) {
    Canvas(modifier) { drawAppIcon(style, false, 0f, tint) }
}
