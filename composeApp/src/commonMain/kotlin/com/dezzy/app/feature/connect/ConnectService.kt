/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.connect

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.scope.Scope

/**
 * Dezzy Connect («dezzy-connect-v1»): encuentra los Dezzy de tu grupo en la red de casa y habla con
 * ellos directamente, sin servidores. Mismo protocolo que `dezzy-desktop/src/main/connect.ts`.
 */
interface ConnectService {
    val state: StateFlow<ConnectState>

    /** Mensajes de los demás y cambios de estado. */
    val events: SharedFlow<ConnectEvent>

    /** Abre avisos y servidor. Repetirlo no hace nada. */
    fun start()

    /** Clave de grupo (64 hex). La crea y guarda en SecureStorage («account_connect_key») si no existe. */
    suspend fun groupKeyHex(): String

    /** Busca durante unos segundos (al abrir la hoja de dispositivos). */
    fun search()

    suspend fun open(id: String): Result<Unit>

    /** A todos los conectados, sólo a [to], o a todos menos [except]. */
    fun send(message: ConnectMessage, to: String? = null, except: String? = null)

    /** Cierra la conexión con [id], o con todos. */
    fun close(id: String? = null)

    fun forget(id: String)

    /** Publica qué canción está puesta aquí para que otros Dezzy puedan entrar o continuar. */
    fun setNow(now: ConnectNow?)
}

/** Lo que cada plataforma aporta a Connect: nombre del equipo y bloqueo multicast de Android. */
interface ConnectPlatform {
    val kind: DeviceKind
    fun deviceName(): String

    /** Android necesita un MulticastLock para recibir los avisos de difusión. */
    fun setMulticast(enabled: Boolean) {}
}

expect fun createConnectPlatform(scope: Scope): ConnectPlatform

/** Actual en `jvmShared` (UDP 47830 + TCP), común a Android y escritorio. */
expect fun createConnectService(scope: Scope): ConnectService
