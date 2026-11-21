/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import com.dezzy.app.core.database.DefaultMusicRepository
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.net.HttpClientFactory
import com.dezzy.app.core.prefs.createPlatformSecureStorage
import com.dezzy.app.source.youtube.YouTubeMusicClient
import org.koin.core.module.Module
import org.koin.dsl.module

val coreModule: Module = module {
    single { HttpClientFactory.create() }
    single<MusicRepository> { DefaultMusicRepository() }
    single { createPlatformSecureStorage() }
    single { YouTubeMusicClient(get()) }
}
