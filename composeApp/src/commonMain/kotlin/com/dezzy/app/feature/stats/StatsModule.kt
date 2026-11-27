/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.stats

import org.koin.core.module.Module
import org.koin.dsl.module

/** Estadísticas: necesita `DezzyDatabase` (ya en CoreModule). No hay nada que arrancar al inicio. */
val statsModule: Module = module {
    single { StatsStore(get()) }
}
