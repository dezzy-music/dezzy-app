/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Wallpaper
import com.dezzy.app.ui.player.kit.LyricsBackground
import com.dezzy.app.ui.player.kit.LyricsBackgroundKey
import com.dezzy.app.ui.player.kit.LyricsBgBlurKey
import com.dezzy.app.ui.player.kit.LyricsBgDimKey
import com.dezzy.app.ui.player.kit.LyricsBgImageUriKey
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.dezzy.app.feature.lyrics.LyricsTranslateLangKey
import com.dezzy.app.feature.lyrics.TRANSLATE_LANGUAGES
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.player.lyrics.LyricsAlign
import com.dezzy.app.ui.player.lyrics.LyricsAlignKey
import com.dezzy.app.ui.player.lyrics.LyricsAnim
import com.dezzy.app.ui.player.lyrics.LyricsAnimKey
import com.dezzy.app.ui.player.lyrics.LyricsSyncOffsetKey
import com.dezzy.app.ui.player.lyrics.LyricsTextScaleKey
import com.dezzy.app.ui.screens.ScreenContext
import kotlin.math.roundToInt

private const val NO_TRANSLATION = ""

@Composable
internal fun LyricsSettingsScreen(ctx: ScreenContext) {
    var lang by rememberPreference(LyricsTranslateLangKey, NO_TRANSLATION)
    var align by rememberEnumPreference(LyricsAlignKey, LyricsAlign.LEFT)
    var anim by rememberEnumPreference(LyricsAnimKey, LyricsAnim.FOCUS)
    var scale by rememberPreference(LyricsTextScaleKey, 1f)
    var offset by rememberPreference(LyricsSyncOffsetKey, 0)

    var bgMode by rememberEnumPreference(LyricsBackgroundKey, LyricsBackground.SAME_AS_PLAYER)
    var bgImage by rememberPreference(LyricsBgImageUriKey, "")
    var bgBlur by rememberPreference(LyricsBgBlurKey, 0f)
    var bgDim by rememberPreference(LyricsBgDimKey, 0.4f)
    val pickBgImage = rememberOwnImagePicker(bgImage) { bgImage = it }

    SettingsPage("Letras e IA", onBack = { ctx.navigator.back() }) {
        item(key = "translate") {
            SettingsGroup("Traducción") {
                SettingsChoiceRow(
                    title = "Traducir a",
                    icon = Icons.Rounded.Translate,
                    options = listOf(NO_TRANSLATION) + TRANSLATE_LANGUAGES.map { it.first },
                    selected = lang,
                    label = { code -> TRANSLATE_LANGUAGES.firstOrNull { it.first == code }?.second ?: "No traducir" },
                    onSelect = { lang = it },
                    subtitle = null,
                )
            }
        }
        item(key = "background") {
            SettingsGroup("Fondo de las letras") {
                SettingsChoiceRow(
                    title = "Fondo",
                    icon = Icons.Rounded.Wallpaper,
                    options = LyricsBackground.entries.toList(),
                    selected = bgMode,
                    label = {
                        when (it) {
                            LyricsBackground.SAME_AS_PLAYER -> "Igual que el reproductor"
                            LyricsBackground.COVER_GRADIENT -> "Degradado de la carátula"
                            LyricsBackground.SOLID -> "Color sólido"
                            LyricsBackground.IMAGE -> "Imagen propia"
                        }
                    },
                    onSelect = { bgMode = it },
                )
                if (bgMode == LyricsBackground.IMAGE) {
                    SettingsDivider()
                    SettingsRow(
                        title = "Imagen de fondo",
                        subtitle = if (bgImage.isBlank()) "Elegir una imagen de la galería" else "Cambiar la imagen",
                        icon = Icons.Rounded.Image,
                        onClick = { pickBgImage() },
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        title = "Desenfoque",
                        icon = Icons.Rounded.BlurOn,
                        value = bgBlur,
                        valueRange = 0f..40f,
                        steps = 7,
                        valueLabel = { it.roundToInt().toString() },
                        onValueChangeFinished = { bgBlur = it },
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        title = "Atenuación",
                        icon = Icons.Rounded.Contrast,
                        value = bgDim,
                        valueRange = 0f..0.9f,
                        steps = 8,
                        valueLabel = { "${(it * 100).roundToInt()} %" },
                        onValueChangeFinished = { bgDim = it },
                    )
                }
            }
        }
        item(key = "look") {
            SettingsGroup("Letras") {
                SettingsChoiceRow(
                    title = "Posición del texto de las letras",
                    icon = Icons.Rounded.FormatAlignLeft,
                    options = LyricsAlign.entries.toList(),
                    selected = align,
                    label = { it.label },
                    onSelect = { align = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Estilo de animación de letras",
                    icon = Icons.Rounded.Animation,
                    options = LyricsAnim.entries.toList(),
                    selected = anim,
                    label = { it.label },
                    onSelect = { anim = it },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Tamaño del texto",
                    icon = Icons.Rounded.FormatSize,
                    value = scale,
                    valueRange = 0.7f..1.6f,
                    steps = 8,
                    valueLabel = { "${(it * 100).roundToInt()} %" },
                    onValueChangeFinished = { scale = it },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Desfase de sincronía",
                    icon = Icons.Rounded.Timer,
                    value = offset.toFloat(),
                    valueRange = -2000f..2000f,
                    steps = 39,
                    valueLabel = { "${it.roundToInt()} ms" },
                    onValueChangeFinished = { offset = it.roundToInt() },
                )
            }
        }
    }
}
