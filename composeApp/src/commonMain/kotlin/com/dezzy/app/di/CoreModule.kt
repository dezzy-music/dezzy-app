/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import com.dezzy.app.core.accounts.AccountStore
import com.dezzy.app.core.accounts.SessionHealth
import com.dezzy.app.core.database.AccountAwareMusicRepository
import com.dezzy.app.core.database.SqlMusicRepository
import com.dezzy.app.source.spotify.SpotifyClient
import com.dezzy.app.source.spotify.SpotifyImporter
import com.dezzy.app.source.spotify.SpotifyMatcher
import com.dezzy.app.source.youtube.ContentLocale
import com.dezzy.app.source.youtube.YouTubeAuth
import com.dezzy.app.source.youtube.YouTubeLibrarySync
import com.dezzy.app.source.youtube.YouTubeLogin
import kotlin.time.Clock
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.net.HttpClientFactory
import com.dezzy.app.core.database.SqlStreamCache
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.source.stream.HttpStreamProbe
import com.dezzy.app.source.stream.StreamUrlResolver
import com.dezzy.app.source.youtube.YouTubeMusicClient
import com.dezzy.app.feature.video.MusicVideoResolver
import org.koin.core.module.Module
import org.koin.dsl.module
import com.dezzy.app.feature.discord.discordModule
import com.dezzy.app.feature.lyrics.lyricsModule
import com.dezzy.app.feature.canvas.canvasModule
import com.dezzy.app.feature.downloads.downloadsModule
import com.dezzy.app.feature.lossless.losslessModule
import com.dezzy.app.feature.audio.audioModule
import com.dezzy.app.feature.scrobble.scrobbleModule
import com.dezzy.app.source.spotify.spotifyFeaturesModule

val coreModule: Module = module {
    includes(scrobbleModule, discordModule, spotifyFeaturesModule, lyricsModule, canvasModule, downloadsModule, losslessModule, audioModule)
    single { HttpClientFactory.create() }
    single { DezzyDatabase(get()) }
    single { DezzyPreferences(get()) }
    single { SessionHealth(now = { Clock.System.now().toEpochMilliseconds() }) }
    single { AccountStore(get(), get()) }
    single { YouTubeAuth(get()) }
    single { ContentLocale(get()) }
    // El cliente de catálogo lleva la sesión; MusicVideoResolver y StreamUrlResolver reciben el HttpClient sin cookies.
    single { YouTubeMusicClient(get(), get(), get()) }
    single { SqlMusicRepository(get()) }
    single<MusicRepository> { AccountAwareMusicRepository(get<SqlMusicRepository>(), get(), get()) }
    single { YouTubeLibrarySync(get(), get<SqlMusicRepository>(), get(), get()) }
    single { YouTubeLogin(get(), get(), get()) }
    single { SpotifyClient(get(), get(), get()) }
    single { SpotifyMatcher(get(), get<SqlMusicRepository>()) }
    single { SpotifyImporter(get(), get(), get<SqlMusicRepository>(), get()) }
    single { MusicVideoResolver(get(), get(), get()) }
    single { StreamUrlResolver(engine = get(), probe = HttpStreamProbe(get()), cache = SqlStreamCache(get())) }
}
