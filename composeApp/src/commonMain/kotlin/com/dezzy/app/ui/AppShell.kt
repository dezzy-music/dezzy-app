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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.dezzy.app.ui.components.FloatingNavBar
import com.dezzy.app.ui.components.FullNavBar
import com.dezzy.app.ui.components.NavBarStyle
import com.dezzy.app.ui.components.rememberNavBarStyle
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.navigation.Navigator
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.navigation.Tab
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.model.SessionProvider
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.ui.screens.WelcomeLoginDialog
import com.dezzy.app.feature.video.VideoPipState
import com.dezzy.app.ui.player.video.VideoPipLayer
import com.dezzy.app.ui.player.PlayerRouter
import com.dezzy.app.ui.player.kit.BottomSheetState
import com.dezzy.app.ui.player.kit.LocalPlayerConnection
import com.dezzy.app.ui.player.kit.NavController
import com.dezzy.app.ui.player.kit.PlayerConnection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
    val snackbar = remember { SnackbarHostState() }
    val navBarStyle = rememberNavBarStyle().value
    val repository: MusicRepository = koinInject()
    val scope = rememberCoroutineScope()
    val prefs: DezzyPreferences = koinInject()
    val accounts: AccountStore = koinInject()
    val sessions by accounts.sessions.collectAsState()
    // Mientras no se sabe, se asume «ya mostrada» para que no parpadee.
    val welcomeShown by prefs.welcomeLoginShown.collectAsState(true)
    val toast: (String) -> Unit = remember(snackbar, scope) {
        { message ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }
    val connectBridge: com.dezzy.app.feature.connect.ConnectPlaybackBridge = koinInject()
    androidx.compose.runtime.LaunchedEffect(navigator) {
        com.dezzy.app.feature.social.P2PDeepLinks.pending.collect { peer ->
            if (peer == null) return@collect
            com.dezzy.app.feature.social.P2PDeepLinks.pending.value = null
            navigator.select(com.dezzy.app.ui.navigation.Tab.TOGETHER)
            if (peer.isNotBlank()) navigator.push(com.dezzy.app.ui.navigation.Screen.P2PChat(peer))
        }
    }
    androidx.compose.runtime.LaunchedEffect(connectBridge) { connectBridge.toasts.collect { toast(it) } }
    val togetherSession: com.dezzy.app.feature.together.TogetherSession = koinInject()
    androidx.compose.runtime.LaunchedEffect(togetherSession) { togetherSession.toasts.collect { toast(it) } }
    androidx.compose.runtime.LaunchedEffect(navigator) {
        com.dezzy.app.feature.together.TogetherDeepLinks.pending.collect { code ->
            if (code == null) return@collect
            com.dezzy.app.feature.together.TogetherDeepLinks.pending.value = null
            navigator.push(com.dezzy.app.ui.navigation.Screen.Together(code.ifBlank { null }))
        }
    }
    val songMods: com.dezzy.app.feature.personalize.SongMods = koinInject()
    val connection = remember(player, client, repository) {
        PlayerConnection(player, repository, client, scope, toast, songMods.all) { title, phase ->
            expanded = false
            navigator.push(Screen.Pending(title, phase))
        }
    }
    val sheet = remember { BottomSheetState { expanded = false } }
    val navController = remember(navigator, client) { NavController(navigator, client, scope, sheet, toast) }

    PlatformBackHandler(enabled = expanded) { expanded = false }
    PlatformBackHandler(enabled = !expanded && navigator.canGoBack) { navigator.back() }

    CompositionLocalProvider(LocalPlayerConnection provides connection) {
    val hasWallpaper = com.dezzy.app.ui.screens.personalize.rememberHasAppWallpaper()
    BoxWithConstraints(Modifier.fillMaxSize().background(if (hasWallpaper) androidx.compose.ui.graphics.Color.Transparent else colors.surface)) {
        com.dezzy.app.ui.screens.personalize.AppWallpaper(Modifier.fillMaxSize())
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
                val density = LocalDensity.current
                var bottomStackPx by remember { mutableIntStateOf(0) }
                val bottomStack = with(density) { bottomStackPx.toDp() }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // Padding inferior = alto de minirreproductor + píldora, para no tapar la última fila.
                    Box(Modifier.fillMaxSize().padding(bottom = bottomStack)) {
                        TabContent(navigator, player, client, state, snackbar)
                    }
                    // Velo bajo la barra de estado para que el contenido no choque con los iconos.
                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.surface.copy(alpha = 0.92f)))
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomStack))
                    // Pila inferior flotante: minirreproductor encima y píldora de navegación debajo.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bottomStackPx = it.height },
                    ) {
                        MiniPlayer(state, player, onExpand = { expanded = true })
                        if (wide) {
                            Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
                        } else {
                            if (navBarStyle == NavBarStyle.DOCK) {
                                FloatingNavBar(selected = navigator.tab, onSelect = { navigator.select(it) })
                            } else {
                                FullNavBar(selected = navigator.tab, onSelect = { navigator.select(it) })
                            }
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
            PlayerRouter(connection, state, sheet, navController)
        }
        if (expanded) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        // Ventana flotante (PiP) del modo vídeo: encima de todo.
        VideoPipLayer(connection, onEligibleChange = { VideoPipState.eligible.value = it })
        // Bienvenida de la primera vez: sólo si nunca se mostró y no hay sesión de YouTube.
        if (!welcomeShown && SessionProvider.YOUTUBE !in sessions) {
            WelcomeLoginDialog(
                onDismiss = { scope.launch { prefs.setWelcomeLoginShown(true) } },
                onYoutubeMusicLogin = {
                    scope.launch { prefs.setWelcomeLoginShown(true) }
                    navigator.push(Screen.Settings(SettingsPage.YOUTUBE_LOGIN))
                },
                onSpotifyLogin = {
                    scope.launch { prefs.setWelcomeLoginShown(true) }
                    navigator.push(Screen.Settings(SettingsPage.SPOTIFY_LOGIN))
                },
            )
        }
    }
    }
}
