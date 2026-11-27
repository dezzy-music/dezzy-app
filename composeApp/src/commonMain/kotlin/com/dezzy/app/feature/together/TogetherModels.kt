/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.together

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/** Nombre visible en las salas de «Escuchar juntos» ("" = sin elegir). */
val TogetherNameKey: Preferences.Key<String> = stringPreferencesKey("together_name")

data class RoomUser(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val connected: Boolean,
)

/** Canción de la sala. [duration] en milisegundos. */
data class RoomTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0,
    val thumbnail: String? = null,
    val suggestedBy: String? = null,
)

data class RoomChat(
    val id: String,
    val userId: String,
    val name: String,
    val text: String,
    val at: Long,
)

data class RoomSettings(
    val allowGuestsToControlPlayback: Boolean = false,
    val allowGuestsToAddTracks: Boolean = true,
    val requireHostApprovalToJoin: Boolean = false,
    val isPublic: Boolean = false,
    /** Modo DJ: sólo el anfitrión decide lo que suena; los demás proponen y votan. */
    val djModeEnabled: Boolean = false,
    /** Votos con los que una propuesta entra sola en la cola (0 = decide el anfitrión). */
    val autoQueueVotes: Int = 0,
)

/** Propuesta de un invitado, con sus votos a favor. */
data class RoomSuggestion(
    val id: String,
    val fromId: String,
    val fromName: String,
    val track: RoomTrack,
    val votes: Int,
    val voters: List<String>,
)

data class RoomSnapshot(
    val code: String,
    val hostId: String,
    val users: List<RoomUser>,
    val track: RoomTrack?,
    val playing: Boolean,
    /** Posición en ms en el momento [at] (reloj local). */
    val position: Long,
    val at: Long,
    val queue: List<RoomTrack>,
    val settings: RoomSettings,
)

/** Orden de reproducción que llega del anfitrión. [position] en ms. */
data class RoomPlayback(
    val action: String,
    val trackId: String?,
    val position: Long,
    val track: RoomTrack?,
    val queue: List<RoomTrack>?,
)

/** Géneros de sala (los mismos que el móvil y el escritorio). */
enum class RoomTheme { MIXED, REGGAETON, POP, LATIN, LOFI, ROCK, HIP_HOP, RNB, ELECTRONIC, KPOP, JAZZ, CLASSICAL }

/** Sala pública que anuncia el servidor. */
data class PublicRoom(
    val code: String,
    val hostName: String,
    val theme: RoomTheme,
    val participants: Int,
    val trackTitle: String?,
    val trackArtist: String?,
    val thumbnail: String?,
)

/** Lo que cada oyente informa al anfitrión (panel del anfitrión). */
data class ListenerInfo(
    val userId: String,
    val trackId: String?,
    val positionMs: Long,
    val playing: Boolean,
    val buffering: Boolean,
    val at: Long,
)

sealed interface TogetherState {
    data class Idle(val error: String? = null) : TogetherState
    data object Connecting : TogetherState

    /** Esperando aprobación del anfitrión. */
    data class Waiting(val code: String) : TogetherState

    data class InRoom(
        val me: String,
        val isHost: Boolean,
        val room: RoomSnapshot,
        val chat: List<RoomChat>,
        /** (id, nombre) de quien pide entrar. */
        val pending: List<Pair<String, String>>,
        val suggestions: List<RoomSuggestion>,
        val listeners: Map<String, ListenerInfo> = emptyMap(),
    ) : TogetherState
}
