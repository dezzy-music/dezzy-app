/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.downloads

import android.content.Context
import org.koin.mp.KoinPlatform
import java.io.File
import java.io.FileOutputStream

private class AndroidDownloadStorage(context: Context) : DownloadStorage {
    private val dir = File(context.applicationContext.filesDir, "downloads")

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

/** El Context sale de Koin (androidContext ya lo registró), así no hace falta tocar PlatformModule. */
actual fun createDownloadStorage(): DownloadStorage = AndroidDownloadStorage(KoinPlatform.getKoin().get<Context>())
