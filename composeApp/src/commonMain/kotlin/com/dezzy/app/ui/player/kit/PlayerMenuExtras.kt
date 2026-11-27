/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.dezzy.app.feature.audio.AudioEffects
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Abre el visualizador a pantalla completa (lo fija el enrutador del reproductor). */
val LocalOpenVisualizer = staticCompositionLocalOf<() -> Unit> { {} }

/** Abre la pantalla siempre activa (la fija el enrutador del reproductor). */
val LocalOpenAod = staticCompositionLocalOf<() -> Unit> { {} }

/** Subtítulo de la fila «Voz / Instrumental»: «Original», «Más voz» o «Instrumental», + « · DSP». */
fun vocalMixSubtitle(mix: Float): String {
    val label = when {
        abs(mix) < 0.02f -> "Original"
        mix > 0f -> "Más voz"
        else -> "Instrumental"
    }
    return "$label · DSP"
}

/** «1.00», con dos decimales y punto. */
fun formatFactor(value: Float): String {
    val hundredths = (value * 100f).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

/** Encabezado pequeño y en negrita de un grupo de filas. */
@Composable
fun MenuGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** Fila de 56 dp: icono a la izquierda, texto en negrita y subtítulo gris. */
@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Chip de contorno con icono y texto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(icon, null) },
    )
}

/** Diálogo «Voz / Instrumental»: de −1 (sólo instrumental) a +1 (sólo voz). */
@Composable
fun VocalMixDialog(onDismiss: () -> Unit) {
    val fx: AudioEffects = koinInject()
    val fxState by fx.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voz / Instrumental") },
        text = {
            Column {
                Text(vocalMixSubtitle(fxState.vocalMix), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = fxState.vocalMix,
                    onValueChange = { fx.setVocalMix(it) },
                    valueRange = -1f..1f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Instrumental", style = MaterialTheme.typography.labelSmall)
                    Text("Original", style = MaterialTheme.typography.labelSmall)
                    Text("Voz", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        dismissButton = { TextButton(onClick = { fx.setVocalMix(0f) }) { Text("Original") } },
    )
}

/** Diálogo «Velocidad y tono»: dos deslizadores de 0,5 a 2,0 y «Restablecer». */
@Composable
fun SpeedPitchDialog(onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val state by connection.controller.state.collectAsState()
    val controller = connection.controller
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velocidad y tono") },
        text = {
            Column {
                Text("Velocidad  x${formatFactor(state.speed)}", fontWeight = FontWeight.Bold)
                Slider(
                    value = state.speed,
                    onValueChange = { controller.setPlaybackSpeed((it * 20f).roundToInt() / 20f) },
                    valueRange = 0.5f..2f,
                )
                Spacer(Modifier.height(8.dp))
                Text("Tono  x${formatFactor(state.pitch)}", fontWeight = FontWeight.Bold)
                Slider(
                    value = state.pitch,
                    onValueChange = { controller.setPlaybackPitch((it * 20f).roundToInt() / 20f) },
                    valueRange = 0.5f..2f,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        dismissButton = {
            TextButton(onClick = {
                controller.setPlaybackSpeed(1f)
                controller.setPlaybackPitch(1f)
            }) { Text("Restablecer") }
        },
    )
}
