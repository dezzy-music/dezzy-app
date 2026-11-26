/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.di

import com.dezzy.app.core.database.SqlMusicRepository
import com.dezzy.app.core.database.db.DezzyDatabase
import com.dezzy.app.core.database.MusicRepository
import com.dezzy.app.core.net.HttpClientFactory
import com.dezzy.app.core.database.SqlStreamCache
import com.dezzy.app.core.prefs.DezzyPreferences
import com.dezzy.app.source.stream.HttpStreamProbe
import com.dezzy.app.source.stream.StreamUrlResolver
import com.dezzy.app.source.youtube.YouTubeMusicClient
import org.koin.core.module.Module
import org.koin.dsl.module

val coreModule: Module = module {
    single { HttpClientFactory.create() }
    single { DezzyDatabase(get()) }
    single<MusicRepository> { SqlMusicRepository(get()) }
    single { YouTubeMusicClient(get()) }
    single { DezzyPreferences(get()) }
    single { StreamUrlResolver(engine = get(), probe = HttpStreamProbe(get()), cache = SqlStreamCache(get())) }
}
