/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.canvas

import androidx.datastore.preferences.core.booleanPreferencesKey

/** Canvas de la canción: trozo de videoclip en bucle sobre la carátula. Encendido por defecto. */
val CanvasSongEnabledKey = booleanPreferencesKey("canvas_song")

/** Canvas del artista: cabecera en movimiento en la página del artista. Encendido por defecto. */
val CanvasArtistEnabledKey = booleanPreferencesKey("canvas_artist")

const val CANVAS_SONG_DEFAULT = true
const val CANVAS_ARTIST_DEFAULT = true
