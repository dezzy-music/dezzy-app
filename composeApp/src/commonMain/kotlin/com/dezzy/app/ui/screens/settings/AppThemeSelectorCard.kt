/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.ui.theme.AppInterfaceTheme
import com.dezzy.app.ui.theme.LiquidGlassIntensity
import com.dezzy.app.ui.theme.NothingBoneWhite
import com.dezzy.app.ui.theme.NothingCharcoal
import com.dezzy.app.ui.theme.NothingDotRing
import com.dezzy.app.ui.theme.NothingElevatedDark
import com.dezzy.app.ui.theme.NothingVermilion
import com.dezzy.app.ui.theme.dezzyStyledSurface
import com.dezzy.app.ui.theme.liquidGlassSurface
import com.dezzy.app.ui.theme.nothingDotFontFamily

/**
 * Selector visual de «Estilo de la interfaz» (Normal, Liquid Glass o Nothing) con vista previa
 * en vivo y selector de «Intensidad del cristal».
 */
@Composable
fun AppThemeSelectorCard(
    selectedTheme: AppInterfaceTheme,
    onSelectTheme: (AppInterfaceTheme) -> Unit,
    intensity: LiquidGlassIntensity,
    onSelectIntensity: (LiquidGlassIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLiquidGlass = selectedTheme == AppInterfaceTheme.LIQUID_GLASS
    val isStyled = selectedTheme != AppInterfaceTheme.NORMAL
    val cardModifier = if (isStyled) {
        modifier
            .fillMaxWidth()
            .dezzyStyledSurface(
                shape = RoundedCornerShape(24.dp),
                prominent = false,
                useBackdrop = true,
                enableShadow = false,
            )
    } else {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    }

    Box(modifier = cardModifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tema de la aplicación",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Alterna entre Normal (Material 3), LiquidGlass y Nothing OS 3.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemePreviewOption(
                    title = "Normal",
                    subtitle = "Material 3",
                    selected = selectedTheme == AppInterfaceTheme.NORMAL,
                    previewMode = AppThemePreviewMode.NORMAL,
                    onClick = { onSelectTheme(AppInterfaceTheme.NORMAL) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewOption(
                    title = "Liquid Glass",
                    subtitle = "Apple iOS 26",
                    selected = selectedTheme == AppInterfaceTheme.LIQUID_GLASS,
                    previewMode = AppThemePreviewMode.LIQUID_GLASS,
                    onClick = { onSelectTheme(AppInterfaceTheme.LIQUID_GLASS) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewOption(
                    title = "Nothing",
                    subtitle = "Glyph OLED",
                    selected = selectedTheme == AppInterfaceTheme.NOTHING,
                    previewMode = AppThemePreviewMode.NOTHING,
                    onClick = { onSelectTheme(AppInterfaceTheme.NOTHING) },
                    modifier = Modifier.weight(1f),
                )
            }

            AnimatedVisibility(visible = isLiquidGlass) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = "Intensidad del cristal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LiquidGlassIntensity.entries.forEach { preset ->
                            val active = intensity == preset
                            val label = when (preset) {
                                LiquidGlassIntensity.SUBTLE -> "Sutil"
                                LiquidGlassIntensity.PURE -> "Pura"
                                LiquidGlassIntensity.INTENSE -> "Intensa"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidGlassSurface(
                                        shape = CircleShape,
                                        prominent = active,
                                        useBackdrop = false,
                                        enableShadow = false,
                                    )
                                    .clickable { onSelectIntensity(preset) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AppThemePreviewMode {
    NORMAL,
    LIQUID_GLASS,
    NOTHING,
}

@Composable
private fun ThemePreviewOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    previewMode: AppThemePreviewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val accentBorder = if (previewMode == AppThemePreviewMode.NOTHING) NothingVermilion else MaterialTheme.colorScheme.primary
    val borderColor = if (selected) accentBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val borderWidth = if (selected) 2.dp else 1.dp

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                if (previewMode == AppThemePreviewMode.NOTHING) {
                    NothingCharcoal
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                }
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = modifier
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (previewMode) {
                AppThemePreviewMode.LIQUID_GLASS -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                    ),
                                ),
                            )
                            .padding(7.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 9.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.38f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 9.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .border(0.9.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                                .padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.48f)),
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.85f)),
                            )
                        }
                    }
                }
                AppThemePreviewMode.NOTHING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .padding(7.dp),
                    ) {
                        NothingDotRing(
                            isPlaying = false,
                            ringCount = 3,
                            modifier = Modifier
                                .size(30.dp)
                                .align(Alignment.TopStart),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Text(
                                text = "OS",
                                fontFamily = nothingDotFontFamily(),
                                fontSize = 8.sp,
                                color = NothingBoneWhite,
                            )
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(NothingVermilion),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(CircleShape)
                                .background(NothingElevatedDark)
                                .border(0.7.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(NothingBoneWhite),
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(NothingVermilion),
                            )
                        }
                    }
                }
                AppThemePreviewMode.NORMAL -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(7.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accentBorder,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
