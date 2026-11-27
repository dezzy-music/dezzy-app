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
import com.dezzy.app.ui.screens.downloads.DownloadsScreen
import com.dezzy.app.ui.screens.lossless.LosslessScreen
import com.dezzy.app.ui.screens.lossless.LosslessDownloadsScreen
import com.dezzy.app.ui.screens.settings.LosslessSettingsScreen
import com.dezzy.app.ui.screens.settings.EqualizerScreen

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
    androidx.compose.runtime.CompositionLocalProvider(com.dezzy.app.ui.components.LocalScreenContext provides ctx) {
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
            SettingsPage.LOSSLESS -> LosslessSettingsScreen(ctx)
            SettingsPage.EQUALIZER -> EqualizerScreen(ctx)
            SettingsPage.DESKTOP_LINK -> com.dezzy.app.ui.screens.social.DesktopLinkSettingsScreen(ctx)
            SettingsPage.SOCIAL -> com.dezzy.app.ui.screens.social.SocialSettingsScreen(ctx)
            SettingsPage.DJ -> com.dezzy.app.ui.screens.dj.DjSettingsScreen(ctx)
            SettingsPage.DJ_VOICE -> com.dezzy.app.ui.screens.dj.DjVoiceSetupScreen(ctx)
            SettingsPage.APP_ICON -> com.dezzy.app.ui.screens.personalize.AppIconScreen(ctx)
            SettingsPage.WALLPAPER -> com.dezzy.app.ui.screens.personalize.WallpaperSettingsScreen(ctx)
            SettingsPage.COLOR_PALETTE -> com.dezzy.app.ui.screens.settings.ColorPaletteScreen(ctx)
            SettingsPage.THEME_CREATOR -> com.dezzy.app.ui.screens.settings.ThemeCreatorScreen(ctx)
            SettingsPage.BACKUP -> com.dezzy.app.ui.screens.settings.BackupSettingsScreen(ctx)
            SettingsPage.PO_TOKEN -> com.dezzy.app.ui.screens.settings.PoTokenSettingsScreen(ctx)
            SettingsPage.EXPERIMENTS -> com.dezzy.app.ui.screens.settings.ExperimentsSettingsScreen(ctx)
            SettingsPage.AOD -> com.dezzy.app.ui.screens.settings.AodSettingsScreen(ctx)
        }
        Screen.AccountPlaylists -> AccountPlaylistsScreen(ctx)
        Screen.Downloads -> DownloadsScreen(ctx)
        Screen.Lossless -> LosslessScreen(ctx)
        Screen.LosslessDownloads -> LosslessDownloadsScreen(ctx)
        is Screen.P2PChat -> com.dezzy.app.ui.screens.social.P2PChatScreen(ctx, top.pubkey)
        is Screen.P2PProfile -> com.dezzy.app.ui.screens.social.P2PProfileScreen(ctx, top.pubkey)
        Screen.P2PWelcome -> com.dezzy.app.ui.screens.social.P2PWelcomeScreen(ctx)
        is Screen.Together -> com.dezzy.app.ui.screens.together.TogetherScreen(ctx, top.joinCode)
        Screen.TogetherChat -> com.dezzy.app.ui.screens.together.TogetherChatScreen(ctx)
        Screen.TogetherDashboard -> com.dezzy.app.ui.screens.together.TogetherDashboardScreen(ctx)
        Screen.Dj -> com.dezzy.app.ui.screens.dj.DjScreen(ctx)
        Screen.Stats -> com.dezzy.app.ui.screens.stats.StatsScreen(ctx)
        Screen.YearInMusic -> com.dezzy.app.ui.screens.stats.YearInMusicScreen(ctx)
        Screen.TimeMachine -> com.dezzy.app.ui.screens.stats.TimeMachineScreen(ctx)
        is Screen.SongDna -> com.dezzy.app.ui.screens.stats.SongDnaScreen(ctx, top.videoId)
        Screen.MusicMap -> com.dezzy.app.ui.screens.musicmap.MusicMapScreen(ctx)
        is Screen.Charts -> com.dezzy.app.ui.screens.musicmap.ChartsScreen(ctx, top.countryCode)
        is Screen.SongStats -> com.dezzy.app.ui.screens.musicmap.SongStatsScreen(ctx, top.videoId)
        Screen.NewReleases -> com.dezzy.app.ui.screens.musicmap.NewReleasesScreen(ctx)
        Screen.LibraryGenres -> com.dezzy.app.ui.screens.musicmap.LibraryGenresScreen(ctx)
        Screen.Experiences -> com.dezzy.app.ui.screens.experiences.ExperiencesScreen(ctx)
        Screen.DezzyIA -> com.dezzy.app.ui.screens.ai.DezzyIAScreen(ctx)
        null -> when (navigator.tab) {
            Tab.HOME -> HomeScreen(ctx, state)
            Tab.SEARCH -> SearchScreen(ctx, "")
            Tab.LIBRARY -> LibraryScreen(ctx)
            Tab.TOGETHER -> com.dezzy.app.ui.screens.social.SocialHubScreen(ctx)
        }
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
