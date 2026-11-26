/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

/** Acceso a la carpeta interna de descargas. Rutas absolutas con «/». */
interface DownloadStorage {
    /** Carpeta de descargas (creada). */
    fun root(): String

    /** Añade los primeros [length] bytes de [bytes] al final del archivo (lo crea si no existe). */
    fun append(path: String, bytes: ByteArray, length: Int)

    /** Tamaño del archivo; 0 si no existe. */
    fun size(path: String): Long

    fun rename(from: String, to: String): Boolean

    fun delete(path: String): Boolean

    fun exists(path: String): Boolean

    /** Espacio libre en el volumen de la carpeta; null si no se sabe. */
    fun freeBytes(): Long?
}

/** Android: `filesDir/downloads`; desktop: `~/.dezzy/downloads`. */
expect fun createDownloadStorage(): DownloadStorage

/**
 * Aviso del gestor a la plataforma de que hay (o ya no) trabajo de descarga pendiente.
 * Android arranca/para el servicio en primer plano; desktop no hace nada.
 */
expect fun onDownloadsActive(active: Boolean)
