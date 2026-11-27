/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import org.koin.core.module.Module
import org.koin.dsl.module

val djModule: Module = module {
    single { DjStore(get(), get()) }
    single { DjSettings(get(), get()) }
    single { GeminiDjClient(get()) }
    single { DjVoiceApi(get()) }
    single<DjAudioPlayer> { createDjAudioPlayer(this) }
    single { DjSetBuilder(get()) }
    single { DjSkipBridge(get(), get()) }
    single { DjController(get(), get(), get(), get(), get(), get(), get()) }
}
