/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import androidx.datastore.preferences.core.booleanPreferencesKey

/** Saltar los silencios de las canciones. */
val SkipSilenceKey = booleanPreferencesKey("playerSkipSilence")

/** Reproducción automática de música similar al acabarse la cola. Por defecto sí. */
val AutoplayKey = booleanPreferencesKey("playerAutoplay")

/** Pausar cuando el volumen de medios llega a 0 (y reanudar al subirlo si la pausa la puso la app). */
val PauseOnMuteKey = booleanPreferencesKey("playerPauseOnMute")

/** Reproducir al conectar auriculares o un altavoz Bluetooth si hay cola. */
val PlayOnBluetoothKey = booleanPreferencesKey("playerPlayOnBluetooth")

/** Guardar el historial y el tiempo escuchado (estadísticas). Por defecto sí. */
val SaveListenHistoryKey = booleanPreferencesKey("privacySaveListenHistory")
