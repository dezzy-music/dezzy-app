/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lyrics

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/** Una palabra con su tiempo (LRC mejorado o TTML). */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/** Una línea con tiempo. [text] vacío = pausa (la interfaz pinta «♪»). */
data class SyncedLine(val startMs: Long, val text: String, val words: List<LyricWord>? = null)

data class LyricsResult(val synced: List<SyncedLine>?, val plain: String?, val source: String)

data class LyricsQuery(
    val videoId: String?,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationSec: Int?,
)

sealed interface LyricsState {
    /** No suena nada. */
    data object Idle : LyricsState
    data class Loading(val videoId: String) : LyricsState
    data class Found(val videoId: String, val result: LyricsResult) : LyricsState
    data class NotFound(val videoId: String) : LyricsState
}

sealed interface TranslationState {
    data object Off : TranslationState
    data class Loading(val lang: String) : TranslationState

    /** Mismo índice que synced (o que las líneas de plain); null = igual al original o vacía. */
    data class Ready(val lang: String, val lines: List<String?>) : TranslationState
    data class Failed(val lang: String) : TranslationState
}

/** Idioma al que se traducen las letras; "" = sin traducir. */
val LyricsTranslateLangKey: Preferences.Key<String> = stringPreferencesKey("lyrics_translate_lang")

val TRANSLATE_LANGUAGES: List<Pair<String, String>> = listOf(
    "es" to "Español",
    "en" to "Inglés",
    "pt" to "Portugués",
    "fr" to "Francés",
    "it" to "Italiano",
    "de" to "Alemán",
    "ja" to "Japonés",
    "ko" to "Coreano",
)
