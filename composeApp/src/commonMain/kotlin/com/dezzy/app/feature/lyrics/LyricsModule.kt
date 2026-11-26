/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import org.koin.core.module.Module
import org.koin.dsl.module

val lyricsModule: Module = module {
    single { LyricsTranslator(get()) }
    single { LyricsService(get(), get()) }
    single { LyricsManager(get(), get(), get(), get()) }
}
