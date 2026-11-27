/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dezzy.app.feature.canvas.CANVAS_ARTIST_DEFAULT
import com.dezzy.app.feature.canvas.CANVAS_SONG_DEFAULT
import com.dezzy.app.feature.canvas.CanvasArtistEnabledKey
import com.dezzy.app.feature.canvas.CanvasSongEnabledKey
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.dezzy.app.feature.personalize.wallpaperFiles
import com.dezzy.app.ui.player.kit.MiniPlayerBackground
import com.dezzy.app.ui.player.kit.MiniPlayerBackgroundKey
import com.dezzy.app.ui.player.kit.MiniPlayerImageUriKey
import com.dezzy.app.ui.player.kit.PlayerCustomBrightnessKey
import com.dezzy.app.ui.player.kit.PlayerCustomImageUriKey
import com.dezzy.app.ui.screens.personalize.MediaKind
import com.dezzy.app.ui.screens.personalize.extensionForMime
import com.dezzy.app.ui.screens.personalize.rememberMediaPicker
import com.dezzy.app.ui.theme.AppFont
import com.dezzy.app.ui.theme.AppFontKey
import com.dezzy.app.ui.player.kit.BlurRadiusKey
import com.dezzy.app.ui.player.kit.DisableBlurKey
import com.dezzy.app.ui.player.kit.PlayerBackgroundStyle
import com.dezzy.app.ui.player.kit.PlayerBackgroundStyleKey
import com.dezzy.app.ui.player.kit.PlayerDesignStyle
import com.dezzy.app.ui.player.kit.PlayerDesignStyleKey
import com.dezzy.app.ui.player.kit.ShowCodecOnPlayerKey
import com.dezzy.app.ui.player.kit.SliderStyle
import com.dezzy.app.ui.player.kit.SliderStyleKey
import com.dezzy.app.ui.player.kit.StyledPlaybackSlider
import com.dezzy.app.ui.player.kit.VisualizerStyleKey
import com.dezzy.app.ui.player.kit.VisualizerSwipeKey
import com.dezzy.app.ui.player.kit.label
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.player.styles.PlayerStyleCoverflowSelector
import com.dezzy.app.ui.player.styles.VisualizerCarousel
import com.dezzy.app.ui.player.styles.VisualizerStyle
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.theme.AppInterfaceTheme
import com.dezzy.app.ui.theme.LiquidGlassIntensity
import kotlinx.coroutines.launch

/** Estilos del reproductor que dibujan su propio fondo fijo: no admiten «Fondo del reproductor». */
private val StandaloneBackgroundStyles = setOf(
    PlayerDesignStyle.V7,
    PlayerDesignStyle.IPOD,
    PlayerDesignStyle.SPOTIFY,
    PlayerDesignStyle.CAR,
    PlayerDesignStyle.APPLE_MUSIC,
    PlayerDesignStyle.LIVE_MESH,
    PlayerDesignStyle.AURORA,
    PlayerDesignStyle.V1,
)

@Composable
internal fun AppearanceSettingsScreen(ctx: ScreenContext) {
    val prefs = ctx.prefs
    val scope = ctx.scope

    val themeMode by prefs.themeMode.collectAsState("dark")
    val pureBlack by prefs.pureBlack.collectAsState(false)
    val dynamicColor by prefs.dynamicColor.collectAsState(false)
    val interfaceThemeName by prefs.interfaceTheme.collectAsState("NORMAL")
    val glassIntensityName by prefs.glassIntensity.collectAsState("PURE")
    val interfaceTheme = AppInterfaceTheme.entries.firstOrNull { it.name == interfaceThemeName } ?: AppInterfaceTheme.NORMAL
    val glassIntensity = LiquidGlassIntensity.entries.firstOrNull { it.name == glassIntensityName } ?: LiquidGlassIntensity.PURE

    var playerStyle by rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V1)
    var playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    var sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    var disableBlur by rememberPreference(DisableBlurKey, false)
    var blurRadius by rememberPreference(BlurRadiusKey, 36f)
    var showCodec by rememberPreference(ShowCodecOnPlayerKey, true)
    var visualizerName by rememberPreference(VisualizerStyleKey, VisualizerStyle.ESFERA.name)
    var visualizerSwipe by rememberPreference(VisualizerSwipeKey, true)
    var canvasSong by rememberPreference(CanvasSongEnabledKey, CANVAS_SONG_DEFAULT)
    var canvasArtist by rememberPreference(CanvasArtistEnabledKey, CANVAS_ARTIST_DEFAULT)

    var appFont by rememberEnumPreference(AppFontKey, AppFont.DEFAULT)
    var customImageUri by rememberPreference(PlayerCustomImageUriKey, "")
    var customBrightness by rememberPreference(PlayerCustomBrightnessKey, 1f)
    var miniBackground by rememberEnumPreference(MiniPlayerBackgroundKey, MiniPlayerBackground.DEFAULT)
    var miniImageUri by rememberPreference(MiniPlayerImageUriKey, "")
    val pickPlayerImage = rememberOwnImagePicker(customImageUri) { customImageUri = it }
    val pickMiniImage = rememberOwnImagePicker(miniImageUri) { miniImageUri = it }

    var showStyleSelector by remember { mutableStateOf(false) }

    SettingsPage(title = "Apariencia", onBack = { ctx.navigator.back() }) {
        item(key = "theme_card") {
            AppThemeSelectorCard(
                selectedTheme = interfaceTheme,
                onSelectTheme = { scope.launch { prefs.setInterfaceTheme(it.name) } },
                intensity = glassIntensity,
                onSelectIntensity = { scope.launch { prefs.setGlassIntensity(it.name) } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        item(key = "colors") {
            SettingsGroup(label = "Colores") {
                SettingsChoiceRow(
                    title = "Tema",
                    icon = Icons.Rounded.DarkMode,
                    options = listOf("system", "light", "dark"),
                    selected = themeMode,
                    label = {
                        when (it) {
                            "system" -> "Tema del sistema"
                            "light" -> "Tema claro"
                            else -> "Tema oscuro"
                        }
                    },
                    onSelect = { scope.launch { prefs.setThemeMode(it) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Fondo negro puro",
                    subtitle = "Ahorra batería en pantallas OLED",
                    icon = Icons.Rounded.Contrast,
                    checked = pureBlack,
                    onCheckedChange = { scope.launch { prefs.setPureBlack(it) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Color dinámico",
                    subtitle = "Usa los colores de tu fondo de pantalla",
                    icon = Icons.Rounded.Palette,
                    checked = dynamicColor,
                    onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Paleta de color",
                    subtitle = "Elige o crea los colores de la app",
                    icon = Icons.Rounded.Palette,
                    onClick = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.COLOR_PALETTE)) },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Icono de la app",
                    subtitle = "Forma y colores del icono, en la app y en el launcher",
                    icon = Icons.Rounded.Apps,
                    onClick = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.APP_ICON)) },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Fuente de la app",
                    icon = Icons.Rounded.TextFields,
                    options = AppFont.entries.toList(),
                    selected = appFont,
                    label = { it.label },
                    onSelect = { appFont = it },
                )
                SettingsDivider()
                var navStyle by com.dezzy.app.ui.components.rememberNavBarStyle()
                SettingsChoiceRow(
                    title = "Barra de navegación",
                    icon = Icons.Rounded.Apps,
                    options = com.dezzy.app.ui.components.NavBarStyle.entries.toList(),
                    selected = navStyle,
                    label = { if (it == com.dezzy.app.ui.components.NavBarStyle.FULL) "Completa" else "Dock" },
                    onSelect = { navStyle = it },
                )
            }
        }

        item(key = "player") {
            SettingsGroup(label = "Reproductor") {
                SettingsRow(
                    title = "Estilo del reproductor",
                    subtitle = playerStyle.label(),
                    icon = Icons.Rounded.PlayCircle,
                    onClick = { showStyleSelector = true },
                )
                if (playerStyle !in StandaloneBackgroundStyles) {
                    SettingsDivider()
                    SettingsChoiceRow(
                        title = "Fondo del reproductor",
                        icon = Icons.Rounded.Gradient,
                        options = PlayerBackgroundStyle.entries.toList(),
                        selected = playerBackground,
                        label = {
                            when (it) {
                                PlayerBackgroundStyle.DEFAULT -> "Seguir el tema"
                                PlayerBackgroundStyle.GRADIENT -> "Degradado"
                                PlayerBackgroundStyle.GLOW -> "Resplandor"
                                PlayerBackgroundStyle.COLORING -> "Coloreado"
                                PlayerBackgroundStyle.CUSTOM -> "Personalizado"
                            }
                        },
                        onSelect = { playerBackground = it },
                    )
                    if (playerBackground == PlayerBackgroundStyle.CUSTOM) {
                        SettingsDivider()
                        SettingsRow(
                            title = "Fondo personalizado",
                            subtitle = if (customImageUri.isBlank()) "Elegir una imagen de la galería" else "Cambiar la imagen",
                            icon = Icons.Rounded.Image,
                            onClick = { pickPlayerImage() },
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            title = "Atenuación de la imagen",
                            icon = Icons.Rounded.Contrast,
                            value = customBrightness,
                            valueRange = 0.2f..1f,
                            steps = 7,
                            valueLabel = { "${((1f - it) * 100).toInt()} %" },
                            onValueChangeFinished = { customBrightness = it },
                        )
                    }
                }
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Fondo del minirreproductor",
                    icon = Icons.Rounded.Gradient,
                    options = MiniPlayerBackground.entries.toList(),
                    selected = miniBackground,
                    label = {
                        when (it) {
                            MiniPlayerBackground.DEFAULT -> "Predeterminado"
                            MiniPlayerBackground.COVER_COLOR -> "Color de la carátula"
                            MiniPlayerBackground.TRANSPARENT -> "Transparente (cristal)"
                            MiniPlayerBackground.IMAGE -> "Imagen propia"
                        }
                    },
                    onSelect = { miniBackground = it },
                )
                if (miniBackground == MiniPlayerBackground.IMAGE) {
                    SettingsDivider()
                    SettingsRow(
                        title = "Imagen del minirreproductor",
                        subtitle = if (miniImageUri.isBlank()) "Elegir una imagen de la galería" else "Cambiar la imagen",
                        icon = Icons.Rounded.Image,
                        onClick = { pickMiniImage() },
                    )
                }
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Desactivar el desenfoque",
                    subtitle = "Quita los desenfoques de fondo para ir más fluido",
                    icon = Icons.Rounded.BlurOn,
                    checked = disableBlur,
                    onCheckedChange = { disableBlur = it },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Radio del desenfoque",
                    icon = Icons.Rounded.BlurOn,
                    value = blurRadius,
                    valueRange = 0f..100f,
                    steps = 19,
                    valueLabel = { it.toInt().toString() },
                    onValueChangeFinished = { blurRadius = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Mostrar el códec en el reproductor",
                    subtitle = "Formato y calidad de la canción que suena",
                    icon = Icons.Rounded.GraphicEq,
                    checked = showCodec,
                    onCheckedChange = { showCodec = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Canvas de la canción",
                    subtitle = "Un trozo del videoclip, sin sonido, sobre la carátula",
                    icon = Icons.Rounded.Videocam,
                    checked = canvasSong,
                    onCheckedChange = { canvasSong = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Canvas del artista",
                    subtitle = "Cabecera en movimiento en la página del artista",
                    icon = Icons.Rounded.Videocam,
                    checked = canvasArtist,
                    onCheckedChange = { canvasArtist = it },
                )
            }
        }

        item(key = "slider") {
            SettingsGroup(label = "Estilo de la barra de progreso") {
                SettingsRow(
                    title = "Estilo de la barra de progreso",
                    subtitle = sliderStyleLabel(sliderStyle),
                    icon = Icons.Rounded.LinearScale,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                ) {
                    SliderStyle.entries.forEach { style ->
                        SliderStyleCard(
                            sliderStyle = style,
                            selected = style == sliderStyle,
                            onClick = { sliderStyle = style },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item(key = "visualizer") {
            SettingsGroup(label = "Visualizador") {
                VisualizerCarousel(
                    selected = VisualizerStyle.entries.firstOrNull { it.name == visualizerName } ?: VisualizerStyle.ESFERA,
                    onSelect = { visualizerName = it.name },
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Deslizar para cambiar",
                    subtitle = "En la pantalla del visualizador",
                    icon = Icons.Rounded.Swipe,
                    checked = visualizerSwipe,
                    onCheckedChange = { visualizerSwipe = it },
                )
            }
        }

        item(key = "wallpapers") {
            SettingsGroup(label = "Fondos de pantalla") {
                SettingsRow(
                    title = "Fondo de imagen o vídeo",
                    subtitle = "Vertical y horizontal, también desde Pinterest",
                    icon = Icons.Rounded.Image,
                    onClick = { ctx.navigator.push(com.dezzy.app.ui.navigation.Screen.Settings(com.dezzy.app.ui.navigation.SettingsPage.WALLPAPER)) },
                )
            }
        }
    }

    if (showStyleSelector) {
        Dialog(
            onDismissRequest = { showStyleSelector = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showStyleSelector = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver")
                        }
                        Text(
                            "Estilo del reproductor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    PlayerStyleCoverflowSelector(
                        selectedStyle = playerStyle,
                        onApplyStyle = {
                            playerStyle = it
                            showStyleSelector = false
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Selector de imagen de la galería: copia la elegida a la carpeta privada de la app, borra la copia
 * anterior (si era propia) y avisa con la nueva URI `file://…`.
 */
@Composable
internal fun rememberOwnImagePicker(current: String, onImported: (String) -> Unit): () -> Unit {
    val files = remember { wallpaperFiles() }
    val scope = rememberCoroutineScope()
    val currentState = rememberUpdatedState(current)
    val onImportedState = rememberUpdatedState(onImported)
    val pick = rememberMediaPicker { uri, mime ->
        scope.launch {
            val imported = files.importPicked(uri, extensionForMime(mime, isVideo = false))
            if (imported != null) {
                val old = currentState.value
                if (old.isNotBlank() && files.isOwnFile(old)) files.deleteOwnFile(old)
                onImportedState.value(imported)
            }
        }
    }
    return { pick(MediaKind.IMAGE) }
}

private fun sliderStyleLabel(style: SliderStyle): String = when (style) {
    SliderStyle.Standard -> "Estándar"
    SliderStyle.Wavy -> "Ondulado"
    SliderStyle.Slim -> "Fino"
}

/** Tarjeta de un estilo de barra de progreso con vista previa animada. */
@Composable
private fun SliderStyleCard(
    sliderStyle: SliderStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vista previa que se mueve sola para que cada estilo se vea «vivo».
    val infiniteTransition = rememberInfiniteTransition(label = "sliderPreview")
    val animatedValue by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sliderPreviewValue",
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = tween(220),
        label = "cardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        animationSpec = tween(220),
        label = "cardBorder",
    )
    val borderWidth = if (selected) 2.dp else 1.dp

    val containerBrush = if (selected) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    }

    Box(
        modifier = modifier
            .height(96.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(containerBrush)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            StyledPlaybackSlider(
                sliderStyle = sliderStyle,
                value = animatedValue,
                valueRange = 0f..1f,
                onValueChange = {},
                onValueChangeFinished = {},
                activeColor = MaterialTheme.colorScheme.primary,
                isPlaying = true,
                songKey = "slider-preview",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Text(
                text = sliderStyleLabel(sliderStyle),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Capa transparente: un toque en cualquier punto elige la tarjeta, aunque la vista previa
        // (no interactiva) se lo tragaría.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
