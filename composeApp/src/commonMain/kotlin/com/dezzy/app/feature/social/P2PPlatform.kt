/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.social

import kotlinx.coroutines.flow.StateFlow
import org.koin.core.scope.Scope

/** Herramientas de imagen y video que dependen de la plataforma. */
interface P2PImageTools {
    /** Foto de perfil cuadrada y pequeña como `data:image/jpeg;base64,…` (null si no se puede leer). */
    suspend fun avatarDataUri(source: String, size: Int = 192): String?

    suspend fun avatarDataUri(bytes: ByteArray, size: Int = 192): String?

    /** Reduce una foto (lado mayor [maxSide]) y la devuelve en JPEG. */
    suspend fun compressPhoto(source: String, maxSide: Int = 1080): ByteArray?

    suspend fun compressPhoto(bytes: ByteArray, maxSide: Int = 1080): ByteArray?

    /** Comprueba un video de portada (hasta 15 s) y saca su fotograma. */
    suspend fun prepareBannerVideo(source: String): Result<P2PBannerVideo>
}

/** Avisos del sistema. En el escritorio no hace nada. */
interface P2PNotifier {
    fun notifyMessage(peer: String, name: String, text: String, songTitle: String?, createdAtSec: Long)

    fun notifyFollowRequest(peer: String, name: String)

    fun clearChat(peer: String)

    fun clearAll()
}

enum class NearbyStatus { IDLE, SCANNING, NO_PERMISSION, BLUETOOTH_OFF, UNSUPPORTED }

/** Bluetooth de baja energía para detectar gente cerca (servicio 0xD22E/0xD22F). */
interface P2PBle {
    val status: StateFlow<NearbyStatus>

    fun requiredPermissions(): List<String>

    fun hasPermissions(): Boolean

    /** Anuncia mi clave (si [advertise]) y escucha a los demás; [onFound] recibe clave hex y señal. */
    fun start(myPubkey: String, advertise: Boolean, onFound: (pubkey: String, rssi: Int) -> Unit)

    fun stop()
}

expect fun createP2PImageTools(scope: Scope): P2PImageTools

expect fun createP2PNotifier(scope: Scope): P2PNotifier

expect fun createP2PBle(scope: Scope): P2PBle

expect fun p2pDeviceName(): String
