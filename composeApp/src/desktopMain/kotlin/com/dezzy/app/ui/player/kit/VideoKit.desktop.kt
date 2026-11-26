/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dezzy.app.feature.video.VideoPlayerHandle
import com.dezzy.app.feature.video.VideoResizeMode

// En escritorio (sólo pruebas) no hay modo vídeo: la superficie queda en negro.

@Composable
actual fun VideoSurface(
    player: VideoPlayerHandle?,
    active: Boolean,
    modifier: Modifier,
    resizeMode: VideoResizeMode,
    pipSurface: Boolean,
) {
    Box(modifier.background(Color.Black))
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit

@Composable
actual fun VideoFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}
