/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.ui.player.kit.CustomImageBackground

/** Letra de ejemplo de la vista previa: una línea ya cantada, la actual y las siguientes. */
private val PreviewLines = listOf(
    "Las luces de la ciudad",
    "se apagan cuando llegas",
    "y suena nuestra canción",
    "como la primera vez",
    "deja que siga sonando",
)

/**
 * Editor del «Fondo personalizado»: vista previa en un móvil en miniatura con la imagen, la
 * letra y los controles encima, y los deslizadores que la cambian en vivo. Se guarda al soltar.
 */
@Composable
internal fun PlayerBackgroundEditor(
    imageUri: String,
    blur: Float,
    brightness: Float,
    contrast: Float,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onBlurChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
) {
    var blurDraft by remember(blur) { mutableFloatStateOf(blur) }
    var brightnessDraft by remember(brightness) { mutableFloatStateOf(brightness) }
    var contrastDraft by remember(contrast) { mutableFloatStateOf(contrast) }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxWidth().settingsCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // El móvil en miniatura (proporción 9:19,5 como la pantalla).
            Box(
                Modifier
                    .width(150.dp)
                    .aspectRatio(9f / 19.5f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(3.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
                    .background(Color(0xFF141414))
                    .clickable(onClick = onPickImage),
            ) {
                if (imageUri.isNotBlank()) {
                    // La miniatura es ~2,4 veces más pequeña que la pantalla: el desenfoque se escala igual.
                    CustomImageBackground(imageUri, blurDraft / 2.4f, contrastDraft, brightnessDraft)
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A))),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                            Text("Elige una imagen", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                PreviewLyricsOverlay()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vista previa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Así se verá detrás del reproductor y de las letras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                    Text(if (imageUri.isBlank()) "Elegir imagen" else "Cambiar imagen", maxLines = 1)
                }
                if (imageUri.isNotBlank()) {
                    OutlinedButton(onClick = onRemoveImage, modifier = Modifier.fillMaxWidth()) {
                        Text("Quitar", maxLines = 1)
                    }
                }
            }
        }

        if (imageUri.isNotBlank()) {
            EditorSlider(
                title = "Desenfoque",
                value = blurDraft,
                range = 0f..40f,
                label = if (blurDraft < 0.5f) "Nítida" else "${blurDraft.toInt()}",
                onChange = { blurDraft = it },
                onDone = { onBlurChange(blurDraft) },
            )
            EditorSlider(
                title = "Atenuación",
                value = brightnessDraft,
                range = 0.2f..1f,
                label = "${((1f - brightnessDraft) * 100).toInt()} %",
                onChange = { brightnessDraft = it },
                onDone = { onBrightnessChange(brightnessDraft) },
            )
            EditorSlider(
                title = "Contraste",
                value = contrastDraft,
                range = 0.5f..1.5f,
                label = "${(contrastDraft * 100).toInt()} %",
                onChange = { contrastDraft = it },
                onDone = { onContrastChange(contrastDraft) },
            )
        }
    }
}

@Composable
private fun EditorSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onDone)
    }
}

/** Cabecera, letra con la línea actual resaltada y controles, en pequeño. */
@Composable
private fun PreviewLyricsOverlay() {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.35f)))
            Column {
                Text("Tu canción", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Artista", color = Color.White.copy(alpha = 0.7f), fontSize = 7.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        PreviewLines.forEachIndexed { i, line ->
            val current = i == 2
            Text(
                text = line,
                color = Color.White.copy(alpha = if (current) 1f else if (i < 2) 0.45f else 0.6f),
                fontSize = if (current) 13.sp else 11.sp,
                lineHeight = if (current) 15.sp else 13.sp,
                fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(0.4f).height(2.dp).background(Color.White))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Pause, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
