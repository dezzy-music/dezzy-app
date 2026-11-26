/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.canvas

import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dezzy.app.R
import com.dezzy.app.feature.video.MusicVideoCache

@OptIn(UnstableApi::class)
@Composable
actual fun CanvasVideo(
    url: String,
    loopStartMs: Long,
    loopLengthMs: Long,
    playing: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var firstFrame by remember(url, loopStartMs, loopLengthMs) { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MusicVideoCache.dataSourceFactory(context)))
            .build()
            .apply {
                // Mudo del todo: sin pista de audio, volumen 0 y sin pedir el foco de audio.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false,
                )
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playWhenReady = false
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, url, loopStartMs, loopLengthMs) {
        val uri = url.trim()
        val isHls = uri.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            uri.contains("/manifest/hls", ignoreCase = true)
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(loopStartMs.coerceAtLeast(0L))
            .setEndPositionMs(if (loopLengthMs > 0) loopStartMs.coerceAtLeast(0L) + loopLengthMs else C.TIME_END_OF_SOURCE)
            .build()
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setClippingConfiguration(clip)
            .build()
        player.stop()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = playing
    }

    LaunchedEffect(player, playing) {
        if (player.playWhenReady != playing) player.playWhenReady = playing
    }

    val alpha by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasVideoAlpha",
    )

    val exo: Player = player
    AndroidView(
        factory = { ctx ->
            // Mismo layout que el videoclip: PlayerView con TextureView y sin controles.
            (LayoutInflater.from(ctx).inflate(R.layout.video_player_view, null, false) as PlayerView).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                this.player = exo
            }
        },
        update = { view -> if (view.player !== exo) view.player = exo },
        onRelease = { it.player = null },
        modifier = modifier.alpha(alpha),
    )
}
