/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Personalización: icono de la app, fondo (imagen/vídeo, también desde Pinterest) y mods de canción.
 * Nada que arrancar al inicio: todo se lee bajo demanda desde las preferencias.
 */
val personalizeModule: Module = module {
    single { AppIconStore(get()) }
    single { WallpaperStore(get()) }
    single { SongMods(get()) }
    single { PinterestWallpaper(get()) }
}
