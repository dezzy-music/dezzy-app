/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import org.koin.core.module.Module
import org.koin.dsl.module

/** Opus llama a `get<ConnectPlaybackBridge>().start()` al arrancar la app (arranca también el servicio). */
val connectModule: Module = module {
    single<ConnectService> { createConnectService(this) }
    single { ConnectPlaybackBridge(get(), get(), get()) }
}
