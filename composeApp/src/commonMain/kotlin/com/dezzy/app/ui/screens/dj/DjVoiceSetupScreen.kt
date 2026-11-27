/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.feature.dj.DjVoice
import com.dezzy.app.feature.dj.DjVoiceApi
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.components.glass.DezzyTextButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Asistente de 4 pasos: cerebro (clave de Gemini), proveedor de voz, clave de la voz y voz.
 * Las claves son del propio usuario y se guardan cifradas en el dispositivo.
 */
@Composable
fun DjVoiceSetupScreen(ctx: ScreenContext, onClose: () -> Unit = { ctx.navigator.back() }) {
    val settings: DjSettings = koinInject()
    val api: DjVoiceApi = koinInject()
    val uri = LocalUriHandler.current

    var step by remember { mutableIntStateOf(0) }
    var geminiKey by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(DjVoice.FREE) }
    var ttsKey by remember { mutableStateOf("") }
    var voices by remember { mutableStateOf<List<DjVoice.Voice>>(emptyList()) }
    var voiceId by remember { mutableStateOf("") }
    var voiceName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        geminiKey = settings.geminiKey()
        provider = settings.currentProvider()
    }

    val goBack: () -> Unit = {
        message = null
        if (step == 0) onClose() else step = if (step == 3 && provider == DjVoice.FREE) 1 else step - 1
    }
    PlatformBackHandler(enabled = true, onBack = goBack)

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle("Voz del DJ", onBack = goBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Paso ${step + 1} de 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            when (step) {
                0 -> {
                    Text("El cerebro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Con una clave de Gemini (gratuita) el DJ comenta cada canción con datos de tu historial. " +
                            "La clave es tuya: se guarda cifrada en este dispositivo y sólo se envía a Google.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    DezzyTextButton(onClick = { runCatching { uri.openUri(DjVoice.GEMINI_KEY_URL) } }) { Text("Conseguir una clave") }
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it; message = null },
                        label = { Text("Clave de Gemini") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                1 -> {
                    Text("La voz", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Elige quién habla. Las de pago usan tu propia clave.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        DjVoice.FREE to "Gratuita (voz del sistema)",
                        DjVoice.ELEVENLABS to "ElevenLabs (natural, con clave)",
                        DjVoice.OPENAI to "OpenAI (natural, con clave)",
                    ).forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { provider = id; message = null },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = provider == id, onClick = { provider = id; message = null })
                            Text(label)
                        }
                    }
                }
                2 -> {
                    Text("Clave de ${DjVoice.providerName(provider)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Se guarda cifrada en este dispositivo. El gasto va a tu cuenta del proveedor.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    DezzyTextButton(onClick = { runCatching { uri.openUri(DjVoice.keyUrl(provider)) } }) { Text("Conseguir una clave") }
                    OutlinedTextField(
                        value = ttsKey,
                        onValueChange = { ttsKey = it; message = null },
                        label = { Text("Clave de ${DjVoice.providerName(provider)}") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    remaining?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Te quedan $it caracteres este mes.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> {
                    Text("Elige una voz", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    voices.forEach { v ->
                        Row(
                            Modifier.fillMaxWidth().clickable { voiceId = v.id; voiceName = v.name },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = voiceId == v.id, onClick = { voiceId = v.id; voiceName = v.name })
                            Column {
                                Text(v.name, fontWeight = FontWeight.Medium)
                                if (v.description.isNotBlank()) {
                                    Text(v.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (step == 0) {
                DezzyOutlinedButton(onClick = { step = 1; message = null }, modifier = Modifier.weight(1f)) { Text("Omitir") }
            }
            DezzyButton(
                onClick = {
                    ctx.scope.launch {
                        message = null
                        when (step) {
                            0 -> {
                                if (geminiKey.isBlank()) { step = 1; return@launch }
                                busy = true
                                val check = api.checkGemini(geminiKey)
                                busy = false
                                if (check.ok) { settings.saveGeminiKey(geminiKey); step = 1 } else message = check.message
                            }
                            1 -> {
                                if (provider == DjVoice.FREE) {
                                    settings.forgetVoice()
                                    ctx.toast("Voz gratuita activada")
                                    onClose()
                                } else step = 2
                            }
                            2 -> {
                                busy = true
                                val check = api.check(provider, ttsKey)
                                busy = false
                                if (check.ok) {
                                    voices = check.voices
                                    remaining = check.remaining
                                    val first = voices.firstOrNull { it.id == DjVoice.defaultVoice(provider) } ?: voices.firstOrNull()
                                    voiceId = first?.id ?: DjVoice.defaultVoice(provider)
                                    voiceName = first?.name.orEmpty()
                                    step = 3
                                } else message = check.message
                            }
                            else -> {
                                settings.saveVoice(provider, ttsKey, voiceId.ifBlank { DjVoice.defaultVoice(provider) }, voiceName)
                                ctx.toast("Voz guardada")
                                onClose()
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        busy -> "Comprobando..."
                        step == 0 -> if (geminiKey.isBlank()) "Continuar" else "Comprobar y guardar"
                        step == 1 -> if (provider == DjVoice.FREE) "Usar voz gratuita" else "Siguiente"
                        step == 2 -> "Comprobar clave"
                        else -> "Guardar"
                    },
                )
            }
        }
    }
}
