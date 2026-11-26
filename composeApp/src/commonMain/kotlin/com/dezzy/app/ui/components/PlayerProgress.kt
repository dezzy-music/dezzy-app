/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Barra de progreso del reproductor: mientras se arrastra se ve el tiempo de destino y la
 * canción sólo salta al soltar, para no pedir un salto por cada píxel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showTimes: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0)
    val fraction = dragging ?: if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            thumb = {
                Box(Modifier.size(if (dragging != null) 18.dp else 12.dp).clip(CircleShape).background(activeColor))
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(activeTrackColor = activeColor, inactiveTrackColor = inactiveColor),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        if (showTimes) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                Text(formatClock(((dragging?.times(duration))?.toLong() ?: positionMs) / 1000), style = MaterialTheme.typography.labelSmall, color = textColor)
                Spacer(Modifier.weight(1f))
                Text(if (duration > 0) formatClock(duration / 1000) else "--:--", style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

/** Línea fina de progreso (mini reproductor). */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.fillMaxWidth().height(2.dp).background(color.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(color))
    }
}
