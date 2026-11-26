/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.youtube

import com.dezzy.app.core.prefs.DezzyPreferences
import kotlinx.coroutines.flow.first

/** Idioma (`hl`) y país (`gl`) del contenido, según los ajustes. */
class ContentLocale(private val prefs: DezzyPreferences) {
    suspend fun hl(): String = prefs.contentLanguage.first()
    suspend fun gl(): String = prefs.contentCountry.first()
}
