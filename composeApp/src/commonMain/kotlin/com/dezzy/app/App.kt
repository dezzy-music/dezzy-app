/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dezzy.app.feature.player.PlayerTestScreen
import org.koin.compose.koinInject

@Composable
fun App() {
    val darkColors = darkColorScheme(
        primary = Color(0xFF6C5CE7),
        background = Color(0xFF0F0E17),
        surface = Color(0xFF1B1A24),
        onPrimary = Color.White,
        onBackground = Color(0xFFFFFFFE),
        onSurface = Color(0xFFFFFFFE)
    )

    MaterialTheme(colorScheme = darkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PlayerTestScreen(player = koinInject(), client = koinInject())
        }
    }
}
