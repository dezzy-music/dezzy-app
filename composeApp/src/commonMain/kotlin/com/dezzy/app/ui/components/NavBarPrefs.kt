/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dezzy.app.ui.player.kit.rememberEnumPreference

/** Estilo de la barra de navegación: FULL = barra M3 de ancho completo; DOCK = dock compacto con búsqueda. */
enum class NavBarStyle { FULL, DOCK }

/** Nombre visible de cada estilo. */
fun NavBarStyle.label(): String = when (this) {
    NavBarStyle.FULL -> "Completa"
    NavBarStyle.DOCK -> "Dock"
}

val NavBarStyleKey = stringPreferencesKey("navBarStyle")

/** Lee y escribe el estilo de la barra (predeterminado FULL); asignar `.value` lo guarda. */
@Composable
fun rememberNavBarStyle(): MutableState<NavBarStyle> =
    rememberEnumPreference(NavBarStyleKey, NavBarStyle.FULL)
