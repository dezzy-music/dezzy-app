/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dezzy.app.source.spotify.SpotifyClient
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.login.PlatformLoginWebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

/** Valor de la cookie `sp_dc` dentro de una cabecera `Cookie`. */
private fun spDcFrom(header: String): String? =
    header.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("sp_dc=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }

/** Inicio de sesión de Spotify: web de Spotify, captura de `sp_dc` y verificación. */
@Composable
internal fun SpotifyLoginScreen(ctx: ScreenContext) {
    val client: SpotifyClient = koinInject()
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var spDcValue by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    fun verify(spDc: String) {
        isProcessing = true
        hasError = false
        statusMessage = "Verificando sesión de Spotify..."
        ctx.scope.launch {
            val result = runCatching { client.completeLogin(spDc).getOrThrow() }
            result.fold(
                {
                    statusMessage = "¡Conectado a Spotify!"
                    delay(300)
                    ctx.navigator.back()
                },
                {
                    statusMessage = "Error: ${it.message?.takeIf { m -> m.isNotBlank() } ?: "desconocido"}"
                    hasError = true
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle("Iniciar sesión en Spotify", onBack = { ctx.navigator.back() })

        if (isProcessing && !hasError) LinearProgressIndicator(Modifier.fillMaxWidth())

        Box(Modifier.weight(1f).fillMaxSize()) {
            key(attempt) {
                PlatformLoginWebView(
                    startUrl = SPOTIFY_LOGIN_URL,
                    cookieUrl = "https://open.spotify.com",
                    isLoggedIn = { spDcFrom(it) != null },
                    onCaptured = { header ->
                        val spDc = spDcFrom(header)
                        if (spDc != null) {
                            spDcValue = spDc
                            verify(spDc)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isProcessing || hasError) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        if (!hasError) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(
                            text = statusMessage.ifEmpty { "Iniciando sesión..." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        if (hasError) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { spDcValue?.let { verify(it) } }) { Text("Reintentar") }
                            TextButton(
                                onClick = {
                                    // Volver a cargar la página de entrada desde cero.
                                    hasError = false
                                    isProcessing = false
                                    statusMessage = ""
                                    spDcValue = null
                                    attempt++
                                },
                            ) { Text("Empezar de nuevo") }
                        }
                    }
                }
            }
        }
    }
}
