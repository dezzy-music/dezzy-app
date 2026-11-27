/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.dezzy.app.ui.player.kit.AudioSpectrumEngine
import com.dezzy.app.ui.player.kit.rememberPowerSaveMode
import com.dezzy.app.ui.player.kit.rememberRecordAudioPermission
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orbe del DJ: late con el volumen de lo que suena (o de la voz del DJ) y dibuja el espectro
 * alrededor. Sin permiso de micrófono o en escritorio queda una respiración suave.
 *
 * @param audioSessionId sesión de audio a analizar (0 = ninguna).
 */
@Composable
fun DjVisualizerBox(
    audioSessionId: Int,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val permission = rememberRecordAudioPermission()
    val powerSave = rememberPowerSaveMode()
    val engine = remember { AudioSpectrumEngine() }
    var tick by remember { mutableLongStateOf(0L) }
    var live by remember { mutableLongStateOf(0L) }

    DisposableEffect(audioSessionId, permission.granted, active) {
        live = if (active && permission.granted && engine.start(audioSessionId)) 1L else 0L
        onDispose { engine.release() }
    }
    // Un fotograma por cuadro (la mitad con ahorro de batería) para repintar el espectro.
    LaunchedEffect(active, powerSave) {
        while (active) {
            androidx.compose.runtime.withFrameNanos { tick = it }
            if (powerSave) androidx.compose.runtime.withFrameNanos { }
        }
    }

    val breathing by rememberInfiniteTransition(label = "dj-breath").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "dj-breath-scale",
    )

    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.matchParentSize()) {
            val frame = tick
            val c = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension * 0.28f
            val level = if (live == 1L) engine.loudness.coerceIn(0f, 1f) else 0f
            val radius = base * (if (live == 1L) 1f + level * 0.35f else breathing)
            // Halo
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = radius * 2.1f),
                radius = radius * 2.1f,
                center = c,
            )
            // Barras del espectro en círculo
            val bars = AudioSpectrumEngine.BANDS
            for (i in 0 until bars) {
                val v = if (live == 1L) engine.bands[i].coerceIn(0f, 1f) else 0.05f + 0.04f * (1f + sin(i * 0.7f + breathing * 6f))
                val angle = (i.toFloat() / bars) * 2f * PI.toFloat()
                val inner = radius * 1.12f
                val outer = inner + radius * 0.75f * v
                drawLine(
                    color = color.copy(alpha = 0.55f + 0.45f * v),
                    start = Offset(c.x + cos(angle) * inner, c.y + sin(angle) * inner),
                    end = Offset(c.x + cos(angle) * outer, c.y + sin(angle) * outer),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Orbe
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f)),
                    center = Offset(c.x - radius * 0.3f, c.y - radius * 0.3f),
                    radius = radius * 1.4f,
                ),
                radius = radius,
                center = c,
            )
            rotate(degrees = (frame / 40_000_000L % 360L).toFloat(), pivot = c) {
                drawArc(
                    color = Color.White.copy(alpha = 0.22f),
                    startAngle = 200f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius * 0.8f, c.y - radius * 0.8f),
                    size = Size(radius * 1.6f, radius * 1.6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
