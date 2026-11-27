/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import com.dezzy.app.ui.player.kit.label
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.Preferences
import com.dezzy.app.feature.canvas.CANVAS_ARTIST_DEFAULT
import com.dezzy.app.feature.canvas.CANVAS_SONG_DEFAULT
import com.dezzy.app.feature.canvas.CanvasArtistEnabledKey
import com.dezzy.app.feature.canvas.CanvasSongEnabledKey
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.dezzy.app.feature.dj.DjSettings
import com.dezzy.app.feature.personalize.wallpaperFiles
import com.dezzy.app.feature.together.TogetherNameKey
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage as SettingsPageId
import com.dezzy.app.ui.player.kit.CanvasMode
import com.dezzy.app.ui.player.kit.CanvasModeKey
import com.dezzy.app.ui.player.kit.MiniPlayerImageUriKey
import com.dezzy.app.ui.player.kit.MiniPlayerStyle
import com.dezzy.app.ui.player.kit.MiniPlayerStyleKey
import com.dezzy.app.ui.player.kit.PlayerCustomBrightnessKey
import com.dezzy.app.ui.player.kit.PlayerCustomImageUriKey
import com.dezzy.app.ui.screens.personalize.MediaKind
import com.dezzy.app.ui.screens.personalize.extensionForMime
import com.dezzy.app.ui.screens.personalize.rememberMediaPicker
import com.dezzy.app.ui.screens.settings.prefs.ArtistVideoBackgroundKey
import com.dezzy.app.ui.screens.settings.prefs.ArtistVideoThumbnailsKey
import com.dezzy.app.ui.screens.settings.prefs.CropThumbnails11Key
import com.dezzy.app.ui.screens.settings.prefs.DefaultLibraryTabKey
import com.dezzy.app.ui.screens.settings.prefs.DefaultTabKey
import com.dezzy.app.ui.screens.settings.prefs.FloatingBubbleKey
import com.dezzy.app.ui.screens.settings.prefs.GridCellSizeKey
import com.dezzy.app.ui.screens.settings.prefs.HidePlayerThumbnailKey
import com.dezzy.app.ui.screens.settings.prefs.HomeCategoryChipsKey
import com.dezzy.app.ui.screens.settings.prefs.KeepScreenOnPlayerKey
import com.dezzy.app.ui.screens.settings.prefs.LibraryTagsKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsAnimStyleKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsAppleBlurKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsAutoScrollKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsHideStatusBarKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsLineSpacingKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsStandardBlurKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsTapToSeekKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsV2AnimKey
import com.dezzy.app.ui.screens.settings.prefs.LyricsV2Key
import com.dezzy.app.ui.screens.settings.prefs.MiniSwipeSensitivityKey
import com.dezzy.app.ui.screens.settings.prefs.PinterestPinKey
import com.dezzy.app.ui.screens.settings.prefs.PlayerButtonColorsKey
import com.dezzy.app.ui.screens.settings.prefs.PlayerCornerRadiusKey
import com.dezzy.app.ui.screens.settings.prefs.PlayerSwipeToChangeKey
import com.dezzy.app.ui.screens.settings.prefs.ShowCachedAutoPlaylistKey
import com.dezzy.app.ui.screens.settings.prefs.ShowCommentsButtonKey
import com.dezzy.app.ui.screens.settings.prefs.ShowDownloadedAutoPlaylistKey
import com.dezzy.app.ui.screens.settings.prefs.ShowLikedAutoPlaylistKey
import com.dezzy.app.ui.screens.settings.prefs.ShowTopAutoPlaylistKey
import com.dezzy.app.ui.screens.settings.prefs.SpinningThumbnailKey
import com.dezzy.app.ui.screens.settings.prefs.SwipeSongActionsKey
import com.dezzy.app.ui.screens.settings.prefs.ThemeRandomAtStartKey
import com.dezzy.app.ui.screens.settings.prefs.WallpaperBlurKey
import com.dezzy.app.ui.screens.settings.prefs.WallpaperIdleFadeKey
import com.dezzy.app.ui.screens.settings.prefs.WallpaperImageEnabledKey
import com.dezzy.app.ui.screens.settings.prefs.WallpaperVideoEnabledKey
import com.dezzy.app.ui.theme.AppFont
import com.dezzy.app.ui.theme.AppFontKey
import com.dezzy.app.ui.theme.fontFamily
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
import com.dezzy.app.ui.player.lyrics.LyricsAlign
import com.dezzy.app.ui.player.lyrics.LyricsAlignKey
import com.dezzy.app.ui.player.lyrics.LyricsAnim
import com.dezzy.app.ui.player.lyrics.LyricsAnimKey
import com.dezzy.app.ui.player.lyrics.LyricsTextScaleKey
import com.dezzy.app.ui.player.styles.PlayerStyleCoverflowSelector
import com.dezzy.app.ui.player.styles.VisualizerCarousel
import com.dezzy.app.ui.player.styles.VisualizerStyle
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.theme.AppInterfaceTheme
import com.dezzy.app.ui.theme.LiquidGlassIntensity
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    // Ajustes que dependen de otro: sólo se muestran cuando el principal está activo.
    val lyricsV2 by rememberPreference(LyricsV2Key, false)
    val wallpaperImage by rememberPreference(WallpaperImageEnabledKey, false)
    val wallpaperVideo by rememberPreference(WallpaperVideoEnabledKey, false)
    var wallpaperBlur by rememberPreference(WallpaperBlurKey, 0f)
    var canvasArtist by rememberPreference(CanvasArtistEnabledKey, CANVAS_ARTIST_DEFAULT)
    var canvasMode by rememberEnumPreference(CanvasModeKey, CanvasMode.APPLE_WITH_FALLBACK)

    var appFont by rememberEnumPreference(AppFontKey, AppFont.NUNITO)
    var customImageUri by rememberPreference(PlayerCustomImageUriKey, "")
    var customBrightness by rememberPreference(PlayerCustomBrightnessKey, 1f)
    var customBlur by rememberPreference(com.dezzy.app.ui.player.kit.PlayerCustomBlurKey, 0f)
    var customContrast by rememberPreference(com.dezzy.app.ui.player.kit.PlayerCustomContrastKey, 1f)
    var miniBgStyle by rememberEnumPreference(
        com.dezzy.app.ui.player.kit.MiniBackgroundStyleKey,
        com.dezzy.app.ui.player.kit.MiniBackgroundStyle.DEZZY_STYLE,
    )
    var miniDynamic by rememberPreference(com.dezzy.app.ui.player.kit.MiniDynamicColorKey, true)
    var miniStyle by rememberEnumPreference(MiniPlayerStyleKey, MiniPlayerStyle.PILL)
    val pickPlayerImage = rememberOwnImagePicker(customImageUri) { customImageUri = it }

    var randomAtStart by rememberPreference(ThemeRandomAtStartKey, false)
    var cornerRadius by rememberPreference(PlayerCornerRadiusKey, 16f)
    var swipeSensitivity by rememberPreference(MiniSwipeSensitivityKey, 73f)
    var lyricsAlign by rememberEnumPreference(LyricsAlignKey, LyricsAlign.CENTER)
    var lyricsAnim by rememberEnumPreference(LyricsAnimKey, LyricsAnim.FOCUS)
    var lyricsScale by rememberPreference(LyricsTextScaleKey, 1f)
    var lyricsSpacing by rememberPreference(LyricsLineSpacingKey, 1.3f)
    var userName by rememberPreference(TogetherNameKey, "")
    var navStyle by com.dezzy.app.ui.components.rememberNavBarStyle()

    val dj: DjSettings = koinInject()
    val djName by dj.name.collectAsState("")
    val djLanguage by dj.language.collectAsState("auto")

    var showStyleSelector by remember { mutableStateOf(false) }
    var showRadiusDialog by remember { mutableStateOf(false) }

    SettingsPage(title = "Apariencia", onBack = { ctx.navigator.back() }) {
        // ── Tema ────────────────────────────────────────────────────────────────────────────
        item(key = "theme_card") {
            Column {
                Text(
                    "Tema",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp),
                )
                AppThemeSelectorCard(
                    selectedTheme = interfaceTheme,
                    onSelectTheme = { scope.launch { prefs.setInterfaceTheme(it.name) } },
                    intensity = glassIntensity,
                    onSelectIntensity = { scope.launch { prefs.setGlassIntensity(it.name) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        item(key = "theme") {
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Activar el tema dinámico",
                    subtitle = "Usa los colores de tu fondo de pantalla",
                    icon = Icons.Rounded.Palette,
                    checked = dynamicColor,
                    onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                )
                if (!dynamicColor) {
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Tema aleatorio al inicio",
                    subtitle = "Aplicar paletas de colores aleatorias cada vez que la aplicación se inicie",
                    icon = Icons.Rounded.Gradient,
                    checked = randomAtStart,
                    onCheckedChange = { randomAtStart = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Paleta de color",
                    subtitle = "Elige los colores del tema de tu aplicación",
                    icon = Icons.Rounded.Palette,
                    onClick = { ctx.navigator.push(Screen.Settings(SettingsPageId.COLOR_PALETTE)) },
                )
                }
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Tema oscuro",
                    icon = Icons.Rounded.DarkMode,
                    options = listOf("dark", "light", "system"),
                    selected = themeMode,
                    label = {
                        when (it) {
                            "system" -> "Según el sistema"
                            "light" -> "Apagado"
                            else -> "Encendido"
                        }
                    },
                    onSelect = { scope.launch { prefs.setThemeMode(it) } },
                )
                if (themeMode != "light") {
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Negro puro",
                    subtitle = "Ahorra batería en pantallas OLED",
                    icon = Icons.Rounded.Contrast,
                    checked = pureBlack,
                    onCheckedChange = { scope.launch { prefs.setPureBlack(it) } },
                )
                }
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Fuente de la aplicación",
                    icon = Icons.Rounded.TextFields,
                    options = AppFont.entries.toList(),
                    selected = appFont,
                    label = { it.label },
                    onSelect = { appFont = it },
                    optionFont = { it.fontFamily() },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Icono de la app",
                    subtitle = "Forma y colores del icono, en la app y en el launcher",
                    icon = Icons.Rounded.Apps,
                    onClick = { ctx.navigator.push(Screen.Settings(SettingsPageId.APP_ICON)) },
                )
            }
        }

        // ── Desenfoque ──────────────────────────────────────────────────────────────────────────
        item(key = "effects") {
            SettingsGroup(label = "Desenfoque") {
                SettingsSwitchRow(
                    title = "Desactivar efectos de desenfoque",
                    subtitle = "Desactivar los efectos de desenfoque en toda la aplicación",
                    icon = Icons.Rounded.BlurOn,
                    checked = disableBlur,
                    onCheckedChange = { disableBlur = it },
                )
                if (!disableBlur) {
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
                }
            }
        }

        // ── Fondo de la app ─────────────────────────────────────────────────────────────────
        item(key = "wallpapers") {
            SettingsGroup(label = "Fondo de la app") {
                PinterestBlock(ctx)
                SettingsDivider()
                PrefSwitchRow("Fondo con imagen", null, WallpaperImageEnabledKey, false, Icons.Rounded.Image)
                SettingsDivider()
                PrefSwitchRow("Fondo con video", null, WallpaperVideoEnabledKey, false, Icons.Rounded.Videocam)
                if (wallpaperImage || wallpaperVideo) {
                SettingsDivider()
                SettingsSliderRow(
                    title = "Desenfoque del fondo",
                    icon = Icons.Rounded.BlurOn,
                    value = wallpaperBlur,
                    valueRange = 0f..40f,
                    steps = 7,
                    valueLabel = { if (it < 1f) "Nítido" else "${it.toInt()}" },
                    onValueChangeFinished = { wallpaperBlur = it },
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Desvanecimiento por inactividad",
                    "Desvanece la pantalla de inicio tras 2 minutos de inactividad si hay un fondo activo",
                    WallpaperIdleFadeKey,
                    false,
                    Icons.Rounded.Gradient,
                )
                SettingsDivider()
                SettingsRow(
                    title = "Elegir fondo",
                    subtitle = "Vertical y horizontal, imagen o vídeo",
                    icon = Icons.Rounded.Image,
                    onClick = { ctx.navigator.push(Screen.Settings(SettingsPageId.WALLPAPER)) },
                )
                }
            }
        }

        // ── Reproductor ─────────────────────────────────────────────────────────────────────────
        item(key = "player") {
            SettingsGroup(label = "Reproductor") {
                SettingsRow(title = "Estilo del diseño del reproductor", icon = Icons.Rounded.PlayCircle)
                PlayerStyleInlineCarousel(
                    current = playerStyle,
                    onApply = { playerStyle = it },
                    onOpenFull = { showStyleSelector = true },
                )
                // «Fondo del reproductor»: también va detrás de las letras, con cualquier estilo.
                run {
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
                        PlayerBackgroundEditor(
                            imageUri = customImageUri,
                            blur = customBlur,
                            brightness = customBrightness,
                            contrast = customContrast,
                            onPickImage = { pickPlayerImage() },
                            onRemoveImage = { customImageUri = "" },
                            onBlurChange = { customBlur = it },
                            onBrightnessChange = { customBrightness = it },
                            onContrastChange = { customContrast = it },
                        )
                    }
                }
                SettingsDivider()
                PrefSwitchRow(
                    "Ocultar miniatura del reproductor",
                    "Reemplaza la caratula del álbum con el logo de la app en el reproductor",
                    HidePlayerThumbnailKey,
                    false,
                    Icons.Rounded.Image,
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Miniatura giratoria",
                    "Habilitar un efecto de animación de miniatura giratoria",
                    SpinningThumbnailKey,
                    false,
                    Icons.Rounded.Image,
                )
                SettingsDivider()
                SettingsRow(
                    title = "Personaliza el radio de las esquinas",
                    subtitle = "${cornerRadius.toInt()} dp",
                    icon = Icons.Rounded.Apps,
                    onClick = { showRadiusDialog = true },
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Cortar miniaturas a 1:1",
                    "Mostrar miniaturas de YouTube en formato cuadrado en lugar de 16:9",
                    CropThumbnails11Key,
                    false,
                    Icons.Rounded.Image,
                )
                SettingsDivider()
                PrefStringChoiceRow(
                    title = "Colores de los botones del reproductor",
                    key = PlayerButtonColorsKey,
                    default = "DEFAULT",
                    options = listOf("DEFAULT" to "Por defecto", "DYNAMIC" to "Color dinámico", "WHITE" to "Blanco"),
                    icon = Icons.Rounded.Palette,
                )
                SettingsDivider()
                var sliderPicker by remember { mutableStateOf(false) }
                SettingsRow(
                    title = "Estilo de control deslizante del reproductor",
                    subtitle = sliderStyleLabel(sliderStyle),
                    icon = Icons.Rounded.LinearScale,
                    onClick = { sliderPicker = true },
                )
                if (sliderPicker) {
                    AlertDialog(
                        onDismissRequest = { sliderPicker = false },
                        title = { Text("Estilo de control deslizante del reproductor") },
                        text = {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SliderStyle.entries.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { style ->
                                            SliderStyleCard(
                                                sliderStyle = style,
                                                selected = style == sliderStyle,
                                                onClick = { sliderStyle = style; sliderPicker = false },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { sliderPicker = false }) { Text("Cancelar") } },
                    )
                }
                SettingsDivider()
                PrefSwitchRow("Activar deslizar para cambiar de canción", null, PlayerSwipeToChangeKey, true, Icons.Rounded.Swipe)
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Mostrar códec en el reproductor",
                    subtitle = "Mostrar información del códec de audio debajo de la barra de tiempo",
                    icon = Icons.Rounded.GraphicEq,
                    checked = showCodec,
                    onCheckedChange = { showCodec = it },
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Mostrar botón de comentarios",
                    "Mostrar un botón para ver los comentarios en la cola del reproductor",
                    ShowCommentsButtonKey,
                    true,
                    Icons.Rounded.TextFields,
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Mantener pantalla encendida",
                    "Evita que la pantalla se apague cuando el reproductor está expandido",
                    KeepScreenOnPlayerKey,
                    false,
                    Icons.Rounded.PlayCircle,
                )
            }
        }

        // ── Mini reproductor ────────────────────────────────────────────────────────────────────
        item(key = "mini_player") {
            SettingsGroup(label = "Mini reproductor") {
                SettingsSwitchRow(
                    title = "Nuevo diseño de mini reproductor",
                    icon = Icons.Rounded.PlayCircle,
                    checked = miniStyle == MiniPlayerStyle.BAR,
                    onCheckedChange = { miniStyle = if (it) MiniPlayerStyle.BAR else MiniPlayerStyle.PILL },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Color dinámico en mini reproductor",
                    subtitle = "Usa el color dominante de la carátula como fondo",
                    icon = Icons.Rounded.Palette,
                    checked = miniDynamic,
                    onCheckedChange = { miniDynamic = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Estilo de fondo del mini reproductor",
                    icon = Icons.Rounded.Gradient,
                    options = com.dezzy.app.ui.player.kit.MiniBackgroundStyle.entries.toList(),
                    selected = miniBgStyle,
                    label = { it.label() },
                    onSelect = { miniBgStyle = it },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Sensibilidad del deslizamiento del mini reproductor",
                    icon = Icons.Rounded.Swipe,
                    value = swipeSensitivity,
                    valueRange = 10f..100f,
                    steps = 17,
                    valueLabel = { "${it.toInt()}%" },
                    onValueChangeFinished = { swipeSensitivity = it },
                )
            }
        }

        // ── Canvas ──────────────────────────────────────────────────────────────────────────────
        item(key = "canvas") {
            SettingsGroup(label = "Canvas") {
                SettingsChoiceRow(
                    title = "Canvas de Canciones",
                    icon = Icons.Rounded.Videocam,
                    options = CanvasMode.entries.toList(),
                    selected = if (!canvasSong) CanvasMode.NONE else canvasMode,
                    label = {
                        when (it) {
                            CanvasMode.NONE -> "Desactivado"
                            CanvasMode.SPOTIFY -> "Spotify"
                            CanvasMode.SPOTIFY_WITH_FALLBACK -> "Spotify + Fallback de Video"
                            CanvasMode.APPLE -> "Apple Music"
                            CanvasMode.APPLE_WITH_FALLBACK -> "Apple Music + Fallback de Video"
                        }
                    },
                    onSelect = {
                        canvasSong = it != CanvasMode.NONE
                        if (it != CanvasMode.NONE) canvasMode = it
                    },
                )
                if (canvasSong && (canvasMode == CanvasMode.SPOTIFY || canvasMode == CanvasMode.SPOTIFY_WITH_FALLBACK)) Text(
                    "El Canvas de Spotify solo funciona si has iniciado sesión en Spotify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Canvas de Artistas",
                    icon = Icons.Rounded.Videocam,
                    options = listOf(false, true),
                    selected = canvasArtist,
                    label = { if (it) "Apple Music" else "Desactivado" },
                    onSelect = { canvasArtist = it },
                )
            }
        }

        // ── Letras ──────────────────────────────────────────────────────────────────────────
        item(key = "lyrics") {
            SettingsGroup(label = "Letras") {
                PrefSwitchRow(
                    "Lyrics V2 (Experimental)",
                    "Use the new fluid word-synced lyrics engine",
                    LyricsV2Key,
                    false,
                    Icons.Rounded.TextFields,
                )
                if (lyricsV2) {
                    SettingsDivider()
                    PrefStringChoiceRow(
                        title = "Estilo de animación de letras V2",
                        key = LyricsV2AnimKey,
                        default = "HEATMAP",
                        options = listOf("HEATMAP" to "Mapa de calor", "FADE" to "Desvanecer", "SCALE" to "Escala"),
                        icon = Icons.Rounded.TextFields,
                    )
                }
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Posición del texto de las letras",
                    icon = Icons.Rounded.TextFields,
                    options = LyricsAlign.entries.toList(),
                    selected = lyricsAlign,
                    label = { it.label },
                    onSelect = { lyricsAlign = it },
                )
                if (!lyricsV2) {
                SettingsDivider()
                PrefStringChoiceRow(
                    title = "Lyrics Animation Style",
                    key = LyricsAnimStyleKey,
                    default = "APPLE",
                    options = listOf("APPLE" to "Apple", "FADE" to "Fade", "SLIDE" to "Slide", "NONE" to "None"),
                    icon = Icons.Rounded.TextFields,
                )
                }
                SettingsDivider()
                PrefSwitchRow("Cambia la letra al presionarla", null, LyricsTapToSeekKey, true, Icons.Rounded.TextFields)
                SettingsDivider()
                PrefSwitchRow("Auto desplazamiento de letras", null, LyricsAutoScrollKey, true, Icons.Rounded.TextFields)
                // Con los desenfoques desactivados en toda la app, estas dos opciones no harían nada.
                if (!disableBlur) {
                SettingsDivider()
                PrefSwitchRow(
                    "Desenfoque de letras estilo Apple Music",
                    "Aplicar desenfoque a las líneas de letras inactivas para un efecto de enfoque premium",
                    LyricsAppleBlurKey,
                    true,
                    Icons.Rounded.BlurOn,
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Desenfoque de letras estándar",
                    "Aplicar un fondo desenfocado estándar a las letras",
                    LyricsStandardBlurKey,
                    false,
                    Icons.Rounded.BlurOn,
                )
                }
                SettingsDivider()
                PrefSwitchRow(
                    "Ocultar barra de estado en pantalla completa",
                    "Ocultar la barra de estado cuando el modo de pantalla completa de letras esté activo",
                    LyricsHideStatusBarKey,
                    false,
                    Icons.Rounded.TextFields,
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Tamaño del texto de letras",
                    icon = Icons.Rounded.TextFields,
                    value = lyricsScale * 26f,
                    valueRange = 16f..40f,
                    steps = 23,
                    valueLabel = { "${it.toInt()} sp" },
                    onValueChangeFinished = { lyricsScale = it / 26f },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Espaciado entre líneas de la letra",
                    icon = Icons.Rounded.LinearScale,
                    value = lyricsSpacing,
                    valueRange = 1f..2f,
                    steps = 9,
                    valueLabel = { "${kotlin.math.round(it * 10f) / 10f}x" },
                    onValueChangeFinished = { lyricsSpacing = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Animación de la línea activa",
                    icon = Icons.Rounded.TextFields,
                    options = LyricsAnim.entries.toList(),
                    selected = lyricsAnim,
                    label = { it.label },
                    onSelect = { lyricsAnim = it },
                )
            }
        }

        // ── Visualizador ────────────────────────────────────────────────────────────────────────
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

        // ── Navegación y biblioteca ─────────────────────────────────────────────────────────────
        item(key = "navigation") {
            SettingsGroup(label = "Navegación y biblioteca") {
                PrefStringChoiceRow(
                    title = "Pestaña abierta por defecto",
                    key = DefaultTabKey,
                    default = "HOME",
                    options = listOf("HOME" to "Inicio", "SEARCH" to "Buscar", "LIBRARY" to "Tu biblioteca", "ROOMS" to "Salas"),
                    icon = Icons.Rounded.Apps,
                )
                SettingsDivider()
                var navPicker by remember { mutableStateOf(false) }
                SettingsRow(
                    title = "Estilo de la barra de navegación",
                    subtitle = navStyleLabel(navStyle),
                    icon = Icons.Rounded.Apps,
                    onClick = { navPicker = true },
                )
                if (navPicker) {
                    AlertDialog(
                        onDismissRequest = { navPicker = false },
                        title = { Text("Estilo de la barra de navegación") },
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.dezzy.app.ui.components.NavBarStyle.entries.forEach { style ->
                                    NavStyleCard(
                                        style = style,
                                        selected = style == navStyle,
                                        onClick = { navStyle = style; navPicker = false },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { navPicker = false }) { Text("Cancelar") } },
                    )
                }
                SettingsDivider()
                PrefSwitchRow(
                    "Show home category chips",
                    "Show category filter chips on the home screen",
                    HomeCategoryChipsKey,
                    true,
                    Icons.Rounded.Apps,
                )
                SettingsDivider()
                PrefStringChoiceRow(
                    title = "Cambiar pestaña predeterminada de la biblioteca",
                    key = DefaultLibraryTabKey,
                    default = "PLAYLISTS",
                    options = listOf(
                        "PLAYLISTS" to "Listas de reproducción",
                        "ALBUMS" to "Álbumes",
                        "ARTISTS" to "Artistas",
                        "SONGS" to "Canciones",
                        "GENRES" to "Géneros",
                    ),
                    icon = Icons.Rounded.Apps,
                )
                SettingsDivider()
                PrefStringChoiceRow(
                    title = "Tamaño de celda de la cuadrícula",
                    key = GridCellSizeKey,
                    default = "SMALL",
                    options = listOf("SMALL" to "Pequeña", "LARGE" to "Grande"),
                    icon = Icons.Rounded.Apps,
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Mostrar etiquetas en la biblioteca",
                    "Mostrar el filtro de etiquetas en la pestaña de la biblioteca",
                    LibraryTagsKey,
                    true,
                    Icons.Rounded.Apps,
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Desliza a la izquierda la canción para añadirla a la cola, o a la derecha para reproducirla a continuación",
                    null,
                    SwipeSongActionsKey,
                    true,
                    Icons.Rounded.Swipe,
                )
            }
        }

        // ── DJ ──────────────────────────────────────────────────────────────────────────────────
        item(key = "dj") {
            SettingsGroup(label = "DJ") {
                TextPrefRow(
                    title = "Nombre del DJ",
                    value = djName,
                    placeholder = "DJ Dezzy",
                    icon = Icons.Rounded.GraphicEq,
                    onSave = { scope.launch { dj.setName(it) } },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Idioma del DJ",
                    icon = Icons.Rounded.TextFields,
                    options = listOf("auto", "es", "en"),
                    selected = djLanguage,
                    label = {
                        when (it) {
                            "es" -> "Español"
                            "en" -> "English"
                            else -> "Automático"
                        }
                    },
                    onSelect = { scope.launch { dj.setLanguage(it) } },
                )
                SettingsDivider()
                TextPrefRow(
                    title = "Tu Nombre (Usuario)",
                    value = userName,
                    placeholder = "Sin nombre",
                    icon = Icons.Rounded.TextFields,
                    onSave = { userName = it.take(40) },
                )
            }
        }

        // ── Más ─────────────────────────────────────────────────────────────────────────────────
        item(key = "misc") {
            SettingsGroup(label = "Más") {
                SettingsRow(
                    title = "Always On Display",
                    subtitle = "Estilos, formas y opciones de personalización",
                    icon = Icons.Rounded.DarkMode,
                    onClick = { ctx.navigator.push(Screen.Settings(SettingsPageId.AOD)) },
                )
                SettingsDivider()
                var bubble by rememberPreference(FloatingBubbleKey, false)
                SettingsSwitchRow(
                    title = "Burbuja Flotante",
                    subtitle = "Habilitar un reproductor de burbuja flotante en pantalla",
                    icon = Icons.Rounded.PlayCircle,
                    checked = bubble,
                    onCheckedChange = {
                        bubble = it
                        if (it) com.dezzy.app.feature.bubble.OverlayPermission.request?.invoke()
                    },
                )
            }
        }

        // ── Auto listas de reproducción ─────────────────────────────────────────────────────
        item(key = "auto_playlists") {
            SettingsGroup(label = "Auto listas de reproducción") {
                PrefSwitchRow("Mostrar lista de reproducción \"Me gusta\"", null, ShowLikedAutoPlaylistKey, true, Icons.Rounded.Apps)
                SettingsDivider()
                PrefSwitchRow("Mostrar lista de reproducción \"Descargado\"", null, ShowDownloadedAutoPlaylistKey, true, Icons.Rounded.Apps)
                SettingsDivider()
                PrefSwitchRow("Mostrar lista de reproducción \"Top\"", null, ShowTopAutoPlaylistKey, true, Icons.Rounded.Apps)
                SettingsDivider()
                PrefSwitchRow("Mostrar lista de reproducción \"En cache\"", null, ShowCachedAutoPlaylistKey, true, Icons.Rounded.Apps)
            }
        }

        // ── Perfil del Artista ──────────────────────────────────────────────────────────────
        item(key = "artist_profile") {
            SettingsGroup(label = "Perfil del Artista") {
                PrefSwitchRow(
                    "Miniaturas de video del artista",
                    "Mostrar pequeñas miniaturas de video animadas junto al nombre del artista",
                    ArtistVideoThumbnailsKey,
                    true,
                    Icons.Rounded.Videocam,
                )
                SettingsDivider()
                PrefSwitchRow(
                    "Fondo de video del artista (Apple Music)",
                    "Mostrar video editorial de Apple Music como fondo en el perfil del artista",
                    ArtistVideoBackgroundKey,
                    true,
                    Icons.Rounded.Videocam,
                )
            }
        }
    }

    if (showRadiusDialog) {
        var draft by remember { mutableStateOf(cornerRadius) }
        AlertDialog(
            onDismissRequest = { showRadiusDialog = false },
            title = { Text("Personaliza el radio de las esquinas") },
            text = {
                Column {
                    Text("${draft.toInt()} dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = draft,
                        onValueChange = { draft = it },
                        valueRange = 0f..32f,
                        steps = 31,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cornerRadius = draft
                    showRadiusDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showRadiusDialog = false }) { Text("Cancelar") } },
        )
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

/** Interruptor ligado a una clave booleana de preferencias. */
@Composable
private fun PrefSwitchRow(
    title: String,
    subtitle: String?,
    key: Preferences.Key<Boolean>,
    default: Boolean,
    icon: ImageVector?,
) {
    var value by rememberPreference(key, default)
    SettingsSwitchRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        checked = value,
        onCheckedChange = { value = it },
    )
}

/** Elección ligada a una clave de texto; [options] son pares valor guardado → texto visible. */
@Composable
private fun PrefStringChoiceRow(
    title: String,
    key: Preferences.Key<String>,
    default: String,
    options: List<Pair<String, String>>,
    icon: ImageVector?,
) {
    var value by rememberPreference(key, default)
    SettingsChoiceRow(
        title = title,
        icon = icon,
        options = options.map { it.first },
        selected = value,
        label = { code -> options.firstOrNull { it.first == code }?.second ?: code },
        onSelect = { value = it },
    )
}

/** Fila con un valor de texto que se edita en un diálogo. */
@Composable
private fun TextPrefRow(
    title: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    onSave: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(title = title, subtitle = value.ifBlank { placeholder }, icon = icon, onClick = { open = true })
    if (open) {
        var draft by remember(value) { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(draft.trim())
                    open = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        )
    }
}

/** Bloque «Desde un pin de Pinterest»: texto de ayuda, campo con el enlace y botón «Usar». */
@Composable
private fun PinterestBlock(ctx: ScreenContext) {
    var saved by rememberPreference(PinterestPinKey, "")
    var draft by remember(saved) { mutableStateOf(saved) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Desde un pin de Pinterest", style = MaterialTheme.typography.titleSmall)
        Text(
            "Pega el enlace de un pin con imagen o video. Si es vertical, será tu fondo; si es horizontal, sólo se usará al girar el móvil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text("pin.it/… o pinterest.com/pin/…") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                saved = draft.trim()
                ctx.toast("Guardado")
            }) { Text("Usar") }
        }
    }
}

/** Carrusel en línea de estilos del reproductor: contador «n/N», nombre y «En uso». */
@Composable
private fun PlayerStyleInlineCarousel(
    current: PlayerDesignStyle,
    onApply: (PlayerDesignStyle) -> Unit,
    onOpenFull: () -> Unit,
) {
    val styles = PlayerDesignStyle.entries
    var index by remember(current) { mutableStateOf(styles.indexOf(current).coerceAtLeast(0)) }
    val focused = styles[index]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { index = (index - 1 + styles.size) % styles.size }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Anterior")
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenFull).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${index + 1}/${styles.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(focused.label(), style = MaterialTheme.typography.titleMedium)
            if (focused == current) {
                Text(
                    "En uso",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            } else {
                TextButton(onClick = { onApply(focused) }) { Text("Usar") }
            }
        }
        IconButton(onClick = { index = (index + 1) % styles.size }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Siguiente")
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
    SliderStyle.Thick -> "Grueso"
    SliderStyle.Circular -> "Circular"
    SliderStyle.Slim -> "Simple"
    SliderStyle.Neon -> "Brillo de neón"
    SliderStyle.Capsule -> "Cápsula"
    SliderStyle.DoubleLine -> "Línea doble"
    SliderStyle.Dots -> "Puntos"
    SliderStyle.AudioWave -> "Onda de audio"
    SliderStyle.ShootingStar -> "Estrella fugaz"
    SliderStyle.Aurora -> "Aurora boreal"
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
            .padding(8.dp),
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
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

private fun navStyleLabel(style: com.dezzy.app.ui.components.NavBarStyle): String = when (style) {
    com.dezzy.app.ui.components.NavBarStyle.FULL -> "Completa"
    com.dezzy.app.ui.components.NavBarStyle.SLIM -> "Delgada"
    com.dezzy.app.ui.components.NavBarStyle.DOCK -> "Flotante"
}

/** Tarjeta con un dibujo de la barra para el selector de estilo de navegación. */
@Composable
private fun NavStyleCard(
    style: com.dezzy.app.ui.components.NavBarStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)).background(c.surface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val dot = @Composable { on: Boolean ->
                Box(
                    Modifier.size(width = 12.dp, height = 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (on) c.primary else c.onSurfaceVariant.copy(alpha = 0.5f)),
                )
            }
            when (style) {
                com.dezzy.app.ui.components.NavBarStyle.FULL -> Row(
                    Modifier.fillMaxWidth().height(26.dp).background(c.surfaceContainer),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { i ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            dot(i == 0)
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.size(width = 10.dp, height = 2.dp).background(c.onSurfaceVariant.copy(alpha = 0.5f)))
                        }
                    }
                }
                com.dezzy.app.ui.components.NavBarStyle.SLIM -> Row(
                    Modifier.fillMaxWidth().height(16.dp).background(c.surfaceContainer),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) { repeat(4) { i -> dot(i == 0) } }
                com.dezzy.app.ui.components.NavBarStyle.DOCK -> Row(
                    Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.height(18.dp).clip(RoundedCornerShape(9.dp)).background(c.surfaceContainerHighest)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { repeat(3) { i -> dot(i == 0) } }
                    Box(Modifier.size(18.dp).clip(CircleShape).background(c.primaryContainer))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            navStyleLabel(style),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) c.primary else c.onSurfaceVariant,
        )
    }
}
