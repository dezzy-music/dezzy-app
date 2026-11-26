/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import java.io.File
import java.io.FileOutputStream

private class DesktopDownloadStorage : DownloadStorage {
    private val dir = File(System.getProperty("user.home"), ".dezzy/downloads")

    override fun root(): String {
        dir.mkdirs()
        return dir.absolutePath.replace('\\', '/')
    }

    override fun append(path: String, bytes: ByteArray, length: Int) {
        File(path).parentFile?.mkdirs()
        FileOutputStream(path, true).use { it.write(bytes, 0, length) }
    }

    override fun size(path: String): Long = File(path).let { if (it.isFile) it.length() else 0L }

    override fun rename(from: String, to: String): Boolean = File(from).renameTo(File(to))

    override fun delete(path: String): Boolean = File(path).delete()

    override fun exists(path: String): Boolean = File(path).isFile

    override fun freeBytes(): Long? = runCatching { dir.apply { mkdirs() }.usableSpace }.getOrNull()
}

actual fun createDownloadStorage(): DownloadStorage = DesktopDownloadStorage()

/** Desktop no tiene servicio en primer plano. */
actual fun onDownloadsActive(active: Boolean) = Unit
