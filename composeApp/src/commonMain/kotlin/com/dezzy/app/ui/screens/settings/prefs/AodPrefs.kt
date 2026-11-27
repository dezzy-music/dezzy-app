/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.settings.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Estilos de la pantalla siempre activa. */
enum class AodStyle(val label: String, val description: String) {
    CLASSIC("Clásico", "Portada centrada"),
    AMBIENT("Ambiente", "Arte difuminado de fondo"),
    MINIMAL("Minimal", "Sólo texto y reloj"),
    SPOTLIGHT("Spotlight", "Portada con foco de luz"),
}

/** Forma de la portada en la pantalla siempre activa. */
enum class AodCoverShape(val label: String) {
    ROUNDED("Redondo"),
    CIRCLE("Círculo"),
    SQUIRCLE("Squircle"),
    DIAMOND("Diamante"),
    HEXAGON("Hexágono"),
    STAR("Estrella"),
    ARCH("Arco"),
    FLOWER("Flor"),
}

enum class AodButtonStyle(val label: String) { ROUNDED("Rounded"), SQUARE("Square"), ACCENT("Accent") }

val AodImmersiveKey = booleanPreferencesKey("aodImmersive")

/** Minutos de inactividad antes de activar; 0 = nunca. */
val AodInactivityMinutesKey = intPreferencesKey("aodInactivityMinutes")
val AodStyleKey = stringPreferencesKey("aodStyle")
val AodCoverShapeKey = stringPreferencesKey("aodCoverShape")
val AodButtonStyleKey = stringPreferencesKey("aodButtonStyle")

/** Fracción del ancho que ocupa la portada (0,40–1,00). */
val AodCoverFractionKey = floatPreferencesKey("aodCoverFraction")

/** Escala global de título, artista y tiempos (0,75–1,40). */
val AodTextScaleKey = floatPreferencesKey("aodTextScale")

/** Opacidad del velo en Ambiente y Spotlight (0–1). */
val AodDimKey = floatPreferencesKey("aodDim")
val AodTransitionMsKey = intPreferencesKey("aodTransitionMs")
val AodShowClockKey = booleanPreferencesKey("aodShowClock")
val AodShowTitleKey = booleanPreferencesKey("aodShowTitle")
val AodShowArtistKey = booleanPreferencesKey("aodShowArtist")
val AodShowTimeKey = booleanPreferencesKey("aodShowTime")
val AodShowProgressKey = booleanPreferencesKey("aodShowProgress")
val AodShowControlsKey = booleanPreferencesKey("aodShowControls")
