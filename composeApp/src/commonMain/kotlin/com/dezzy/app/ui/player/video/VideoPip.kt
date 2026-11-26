/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dezzy.app.feature.video.VideoPipEnabledKey
import com.dezzy.app.feature.video.VideoPipState
import com.dezzy.app.feature.video.VideoPlayerListener
import com.dezzy.app.feature.video.VideoResizeMode
import com.dezzy.app.feature.video.VideoResizeModeKey
import com.dezzy.app.ui.player.kit.PlayerConnection
import com.dezzy.app.ui.player.kit.VideoSurface
import com.dezzy.app.ui.player.kit.rememberEnumPreference
import com.dezzy.app.ui.player.kit.rememberPreference

/**
 * Imagen en imagen del modo video, a nivel raíz de MainActivity:
 * - avisa a la Activity de si ahora mismo puede pasar a PiP (modo video sonando y la opción
 *   activada en Ajustes) para que entre al salir de la app;
 * - en PiP tapa toda la interfaz con sólo el video (las demás superficies se sueltan solas).
 */
@Composable
fun VideoPipLayer(
    playerConnection: PlayerConnection,
    onEligibleChange: (Boolean) -> Unit,
) {
    val videoModeActive by playerConnection.videoModeActive.collectAsState()
    val videoPlayer by playerConnection.videoPlayerInstance.collectAsState()
    val inPip by VideoPipState.inPip.collectAsState()
    val pipEnabled by rememberPreference(VideoPipEnabledKey, defaultValue = true)
    val resizeModePref by rememberEnumPreference(VideoResizeModeKey, VideoResizeMode.FIT)

    var videoPlaying by remember { mutableStateOf(false) }
    DisposableEffect(videoPlayer) {
        val vp = videoPlayer
        if (vp == null) {
            videoPlaying = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : VideoPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                videoPlaying = isPlaying || vp.playWhenReady
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                videoPlaying = playWhenReady
            }
        }
        videoPlaying = vp.playWhenReady
        vp.addListener(listener)
        onDispose { vp.removeListener(listener) }
    }

    val notify by rememberUpdatedState(onEligibleChange)
    val eligible = pipEnabled && videoModeActive && videoPlayer != null && videoPlaying
    LaunchedEffect(eligible) { notify(eligible) }
    DisposableEffect(Unit) { onDispose { notify(false) } }

    if (inPip && videoModeActive && videoPlayer != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            VideoSurface(
                player = videoPlayer,
                active = true,
                pipSurface = true,
                resizeMode = resizeModePref,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
