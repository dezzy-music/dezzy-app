/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dezzy.app.ui.navigation.Tab
import com.dezzy.app.ui.theme.dezzyStyledSurface
import com.dezzy.app.ui.theme.isCustomStyledTheme

/** Alto de la píldora flotante (sin insets), para que el armazón calcule el hueco inferior. */
val FloatingNavBarHeight = 72.dp

/** Separación de la píldora con los bordes laterales e inferior. */
val FloatingNavBarMargin = 12.dp

/**
 * Barra de navegación flotante (fase 1 A): píldora separada de los bordes, encima del contenido, con el
 * indicador en píldora de 64x32 del boceto. Respeta el inset inferior del sistema y adopta el estilo del
 * tema (NORMAL: superficie tonal con sombra; LIQUID_GLASS y NOTHING: superficie de [dezzyStyledSurface]).
 */
@Composable
fun FloatingNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(36.dp)
    val styled = isCustomStyledTheme()
    val surface = if (styled) {
        Modifier.dezzyStyledSurface(shape = shape, prominent = false)
    } else {
        Modifier.shadow(6.dp, shape).clip(shape).background(colors.surfaceContainerHigh)
    }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = FloatingNavBarMargin, vertical = FloatingNavBarMargin),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(FloatingNavBarHeight)
                .then(surface)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            tint = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
