/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dezzy.app.source.youtube.YouTubeLogin
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.ScreenTitle
import com.dezzy.app.ui.screens.TopSpacer
import com.dezzy.app.ui.screens.login.PlatformLoginWebView
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val YOUTUBE_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/** Inicio de sesión de YouTube Music con la web de Google. */
@Composable
internal fun YouTubeLoginScreen(ctx: ScreenContext) {
    val login: YouTubeLogin = koinInject()
    var cookie by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun complete(header: String) {
        processing = true
        error = null
        ctx.scope.launch {
            val result = runCatching { login.complete(header).getOrThrow() }
            processing = false
            result.fold(
                { ctx.navigator.back() },
                { error = it.message?.takeIf { m -> m.isNotBlank() } ?: "No se pudo completar el inicio de sesión." },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle("Iniciar sesión", onBack = { ctx.navigator.back() })
        Box(Modifier.weight(1f).fillMaxSize()) {
            PlatformLoginWebView(
                startUrl = YOUTUBE_LOGIN_URL,
                cookieUrl = "https://music.youtube.com",
                isLoggedIn = { "SAPISID=" in it },
                onCaptured = { header ->
                    cookie = header
                    complete(header)
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (processing || error != null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        if (processing) {
                            CircularProgressIndicator()
                            Text("Iniciando sesión…", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(
                                error.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = { cookie?.let { complete(it) } }) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}
