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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val circle = CircleShape
    val searchOn = selected == Tab.SEARCH
    val searchSurface = if (styled) {
        Modifier.dezzyStyledSurface(shape = circle, prominent = true)
    } else {
        Modifier.shadow(6.dp, circle).clip(circle)
            .background(if (searchOn) colors.primary else colors.primaryContainer)
    }
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = FloatingNavBarMargin, vertical = FloatingNavBarMargin),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dock compacto centrado, sólo iconos (la búsqueda va en su botón circular).
        Row(
            Modifier
                .height(DockHeight)
                .then(surface)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.filter { it != Tab.SEARCH }.forEach { tab ->
                val on = tab == selected
                Box(
                    Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) colors.secondaryContainer else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (on) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                        tint = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    )
                }
            }
        }
        // Botón circular de búsqueda destacado.
        Box(
            Modifier
                .size(DockHeight)
                .then(searchSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSelect(Tab.SEARCH) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tab.SEARCH.selectedIcon,
                contentDescription = Tab.SEARCH.label,
                tint = if (!styled && searchOn) colors.onPrimary else if (styled) colors.onSurface else colors.onPrimaryContainer,
            )
        }
    }
}

private val DockHeight = 56.dp

/**
 * Barra completa (boceto A): NavigationBar M3 de ancho completo pegada abajo, indicador en píldora,
 * icono + etiqueta; respeta los insets del sistema.
 */
@Composable
fun FullNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            NavigationBarItem(
                selected = on,
                onClick = { onSelect(tab) },
                icon = { Icon(if (on) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** Barra delgada: ancho completo, sólo iconos con indicador en píldora, más baja que la completa. */
@Composable
fun SlimNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(52.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Box(
                Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) colors.secondaryContainer else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (on) tab.selectedIcon else tab.icon,
                    contentDescription = tab.label,
                    tint = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}
