/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * «Escuchar juntos». Opus llama a `get<TogetherSession>().start()` al arrancar la app (idempotente);
 * no abre ninguna conexión hasta que el usuario crea o se une a una sala.
 */
val togetherModule: Module = module {
    single { TogetherClient(get()) }
    single { TogetherSession(get(), get(), get()) }
}
