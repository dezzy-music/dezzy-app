/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.dj.DjController
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.feature.dj.DjUiState
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import org.koin.compose.koinInject

/** Pantalla del DJ: el orbe, lo que dice, y pedirle música escribiendo, dictando o con una vibra. */
@Composable
fun DjScreen(ctx: ScreenContext) {
    val dj: DjController = koinInject()
    val settings: DjSettings = koinInject()
    val ui by dj.uiState.collectAsState()
    val status by dj.statusText.collectAsState()
    val script by dj.djScript.collectAsState()
    val vibes by dj.dynamicVibeCards.collectAsState()
    val hasBrain by dj.hasBrain.collectAsState()
    val voiceSession by dj.djAudioSessionId.collectAsState()
    val rawName by settings.name.collectAsState("")
    val djName = rawName.trim().let { if (it.isNotBlank()) "DJ $it" else "DJ Dezzy" }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dj.refreshKeys()
        dj.loadVibeSuggestions()
    }

    if (showSettings) {
        PlatformBackHandler(enabled = true) { showSettings = false }
        DjSettingsScreen(ctx, onBack = { showSettings = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle(
            djName,
            onBack = { ctx.navigator.back() },
            actions = {
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "Ajustes del DJ") }
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DjVisualizerBox(
                audioSessionId = if (voiceSession > 0) voiceSession else ctx.player.audioSessionId,
                active = ui != DjUiState.IDLE,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (ui == DjUiState.IDLE) dj.startDjSession()
                    },
            )
            Text(status, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (script.isNotBlank() && ui != DjUiState.IDLE) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "«$script»",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (hasBrain == false) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sin una clave de Gemini sólo digo frases fijas. Con tu clave (gratis) comento cada canción de verdad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                DezzyOutlinedButton(onClick = { showSettings = true }) { Text("Configurar") }
            }
            Spacer(Modifier.height(20.dp))
            Row2(
                ui = ui,
                onStart = { dj.startDjSession() },
                onStop = { dj.stop() },
            )
            Spacer(Modifier.height(20.dp))
            DjVibeRow(vibes) { dj.startDjSession(it.query) }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            DjRequestBar(djName) { dj.handleUserRequest(it) }
        }
    }
    DjTasteDialog(dj, djName)
}

@Composable
private fun Row2(ui: DjUiState, onStart: () -> Unit, onStop: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui == DjUiState.IDLE) {
            DezzyButton(onClick = onStart) { Text("Empezar sesión") }
        } else {
            DezzyOutlinedButton(onClick = onStop) {
                Icon(Icons.Rounded.Stop, null)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("Terminar")
            }
        }
    }
}

/** Pregunta de la primera vez: qué música le gusta al oyente. */
@Composable
internal fun DjTasteDialog(dj: DjController, djName: String) {
    val question by dj.askingTaste.collectAsState()
    val q = question ?: return
    var answer by remember(q) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { dj.cancelTaste() },
        title = { Text(djName) },
        text = {
            Column {
                Text(q)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = answer, onValueChange = { answer = it }, singleLine = false, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { dj.answerTaste(answer) }, enabled = answer.isNotBlank()) { Text("Responder") } },
        dismissButton = { TextButton(onClick = { dj.cancelTaste() }) { Text("Ahora no") } },
    )
}
