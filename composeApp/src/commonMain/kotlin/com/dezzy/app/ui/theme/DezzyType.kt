/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import com.dezzy.app.resources.Res
import com.dezzy.app.resources.nunito_black
import com.dezzy.app.resources.nunito_bold
import com.dezzy.app.resources.nunito_extrabold
import com.dezzy.app.resources.nunito_medium
import com.dezzy.app.resources.nunito_regular
import com.dezzy.app.resources.nunito_semibold
import com.dezzy.app.resources.poppins_regular
import com.dezzy.app.resources.poppins_bold
import com.dezzy.app.resources.inter
import com.dezzy.app.resources.montserrat
import com.dezzy.app.resources.lora
import com.dezzy.app.resources.playfair_display
import com.dezzy.app.resources.rubik
import com.dezzy.app.resources.kanit_regular
import com.dezzy.app.resources.kanit_bold
import com.dezzy.app.resources.comfortaa
import com.dezzy.app.resources.pacifico
import com.dezzy.app.resources.lobster
import com.dezzy.app.resources.dancing_script
import com.dezzy.app.resources.caveat
import com.dezzy.app.resources.great_vibes
import com.dezzy.app.resources.cinzel
import com.dezzy.app.resources.outfit
import com.dezzy.app.resources.syne
import com.dezzy.app.resources.space_grotesk
import com.dezzy.app.resources.merriweather
import com.dezzy.app.resources.oswald
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.sp

private val base = Typography()

/**
 * Fuente de la app: las mismas 26 opciones y en el mismo orden que la app actual. Los archivos son
 * de Google Fonts (licencia OFL); «SF Pro Display» es de Apple y no se puede incluir, así que usa
 * Inter, la más parecida.
 */
enum class AppFont(val label: String) {
    POPPINS("Poppins (Predeterminada)"),
    SF_PRO("SF Pro Display (Premium)"),
    DEFAULT("Material 3"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    SYSTEM("Sistema"),
    INTER("Inter"),
    ROBOTO("Roboto"),
    MONTSERRAT("Montserrat"),
    LORA("Lora"),
    PLAYFAIR("Playfair Display"),
    NUNITO("Nunito"),
    RUBIK("Rubik"),
    KANIT("Kanit"),
    COMFORTAA("Comfortaa"),
    PACIFICO("Pacifico"),
    LOBSTER("Lobster"),
    DANCING_SCRIPT("Dancing Script"),
    CAVEAT("Caveat"),
    GREAT_VIBES("Great Vibes"),
    CINZEL("Cinzel"),
    OUTFIT("Outfit"),
    SYNE("Syne"),
    SPACE_GROTESK("Space Grotesk"),
    MERRIWEATHER("Merriweather"),
    OSWALD("Oswald"),
}

val AppFontKey = stringPreferencesKey("appFont")

@Composable
private fun nunitoFamily(): FontFamily = FontFamily(
    Font(Res.font.nunito_regular, FontWeight.Normal),
    Font(Res.font.nunito_medium, FontWeight.Medium),
    Font(Res.font.nunito_semibold, FontWeight.SemiBold),
    Font(Res.font.nunito_bold, FontWeight.Bold),
    Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(Res.font.nunito_black, FontWeight.Black),
)

/** Familia de un archivo variable o de peso único: el mismo archivo sirve para todos los pesos. */
@Composable
private fun single(res: FontResource): FontFamily = FontFamily(
    Font(res, FontWeight.Normal),
    Font(res, FontWeight.Medium),
    Font(res, FontWeight.SemiBold),
    Font(res, FontWeight.Bold),
    Font(res, FontWeight.ExtraBold),
    Font(res, FontWeight.Black),
)

/** Familia de [AppFont]; null = la tipografía de Material 3 sin cambios. */
@Composable
fun AppFont.fontFamily(): FontFamily? = when (this) {
    AppFont.POPPINS -> FontFamily(
        Font(Res.font.poppins_regular, FontWeight.Normal),
        Font(Res.font.poppins_regular, FontWeight.Medium),
        Font(Res.font.poppins_bold, FontWeight.SemiBold),
        Font(Res.font.poppins_bold, FontWeight.Bold),
        Font(Res.font.poppins_bold, FontWeight.ExtraBold),
        Font(Res.font.poppins_bold, FontWeight.Black),
    )
    AppFont.SF_PRO, AppFont.INTER -> single(Res.font.inter)
    AppFont.DEFAULT -> null
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONOSPACE -> FontFamily.Monospace
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.ROBOTO -> FontFamily.SansSerif
    AppFont.MONTSERRAT -> single(Res.font.montserrat)
    AppFont.LORA -> single(Res.font.lora)
    AppFont.PLAYFAIR -> single(Res.font.playfair_display)
    AppFont.NUNITO -> nunitoFamily()
    AppFont.RUBIK -> single(Res.font.rubik)
    AppFont.KANIT -> FontFamily(
        Font(Res.font.kanit_regular, FontWeight.Normal),
        Font(Res.font.kanit_regular, FontWeight.Medium),
        Font(Res.font.kanit_bold, FontWeight.SemiBold),
        Font(Res.font.kanit_bold, FontWeight.Bold),
        Font(Res.font.kanit_bold, FontWeight.ExtraBold),
        Font(Res.font.kanit_bold, FontWeight.Black),
    )
    AppFont.COMFORTAA -> single(Res.font.comfortaa)
    AppFont.PACIFICO -> single(Res.font.pacifico)
    AppFont.LOBSTER -> single(Res.font.lobster)
    AppFont.DANCING_SCRIPT -> single(Res.font.dancing_script)
    AppFont.CAVEAT -> single(Res.font.caveat)
    AppFont.GREAT_VIBES -> single(Res.font.great_vibes)
    AppFont.CINZEL -> single(Res.font.cinzel)
    AppFont.OUTFIT -> single(Res.font.outfit)
    AppFont.SYNE -> single(Res.font.syne)
    AppFont.SPACE_GROTESK -> single(Res.font.space_grotesk)
    AppFont.MERRIWEATHER -> single(Res.font.merriweather)
    AppFont.OSWALD -> single(Res.font.oswald)
}

/** Aplica la fuente elegida a todos los estilos de la tipografía. */
@Composable
fun Typography.withAppFont(font: AppFont): Typography {
    val f = font.fontFamily() ?: return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = f),
        displayMedium = displayMedium.copy(fontFamily = f),
        displaySmall = displaySmall.copy(fontFamily = f),
        headlineLarge = headlineLarge.copy(fontFamily = f),
        headlineMedium = headlineMedium.copy(fontFamily = f),
        headlineSmall = headlineSmall.copy(fontFamily = f),
        titleLarge = titleLarge.copy(fontFamily = f),
        titleMedium = titleMedium.copy(fontFamily = f),
        titleSmall = titleSmall.copy(fontFamily = f),
        bodyLarge = bodyLarge.copy(fontFamily = f),
        bodyMedium = bodyMedium.copy(fontFamily = f),
        bodySmall = bodySmall.copy(fontFamily = f),
        labelLarge = labelLarge.copy(fontFamily = f),
        labelMedium = labelMedium.copy(fontFamily = f),
        labelSmall = labelSmall.copy(fontFamily = f),
    )
}

/** Tipografía normal de Dezzy (fuente por defecto del sistema). */
val DezzyTypography = base.copy(
    // Títulos de pantalla
    headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    // Títulos de sección
    titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    // Filas
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    // Navegación
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)
