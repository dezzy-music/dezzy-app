/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.source.spotify.SpotifyFeatures
import com.dezzy.app.feature.lyrics.LyricsManager
import com.dezzy.app.source.spotify.SpotifyImporter
import com.dezzy.app.source.youtube.YouTubeLibrarySync
import com.dezzy.app.ui.AppShell
import com.dezzy.app.ui.theme.AppInterfaceTheme
import com.dezzy.app.ui.theme.DarkMode
import com.dezzy.app.ui.theme.DezzyTheme
import com.dezzy.app.ui.theme.LiquidGlassIntensity
import org.koin.compose.koinInject

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    val prefs: DezzyPreferences = koinInject()
    val themeMode by prefs.themeMode.collectAsState("dark")
    val pureBlack by prefs.pureBlack.collectAsState(false)
    val dynamicColor by prefs.dynamicColor.collectAsState(false)
    val interfaceTheme by prefs.interfaceTheme.collectAsState("NORMAL")
    val glass by prefs.glassIntensity.collectAsState("PURE")
    val importer: SpotifyImporter = koinInject()
    val librarySync: YouTubeLibrarySync = koinInject()
    val spotifyFeatures: SpotifyFeatures = koinInject()
    val lyricsManager: LyricsManager = koinInject()
    LaunchedEffect(Unit) {
        lyricsManager.start()
        importer.startAutoSync()
        runCatching { librarySync.syncLikesIfDue() }
        runCatching { spotifyFeatures.maybeAutoSyncLikes() }
    }

    DezzyTheme(
        darkMode = when (themeMode) {
            "system" -> DarkMode.SYSTEM
            "light" -> DarkMode.LIGHT
            else -> DarkMode.DARK
        },
        pureBlack = pureBlack,
        dynamicColor = dynamicColor,
        interfaceTheme = AppInterfaceTheme.entries.firstOrNull { it.name == interfaceTheme } ?: AppInterfaceTheme.NORMAL,
        glassIntensity = LiquidGlassIntensity.entries.firstOrNull { it.name == glass } ?: LiquidGlassIntensity.PURE,
    ) {
        AppShell(player = koinInject(), client = koinInject())
    }
}
