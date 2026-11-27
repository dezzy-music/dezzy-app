/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.LocalBottomSheetPageState
import com.dezzy.app.ui.player.kit.LocalMenuState
import com.dezzy.app.ui.player.kit.LocalOpenPlayerStyleSelector
import com.dezzy.app.ui.player.kit.LocalOpenSleepTimer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.dezzy.app.ui.player.aod.AodScreen
import com.dezzy.app.ui.player.kit.LocalOpenAod
import com.dezzy.app.ui.player.kit.LocalOpenVisualizer
import com.dezzy.app.ui.player.kit.rememberPreference
import com.dezzy.app.ui.screens.settings.prefs.AodInactivityMinutesKey
import com.dezzy.app.ui.player.kit.MenuHost
import com.dezzy.app.ui.player.kit.MenuState
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.PlayerDesignStyle
import com.dezzy.app.ui.player.kit.PlayerDesignStyleKey
import com.dezzy.app.ui.player.kit.PlayerMenu
import com.dezzy.app.ui.player.kit.ShowMediaInfo
import com.dezzy.app.ui.player.kit.SleepTimerDialog
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.lyrics.LyricsScreen
import com.dezzy.app.ui.player.styles.AuroraPlayer
import com.dezzy.app.ui.player.styles.CarModePlayer
import com.dezzy.app.ui.player.styles.ClassicPlayer
import com.dezzy.app.ui.player.styles.DeezerPlayer
import com.dezzy.app.ui.player.styles.DezzyPlayer
import com.dezzy.app.ui.player.styles.IpodPlayer
import com.dezzy.app.ui.player.styles.NcsVisualizerScreen
import com.dezzy.app.ui.player.styles.PlayerStyleCoverflowSelector
import com.dezzy.app.ui.player.styles.RedesignedStylePlayer
import com.dezzy.app.ui.player.styles.SpotifyPlayer
import com.dezzy.app.ui.player.styles.VinylPlayer
import com.dezzy.app.ui.player.video.VideoModeOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Reproductor expandido: elige el estilo guardado (13 estilos) y le da los paneles comunes —
 * cola, menú, temporizador, visualizador y selector de estilo—.
 */
@Composable
fun PlayerRouter(
    connection: PlayerConnection,
    state: PlaybackState,
    sheet: BottomSheetState,
    navController: NavController,
) {
    val controller = connection.controller
    val style by rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V1)
    val menuState = remember { MenuState() }
    val pageState = remember { MenuState() }
    val positionState: State<Long> = remember(controller) { controller.state.map { it.positionMs } }
        .collectAsState(controller.state.value.positionMs)
    val durationState: State<Long> = remember(controller) { controller.state.map { it.durationMs } }
        .collectAsState(controller.state.value.durationMs)
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showVisualizer by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val mediaMetadata by connection.mediaMetadata.collectAsState()
    val isPlaying by connection.isPlaying.collectAsState()
    var showAod by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    val aodMinutes by rememberPreference(AodInactivityMinutesKey, 0)

    // Activación automática de la pantalla siempre activa tras N minutos sin toques.
    LaunchedEffect(lastTouch, aodMinutes, isPlaying, sheet.isExpanded, mediaMetadata != null) {
        if (aodMinutes > 0 && isPlaying && sheet.isExpanded && mediaMetadata != null && !showAod) {
            delay(aodMinutes * 60_000L)
            showAod = true
        }
    }

    // Cuenta atrás del temporizador (sólo cuando hay uno puesto).
    val sleepAt = state.sleepAtMillis
    var sleepLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepAt) {
        while (sleepAt != null) {
            sleepLeft = (sleepAt - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0)
            delay(1000)
        }
        sleepLeft = 0
    }
    val sleepEnabled = sleepAt != null || state.sleepAtEndOfSong

    PlatformBackHandler(enabled = showVisualizer) { showVisualizer = false }
    PlatformBackHandler(enabled = showStyles) { showStyles = false }

    val onQueue = { showQueue = true }
    val onLyrics = { showLyrics = true }
    val onSleep = { showSleep = true }
    val onVisualizer = { showVisualizer = true }
    val onMenu: () -> Unit = {
        mediaMetadata?.let { meta ->
            menuState.show {
                PlayerMenu(
                    mediaMetadata = meta,
                    navController = navController,
                    playerBottomSheetState = sheet,
                    onShowDetailsDialog = {
                        menuState.dismiss()
                        pageState.show { ShowMediaInfo(meta.id) }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }
    val onSlider: (Long?) -> Unit = { sliderPosition = it }

    CompositionLocalProvider(
        LocalMenuState provides menuState,
        LocalBottomSheetPageState provides pageState,
        LocalOpenPlayerStyleSelector provides { showStyles = true },
        LocalOpenSleepTimer provides { showSleep = true },
        LocalOpenVisualizer provides { showVisualizer = true },
        LocalOpenAod provides { showAod = true },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            lastTouch = Clock.System.now().toEpochMilliseconds()
                        }
                    }
                },
        ) {
            when (style) {
                PlayerDesignStyle.V1 -> ClassicPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics, onSleepTimerClick = onSleep,
                    sleepTimerEnabled = sleepEnabled, sleepTimerTimeLeft = sleepLeft,
                    onMenuClick = onMenu, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.V2, PlayerDesignStyle.V3, PlayerDesignStyle.V4, PlayerDesignStyle.V6 -> RedesignedStylePlayer(
                    style = style, state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics,
                    onMenuClick = onMenu, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.V5, PlayerDesignStyle.APPLE_MUSIC -> DezzyPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics,
                    playerStyle = style,
                    onSleepTimerClick = onSleep, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.V7 -> DeezerPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics, onSleepTimerClick = onSleep,
                    onMenuClick = onMenu, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.IPOD -> IpodPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onMenuClick = onMenu, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.SPOTIFY -> SpotifyPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics, onSleepTimerClick = onSleep,
                    onMenuClick = onMenu, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.CAR -> CarModePlayer(
                    state = sheet, navController = navController,
                    mediaMetadata = mediaMetadata, isPlaying = isPlaying,
                    onQueueClick = onQueue, onVisualizerClick = onVisualizer,
                )
                PlayerDesignStyle.LIVE_MESH -> VinylPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics, onSleepTimerClick = onSleep,
                    onVisualizerClick = onVisualizer, onMenuClick = onMenu,
                )
                PlayerDesignStyle.AURORA -> AuroraPlayer(
                    state = sheet, navController = navController,
                    positionState = positionState, durationState = durationState,
                    sliderPosition = sliderPosition, onSliderPositionChange = onSlider,
                    onQueueClick = onQueue, onLyricsClick = onLyrics, onSleepTimerClick = onSleep,
                    onMenuClick = onMenu, onVisualizerClick = onVisualizer,
                )
            }

            VideoModeOverlay(
                playerConnection = connection,
                showFloatingToggle = style == PlayerDesignStyle.V5 && !showQueue,
                onCollapse = { sheet.collapseSoft() },
                onQueueClick = { showQueue = true },
            )

            AnimatedVisibility(showVisualizer && mediaMetadata != null, enter = fadeIn(), exit = fadeOut()) {
                mediaMetadata?.let { meta ->
                    NcsVisualizerScreen(mediaMetadata = meta, onBackClick = { showVisualizer = false })
                }
            }

            AnimatedVisibility(showStyles, enter = fadeIn(), exit = fadeOut()) {
                StyleSelectorPage(style, onApply = { showStyles = false }, onBack = { showStyles = false })
            }

            if (showLyrics) {
                LyricsScreen(playerConnection = connection, onClose = { showLyrics = false })
            }

            if (showAod) {
                Box(Modifier.fillMaxSize().zIndex(10f)) {
                    AodScreen(playerConnection = connection, onClose = {
                        showAod = false
                        lastTouch = Clock.System.now().toEpochMilliseconds()
                    })
                }
            }
        }

        MenuHost(menuState)
        MenuHost(pageState)
        if (showQueue) QueueSheet(state, controller, onDismiss = { showQueue = false })
        if (showSleep) SleepTimerDialog(onDismiss = { showSleep = false })
    }
}

@Composable
private fun StyleSelectorPage(current: PlayerDesignStyle, onApply: () -> Unit, onBack: () -> Unit) {
    var style by rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V1)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
            Text("Estilo del reproductor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        PlayerStyleCoverflowSelector(
            selectedStyle = current,
            onApplyStyle = { style = it; onApply() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
