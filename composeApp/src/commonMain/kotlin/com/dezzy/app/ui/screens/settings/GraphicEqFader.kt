/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.audio.EQ_MAX_GAIN_DB
import kotlin.math.roundToInt

/** Fader vertical del ecualizador gráfico: de -15 dB a +15 dB, 0 dB en el centro. */
@Composable
internal fun GraphicEqFader(
    label: String,
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val maxDb = EQ_MAX_GAIN_DB
    val range = maxDb * 2f

    // Mapear la ganancia a un porcentaje (0.0 a 1.0), donde 0.5 es 0dB
    val percent = ((gainDb + maxDb) / range).coerceIn(0f, 1f)
    val currentPercent by rememberUpdatedState(percent)
    val currentOnChange by rememberUpdatedState(onGainChange)

    val labelColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val knobMarkColor = MaterialTheme.colorScheme.onPrimary
    val zeroColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(60.dp).padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .width(44.dp)
                .background(trackColor, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val height = size.height.toFloat()
                        // Invertimos la dirección (hacia arriba es positivo)
                        val deltaPercent = -dragAmount / height
                        val newPercent = (currentPercent + deltaPercent).coerceIn(0f, 1f)
                        currentOnChange((newPercent * range) - maxDb)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Marcas (como en un fader profesional)
                val lines = 21
                for (i in 0 until lines) {
                    val y = (i.toFloat() / (lines - 1)) * height
                    val isCenter = i == 10
                    val lineColor = if (isCenter) activeColor.copy(alpha = 0.5f) else tickColor
                    val lineWidth = if (isCenter) width * 0.8f else width * 0.4f
                    val startX = (width - lineWidth) / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(startX, y),
                        end = Offset(startX + lineWidth, y),
                        strokeWidth = 2f,
                    )
                }

                // La perilla del fader
                val faderY = height - (percent * height)

                drawLine(
                    color = activeColor,
                    start = Offset(6f, faderY),
                    end = Offset(width - 6f, faderY),
                    strokeWidth = 16f,
                    cap = StrokeCap.Round,
                )

                // Indicador central de la perilla
                drawLine(
                    color = knobMarkColor,
                    start = Offset(width / 2f - 4f, faderY),
                    end = Offset(width / 2f + 4f, faderY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }

        Text(
            text = if (gainDb > 0) "+${gainDb.roundToInt()}" else "${gainDb.roundToInt()}",
            color = if (gainDb == 0f) zeroColor else activeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
