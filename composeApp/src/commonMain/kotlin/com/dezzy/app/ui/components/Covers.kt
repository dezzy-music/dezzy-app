/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Pide al catálogo la carátula al tamaño que se va a pintar (las de YouTube Music aceptan
 * `=wN-hN` al final y las de YouTube `hqdefault`/`maxresdefault`).
 */
fun sizedThumbnail(url: String?, px: Int): String? {
    if (url == null) return null
    val size = px.coerceIn(60, 1200)
    return when {
        Regex("=w\\d+-h\\d+").containsMatchIn(url) -> url.replace(Regex("=w\\d+-h\\d+[^&]*"), "=w$size-h$size-l90-rj")
        url.contains("i.ytimg.com") && size > 480 -> url.replace(Regex("/(hq|mq|sd)default"), "/maxresdefault")
        else -> url
    }
}

/** Carátula con marcador mientras carga y nota musical si no hay imagen. */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    sizePx: Int = 240,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(shape).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = colors.onSurfaceVariant)
        } else {
            SubcomposeAsyncImage(
                model = sizedThumbnail(url, sizePx),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerBox(Modifier.fillMaxSize()) },
                error = { Icon(Icons.Rounded.MusicNote, null, tint = colors.onSurfaceVariant) },
            )
        }
    }
}

/** Esqueleto con brillo que avanza, para filas y tarjetas que aún cargan. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val shine = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(listOf(base, shine, base), start = Offset(x, 0f), end = Offset(x + 400f, 200f)),
        ),
    )
}
