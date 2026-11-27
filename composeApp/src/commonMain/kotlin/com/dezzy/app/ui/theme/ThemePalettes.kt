/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Paleta activa: "" = ninguna (colores de siempre), "preset:N", "custom:ID" o "inline:#p,#s,#t,#n". */
val ColorPaletteKey = stringPreferencesKey("colorPalette")

/** Paletas propias guardadas, como arreglo JSON. */
val CustomPalettesKey = stringPreferencesKey("customPalettes")

/** Las cuatro semillas de un tema. */
data class ThemeSeeds(val primary: Color, val secondary: Color, val tertiary: Color, val neutral: Color)

/** Paleta propia guardada. */
data class CustomPalette(val id: String, val name: String, val seeds: ThemeSeeds)

/** Orden EXACTO de las 65 semillas del carrusel. */
val PaletteSeedValues: List<Long> = listOf(
    0xED5564, 0x4A90D9, 0x00BFFF, 0x2C3E50, 0x87CEEB, 0x0047AB, 0x7DF9FF, 0x2ECC71, 0x1ABC9C, 0x228B22,
    0x1DB954, 0x98FF98, 0x808000, 0x9CAF88, 0xE67E22, 0xF39C12, 0xFFBF00, 0xFF9800, 0xFFDAB9, 0xFF8243,
    0x9B59B6, 0xB39DDB, 0x6B5B95, 0xEE82EE, 0x9966CC, 0x645394, 0xFFB7C5, 0xF7CAC9, 0xFF00FF, 0xFF69B4,
    0xDE5D83, 0xFF7F50, 0xFFC1CC, 0xDC143C, 0xFF0000, 0x722F37, 0xE0115F, 0xFF2400, 0x36454F, 0xC0C0C0,
    0x708090, 0xE2725B, 0x6F4E37, 0x474747, 0x967969, 0xC2B280, 0xB66A50, 0xFFD1DC, 0xAEC6CF, 0x77DD77,
    0xFDFD96, 0xB19CD9, 0x39FF14, 0xFF10F0, 0x00F5FF, 0xFF5F1F, 0xFF6EC7, 0x006994, 0x0B3D0B, 0xD2691E,
    0xADD8E6, 0x98FB98, 0xFFD700, 0x4B0082, 0x00FF7F,
)

val PaletteSeedColors: List<Color> = PaletteSeedValues.map { Color((0xFF000000L or it).toInt()) }

/** Nombres cortos, en el mismo orden que [PaletteSeedValues]. */
val PaletteSeedNames: List<String> = listOf(
    "Coral Dezzy", "Azul cielo", "Celeste", "Azul medianoche", "Azul claro", "Cobalto", "Eléctrico", "Esmeralda", "Turquesa", "Verde bosque",
    "Verde música", "Menta", "Oliva", "Salvia", "Zanahoria", "Naranja", "Ámbar", "Naranja vivo", "Melocotón", "Mandarina",
    "Amatista", "Lavanda", "Violeta", "Orquídea", "Amatista clara", "Púrpura", "Cerezo", "Rosa cuarzo", "Magenta", "Rosa fuerte",
    "Frambuesa", "Coral", "Rosa pastel", "Carmesí", "Rojo", "Vino", "Rubí", "Escarlata", "Pizarra", "Plata",
    "Gris pizarra", "Terracota", "Café", "Grafito", "Topo", "Arena", "Arcilla", "Algodón", "Azul pastel", "Verde pastel",
    "Limón pastel", "Glicina", "Verde neón", "Rosa neón", "Cian neón", "Naranja neón", "Chicle", "Mar profundo", "Verde selva", "Chocolate",
    "Hielo", "Prado", "Dorado", "Índigo", "Primavera",
)

// ── HSL a mano ───────────────────────────────────────────────────────────────────────────────

private class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d < 1e-6f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f)).coerceAtLeast(1e-6f)
    val h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s.coerceIn(0f, 1f)
    val ll = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * ll - 1f)) * ss
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = ll - c / 2f
    val (r, g, b) = when {
        hh < 60f -> Triple(c, x, 0f)
        hh < 120f -> Triple(x, c, 0f)
        hh < 180f -> Triple(0f, c, x)
        hh < 240f -> Triple(0f, x, c)
        hh < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, 1f)
}

/** Mismo tono y saturación (con tope) de la semilla, con la luminosidad pedida. */
private fun Color.tone(l: Float, maxSat: Float = 0.95f): Color {
    val hsl = toHsl()
    return hslColor(hsl.h, min(hsl.s, maxSat), l)
}

/** Mezcla [fg] sobre [bg] en la proporción [t] (0 = sólo fondo, 1 = sólo fg). */
fun mixOver(bg: Color, fg: Color, t: Float): Color = lerp(bg, fg, t)

// ── Semillas y esquema ───────────────────────────────────────────────────────────────────────

/** Deriva las otras tres semillas a partir de la primaria: ±60° de tono y neutro casi sin saturación. */
fun seedsFromPrimary(primary: Color): ThemeSeeds {
    val hsl = primary.toHsl()
    return ThemeSeeds(
        primary = primary,
        secondary = hslColor(hsl.h - 60f, hsl.s * 0.7f, hsl.l),
        tertiary = hslColor(hsl.h + 60f, hsl.s * 0.85f, hsl.l),
        neutral = hslColor(hsl.h, hsl.s * 0.12f, 0.5f),
    )
}

fun presetSeeds(index: Int): ThemeSeeds = seedsFromPrimary(PaletteSeedColors[index.coerceIn(0, PaletteSeedColors.lastIndex)])

/** Esquema M3 claro u oscuro, coherente, a partir de las cuatro semillas. */
fun buildSchemeFromSeeds(seeds: ThemeSeeds, dark: Boolean): ColorScheme {
    val p = seeds.primary
    val s = seeds.secondary
    val t = seeds.tertiary
    val n = seeds.neutral
    return if (dark) {
        darkColorScheme(
            primary = p.tone(0.78f), onPrimary = p.tone(0.16f),
            primaryContainer = p.tone(0.30f), onPrimaryContainer = p.tone(0.90f),
            secondary = s.tone(0.78f), onSecondary = s.tone(0.16f),
            secondaryContainer = s.tone(0.30f), onSecondaryContainer = s.tone(0.90f),
            tertiary = t.tone(0.78f), onTertiary = t.tone(0.16f),
            tertiaryContainer = t.tone(0.30f), onTertiaryContainer = t.tone(0.90f),
            background = n.tone(0.07f, 0.12f), onBackground = n.tone(0.90f, 0.12f),
            surface = n.tone(0.07f, 0.12f), onSurface = n.tone(0.90f, 0.12f),
            surfaceVariant = n.tone(0.25f, 0.15f), onSurfaceVariant = n.tone(0.80f, 0.15f),
            surfaceTint = p.tone(0.78f),
            inverseSurface = n.tone(0.90f, 0.12f), inverseOnSurface = n.tone(0.20f, 0.12f),
            inversePrimary = p.tone(0.40f),
            outline = n.tone(0.60f, 0.12f), outlineVariant = n.tone(0.30f, 0.15f),
            surfaceBright = n.tone(0.24f, 0.12f), surfaceDim = n.tone(0.06f, 0.12f),
            surfaceContainerLowest = n.tone(0.04f, 0.12f), surfaceContainerLow = n.tone(0.10f, 0.12f),
            surfaceContainer = n.tone(0.12f, 0.12f), surfaceContainerHigh = n.tone(0.17f, 0.12f),
            surfaceContainerHighest = n.tone(0.22f, 0.12f),
        )
    } else {
        lightColorScheme(
            primary = p.tone(0.40f), onPrimary = Color.White,
            primaryContainer = p.tone(0.90f), onPrimaryContainer = p.tone(0.12f),
            secondary = s.tone(0.40f), onSecondary = Color.White,
            secondaryContainer = s.tone(0.90f), onSecondaryContainer = s.tone(0.12f),
            tertiary = t.tone(0.40f), onTertiary = Color.White,
            tertiaryContainer = t.tone(0.90f), onTertiaryContainer = t.tone(0.12f),
            background = n.tone(0.98f, 0.12f), onBackground = n.tone(0.10f, 0.12f),
            surface = n.tone(0.98f, 0.12f), onSurface = n.tone(0.10f, 0.12f),
            surfaceVariant = n.tone(0.90f, 0.15f), onSurfaceVariant = n.tone(0.30f, 0.15f),
            surfaceTint = p.tone(0.40f),
            inverseSurface = n.tone(0.20f, 0.12f), inverseOnSurface = n.tone(0.95f, 0.12f),
            inversePrimary = p.tone(0.78f),
            outline = n.tone(0.50f, 0.12f), outlineVariant = n.tone(0.80f, 0.15f),
            surfaceBright = n.tone(0.98f, 0.12f), surfaceDim = n.tone(0.86f, 0.12f),
            surfaceContainerLowest = Color.White, surfaceContainerLow = n.tone(0.96f, 0.12f),
            surfaceContainer = n.tone(0.94f, 0.12f), surfaceContainerHigh = n.tone(0.92f, 0.12f),
            surfaceContainerHighest = n.tone(0.90f, 0.12f),
        )
    }
}

// ── Hexadecimal ──────────────────────────────────────────────────────────────────────────────

/** `#AARRGGBB` en mayúsculas. */
fun Color.toArgbHex(): String {
    val v = ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or
        ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()
    return "#" + (v.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
}

/** Acepta `#RRGGBB` o `#AARRGGBB` (con o sin `#`); null si no es válido. */
fun parseHexColor(text: String): Color? {
    val h = text.trim().removePrefix("#")
    if (h.length != 6 && h.length != 8) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color((if (h.length == 6) (0xFF000000L or v) else v).toInt())
}

// ── Persistencia: paleta activa y paletas propias ────────────────────────────────────────────

private fun ThemeSeeds.inlineString(): String =
    listOf(primary, secondary, tertiary, neutral).joinToString(",") { it.toArgbHex() }

fun inlineActiveValue(seeds: ThemeSeeds): String = "inline:" + seeds.inlineString()

fun presetActiveValue(index: Int): String = "preset:$index"

fun customActiveValue(id: String): String = "custom:$id"

/** Semillas de la paleta activa; null si no hay ninguna elegida (o ya no existe). */
fun resolveActiveSeeds(active: String, customsJson: String): ThemeSeeds? = when {
    active.startsWith("preset:") -> active.removePrefix("preset:").toIntOrNull()
        ?.takeIf { it in PaletteSeedColors.indices }?.let { presetSeeds(it) }
    active.startsWith("custom:") -> {
        val id = active.removePrefix("custom:")
        parseCustomPalettes(customsJson).firstOrNull { it.id == id }?.seeds
    }
    active.startsWith("inline:") -> {
        val parts = active.removePrefix("inline:").split(',').map { parseHexColor(it) }
        if (parts.size == 4 && parts.all { it != null }) ThemeSeeds(parts[0]!!, parts[1]!!, parts[2]!!, parts[3]!!) else null
    }
    else -> null
}

private fun JsonObject.hex(key: String): Color? = this[key]?.jsonPrimitive?.contentOrNull?.let { parseHexColor(it) }

private fun JsonObject.toSeedsOrNull(): ThemeSeeds? {
    val primary = hex("primary") ?: hex("seed") ?: return null
    val fallback = seedsFromPrimary(primary)
    return ThemeSeeds(
        primary = primary,
        secondary = hex("secondary") ?: fallback.secondary,
        tertiary = hex("tertiary") ?: fallback.tertiary,
        neutral = hex("neutral") ?: fallback.neutral,
    )
}

private fun ThemeSeeds.toJson(name: String, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id)
    put("name", name)
    put("primary", primary.toArgbHex())
    put("secondary", secondary.toArgbHex())
    put("tertiary", tertiary.toArgbHex())
    put("neutral", neutral.toArgbHex())
}

fun parseCustomPalettes(json: String): List<CustomPalette> = runCatching {
    Json.parseToJsonElement(json).jsonArray.mapNotNull { el ->
        val o = el.jsonObject
        val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val seeds = o.toSeedsOrNull() ?: return@mapNotNull null
        CustomPalette(id, o["name"]?.jsonPrimitive?.contentOrNull ?: "Personalizado", seeds)
    }
}.getOrDefault(emptyList())

fun encodeCustomPalettes(list: List<CustomPalette>): String =
    JsonArray(list.map { it.seeds.toJson(it.name, it.id) }).toString()

/** JSON de exportación: {"name","primary","secondary","tertiary","neutral"}. */
fun exportThemeJson(name: String, seeds: ThemeSeeds): String = seeds.toJson(name).toString()

/** Importa {"name","primary",…} o {"name","seed"}; null si no se entiende. */
fun parseThemeJson(text: String): Pair<String, ThemeSeeds>? = runCatching {
    val o = Json.parseToJsonElement(text).jsonObject
    val seeds = o.toSeedsOrNull() ?: return@runCatching null
    (o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Importado") to seeds
}.getOrNull()
