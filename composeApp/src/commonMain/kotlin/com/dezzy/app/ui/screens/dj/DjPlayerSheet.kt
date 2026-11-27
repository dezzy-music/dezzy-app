/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.dj.DjController
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.feature.dj.DjUiState
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.screens.ScreenContext
import org.koin.compose.koinInject

/**
 * Hoja del DJ para abrir desde el reproductor: estado, lo que dice, pedir algo y abrir la pantalla
 * completa. Los reproductores la usan en lugar de la pantalla pendiente «DJ Dezzy».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjPlayerSheet(ctx: ScreenContext, onDismiss: () -> Unit) {
    val dj: DjController = koinInject()
    val settings: DjSettings = koinInject()
    val ui by dj.uiState.collectAsState()
    val status by dj.statusText.collectAsState()
    val script by dj.djScript.collectAsState()
    val vibes by dj.dynamicVibeCards.collectAsState()
    val voiceSession by dj.djAudioSessionId.collectAsState()
    val rawName by settings.name.collectAsState("")
    val djName = rawName.trim().let { if (it.isNotBlank()) "DJ $it" else "DJ Dezzy" }

    LaunchedEffect(Unit) {
        dj.refreshKeys()
        dj.loadVibeSuggestions()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(djName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            DjVisualizerBox(
                audioSessionId = if (voiceSession > 0) voiceSession else ctx.player.audioSessionId,
                active = ui != DjUiState.IDLE,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(180.dp),
            )
            Text(status, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            if (script.isNotBlank() && ui != DjUiState.IDLE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "«$script»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui == DjUiState.IDLE) {
                    DezzyButton(onClick = { dj.startDjSession() }) { Text("Empezar sesión") }
                } else {
                    DezzyOutlinedButton(onClick = { dj.stop() }) { Text("Terminar") }
                }
                DezzyOutlinedButton(onClick = {
                    onDismiss()
                    ctx.navigator.push(Screen.Dj)
                }) { Text("Abrir DJ") }
            }
            Spacer(Modifier.height(12.dp))
            DjRequestBar(djName) {
                dj.handleUserRequest(it)
                onDismiss()
            }
            Spacer(Modifier.height(8.dp))
            DjVibeRow(vibes) {
                dj.startDjSession(it.query)
                onDismiss()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    DjTasteDialog(dj, djName)
}
