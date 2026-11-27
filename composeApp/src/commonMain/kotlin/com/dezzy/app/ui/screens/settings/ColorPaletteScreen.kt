/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.theme.AppInterfaceTheme
import com.dezzy.app.ui.theme.ColorPaletteKey
import com.dezzy.app.ui.theme.CustomPalette
import com.dezzy.app.ui.theme.CustomPalettesKey
import com.dezzy.app.ui.theme.PaletteSeedColors
import com.dezzy.app.ui.theme.ThemeSeeds
import com.dezzy.app.ui.theme.customActiveValue
import com.dezzy.app.ui.theme.encodeCustomPalettes
import com.dezzy.app.ui.theme.mixOver
import com.dezzy.app.ui.theme.parseCustomPalettes
import com.dezzy.app.ui.theme.parseThemeJson
import com.dezzy.app.ui.theme.presetActiveValue
import com.dezzy.app.ui.theme.presetSeeds
import com.dezzy.app.ui.theme.resolveActiveSeeds
import kotlinx.coroutines.launch

private val SwatchBase = Color(0xFF2B2B2B)
private const val PageSize = 4

/** Añade una paleta propia a la lista guardada: devuelve el JSON nuevo y el id asignado. */
internal fun appendCustomPalette(customsJson: String, name: String, seeds: ThemeSeeds): Pair<String, String> {
    val list = parseCustomPalettes(customsJson)
    val id = "c" + kotlin.time.Clock.System.now().toEpochMilliseconds()
    val finalName = name.trim().ifBlank { "Personalizado ${list.size + 1}" }
    return encodeCustomPalettes(list + CustomPalette(id, finalName, seeds)) to id
}

@Composable
internal fun ColorPaletteScreen(ctx: ScreenContext) {
    val prefs = ctx.prefs
    val scope = ctx.scope
    var active by rememberPreference(ColorPaletteKey, "")
    var customsJson by rememberPreference(CustomPalettesKey, "[]")
    val customs = remember(customsJson) { parseCustomPalettes(customsJson) }
    val seeds = remember(active, customsJson) { resolveActiveSeeds(active, customsJson) } ?: presetSeeds(0)

    val interfaceThemeName by prefs.interfaceTheme.collectAsState("NORMAL")
    val interfaceTheme = AppInterfaceTheme.entries.firstOrNull { it.name == interfaceThemeName } ?: AppInterfaceTheme.NORMAL

    val pickJson = rememberThemeJsonPicker { text ->
        parseThemeJson(text)?.let { (name, imported) ->
            val (json, id) = appendCustomPalette(customsJson, name, imported)
            customsJson = json
            active = customActiveValue(id)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ctx.navigator.back() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Paleta de color", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }

            PalettePreview(seeds, isCustom = active.startsWith("custom:") || active.startsWith("inline:"))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    AppInterfaceTheme.NORMAL to "Normal",
                    AppInterfaceTheme.LIQUID_GLASS to "Liquid Glass",
                    AppInterfaceTheme.NOTHING to "Nothing",
                ).forEach { (theme, label) ->
                    FilterChip(
                        selected = interfaceTheme == theme,
                        onClick = { scope.launch { prefs.setInterfaceTheme(theme.name) } },
                        label = { Text(label) },
                    )
                }
            }

            // Muestras: 65 semillas y después las paletas propias.
            val entries: List<Pair<String, Color>> =
                PaletteSeedColors.mapIndexed { i, c -> presetActiveValue(i) to c } +
                    customs.map { customActiveValue(it.id) to it.seeds.primary }
            val pages = (entries.size + PageSize - 1) / PageSize
            val pager = rememberPagerState(pageCount = { pages })
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (k in 0 until PageSize) {
                        val entry = entries.getOrNull(page * PageSize + k)
                        if (entry == null) {
                            Spacer(Modifier.size(76.dp))
                        } else {
                            Swatch(seed = entry.second, selected = active == entry.first, onClick = { active = entry.first })
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 190.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Un punto por página; a partir de 12 se agrupan para que quepan.
                for (i in 0 until pages) {
                    val on = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(if (on) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (on) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.THEME_CREATOR)) },
                containerColor = Color.White,
                contentColor = Color.Black,
                icon = { Icon(Icons.Rounded.Palette, null) },
                text = { Text("Personalizar Tema") },
            )
            ExtendedFloatingActionButton(
                onClick = { pickJson() },
                containerColor = SwatchBase,
                contentColor = Color.White,
                icon = { Icon(Icons.Rounded.CloudDownload, null) },
                text = { Text("Importar Tema") },
            )
        }
    }
}

/** Cuadrado de 76 dp con el círculo de tres tonos: mitad derecha = semilla, izquierda = mezclas 1/3 y 1/2. */
@Composable
private fun Swatch(seed: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(76.dp)
            .clip(shape)
            .background(SwatchBase)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, Color.White), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(56.dp)) {
            val box = Size(size.width, size.height)
            drawArc(seed, startAngle = -90f, sweepAngle = 180f, useCenter = true, topLeft = Offset.Zero, size = box)
            drawArc(mixOver(SwatchBase, seed, 1f / 3f), startAngle = 180f, sweepAngle = 90f, useCenter = true, topLeft = Offset.Zero, size = box)
            drawArc(mixOver(SwatchBase, seed, 0.5f), startAngle = 90f, sweepAngle = 90f, useCenter = true, topLeft = Offset.Zero, size = box)
        }
    }
}

/** Tarjeta de vista previa de la paleta elegida. */
@Composable
private fun PalettePreview(seeds: ThemeSeeds, isCustom: Boolean) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(230.dp)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    listOf(mixOver(Color(0xFF3A3A3A), seeds.primary, 0.35f), Color(0xFF1A1A1A)),
                ),
            ),
    ) {
        // Arriba-izquierda: tarjeta translúcida con carátula y progreso al 60 %.
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .width(170.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape).background(seeds.primary))
            }
        }
        // Arriba-derecha: botón play blanco.
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black)
        }
        // Centro: tres círculos blancos superpuestos, de mayor a menor.
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(64.dp, 48.dp, 32.dp).forEach { d ->
                Box(Modifier.size(d).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White))
            }
        }
        // Abajo: tres chips con contorno y un punto; sobre el tercero, la etiqueta «Personalizado».
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(seeds.primary, seeds.secondary, seeds.tertiary).forEachIndexed { i, dot ->
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 72.dp, height = 36.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    }
                    if (i == 2 && isCustom) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-30).dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Personalizado", color = Color.Black, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
