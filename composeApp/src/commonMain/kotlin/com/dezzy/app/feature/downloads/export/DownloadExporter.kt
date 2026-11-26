/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads.export

import com.dezzy.app.feature.downloads.DownloadItem

/** Copia las descargas terminadas a una carpeta elegida por el usuario. Android: URI de árbol SAF; desktop: ruta. */
expect class DownloadExporter {
    /** Número de archivos copiados (los que ya estaban en la carpeta no cuentan). */
    suspend fun export(folder: String, items: List<DownloadItem>): Int
}

/** Crea el [DownloadExporter] de la plataforma desde Koin (Android necesita el contexto). */
expect fun createDownloadExporter(scope: org.koin.core.scope.Scope): DownloadExporter
