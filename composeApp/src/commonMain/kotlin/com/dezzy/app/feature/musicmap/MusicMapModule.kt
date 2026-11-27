/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.musicmap

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Mapa musical y éxitos. Nada que arrancar al inicio: todo se crea al abrir una pantalla.
 * Necesita en Koin: DezzyDatabase, SqlDriver, HttpClient, YouTubeMusicClient, SpotifyFeatures,
 * DezzyPreferences, P2PMap (ya existen) y el `Context` de Android para la ubicación.
 */
val musicMapModule: Module = module {
    single { MusicMapStore(get(), get()) }
    single { MusicMapCharts(get(), get()) }
    single { MusicMapGenres(get(), get()) }
    single { SongChartStats(get(), get(), get()) }
    single { createMapLocationSource(this) }
    single { MusicMapLocation(get(), get(), get()) }
}
