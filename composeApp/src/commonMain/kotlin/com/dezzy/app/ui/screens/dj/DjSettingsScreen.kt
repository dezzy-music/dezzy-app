/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.dj

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.feature.dj.DjStore
import com.dezzy.app.feature.dj.DjVoice
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.SettingsChoiceRow
import com.dezzy.app.ui.screens.settings.SettingsDivider
import com.dezzy.app.ui.screens.settings.SettingsGroup
import com.dezzy.app.ui.screens.settings.SettingsPage
import com.dezzy.app.ui.screens.settings.SettingsRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Ajustes del DJ: nombre, idioma, estilo, cuánto habla, claves y voz. */
@Composable
fun DjSettingsScreen(ctx: ScreenContext, onBack: () -> Unit = { ctx.navigator.back() }) {
    val settings: DjSettings = koinInject()
    val store: DjStore = koinInject()
    val language by settings.language.collectAsState("auto")
    val style by settings.style.collectAsState("radio")
    val talk by settings.talk.collectAsState("blocks")
    val provider by settings.provider.collectAsState(DjVoice.FREE)
    val voiceName by settings.voiceName.collectAsState("")
    val savedName by settings.name.collectAsState("")
    val keysVersion by settings.keysVersion.collectAsState()
    var name by remember(savedName) { mutableStateOf(savedName) }
    var hasGemini by remember { mutableStateOf(false) }
    var wizard by remember { mutableStateOf(false) }

    LaunchedEffect(keysVersion) { hasGemini = settings.geminiKey().isNotBlank() }

    if (wizard) {
        PlatformBackHandler(enabled = true) { wizard = false }
        DjVoiceSetupScreen(ctx, onClose = { wizard = false })
        return
    }

    SettingsPage("DJ", onBack = onBack) {
        item(key = "name") {
            SettingsGroup("Tu DJ") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("Nombre (DJ …)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { ctx.scope.launch { settings.setName(name); ctx.toast("Nombre guardado") } },
                        enabled = name.trim() != savedName.trim(),
                    ) { Text("Guardar") }
                }
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Idioma",
                    icon = Icons.Rounded.Language,
                    options = listOf("auto", "es", "en"),
                    selected = language,
                    label = { when (it) { "es" -> "Español"; "en" -> "English"; else -> "Automático" } },
                    onSelect = { ctx.scope.launch { settings.setLanguage(it) } },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Estilo",
                    icon = Icons.Rounded.GraphicEq,
                    options = listOf("radio", "party", "chill"),
                    selected = style,
                    label = { when (it) { "party" -> "De fiesta"; "chill" -> "Relajado"; else -> "Radio" } },
                    onSelect = { ctx.scope.launch { settings.setStyle(it) } },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Cuánto habla",
                    icon = Icons.Rounded.Speed,
                    options = listOf("start", "blocks", "often"),
                    selected = talk,
                    label = {
                        when (it) {
                            "start" -> "Sólo al empezar"
                            "often" -> "A menudo (cada 2 canciones)"
                            else -> "Al cambiar de bloque"
                        }
                    },
                    onSelect = { ctx.scope.launch { settings.setTalk(it) } },
                )
            }
        }
        item(key = "brain") {
            SettingsGroup("Cerebro y voz") {
                SettingsRow(
                    title = "Gemini (el cerebro)",
                    subtitle = if (hasGemini) "Clave guardada: el DJ comenta cada canción" else "Sin clave: sólo frases fijas",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = { wizard = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Voz",
                    subtitle = when (provider) {
                        DjVoice.FREE -> "Voz gratuita del sistema"
                        else -> DjVoice.providerName(provider) + voiceName.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    },
                    icon = Icons.Rounded.Mic,
                    onClick = { wizard = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Asistente de configuración",
                    subtitle = "Cerebro, proveedor y voz paso a paso",
                    icon = Icons.Rounded.Tune,
                    onClick = { wizard = true },
                )
                if (hasGemini || provider != DjVoice.FREE) {
                    SettingsDivider()
                    SettingsRow(
                        title = "Borrar mis claves",
                        subtitle = "Quita Gemini y la voz de pago de este dispositivo",
                        icon = Icons.Rounded.Delete,
                        onClick = {
                            ctx.scope.launch {
                                settings.saveGeminiKey("")
                                settings.forgetVoice()
                                ctx.toast("Claves borradas")
                            }
                        },
                    )
                }
            }
        }
        item(key = "taste") {
            SettingsGroup("Gustos") {
                SettingsRow(
                    title = "Olvidar saltos",
                    subtitle = "El DJ vuelve a ofrecerte lo que saltaste",
                    icon = Icons.Rounded.Person,
                    onClick = { ctx.scope.launch { store.clearSkips(); ctx.toast("Saltos olvidados") } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Olvidar lo que conté de mis gustos",
                    subtitle = "La próxima vez te lo vuelve a preguntar",
                    icon = Icons.Rounded.Delete,
                    onClick = { ctx.scope.launch { settings.setTaste(""); ctx.toast("Hecho") } },
                )
            }
        }
    }
}
