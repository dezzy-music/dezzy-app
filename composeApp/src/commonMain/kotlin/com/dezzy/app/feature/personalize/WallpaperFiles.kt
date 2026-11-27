/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.personalize

/** Destino de una descarga que se va escribiendo por trozos (los vídeos pueden pesar mucho). */
interface WallpaperSink {
    fun write(buf: ByteArray, len: Int)

    /** Cierra el archivo con la extensión real detectada y devuelve su URI (`file://…`). */
    fun finish(ext: String): String

    /** Descarta lo escrito (error o cancelación). Es seguro llamarlo tras [finish]. */
    fun abort()
}

/** Lo que el fondo de pantalla necesita del sistema de archivos y de la red de cada plataforma. */
interface WallpaperFiles {
    fun openSink(): WallpaperSink

    /** ¿Este fondo lo bajó Dezzy de Pinterest o lo copió a su carpeta privada? (para borrar el viejo). */
    fun isOwnFile(uri: String): Boolean
    fun deleteOwnFile(uri: String)

    /** Copia un archivo elegido de la galería (`content://…`) a la carpeta privada de la app y devuelve su `file://`. */
    suspend fun importPicked(uri: String, ext: String): String?

    /** ¿Es más ancho que alto? Cuenta la rotación del vídeo y el EXIF de la foto. Ante la duda, false. */
    fun isLandscape(uri: String, isVideo: Boolean): Boolean

    /** Si el dispositivo tiene conexión (para distinguir «sin internet» de «Pinterest no responde»). */
    fun isOnline(): Boolean
}

expect fun wallpaperFiles(): WallpaperFiles
