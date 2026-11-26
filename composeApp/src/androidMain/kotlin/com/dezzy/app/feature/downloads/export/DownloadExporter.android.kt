/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.dezzy.app.feature.downloads.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import java.io.File

/** Copia a la carpeta elegida con el selector de árbol (SAF), usando [DocumentsContract]. */
actual class DownloadExporter(private val context: Context) {
    actual suspend fun export(folder: String, items: List<DownloadItem>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val tree = Uri.parse(folder)
        val parent = try {
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } catch (e: Exception) {
            return@withContext 0
        }
        val existing = HashSet<String>()
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) existing.add(c.getString(0))
            }
        } catch (e: Exception) {
            // Sin lista de existentes: se copia todo y SAF renombra los repetidos.
        }
        var copied = 0
        for (item in items) {
            val source = item.filePath?.let { File(it) }?.takeIf { it.isFile } ?: continue
            if (source.name in existing) continue
            try {
                val doc = DocumentsContract.createDocument(resolver, parent, mimeFor(source.name), source.name) ?: continue
                val ok = resolver.openOutputStream(doc)?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
                if (ok) {
                    copied++
                    existing.add(source.name)
                } else {
                    runCatching { DocumentsContract.deleteDocument(resolver, doc) }
                }
            } catch (e: Exception) {
                // Un archivo que falla no detiene el resto.
            }
        }
        copied
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "webm" -> "audio/webm"
        else -> "application/octet-stream"
    }
}

actual fun createDownloadExporter(scope: org.koin.core.scope.Scope): DownloadExporter =
    DownloadExporter(scope.androidContext())
