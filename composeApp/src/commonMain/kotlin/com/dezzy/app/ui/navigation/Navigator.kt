/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** Las cuatro pestañas de la barra inferior (boceto A de la fase 1). */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Inicio", Icons.Outlined.Home, Icons.Filled.Home),
    SEARCH("Buscar", Icons.Outlined.Search, Icons.Filled.Search),
    LIBRARY("Biblioteca", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
    TOGETHER("Juntos", Icons.Outlined.Groups, Icons.Filled.Groups),
}

/** Pantallas que se apilan encima de una pestaña. */
sealed interface Screen {
    data class Album(val id: String) : Screen
    data class Artist(val id: String) : Screen
    data class Playlist(val id: String) : Screen
    data class Search(val query: String) : Screen
    /** Lista guardada en el móvil. */
    data class LocalPlaylist(val id: String) : Screen
    /** «Tus me gusta». */
    data object Liked : Screen
    data object History : Screen
    data object MoodsAndGenres : Screen
    /** Página de `browse` con carruseles (un estado de ánimo o un género). */
    data class Browse(val title: String, val browseId: String, val params: String?) : Screen
    /** Función que llega en una fase posterior de la migración. */
    data class Pending(val title: String, val phase: String) : Screen
    /** Ajustes (página principal o una subpágina). */
    data class Settings(val page: SettingsPage = SettingsPage.MAIN) : Screen
    /** Listas de YouTube Music de la cuenta con sesión. */
    data object AccountPlaylists : Screen
}

enum class SettingsPage {
    MAIN, APPEARANCE, PLAYER, PRIVACY, STORAGE, ABOUT,
    ACCOUNTS, CONTENT, YOUTUBE_LOGIN, SPOTIFY, SPOTIFY_LOGIN, SPOTIFY_IMPORT,
}

/**
 * Navegación propia y sencilla: cada pestaña guarda su pila, volver a tocar la pestaña activa
 * vuelve a su raíz y «atrás» primero vacía la pila y luego regresa al Inicio.
 */
@Stable
class Navigator {
    var tab by mutableStateOf(Tab.HOME)
        private set
    private val stacks = Tab.entries.associateWith { mutableStateListOf<Screen>() }

    val stack: List<Screen> get() = stacks.getValue(tab)
    val top: Screen? get() = stack.lastOrNull()

    fun select(target: Tab) {
        if (target == tab) stacks.getValue(tab).clear() else tab = target
    }

    fun push(screen: Screen) {
        val s = stacks.getValue(tab)
        if (s.lastOrNull() != screen) s.add(screen)
    }

    /** true si consumió el «atrás». */
    fun back(): Boolean {
        val s = stacks.getValue(tab)
        return when {
            s.isNotEmpty() -> { s.removeAt(s.lastIndex); true }
            tab != Tab.HOME -> { tab = Tab.HOME; true }
            else -> false
        }
    }

    val canGoBack: Boolean get() = stack.isNotEmpty() || tab != Tab.HOME
}

/** «Atrás» del sistema (Android); en escritorio no hace nada. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
