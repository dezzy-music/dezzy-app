/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.ui.player.kit.rememberEnumPreference

/**
 * Estilo de la barra de navegación: FULL = barra de ancho completo con etiquetas; SLIM = la misma
 * barra delgada, sólo iconos; DOCK = dock flotante con botón de búsqueda aparte.
 */
enum class NavBarStyle { FULL, SLIM, DOCK }

/** Nombre visible de cada estilo. */
fun NavBarStyle.label(): String = when (this) {
    NavBarStyle.FULL -> "Completa"
    NavBarStyle.SLIM -> "Delgada"
    NavBarStyle.DOCK -> "Flotante"
}

val NavBarStyleKey = stringPreferencesKey("navBarStyle")

/** Lee y escribe el estilo de la barra (predeterminado FULL); asignar `.value` lo guarda. */
@Composable
fun rememberNavBarStyle(): MutableState<NavBarStyle> =
    rememberEnumPreference(NavBarStyleKey, NavBarStyle.FULL)
