/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.dezzy.app.feature.audio.AudioEffects
import com.dezzy.app.feature.downloads.MobileDataQualityKey
import com.dezzy.app.feature.video.VideoKeepModeOnSongChangeKey
import com.dezzy.app.feature.video.VideoMaxQualityKey
import com.dezzy.app.feature.video.VideoPipEnabledKey
import com.dezzy.app.feature.video.VideoQuality
import com.dezzy.app.feature.video.VideoResizeMode
import com.dezzy.app.feature.video.VideoResizeModeKey
import com.dezzy.app.feature.player.PauseOnMuteKey
import com.dezzy.app.feature.player.PlayOnBluetoothKey
import com.dezzy.app.feature.player.SkipSilenceKey
import com.dezzy.app.source.stream.AudioQuality
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.player.kit.MiniPlayerStyle
import com.dezzy.app.ui.player.kit.MiniPlayerStyleKey
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private fun audioQualityLabel(q: AudioQuality): String = when (q) {
    AudioQuality.AUTO -> "Automática"
    AudioQuality.HIGH -> "Alta"
    AudioQuality.LOW -> "Baja"
}

/** Calidad guardada como «auto» / «high» / «low». */
internal fun qualityLabel(key: String): String = when (key) {
    "high" -> "Alta"
    "low" -> "Baja"
    else -> "Automática"
}

@Composable
internal fun PlayerSettingsScreen(ctx: ScreenContext) {
    val prefs = ctx.prefs
    val scope = ctx.scope
    val audioEffects: AudioEffects = koinInject()
    val audioFx by audioEffects.state.collectAsState()
    val quality by prefs.audioQuality.collectAsState("auto")
    val normalize by prefs.normalizeVolume.collectAsState(false)
    val crossfade by prefs.crossfadeSeconds.collectAsState(0)
    val persistentQueue by prefs.persistentQueue.collectAsState(true)
    val autoSkip by prefs.autoSkip.collectAsState(true)

    var mobileQuality by rememberPreference(MobileDataQualityKey, "auto")
    var videoQuality by rememberEnumPreference(VideoMaxQualityKey, VideoQuality.P720)
    var resizeMode by rememberEnumPreference(VideoResizeModeKey, VideoResizeMode.FIT)
    var keepMode by rememberPreference(VideoKeepModeOnSongChangeKey, true)
    var pip by rememberPreference(VideoPipEnabledKey, true)
    var skipSilence by rememberPreference(SkipSilenceKey, false)
    var pauseOnMute by rememberPreference(PauseOnMuteKey, false)
    var playOnBluetooth by rememberPreference(PlayOnBluetoothKey, false)
    var miniStyle by rememberEnumPreference(MiniPlayerStyleKey, MiniPlayerStyle.PILL)

    SettingsPage(title = "Reproductor y audio", onBack = { ctx.navigator.back() }) {
        item(key = "quality") {
            SettingsGroup("Calidad") {
                SettingsChoiceRow(
                    title = "Calidad de audio",
                    icon = Icons.Rounded.HighQuality,
                    options = listOf(AudioQuality.AUTO, AudioQuality.HIGH, AudioQuality.LOW),
                    selected = AudioQuality.entries.firstOrNull { it.name.equals(quality, ignoreCase = true) } ?: AudioQuality.AUTO,
                    label = ::audioQualityLabel,
                    onSelect = { scope.launch { prefs.setAudioQuality(it.name) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Motor de audio Hi-Res (32-bit float)",
                    subtitle = when {
                        !audioFx.processingSupported -> "El procesado de audio sólo funciona en Android"
                        !audioFx.nativeAvailable -> "Usando el motor de respaldo"
                        else -> "Se aplica al volver a abrir la app"
                    },
                    checked = audioFx.hiResEnabled,
                    onCheckedChange = audioEffects::setHiResEnabled,
                    enabled = audioFx.processingSupported,
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Calidad con datos móviles",
                    options = listOf("auto", "high", "low"),
                    selected = mobileQuality,
                    label = { qualityLabel(it) },
                    onSelect = { mobileQuality = it },
                )
            }
        }
        item(key = "sound") {
            SettingsGroup("Sonido") {
                SettingsSwitchRow(
                    title = "Normalizar el volumen",
                    subtitle = "Mismo nivel entre canciones",
                    icon = Icons.Rounded.VolumeUp,
                    checked = normalize,
                    onCheckedChange = { scope.launch { prefs.setNormalizeVolume(it) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Fundido entre canciones",
                    icon = Icons.Rounded.Tune,
                    value = crossfade.toFloat(),
                    valueRange = 0f..12f,
                    steps = 11,
                    valueLabel = { if (it.roundToInt() == 0) "Desactivado" else "${it.roundToInt()} s" },
                    onValueChangeFinished = { scope.launch { prefs.setCrossfadeSeconds(it.roundToInt()) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Saltar silencios",
                    subtitle = "Acorta los silencios dentro de las canciones",
                    icon = Icons.Rounded.FastForward,
                    checked = skipSilence,
                    onCheckedChange = { skipSilence = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Ecualizador",
                    subtitle = if (audioFx.eqEnabled) "Activado" else "Desactivado",
                    icon = Icons.Rounded.GraphicEq,
                    onClick = { ctx.navigator.push(Screen.Settings(SettingsPage.EQUALIZER)) },
                )
            }
        }
        item(key = "behavior") {
            SettingsGroup("Comportamiento") {
                SettingsChoiceRow(
                    title = "Estilo del minirreproductor",
                    icon = Icons.Rounded.Queue,
                    options = MiniPlayerStyle.entries.toList(),
                    selected = miniStyle,
                    label = { if (it == MiniPlayerStyle.PILL) "Píldora" else "Barra" },
                    onSelect = { miniStyle = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Recordar la cola",
                    subtitle = "Recupera lo que sonaba al volver a abrir",
                    icon = Icons.Rounded.Queue,
                    checked = persistentQueue,
                    onCheckedChange = { scope.launch { prefs.setPersistentQueue(it) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Saltar canciones que fallan",
                    icon = Icons.Rounded.SkipNext,
                    checked = autoSkip,
                    onCheckedChange = { scope.launch { prefs.setAutoSkip(it) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Pausar al silenciar",
                    subtitle = "Pausa si el volumen de medios llega a 0 y reanuda al subirlo",
                    icon = Icons.Rounded.VolumeOff,
                    checked = pauseOnMute,
                    onCheckedChange = { pauseOnMute = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Reproducir al conectar Bluetooth",
                    subtitle = "Si hay cola, empieza a sonar al conectar auriculares o altavoz",
                    icon = Icons.Rounded.Bluetooth,
                    checked = playOnBluetooth,
                    onCheckedChange = { playOnBluetooth = it },
                )
            }
        }
        item(key = "video") {
            SettingsGroup("Vídeo") {
                SettingsChoiceRow(
                    title = "Calidad máxima del vídeo",
                    icon = Icons.Rounded.Movie,
                    options = VideoQuality.entries.toList(),
                    selected = videoQuality,
                    label = { "${it.maxHeight}p" },
                    onSelect = { videoQuality = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Ajuste de la imagen",
                    icon = Icons.Rounded.Crop,
                    options = VideoResizeMode.entries.toList(),
                    selected = resizeMode,
                    label = {
                        when (it) {
                            VideoResizeMode.FIT -> "Ajustar"
                            VideoResizeMode.CROP -> "Recortar"
                            VideoResizeMode.FILL -> "Rellenar"
                        }
                    },
                    onSelect = { resizeMode = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Mantener el modo vídeo al cambiar de canción",
                    checked = keepMode,
                    onCheckedChange = { keepMode = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Ventana flotante (PiP)",
                    checked = pip,
                    onCheckedChange = { pip = it },
                )
            }
        }
    }
}
