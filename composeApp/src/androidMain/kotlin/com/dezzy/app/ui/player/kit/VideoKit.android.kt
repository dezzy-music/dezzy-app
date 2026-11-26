/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dezzy.app.R
import com.dezzy.app.feature.video.VideoFullscreenOrientation
import com.dezzy.app.feature.video.VideoPipState
import com.dezzy.app.feature.video.VideoPlayerHandle
import com.dezzy.app.feature.video.VideoResizeMode

@OptIn(UnstableApi::class)
private fun VideoResizeMode.toPlayerViewMode(): Int = when (this) {
    VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    VideoResizeMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
}

@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(
    player: VideoPlayerHandle?,
    active: Boolean,
    modifier: Modifier,
    resizeMode: VideoResizeMode,
    pipSurface: Boolean,
) {
    val inPip by VideoPipState.inPip.collectAsState()
    val attached = active && (pipSurface || !inPip)
    val mode = resizeMode.toPlayerViewMode()
    AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.video_player_view, null, false) as PlayerView
        },
        update = { view ->
            val target = if (attached) player?.platformPlayer as? Player else null
            if (view.player !== target) view.player = target
            if (view.resizeMode != mode) view.resizeMode = mode
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
actual fun VideoFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Gira la app de verdad a horizontal (sensor) mientras dure la pantalla completa y
    // devuelve la orientación anterior al salir. Si el sistema no lo permite (p. ej. ventana
    // múltiple), se mantiene el giro de 90° de la capa como respaldo.
    val activityContext = LocalContext.current
    DisposableEffect(activityContext) {
        val activity = activityContext.findHostActivity()
        // Primero se avisa a los reproductores (Spotify/Deezer) para que su bloqueo a vertical
        // no pise el giro.
        VideoFullscreenOrientation.active.value = true
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            VideoFullscreenOrientation.active.value = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // Si algo vuelve a pedir vertical mientras dura la pantalla completa, se reafirma horizontal.
    val currentOrientation = LocalConfiguration.current.orientation
    LaunchedEffect(currentOrientation) {
        val activity = activityContext.findHostActivity() ?: return@LaunchedEffect
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window?.setDimAmount(0f)
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        // Tras girar, la ventana del diálogo tiene que volver a ocupar toda la pantalla.
        val dialogConfiguration = LocalConfiguration.current
        LaunchedEffect(window, dialogConfiguration.orientation, dialogConfiguration.screenWidthDp) {
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
        // El giro se decide por la forma REAL de la ventana: si el sistema no ha girado, la
        // capa se gira 90° y el vídeo se ve horizontal igualmente.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val windowIsPortrait = maxHeight > maxWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotateQuarterTurn(enabled = windowIsPortrait),
            ) {
                content()
            }
        }
    }
}
