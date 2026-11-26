/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.dezzy.app.feature.video.VideoPlayerHandle
import com.dezzy.app.feature.video.VideoResizeMode

/**
 * Donde se pinta el videoclip. Sólo se engancha cuando [active]; en imagen en imagen sólo la
 * superficie de la capa PiP ([pipSurface]) lo está, para que el vídeo no salte entre vistas.
 */
@Composable
expect fun VideoSurface(
    player: VideoPlayerHandle?,
    active: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    pipSurface: Boolean = false,
)

/** Mantiene la pantalla encendida mientras [enabled]. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/**
 * Pantalla completa del vídeo: gira la app a horizontal mientras dura, oculta las barras del
 * sistema y, si la ventana sigue en vertical, gira la capa 90° para verlo horizontal igualmente.
 */
@Composable
expect fun VideoFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

/**
 * Gira el contenido 90° y le intercambia ancho y alto: la pantalla completa en horizontal sin
 * rotar la actividad (rotarla la recrearía y los estilos Spotify/Deezer fuerzan el vertical).
 */
fun Modifier.rotateQuarterTurn(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    ),
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = (placeable.height - placeable.width) / 2,
                        y = (placeable.width - placeable.height) / 2,
                    )
                }
            }
            .graphicsLayer { rotationZ = 90f }
    }
