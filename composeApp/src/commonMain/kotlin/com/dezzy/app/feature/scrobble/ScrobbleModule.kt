/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.scrobble

import org.koin.core.module.Module
import org.koin.dsl.module

val scrobbleModule: Module = module {
    single { LastFmClient(get(), get()) }
    single { ListenBrainzClient(get(), get()) }
    single { Scrobbler(get(), get(), get(), get()) }
}
