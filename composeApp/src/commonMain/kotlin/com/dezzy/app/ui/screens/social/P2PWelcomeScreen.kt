/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.social.P2PIdentity
import com.dezzy.app.feature.social.P2PSocial
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.components.glass.DezzyOutlinedButton
import com.dezzy.app.ui.components.glass.DezzyTextButton
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class WelcomeStep { INTRO, WORDS, RESTORE }

/**
 * Bienvenida a la red social: crear cuenta (nombre → 12 palabras de recuperación) o restaurar una.
 * [embedded] = dentro de la pestaña Social (sin flecha de «atrás»); [onBusy] avisa de que ya hay una
 * cuenta a medio crear para que la pestaña no cambie hasta [onDone].
 */
@Composable
fun P2PWelcomeScreen(
    ctx: ScreenContext,
    embedded: Boolean = false,
    onBusy: () -> Unit = {},
    onDone: () -> Unit = { ctx.navigator.back() },
) {
    val identity: P2PIdentity = koinInject()
    val social: P2PSocial = koinInject()
    var step by remember { mutableStateOf(WelcomeStep.INTRO) }
    var name by remember { mutableStateOf("") }
    var words by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PlatformBackHandler(enabled = step != WelcomeStep.INTRO) { step = WelcomeStep.INTRO }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopSpacer()
        ScreenTitle(
            when (step) {
                WelcomeStep.INTRO -> "Red social"
                WelcomeStep.WORDS -> "Tus 12 palabras"
                WelcomeStep.RESTORE -> "Restaurar cuenta"
            },
            onBack = if (step != WelcomeStep.INTRO) ({ step = WelcomeStep.INTRO }) else if (!embedded) ({ ctx.navigator.back() }) else null,
        )
        when (step) {
            WelcomeStep.INTRO -> WelcomeIntro(
                name = name,
                onName = { if (it.length <= 40) name = it },
                busy = busy,
                error = error,
                onCreate = {
                    busy = true
                    error = null
                    ctx.scope.launch {
                        runCatching {
                            onBusy()
                            words = identity.create()
                            runCatching { social.updateMyProfile(name.trim().ifBlank { "Dezzy" }, "", null, false) }
                        }.onSuccess { step = WelcomeStep.WORDS }
                            .onFailure { error = "No se pudo crear la cuenta. Inténtalo otra vez."; onDone() }
                        busy = false
                    }
                },
                onRestore = { step = WelcomeStep.RESTORE },
            )
            WelcomeStep.WORDS -> P2PBackupWordsContent(
                words = words,
                confirmLabel = "Continuar",
                onConfirm = {
                    ctx.scope.launch {
                        runCatching { identity.markBackedUp() }
                        onDone()
                    }
                },
            )
            WelcomeStep.RESTORE -> RestoreContent(
                busy = busy,
                error = error,
                onRestore = { input ->
                    busy = true
                    error = null
                    ctx.scope.launch {
                        onBusy()
                        val clean = input.trim()
                        val result = if (clean.startsWith("nsec", ignoreCase = true)) {
                            identity.restoreNsec(clean)
                        } else {
                            identity.restore(clean.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() })
                        }
                        busy = false
                        if (result.isSuccess) {
                            runCatching { identity.markBackedUp() }
                            onDone()
                        } else {
                            error = "Las palabras no son válidas. Revisa que sean 12 y estén en orden."
                            onDone()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeIntro(
    name: String,
    onName: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (SocialVisibility.FRIENDS_AND_MESSAGES) "Chatea con tus amigos, mira qué escuchan y comparte canciones. Todo cifrado y sin teléfono ni correo."
            else "Mira quién escucha Dezzy cerca de ti y qué suena en el mapa. Sin teléfono ni correo.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (SocialVisibility.FRIENDS_AND_MESSAGES) {
            FeatureRow(Icons.Rounded.Chat, "Mensajes cifrados", "Sólo tú y tu amigo podéis leerlos.")
            FeatureRow(Icons.Rounded.Headphones, "Escuchando ahora", "Ve lo que suena en la música de tus amigos.")
        } else {
            FeatureRow(Icons.Rounded.Headphones, "Cerca de ti", "Ve quién usa Dezzy cerca y qué está escuchando.")
        }
        FeatureRow(Icons.Rounded.VpnKey, "Tu cuenta son 12 palabras", "Guárdalas: con ellas la recuperas en cualquier móvil.")
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Tu nombre (opcional)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        DezzyButton(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Creando…" else "Crear mi cuenta")
        }
        DezzyOutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ya tengo una cuenta") }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Muestra las 12 palabras (con la pantalla protegida contra capturas). Con [confirmLabel] pide marcar
 * que se han guardado antes de continuar; sin él, sólo se enseñan.
 */
@Composable
internal fun P2PBackupWordsContent(
    words: List<String>,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
) {
    SecureWindow()
    var saved by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Apúntalas en papel y guárdalas en un sitio seguro. Quien las tenga puede entrar en tu cuenta, y si las pierdes no se pueden recuperar.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        words.chunked(3).forEachIndexed { row, group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEachIndexed { i, word ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "${row * 3 + i + 1}. $word",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        if (confirmLabel != null) {
            Row(Modifier.fillMaxWidth().clickable { saved = !saved }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saved, onCheckedChange = { saved = it })
                Spacer(Modifier.width(4.dp))
                Text("He guardado mis 12 palabras", fontSize = 15.sp)
            }
            DezzyButton(onClick = onConfirm, enabled = saved, modifier = Modifier.fillMaxWidth()) { Text(confirmLabel) }
        }
    }
}

@Composable
private fun RestoreContent(busy: Boolean, error: String?, onRestore: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Escribe tus 12 palabras separadas por espacios (o tu clave nsec).",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("12 palabras o nsec") },
            minLines = 3,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        DezzyTextButton(onClick = { show = !show }) { Text(if (show) "Ocultar" else "Mostrar") }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        DezzyButton(onClick = { onRestore(input) }, enabled = input.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Restaurando…" else "Restaurar")
        }
    }
}
