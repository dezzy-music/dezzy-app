/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference

/** Tema raíz de Dezzy: color, tipografía y estilo de interfaz (normal, cristal líquido o Nothing). */
@Composable
fun DezzyTheme(
    darkMode: DarkMode = DarkMode.SYSTEM,
    pureBlack: Boolean = false,
    dynamicColor: Boolean = false,
    interfaceTheme: AppInterfaceTheme = AppInterfaceTheme.NORMAL,
    glassIntensity: LiquidGlassIntensity = LiquidGlassIntensity.PURE,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    val activePalette by rememberPreference(ColorPaletteKey, "")
    val customPalettes by rememberPreference(CustomPalettesKey, "[]")
    val paletteSeeds = remember(activePalette, customPalettes) { resolveActiveSeeds(activePalette, customPalettes) }

    var scheme = if (interfaceTheme == AppInterfaceTheme.NOTHING) {
        buildNothingColorScheme(dark)
    } else if (paletteSeeds != null) {
        buildSchemeFromSeeds(paletteSeeds, dark)
    } else {
        (if (dynamicColor) platformDynamicColorScheme(dark) else null)
            ?: (if (dark) DezzyDarkColors else DezzyLightColors)
    }
    if (dark && pureBlack && interfaceTheme != AppInterfaceTheme.NOTHING) {
        scheme = scheme.toPureBlack()
    }

    val appFont by rememberEnumPreference(AppFontKey, AppFont.DEFAULT)
    val typography = if (interfaceTheme == AppInterfaceTheme.NOTHING) nothingTypography() else DezzyTypography.withAppFont(appFont)

    CompositionLocalProvider(
        LocalAppInterfaceTheme provides interfaceTheme,
        LocalLiquidGlassIntensity provides glassIntensity,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content,
        )
    }
}
