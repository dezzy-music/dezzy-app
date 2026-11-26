/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.styles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.dezzy.app.ui.components.glass.DezzyButton
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.PlayerDesignStyle
import com.dezzy.app.ui.theme.NothingBoneWhite
import com.dezzy.app.ui.theme.NothingCharcoal
import com.dezzy.app.ui.theme.NothingDotRing
import com.dezzy.app.ui.theme.NothingVermilion
import com.dezzy.app.ui.theme.dezzyStyledSurface
import com.dezzy.app.ui.theme.isCustomStyledTheme
import com.dezzy.app.ui.theme.isNothingTheme
import com.dezzy.app.ui.theme.nothingDotFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class PlayerStyleMeta(
    val gradientStart: Color,
    val gradientEnd: Color,
    val accent: Color,
    val circularArt: Boolean = false,
    val isIpod: Boolean = false,
)

private fun metaForStyle(style: PlayerDesignStyle): PlayerStyleMeta =
    when (style) {
        PlayerDesignStyle.V1 -> PlayerStyleMeta(Color(0xFF4338CA), Color(0xFF14122E), Color(0xFFA5B4FC))
        PlayerDesignStyle.V2 -> PlayerStyleMeta(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF38BDF8), circularArt = true)
        PlayerDesignStyle.V3 -> PlayerStyleMeta(Color(0xFF334155), Color(0xFF0F172A), Color(0xFFE2E8F0))
        PlayerDesignStyle.V4 -> PlayerStyleMeta(Color(0xFF701A75), Color(0xFF09090B), Color(0xFFF472B6))
        PlayerDesignStyle.V5 -> PlayerStyleMeta(Color(0xFF7C2D12), Color(0xFF140D0B), Color(0xFFFDBA74))
        PlayerDesignStyle.V6 -> PlayerStyleMeta(Color(0xFF065F46), Color(0xFF042F2E), Color(0xFF34D399))
        PlayerDesignStyle.V7 -> PlayerStyleMeta(Color(0xFF7E22CE), Color(0xFF18181B), Color(0xFFE879F9))
        PlayerDesignStyle.IPOD -> PlayerStyleMeta(Color(0xFFD5DCE4), Color(0xFF64748B), Color(0xFF1E293B), isIpod = true)
        PlayerDesignStyle.SPOTIFY -> PlayerStyleMeta(Color(0xFF14532D), Color(0xFF09090B), Color(0xFF22C55E))
        PlayerDesignStyle.CAR -> PlayerStyleMeta(Color(0xFF991B1B), Color(0xFF09090B), Color(0xFFEF4444))
        PlayerDesignStyle.APPLE_MUSIC -> PlayerStyleMeta(Color(0xFFBE123C), Color(0xFF4C0519), Color(0xFFFDA4AF))
        PlayerDesignStyle.LIVE_MESH -> PlayerStyleMeta(Color(0xFF6D28D9), Color(0xFF0F766E), Color(0xFF34D399))
        PlayerDesignStyle.AURORA -> PlayerStyleMeta(Color(0xFF0D9488), Color(0xFF312E81), Color(0xFF5EEAD4), circularArt = true)
    }

@Composable
internal fun playerDesignStyleLabel(style: PlayerDesignStyle): String =
    when (style) {
        PlayerDesignStyle.V1 -> "Clásico"
        PlayerDesignStyle.V2 -> "Moderno"
        PlayerDesignStyle.V3 -> "Mínimo"
        PlayerDesignStyle.V4 -> "Cinemático"
        PlayerDesignStyle.V5 -> "Dezzy"
        PlayerDesignStyle.V6 -> "Expresivo"
        PlayerDesignStyle.V7 -> "Deezer"
        PlayerDesignStyle.IPOD -> "iPod"
        PlayerDesignStyle.SPOTIFY -> "Spotify"
        PlayerDesignStyle.CAR -> "Car Mode"
        PlayerDesignStyle.APPLE_MUSIC -> "Apple Music"
        PlayerDesignStyle.LIVE_MESH -> "Dezzy Style"
        PlayerDesignStyle.AURORA -> "Aurora"
    }

/**
 * Interactive 3D Coverflow player-style deck without bottom pill:
 * users glide sideways through visual previews rendered with their currently playing song
 * (or a Michael Jackson "Billie Jean" showcase when playback is idle), and an Apply button
 * appears whenever the centered card differs from the active player style.
 */
@Composable
fun PlayerStyleCoverflowSelector(
    selectedStyle: PlayerDesignStyle,
    onApplyStyle: (PlayerDesignStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val styles = PlayerDesignStyle.entries
    val currentAppliedIndex = styles.indexOf(selectedStyle).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = currentAppliedIndex,
        pageCount = { styles.size },
    )
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    val playerConnection = LocalPlayerConnection.current
    val liveMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value

    val previewTitle = liveMetadata?.title?.takeIf { it.isNotBlank() } ?: "Billie Jean"
    val previewArtist = liveMetadata?.artists
        ?.joinToString(", ") { it.name }
        ?.takeIf { it.isNotBlank() }
        ?: "Michael Jackson"
    val previewCoverUrl = liveMetadata?.thumbnailUrl

    // Sync pager if active preference changes externally
    LaunchedEffect(selectedStyle) {
        val target = styles.indexOf(selectedStyle)
        if (target >= 0 && pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Subtle haptic detent as each style snaps into center
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
    }

    val focusedStyle = styles[pagerState.currentPage.coerceIn(styles.indices)]
    val needsApply = focusedStyle != selectedStyle
    val isNothing = isNothingTheme()
    val isStyled = isCustomStyledTheme()

    val outerShape = RoundedCornerShape(24.dp)
    val outerBg = when {
        isNothing -> NothingCharcoal
        isStyled -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(outerShape)
            .background(outerBg)
            .then(
                if (isStyled) {
                    Modifier.dezzyStyledSurface(
                        shape = outerShape,
                        prominent = false,
                        useBackdrop = true,
                        enableShadow = false,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 14.dp),
    ) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Estilo del reproductor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$previewTitle · $previewArtist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${pagerState.currentPage + 1}/${styles.size}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = if (isNothing) nothingDotFontFamily() else MaterialTheme.typography.labelMedium.fontFamily,
                ),
                color = if (isNothing) NothingVermilion else MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 3D Winged Coverflow Pager
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(256.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = 178.dp
            val sidePadding = ((maxWidth - cardWidth) / 2f).coerceAtLeast(16.dp)

            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = sidePadding),
                beyondViewportPageCount = 3,
                pageSpacing = (-28).dp,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = styles[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOff = abs(pageOffset)
                val clamped = absOff.coerceIn(0f, 2f)

                val cardScale = lerp(1f, 0.78f, (clamped / 1.6f).coerceIn(0f, 1f))
                val cardAlpha = lerp(1f, 0.62f, (clamped / 2f).coerceIn(0f, 1f))
                val cardRotY = (pageOffset * -24f).coerceIn(-36f, 36f)
                val shiftX = -pageOffset * 18f * density

                CoverflowPlayerStyleCard(
                    style = item,
                    styleLabel = playerDesignStyleLabel(item),
                    trackTitle = previewTitle,
                    trackArtist = previewArtist,
                    coverUrl = previewCoverUrl,
                    isActive = item == selectedStyle,
                    isCentered = page == pagerState.currentPage,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    },
                    modifier = Modifier
                        .width(cardWidth)
                        .height(242.dp)
                        .zIndex(20f - clamped)
                        .graphicsLayer {
                            scaleX = cardScale
                            scaleY = cardScale
                            rotationY = cardRotY
                            translationX = shiftX
                            alpha = cardAlpha
                            cameraDistance = 14f * density
                        },
                )
            }
        }

        // Dynamic Apply CTA: visible only when centered card != saved style
        AnimatedVisibility(
            visible = needsApply,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
            ) {
                DezzyButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApplyStyle(focusedStyle)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNothing) NothingVermilion else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Aplicar estilo · ${playerDesignStyleLabel(focusedStyle)}",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverflowPlayerStyleCard(
    style: PlayerDesignStyle,
    styleLabel: String,
    trackTitle: String,
    trackArtist: String,
    coverUrl: String?,
    isActive: Boolean,
    isCentered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = metaForStyle(style)
    val cardShape = RoundedCornerShape(20.dp)
    val rimAlpha = if (isCentered) 0.56f else 0.28f
    val isNothing = isNothingTheme()

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(meta.gradientStart, meta.gradientEnd)))
            .border(
                width = if (isCentered) 1.4.dp else 1.dp,
                color = if (isActive && isNothing) NothingVermilion else Color.White.copy(alpha = rimAlpha),
                shape = cardShape,
            )
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Visual Player Preview Stage (~72% height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                // Top active pill
                if (isActive) {
                    Surface(
                        color = if (isNothing) NothingVermilion else Color.Black.copy(alpha = 0.48f),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Text(
                            text = "En uso",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }

                if (meta.isIpod) {
                    // Authentic iPod Classic miniature: inset screen + Click Wheel
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize().padding(top = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0E131B))
                                .border(1.dp, Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                                TrackArtworkBox(
                                    coverUrl = coverUrl,
                                    circular = false,
                                    accent = Color(0xFF38BDF8),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(trackTitle, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(trackArtist, fontSize = 8.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3F4F6))
                                .border(1.dp, Color(0xFF94A3B8), CircleShape),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFCBD5E1)),
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (meta.circularArt && isNothing) {
                                NothingDotRing(isPlaying = false, ringCount = 3, modifier = Modifier.size(106.dp))
                            }
                            TrackArtworkBox(
                                coverUrl = coverUrl,
                                circular = meta.circularArt,
                                accent = meta.accent,
                                modifier = Modifier
                                    .size(if (style == PlayerDesignStyle.V5) 98.dp else 88.dp)
                                    .then(
                                        if (style == PlayerDesignStyle.V4) {
                                            Modifier.border(1.5.dp, meta.accent, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // Mini scrub line + controls hint
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(meta.accent),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp),
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(if (style == PlayerDesignStyle.CAR) 26.dp else 22.dp)
                                    .clip(CircleShape)
                                    .background(if (style == PlayerDesignStyle.SPOTIFY) meta.accent else Color.White),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }

            // Frosted footer matching the Pinterest pin bottom strip
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xA6111014))
                    .border(
                        width = 0.6.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    text = styleLabel,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = if (isNothing) nothingDotFontFamily() else MaterialTheme.typography.titleSmall.fontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = NothingBoneWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$trackTitle · $trackArtist",
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrackArtworkBox(
    coverUrl: String?,
    circular: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val shape = if (circular) CircleShape else RoundedCornerShape(14.dp)

    if (!coverUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(shape)
                .border(0.8.dp, Color.White.copy(alpha = 0.3f), shape),
        )
    } else {
        // Michael Jackson "Billie Jean" Thriller-era iconic fedora & spotlight fallback art
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF3A1E08),
                            Color(0xFF150A04),
                            Color(0xFF070709),
                        ),
                    ),
                )
                .border(1.dp, Color(0x66FACC15), shape),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                val w = size.width
                val h = size.height
                // Golden stage spotlight halo
                drawCircle(
                    color = Color(0x38FACC15),
                    radius = w * 0.42f,
                    center = Offset(w * 0.5f, h * 0.46f),
                )
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = w * 0.42f,
                    center = Offset(w * 0.5f, h * 0.46f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                // Iconic MJ angled fedora silhouette
                val crown = Path().apply {
                    moveTo(w * 0.24f, h * 0.50f)
                    lineTo(w * 0.34f, h * 0.24f)
                    lineTo(w * 0.52f, h * 0.28f)
                    lineTo(w * 0.68f, h * 0.24f)
                    lineTo(w * 0.76f, h * 0.50f)
                    close()
                }
                drawPath(crown, color = Color(0xFFF4F4F0))
                // Wide slanted hat brim
                drawRoundRect(
                    color = Color(0xFFFACC15),
                    topLeft = Offset(w * 0.10f, h * 0.48f),
                    size = Size(w * 0.80f, h * 0.08f),
                    cornerRadius = CornerRadius(h * 0.04f, h * 0.04f),
                )
                // Subtle sequin glove / spotlight glint below
                drawCircle(color = Color(0xFFF4F4F0), radius = 2.5.dp.toPx(), center = Offset(w * 0.32f, h * 0.74f))
                drawCircle(color = Color(0xFFFACC15), radius = 2.dp.toPx(), center = Offset(w * 0.50f, h * 0.70f))
                drawCircle(color = Color(0xFFF4F4F0), radius = 2.5.dp.toPx(), center = Offset(w * 0.68f, h * 0.74f))
            }
        }
    }
}
