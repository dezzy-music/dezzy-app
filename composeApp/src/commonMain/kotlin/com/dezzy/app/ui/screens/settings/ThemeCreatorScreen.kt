/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.theme.ColorPaletteKey
import com.dezzy.app.ui.theme.CustomPalettesKey
import com.dezzy.app.ui.theme.PaletteSeedColors
import com.dezzy.app.ui.theme.ThemeSeeds
import com.dezzy.app.ui.theme.buildSchemeFromSeeds
import com.dezzy.app.ui.theme.customActiveValue
import com.dezzy.app.ui.theme.exportThemeJson
import com.dezzy.app.ui.theme.inlineActiveValue
import com.dezzy.app.ui.theme.parseCustomPalettes
import com.dezzy.app.ui.theme.parseHexColor
import com.dezzy.app.ui.theme.parseThemeJson
import com.dezzy.app.ui.theme.presetSeeds
import com.dezzy.app.ui.theme.resolveActiveSeeds
import com.dezzy.app.ui.theme.toArgbHex
import kotlinx.coroutines.launch

private val CardColor = Color(0xFF1F1F1F)
private val ButtonDark = Color(0xFF2B2B2B)
private val SeedLabels = listOf("Primario", "Secundario", "Terciario", "Neutro")

/** Índice del item «Colores Semilla» en la lista (para el botón de la paleta). */
private const val SeedsItemIndex = 3

private fun ThemeSeeds.get(i: Int): Color = when (i) {
    0 -> primary
    1 -> secondary
    2 -> tertiary
    else -> neutral
}

private fun ThemeSeeds.with(i: Int, c: Color): ThemeSeeds = when (i) {
    0 -> copy(primary = c)
    1 -> copy(secondary = c)
    2 -> copy(tertiary = c)
    else -> copy(neutral = c)
}

@Composable
internal fun ThemeCreatorScreen(ctx: ScreenContext) {
    var active by rememberPreference(ColorPaletteKey, "")
    var customsJson by rememberPreference(CustomPalettesKey, "[]")
    val activeSeeds = remember(active, customsJson) { resolveActiveSeeds(active, customsJson) } ?: presetSeeds(0)

    var seeds by remember { mutableStateOf(activeSeeds) }
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    var previewDark by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val scheme = remember(seeds, previewDark) { buildSchemeFromSeeds(seeds, previewDark) }

    val pickJson = rememberThemeJsonPicker { text ->
        parseThemeJson(text)?.let { (n, s) ->
            seeds = s
            name = n
        }
    }
    val saveJson = rememberThemeJsonSaver()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ctx.navigator.back() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Creador de Temas",
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                TextButton(onClick = {
                    seeds = activeSeeds
                    name = ""
                }) { Text("Reiniciar") }
                TextButton(onClick = {
                    val list = parseCustomPalettes(customsJson)
                    val (json, id) = appendCustomPalette(customsJson, name.ifBlank { "Personalizado ${list.size + 1}" }, seeds)
                    customsJson = json
                    active = customActiveValue(id)
                }) { Text("Guardar") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Vista previa del tema (cabecera)
                item(key = "preview_head") {
                    ThemeCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("Vista previa del tema", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Ajusta los colores, previsualiza el resultado y aplícalo.",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                            Box(
                                Modifier
                                    .padding(start = 8.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { scope.launch { listState.animateScrollToItem(SeedsItemIndex) } },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Palette, "Colores semilla", tint = Color.Black) }
                        }
                        MaterialTheme(colorScheme = scheme) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
                                        Column(Modifier.weight(1f)) {
                                            Text("Reproduciendo ahora", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Tarjeta de vista previa de tu tema", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (i in 0 until 4) {
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (i < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {}) {
                                            Icon(Icons.Rounded.Star, null, Modifier.size(18.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text("Acción Principal", maxLines = 1, fontSize = 12.sp)
                                        }
                                        FilledTonalButton(onClick = {}) {
                                            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text("Acción Secundaria", maxLines = 1, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Components & surfaces
                item(key = "components") {
                    ThemeCard {
                        Text("Vista previa del tema", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Components & surfaces", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                        SingleChoiceSegmentedButtonRow(Modifier.padding(top = 10.dp).fillMaxWidth()) {
                            SegmentedButton(
                                selected = !previewDark,
                                onClick = { previewDark = false },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("Light") }
                            SegmentedButton(
                                selected = previewDark,
                                onClick = { previewDark = true },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("Dark") }
                        }
                        MaterialTheme(colorScheme = scheme) { ComponentsPreview() }
                    }
                }

                // 3. Detalles del Tema
                item(key = "details") {
                    ThemeCard {
                        Text("Detalles del Tema", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nombre (opcional)") },
                            singleLine = true,
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                        )
                        Button(
                            onClick = { active = inlineActiveValue(seeds) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        ) { Text("Aplicar Tema") }
                    }
                }

                // 4. Colores Semilla
                item(key = "seeds") {
                    ThemeCard {
                        Text("Colores Semilla", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0 until 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (col in 0 until 2) {
                                        val i = row * 2 + col
                                        val on = selected == i
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(if (on) Color.White else ButtonDark)
                                                .clickable { selected = i },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(SeedLabels[i], color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Color elegido
                item(key = "color") {
                    val color = seeds.get(selected)
                    ThemeCard {
                        Text("Color ${SeedLabels[selected]}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(color))
                            Column(Modifier.weight(1f)) {
                                Text("Hexadecimal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(color.toArgbHex(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(color.toArgbHex())) }) {
                                Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Copiar")
                            }
                        }
                        var hexText by remember(selected, color) { mutableStateOf(color.toArgbHex()) }
                        OutlinedTextField(
                            value = hexText,
                            onValueChange = {
                                hexText = it
                                parseHexColor(it)?.let { c -> seeds = seeds.with(selected, c) }
                            },
                            label = { Text("Valor hex. (#RRGGBB o #AARRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        )
                        ChannelRow("R", color.red, Color(0xFFFF5252)) { seeds = seeds.with(selected, color.copy(red = it)) }
                        ChannelRow("G", color.green, Color(0xFF4CAF50)) { seeds = seeds.with(selected, color.copy(green = it)) }
                        ChannelRow("B", color.blue, Color(0xFF448AFF)) { seeds = seeds.with(selected, color.copy(blue = it)) }
                        Text("Predefinidos", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(PaletteSeedColors) { _, c ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .clickable { seeds = seeds.with(selected, c) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ExtendedFloatingActionButton(
                onClick = { pickJson() },
                containerColor = ButtonDark,
                contentColor = Color.White,
                icon = { Icon(Icons.Rounded.CloudDownload, null) },
                text = { Text("Importar Tema") },
            )
            ExtendedFloatingActionButton(
                onClick = { saveJson("tema.json", exportThemeJson(name.ifBlank { "Personalizado" }, seeds)) },
                containerColor = Color.White,
                contentColor = Color.Black,
                icon = { Icon(Icons.Rounded.Share, null) },
                text = { Text("Exportar Tema") },
            )
        }
    }
}

@Composable
private fun ThemeCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardColor)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun ChannelRow(letter: String, value: Float, channel: Color, onChange: (Float) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(channel.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text(letter, color = channel, fontWeight = FontWeight.Bold) }
        Slider(
            value = value * 255f,
            onValueChange = { onChange((it / 255f).coerceIn(0f, 1f)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = channel, activeTrackColor = channel, inactiveTrackColor = channel.copy(alpha = 0.25f)),
            modifier = Modifier.weight(1f),
        )
        Text((value * 255f + 0.5f).toInt().toString(), fontSize = 14.sp, modifier = Modifier.size(width = 34.dp, height = 20.dp))
    }
}

/** «Components & surfaces», pintado con el esquema del tema que rodea esta función. */
@Composable
private fun ComponentsPreview() {
    var sw by remember { mutableStateOf(true) }
    var cb by remember { mutableStateOf(true) }
    var radio by remember { mutableStateOf(true) }
    var slider by remember { mutableStateOf(0.4f) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Premium look", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Badge { Text("3") }
                Icon(Icons.Rounded.MoreVert, null, Modifier.padding(start = 8.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {}) { Text("Outlined") }
                    TextButton(onClick = {}) { Text("Text") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("Assist") })
                    FilterChip(selected = true, onClick = {}, label = { Text("Selected") })
                    AssistChip(
                        onClick = {},
                        label = { Text("Tertiary") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                }
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("Outline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = sw, onCheckedChange = { sw = it })
                    Checkbox(checked = cb, onCheckedChange = { cb = it })
                    RadioButton(selected = radio, onClick = { radio = !radio })
                }
                Slider(value = slider, onValueChange = { slider = it })
                Text("Progress", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Loading", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                }
                Text("List", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ListItem(
                headlineContent = { Text("Playlist") },
                supportingContent = { Text("Curated picks • 24 tracks") },
            )
            ListItem(
                headlineContent = { Text("Liked songs") },
                supportingContent = { Text("Smart collection") },
                leadingContent = { Icon(Icons.Rounded.Favorite, null) },
            )
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Rounded.Explore, null) }, label = { Text("Explore") })
                NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Rounded.LibraryMusic, null) }, label = { Text("Library") })
            }
        }
    }
}
