/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.social

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Visor de fotos a pantalla completa: pellizco y doble toque para ampliar, arrastre vertical para cerrar. */
@Composable
internal fun P2PPhotoViewer(urls: List<String>, startIndex: Int = 0, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.lastIndex), pageCount = { urls.size })
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val fade = (1f - abs(dragY.value) / 900f).coerceIn(0.2f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = dragY.value }
                    .pointerInput(zoomed) {
                        if (!zoomed) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (abs(total) > 260f) onDismiss() else scope.launch { dragY.animateTo(0f) }
                                    total = 0f
                                },
                                onDragCancel = { total = 0f; scope.launch { dragY.animateTo(0f) } },
                            ) { _, delta ->
                                total += delta
                                scope.launch { dragY.snapTo(dragY.value + delta) }
                            }
                        }
                    },
            ) { page ->
                ZoomablePhoto(
                    url = urls[page],
                    onZoomChange = { if (page == pager.currentPage) zoomed = it },
                    onTap = { chrome = !chrome },
                )
            }
            AnimatedVisibility(chrome, Modifier.align(Alignment.TopStart)) {
                IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White)
                }
            }
            if (urls.size > 1) {
                AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter)) {
                    Text(
                        "${pager.currentPage + 1} / ${urls.size}",
                        color = Color.White,
                        modifier = Modifier.statusBarsPadding().padding(top = 18.dp),
                    )
                }
            }
        }
    }
    LaunchedEffect(pager.currentPage) { zoomed = false }
}

@Composable
private fun ZoomablePhoto(url: String, onZoomChange: (Boolean) -> Unit, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        onZoomChange(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan * scale else Offset.Zero
                    onZoomChange(scale > 1f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
