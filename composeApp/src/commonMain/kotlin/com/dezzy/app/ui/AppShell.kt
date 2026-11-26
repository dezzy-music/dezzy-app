/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.navigation.Navigator
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.navigation.Tab
import com.dezzy.app.ui.player.ExpandedPlayer
import com.dezzy.app.ui.player.MiniPlayer
import com.dezzy.app.ui.screens.TabContent

/**
 * Armazón de la app (fase 1 A): de borde a borde, barra inferior con indicador en píldora (riel
 * lateral desde 600 dp), mini reproductor encima de la barra y reproductor expandido encima de todo.
 */
@Composable
fun AppShell(player: PlayerController, client: YouTubeMusicClient) {
    val navigator = remember { Navigator() }
    val state by player.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    PlatformBackHandler(enabled = expanded) { expanded = false }
    PlatformBackHandler(enabled = !expanded && navigator.canGoBack) { navigator.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                NavigationRail(containerColor = colors.surfaceContainer) {
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = navigator.tab == tab,
                            onClick = { navigator.select(tab) },
                            icon = { Icon(if (navigator.tab == tab) tab.selectedIcon else tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TabContent(navigator, player, client, state)
                    // Velo bajo la barra de estado para que el contenido no choque con los iconos.
                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.surface.copy(alpha = 0.92f)))
                }
                MiniPlayer(state, player, onExpand = { expanded = true })
                if (wide) {
                    Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
                } else {
                    NavigationBar(containerColor = colors.surfaceContainer) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = navigator.tab == tab,
                                onClick = { navigator.select(tab) },
                                icon = { Icon(if (navigator.tab == tab) tab.selectedIcon else tab.icon, null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded && state.queue.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ExpandedPlayer(state, player, onCollapse = { expanded = false })
        }
    }
}
