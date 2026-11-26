/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Color de marca de Dezzy. */
val DezzyBrand = Color(0xFFED5564)

val DezzyLightColors = lightColorScheme(
    primary = Color(0xFF9C3F48),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADB),
    onPrimaryContainer = Color(0xFF40000E),
    secondary = Color(0xFF775657),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADA),
    onSecondaryContainer = Color(0xFF2C1516),
    tertiary = Color(0xFF755A2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDAF),
    onTertiaryContainer = Color(0xFF281800),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF3DDDE),
    onSurfaceVariant = Color(0xFF534344),
    outline = Color(0xFF857373),
    outlineVariant = Color(0xFFD8C1C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFEF1F1),
    surfaceContainer = Color(0xFFF8EBEB),
    surfaceContainerHigh = Color(0xFFF3E5E5),
    surfaceContainerHighest = Color(0xFFEDE0E0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

val DezzyDarkColors = darkColorScheme(
    primary = Color(0xFFFFB2B6),
    onPrimary = Color(0xFF5F1320),
    primaryContainer = Color(0xFF7E2832),
    onPrimaryContainer = Color(0xFFFFDADB),
    secondary = Color(0xFFE6BDBD),
    onSecondary = Color(0xFF44292A),
    secondaryContainer = Color(0xFF5D3F40),
    onSecondaryContainer = Color(0xFFFFDADA),
    tertiary = Color(0xFFE5C18D),
    onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF5B4319),
    onTertiaryContainer = Color(0xFFFFDDAF),
    background = Color(0xFF1A1112),
    onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF1A1112),
    onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF534344),
    onSurfaceVariant = Color(0xFFD8C1C2),
    outline = Color(0xFFA08C8C),
    outlineVariant = Color(0xFF534344),
    surfaceContainerLowest = Color(0xFF140C0D),
    surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF271D1E),
    surfaceContainerHigh = Color(0xFF322828),
    surfaceContainerHighest = Color(0xFF3D3233),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Variante de negro puro (OLED): fondos negros y contenedores casi negros. */
fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
