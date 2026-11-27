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
import androidx.compose.runtime.setValue
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.prefs.*
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

    var hideExplicit by rememberPreference(HideExplicitKey, false)
    var hideVideos by rememberPreference(HideVideoSongsKey, false)
    var appLanguage by rememberPreference(AppLanguageKey, "system")
    var proxy by rememberPreference(ProxyEnabledKey, false)
    var lrclib by rememberPreference(EnableLrcLibKey, true)
    var kugou by rememberPreference(EnableKuGouKey, true)
    var betterLyrics by rememberPreference(EnableBetterLyricsKey, true)
    var simp by rememberPreference(EnableSimpMusicKey, true)
    var preferred by rememberPreference(PreferredLyricsProviderKey, "LrcLib")
    var romaJa by rememberPreference(RomanizeJapaneseKey, false)
    var romaKo by rememberPreference(RomanizeKoreanKey, false)
    var preload by rememberPreference(PreloadLyricsKey, false)
    var preloadCount by rememberPreference(PreloadLyricsCountKey, 1)

    SettingsPage(title = "Contenido", onBack = { ctx.navigator.back() }) {
        item(key = "content") {
            SettingsGroup("General") {
                SettingsChoiceRow(
                    title = "Idioma por defecto del contenido",
                    icon = Icons.Rounded.Language,
                    options = CONTENT_LANGUAGES.map { it.first },
                    selected = language,
                    label = ::languageName,
                    onSelect = { code -> ctx.scope.launch { ctx.prefs.setContentLanguage(code) } },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "País por defecto del contenido",
                    icon = Icons.Rounded.Public,
                    options = CONTENT_COUNTRIES.map { it.first },
                    selected = country,
                    label = ::countryName,
                    onSelect = { code -> ctx.scope.launch { ctx.prefs.setContentCountry(code) } },
                )
                SettingsDivider()
                SettingsSwitchRow(title = "Ocultar contenido explícito", checked = hideExplicit, onCheckedChange = { hideExplicit = it })
                SettingsDivider()
                SettingsSwitchRow(title = "Ocultar vídeos musicales", checked = hideVideos, onCheckedChange = { hideVideos = it })
            }
        }
        item(key = "app-language") {
            SettingsGroup("Idioma de la aplicación") {
                SettingsChoiceRow(
                    title = "Idioma de la aplicación",
                    options = listOf("system", "es", "en"),
                    selected = appLanguage,
                    label = { when (it) { "es" -> "Español"; "en" -> "English"; else -> "Predeterminado del sistema" } },
                    onSelect = { appLanguage = it },
                )
            }
        }
        item(key = "proxy") {
            SettingsGroup("Proxy") {
                SettingsSwitchRow(title = "Activar proxy", checked = proxy, onCheckedChange = { proxy = it })
            }
        }
        item(key = "lyrics") {
            SettingsGroup("Letras") {
                SettingsSwitchRow(title = "Activar el proveedor de letras LrcLib", checked = lrclib, onCheckedChange = { lrclib = it })
                SettingsDivider()
                SettingsSwitchRow(title = "Activar el proveedor de letras KuGou", checked = kugou, onCheckedChange = { kugou = it })
                SettingsDivider()
                SettingsSwitchRow(title = "Activar BetterLyrics", checked = betterLyrics, onCheckedChange = { betterLyrics = it })
                SettingsDivider()
                SettingsSwitchRow(title = "Activar letras de SimpMusic", checked = simp, onCheckedChange = { simp = it })
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Proveedor preferido de letras",
                    options = listOf("LrcLib", "KuGou", "BetterLyrics", "SimpMusic"),
                    selected = preferred,
                    label = { it },
                    onSelect = { preferred = it },
                )
                SettingsDivider()
                SettingsSwitchRow(title = "Romanizar letras Japonesas", checked = romaJa, onCheckedChange = { romaJa = it })
                SettingsDivider()
                SettingsSwitchRow(title = "Romanizar letras Coreanas", checked = romaKo, onCheckedChange = { romaKo = it })
                SettingsDivider()
                SettingsSwitchRow(title = "Preload queue lyrics", checked = preload, onCheckedChange = { preload = it })
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Number of songs to preload",
                    options = listOf(1, 2, 3, 5, 10),
                    selected = preloadCount,
                    label = { "$it" },
                    onSelect = { preloadCount = it },
                )
            }
        }
    }
}
