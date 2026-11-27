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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.ScreenContext
import com.dezzy.app.ui.screens.settings.prefs.*
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
    var client by rememberPreference(PlaybackClientKey, "Android VR")
    var metered by rememberPreference(MeteredNetworkKey, false)
    var historyDays by rememberPreference(HistoryDurationKey, 30)
    var offload by rememberPreference(AudioOffloadKey, false)
    var progressiveSeek by rememberPreference(ProgressiveSeekKey, false)
    var castVideo by rememberPreference(CastVideoKey, false)
    var autoDownloadLike by rememberPreference(AutoDownloadOnLikeKey, false)
    var stopOnTask by rememberPreference(StopMusicOnTaskClearKey, false)
    var cpuAwake by rememberPreference(KeepCpuAwakeKey, false)
    var separators by rememberPreference(ArtistSeparatorsKey, ",;/&")
    var externalDl by rememberPreference(ExternalDownloaderKey, false)
    var dlPackage by rememberPreference(ExternalDownloaderPackageKey, "")
    var packageDialog by remember { mutableStateOf(false) }

    SettingsPage(title = "Reproductor y audio", onBack = { ctx.navigator.back() }) {
        item(key = "quality") {
            SettingsGroup("Reproductor") {
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
                    subtitle = if (!audioFx.processingSupported) {
                        "El procesado de audio sólo funciona en Android"
                    } else {
                        "Decodifica, ecualiza y envía el audio en coma flotante de 32 bits: conserva la resolución de FLAC de 24 bits y el ecualizador no recorta. Desactiva \"Omitir silencio\". Se aplica al reiniciar la app."
                    },
                    checked = audioFx.hiResEnabled,
                    onCheckedChange = audioEffects::setHiResEnabled,
                    enabled = audioFx.processingSupported,
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Cliente de reproducción",
                    options = listOf("Android VR", "Web Remix", "iOS", "TV"),
                    selected = client,
                    label = { it },
                    onSelect = { client = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Tratar la red como de uso medido",
                    subtitle = "Si esta activada, la aplicación preferirá las transmisiones de baja calidad en redes de uso medido",
                    checked = metered,
                    onCheckedChange = { metered = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Duración del historial",
                    options = listOf(7, 30, 60, 90, 365),
                    selected = historyDays,
                    label = { "$it" },
                    onSelect = { historyDays = it },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Fundido cruzado",
                    icon = Icons.Rounded.Tune,
                    value = crossfade.toFloat(),
                    valueRange = 0f..12f,
                    steps = 11,
                    valueLabel = { if (it.roundToInt() == 0) "Crossfade disabled" else "${it.roundToInt()} s" },
                    onValueChangeFinished = { scope.launch { prefs.setCrossfadeSeconds(it.roundToInt()) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Omitir silencio",
                    icon = Icons.Rounded.FastForward,
                    checked = skipSilence,
                    onCheckedChange = { skipSilence = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Normalización de audio",
                    icon = Icons.Rounded.VolumeUp,
                    checked = normalize,
                    onCheckedChange = { scope.launch { prefs.setNormalizeVolume(it) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Offload de audio",
                    subtitle = "Permite que los dispositivos compatibles reproduzcan audio utilizando decodificación de hardware de bajo consumo. Algunas funciones de procesamiento de audio están desactivadas.",
                    checked = offload,
                    onCheckedChange = { offload = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Búsqueda progresiva",
                    subtitle = "Si esta habilitado, añade 5 segundos extra de forma incremental en cada salto de búsqueda",
                    checked = progressiveSeek,
                    onCheckedChange = { progressiveSeek = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Pause when device volume is 0",
                    subtitle = "Automatically pause music when muted and resume when unmuted",
                    icon = Icons.Rounded.VolumeOff,
                    checked = pauseOnMute,
                    onCheckedChange = { pauseOnMute = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Auto-start on Bluetooth connect",
                    subtitle = "Automatically resume playback when a Bluetooth audio device connects",
                    icon = Icons.Rounded.Bluetooth,
                    checked = playOnBluetooth,
                    onCheckedChange = { playOnBluetooth = it },
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
        item(key = "video") {
            SettingsGroup("Reproducción de video") {
                SettingsChoiceRow(
                    title = "Calidad máxima de video",
                    icon = Icons.Rounded.Movie,
                    options = VideoQuality.entries.toList(),
                    selected = videoQuality,
                    label = { "${it.maxHeight}p" },
                    onSelect = { videoQuality = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Ajuste del video",
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
                    title = "Mantener modo video",
                    subtitle = "Seguir en modo video al cambiar de canción",
                    checked = keepMode,
                    onCheckedChange = { keepMode = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Imagen en imagen",
                    subtitle = "Seguir viendo el video en una ventana pequeña al salir de la app",
                    checked = pip,
                    onCheckedChange = { pip = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Castear video",
                    subtitle = "Enviar el video musical (no solo el audio) al castear",
                    checked = castVideo,
                    onCheckedChange = { castVideo = it },
                )
            }
        }
        item(key = "queue") {
            SettingsGroup("Cola") {
                SettingsSwitchRow(
                    title = "Cola persistente",
                    subtitle = "Restaura tú ultima cola cuando se inicia la aplicación",
                    icon = Icons.Rounded.Queue,
                    checked = persistentQueue,
                    onCheckedChange = { scope.launch { prefs.setPersistentQueue(it) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Auto descargar al dar me gusta",
                    subtitle = "Descargar las canciones automáticamente al darle me gusta",
                    checked = autoDownloadLike,
                    onCheckedChange = { autoDownloadLike = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Omitir automáticamente la siguiente canción cuando ocurran errores",
                    subtitle = "Asegúrate de que la reproducción no se interrumpa",
                    icon = Icons.Rounded.SkipNext,
                    checked = autoSkip,
                    onCheckedChange = { scope.launch { prefs.setAutoSkip(it) } },
                )
            }
        }
        item(key = "misc") {
            SettingsGroup("Varios") {
                SettingsSwitchRow(
                    title = "Detener la música al finalizar la tarea",
                    checked = stopOnTask,
                    onCheckedChange = { stopOnTask = it },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Keep CPU awake during playback",
                    subtitle = "Prevents the system from interrupting music by holding a CPU wake lock",
                    checked = cpuAwake,
                    onCheckedChange = { cpuAwake = it },
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Símbolos para separar artistas",
                    options = listOf(",;/&", ",", ";", "/", "&"),
                    selected = separators,
                    label = { s -> s.map { "\"$it\"" }.joinToString(" ") },
                    onSelect = { separators = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Etiquetas de lista de reproducción",
                    subtitle = "Crea y administra etiquetas para organizar las listas de reproducción",
                    onClick = { ctx.toast("Las etiquetas de listas llegan en una próxima versión") },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "External Downloader",
                    subtitle = "Download songs through an external app by package name",
                    checked = externalDl,
                    onCheckedChange = { externalDl = it },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Downloader App Package",
                    subtitle = dlPackage.ifBlank { "Package name of the external downloader app (e.g. com.deniscerri.ytdl)" },
                    onClick = if (externalDl) ({ packageDialog = true }) else null,
                    trailing = { Spacer(Modifier.size(0.dp)) },
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
    }

    if (packageDialog) {
        var draft by remember { mutableStateOf(dlPackage) }
        AlertDialog(
            onDismissRequest = { packageDialog = false },
            title = { Text("Downloader App Package") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("com.deniscerri.ytdl") },
                )
            },
            confirmButton = { TextButton(onClick = { dlPackage = draft.trim(); packageDialog = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { packageDialog = false }) { Text("Cancelar") } },
        )
    }
}
