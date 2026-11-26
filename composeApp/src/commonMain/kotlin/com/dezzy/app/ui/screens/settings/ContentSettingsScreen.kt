/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch

/** Idiomas del catálogo (código a YouTube Music) con su nombre en español. */
internal val CONTENT_LANGUAGES: List<Pair<String, String>> = listOf(
    "es" to "Español",
    "en" to "Inglés",
    "pt" to "Portugués",
    "fr" to "Francés",
    "de" to "Alemán",
    "it" to "Italiano",
)

/** Países del catálogo (código ISO) con su nombre en español. */
internal val CONTENT_COUNTRIES: List<Pair<String, String>> = listOf(
    "MX" to "México",
    "ES" to "España",
    "US" to "Estados Unidos",
    "AR" to "Argentina",
    "CO" to "Colombia",
    "CL" to "Chile",
    "PE" to "Perú",
    "VE" to "Venezuela",
    "EC" to "Ecuador",
    "UY" to "Uruguay",
    "BR" to "Brasil",
    "PT" to "Portugal",
    "GB" to "Reino Unido",
    "CA" to "Canadá",
    "FR" to "Francia",
    "DE" to "Alemania",
    "IT" to "Italia",
)

internal fun languageName(code: String): String = CONTENT_LANGUAGES.firstOrNull { it.first == code }?.second ?: code

internal fun countryName(code: String): String = CONTENT_COUNTRIES.firstOrNull { it.first == code }?.second ?: code

/** «Contenido»: idioma y país con los que YouTube Music arma el catálogo. */
@Composable
internal fun ContentSettingsScreen(ctx: ScreenContext) {
    val language by ctx.prefs.contentLanguage.collectAsState("es")
    val country by ctx.prefs.contentCountry.collectAsState("US")

    SettingsPage(title = "Contenido", onBack = { ctx.navigator.back() }) {
        item(key = "content") {
            SettingsGroup {
                SettingsChoiceRow(
                    title = "Idioma del contenido",
                    icon = Icons.Rounded.Language,
                    options = CONTENT_LANGUAGES.map { it.first },
                    selected = language,
                    label = ::languageName,
                    onSelect = { code -> ctx.scope.launch { ctx.prefs.setContentLanguage(code) } },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "País del contenido",
                    icon = Icons.Rounded.Public,
                    options = CONTENT_COUNTRIES.map { it.first },
                    selected = country,
                    label = ::countryName,
                    onSelect = { code -> ctx.scope.launch { ctx.prefs.setContentCountry(code) } },
                )
            }
        }
    }
}
