/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.audio

import com.dezzy.app.core.prefs.DezzyPreferences

/** Escritorio no tiene motor de audio propio: los ajustes se guardan en memoria y no suenan. */
actual fun createAudioEffects(prefs: DezzyPreferences): AudioEffects = InMemoryAudioEffects()
