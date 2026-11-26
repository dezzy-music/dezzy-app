/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dezzy.app.feature.player.PlaybackState
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.ui.navigation.Navigator
import com.dezzy.app.ui.navigation.Screen
import com.dezzy.app.ui.navigation.SettingsPage
import com.dezzy.app.ui.screens.settings.AboutScreen
import com.dezzy.app.ui.screens.settings.AccountsSettingsScreen
import com.dezzy.app.ui.screens.settings.AppearanceSettingsScreen
import com.dezzy.app.ui.screens.settings.ContentSettingsScreen
import com.dezzy.app.ui.screens.settings.PlayerSettingsScreen
import com.dezzy.app.ui.screens.settings.PrivacySettingsScreen
import com.dezzy.app.ui.screens.settings.SettingsScreen
import com.dezzy.app.ui.screens.settings.SpotifyImportScreen
import com.dezzy.app.ui.screens.settings.ScrobbleSettingsScreen
import com.dezzy.app.ui.screens.settings.DiscordSettingsScreen
import com.dezzy.app.ui.screens.settings.LyricsSettingsScreen
import com.dezzy.app.ui.screens.settings.SpotifyLoginScreen
import com.dezzy.app.ui.screens.settings.SpotifySettingsScreen
import com.dezzy.app.ui.screens.settings.StorageSettingsScreen
import com.dezzy.app.ui.screens.settings.YouTubeLoginScreen
import com.dezzy.app.ui.navigation.Tab
import org.koin.compose.koinInject

/** Contenido de la pestaña activa o de la pantalla apilada encima. */
@Composable
fun TabContent(
    navigator: Navigator,
    player: PlayerController,
    client: YouTubeMusicClient,
    state: PlaybackState,
    snackbar: SnackbarHostState,
) {
    val ctx = ScreenContext(
        navigator = navigator,
        player = player,
        client = client,
        repository = koinInject(),
        prefs = koinInject(),
        snackbar = snackbar,
        scope = rememberCoroutineScope(),
        playingId = state.queue.current?.track?.videoId,
    )
    when (val top = navigator.top) {
        is Screen.Album -> AlbumScreen(top.id, ctx)
        is Screen.Artist -> ArtistScreen(top.id, ctx)
        is Screen.Playlist -> PlaylistScreen(top.id, ctx)
        is Screen.LocalPlaylist -> LocalPlaylistScreen(top.id, ctx)
        Screen.Liked -> LikedScreen(ctx)
        Screen.History -> HistoryScreen(ctx)
        is Screen.Search -> SearchScreen(ctx, top.query)
        Screen.MoodsAndGenres -> MoodsAndGenresScreen(ctx)
        is Screen.Browse -> BrowseScreen(top, ctx)
        is Screen.Pending -> PendingScreen(top, ctx)
        is Screen.Settings -> when (top.page) {
            SettingsPage.MAIN -> SettingsScreen(ctx)
            SettingsPage.APPEARANCE -> AppearanceSettingsScreen(ctx)
            SettingsPage.PLAYER -> PlayerSettingsScreen(ctx)
            SettingsPage.PRIVACY -> PrivacySettingsScreen(ctx)
            SettingsPage.STORAGE -> StorageSettingsScreen(ctx)
            SettingsPage.ABOUT -> AboutScreen(ctx)
            SettingsPage.ACCOUNTS -> AccountsSettingsScreen(ctx)
            SettingsPage.CONTENT -> ContentSettingsScreen(ctx)
            SettingsPage.YOUTUBE_LOGIN -> YouTubeLoginScreen(ctx)
            SettingsPage.SPOTIFY -> SpotifySettingsScreen(ctx)
            SettingsPage.SPOTIFY_LOGIN -> SpotifyLoginScreen(ctx)
            SettingsPage.SPOTIFY_IMPORT -> SpotifyImportScreen(ctx)
            SettingsPage.SCROBBLING -> ScrobbleSettingsScreen(ctx)
            SettingsPage.DISCORD -> DiscordSettingsScreen(ctx)
            SettingsPage.LYRICS -> LyricsSettingsScreen(ctx)
        }
        Screen.AccountPlaylists -> AccountPlaylistsScreen(ctx)
        null -> when (navigator.tab) {
            Tab.HOME -> HomeScreen(ctx, state)
            Tab.SEARCH -> SearchScreen(ctx, "")
            Tab.LIBRARY -> LibraryScreen(ctx)
            Tab.TOGETHER -> PendingScreen(Screen.Pending("Juntos", "9 (conectado)"), ctx, root = true)
        }
    }
}

/** Una función que llega en una fase posterior: lo dice claro, sin pantalla vacía. */
@Composable
fun PendingScreen(screen: Screen.Pending, ctx: ScreenContext, root: Boolean = false) {
    Column(Modifier.fillMaxSize()) {
        TopSpacer()
        ScreenTitle(screen.title, onBack = if (root) null else ({ ctx.navigator.back() }))
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "«${screen.title}» llega en la fase ${screen.phase} de la migración.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
