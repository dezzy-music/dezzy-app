/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.personalize

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dezzy.app.R

@Composable
actual fun rememberMediaPicker(onPicked: (uri: String, mime: String?) -> Unit): (MediaKind) -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback(uri.toString(), runCatching { context.contentResolver.getType(uri) }.getOrNull())
    }
    return remember(launcher) {
        { kind ->
            launcher.launch(
                PickVisualMediaRequest(
                    when (kind) {
                        MediaKind.IMAGE -> ActivityResultContracts.PickVisualMedia.ImageOnly
                        MediaKind.VIDEO -> ActivityResultContracts.PickVisualMedia.VideoOnly
                        MediaKind.IMAGE_OR_VIDEO -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    },
                ),
            )
        }
    }
}

/** Mismo layout que el videoclip: PlayerView con TextureView y sin controles; sin audio ni foco de audio. */
@OptIn(UnstableApi::class)
@Composable
actual fun WallpaperVideo(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            volume = 0f
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false,
            )
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    // Pausa al salir de la app y sigue al volver.
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val target = remember(uri) { Uri.parse(uri) }
    AndroidView(
        factory = { ctx ->
            val exo: Player = player
            (LayoutInflater.from(ctx).inflate(R.layout.video_player_view, null, false) as PlayerView).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                this.player = exo
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            if (player.currentMediaItem?.localConfiguration?.uri != target) {
                player.setMediaItem(MediaItem.fromUri(target))
                player.prepare()
            }
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
