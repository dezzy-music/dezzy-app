/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.player.lyrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.core.model.Track

/** Posición del texto de las letras. */
enum class LyricsAlign(val label: String, val textAlign: TextAlign) {
    LEFT("Izquierda", TextAlign.Start),
    CENTER("Centro", TextAlign.Center),
    RIGHT("Derecha", TextAlign.End),
}

/** Cómo entra la línea que suena. */
enum class LyricsAnim(val label: String) {
    FOCUS("Foco de luz"),
    FADE("Desvanecer"),
    SLIDE("Deslizar"),
    NONE("Sin animación"),
}

/** Alineación del texto (nombre de [LyricsAlign]). */
val LyricsAlignKey = stringPreferencesKey("lyrics_align")

/** Estilo de animación (nombre de [LyricsAnim]). */
val LyricsAnimKey = stringPreferencesKey("lyrics_anim")

/** Escala del tamaño del texto: 0,7 a 1,6 (1 = normal). */
val LyricsTextScaleKey = floatPreferencesKey("lyrics_text_scale")

/** Desfase de sincronía en milisegundos (positivo = la letra va más adelantada). */
val LyricsSyncOffsetKey = intPreferencesKey("lyrics_sync_offset_ms")

/** Estado global de «elegir líneas para compartir» (mismo contrato que en Dezzy 3.x). */
object LyricsSelectionState {
    var isSelectingForShare by mutableStateOf(false)
    var pendingShareSong by mutableStateOf<Track?>(null)
}
