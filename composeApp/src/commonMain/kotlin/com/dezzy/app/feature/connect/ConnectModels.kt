/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

/*
 * Tipos de Dezzy Connect. Los nombres de campo son los de `dezzy-desktop/src/shared/connect.ts`:
 * lo que viaja por la red tiene que ser idéntico en los dos lados.
 */

enum class DeviceKind { pc, phone }

/** Resumen ligero de lo que suena en otro Dezzy de la red. */
data class ConnectNow(val id: String, val title: String, val artists: String, val playing: Boolean)

/** Un Dezzy de tu grupo (vinculado con la misma clave). */
data class ConnectDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    /** Respondió hace poco en la red local. */
    val available: Boolean,
    /** Hay conexión abierta con él ahora mismo. */
    val connected: Boolean,
    val now: ConnectNow? = null,
)

data class ConnectPeer(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val incoming: Boolean,
    val rtt: Int? = null,
)

data class ConnectSelf(val id: String, val name: String)

data class ConnectState(
    /** Hay clave de grupo y las redes están abiertas. */
    val ready: Boolean = false,
    val searching: Boolean = false,
    val self: ConnectSelf = ConnectSelf("", ""),
    val devices: List<ConnectDevice> = emptyList(),
    /** Dispositivos con conexión abierta (hasta 3 a la vez). */
    val peers: List<ConnectPeer> = emptyList(),
    /** El primero de [peers] (compatibilidad). */
    val peer: ConnectPeer? = null,
    /** Ida y vuelta medida con [peer], en ms. */
    val rtt: Int? = null,
    val error: String? = null,
)

/** Canción tal como viaja entre dispositivos (la duración, en segundos). */
@Serializable
data class ConnectTrack(
    val id: String,
    val title: String,
    val artists: String = "",
    val album: String? = null,
    val thumb: String? = null,
    val dur: Double? = null,
)

/** Lo que suena. `at` es el reloj (ms) en que valía `pos` (s); al llegar ya está en el reloj local. */
@Serializable
data class ConnectPlayback(
    val queue: List<ConnectTrack>,
    val index: Int,
    val pos: Double,
    val playing: Boolean,
    val at: Double,
    val vol: Double,
)

@Serializable
enum class ConnectMode { there, both }

@Serializable
enum class ControlScope { all, one }

@Serializable
enum class ConnectJoinMode { remote, pull, follow }

/** Órdenes dentro de `{t:"cmd", cmd}`; el discriminador es `op`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("op")
sealed interface ConnectCommand {
    @Serializable @SerialName("toggle") data object Toggle : ConnectCommand
    @Serializable @SerialName("next") data object Next : ConnectCommand
    @Serializable @SerialName("prev") data object Prev : ConnectCommand
    @Serializable @SerialName("play") data object Play : ConnectCommand
    @Serializable @SerialName("pause") data object Pause : ConnectCommand
    @Serializable @SerialName("seek") data class Seek(val pos: Double) : ConnectCommand
    @Serializable @SerialName("vol") data class Vol(val v: Double) : ConnectCommand
    @Serializable @SerialName("playAt") data class PlayAt(val index: Int) : ConnectCommand
    /** Salta a esa canción de la cola si no suena ya. */
    @Serializable @SerialName("playTrack") data class PlayTrack(val id: String) : ConnectCommand
    /** Sólo pide que el otro mande su estado. */
    @Serializable @SerialName("sync") data object Sync : ConnectCommand
    @Serializable @SerialName("load") data class Load(val queue: List<ConnectTrack>, val index: Int) : ConnectCommand
    @Serializable @SerialName("radio") data class Radio(val track: ConnectTrack) : ConnectCommand
    @Serializable @SerialName("queue") data class Queue(val queue: List<ConnectTrack>, val index: Int) : ConnectCommand
}

/** Nombre de la orden en el cable (el valor de `op`). */
fun ConnectCommand.wireOp(): String = when (this) {
    ConnectCommand.Toggle -> "toggle"
    ConnectCommand.Next -> "next"
    ConnectCommand.Prev -> "prev"
    ConnectCommand.Play -> "play"
    ConnectCommand.Pause -> "pause"
    is ConnectCommand.Seek -> "seek"
    is ConnectCommand.Vol -> "vol"
    is ConnectCommand.PlayAt -> "playAt"
    is ConnectCommand.PlayTrack -> "playTrack"
    ConnectCommand.Sync -> "sync"
    is ConnectCommand.Load -> "load"
    is ConnectCommand.Radio -> "radio"
    is ConnectCommand.Queue -> "queue"
}

/** Mensajes entre dispositivos una vez abierta la conexión; el discriminador es `t`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("t")
sealed interface ConnectMessage {
    /** «Reproduce esto»: allí solo o en los dos a la vez. `ctl`: quién puede controlar. */
    @Serializable @SerialName("take")
    data class Take(
        val mode: ConnectMode,
        val pb: ConnectPlayback,
        val ctl: ControlScope? = null,
        val offset: Double? = null,
    ) : ConnectMessage

    @Serializable @SerialName("state")
    data class State(val pb: ConnectPlayback, val ctl: ControlScope? = null) : ConnectMessage

    @Serializable @SerialName("cmd")
    data class Cmd(val cmd: ConnectCommand) : ConnectMessage

    /** Pide entrar a la reproducción que ya suena en el otro dispositivo. */
    @Serializable @SerialName("join")
    data class Join(val mode: ConnectJoinMode) : ConnectMessage

    /** Devuelve la cola y posición actuales al dispositivo que pidió «pull». */
    @Serializable @SerialName("handoff")
    data class Handoff(val pb: ConnectPlayback) : ConnectMessage

    /** Deja de sonar; la conexión se cierra después. */
    @Serializable @SerialName("stop")
    data object Stop : ConnectMessage

    /** Diferencia medida por quien sigue al otro (ms) y su volumen. */
    @Serializable @SerialName("drift")
    data class Drift(val ms: Double, val vol: Double) : ConnectMessage

    /** Ajuste manual de sincronía, en ms. */
    @Serializable @SerialName("offset")
    data class Offset(val ms: Double) : ConnectMessage

    /** El centro avisa de quién acaba de tocar la música. */
    @Serializable @SerialName("ctrl")
    data class Ctrl(val by: String, val name: String, val op: String) : ConnectMessage
}

sealed interface ConnectEvent {
    data class StateChanged(val state: ConnectState) : ConnectEvent

    /** `from` y `name`: el dispositivo que lo mandó. */
    data class Message(val message: ConnectMessage, val from: String, val name: String) : ConnectEvent
}

/** JSON de los mensajes de sesión (los de dentro de las tramas cifradas). */
object ConnectJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(message: ConnectMessage): String = json.encodeToString(ConnectMessage.serializer(), message)

    /** Null si no es un mensaje que entendamos (versión distinta, basura…). */
    fun decode(obj: JsonObject): ConnectMessage? =
        runCatching { json.decodeFromJsonElement(ConnectMessage.serializer(), obj) }.getOrNull()
}
