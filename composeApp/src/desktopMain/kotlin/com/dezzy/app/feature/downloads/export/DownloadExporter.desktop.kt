/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads.export

import com.dezzy.app.feature.downloads.DownloadItem
import java.io.File

actual class DownloadExporter {
    actual suspend fun export(folder: String, items: List<DownloadItem>): Int {
        val dir = File(folder)
        if (!dir.isDirectory) return 0
        var copied = 0
        for (item in items) {
            val source = item.filePath?.let { File(it) }?.takeIf { it.isFile } ?: continue
            val target = File(dir, source.name)
            if (target.exists()) continue
            try {
                source.copyTo(target)
                copied++
            } catch (e: Exception) {
                target.delete()
            }
        }
        return copied
    }
}

actual fun createDownloadExporter(scope: org.koin.core.scope.Scope): DownloadExporter = DownloadExporter()
