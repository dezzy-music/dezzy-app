/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import org.koin.core.module.Module
import org.koin.dsl.module

/** El motor de audio: la implementación depende de la plataforma ([createAudioEffects]). */
val audioModule: Module = module {
    single<AudioEffects> { createAudioEffects(get()) }
}
