/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

/** Lee hasta [max] bytes del principio del archivo; null si no existe o falla. */
expect fun readFileHead(path: String, max: Int): ByteArray?

/**
 * Sustituye los primeros [oldHeadLength] bytes del archivo por [newHead] sin cargar el audio en memoria
 * (escribe a un temporal y renombra). Devuelve false si algo falla y deja el original intacto.
 */
expect fun replaceFileHead(path: String, oldHeadLength: Int, newHead: ByteArray): Boolean

/** Copia una descarga a la carpeta pública Música/Dezzy. Android: MediaStore; desktop: no hace nada. */
expect class PublicMusicCopier {
    /** true si quedó copiada (o ya estaba). */
    suspend fun copy(path: String, displayName: String, mime: String): Boolean
}

/** Crea el [PublicMusicCopier] de la plataforma desde Koin (Android necesita el contexto). */
expect fun createPublicMusicCopier(scope: org.koin.core.scope.Scope): PublicMusicCopier
