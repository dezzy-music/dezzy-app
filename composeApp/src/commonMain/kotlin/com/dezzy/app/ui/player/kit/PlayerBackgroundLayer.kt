/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.kit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/**
 * Fondo animado «Fondo del reproductor» (Degradado, Resplandor, Coloreado, Personalizado).
 * Con el estilo DEFAULT no dibuja nada. Lo usan el reproductor Clásico y las letras.
 */
@Composable
fun PlayerBackgroundLayer(
    thumbnailUrl: String?,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val (playerCustomImageUri) = rememberPreference(PlayerCustomImageUriKey, "")
    val (playerCustomBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (playerCustomContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (playerCustomBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurRadius) = rememberPreference(BlurRadiusKey, 36f)
    val blurScale = (blurRadius / 36f).coerceIn(0f, 3f)
    val platformContext = LocalPlatformContext.current

    if (playerBackground == PlayerBackgroundStyle.DEFAULT) return

    Box(modifier.fillMaxSize().clipToBounds()) {
        val infiniteTransition = rememberInfiniteTransition(label = "playerBgRotation")

        val anchorRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -360f,
            animationSpec = infiniteRepeatable(
                animation = tween(80000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "anchorRotation",
        )
        val fastRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(40000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "fastRotation",
        )
        val slowRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(60000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "slowRotation",
        )

        val isGradientBased = playerBackground == PlayerBackgroundStyle.GRADIENT ||
            playerBackground == PlayerBackgroundStyle.COLORING ||
            playerBackground == PlayerBackgroundStyle.GLOW

        val imageUrl = when (playerBackground) {
            PlayerBackgroundStyle.CUSTOM -> playerCustomImageUri
            else -> thumbnailUrl
        }

        if (playerBackground == PlayerBackgroundStyle.CUSTOM && playerCustomImageUri.isNotBlank()) {
            // La imagen propia se ve tal cual: a pantalla completa, sin giros y sólo con el
            // desenfoque, la atenuación y el contraste que se eligen en Ajustes.
            CustomImageBackground(
                uri = playerCustomImageUri,
                blur = if (disableBlur) 0f else playerCustomBlur,
                contrast = playerCustomContrast,
                brightness = playerCustomBrightness,
            )
        } else if (isGradientBased || imageUrl.isNullOrBlank()) {
            val colors = if (gradientColors.size >= 2) {
                gradientColors
            } else if (gradientColors.isNotEmpty()) {
                listOf(gradientColors[0], gradientColors[0])
            } else {
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.7f
                        scaleY = 1.7f
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .playerBlur(100.dp, disableBlur, blurScale)
                        .graphicsLayer { rotationZ = anchorRotation }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(colors[0].copy(alpha = 0.8f), Color.Transparent),
                                radius = 800f,
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .playerBlur(120.dp, disableBlur, blurScale)
                        .graphicsLayer {
                            rotationZ = fastRotation
                            alpha = 0.6f
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf((colors.getOrNull(1) ?: colors[0]).copy(alpha = 0.8f), Color.Transparent),
                                radius = 600f,
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .playerBlur(120.dp, disableBlur, blurScale)
                        .graphicsLayer {
                            rotationZ = slowRotation
                            alpha = 0.5f
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(colors.last().copy(alpha = 0.8f), Color.Transparent),
                                radius = 700f,
                            ),
                        ),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))),
                )
            }
        } else {
            AnimatedContent(
                targetState = imageUrl,
                transitionSpec = { fadeIn(tween(1500)).togetherWith(fadeOut(tween(1500))) },
                label = "playerBgImage",
            ) { targetUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.7f
                            scaleY = 1.7f
                        },
                ) {
                    val colorFilter = if (playerBackground == PlayerBackgroundStyle.CUSTOM) {
                        val contrastVal = playerCustomContrast
                        val brightnessVal = playerCustomBrightness
                        val t = (1f - contrastVal) * 128f + (brightnessVal - 1f) * 255f
                        val matrixArray = floatArrayOf(
                            contrastVal, 0f, 0f, 0f, t,
                            0f, contrastVal, 0f, 0f, t,
                            0f, 0f, contrastVal, 0f, t,
                            0f, 0f, 0f, 1f, 0f,
                        )
                        ColorFilter.colorMatrix(ColorMatrix(matrixArray))
                    } else {
                        val matrix = remember {
                            val m = ColorMatrix()
                            m.setToSaturation(1.8f)
                            m
                        }
                        ColorFilter.colorMatrix(matrix)
                    }

                    val baseBlur = 100.dp + (if (playerBackground == PlayerBackgroundStyle.CUSTOM) playerCustomBlur.dp else 0.dp)

                    AsyncImage(
                        model = ImageRequest.Builder(platformContext).data(targetUrl).size(128, 128).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .playerBlur(baseBlur, disableBlur, blurScale)
                            .graphicsLayer { rotationZ = anchorRotation },
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(platformContext).data(targetUrl).size(128, 128).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        alignment = Alignment.TopStart,
                        modifier = Modifier
                            .fillMaxSize()
                            .playerBlur(baseBlur + 20.dp, disableBlur, blurScale)
                            .graphicsLayer {
                                rotationZ = fastRotation
                                alpha = 0.6f
                            },
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(platformContext).data(targetUrl).size(128, 128).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        alignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .fillMaxSize()
                            .playerBlur(baseBlur + 20.dp, disableBlur, blurScale)
                            .graphicsLayer {
                                rotationZ = slowRotation
                                alpha = 0.5f
                            },
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))),
                    )
                }
            }
        }
    }
}

/** Blur de fondo que respeta "Desactivar desenfoque" y la escala de intensidad. */
private fun Modifier.playerBlur(radius: Dp, disabled: Boolean, scale: Float): Modifier =
    if (disabled || scale <= 0f) this else blur(radius * scale)

/**
 * Imagen propia del «Fondo personalizado», a pantalla completa. [blur] en dp (0 = nítida),
 * [brightness] 1 = sin atenuar y [contrast] 1 = sin cambios. También la usa la vista previa.
 */
@Composable
fun CustomImageBackground(
    uri: String,
    blur: Float,
    contrast: Float,
    brightness: Float,
    modifier: Modifier = Modifier,
) {
    val t = (1f - contrast) * 128f
    val filter = remember(contrast) {
        if (contrast == 1f) null
        else ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    Box(modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            modifier = Modifier
                .fillMaxSize()
                // Un poco más grande para que el desenfoque no deje bordes claros.
                .then(if (blur > 0f) Modifier.graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }.blur(blur.dp) else Modifier),
        )
        // Atenuación: negro encima según el brillo elegido (1 = nada).
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.85f))))
        // Un velo suave abajo para que los controles y la letra se lean siempre.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))),
        )
    }
}
