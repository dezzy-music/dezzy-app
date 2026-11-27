/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.prefs.AodButtonStyle
import com.dezzy.app.ui.screens.settings.prefs.AodButtonStyleKey
import com.dezzy.app.ui.screens.settings.prefs.AodCoverFractionKey
import com.dezzy.app.ui.screens.settings.prefs.AodCoverShape
import com.dezzy.app.ui.screens.settings.prefs.AodCoverShapeKey
import com.dezzy.app.ui.screens.settings.prefs.AodDimKey
import com.dezzy.app.ui.screens.settings.prefs.AodImmersiveKey
import com.dezzy.app.ui.screens.settings.prefs.AodInactivityMinutesKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowArtistKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowClockKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowControlsKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowProgressKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowTimeKey
import com.dezzy.app.ui.screens.settings.prefs.AodShowTitleKey
import com.dezzy.app.ui.screens.settings.prefs.AodStyle
import com.dezzy.app.ui.screens.settings.prefs.AodStyleKey
import com.dezzy.app.ui.screens.settings.prefs.AodTextScaleKey
import com.dezzy.app.ui.screens.settings.prefs.AodTransitionMsKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun AodSettingsScreen(ctx: ScreenContext) {
    var immersive by rememberPreference(AodImmersiveKey, false)
    var inactivity by rememberPreference(AodInactivityMinutesKey, 0)
    var style by rememberEnumPreference(AodStyleKey, AodStyle.CLASSIC)
    var shape by rememberEnumPreference(AodCoverShapeKey, AodCoverShape.ROUNDED)
    var buttons by rememberEnumPreference(AodButtonStyleKey, AodButtonStyle.ROUNDED)
    var coverFraction by rememberPreference(AodCoverFractionKey, 0.65f)
    var textScale by rememberPreference(AodTextScaleKey, 1f)
    var dim by rememberPreference(AodDimKey, 0.55f)
    var transitionMs by rememberPreference(AodTransitionMsKey, 700)
    var showClock by rememberPreference(AodShowClockKey, true)
    var showTitle by rememberPreference(AodShowTitleKey, true)
    var showArtist by rememberPreference(AodShowArtistKey, true)
    var showTime by rememberPreference(AodShowTimeKey, true)
    var showProgress by rememberPreference(AodShowProgressKey, true)
    var showControls by rememberPreference(AodShowControlsKey, true)

    SettingsPage(title = "Pantalla siempre activa", onBack = { ctx.navigator.back() }) {
        item(key = "aod:full") {
            SettingsGroup("Full Screen") {
                SettingsSwitchRow(
                    title = "Immersive Mode",
                    subtitle = "Hides the system status bar, navigation, and clock",
                    checked = immersive,
                    onCheckedChange = { immersive = it },
                )
            }
        }
        item(key = "aod:auto") {
            SettingsGroup("Automatic Activation") {
                SettingsSliderRow(
                    title = "Inactivity Time",
                    icon = null,
                    value = inactivity.toFloat(),
                    valueRange = 0f..30f,
                    steps = 29,
                    valueLabel = {
                        val m = it.roundToInt()
                        (if (m == 0) "Never" else if (m == 1) "1 minute" else "$m minutes") +
                            " · Automatically activate AOD after the selected inactivity time"
                    },
                    onValueChangeFinished = { inactivity = it.roundToInt() },
                )
            }
        }
        item(key = "aod:style") {
            SettingsGroup("Estilo AOD") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AodStyle.entries.forEach { s ->
                        val sel = s == style
                        Column(
                            Modifier
                                .width(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { style = s }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(s.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item(key = "aod:shape") {
            SettingsGroup("Forma de la portada") {
                // Rejilla de 4 columnas × 2 filas.
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AodCoverShape.entries.chunked(4).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { s ->
                                val sel = s == shape
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                            else Modifier
                                        )
                                        .clickable { shape = s }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(aodShapeOf(s))
                                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                                    )
                                    Text(s.label, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "aod:buttons") {
            SettingsGroup("Control buttons") {
                SettingsChoiceRow(
                    title = "Button style",
                    options = AodButtonStyle.entries.toList(),
                    selected = buttons,
                    label = { it.label },
                    onSelect = { buttons = it },
                )
            }
        }
        item(key = "aod:typo") {
            SettingsGroup("Typography") {
                SettingsSliderRow(
                    title = "Tamaño relativo",
                    icon = null,
                    value = coverFraction,
                    valueRange = 0.40f..1.00f,
                    steps = 11,
                    valueLabel = { "${(it * 100).roundToInt()}% · Fracción del ancho de pantalla que ocupa la portada" },
                    onValueChangeFinished = { coverFraction = it },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Text size",
                    icon = null,
                    value = textScale,
                    valueRange = 0.75f..1.40f,
                    steps = 12,
                    valueLabel = { "${(it * 100).roundToInt()}% · Global scale for title, artist and timers" },
                    onValueChangeFinished = { textScale = it },
                )
            }
        }
        item(key = "aod:dim") {
            SettingsGroup("Oscuridad del fondo") {
                SettingsSliderRow(
                    title = "Nivel de oscuridad",
                    icon = null,
                    value = dim,
                    valueRange = 0f..1f,
                    steps = 19,
                    valueLabel = { "${(it * 100).roundToInt()}% · Opacidad del overlay en los estilos Ambiente y Spotlight" },
                    onValueChangeFinished = { dim = it },
                )
            }
        }
        item(key = "aod:transitions") {
            SettingsGroup("Transitions") {
                SettingsSliderRow(
                    title = "Transition duration",
                    icon = null,
                    value = transitionMs.toFloat(),
                    valueRange = 200f..1500f,
                    steps = 12,
                    valueLabel = {
                        val ms = it.roundToInt()
                        val speed = when { ms < 500 -> "Fast"; ms < 1000 -> "Medium"; else -> "Slow" }
                        "${ms}ms · Crossfade speed between tracks ($speed)"
                    },
                    onValueChangeFinished = { transitionMs = it.roundToInt() },
                )
            }
        }
        item(key = "aod:clock") {
            SettingsGroup("System clock") {
                SettingsSwitchRow(
                    title = "Show clock",
                    subtitle = "Shows the system time on AOD",
                    checked = showClock,
                    onCheckedChange = { showClock = it },
                )
            }
        }
        item(key = "aod:visible") {
            SettingsGroup("Elementos visibles") {
                SettingsSwitchRow("Mostrar título", checked = showTitle, onCheckedChange = { showTitle = it })
                SettingsDivider()
                SettingsSwitchRow("Mostrar artista", checked = showArtist, onCheckedChange = { showArtist = it })
                SettingsDivider()
                SettingsSwitchRow(
                    "Mostrar tiempo", "Etiquetas de posición y duración",
                    checked = showTime, onCheckedChange = { showTime = it },
                )
                SettingsDivider()
                SettingsSwitchRow("Mostrar barra de progreso", checked = showProgress, onCheckedChange = { showProgress = it })
                SettingsDivider()
                SettingsSwitchRow(
                    "Mostrar controles", "Anterior, Play/Pausa, Siguiente",
                    checked = showControls, onCheckedChange = { showControls = it },
                )
            }
        }
    }
}

// ── Formas de la portada ─────────────────────────────────────────────────────────────────────

/** Forma recortable de cada opción de portada (la misma que se dibuja en la rejilla). */
internal fun aodShapeOf(s: AodCoverShape): Shape = when (s) {
    AodCoverShape.ROUNDED -> RoundedCornerShape(22)
    AodCoverShape.CIRCLE -> CircleShape
    AodCoverShape.SQUIRCLE -> RoundedCornerShape(38)
    AodCoverShape.ARCH -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8, bottomStartPercent = 8)
    AodCoverShape.DIAMOND -> polarShape(4, 0.0) { 1.0 }
    AodCoverShape.HEXAGON -> polarShape(6, 0.0) { 1.0 }
    AodCoverShape.STAR -> polarShape(10, -PI / 2) { i -> if (i % 2 == 0) 1.0 else 0.45 }
    AodCoverShape.FLOWER -> flowerShape()
}

/** Polígono de [sides] vértices; [radius] da el radio relativo (0–1) de cada vértice. */
private fun polarShape(sides: Int, startAngle: Double, radius: (Int) -> Double): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) / 2f
        val path = Path()
        for (i in 0 until sides) {
            val a = startAngle + 2 * PI * i / sides
            val rr = r * radius(i).toFloat()
            val p = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Flor de 8 pétalos: radio que oscila con el ángulo. */
private fun flowerShape(): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) / 2f
        val path = Path()
        val steps = 160
        for (i in 0..steps) {
            val a = 2 * PI * i / steps
            val rr = r * (0.82f + 0.18f * abs(cos(4 * a)).toFloat())
            val p = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
