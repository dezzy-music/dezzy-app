/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.dj.DjVibeCard
import com.dezzy.app.feature.dj.rememberDjDictation
import com.dezzy.app.ui.components.glass.DezzySuggestionChip

/** Hoja para pedirle música al DJ: escribir, dictar o elegir una vibra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjInteractionSheet(
    djName: String,
    vibes: List<DjVibeCard>,
    onSubmit: (String) -> Unit,
    onVibe: (DjVibeCard) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("¿Qué quieres escuchar?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            DjRequestBar(djName) {
                onSubmit(it)
                onDismiss()
            }
            Spacer(Modifier.height(16.dp))
            Text("Vibras para ahora", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            DjVibeRow(vibes) {
                onVibe(it)
                onDismiss()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Campo de texto con micrófono y botón de enviar. */
@Composable
internal fun DjRequestBar(djName: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val dictate = rememberDjDictation(djName) { heard ->
        text = heard
        onSubmit(heard)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Pídele algo a $djName") },
        )
        IconButton(onClick = dictate) { Icon(Icons.Rounded.Mic, "Dictar") }
        IconButton(onClick = { if (text.isNotBlank()) onSubmit(text) }, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Enviar")
        }
    }
}

/** Fila de vibras (tarjetas de una línea). */
@Composable
internal fun DjVibeRow(vibes: List<DjVibeCard>, onClick: (DjVibeCard) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vibes.forEach { card ->
            DezzySuggestionChip(onClick = { onClick(card) }, label = { Text(card.title) })
        }
    }
}
